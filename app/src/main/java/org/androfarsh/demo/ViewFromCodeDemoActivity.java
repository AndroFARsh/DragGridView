package org.androfarsh.demo;

import java.util.Random;

import org.androfarsh.demo.drahgrid.R;
import org.androfarsh.widget.DragGridLayout;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;


public class ViewFromCodeDemoActivity extends BaseDemoActivity {
	public static final int TITLE = R.string.view_from_code_demo_name;
	private static final int[] WIDGETS_TITLE = new int[]{R.string.widget_1, R.string.widget_2, R.string.widget_3, R.string.widget_4, R.string.widget_5};
	private static final int[] WIDGETS_DRAWABLE = new int[]{R.drawable.widget1, R.drawable.widget2, R.drawable.widget3, R.drawable.widget4, R.drawable.widget5};
	
	private DragGridLayout mGridView;
	private Random mRandom = new Random();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mGridView = new DragGridLayout(this);
		mGridView.setCellCount(3);
		mGridView.setHighlight(R.drawable.highlight);
		mGridView.setCellImage(R.drawable.cell);
		
		int count = Math.min(WIDGETS_TITLE.length, WIDGETS_DRAWABLE.length);
		for (int i = 0; i < count; ++i){
			TextView view = new TextView(this);
			view.setText(WIDGETS_TITLE[i]);
			view.setBackgroundResource(WIDGETS_DRAWABLE[i]);
			view.setTextColor(Color.WHITE);
			view.setGravity(Gravity.CENTER);
			
			DragGridLayout.LayoutParams lp = new DragGridLayout.LayoutParams();
			lp.setMargins(5, 5, 5, 5);
			lp.setVerticalSize(i%2 == 0 ? 1 : 2);
			lp.setHorizontalSize((i + mRandom.nextInt(2))%2 == 0 ? 1 : 2);
			
			view.setLayoutParams(lp);
			mGridView.addView(view, lp);
		}
		
		setContentView(mGridView);
	}

	

	@Override
	public DragGridLayout getDragGridLayout() {
		return mGridView;
	}
}
