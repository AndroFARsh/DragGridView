package org.androfarsh.demo;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.androfarsh.demo.drahgrid.R;
import org.androfarsh.widget.DragGridLayout;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;


public class RootViewDemoActivity extends BaseDemoActivity {
	private static final class TimeUpdater implements Runnable {
		static Date DATE = new Date();
		static DateFormat FORMATER = SimpleDateFormat.getTimeInstance(SimpleDateFormat.MEDIUM);

		TextView mView;

		TimeUpdater(TextView view){
			mView = view;
		}

		@Override
		public void run() {
			DATE.setTime(System.currentTimeMillis());
			mView.setText(FORMATER.format(DATE));
			mView.postDelayed(this, 1000);
		}

		public void start(){
			mView.post(this);
		}

		public void stop(){
			mView.post(this);
		}
	}

	private static final class ImageUpdater implements Runnable {
		private static final long DELAY_MILLIS = 15000;
		private static final int[] DRAWABLE = new int[]{R.drawable.wp0, R.drawable.wp1, R.drawable.wp2};

		private int mDrwawableIndex;
		private final ImageView mView;

		ImageUpdater(ImageView view){
			mView = view;
		}

		@Override
		public void run() {
			mDrwawableIndex++;
			if (mDrwawableIndex >= DRAWABLE.length){
				mDrwawableIndex = 0;
			}
			mView.setImageResource(DRAWABLE[mDrwawableIndex]);
			mView.postDelayed(this, DELAY_MILLIS);
		}

		public void start(){
			mView.post(this);
		}

		public void stop(){
			mView.post(this);
		}
	}

	public static final int TITLE = R.string.root_view_demo_name;

	private DragGridLayout mGridView;
	private ImageUpdater mRootBgUpdater;
	private TimeUpdater mRootWatchUpdater;
	private TimeUpdater mWidgetWatchUpdater;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.root_view_demo_screen);

		mGridView = (DragGridLayout)findViewById(R.id.grid_view);
		mRootBgUpdater = new ImageUpdater((ImageView) findViewById(R.id.bg));
		mRootWatchUpdater =  new TimeUpdater((TextView)findViewById(R.id.watch_view));
		mWidgetWatchUpdater =  new TimeUpdater((TextView)findViewById(R.id.widget_watch_view));
	}

	@Override
	protected void onResume() {
		mRootBgUpdater.start();
		mRootWatchUpdater.start();
		mWidgetWatchUpdater.start();
		super.onResume();
	}

	@Override
	protected void onPause() {
		mRootBgUpdater.stop();
		mRootWatchUpdater.stop();
		mWidgetWatchUpdater.stop();
		super.onPause();
	}

	@Override
	public DragGridLayout getDragGridLayout() {
		return mGridView;
	}
}
