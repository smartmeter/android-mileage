package com.evancharlton.mileage.adapters;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.evancharlton.mileage.R;
import com.evancharlton.mileage.dao.CachedValue;
import com.evancharlton.mileage.dao.Vehicle;
import com.evancharlton.mileage.provider.Statistics;
import com.evancharlton.mileage.provider.Statistics.Statistic;
import com.evancharlton.mileage.provider.Statistics.StatisticsGroup;
import com.evancharlton.mileage.provider.tables.CacheTable;

public class VehicleStatisticsAdapter extends BaseAdapter {
	private static final String TAG = "VehicleStatisticsAdapter";
	private static final int TYPE_STATISTIC = 0;
	private static final int TYPE_GROUP = 1;
	private static final ArrayList<StatisticHolder> mObjects = new ArrayList<StatisticHolder>();
	private static final HashMap<String, String> mValues = new HashMap<String, String>();

	private final Context mContext;
	private final Vehicle mVehicle;
	private final LayoutInflater mInflater;

	private Cursor mCursor;

	public VehicleStatisticsAdapter(Context context, Vehicle vehicle) {
		mInflater = LayoutInflater.from(context);
		mContext = context;
		mVehicle = vehicle;

		for (StatisticsGroup group : Statistics.GROUPS) {
			mObjects.add(new StatisticHolder(context, group));
			for (Statistic statistic : group.getStatistics()) {
				mObjects.add(new StatisticHolder(context, statistic, vehicle));
			}
		}
	}

	public void setValue(Statistic statistic, double value) {
		setValue(statistic, statistic.format(mContext, mVehicle, value));
	}

	public void setValue(Statistic statistic, String value) {
		mValues.put(statistic.getKey(), value);
	}

	public void flush() {
		// flush the statistics to disk
		new Thread() {
			@Override
			public void run() {
				Log.d(TAG, "Erasing the cache ...");
				// erase the existing cache
				String where = CachedValue.ITEM + " = ?";
				String[] selectionArgs = new String[] {
					String.valueOf(mVehicle.getId())
				};
				mContext.getContentResolver().delete(CacheTable.BASE_URI, where, selectionArgs);

				Log.d(TAG, "Building new values ...");
				// write the new cache
				ContentValues[] values = new ContentValues[mObjects.size()];
				final int length = mObjects.size();
				final long vehicleId = mVehicle.getId();
				for (int i = 0; i < length; i++) {
					ContentValues v = new ContentValues();
					v.put(CachedValue.ITEM, vehicleId);
					v.put(CachedValue.VALID, true);
					v.put(CachedValue.VALUE, mValues.get(mObjects.get(i).key));
					v.put(CachedValue.KEY, mObjects.get(i).key);
					values[i] = v;
				}
				Log.d(TAG, "Writing to database ...");
				mContext.getContentResolver().bulkInsert(CacheTable.BASE_URI, values);
				Log.d(TAG, "Caching complete!");
			}
		}.start();
	}

	public void changeCursor(Cursor cursor) {
		mCursor = cursor;

		// TODO(3.1) - This isn't efficient and it runs on the UI thread. Boo!
		cursor.moveToPosition(-1);
		final int key_position = cursor.getColumnIndex(CachedValue.KEY);
		final int value_position = cursor.getColumnIndex(CachedValue.VALUE);
		while (cursor.moveToNext()) {
			String key = cursor.getString(key_position);
			String value = cursor.getString(value_position);
			mValues.put(key, value);
		}

		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return mObjects.size();
	}

	@Override
	public Object getItem(int position) {
		return mObjects.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		StatisticHolder holder = mObjects.get(position);

		if (convertView == null) {
			switch (holder.type) {
				case TYPE_STATISTIC:
					convertView = mInflater.inflate(R.layout.statistic, parent, false);
					break;
				case TYPE_GROUP:
					convertView = mInflater.inflate(R.layout.divider, parent, false);
					break;
			}
		}

		ViewHolder viewHolder = (ViewHolder) convertView.getTag();
		if (viewHolder == null) {
			viewHolder = new ViewHolder(convertView);
		}

		switch (holder.type) {
			case TYPE_STATISTIC:
				viewHolder.text.setText(holder.text);
				viewHolder.value.setText(mValues.get(holder.key));
				break;
			case TYPE_GROUP:
				viewHolder.text.setText(holder.text);
				break;
		}

		return convertView;
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public int getItemViewType(int position) {
		return mObjects.get(position).type;
	}

	@Override
	public boolean isEnabled(int position) {
		return getItemViewType(position) == TYPE_STATISTIC;
	}

	private static class StatisticHolder {
		public final String key;
		public final String text;
		public final int type;

		public StatisticHolder(Context context, Statistic statistic, Vehicle vehicle) {
			key = statistic.getKey();
			text = statistic.getLabel(context, vehicle);
			type = TYPE_STATISTIC;
		}

		public StatisticHolder(Context context, StatisticsGroup group) {
			key = null;
			text = context.getString(group.getLabel());
			type = TYPE_GROUP;
		}
	}

	private static class ViewHolder {
		public final TextView text;
		public final TextView value;

		public ViewHolder(View convertView) {
			text = (TextView) convertView.findViewById(android.R.id.text1);
			value = (TextView) convertView.findViewById(android.R.id.text2);
			convertView.setTag(this);
		}
	}
}
