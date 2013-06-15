package org.androfarsh.demo;

import java.lang.reflect.Field;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

@SuppressWarnings("rawtypes")
public class MainActivity extends BaseActivity {
	private static final String TAG = MainActivity.class.getSimpleName();
	private static final Class[] DEMOS = new Class[]{SimpleDemoActivty.class, RootViewDemoActivty.class};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_screen);

		ListView list = (ListView) findViewById(android.R.id.list);
		list.setAdapter(new DemoActivityAdapter());
		list.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Intent intent = new Intent(MainActivity.this, DEMOS[position]);
				startActivity(intent);
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.git:

			break;

		default:
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	private final class DemoActivityAdapter extends BaseAdapter {
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null){
				convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
			}

			Class<? extends Activity> clazz = getItem(position);
			try {
				Field title = clazz.getField("TITLE");
				((TextView)convertView).setText(title.getInt(clazz));
			} catch (IllegalArgumentException e) {
				Log.e(TAG, e.getMessage(), e);
			} catch (IllegalAccessException e) {
				Log.e(TAG, e.getMessage(), e);
			} catch (NoSuchFieldException e) {
				((TextView)convertView).setText(clazz.getSimpleName());
				Log.e(TAG, e.getMessage(), e);
			}
			return convertView;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Class<? extends Activity> getItem(int position) {
			return DEMOS[position];
		}

		@Override
		public int getCount() {
			return DEMOS.length;
		}
	}
}
