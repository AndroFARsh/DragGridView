package org.androfarsh.widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;

import com.example.draggridviewlibrary.R;

public class DragGridLayout extends ViewGroup {
	public static final int VERTICAL = 0;
	public static final int HORIZONTAL = 1;
	private static final int HOVER_DURATION = 300;
	private static final int UNDEFINED = -1;
	private static final int DURATION = 100;
	private static final int DEFAULT_CELL_COUNT = 4;
	private static final float SCALE = 0.2f;

	public enum Mode {DEFAULT, ONE_TERM_OPERATION, EDIT_MODE};
	
	private final Paint paint = new Paint();

	private int cellSize;

	private boolean debugMode = false;

	private Mode mode = Mode.DEFAULT;

	private boolean dragging = false;

	private Node dragNode;

	private int prevX;

	private int prevY;

	private int rows;

	private int columns;

	private int cellCount;

	private int orientation;

	private float scale = SCALE;

	private HierarchyChangeListenerImpl hierarchyChangeListener;

	private Drawable highlightDrawable = new StateListDrawable() {
		{
			this.addState(new int[]{-R.attr.state_drop_allow},
					new ColorDrawable(Color.argb(128, 128, 0, 0)));
			this.addState(new int[]{},
					new ColorDrawable(Color.argb(128, 0, 128, 0)));
		}
	};

	private OnDragListener dragListener;

	private List<View> children = new ArrayList<View>();

	private Set<Node> nodes = new HashSet<DragGridLayout.Node>();

	private long hoverTime;

	private GestureDetectorCompat gestureDetector;

	public DragGridLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		final TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.DragGridLayout, defStyle, 0);

		cellCount = Math
				.max(1, a.getInt(R.styleable.DragGridLayout_cell_count,
						DEFAULT_CELL_COUNT));

		orientation = a.getInt(R.styleable.DragGridLayout_orientation, 0);

		debugMode = a.getBoolean(R.styleable.DragGridLayout_debug_mode, false);

		a.recycle();

		init(context);
	}

	public DragGridLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public DragGridLayout(Context context) {
		super(context);

		init(context);
	}

	private void init(Context context) {
		gestureDetector = new GestureDetectorCompat(context, new GestureListenerImpl());
		gestureDetector.setIsLongpressEnabled(true);
		
		hierarchyChangeListener = new HierarchyChangeListenerImpl();
		super.setOnHierarchyChangeListener(hierarchyChangeListener);
		setChildrenDrawingOrderEnabled(true);
		setLongClickable(true);
	}

	@Override
	protected void measureChild(View child, int parentWidthMeasureSpec,
			int parentHeightMeasureSpec) {
		final DragGridLayout.LayoutParams lp = (LayoutParams) child
				.getLayoutParams();

		if (!dragging || (dragNode == null) || (dragNode.view != child)) {
			lp.width = resolveCellSize(lp.horizonalSize) * cellSize;
			lp.height = resolveCellSize(lp.verticalSize) * cellSize;

		}

		final int childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(
				parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight(),
				lp.width);
		final int childHeightMeasureSpec = ViewGroup.getChildMeasureSpec(
				parentHeightMeasureSpec, getPaddingTop() + getPaddingBottom(),
				lp.height);

		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
	}

	private int resolveCellSize(int size) {
		if (size == LayoutParams.FILL) {
			return cellCount;
		}
		return size;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int count = getChildCount();

		int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
		int maxHeight = MeasureSpec.getSize(heightMeasureSpec);

		if (orientation == VERTICAL) {
			cellSize = maxWidth / cellCount;
		} else {
			cellSize = maxHeight / cellCount;
		}
		
		// Find rightmost and bottom-most child
		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			if (child.getVisibility() != GONE) {
				int childRight;
				int childBottom;

				final DragGridLayout.LayoutParams lp = (DragGridLayout.LayoutParams) child
						.getLayoutParams();

				if ((dragNode != null && dragNode.view == child)
						|| lp.animation) {
					childRight = lp.x + child.getMeasuredWidth();
					childBottom = lp.y + child.getMeasuredHeight();
				} else {
					childRight = (lp.column + lp.horizonalSize) * cellSize;
					childBottom = (lp.row + lp.verticalSize) * cellSize;
				}

				maxWidth = Math.max(maxWidth, childRight);
				maxHeight = Math.max(maxHeight, childBottom);
			}
		}

		// Account for padding too
		maxWidth += getPaddingLeft() + getPaddingRight();
		maxHeight += getPaddingTop() + getPaddingBottom();

		maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
		maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());

		switch (orientation) {
			case VERTICAL :
				columns = cellCount;
				rows = Math.max(mode != Mode.DEFAULT ? rows : 0, ((maxHeight / cellSize) + ((maxHeight % cellSize > 0) ? 1 :0)));
				maxHeight = Math.max(maxHeight, rows * cellSize);
				break;
			case HORIZONTAL :
				rows = cellCount;
				columns = Math.max(mode != Mode.DEFAULT ? columns : 0, ((maxWidth / cellSize) + ((maxWidth % cellSize > 0) ? 1 :0)));
				maxWidth = Math.max(maxWidth, columns * cellSize);
		}

		widthMeasureSpec = View.resolveSize(maxWidth, widthMeasureSpec);
		heightMeasureSpec = View.resolveSize(maxHeight, heightMeasureSpec);

		measureChildren(widthMeasureSpec, heightMeasureSpec);
		setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);

		int newCellSize = (orientation == VERTICAL
				? getMeasuredWidth()
				: getMeasuredHeight()) / cellCount;

		if (newCellSize != cellSize) {
			cellSize = newCellSize;

			requestLayout();
		}
	}

	/**
	 * Returns a set of layout parameters with a width of
	 * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}, a height of
	 * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT} and with the
	 * coordinates (0, 0).
	 */
	@Override
	protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0);
	}

	public Mode getMode() {
		return this.mode;
	}
	
	public void setMode(Mode value) {
		if (mode != value) {
			mode = value;
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int count = getChildCount();

		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			if (child.getVisibility() != GONE) {
				final DragGridLayout.LayoutParams lp = (DragGridLayout.LayoutParams) child
						.getLayoutParams();

				if (!dragging
						&& ((dragNode != null) && (dragNode.view != child) && !lp.animation)
						|| (dragNode == null) || (dragNode.view != child)) {
					lp.x = lp.column * cellSize;
					lp.y = lp.row * cellSize;
				}

				final int childLeft = getPaddingLeft() + lp.x;
				final int childTop = getPaddingTop() + lp.y;
				child.layout(childLeft, childTop,
						childLeft + child.getMeasuredWidth(),
						childTop + child.getMeasuredHeight());
			}
		}
	}

	@Override
	public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new DragGridLayout.LayoutParams(getContext(), attrs);
	}

	// Override to allow type-checking of LayoutParams.
	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof DragGridLayout.LayoutParams;
	}

	@Override
	protected DragGridLayout.LayoutParams generateLayoutParams(
			ViewGroup.LayoutParams p) {
		if (p instanceof DragGridLayout.LayoutParams) {
			return new DragGridLayout.LayoutParams((LayoutParams) p);
		}
		return new DragGridLayout.LayoutParams(p);
	}

	@Override
	public boolean shouldDelayChildPressedState() {
		return false;
	}

	@Override
	protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
		if ((dragNode != null) && (highlightDrawable != null)
				&& (dragNode.view == child)) {
			drawHighlight(canvas);
		}

		return super.drawChild(canvas, child, drawingTime);
	}

	@Override
	protected int getChildDrawingOrder(int childCount, int i) {
		if (dragNode != null) {
			final int index = indexOfChild(dragNode.view);
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

	@Override
	protected void dispatchDraw(Canvas canvas) {
		if (debugMode) {
			drawDebugGrid(canvas);
		}

		super.dispatchDraw(canvas);
	}

	private void drawHighlight(Canvas canvas) {
		if ((dragNode.currentCol != UNDEFINED)
				&& (dragNode.currentRow != UNDEFINED)) {
			final int stateSet[] = new int[]{R.attr.state_drop_allow
					* (isDropAllowed(dragNode.currentRow, dragNode.currentCol,
							dragNode) ? 1 : -1)};
			final Rect boundRect = new Rect(
					dragNode.currentCol * cellSize,
					dragNode.currentRow * cellSize,
					(dragNode.currentCol * cellSize)
							+ (resolveCellSize(dragNode.lp.horizonalSize) * cellSize),
					(dragNode.currentRow * cellSize)
							+ (resolveCellSize(dragNode.lp.verticalSize) * cellSize));

			highlightDrawable.setState(stateSet);
			highlightDrawable.setBounds(boundRect);

			canvas.save();
			canvas.clipRect(boundRect);
			highlightDrawable.draw(canvas);
			canvas.restore();
		}
	}
	private void drawDebugGrid(Canvas canvas) {
		paint.setStrokeWidth(1);
		paint.setColor(Color.BLUE);

		for (int i = 0; i <= columns; ++i) {
			final int x = cellSize * i;
			canvas.drawLine(x, 0, x, getMeasuredHeight(), paint);
		}

		for (int i = 0; i <= rows; ++i) {
			final int y = cellSize * i;
			canvas.drawLine(0, y, getMeasuredWidth(), y, paint);
		}
	}

	private int getRow(Rect viewRect, int size) {
		int row = Math.max(0, Math.round(((float) viewRect.top) / cellSize));
		
		if (orientation == HORIZONTAL) {
			return Math.min(Math.max(0, rows - size), row);
		}
		return row;
	}

	private int getColumn(Rect viewRect, int size) {
		int col = Math.max(0,
				Math.round(((float) viewRect.left) / cellSize));
		
		if (orientation == HORIZONTAL) {
			return Math.min(Math.max(0, columns - size), col);
		}
		return col;
	}

	public void setDebugMode(boolean enable) {
		if (debugMode == enable) {
			this.debugMode = enable;
			invalidate();
		}
	}

	@Override
	public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
		hierarchyChangeListener.listener = listener;
	}

	private void calcRect(Rect outRect, View view) {
		if ((outRect == null) || (view == null)) {
			return;
		}

		outRect.left = view.getLeft();
		outRect.top = view.getTop();
		outRect.right = view.getRight();
		outRect.bottom = view.getBottom();
	}

	private void calcRect(Node node) {
		if (node == null) {
			return;
		}

		node.rect.left = node.lp.x;
		node.rect.top = node.lp.y;
		node.rect.right = node.lp.x + node.lp.width;
		node.rect.bottom = node.lp.y + node.lp.height;
	}

	private View findIntersectChild(Rect rect) {
		final Rect viewRect = new Rect();
		final int count = getChildCount();
		for (int i = count - 1; i >= 0; --i) {
			final View child = getChildAt(i);
			calcRect(viewRect, child);
			if (rect.intersect(viewRect)) {
				return child;
			}
		}
		return null;
	}

	private int findIndexes(int[] matrix, int rows, int columns, int subRows,
			int subCols) {
		for (int or = 0; or <= (rows - subRows); or++) {
			outerCol : for (int oc = 0; oc <= (columns - subCols); oc++) {
				for (int ir = 0; ir < subRows; ir++) {
					for (int ic = 0; ic < subCols; ic++) {
						final int index = ((oc + ic) * columns) + (or + ir);
						if (index >= matrix.length || matrix[index] != 0) {
							continue outerCol;
						}
					}
				}
				return ((oc * columns) + or);
			}
		}
		return -1;
	}

	private int[] createMatrix(Node childNode) {
		final int matrix[] = new int[rows * columns];
		for (final View child : children) {
			if (child != childNode.view) {
				final LayoutParams lp = (LayoutParams) child.getLayoutParams();

				for (int i = 0; i < lp.verticalSize; ++i) {
					final int start = ((((dragNode.view != child)
							? lp.row
							: dragNode.currentRow) + i) * columns)
							+ ((dragNode.view != child)
									? lp.column
									: dragNode.currentCol);
					int end = start + resolveCellSize(lp.horizonalSize);
					if (end >= matrix.length) {
						end = matrix.length - 1;
					}

					if ((start < matrix.length) && (end < matrix.length)
							&& (end > -1)) {
						Arrays.fill(matrix, start, end, 1);
					}
				}
			}
		}
		return matrix;
	}

	private void requestLayoutReorder() {
		findNodesUnder(dragNode, nodes);
		if (nodes.isEmpty()) {
			return;
		}

		for (final Node childNode : nodes) {
			final int matrix[] = createMatrix(childNode);
			final int index = findIndexes(matrix, rows, columns,
					resolveCellSize(childNode.lp.horizonalSize),
					resolveCellSize(childNode.lp.verticalSize));
			if (index != -1) {
				childNode.currentRow = index / columns;
				childNode.currentCol = index - (childNode.currentRow * columns);
				animateChild(childNode.currentRow, childNode.currentCol,
						childNode);

				if (debugMode) {
					Log.d(VIEW_LOG_TAG, "[" + index + "]");
					printMatrix(matrix);
				}
			}
			invalidate();
		}
	}

	private void printMatrix(final int[] matrix) {
		final StringBuilder builder = new StringBuilder("\n");
		for (int i = 0; i < matrix.length; ++i) {
			builder.append(matrix[i]);
			if (((i + 1) % columns) == 0) {
				builder.append("\n");
			}
		}
		Log.w(VIEW_LOG_TAG, builder.toString());
	}

	private void requestReturnItem() {
		for (final Iterator<Node> it = nodes.iterator(); it.hasNext();) {
			final Node childNode = it.next();
			if (isDropAllowed(childNode.startRow, childNode.startCol, childNode)) {
				it.remove();
				animateChild(childNode.startRow, childNode.startCol, childNode);
			}
		}
	}

	private boolean isDropAllowed(int row, int col, final Node childNode) {
		final int matrix[] = createMatrix(childNode);
		final int baseIndex = (row * columns) + col;
		
		if (debugMode) {
			printMatrix(matrix);
		}

		for (int i = 0; i < resolveCellSize(childNode.lp.horizonalSize); ++i) {
			for (int j = 0; j < resolveCellSize(childNode.lp.verticalSize); ++j) {
				int index = baseIndex + (j * columns) + i;
				if (index >= matrix.length || matrix[index] != 0) {
					return false;
				}
			}
		}
		return true;
	}

	private void animateChild(int row, int column, final Node node) {
		node.lp.animation = true;
		node.lp.row = row;
		node.lp.column = column;

		final TranslateAnimation animation = new TranslateAnimation(
				-((node.lp.column * cellSize) - node.lp.x), 0,
				-((node.lp.row * cellSize) - node.lp.y), 0);
		animation.setDuration(DURATION);
		animation.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				node.view.clearAnimation();
				node.lp.animation = false;
				node.view.setLayoutParams(node.lp);
				invalidate();
			}
		});
		node.view.startAnimation(animation);
	}

	private void findNodesUnder(Node node, Set<Node> nodes) {
		if (node != null) {
			final Rect rect = new Rect(node.currentCol * cellSize,
					node.currentRow * cellSize,
					(node.currentCol + resolveCellSize(node.lp.horizonalSize))
							* cellSize,
					(node.currentRow + resolveCellSize(node.lp.verticalSize))
							* cellSize);
			final Rect childRect = new Rect();
			final int count = getChildCount();
			for (int index = 0; index < count; ++index) {
				final View child = getChildAt(index);
				calcRect(childRect, child);
				if ((child != node.view) && Rect.intersects(rect, childRect)) {
					final Node childNode = new Node();
					childNode.view = child;
					childNode.lp = (LayoutParams) child.getLayoutParams();
					childNode.currentRow = childNode.startRow = childNode.lp.row;
					childNode.currentCol = childNode.startCol = childNode.lp.column;
					childNode.rect.set(childRect);

					if (!nodes.contains(childNode)) {
						nodes.add(childNode);
					} else {
						for (final Node n : nodes) {
							if (n.view == child) {
								n.currentRow = childNode.currentRow;
								n.currentCol = childNode.currentCol;
							}
						}
					}
				}
			}
		}
	}

	private void onStartDraggin(final View child) {
		dragging = true;
		dragNode = new Node();
		dragNode.view = child;
		dragNode.lp = (DragGridLayout.LayoutParams) (child.getLayoutParams());

		calcRect(dragNode);
		dragNode.currentCol = dragNode.startCol = getColumn(dragNode.rect,
				resolveCellSize(dragNode.lp.verticalSize));
		dragNode.currentRow = dragNode.startRow = getRow(dragNode.rect,
				resolveCellSize(dragNode.lp.horizonalSize));

		if (dragListener != null) {
			dragListener.onDraged(dragNode.currentRow, dragNode.currentCol,
					dragNode.view, this);
		}

		final ScaleAnimation animation = new ScaleAnimation(1f, 1f - scale, 1f,
				1f - scale, dragNode.lp.width / 2, dragNode.lp.height / 2);

		animation.setDuration(DURATION);
		animation.setAnimationListener(new AnimationListener() {

			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				child.clearAnimation();
				final LayoutParams lp = (LayoutParams) child.getLayoutParams();

				final float center = Math.abs(scale / 2);
				lp.x += lp.width * center;
				lp.y += lp.height * center;
				lp.width *= (1 - scale);
				lp.height *= (1 - scale);

				child.setLayoutParams(lp);
				invalidate();
			}
		});
		child.startAnimation(animation);
		invalidate();
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return isEditingAlowed() || super.onInterceptTouchEvent(ev);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (gestureDetector.onTouchEvent(ev)) {
			return true;
		}
		
		final int action = ev.getAction();
		final int x = (int) ev.getX();
		final int y = (int) ev.getY();
		switch (action) {
			case MotionEvent.ACTION_DOWN : 
				Log.e(VIEW_LOG_TAG, "ACTION_DOWN");
				final View child = findIntersectChild(new Rect(x, y, x, y));
				if (child != null && isEditingAlowed()) {
					prevX = x;
					prevY = y;
					
					onStartDraggin(child);
					hoverTime = System.currentTimeMillis();
					return true;
				}
				break;
			case MotionEvent.ACTION_MOVE :
				if (dragNode != null) {
					dragNode.lp.x += (x - prevX);
					dragNode.lp.y += (y - prevY);

					prevX = x;
					prevY = y;

					dragNode.view.setLayoutParams(dragNode.lp);

					calcRect(dragNode);
					final int row = getRow(dragNode.rect,
							resolveCellSize(dragNode.lp.verticalSize));
					final int column = getColumn(dragNode.rect,
							resolveCellSize(dragNode.lp.horizonalSize));

					if ((column != dragNode.currentCol)
							|| (row != dragNode.currentRow)) {
						dragNode.currentRow = row;
						dragNode.currentCol = column;
						hoverTime = ev.getEventTime();
						if (dragListener != null) {
							dragListener.onHoverChanged(dragNode.currentRow,
									dragNode.currentCol, dragNode.view, this);
						}

						requestReturnItem();
					}

					if ((ev.getEventTime() - hoverTime) > HOVER_DURATION) {
						hoverTime = ev.getEventTime();

						requestLayoutReorder();
					}

					requestLayout();
					invalidate();
					return true;
				}
				break;
			case MotionEvent.ACTION_CANCEL :
			case MotionEvent.ACTION_UP :
				if (dragNode != null) {
					nodes.clear();
					calcRect(dragNode);

					dragNode.currentRow = getRow(dragNode.rect,
							resolveCellSize(dragNode.lp.verticalSize));
					dragNode.currentCol = getColumn(dragNode.rect,
							resolveCellSize(dragNode.lp.horizonalSize));

					final int row;
					final int col;
					if (isDropAllowed(dragNode.currentRow, dragNode.currentCol,
							dragNode)) {
						row = dragNode.currentRow;
						col = dragNode.currentCol;
					} else {
						row = dragNode.startRow;
						col = dragNode.startCol;
					}

					if (dragListener != null) {
						dragListener.onDroped(row, col, dragNode.view, this);
					}

					int prevX = dragNode.lp.x;
					int prevY = dragNode.lp.y;
					dragNode.lp.x = (col * cellSize);
					dragNode.lp.y = (row * cellSize);
					dragNode.lp.row = row;
					dragNode.lp.column = col;

					final AnimationSet animation = new AnimationSet(true);
					animation.addAnimation(new TranslateAnimation(prevX
							- dragNode.lp.x, 0, prevY - dragNode.lp.y, 0));
					animation.addAnimation(new ScaleAnimation(1f - scale, 1f,
							1f - scale, 1f));

					animation.setDuration(DURATION);
					animation.setAnimationListener(new AnimationListener() {
						Node node = dragNode;

						@Override
						public void onAnimationStart(final Animation a) {
						}

						@Override
						public void onAnimationRepeat(final Animation a) {
						}

						@Override
						public void onAnimationEnd(final Animation a) {
							if (node != null) {
								dragging = false;
								node.view.clearAnimation();
								node.lp.width *= 1f + scale;
								node.lp.height *= 1f + scale;

								node.view.setLayoutParams(node.lp);
								invalidate();
							}
						}
					});

					dragNode.view.setLayoutParams(dragNode.lp);
					dragNode.view.startAnimation(animation);
					dragNode = null;
					if (Mode.ONE_TERM_OPERATION == mode) {
						setMode(Mode.DEFAULT);
					}
					return true;
				}
				break;
		}
		return super.onTouchEvent(ev);
	}

	private boolean isEditingAlowed() {
		return mode != Mode.DEFAULT;
	}

	public Drawable getHighlightDrawable() {
		return highlightDrawable;
	}

	public void setHighlightDrawable(Drawable highlight) {
		this.highlightDrawable = highlight;
	}

	public void setHighlightResource(int resId) {
		this.highlightDrawable = getResources().getDrawable(resId);
	}

	public OnDragListener getDragListener() {
		return dragListener;
	}

	public void setDragListener(OnDragListener dragListener) {
		this.dragListener = dragListener;
	}

	public float getScale() {
		return scale;
	}

	/**
	 * @param scale
	 *            - scale coefficient
	 */
	public void setScale(float scale) {
		this.scale = scale;
	}

	/**
	 * Per-child layout information associated with AbsoluteLayout. See
	 * {@link android.R.styleable#AbsoluteLayout_Layout Absolute Layout
	 * Attributes} for a list of all child view attributes that this class
	 * supports.
	 */
	public static class LayoutParams extends ViewGroup.LayoutParams {
		public static final int FILL = -1;

		int x;
		int y;

		int column;
		int row;

		int verticalSize = 1;
		int horizonalSize = 1;

		boolean animation;

		@Override
		public String toString() {
			return String.format(Locale.ENGLISH,
					"a=%s [x=%d;y=%d] [w=%d;h=%d] [c=%d;r=%d] [h=%d;v=%d]",
					Boolean.toString(animation), x, y, width, height, column,
					row, horizonalSize, verticalSize);
		}

		public LayoutParams(int width, int height, int x, int y) {
			super(width, height);
			this.x = x;
			this.y = y;
		}

		public LayoutParams(Context c, AttributeSet attrs) {
			super(MATCH_PARENT, MATCH_PARENT);
			final TypedArray a = c.obtainStyledAttributes(attrs,
					R.styleable.DragGridLayout);

			row = a.getInt(R.styleable.DragGridLayout_row, 1);
			column = a.getInt(R.styleable.DragGridLayout_column, 1);

			verticalSize = a.getInt(R.styleable.DragGridLayout_vertical_size, 1);
			horizonalSize = a.getInt(R.styleable.DragGridLayout_horizontal_size, 1);

			if (verticalSize == -2) {
				height = WRAP_CONTENT;
			}

			if (horizonalSize == -2) {
				width = WRAP_CONTENT;
			}

			a.recycle();
		}

		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}

		public LayoutParams(DragGridLayout.LayoutParams source) {
			super(source);

			x = source.x;
			y = source.y;

			column = source.column;
			row = source.row;

			verticalSize = source.verticalSize;
			horizonalSize = source.horizonalSize;
		}
	}

	private class HierarchyChangeListenerImpl
			implements
				OnHierarchyChangeListener {
		private OnHierarchyChangeListener listener;

		HierarchyChangeListenerImpl() {
		}

		@Override
		public void onChildViewAdded(View parent, View child) {
			// generates an id if it's missing
			if (child.getId() == View.NO_ID) {
				child.setId(child.hashCode());
			}
			children.add(child);
			if (listener != null) {
				listener.onChildViewAdded(parent, child);
			}
		}

		@Override
		public void onChildViewRemoved(View parent, View child) {
			if (listener != null) {
				listener.onChildViewRemoved(parent, child);
			}
			children.remove(child);
		}
	}

	private final class GestureListenerImpl implements OnGestureListener {
		private static final int VELOCITY = 1000;

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			return false;
		}

		@Override
		public void onShowPress(MotionEvent e) {
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
				float distanceX, float distanceY) {
			return false;
		}

		@Override
		public void onLongPress(MotionEvent ev) {
			final int x = (int) ev.getX();
			final int y = (int) ev.getY();
			if (!isEditingAlowed()) {
				final View child = findIntersectChild(new Rect(x, y, x, y));
				if (child != null) {
					setMode(Mode.ONE_TERM_OPERATION);
					onStartDraggin(child);
					
					prevX = x;
					prevY = y;
				}
			}
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {

			if ((dragNode != null) && (Math.abs(orientation == VERTICAL ? velocityX : velocityY) > VELOCITY)) {
				Log.v(VIEW_LOG_TAG, String.format(
						"onFling [vX=%f,vY=%f] e1=%s, e2=%s ", velocityX,
						velocityY, e1.toString(), e2.toString()));

				final TranslateAnimation animation;
				if (orientation == VERTICAL) {
					animation = new TranslateAnimation(0,
							-(dragNode.lp.x + dragNode.lp.width), 0, 0);
				} else {
					animation = new TranslateAnimation(0,
							0, 0, -(dragNode.lp.y + dragNode.lp.height));
				}

				animation.setDuration(DURATION);
				dragNode.view.startAnimation(animation);
				removeView(dragNode.view);
				dragNode = null;
				if (mode == Mode.ONE_TERM_OPERATION) {
					mode = Mode.DEFAULT;
				}
				return true;
			}
			return false;
		}
		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}
	}

	interface OnDragListener {
		void onDraged(int row, int column, View view, DragGridLayout parent);

		void onHoverChanged(int newRow, int newColumn, View view,
				DragGridLayout parent);

		void onDroped(int row, int column, View view, DragGridLayout parent);
	}

	private static class Node {
		Rect rect = new Rect();

		View view;

		DragGridLayout.LayoutParams lp;

		int startRow = UNDEFINED;

		int startCol = UNDEFINED;

		int currentRow = UNDEFINED;

		int currentCol = UNDEFINED;

		@Override
		public int hashCode() {
			return view.hashCode();
		}
	}
}
