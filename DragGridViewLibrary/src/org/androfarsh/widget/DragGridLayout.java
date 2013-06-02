package org.androfarsh.widget;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector.OnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;

import com.example.draggridviewlibrary.R;

public class DragGridLayout extends ViewGroup {
	private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
	private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();

	private static final int UNKNOWN = -1;
	private static final float SCALE_FACTOR = 0.8f;
	private static final int DELTA = 10;
	private static final int DURATION = 200;
	private static final int DEFAULT_CELL_COUNT = 4;
	private static final int DEFAULT_GRAVITY = Gravity.CENTER;

	private boolean mEditMode;

	private View mRootView;

	private final Set<Cell> mHoveredCells = new HashSet<Cell>();

	private final List<Cell> mCells = new ArrayList<Cell>();

	private int mCellSize;

	private GestureListenerImpl mGestureListener;

	private GestureDetectorCompat mGestureDetector;

	private final Region mCellsRegion = new Region();

	private final Region mFreeCellRegion = new Region();

	private final Region mTmpRegion = new Region();

	private final Rect mTmpRect = new Rect();

	private Drawable mHighlightDrawable;

	private Drawable mCellDrawable;

	private float mPrevX;

	private float mPrevY;

	private DragNode mDragNode;

	private OnViewDragListener mDragListener;

	private float mScaleFactor = SCALE_FACTOR;

	private boolean mDebugMode;

	private HierarchyChangeListenerImpl mHierarchyChangeListener;

	private BitmapDrawable mRootViewDrawable;

	private OnCellClickListener mCellClickListener;

	private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	private final Runnable mLongHoverDispatcher = new Runnable() {

		@Override
		public void run() {
			onLongHover();
			mLoongHoveredRequested = false;
		}
	};

	private final Handler mHandler = new Handler();

	private boolean mWidgetVisibility = true;

	private Cell mPressedCell;

	private boolean mEditModeSwitchOff;

	private int mRootViewId = UNKNOWN;

	private OnViewRemoveListener mRemoveListener;

	private boolean mLoongHoveredRequested;

	private int mCellCount = DEFAULT_CELL_COUNT;

	private int mGravity = DEFAULT_GRAVITY;

	public DragGridLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DragGridLayout, defStyle, 0);

		mDebugMode = a.getBoolean(R.styleable.DragGridLayout_debug_mode, mDebugMode);
		mCellDrawable = a.getDrawable(R.styleable.DragGridLayout_cell_drawable);
		mHighlightDrawable = a.getDrawable(R.styleable.DragGridLayout_highlight_drawable);
		mCellCount  = a.getInteger(R.styleable.DragGridLayout_cell_count, DEFAULT_CELL_COUNT);
		mGravity   = a.getInteger(R.styleable.DragGridLayout_android_gravity, DEFAULT_GRAVITY);

		final int rootViewRes = a.getResourceId(R.styleable.DragGridLayout_root_layout, UNKNOWN);
		if (rootViewRes != UNKNOWN) {
			String resTypeName = getResources().getResourceTypeName(rootViewRes);
			if ("layout".equals(resTypeName)) {
				setRootViewRes(rootViewRes);
			} else if ("id".equals(resTypeName)) {
				mRootViewId = rootViewRes;
			}
		}

		a.recycle();

		init();
	}

	public DragGridLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public DragGridLayout(Context context) {
		super(context);

		init();
	}

	private void init() {
		mHierarchyChangeListener = new HierarchyChangeListenerImpl();
		mGestureListener = new GestureListenerImpl();
		mGestureDetector = new GestureDetectorCompat(getContext(), mGestureListener);

		mGestureDetector.setIsLongpressEnabled(true);
		setLongClickable(true);
		super.setOnHierarchyChangeListener(mHierarchyChangeListener);
		setChildrenDrawingOrderEnabled(true);
		setClipToPadding(false);
	}

	@Override
	public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
		this.mHierarchyChangeListener.listener = listener;
	}

	@Override
	public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new DragGridLayout.LayoutParams(getContext(), attrs);
	}

	// Override to allow type-checking of LayoutParams.
	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return (p != null) && (p instanceof DragGridLayout.LayoutParams);
	}

	@Override
	protected DragGridLayout.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		if (p == null) {
			return new DragGridLayout.LayoutParams();
		} else if (p instanceof MarginLayoutParams) {
			return new DragGridLayout.LayoutParams((MarginLayoutParams) p);
		}
		return new DragGridLayout.LayoutParams(p);
	}

	private void onLongHover() {

	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int count = getChildCount();

		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			if (child.getVisibility() == GONE) {
				continue;
			}

			final int childLeft;
			final int childTop;
			final int childRight;
			final int childBottom;

			final DragGridLayout.LayoutParams lp = (DragGridLayout.LayoutParams) child.getLayoutParams();

			if ((mDragNode != null)
					&& (mDragNode.view == child)) {
				childLeft = mDragNode.currentRect.left;
				childTop = mDragNode.currentRect.top;
				childRight = mDragNode.currentRect.right;
				childBottom = mDragNode.currentRect.bottom;
			} else if (child == mRootView) {
				childLeft = 0;
				childTop = 0;
				childRight = r - l;
				childBottom = b - t;
			} else {
				childLeft = lp.x + lp.leftMargin;
				childTop = lp.y + lp.topMargin;
				childRight = childLeft + child.getMeasuredWidth();
				childBottom = childTop + child.getMeasuredHeight();
			}

			child.layout(childLeft, childTop, childRight, childBottom);
		}
	}

	@Override
	protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
		final int childWidthMeasureSpec;
		final int childHeightMeasureSpec;
		final LayoutParams lp = (LayoutParams) child.getLayoutParams();

		if (mRootView == child) {
			childWidthMeasureSpec = parentWidthMeasureSpec;
			childHeightMeasureSpec = parentHeightMeasureSpec;
		} else if (((mDragNode) != null) && (mDragNode.view == child)) {
			final int width = mDragNode.currentRect.width();
			final int height = mDragNode.currentRect.height();

			final int widthSize = MeasureSpec.getSize(parentWidthMeasureSpec);
			final int heightSize = MeasureSpec.getSize(parentHeightMeasureSpec);

			childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(
					MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), getPaddingLeft() + getPaddingRight(),
					width);
			childHeightMeasureSpec = ViewGroup.getChildMeasureSpec(
					MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY), getPaddingTop() + getPaddingBottom(),
					height);
		} else {
			final int width = (lp.hSize * mCellSize) - (lp.leftMargin + lp.rightMargin);
			final int height = (lp.vSize * mCellSize) - (lp.topMargin + lp.bottomMargin);

			childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft()
					+ getPaddingRight(), width);
			childHeightMeasureSpec = ViewGroup.getChildMeasureSpec(parentHeightMeasureSpec, getPaddingTop()
					+ getPaddingBottom(), height);
		}
		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		measureChildren(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		mCellsRegion.setEmpty();

		mCells.clear();
		mHoveredCells.clear();
		mCellSize = resolveCellSize(w, h);
		if (!(mCellSize > 0)) {
			return;
		}

		int hCellCount = (w - (getPaddingLeft() + getPaddingRight())) / mCellSize;
		int x = getPaddingLeft() + resolveOffset(w - (getPaddingLeft() + getPaddingRight()),
				mCellSize,
				hCellCount,
				mGravity & Gravity.HORIZONTAL_GRAVITY_MASK);
		int vCcellCount = (h - (getPaddingTop() + getPaddingBottom())) / mCellSize;
		int y = getPaddingTop() + resolveOffset(h - (getPaddingTop() + getPaddingBottom()), mCellSize,
				vCcellCount,
				(mGravity & Gravity.VERTICAL_GRAVITY_MASK));

		final Cell[] prevRow = new Cell[hCellCount];
		final Cell[] currRow = new Cell[hCellCount];

		while ((y + mCellSize) < h){
			resolveCell(currRow, prevRow, x, y, mCells, mCellsRegion);
			System.arraycopy(currRow, 0, prevRow, 0, currRow.length);
			y += mCellSize;
		}

		validateChildLayoutParams();
	}



	private int resolveOffset(int size, int cellSize, int cellCount, int gravity) {
		switch (gravity) {
		case Gravity.LEFT:
		case Gravity.TOP:
			return 0;
		case Gravity.RIGHT:
		case Gravity.BOTTOM:
			return Math.max(0, size - (cellCount * cellSize));
		case Gravity.CENTER_HORIZONTAL:
		case Gravity.CENTER_VERTICAL:
		default:
			return Math.max(0, (size - (cellCount * cellSize)) / 2);
		}
	}

	private void resolveCell(Cell[] curr, Cell[] prev, int offsetX, int offsetY, List<Cell> cells, Region cellsRegion) {
		for (int i = 0; i < curr.length; ++i) {
			curr[i] = new Cell(offsetX + (i * mCellSize), offsetY, mCellSize);

			if (((i - 1) != -1) && (curr[i - 1] != null)) {
				curr[i - 1].right = curr[i];
			}

			curr[i].left = (i - 1) != -1 ? curr[i - 1] : null;
			curr[i].top = (prev[i]);
			if (prev[i] != null) {
				prev[i].bottom = curr[i];
			}

			cells.add(curr[i]);
			cellsRegion.union(curr[i].rect);
		}
	}

	private boolean searchNearestCell(Cell outCell, Cell cell, int direction) {
		mTmpRegion.set(mFreeCellRegion);
		if (cell != null) {
			mTmpRegion.op(cell.rect, Op.INTERSECT);
			mTmpRegion.getBounds(mTmpRect);
			if (!mTmpRegion.isEmpty() && mTmpRegion.isRect() && (mTmpRect.width() == cell.rect.width())
					&& (mTmpRect.height() == cell.rect.height())) {

				outCell.setCell(cell, direction);
				return true;
			}
		}
		return false;
	}

	private boolean requestHoveredCells(DragNode node) {
		mHoveredCells.clear();
		if (!mCellsRegion.quickReject(node.currentRect)) {
			mTmpRegion.set(mFreeCellRegion);
			mTmpRegion.op(node.currentRect, Op.INTERSECT);

			final View view = node.view;
			final LayoutParams lp = (LayoutParams) view.getLayoutParams();

			final Rect rect = mTmpRegion.getBounds();
			final Cell cell = findCellUnder(rect);
			if (cell == null) {
				return false;
			}

			final Cell outCell = new Cell();
			outCell.left = cell;
			outCell.top = cell;

			boolean avaliable = true;
			if ((cell != null) && (lp.hSize > 1)) {
				int i = 1;
				while ((i < lp.hSize) && avaliable) {
					if (searchNearestCell(outCell, (outCell.right != null) ? outCell.right : cell.right, Cell.RIGHT)) {
						i++;
						continue;
					}
					if (searchNearestCell(outCell, (outCell.left != cell) ? outCell.left : cell.left, Cell.LEFT)) {
						i++;
						continue;
					}
					avaliable = false;
				}
			}
			if ((cell != null) && (lp.vSize > 1)) {
				int i = 1;
				while ((i < lp.vSize) && avaliable) {
					if (searchNearestCell(outCell, (outCell.bottom != null) ? outCell.bottom : cell.bottom,
							Cell.BOTTOM)) {
						i++;
						continue;
					}
					if (searchNearestCell(outCell, (outCell.top != cell) ? outCell.top : cell.top, Cell.TOP)) {
						i++;
						continue;
					}
					avaliable = false;
				}
			}

			if (outCell.left != null) {
				mHoveredCells.add(outCell.left);
			}
			if (outCell.right != null) {
				mHoveredCells.add(outCell.right);
			}
			if (outCell.bottom != null) {
				mHoveredCells.add(outCell.bottom);
			}
			if (outCell.top != null) {
				mHoveredCells.add(outCell.top);
			}

			return avaliable;
		}
		return false;
	}

	private void requestDrop(DragNode node) {
		if (requestHoveredCells(node) && requestHoverRect(mTmpRect)) {
			mTmpRegion.set(mFreeCellRegion);
			mTmpRegion.op(mTmpRect, Op.INTERSECT);

			final Rect rect = mTmpRegion.getBounds();
			if (mTmpRegion.isRect() && (rect.width() == mTmpRect.width()) && (rect.height() == mTmpRect.height())) {
				node.startRect.offsetTo(mTmpRect.left, mTmpRect.top);
			}
		}
	}

	private boolean requestHoverRect(Rect rect) {
		rect.setEmpty();
		for (final Cell cell : mHoveredCells) {
			if (!rect.isEmpty()) {
				rect.union(cell.rect);
			} else {
				rect.set(cell.rect);
			}
		}
		return !mHoveredCells.isEmpty() && !rect.isEmpty();
	}

	private int resolveCellSize(int w, int h) {
		w = Math.max(0, w - (getPaddingLeft() + getPaddingRight()));
		h = Math.max(0, h - (getPaddingTop() + getPaddingBottom()));

		final int orientation = getResources().getConfiguration().orientation;
		switch (orientation) {
		case Configuration.ORIENTATION_LANDSCAPE:
			return h / mCellCount;
		case Configuration.ORIENTATION_PORTRAIT:
			return w / mCellCount;
		default:
			return Math.max(h, w) /mCellCount;
		}
	}

	private boolean drawChildDrawable(BitmapDrawable childDrawable, Canvas canvas, View child, long drawingTime) {
		canvas.save();
		DragGridLayout.requestRectangle(mTmpRect, child);
		if (child.getAnimation() == null) {
			canvas.clipRect(mTmpRect);
		}

		final boolean result;
		if (mEditMode && (childDrawable != null) && !childDrawable.getBitmap().isRecycled()) {
			childDrawable.setBounds(mTmpRect);
			childDrawable.draw(canvas);
			result = false;
		} else {
			result = super.drawChild(canvas, child, drawingTime);
		}
		canvas.restore();
		return result;
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		if (mRootView == null) {
			drawCellGrid(canvas);
		}
		super.dispatchDraw(canvas);
	}

	@Override
	protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
		if (child == mRootView) {
			final boolean result = drawChildDrawable(mRootViewDrawable, canvas, child, drawingTime);
			drawCellGrid(canvas);
			return result;
		} else if (mWidgetVisibility) {
			if ((mDragNode != null) && (mDragNode.view == child)) {
				drawHighlight(child, canvas);
				if (mDragNode.view.getAnimation() != null) {
					return super.drawChild(canvas, child, drawingTime);
				}

				if ((mDragNode != null) && (mDragNode.viewDrawable == null)) {
					mDragNode.viewDrawable = DragGridLayout.createDrawingCache(mDragNode.view);
				}
				return drawChildDrawable(mDragNode != null ? mDragNode.viewDrawable : null, canvas, child, drawingTime);
			} else {
				return super.drawChild(canvas, child, drawingTime);
			}
		}
		return false;
	}

	@Override
	protected int getChildDrawingOrder(int childCount, int i) {
		if ((mRootView != null) && (indexOfChild(mRootView) != 0)) {
			throw new IllegalStateException("Root vie)w index shoudl be 0");
		}
		if (mDragNode != null) {
			final int index = indexOfChild(mDragNode.view);
			if (i < index) {
				return i;
			} else if (i < (childCount - 1)) {
				return i + 1;
			} else {
				return index;
			}
		}
		return i;
	}

	private void drawHighlight(final View child, final Canvas canvas) {
		if ((mHighlightDrawable != null) && requestHoverRect(mTmpRect)) {
			final int[] setState;

			mTmpRegion.set(mFreeCellRegion);
			mTmpRegion.op(mTmpRect, Op.INTERSECT);

			mTmpRegion.getBounds(mTmpRect);
			if (mTmpRegion.isRect() && (child.getWidth() <= mTmpRect.width()) && (child.getHeight() <= mTmpRect.height())) {
				setState = new int[] { R.attr.state_drop_allow };
			} else {
				setState = new int[] { -R.attr.state_drop_allow };
			}

			mHighlightDrawable.setState(setState);

			canvas.save();
			canvas.clipRect(mTmpRect);
			mHighlightDrawable.setBounds(mTmpRect);
			mHighlightDrawable.draw(canvas);
			canvas.restore();

			if (mDebugMode){
				mPaint.setStyle(Style.FILL);

				mPaint.setColor(0x660000cc);
				canvas.drawPath(mCellsRegion.getBoundaryPath(), mPaint);

				mPaint.setColor(0x66cc0000);
				canvas.drawPath(mFreeCellRegion.getBoundaryPath(), mPaint);

				mPaint.setColor(0x7700cc00);
				canvas.drawPath(mTmpRegion.getBoundaryPath(), mPaint);
			}

		}
	}

	private void drawCellGrid(Canvas canvas) {
		if (mCellDrawable != null) {
			int i = 0;
			for (final Cell cell : mCells) {
				mTmpRect.set(cell.rect);

				final int[] stateSet = new int[] { (mEditMode ? 1 : -1) * R.attr.state_editing,
						(mPressedCell == cell ? 1 : -1) * android.R.attr.state_pressed };

				canvas.save();
				canvas.clipRect(cell.rect);
				mCellDrawable.setState(stateSet);
				mCellDrawable.setBounds(cell.rect);
				mCellDrawable.draw(canvas);

				if (mDebugMode) {
					mPaint.setTextAlign(Align.CENTER);
					mPaint.setTextSize(30);
					mPaint.setTypeface(Typeface.DEFAULT_BOLD);
					mPaint.setColor(Color.GREEN);
					canvas.drawText(Integer.toString(i), cell.rect.centerX(), cell.rect.centerY(), mPaint);
					++i;
				}

				canvas.restore();
			}
		}

		if (mDebugMode) {
			mPaint.setColor(Color.GREEN);
			mPaint.setStyle(Style.STROKE);
			mPaint.setStrokeWidth(1.5f);
			canvas.drawPath(mCellsRegion.getBoundaryPath(), mPaint);
		}
	}

	private static void requestRectangle(Rect outRect, View view) {
		if ((outRect == null) || (view == null)) {
			return;
		}

		outRect.left = view.getLeft();
		outRect.top = view.getTop();
		outRect.right = view.getRight();
		outRect.bottom = view.getBottom();
	}

	private View findIntersectChild(float x, float y) {
		final int count = getChildCount();
		for (int i = count - 1; i >= 0; --i) {
			final View child = getChildAt(i);
			if (child == mRootView) {
				continue;
			}

			DragGridLayout.requestRectangle(mTmpRect, child);
			if (mTmpRect.contains((int) x, (int) y)) {
				return child;
			}
		}
		return null;
	}

	public boolean isEditMode() {
		return mEditMode;
	}

	private Rect requestChildViewRect(Rect outRect, View view) {
		if (outRect == null) {
			outRect = new Rect();
		}

		final LayoutParams lp = (LayoutParams) view.getLayoutParams();

		outRect.left = lp.x;
		outRect.top = lp.y;
		outRect.right = outRect.left + (mCellSize * lp.hSize);
		outRect.bottom = outRect.top + (mCellSize * lp.vSize);

		return outRect;
	}

	private void requestFreeCellRegion(View view) {
		mFreeCellRegion.set(mCellsRegion);
		final int count = getChildCount();
		for (int i = count - 1; i >= 0; --i) {
			final View child = getChildAt(i);
			if ((child == mRootView) || (child == view)) {
				continue;
			}

			mFreeCellRegion.op(requestChildViewRect(mTmpRect, child), Op.DIFFERENCE);
		}
	}

	private void stopAnimation(View view) {
		if ((view != null) && (view.getAnimation() != null)) {
			view.getAnimation().cancel();
			view.setAnimation(null);
			invalidate();
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (mEditMode) {
			return true;
		}
		return super.onInterceptTouchEvent(ev);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		final float x = ev.getX();
		final float y = ev.getY();
		if (mGestureDetector.onTouchEvent(ev)) {
			mPrevX = x;
			mPrevY = y;
			return true;
		}

		switch (ev.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			if (mEditMode) {
				final View child = findIntersectChild(x, y);
				if (child != null) {
					if ((mDragNode != null) && (mDragNode.view != child)) {
						return true;
					}
					mPrevX = x;
					mPrevY = y;

					stopAnimation(mDragNode != null ? mDragNode.view : null);

					mDragNode = new DragNode(child);
					DragNode.scale(mDragNode.currentRect, mScaleFactor);

					requestFreeCellRegion(child);
					requestHoveredCells(mDragNode);

					if (mDragListener != null) {
						mDragListener.onDrag(mDragNode.view, this);
					}

					final Animation animation = new ScaleAnimation((1f - mScaleFactor) + 1f, 1f, (1f - mScaleFactor) + 1f, 1f,
							Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

					animation.setDuration(DURATION);
					animation.setAnimationListener(new AbstractAnimationListener() {
						@Override
						public void onAnimationEnd(Animation animation) {
							mTmpRect.set(mDragNode.startRect);

							child.setAnimation(null);
							child.requestLayout();
							invalidate(mTmpRect);
						}
					});

					requestLayout();
					child.startAnimation(animation);

					return true;
				}

				final Cell cell = findCellUnder(x, y);
				if (cell != null) {
					mPressedCell = cell;
					invalidate();
					return true;
				}

				mEditModeSwitchOff = true;
				return true;
			}
			break;
		case MotionEvent.ACTION_MOVE:
			if (mPressedCell != null) {
				final Cell cell = findCellUnder(x, y);
				if (mPressedCell != cell) {
					mPressedCell = null;
					invalidate();
					return true;
				}
			} else if (mDragNode != null) {
				mDragNode.currentRect.offset((int) (x - mPrevX), (int) (y - mPrevY));
				if (mDebugMode) {
					Log.e(VIEW_LOG_TAG, "x=" + x + " y=" + y + " prevX=" + mPrevX + " prevY=" + mPrevY + " dX="
							+ (x - mPrevX) + " dY=" + (y - mPrevY));
				}

				if (!requestHoveredCells(mDragNode) && !mHoveredCells.isEmpty()
						&& (Math.abs(x - mPrevX) < DELTA) && (Math.abs(y - mPrevY) < DELTA)){
					if (!mLoongHoveredRequested){
						mLoongHoveredRequested = true;
						mHandler.postDelayed(mLongHoverDispatcher,  LONGPRESS_TIMEOUT+TAP_TIMEOUT);
					}
				}else{
					mLoongHoveredRequested = false;
					mHandler.removeCallbacks(mLongHoverDispatcher);
				}

				mPrevX = x;
				mPrevY = y;

				requestLayout();
				invalidate();
				return true;
			}
			break;
		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP:
			mLoongHoveredRequested = false;
			mHandler.removeCallbacks(mLongHoverDispatcher);
			if (mPressedCell != null) {
				if (mCellClickListener != null) {
					mCellClickListener.onClick(new Point(mPressedCell.rect.left, mPressedCell.rect.top) , this);
				}
				mPressedCell = null;
				invalidate();
				return true;
			} else if (mDragNode != null) {
				mDragNode.currentRect.offset((int) (x - mPrevX), (int) (y - mPrevY));
				requestDrop(mDragNode);

				mPrevX = x;
				mPrevY = y;

				mTmpRect.set(mDragNode.currentRect);
				mTmpRect.union(mDragNode.startRect);

				final View child = mDragNode.view;
				final LayoutParams lp = (LayoutParams) child.getLayoutParams();

				lp.x = mDragNode.startRect.left;
				lp.y = mDragNode.startRect.top;

				mDragNode.startRect.offset(lp.leftMargin, lp.topMargin);

				final AnimationSet animation = new AnimationSet(true);
				animation.addAnimation(new TranslateAnimation(mDragNode.currentRect.left - mDragNode.startRect.left, 0,
						mDragNode.currentRect.top - mDragNode.startRect.top, 0));
				animation.addAnimation(new ScaleAnimation(mScaleFactor, 1f, mScaleFactor, 1f));

				animation.setDuration(DURATION);
				animation.setAnimationListener(new AbstractAnimationListener() {
					@Override
					public void onAnimationEnd(final Animation a) {
						mDragNode.dispose();
						mDragNode = null;

						child.setAnimation(null);

						requestLayout();
						invalidate();
					}
				});

				if (mDragListener != null) {
					mDragListener.onDrop(mDragNode.view, this);
				}

				mDragNode.currentRect.set(mDragNode.startRect);

				child.requestLayout();
				child.startAnimation(animation);
				invalidate(mTmpRect);
				return true;
			} else if (mEditModeSwitchOff) {
				setEditMode(false);
				mEditModeSwitchOff = false;
				return true;
			}
			break;
		}

		return super.onTouchEvent(ev);
	}

	private Cell findCellUnder(float x, float y) {
		final Rect rect = new Rect((int) x, (int) y, Math.round(x), Math.round(y));
		return findCellUnder(rect);
	}

	private Cell findCellUnder(Rect rect) {
		Cell resCell = null;
		final Rect resRect = new Rect();
		for (final Cell cell : mCells) {
			if (mTmpRect.setIntersect(cell.rect, rect)
					&& ((resCell == null) || (resRect.left > mTmpRect.left) || (resRect.top > mTmpRect.top))) {
				mTmpRegion.set(mFreeCellRegion);
				resRect.set(mTmpRect);
				resCell = cell;
			}
		}

		return resCell;
	}

	public void setGestureListener(OnGestureListener listener) {
		mGestureListener.gestureListener = listener;
	}

	@Override
	public View getRootView() {
		return mRootView;
	}

	public void setRootViewRes(int viewId) {
		setRootView(View.inflate(getContext(), viewId, null));
	}

	public void setRootView(View view) {
		if (view != mRootView) {
			removeRootView();
		}
		mRootView = view;
		addView(mRootView, 0, new LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.MATCH_PARENT));
	}

	@Override
	public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
		if ((mRootViewId != UNKNOWN) && (child.getId() == mRootViewId)) {
			if (mRootView == child) {
				return;
			}

			if (mRootView != null) {
				mRootView.setAnimation(null);
				removeView(mRootView);
			}
			mRootView = child;
			index = 0;
		}
		super.addView(child, index, validateLayoutParams(params));
	}

	public void removeRootView() {
		if (mRootView != null) {
			super.removeView(mRootView);
		}
	}

	public void setEditMode(boolean value) {
		if (mEditMode == value) {
			return;
		}

		mEditMode = value;
		if (mRootView != null) {
			if (mEditMode) {
				mRootViewDrawable = DragGridLayout.createDrawingCache(mRootView);
			} else if (mRootViewDrawable != null) {
				mRootViewDrawable.getBitmap().recycle();
				mRootViewDrawable = null;
			}
			invalidate();
		}
	}

	@SuppressWarnings("deprecation")
	private static BitmapDrawable createDrawingCache(View view) {
		BitmapDrawable drawable = null;
		view.buildDrawingCache();
		final Bitmap bitmap = view.getDrawingCache();
		if (bitmap != null) {
			drawable = new BitmapDrawable(Bitmap.createBitmap(bitmap));
			bitmap.recycle();
		}
		view.destroyDrawingCache();
		return drawable;
	}

	@Override
	protected void onDetachedFromWindow() {
		if (mRootViewDrawable != null) {
			mRootViewDrawable.getBitmap().recycle();
			mRootViewDrawable = null;
		}
		mLoongHoveredRequested = false;
		mHandler.removeCallbacks(mLongHoverDispatcher);
		super.onDetachedFromWindow();
	}

	public List<Cell> getCells() {
		return mCells;
	}

	public void setDragListener(OnViewDragListener dragListener) {
		this.mDragListener = dragListener;
	}

	public void setDragScaleCoefficient(float scale) {
		this.mScaleFactor = scale;
	}

	public Drawable getHighlightDrawable() {
		return mHighlightDrawable;
	}

	public void setHighlightDrawable(Drawable highlightDrawable) {
		this.mHighlightDrawable = highlightDrawable;
	}

	public void setHighlightResource(int highlightRes) {
		setHighlightDrawable(getResources().getDrawable(highlightRes));
	}

	public void setCellDrawable(Drawable cellDrawable) {
		this.mCellDrawable = cellDrawable;
	}

	public void setCellResource(int cellRes) {
		setCellDrawable(getResources().getDrawable(cellRes));
	}

	public boolean isDebugMode() {
		return mDebugMode;
	}

	public void setDebugMode(boolean debugMode) {
		if (this.mDebugMode != debugMode) {
			this.mDebugMode = debugMode;
			invalidate();
		}
	}

	private void validateChildLayoutParams() {
		boolean needRequestLayout = false;
		final int size = getChildCount();
		for (int i = 0; i < size; ++i) {
			final View child = getChildAt(i);
			if ((child != mRootView) &&
					(child.getVisibility() != GONE)) {
				LayoutParams lp = (LayoutParams) child.getLayoutParams();
				if ((lp.x == UNKNOWN) || (lp.y == UNKNOWN)){
					child.setLayoutParams(validateLayoutParams(lp));
					needRequestLayout = true;
				}
			}
		}

		if (needRequestLayout){
			requestLayout();
		}
	}

	private LayoutParams validateLayoutParams(ViewGroup.LayoutParams srcLp) {
		final LayoutParams lp;
		if (checkLayoutParams(srcLp)) {
			lp = (LayoutParams) srcLp;
		} else {
			lp = generateLayoutParams(srcLp);
		}

		if (mCells.isEmpty()){
			return lp;
		}

		if (checkIsSpaceFree(lp)){
			return lp;
		}

		final Cell cell = findFreeCell(lp.vSize, lp.hSize);
		if (cell != null) {
			lp.x = cell.rect.left;
			lp.y = cell.rect.top;
		}
		return lp;
	}

	private boolean checkIsSpaceFree(LayoutParams lp){
		final Rect rect = new Rect(lp.leftMargin,
				lp.topMargin,
				(lp.hSize * mCellSize)-lp.rightMargin,
				(lp.vSize * mCellSize)-lp.bottomMargin);
		rect.offset(lp.x, lp.y);

		requestFreeCellRegion(null);

		mTmpRegion.set(mFreeCellRegion);
		mTmpRegion.op(rect, Op.INTERSECT);
		if (!mTmpRegion.isEmpty() && mTmpRegion.isRect()) {
			mTmpRegion.getBounds(mTmpRect);
			return ((mTmpRect.width() == rect.width()) && (mTmpRect.height() == rect.height()));
		}
		return false;
	}

	public Cell findFreeCell(int rows, int cols) {
		final Rect boundRect = new Rect();
		final Rect rect = new Rect(0, 0, cols * mCellSize, rows * mCellSize);

		requestFreeCellRegion(null);

		Cell resCell = null;
		for (final Cell cell : mCells) {
			mTmpRegion.set(mFreeCellRegion);
			rect.offsetTo(cell.rect.left, cell.rect.top);

			mTmpRegion.op(rect, Op.INTERSECT);
			if (!mTmpRegion.isEmpty() && mTmpRegion.isRect()) {
				mTmpRegion.getBounds(boundRect);
				if ((boundRect.width() == rect.width()) && (boundRect.height() == rect.height())) {
					if ((resCell == null) ||
							(cell.rect.top < resCell.rect.top) ||
							((cell.rect.left < resCell.rect.left) && (cell.rect.top == resCell.rect.top))) {
						resCell = cell;
					}
				}
			}
		}

		return resCell;
	}

	public void setCellClickListener(OnCellClickListener cellClickListener) {
		this.mCellClickListener = cellClickListener;
	}

	public void setRemoveListener(OnViewRemoveListener removeListener) {
		this.mRemoveListener = removeListener;
	}

	public boolean isShowWidget() {
		return mWidgetVisibility;
	}

	public void setShowWidget(boolean showWidget) {
		if (this.mWidgetVisibility != showWidget) {
			this.mWidgetVisibility = showWidget;
			invalidate();
		}
	}

	public static class LayoutParams extends MarginLayoutParams {
		int x= UNKNOWN;
		int y= UNKNOWN;

		int vSize = 1;
		int hSize = 1;

		boolean animation;

		@Override
		public String toString() {
			return String.format(Locale.ENGLISH, "a=%s [x=%d;y=%d] [h=%d;v=%d]", Boolean.toString(animation), x, y,
					hSize, vSize);
		}

		LayoutParams() {
			super(MATCH_PARENT, MATCH_PARENT);
		}

		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);
			final TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.DragGridLayout);

			vSize = a.getInt(R.styleable.DragGridLayout_vertical_size, 1);
			hSize = a.getInt(R.styleable.DragGridLayout_horizontal_size, 1);

			a.recycle();
		}

		public void setPosition(Point point){
			x = point.x;
			y = point.y;
		}

		public Point getPosition(){
			return new Point(x, y);
		}

		public LayoutParams(int width, int height) {
			super(width, height);
		}

		public LayoutParams(LayoutParams source) {
			super(source);

			x = source.x;
			y = source.y;
			vSize = source.vSize;
			hSize = source.hSize;
		}

		public LayoutParams(MarginLayoutParams source) {
			super(source);
		}

		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}
	}

	private final class GestureListenerImpl implements OnGestureListener {
		OnGestureListener gestureListener;

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			if ((gestureListener != null) && gestureListener.onSingleTapUp(e)) {
				return true;
			}
			return false;
		}

		@Override
		public void onShowPress(MotionEvent e) {
			if (gestureListener != null) {
				gestureListener.onShowPress(e);
			}
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			if ((gestureListener != null) && gestureListener.onScroll(e1, e2, distanceX, distanceY)) {
				return true;
			}
			return false;
		}

		@Override
		public void onLongPress(MotionEvent ev) {
			if (!mEditMode) {
				final View child = findIntersectChild(ev.getX(), ev.getY());
				if (child != null) {
					setEditMode(true);
				}
			}
			if (gestureListener != null) {
				gestureListener.onLongPress(ev);
			}
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			Log.e(VIEW_LOG_TAG, "velocityX="+velocityX+" velocityY="+velocityY);
			if ((mDragNode != null) && (Math.abs(velocityY) > 1000)){
				final View view = mDragNode.view;
				final Animation animation = new TranslateAnimation(0, 0, 0, (velocityY > 0 ? 1 : -1) * getMeasuredHeight());

				animation.setDuration(DURATION);
				animation.setInterpolator(getContext(), android.R.anim.accelerate_interpolator);
				animation.setAnimationListener(new AbstractAnimationListener() {
					@Override
					public void onAnimationEnd(Animation animation) {
						mDragNode = null;

						view.setAnimation(null);
						removeView(view);
					}
				});

				view.startAnimation(animation);
				return true;
			}

			if ((gestureListener != null) && gestureListener.onFling(e1, e2, velocityX, velocityY)) {
				return true;
			}
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			if ((gestureListener != null) && gestureListener.onDown(e)) {
				return true;
			}
			return false;
		}
	}

	private class HierarchyChangeListenerImpl implements OnHierarchyChangeListener, OnLongClickListener {
		private OnHierarchyChangeListener listener;

		@Override
		public void onChildViewAdded(View parent, View child) {
			// generates an id if it's missing
			if (child.getId() == View.NO_ID) {
				child.setId(child.hashCode());
			}

			if (child != mRootView) {
				child.setOnLongClickListener(this);
			}
			if (listener != null) {
				listener.onChildViewAdded(parent, child);
			}
		}

		@Override
		public void onChildViewRemoved(View parent, View child) {
			child.setOnLongClickListener(null);
			child.setOnTouchListener(null);

			if ((mRemoveListener != null) && (child != mRootView)){
				mRemoveListener.onRemove(child, DragGridLayout.this);
			}

			if (listener != null) {
				listener.onChildViewRemoved(parent, child);
			}
		}

		@Override
		public boolean onLongClick(View child) {
			if ((child != null) && (child != mRootView) && !mEditMode) {
				setEditMode(true);
				return true;
			}
			return false;
		}
	}

	static class Cell {
		public final static int LEFT = 1;
		public final static int RIGHT = 2;
		public final static int TOP = 4;
		public final static int BOTTOM = 8;

		public final Rect rect;

		Cell left;

		Cell top;

		Cell right;

		Cell bottom;

		Cell() {
			rect = new Rect();
		}

		Cell(int x, int y, int s) {
			rect = new Rect(x, y, x + s, y + s);
		}

		@Override
		public int hashCode() {
			return (rect.top << 12) | rect.left;
		}

		public Cell getLeft() {
			return left;
		}

		public Cell getTop() {
			return top;
		}

		public Cell getRight() {
			return right;
		}

		public Cell getBottom() {
			return bottom;
		}

		public void setCell(Cell cell, int direction) {
			switch (direction) {
			case Cell.LEFT:
				left = cell;
				break;
			case Cell.TOP:
				top = cell;
				break;
			case Cell.RIGHT:
				right = cell;
				break;
			case Cell.BOTTOM:
				bottom = cell;
				break;
			}
		}
	}

	static class DragNode {
		Rect startRect = new Rect();
		Rect currentRect = new Rect();
		View view;
		BitmapDrawable viewDrawable;

		DragNode(View view) {
			this.view = view;
			this.viewDrawable = DragGridLayout.createDrawingCache(view);

			final LayoutParams lp = (LayoutParams) view.getLayoutParams();

			DragGridLayout.requestRectangle(startRect, view);
			startRect.offsetTo(lp.x, lp.y);

			DragGridLayout.requestRectangle(currentRect, view);
		}

		public static void scale(Rect rect, float scale) {
			final float dw = rect.width() * (1 - scale) * 0.5f;
			final float dh = rect.height() * (1 - scale) * 0.5f;

			rect.left += dw;
			rect.top += dh;
			rect.right -= dw;
			rect.bottom -= dh;
		}

		@Override
		public int hashCode() {
			return view.hashCode();
		}

		public void dispose() {
			if (viewDrawable != null) {
				viewDrawable.getBitmap().recycle();
				viewDrawable = null;
			}
		}
	}

	public interface OnViewRemoveListener {
		void onRemove(View view, DragGridLayout parent);
	}

	public interface OnViewDragListener {
		void onDrag(View view, DragGridLayout parent);

		void onDrop(View view, DragGridLayout parent);
	}

	public interface OnCellClickListener {
		void onClick(Point point, DragGridLayout parent);
	}
}