package org.androfarsh.demo;

import org.androfarsh.demo.drahgrid.R;
import org.androfarsh.widget.DragGridLayout;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

abstract class BaseDemoActivity extends BaseActivity {

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.demo_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null){
			menu.findItem(R.id.edit_mode).setChecked(getDragGridLayout().isEditMode());
			menu.findItem(R.id.debug_mode).setChecked(getDragGridLayout().isDebugMode());
		}
		return super.onMenuOpened(featureId, menu);
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.debug_mode:
			item.setChecked(!item.isChecked());
			getDragGridLayout().setDebugMode(item.isChecked());
			break;
		case R.id.edit_mode:
			item.setChecked(!item.isChecked());
			getDragGridLayout().setEditMode(item.isChecked());
			break;
		default:
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	@Override
	public void onBackPressed() {
		if (getDragGridLayout().isEditMode()){
			getDragGridLayout().setEditMode(false);
			return;
		}

		super.onBackPressed();
	}
	
	public abstract DragGridLayout getDragGridLayout();
}
