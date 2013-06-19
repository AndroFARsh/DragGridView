package org.androfarsh.demo;

import org.androfarsh.widget.DragGridLayout;

import android.os.Bundle;


public class SimpleDemoActivity extends BaseDemoActivity {
	public static final int TITLE = R.string.simple_demo_name;

	private DragGridLayout mGridView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_demo_screen);

		mGridView = (DragGridLayout)findViewById(R.id.grid_view);
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
