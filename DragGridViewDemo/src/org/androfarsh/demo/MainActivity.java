package org.androfarsh.demo;

import java.lang.reflect.Field;

import org.androfarsh.demo.drahgrid.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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
	private static final Class[] DEMOS = new Class[]{SimpleDemoActivity.class, RootViewDemoActivity.class};

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
		private final int[] colors;

		public DemoActivityAdapter() {
			colors = getResources().getIntArray(R.array.colors);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null){
				convertView = getLayoutInflater().inflate(R.layout.demo_item, parent, false);
				TAG tag = new TAG();
				tag.color = convertView.findViewById(R.id.color);
				tag.text = (TextView) convertView.findViewById(R.id.text);
				convertView.setTag(tag);
			}

			final TAG tag = (org.androfarsh.demo.MainActivity.TAG) convertView.getTag();
			final Class<? extends Activity> clazz = getItem(position);
			try {
				Field title = clazz.getField("TITLE");
				tag.text.setText(title.getInt(clazz));
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (NoSuchFieldException e) {
				tag.text.setText(clazz.getSimpleName());
			}

			tag.color.setBackgroundColor(getColor(position));
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

		private int getColor(int position){
			if (position >= colors.length){
				position = position - ((position / colors.length)*colors.length);
			}
			return colors[position];
		}
	}

	static class TAG {
		View color;
		TextView text;
	}
}
