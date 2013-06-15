package org.androfarsh.demo;

import java.lang.reflect.Field;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

abstract class BaseActivity extends Activity {
	protected static final String TAG = BaseActivity.class.getSimpleName();
	private static final Uri GITHUB_URL = Uri.parse("https://github.com/AndroFARsh/DragGridView");


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	private void setTitle() {
		try {
			Field title = getClass().getField("TITLE");
			setTitle(getString(R.string.demo_title_pattern, getString(R.string.app_name), getString(title.getInt(getClass()))));
		} catch (IllegalArgumentException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IllegalAccessException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (NoSuchFieldException e) {
		}
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.git:
			Intent intent = new Intent(Intent.ACTION_VIEW, GITHUB_URL);
			startActivity(intent);
			break;

		default:
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}
}
