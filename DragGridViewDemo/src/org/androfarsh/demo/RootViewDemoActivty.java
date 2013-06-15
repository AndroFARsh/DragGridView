package org.androfarsh.demo;

import org.androfarsh.widget.DragGridLayout;

import android.os.Bundle;
import android.widget.ImageView;


public class RootViewDemoActivty extends BaseDemoActivity {
	public static final int TITLE = R.string.root_view_demo_name;

	private static final long DELAY_MILLIS = 5000;
	private static final int[] DRAWABLE = new int[]{R.drawable.wp0, R.drawable.wp1, R.drawable.wp2};

	private DragGridLayout mGridView;
	private ImageView mRootView;

	private int mDrwawableIndex;
	private final Runnable mUpdateImage = new Runnable() {

		@Override
		public void run() {
			mDrwawableIndex++;
			if (mDrwawableIndex >= DRAWABLE.length){
				mDrwawableIndex = 0;
			}
			mRootView.setImageResource(DRAWABLE[mDrwawableIndex]);
			mRootView.postDelayed(this, DELAY_MILLIS);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.root_view_demo_screen);

		mGridView = (DragGridLayout)findViewById(R.id.grid_view);
		mRootView = (ImageView)findViewById(R.id.root_view);

		mRootView.postDelayed(mUpdateImage, DELAY_MILLIS);
	}

	@Override
	public void onBackPressed() {
		if (mGridView.isEditMode()){
			mGridView.setEditMode(false);
			return;
		}

		super.onBackPressed();
	}

	@Override
	public DragGridLayout getDragGridLayout() {
		return mGridView;
	}
}
