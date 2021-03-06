/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authored by Anton Kuhlevskyi <anton.kuhleskiy@gmail.com>
 */
package org.androfarsh.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
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
import android.view.animation.Animation.AnimationListener;

public class DragGridLayout extends ViewGroup {
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

		public Cell getBottom() {
			return bottom;
		}

		public Cell getLeft() {
			return left;
		}

		public Cell getRight() {
			return right;
		}

		public Cell getTop() {
			return top;
		}

		@Override
		public int hashCode() {
			return (rect.top << 12) | rect.left;
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

		@Override
		public String toString() {
			return rect.toString();
		}
	}

	private final class GestureListenerImpl implements OnGestureListener {
		OnGestureListener gestureListener;

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			Log.e(VIEW_LOG_TAG, "velocityX=" + velocityX + " velocityY="
					+ velocityY);
			if ((mDragNode != null) && (Math.abs(velocityY) > 1000)) {
				final View view = mDragNode.view;
				final Animation animation = new TranslateAnimation(0, 0, 0,
						(velocityY > 0 ? 1 : -1) * getMeasuredHeight());

				animation.setDuration(DURATION);
				animation.setInterpolator(getContext(),
						android.R.anim.accelerate_interpolator);
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

			if ((gestureListener != null)
					&& gestureListener.onFling(e1, e2, velocityX, velocityY)) {
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
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
				float distanceX, float distanceY) {
			if ((gestureListener != null)
					&& gestureListener.onScroll(e1, e2, distanceX, distanceY)) {
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
		public boolean onSingleTapUp(MotionEvent e) {
			if ((gestureListener != null) && gestureListener.onSingleTapUp(e)) {
				return true;
			}
			return false;
		}
	}

	private class HierarchyChangeListenerImpl implements
	OnHierarchyChangeListener, OnLongClickListener {
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

			if ((mRemoveListener != null) && (child != mRootView)) {
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

	public static class LayoutParams extends MarginLayoutParams {
		int mX = UNKNOWN;
		int mY = UNKNOWN;

		int mVerticalSize = 1;
		int mHorizontalSize = 1;

		public LayoutParams() {
			super(MATCH_PARENT, MATCH_PARENT);
		}

		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);
			final TypedArray a = c.obtainStyledAttributes(attrs,
					R.styleable.DragGridLayout);

			mVerticalSize = a.getInt(R.styleable.DragGridLayout_vertical_size, 1);
			mHorizontalSize = a.getInt(R.styleable.DragGridLayout_horizontal_size, 1);

			a.recycle();
		}

		public LayoutParams(int width, int height) {
			super(width, height);
		}
		
		LayoutParams(int x, int y, int width, int height) {
			super(width, height);

			this.mX = x;
			this.mY = y;
		}

		public LayoutParams(LayoutParams source) {
			super(source);

			mX = source.mX;
			mY = source.mY;
			mVerticalSize = source.mVerticalSize;
			mHorizontalSize = source.mHorizontalSize;
		}

		public LayoutParams(MarginLayoutParams source) {
			super(source);
		}

		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}

		public Point getPosition() {
			return new Point(mX, mY);
		}

		public LayoutParams setPosition(Point point) {
			mX = point.x;
			mY = point.y;
			return this;
		}
		
		public LayoutParams setPosition(int pX, int pY){
			mX = pX;
			mY = pY;
			return this;
		}
		
		public LayoutParams setHorizontalSize(int horizontalSize) {
			mHorizontalSize = horizontalSize;
			return this;
		}
		
		public int getHorizontalSize() {
			return mHorizontalSize;
		}
		
		public LayoutParams setVerticalSize(int verticalSize) {
			mVerticalSize = verticalSize;
			return this;
		}
		
		public int getVerticalSize() {
			return mHorizontalSize;
		}
		
		@Override
		public String toString() {
			return String.format(Locale.ENGLISH,
					"[x=%d;y=%d] [h=%d;v=%d]", mX, mY, mHorizontalSize, mVerticalSize);
		}
	}

	static class Node {
		public static void scale(Rect rect, float scale) {
			final float dw = rect.width() * (1 - scale) * 0.5f;
			final float dh = rect.height() * (1 - scale) * 0.5f;

			rect.left += dw;
			rect.top += dh;
			rect.right -= dw;
			rect.bottom -= dh;
		}

		Rect startRect = new Rect();
		Rect currentRect = new Rect();
		View view;

		BitmapDrawable viewDrawable;

		Node(View view, Rect rect) {
			this.view = view;
			this.viewDrawable = DragGridLayout.createDrawingCache(view);

			startRect.set(rect);
			currentRect.set(rect);
		}

		public void dispose() {
			if (viewDrawable != null) {
				viewDrawable.getBitmap().recycle();
				viewDrawable = null;
			}
		}

		@Override
		public int hashCode() {
			return view.hashCode();
		}
	}
	
	static abstract class AbstractAnimationListener implements AnimationListener {

		@Override
		public void onAnimationStart(Animation animation) {}

		@Override
		public void onAnimationRepeat(Animation animation) {}

	}


	public interface OnCellClickListener {
		void onClick(Point point, DragGridLayout parent);
	}

	public interface OnViewDragListener {
		void onDrag(View view, DragGridLayout parent);

		void onDrop(View view, DragGridLayout parent);
	}

	public interface OnViewRemoveListener {
		void onRemove(View view, DragGridLayout parent);
	}

	private static final int LONGPRESS_MESSAGE = 1;

	private static final int LONGHOVER_MESSAGE = 2;

	private static final int LONGPRESS_TIMEOUT = ViewConfiguration
			.getLongPressTimeout();

	private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();

	private static final int UNKNOWN = -1;

	private static final int DELTA = 5;

	private static final int DURATION = 200;

	private static final int DEFAULT_CELL_COUNT = 4;

	private static final int DEFAULT_GRAVITY = Gravity.CENTER;

	private static final float SCALE_FACTOR = 0.8f;

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

	private boolean mEditMode;

	private View mRootView;

	private final Set<Cell> mHoveredCells = new HashSet<Cell>();

	private final List<Cell> mCells = new ArrayList<Cell>();

	private int mCellSize;

	private GestureListenerImpl mGestureListener;

	private GestureDetectorCompat mGestureDetector;

	private final Region mCellsRegion = new Region();

	private final Region mFreeCellsRegion = new Region();

	private final Region mTmpRegion = new Region();

	private final Rect mTmpRect = new Rect();

	private Drawable mHighlightDrawable;

	private Drawable mCellDrawable;

	private float mPrevX;

	private float mPrevY;

	private Node mDragNode;

	private OnViewDragListener mDragListener;

	private float mScaleFactor = SCALE_FACTOR;

	private boolean mDebugMode;

	private HierarchyChangeListenerImpl mHierarchyChangeListener;
	private BitmapDrawable mRootViewDrawable;

	private OnCellClickListener mCellClickListener;

	private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	@SuppressLint("HandlerLeak")
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case LONGHOVER_MESSAGE:
				requestReorder();
				mLoongHoveredRequested = false;
				break;
			case LONGPRESS_MESSAGE:
				// TODO
				break;
			}
			super.handleMessage(msg);
		}
	};

	private boolean mWidgetVisibility = true;

	private Cell mPressedCell;

	private boolean mEditModeSwitchOff;

	private int mRootViewId = UNKNOWN;

	private OnViewRemoveListener mRemoveListener;

	private boolean mLoongHoveredRequested;

	private int mCellCount = DEFAULT_CELL_COUNT;

	private int mGravity = DEFAULT_GRAVITY;

	private final Set<Node> mNodes = new HashSet<Node>();

	public DragGridLayout(Context context) {
		super(context);

		init();
	}

	public DragGridLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public DragGridLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		final TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.DragGridLayout, defStyle, 0);

		mDebugMode = a.getBoolean(R.styleable.DragGridLayout_debug_mode,
				mDebugMode);
		mCellDrawable = a.getDrawable(R.styleable.DragGridLayout_cell_drawable);
		mHighlightDrawable = a
				.getDrawable(R.styleable.DragGridLayout_highlight_drawable);
		setCellCount(a.getInteger(R.styleable.DragGridLayout_cell_count,
				DEFAULT_CELL_COUNT));
		mGravity = a.getInteger(R.styleable.DragGridLayout_android_gravity,
				DEFAULT_GRAVITY);

		final int rootViewRes = a.getResourceId(
				R.styleable.DragGridLayout_root_layout, UNKNOWN);
		if (rootViewRes != UNKNOWN) {
			String resTypeName = getResources()
					.getResourceTypeName(rootViewRes);
			if ("layout".equals(resTypeName)) {
				setRootViewRes(rootViewRes);
			} else if ("id".equals(resTypeName)) {
				mRootViewId = rootViewRes;
			}
		}

		a.recycle();

		init();
	}

	@Override
	public void addView(View child, int index,
			android.view.ViewGroup.LayoutParams params) {
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

		final LayoutParams lp = validateLayoutParams(params);
		if (mRootView == child) {
			lp.mX = 0;
			lp.mY = 0;
		}
		super.addView(child, index, lp);
	}

	// Override to allow type-checking of LayoutParams.
	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return (p != null) && (p instanceof DragGridLayout.LayoutParams);
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		if (mDebugMode) {
			mPaint.setStyle(Style.FILL_AND_STROKE);
			final Path path = new Path();

			path.reset();
			mCellsRegion.getBoundaryPath(path);
			path.close();
			mPaint.setColor(0x660000cc);
			canvas.drawPath(path, mPaint);

			path.reset();
			mFreeCellsRegion.getBoundaryPath(path);
			path.close();
			mPaint.setColor(0x66cc0000);
			canvas.drawPath(path, mPaint);
		}

		if (mRootView == null) {
			drawCellGrid(canvas);
		}
		super.dispatchDraw(canvas);
	}

	private void drawCellGrid(Canvas canvas) {
		if (mCellDrawable != null) {
			int i = 0;
			for (final Cell cell : mCells) {
				mTmpRect.set(cell.rect);

				final int[] stateSet = new int[] {
						(mEditMode ? 1 : -1) * R.attr.state_editing,
						(mPressedCell == cell ? 1 : -1)
						* android.R.attr.state_pressed };

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
					canvas.drawText(Integer.toString(i), cell.rect.centerX(),
							cell.rect.centerY(), mPaint);
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

	@Override
	protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
		if (child == mRootView) {
			final boolean result = drawChildDrawable(mRootViewDrawable, canvas,
					child, drawingTime);
			drawCellGrid(canvas);
			return result;
		} else if (mWidgetVisibility) {
			if ((mDragNode != null) && (mDragNode.view == child)) {
				drawHighlight(child, canvas);
				if (mDragNode.view.getAnimation() != null) {
					return super.drawChild(canvas, child, drawingTime);
				}

				if ((mDragNode != null) && (mDragNode.viewDrawable == null)) {
					mDragNode.viewDrawable = DragGridLayout
							.createDrawingCache(mDragNode.view);
				}
				return drawChildDrawable(
						mDragNode != null ? mDragNode.viewDrawable : null,
								canvas, child, drawingTime);
			} else {
				return super.drawChild(canvas, child, drawingTime);
			}
		}
		return false;
	}

	private boolean drawChildDrawable(BitmapDrawable childDrawable,
			Canvas canvas, View child, long drawingTime) {
		canvas.save();
		requestCurrentRect(mTmpRect, child);
		if (child.getAnimation() == null) {
			canvas.clipRect(mTmpRect);
		}

		final boolean result;
		if (mEditMode && (childDrawable != null)
				&& !childDrawable.getBitmap().isRecycled()) {
			childDrawable.setBounds(mTmpRect);
			childDrawable.draw(canvas);
			result = false;
		} else {
			result = super.drawChild(canvas, child, drawingTime);
		}
		canvas.restore();
		return result;
	}

	private void drawHighlight(final View child, final Canvas canvas) {
		if ((mHighlightDrawable != null)) {
			requestFreeCellRegion(mDragNode.view);
			if (!requestHoverRect(mTmpRect)) {
				return;
			}
			canvas.save();
			canvas.clipRect(mTmpRect);
			mHighlightDrawable.setBounds(mTmpRect);

			mTmpRegion.set(mFreeCellsRegion);
			mTmpRegion.op(mTmpRect, Op.INTERSECT);
			mTmpRegion.getBounds(mTmpRect);

			final boolean allowed = mTmpRegion.isRect()
					&& (child.getWidth() <= mTmpRect.width())
					&& (child.getHeight() <= mTmpRect.height());
			final int[] stateSet = new int[] { (allowed ? 1 : -1)
					* R.attr.state_drop_allow };

			mHighlightDrawable.setState(stateSet);
			mHighlightDrawable.draw(canvas);
			canvas.restore();
		}
	}

	private void fillHoveredCells(Cell startCell, int vDir, int hDir,
			int vSize, int hSize) {
		Cell rowCell = startCell;
		for (int i = 0; i < vSize; ++i) {
			Cell colCell = rowCell;
			for (int j = 0; j < hSize; ++j) {
				mHoveredCells.add(colCell);
				colCell = (hDir > 0) ? rowCell.right : rowCell.left;
				if (colCell == null) {
					break;
				}
			}
			rowCell = (vDir > 0) ? rowCell.bottom : rowCell.top;
			if (rowCell == null) {
				break;
			}
		}
	}

	private Cell findCellUnder(float x, float y) {
		final Rect rect = new Rect((int) x, (int) y, Math.round(x),
				Math.round(y));
		return findCellUnder(rect);
	}

	private Cell findCellUnder(Rect rect) {
		Cell resCell = null;
		final Rect resRect = new Rect();
		for (final Cell cell : mCells) {
			if (mTmpRect.setIntersect(cell.rect, rect)) {
				if ((resCell == null)
						|| ((mTmpRect.width() * mTmpRect.height()) > (resRect
								.width() * resRect.height()))) {
					resRect.set(mTmpRect);
					resCell = cell;
				}
			}
		}

		return resCell;
	}

	public Cell findFreeCell(int rows, int cols, Region freeRegion) {
		if (mCells.isEmpty()) {
			return null;
		}

		final Rect boundRect = new Rect();
		final Rect rect = new Rect(0, 0, cols * mCellSize, rows * mCellSize);

		for (final Cell cell : mCells) {
			mTmpRegion.set(freeRegion);
			rect.offsetTo(cell.rect.left, cell.rect.top);

			mTmpRegion.op(rect, Op.INTERSECT);
			if (!mTmpRegion.isEmpty() && mTmpRegion.isRect()) {
				mTmpRegion.getBounds(boundRect);
				if ((boundRect.width() == rect.width())
						&& (boundRect.height() == rect.height())) {
					return cell;
				}
			}
		}

		return null;
	}

	private View findIntersectChild(float x, float y) {
		final int count = getChildCount();
		for (int i = count - 1; i >= 0; --i) {
			final View child = getChildAt(i);
			if (child == mRootView) {
				continue;
			}

			requestCurrentRect(mTmpRect, child);
			if (mTmpRect.contains((int) x, (int) y)) {
				return child;
			}
		}
		return null;
	}

	private Set<Node> findNodesUnder(Node dragNode, Set<Cell> hoveredCells) {
		if ((dragNode == null) || hoveredCells.isEmpty()) {
			return Collections.emptySet();
		}

		final Rect tmpRect = new Rect();
		final Region tmpRegion = new Region();
		for (Cell cell : hoveredCells) {
			tmpRegion.union(cell.rect);
		}

		Set<Node> nodes = new HashSet<Node>();
		int childCount = getChildCount();
		for (int i = 0; i < childCount; ++i) {
			View child = getChildAt(i);
			if ((child == mRootView) || (child == dragNode.view)) {
				continue;
			}

			requestCurrentRect(tmpRect, child);
			mTmpRegion.set(tmpRegion);
			mTmpRegion.op(tmpRect, Op.INTERSECT);

			if (!mTmpRegion.isEmpty()) {
				nodes.add(new Node(child, tmpRect));
			}
		}
		return nodes;
	}

	@Override
	public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new DragGridLayout.LayoutParams(getContext(), attrs);
	}

	@Override
	protected DragGridLayout.LayoutParams generateLayoutParams(
			ViewGroup.LayoutParams p) {
		if (p == null) {
			return new DragGridLayout.LayoutParams();
		} else if (p instanceof DragGridLayout.LayoutParams) {
			return new DragGridLayout.LayoutParams((DragGridLayout.LayoutParams)p);
		} else if (p instanceof MarginLayoutParams) {
			return new DragGridLayout.LayoutParams((MarginLayoutParams) p);
		}
		return new DragGridLayout.LayoutParams(p);
	}

	private int getArea(int l, int t, int r, int b) {
		int w = r - l;
		int h = b - t;

		return (w * w) + (h * h);
	}

	public List<Cell> getCells() {
		return mCells;
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

	public Drawable getHighlightDrawable() {
		return mHighlightDrawable;
	}

	@Override
	public View getRootView() {
		return mRootView;
	}

	private void init() {
		mHierarchyChangeListener = new HierarchyChangeListenerImpl();
		mGestureListener = new GestureListenerImpl();
		mGestureDetector = new GestureDetectorCompat(getContext(),
				mGestureListener);

		mGestureDetector.setIsLongpressEnabled(true);
		setLongClickable(true);
		super.setOnHierarchyChangeListener(mHierarchyChangeListener);
		setChildrenDrawingOrderEnabled(true);
		setClipToPadding(false);
	}

	public boolean isDebugMode() {
		return mDebugMode;
	}

	public boolean isEditMode() {
		return mEditMode;
	}

	public boolean isShowWidget() {
		return mWidgetVisibility;
	}

	private boolean isSpaceFree(LayoutParams lp, Region freeRagion) {
		if (mCells.isEmpty() || (lp.mX == UNKNOWN) || (lp.mY == UNKNOWN)) {
			return false;
		}

		final Rect rect = new Rect(lp.leftMargin, lp.topMargin,
				(lp.mHorizontalSize * mCellSize) - lp.rightMargin, (lp.mVerticalSize * mCellSize)
				- lp.bottomMargin);
		rect.offset(lp.mX, lp.mY);

		mTmpRegion.set(freeRagion);
		mTmpRegion.op(rect, Op.INTERSECT);
		if (!mTmpRegion.isEmpty() && mTmpRegion.isRect()) {
			mTmpRegion.getBounds(mTmpRect);
			return ((mTmpRect.width() == rect.width()) && (mTmpRect.height() == rect
					.height()));
		}
		return false;
	}

	@Override
	protected void measureChild(View child, int parentWidthMeasureSpec,
			int parentHeightMeasureSpec) {
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

			childWidthMeasureSpec = ViewGroup
					.getChildMeasureSpec(MeasureSpec.makeMeasureSpec(widthSize,
							MeasureSpec.EXACTLY), getPaddingLeft()
							+ getPaddingRight(), width);
			childHeightMeasureSpec = ViewGroup.getChildMeasureSpec(MeasureSpec
					.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY),
					getPaddingTop() + getPaddingBottom(), height);
		} else {
			final int width = (lp.mHorizontalSize * mCellSize)
					- (lp.leftMargin + lp.rightMargin);
			final int height = (lp.mVerticalSize * mCellSize)
					- (lp.topMargin + lp.bottomMargin);

			childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(
					parentWidthMeasureSpec, getPaddingLeft()
					+ getPaddingRight(), width);
			childHeightMeasureSpec = ViewGroup.getChildMeasureSpec(
					parentHeightMeasureSpec, getPaddingTop()
					+ getPaddingBottom(), height);
		}
		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
	}

	@Override
	protected void onDetachedFromWindow() {
		if (mRootViewDrawable != null) {
			mRootViewDrawable.getBitmap().recycle();
			mRootViewDrawable = null;
		}
		mLoongHoveredRequested = false;
		mHandler.removeMessages(LONGPRESS_MESSAGE);
		mHandler.removeMessages(LONGHOVER_MESSAGE);
		super.onDetachedFromWindow();
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (mEditMode) {
			return true;
		}
		return super.onInterceptTouchEvent(ev);
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

			final DragGridLayout.LayoutParams lp = (DragGridLayout.LayoutParams) child
					.getLayoutParams();

			if ((mDragNode != null) && (mDragNode.view == child)) {
				childLeft = mDragNode.currentRect.left;
				childTop = mDragNode.currentRect.top;
				childRight = mDragNode.currentRect.right;
				childBottom = mDragNode.currentRect.bottom;
			} else if (child == mRootView) {
				childLeft = 0;
				childTop = 0;
				childRight = r - l;
				childBottom = b - t;
			} else if ((lp.mX != UNKNOWN) && (lp.mY != UNKNOWN)) {
				childLeft = lp.mX + lp.leftMargin;
				childTop = lp.mY + lp.topMargin;
				childRight = childLeft + child.getMeasuredWidth();
				childBottom = childTop + child.getMeasuredHeight();
			} else {
				childLeft = 0;
				childTop = 0;
				childRight = 0;
				childBottom = 0;
			}

			child.layout(childLeft, childTop, childRight, childBottom);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		
		//resolveGrid(getMeasuredWidth(), getMeasuredHeight());

		measureChildren(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		resolveGrid(w, h);
	}

	private void resolveGrid(int w, int h) {
		mCellsRegion.setEmpty();

		mCells.clear();
		mHoveredCells.clear();
		mCellSize = resolveCellSize(w, h);
		if (!(mCellSize > 0)) {
			return;
		}

		int cellCount = (w - (getPaddingLeft() + getPaddingRight()))
				/ mCellSize;
		int x = getPaddingLeft()
				+ resolveOffset(w - (getPaddingLeft() + getPaddingRight()),
						mCellSize, cellCount, mGravity
						& Gravity.HORIZONTAL_GRAVITY_MASK);
		int vCcellCount = (h - (getPaddingTop() + getPaddingBottom()))
				/ mCellSize;
		int y = getPaddingTop()
				+ resolveOffset(h - (getPaddingTop() + getPaddingBottom()),
						mCellSize, vCcellCount,
						(mGravity & Gravity.VERTICAL_GRAVITY_MASK));

		final Cell[] prevRow = new Cell[cellCount];
		final Cell[] currRow = new Cell[cellCount];

		while ((y + mCellSize) < h) {
			resolveCell(currRow, prevRow, x, y, mCells);
			System.arraycopy(currRow, 0, prevRow, 0, currRow.length);
			y += mCellSize;
		}

		mCellsRegion.set(mCells.get(0).rect.left, mCells.get(0).rect.top,
				mCells.get(mCells.size() - 1).rect.right,
				mCells.get(mCells.size() - 1).rect.bottom);

		if (validateChildrenLayoutParams()) {
			requestLayout();
			invalidate();
		}
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

					mDragNode = new Node(child, requestPreferredRect(mTmpRect,
							child));
					Node.scale(mDragNode.currentRect, mScaleFactor);

					requestFreeCellRegion(child);
					requestHoveredCells(mDragNode);

					if (mDragListener != null) {
						mDragListener.onDrag(mDragNode.view, this);
					}

					final Animation animation = new ScaleAnimation(
							(1f - mScaleFactor) + 1f, 1f,
							(1f - mScaleFactor) + 1f, 1f,
							Animation.RELATIVE_TO_SELF, 0.5f,
							Animation.RELATIVE_TO_SELF, 0.5f);

					animation.setDuration(DURATION);
					animation
					.setAnimationListener(new AbstractAnimationListener() {
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
				mDragNode.currentRect.offset((int) (x - mPrevX),
						(int) (y - mPrevY));
				if (mDebugMode) {
					Log.w(VIEW_LOG_TAG, "ACTION_MOVE: x=" + x + " y=" + y
							+ " prevX=" + mPrevX + " prevY=" + mPrevY + " dX="
							+ (x - mPrevX) + " dY=" + (y - mPrevY));
				}

				requestHoveredCells(mDragNode);
				boolean dragged = (Math.abs(x - mPrevX) < DELTA)
						&& (Math.abs(y - mPrevY) < DELTA);
				if (dragged) {
					requestReorderRevert();
				}
				if (!mHoveredCells.isEmpty() && dragged) {
					if (!mLoongHoveredRequested) {
						mLoongHoveredRequested = true;
						mHandler.sendEmptyMessageDelayed(LONGHOVER_MESSAGE,
								LONGPRESS_TIMEOUT + TAP_TIMEOUT);
					}
				} else if (mLoongHoveredRequested) {
					mLoongHoveredRequested = false;
					mHandler.removeMessages(LONGHOVER_MESSAGE);
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
			mHandler.removeMessages(LONGHOVER_MESSAGE);
			if (mPressedCell != null) {
				if (mCellClickListener != null) {
					mCellClickListener.onClick(new Point(
							mPressedCell.rect.left, mPressedCell.rect.top),
							this);
				}
				mPressedCell = null;
				invalidate();
				return true;
			} else if (mDragNode != null) {
				mDragNode.currentRect.offset((int) (x - mPrevX),
						(int) (y - mPrevY));
				
				requestHoveredCells(mDragNode);
				requestReorderRevert();
				requestDrop(mDragNode);

				mPrevX = x;
				mPrevY = y;

				mTmpRect.set(mDragNode.currentRect);
				mTmpRect.union(mDragNode.startRect);

				final View child = mDragNode.view;
				final LayoutParams lp = (LayoutParams) child.getLayoutParams();

				lp.mX = mDragNode.startRect.left;
				lp.mY = mDragNode.startRect.top;

				mNodes.clear();
				mDragNode.startRect.offset(lp.leftMargin, lp.topMargin);

				final AnimationSet animation = new AnimationSet(true);
				animation.addAnimation(new TranslateAnimation(
						mDragNode.currentRect.left - mDragNode.startRect.left,
						0, mDragNode.currentRect.top - mDragNode.startRect.top,
						0));
				animation.addAnimation(new ScaleAnimation(mScaleFactor, 1f,
						mScaleFactor, 1f));

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

	public void removeRootView() {
		if (mRootView != null) {
			super.removeView(mRootView);
		}
	}

	private Rect requestCurrentRect(Rect outRect, View view) {
		if (outRect == null) {
			outRect = new Rect();
		}

		outRect.left = view.getLeft();
		outRect.top = view.getTop();
		outRect.right = view.getRight();
		outRect.bottom = view.getBottom();

		return outRect;
	}

	private void requestDrop(Node node) {
		requestFreeCellRegion(node.view);
		if (requestHoveredCells(node)) {
			requestHoverRect(mTmpRect);
			
			mTmpRegion.set(mFreeCellsRegion);
			mTmpRegion.op(mTmpRect, Op.INTERSECT);
			
			Rect bound = mTmpRegion.getBounds();
			if (!mTmpRegion.isEmpty() && 
				!mTmpRegion.isComplex() &&
				(bound.width() == mTmpRect.width()) &&
				(bound.height() == mTmpRect.height())) {
				node.startRect.offsetTo(mTmpRect.left, mTmpRect.top);
				return;
			}
		}
	
		final LayoutParams lp = (LayoutParams) node.view.getLayoutParams();
		final LayoutParams newLp = validateLayoutParams(generateLayoutParams(lp), mFreeCellsRegion);
		if ((lp.mX == newLp.mX) && (lp.mY == newLp.mY)){
			return;
		}
		node.startRect.offsetTo(newLp.mX, newLp.mY);
	}

	private void requestFreeCellRegion(View... views) {
		mFreeCellsRegion.set(mCellsRegion);
		final int count = getChildCount();
		for (int i = count - 1; i >= 0; --i) {
			final View child = getChildAt(i);

			if (child == mRootView) {
				continue;
			}
			boolean needContinue = false;

			for (View view : views) {
				if (view == child) {
					needContinue = true;
					break;
				}
			}
			if (needContinue) {
				continue;
			}

			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			if ((lp.mX == UNKNOWN) || (lp.mY == UNKNOWN)) {
				continue;
			}

			mFreeCellsRegion.op(requestPreferredRect(mTmpRect, child),
					Op.DIFFERENCE);
		}
	}

	private boolean requestHoveredCells(Node node) {
		mHoveredCells.clear();
		if (mCellsRegion.quickReject(node.currentRect)) {
			return false;
		}

		mTmpRegion.set(mCellsRegion);
		mTmpRegion.op(node.currentRect, Op.INTERSECT);

		final View view = node.view;
		final LayoutParams lp = (LayoutParams) view.getLayoutParams();

		int cellCount = lp.mHorizontalSize * lp.mVerticalSize;

		final Rect rect = mTmpRegion.getBounds();

		Cell ltCell = null;
		Cell rtCell = null;
		Cell lbCell = null;
		Cell rbCell = null;

		mTmpRegion.setEmpty();
		for (final Cell cell : mCells) {
			if (mTmpRect.setIntersect(cell.rect, rect)) {
				if (mTmpRegion.isEmpty()) {
					mTmpRegion.set(cell.rect);
					ltCell = rtCell = lbCell = rbCell = cell;
				} else {
					mTmpRegion.op(cell.rect, Op.UNION);

					if ((ltCell.rect.left >= cell.rect.left)
							&& (ltCell.rect.top >= cell.rect.top)) {
						ltCell = cell;
					}
					if ((rtCell.rect.right <= cell.rect.right)
							&& (rtCell.rect.top >= cell.rect.top)) {
						rtCell = cell;
					}
					if ((rbCell.rect.bottom <= cell.rect.bottom)
							&& (lbCell.rect.left >= cell.rect.left)) {
						lbCell = cell;
					}
					if ((rbCell.rect.bottom <= cell.rect.bottom)
							&& (rbCell.rect.right <= cell.rect.right)) {
						rbCell = cell;
					}
				}
			}
		}

		if ((ltCell == null) || (rtCell == null) || (lbCell == null)
				|| (rbCell == null)) {
			return false;
		}

		mTmpRegion.getBounds(mTmpRect);
		int lt = getArea(mTmpRect.left, mTmpRect.top, rect.left, rect.top);
		int rt = getArea(rect.right, mTmpRect.top, mTmpRect.right, rect.top);
		int lb = getArea(mTmpRect.left, rect.bottom, rect.left, mTmpRect.bottom);
		int rb = getArea(rect.right, rect.bottom, mTmpRect.right,
				mTmpRect.bottom);

		int min = Math.min(Math.min(lt, rt), Math.min(lb, rb));
		if (min == lt) {
			fillHoveredCells(ltCell, 1, 1, lp.mVerticalSize, lp.mHorizontalSize);
		} else if (min == rt) {
			fillHoveredCells(rtCell, 1, -1, lp.mVerticalSize, lp.mHorizontalSize);
		} else if (min == lb) {
			fillHoveredCells(lbCell, -1, 1, lp.mVerticalSize, lp.mHorizontalSize);
		} else if (min == rb) {
			fillHoveredCells(rbCell, -1, -1, lp.mVerticalSize, lp.mHorizontalSize);
		}

		return cellCount == mHoveredCells.size();
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

	private Rect requestPreferredRect(Rect outRect, View view) {
		if (outRect == null) {
			outRect = new Rect();
		}
		final LayoutParams lp = checkLayoutParams(view.getLayoutParams()) ? (LayoutParams) view
				.getLayoutParams() : generateLayoutParams(view
						.getLayoutParams());

				outRect.left = lp.mX;
				outRect.top = lp.mY;
				outRect.right = outRect.left + (mCellSize * lp.mHorizontalSize);
				outRect.bottom = outRect.top + (mCellSize * lp.mVerticalSize);

				return outRect;
	}

	private void requestReorder() {
		if (mDragNode == null){
			return;
		}
		Set<Node> nodes = findNodesUnder(mDragNode, mHoveredCells);

		for (final Node childNode : nodes) {
			requestFreeCellRegion(childNode.view, mDragNode.view);
			mFreeCellsRegion.op(mDragNode.currentRect, Op.DIFFERENCE);

			final LayoutParams lp = (LayoutParams) childNode.view.getLayoutParams();
			final LayoutParams newLp = validateLayoutParams(generateLayoutParams(lp), mFreeCellsRegion);
			if ((lp.mX == newLp.mX) && (lp.mY == newLp.mY)){
				continue;
			}
			childNode.view.setLayoutParams(newLp);

			requestPreferredRect(childNode.currentRect, childNode.view);

			final AnimationSet animation = new AnimationSet(true);
			animation.addAnimation(new TranslateAnimation(
					childNode.startRect.left - childNode.currentRect.left, 0,
					childNode.startRect.top - childNode.currentRect.top, 0));

			animation.setDuration(DURATION / 2);
			animation.setAnimationListener(new AbstractAnimationListener() {
				@Override
				public void onAnimationEnd(final Animation a) {
					childNode.view.setAnimation(null);

					requestLayout();
					invalidate();
				}
			});
			childNode.view.setAnimation(animation);
			mNodes.add(childNode);
		}

		if (!nodes.isEmpty()) {
			requestFreeCellRegion();
			requestLayout();
			invalidate();
		}
	}

	private void requestReorderRevert() {
		boolean needInvalidate = false;
		for (final Iterator<Node> it = mNodes.iterator(); it.hasNext();) {
			final Node node = it.next();
			if (mDragNode != null){
				requestFreeCellRegion(mDragNode.view);
			} else {
				requestFreeCellRegion();
			}
			for (Cell cell : mHoveredCells) {
				mFreeCellsRegion.op(cell.rect, Op.DIFFERENCE);
			}
			
			mTmpRegion.set(mFreeCellsRegion);
			mTmpRegion.op(node.startRect, Op.INTERSECT);
			mTmpRegion.getBounds(mTmpRect);
			if ((mTmpRect.width() == node.startRect.width())
					&& (mTmpRect.height() == node.startRect.height())) {
				it.remove();

				LayoutParams lp = (LayoutParams) node.view.getLayoutParams();
				lp.mX = node.startRect.left - lp.leftMargin;
				lp.mY = node.startRect.top - lp.topMargin;

				final AnimationSet animation = new AnimationSet(true);
				animation.addAnimation(new TranslateAnimation(
						-node.startRect.left + node.currentRect.left, 0,
						-node.startRect.top + node.currentRect.top, 0));

				animation.setDuration(DURATION / 2);
				animation.setAnimationListener(new AbstractAnimationListener() {
					@Override
					public void onAnimationEnd(final Animation a) {
						node.view.setAnimation(null);

						requestLayout();
						invalidate();
					}
				});
				node.view.setAnimation(animation);
				needInvalidate = true;
			}
		}

		if (needInvalidate) {
			invalidate();
		}
	}

	private void resolveCell(Cell[] curr, Cell[] prev, int offsetX,
			int offsetY, List<Cell> cells) {
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
		}
	}

	private int resolveCellSize(int w, int h) {
		w = Math.max(0, w - (getPaddingLeft() + getPaddingRight()));
		h = Math.max(0, h - (getPaddingTop() + getPaddingBottom()));

		final int orientation = getResources().getConfiguration().orientation;
		switch (orientation) {
		case Configuration.ORIENTATION_LANDSCAPE:
			return h / getCellCount();
		case Configuration.ORIENTATION_PORTRAIT:
			return w / getCellCount();
		default:
			return Math.max(h, w) / getCellCount();
		}
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

	public void setCellClickListener(OnCellClickListener cellClickListener) {
		this.mCellClickListener = cellClickListener;
	}

	public void setCellImage(Drawable cellDrawable) {
		this.mCellDrawable = cellDrawable;
	}

	public void setCellImage(int cellRes) {
		setCellImage(getResources().getDrawable(cellRes));
	}

	public void setDebugMode(boolean debugMode) {
		if (this.mDebugMode != debugMode) {
			this.mDebugMode = debugMode;
			invalidate();
		}
	}

	public void setDragListener(OnViewDragListener dragListener) {
		this.mDragListener = dragListener;
	}

	public void setDragScaleCoefficient(float scale) {
		this.mScaleFactor = scale;
	}

	public void setEditMode(boolean value) {
		if (this.mEditMode == value) {
			return;
		}

		mEditMode = value;
		if (mRootView != null) {
			if (mEditMode) {
				mRootViewDrawable = DragGridLayout
						.createDrawingCache(mRootView);
			} else if (mRootViewDrawable != null) {
				mRootViewDrawable.getBitmap().recycle();
				mRootViewDrawable = null;
			}
		}
		postInvalidate();
	}

	public void setGestureListener(OnGestureListener listener) {
		mGestureListener.gestureListener = listener;
	}

	public void setHighlight(Drawable highlightDrawable) {
		this.mHighlightDrawable = highlightDrawable;
	}

	public void setHighlight(int highlightRes) {
		setHighlight(getResources().getDrawable(highlightRes));
	}

	@Override
	public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
		this.mHierarchyChangeListener.listener = listener;
	}

	public void setRemoveListener(OnViewRemoveListener removeListener) {
		this.mRemoveListener = removeListener;
	}

	public void setRootView(View view) {
		if (view != mRootView) {
			removeRootView();
		}
		mRootView = view;
		addView(mRootView, 0, new LayoutParams(0, 0,
				android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.MATCH_PARENT));
	}

	public void setRootViewRes(int viewId) {
		setRootView(View.inflate(getContext(), viewId, null));
	}

	public void setShowWidget(boolean showWidget) {
		if (this.mWidgetVisibility != showWidget) {
			this.mWidgetVisibility = showWidget;
			invalidate();
		}
	}

	private void stopAnimation(View view) {
		if ((view != null) && (view.getAnimation() != null)) {
			view.getAnimation().cancel();
			view.setAnimation(null);
			invalidate();
		}
	}

	private boolean validateChildrenLayoutParams() {
		boolean needRequestLayout = false;
		int childCount = getChildCount();
		for (int i = 0; i < childCount; ++i) {
			View child = getChildAt(i);

			if (child == mRootView) {
				continue;
			}

			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			if ((lp.mX == UNKNOWN) || (lp.mY == UNKNOWN)) {
				child.setLayoutParams(validateLayoutParams(lp));
				needRequestLayout = true;
			}
		}
		return needRequestLayout;
	}

	public LayoutParams validateLayoutParams(ViewGroup.LayoutParams srcLp) {
		requestFreeCellRegion();
		return validateLayoutParams(srcLp, mFreeCellsRegion);
	}

	public LayoutParams validateLayoutParams(ViewGroup.LayoutParams srcLp,
			Region freeRehion) {
		final LayoutParams lp;
		if (checkLayoutParams(srcLp)) {
			lp = (LayoutParams) srcLp;
		} else {
			lp = generateLayoutParams(srcLp);
		}

		if (mCells.isEmpty()) {
			return lp;
		}

		if (isSpaceFree(lp, freeRehion)) {
			return lp;
		}

		final Cell cell = findFreeCell(lp.mVerticalSize, lp.mHorizontalSize, freeRehion);
		if (cell != null) {
			lp.mX = cell.rect.left;
			lp.mY = cell.rect.top;
		}
		return lp;
	}

	public int getCellCount() {
		return mCellCount;
	}

	public void setCellCount(int cellCount) {
		if (mCellCount != cellCount){
			mCellCount = cellCount;
			requestLayout();
		}
	}
}