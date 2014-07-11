/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.plugin.tracker;

import java.util.Calendar;
import java.util.Date;

import android.content.ContentProviderClient;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
//import android.util.Log;

import com.androzic.data.TrackerFootprins;
import com.androzic.data.Tracker;
import com.androzic.provider.DataContract;
import com.androzic.Log;

/**
 * This class helps open, create, and upgrade the database file.
 */
class TrackerHelper extends SQLiteOpenHelper
{
	private static final String DATABASE_NAME = "tracker.db";
	private static final int DATABASE_VERSION = 3;
	static final String TABLE_TRACKERS = "trackers";
	static final String TABLE_HISTORY = "history";
	private static final String TAG = "TrackerDataAccess";
	/**
	 * ID
	 * <P>
	 * Type: LONG
	 * </P>
	 */
	public static final String _TRACKER_ID = "_id";
	/**
	 * Map object ID (mapping ID to Androzic map objects)
	 * <P>
	 * Type: LONG
	 * </P>
	 */
	public static final String MOID = "moid";
	/**
	 * Title
	 * <P>
	 * Type: TEXT
	 * </P>
	 */
	public static final String TITLE = "title";
	/**
	 * IMEI
	 * <P>
	 * Type: TEXT
	 * </P>
	 */
	public static final String IMEI = "imei";
	/**
	 * Sender
	 * <P>
	 * Type: TEXT
	 * </P>
	 */
	public static final String SENDER = "sender";
	/**
	 * Icon
	 * <P>
	 * Type: TEXT
	 * </P>
	 */
	public static final String ICON = "icon";
	/**
	 * Latitude
	 * <P>
	 * Type: DOUBLE
	 * </P>
	 */
	public static final String LATITUDE = "latitude";
	/**
	 * Longitude
	 * <P>
	 * Type: DOUBLE
	 * </P>
	 */
	public static final String LONGITUDE = "longitude";
	/**
	 * Speed
	 * <P>
	 * Type: FLOAT
	 * </P>
	 */
	public static final String SPEED = "speed";
	/**
	 * Battery level
	 * <P>
	 * Type: INTEGER
	 * </P>
	 */
	public static final String BATTERY = "battery";
	/**
	 * Signal level
	 * <P>
	 * Type: INTEGER
	 * </P>
	 */
	public static final String SIGNAL = "signal";
	/**
	 * The timestamp for when the note was last modified
	 * <P>
	 * Type: LONG (long from System.curentTimeMillis())
	 * </P>
	 */
	public static final String MODIFIED = "modified";
	// foreign key for tracker history
	public static final String TRACKER_ID = "tracker_id";
	// The timestamp for when the position was received
	public static final String TIME = "time";
	// key for history point
	public static final String _POINT_ID = "_point_id";
	
	private static final String[] trackerColumnsId = new String[] { _TRACKER_ID };
	private static final String[] trackersColumnsAll = new String[] { _TRACKER_ID, MOID, TITLE, ICON, IMEI, SENDER /*, LATITUDE, LONGITUDE, SPEED, BATTERY, SIGNAL, MODIFIED*/ };

	private static final String[] pointColumnsId = new String[] { _POINT_ID };
	private static final String[] pointColumnsAll = new String[] { _POINT_ID, LATITUDE, LONGITUDE, SPEED, BATTERY, SIGNAL, TIME };

	private ContentProviderClient mapObjContentProvider;
	
	private int prefMarkerColor = Color.BLUE;
	private int prefFootprintsCount = 0;
	private Context context;

	TrackerHelper(Context context)
	{

		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		
		Log.w(TAG, "DATABASE_VERSION = " + DATABASE_VERSION);
		
		mapObjContentProvider = context.getContentResolver().acquireContentProviderClient(DataContract.MAPOBJECTS_URI);
		
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		prefFootprintsCount = Integer.parseInt(sharedPreferences.getString(context.getString(R.string.pref_tracker_footprints_count),
																	   context.getString(R.string.def_tracker_footprints_count) 
				                               							)
				                           );
		prefMarkerColor = sharedPreferences.getInt(context.getString(R.string.pref_tracker_markercolor), context.getResources().getColor(R.color.marker));
		
		//dateFormat = new DateFormat();
		this.context = context;
		
	}
	
	public void processIncomingTracker(Tracker tracker, boolean updateAndrozicApp) throws RemoteException
	{
		Log.w(TAG, "IN tracker.sender = " + tracker.sender);
		Log.w(TAG, "IN tracker.time = " + tracker.time);
		Log.w(TAG, "IN tracker.latitude = " + tracker.latitude);
		Log.w(TAG, "IN tracker.longitude = " + tracker.longitude);
		
		Tracker currentTracker = getTracker(tracker.sender);
		
		if(currentTracker != null)
		{
			currentTracker = updateTrackerPositionInDB(tracker);
			updateTrackerInAndrozicApp(currentTracker);
		}
		else
		{
			tracker.name = tracker.sender;
			
			long moid = sendNewTrackerInAndrozicApp(tracker);
			
			tracker.moid = moid;
			
			insertNewTrackerInDB(tracker);
		}
		
	}

	public void processIncomingTracker(Tracker tracker) throws RemoteException
	{
		processIncomingTracker(tracker, true);
	}
	
	private Tracker updateTrackerPositionInDB(Tracker tracker)
	{
		Log.w(TAG, "IN tracker.sender = " + tracker.sender);
		//Log.w(TAG, "IN tracker.time = " + tracker.time);
		//Log.w(TAG, "IN tracker.latitude = " + tracker.latitude);
		//Log.w(TAG, "IN tracker.longitude = " + tracker.longitude);
		
		Tracker currentTracker = getTracker(tracker.sender);
		
		Log.w(TAG, "currentTracker.time = " + currentTracker.time);
		
		
		if (tracker.time == 0)
			tracker.time = System.currentTimeMillis();

		if(currentTracker.time != tracker.time ||
		   currentTracker.latitude != tracker.latitude ||
		   currentTracker.longitude != tracker.longitude ) 
		{
			insertNewFootprintInDB(tracker);
		}
		
		if(currentTracker.time <= tracker.time)
		{			
			currentTracker.longitude = tracker.longitude;
			currentTracker.latitude = tracker.latitude;
			currentTracker.time = tracker.time;
		}
		
		Log.w(TAG, "ret > currentTracker.time = " + currentTracker.time);
		
		return currentTracker;
	}
	
	/**
	 * Update tracker data in CntentProvider for show it on Androzic map
	 * 
	 * @throws RemoteException
	 */
	private void updateTrackerInAndrozicApp(Tracker tracker) throws RemoteException
	{
		Log.w(TAG, "IN tracker.sender = " + tracker.sender);
		//Log.w(TAG, "IN tracker.time = " + tracker.time);
		//Log.w(TAG, "IN tracker.latitude = " + tracker.latitude);
		//Log.w(TAG, "IN tracker.longitude = " + tracker.longitude);
		
		ContentValues values = new ContentValues();
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LATITUDE_COLUMN], tracker.latitude);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LONGITUDE_COLUMN], tracker.longitude);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_NAME_COLUMN], tracker.name);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_IMAGE_COLUMN], tracker.image);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_BACKCOLOR_COLUMN], prefMarkerColor);
		
		Uri uri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, tracker.moid);
		Log.w(TAG, "Tracker uri = " + ( uri != null ? uri : "NULL" ) );
		
		mapObjContentProvider.update(uri, values, null, null);
		
		setFootprintsInAndrozicApp(tracker);
		
	}
	
	/**
	 * Update trackers footprints data in CntentProvider for show it on Androzic map
	 * 
	 * @throws RemoteException
	 */
	private void setFootprintsInAndrozicApp(Tracker tracker) throws RemoteException
	{
		Log.w(TAG, "IN tracker.sender = " + tracker.sender);
		
		Cursor cursor = getTrackerFootprints(tracker._id);
		
		cursor.moveToFirst(); //skip first point
		
		if (prefFootprintsCount > 0 && cursor.moveToNext())
		{
			TrackerFootprins footprint = new TrackerFootprins();
			
			ContentValues values = new ContentValues();
			
			do
			{
				
				footprint = getTrackerFootprint(cursor);
				
				Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(footprint.time);
				Date date = calendar.getTime();
				String time = DateFormat.getTimeFormat(context).format(date);
				
				String pointName = tracker.name + " " + time;
			
				values.clear();
				
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LATITUDE_COLUMN], footprint.latitude);
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LONGITUDE_COLUMN], footprint.longitude);
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_NAME_COLUMN], pointName);
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_IMAGE_COLUMN], "");
				values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_BACKCOLOR_COLUMN], prefMarkerColor);

				if (footprint.moid <= 0)
				{
					Uri uri = mapObjContentProvider.insert(DataContract.MAPOBJECTS_URI, values);
					if (uri != null)
					{
						setFootprintMoidInDB(String.valueOf(footprint._id), String.valueOf(ContentUris.parseId(uri)));
					}
				}
				else
				{
					Uri uri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, footprint.moid);
					mapObjContentProvider.update(uri, values, null, null);
				}
				
			}
			while (cursor.moveToNext() && --prefFootprintsCount > 0);
			
			while (!cursor.isAfterLast())//erase last points from map for preserve displayed footprints count
			{
				footprint = getTrackerFootprint(cursor);
				if (footprint.moid > 0)
				{
					Uri uri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, footprint.moid);
					mapObjContentProvider.delete(uri, null, null);
					
				    setFootprintMoidInDB(footprint._id, 0);
				}
				
				cursor.moveToNext();
			}
			
		}
		
		cursor.close();
	}
	
	
	/**
	 * Sends tracker data in CntentProvider for show it on Androzic map
	 * 
	 * @throws RemoteException
	 */
	private long sendNewTrackerInAndrozicApp(Tracker tracker) throws RemoteException
	{
		Log.w(TAG, "IN tracker.sender = " + tracker.sender);
		
		ContentValues values = new ContentValues();
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LATITUDE_COLUMN], tracker.latitude);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LONGITUDE_COLUMN], tracker.longitude);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_NAME_COLUMN], tracker.name);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_IMAGE_COLUMN], tracker.image);
		values.put(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_BACKCOLOR_COLUMN], prefMarkerColor);
		
		Uri uri = mapObjContentProvider.insert(DataContract.MAPOBJECTS_URI, values);
		if (uri != null)
		{
			return ContentUris.parseId(uri);
		}
		
		return 0;
	}
	
	private void insertNewTrackerInDB(Tracker tracker)
	{
		Log.w(TAG, "IN tracker.sender = " + tracker.sender);
		
		ContentValues values = new ContentValues();
		SQLiteDatabase db = getWritableDatabase();
		
		if (tracker.time == 0)
			tracker.time = System.currentTimeMillis();
		
		values.clear();
		
		values.put(MOID, tracker.moid);
		values.put(TITLE, tracker.name);
		values.put(ICON, tracker.image);
		values.put(IMEI, tracker.imei);
		values.put(SENDER, tracker.sender);
				
		tracker._id = db.insert(TABLE_TRACKERS, null, values);
		
		insertNewFootprintInDB(tracker);		
	}
	
	private long insertNewFootprintInDB(Tracker tracker)
	{
		Log.w(TAG, "IN tracker.sender = " + tracker.sender);
		
		ContentValues values = new ContentValues();
		SQLiteDatabase db = getWritableDatabase();
		
		values.clear();
		values.put(TRACKER_ID, tracker._id);
		values.put(LATITUDE, tracker.latitude);
		values.put(LONGITUDE, tracker.longitude);
		values.put(SPEED, tracker.speed);
		values.put(BATTERY, tracker.battery);
		values.put(SIGNAL, tracker.signal);
		values.put(TIME, Long.valueOf(tracker.time));
		
		return db.insert(TABLE_HISTORY, null, values);
	}
	
	public long updateTracker(Tracker tracker)
	{
		Log.w(TAG, ">>>> updateTracker(" + tracker.sender + ")");
		
		ContentValues values = new ContentValues();
		SQLiteDatabase db = getWritableDatabase();
		
		if (tracker.time == 0)
			tracker.time = System.currentTimeMillis();

		Tracker dbTracker = getTracker(tracker.sender);
		if (dbTracker != null)
		{
			// Preserve user defined properties
			if ("".equals(tracker.name))
				tracker.name = dbTracker.name;
			if ("".equals(tracker.image))
				tracker.image = dbTracker.image;

			// Preserve map object ID if it is no set
			if (tracker.moid == Long.MIN_VALUE)
				tracker.moid = dbTracker.moid;

			// Copy tracker ID
			tracker._id = dbTracker._id;
		}
		
		
		// Set default name for new tracker
		if ("".equals(tracker.name))
			tracker.name = tracker.sender;
			
		values.clear();
		
		values.put(MOID, tracker.moid);
		values.put(TITLE, tracker.name);
		values.put(ICON, tracker.image);
		values.put(IMEI, tracker.imei);
		values.put(SENDER, tracker.sender);
				
		if (dbTracker == null)
		{
			tracker._id = db.insert(TABLE_TRACKERS, null, values);			
		}
		else if (tracker.time >= dbTracker.time )
		{
			tracker._id = dbTracker._id;
			
			db.update(TABLE_TRACKERS, values, _TRACKER_ID + " = ?", new String[] { String.valueOf(dbTracker._id) });
		}
		
		
		if (tracker._id != -1 
		    && ( dbTracker == null || ( dbTracker != null && tracker.time != dbTracker.time )) )
		{
			values.clear();
			values.put(TRACKER_ID, tracker._id);
			values.put(LATITUDE, tracker.latitude);
			values.put(LONGITUDE, tracker.longitude);
			values.put(SPEED, tracker.speed);
			values.put(BATTERY, tracker.battery);
			values.put(SIGNAL, tracker.signal);
			values.put(TIME, Long.valueOf(tracker.time));
			
			db.insert(TABLE_HISTORY, null, values);
		}
		
		if( dbTracker != null && ( tracker.time < dbTracker.time ) )
		{
			tracker = dbTracker;
		}
		
		Log.w(TAG, ">>>> updateTracker tracker.time = " + tracker.time);
		return tracker._id;
	}


	public void removeTracker(Tracker tracker)
	{
		Log.w(TAG, ">>>> removeTracker(" + tracker.sender + ")");
		SQLiteDatabase db = getWritableDatabase();
		Cursor cursor = db.query(TrackerHelper.TABLE_TRACKERS, trackerColumnsId, SENDER + " = ?", new String[] { tracker.sender }, null, null, null);
		if (cursor.getCount() > 0)
		{
			cursor.moveToFirst();
			long id = cursor.getLong(cursor.getColumnIndex(_TRACKER_ID));
			cursor.close();
			db.delete(TrackerHelper.TABLE_TRACKERS, _TRACKER_ID + " = ?", new String[] { String.valueOf(id) });
		}
	}

	public Tracker getTracker(String sender)
	{
		Log.w(TAG, ">>>> getTracker(" + sender + ")");
		
		SQLiteDatabase db = getReadableDatabase();
		
		Cursor cursor = db.query(TABLE_TRACKERS, null, SENDER + " = ?", new String[] { sender }, null, null, null);
				
		if (cursor.getCount() > 0)
		{
			cursor.moveToFirst();
			Tracker tracker = getFullInfoTracker(cursor);
			cursor.close();
			return tracker;
		}
		return null;
	}

	public Tracker getFullInfoTracker(Cursor cursor)
	{
		Log.w(TAG, ">>>> getFullInfoTracker(Cursor cursor)");
		
		SQLiteDatabase db = getReadableDatabase();
		
		Cursor historyCur = db.query(TABLE_HISTORY, null, TRACKER_ID + " = ?", new String[] { cursor.getString(cursor.getColumnIndex(_TRACKER_ID)) }, null, null, TIME + " DESC");
		
		if (historyCur.getCount() == 0)
		{
			return null;
		}		
		
		historyCur.moveToFirst();
			
		Tracker tracker = new Tracker();
		
		tracker._id = cursor.getLong(cursor.getColumnIndex(_TRACKER_ID));
		tracker.moid = cursor.getLong(cursor.getColumnIndex(MOID));
		tracker.name = cursor.getString(cursor.getColumnIndex(TITLE));
		tracker.imei = cursor.getString(cursor.getColumnIndex(IMEI));
		tracker.sender = cursor.getString(cursor.getColumnIndex(SENDER));
		tracker.image = cursor.getString(cursor.getColumnIndex(ICON));
		
		
		tracker.latitude = historyCur.getDouble(historyCur.getColumnIndex(LATITUDE));
		tracker.longitude = historyCur.getDouble(historyCur.getColumnIndex(LONGITUDE));
		tracker.speed = historyCur.getFloat(historyCur.getColumnIndex(SPEED));
		tracker.battery = historyCur.getInt(historyCur.getColumnIndex(BATTERY));
		tracker.signal = historyCur.getInt(historyCur.getColumnIndex(SIGNAL));
		tracker.time = historyCur.getLong(historyCur.getColumnIndex(TIME));
		
		return tracker;
	}

	public TrackerFootprins getTrackerFootprint(Cursor cursor)
	{
		Log.w(TAG, ">>>> getTrackerFootprint(Cursor cursor)");
		
		TrackerFootprins point = new TrackerFootprins();
		
		if (cursor.getCount() == 0)
		{
			return null;
		}
		
		point._id = cursor.getLong(cursor.getColumnIndex(_POINT_ID));
		point.moid = cursor.getLong(cursor.getColumnIndex(MOID));
		point.latitude = cursor.getDouble(cursor.getColumnIndex(LATITUDE));
		point.longitude = cursor.getDouble(cursor.getColumnIndex(LONGITUDE));
		point.speed = cursor.getFloat(cursor.getColumnIndex(SPEED));
		point.battery = cursor.getInt(cursor.getColumnIndex(BATTERY));
		point.signal = cursor.getInt(cursor.getColumnIndex(SIGNAL));
		point.time = cursor.getLong(cursor.getColumnIndex(TIME));
		
		return point;
	}
	
	
	public Cursor getHeadersOfTrackers()
	{	
		Log.w(TAG, ">>>> getHeadersOfTrackers()");
		
		SQLiteDatabase db = getReadableDatabase();
		
		return db.query(TrackerHelper.TABLE_TRACKERS, null , null, null, null, null, null);
	}

	public Cursor getTrackerFootprints(long trackerId)
	{	
		Log.w(TAG, ">>>> getTrackerFootprints()");
		
		SQLiteDatabase db = getReadableDatabase();
		
		return db.query(TrackerHelper.TABLE_HISTORY, null , TRACKER_ID + " = ?" , new String[] {String.valueOf(trackerId)}, null, null, TIME + " DESC");
	}
	
	private int setFootprintMoidInDB(String footprintId, String moid)
	{
		Log.w(TAG, "IN footprintIdr = " + footprintId);
		Log.w(TAG, "IN moid = " + moid);
		
		SQLiteDatabase db = getReadableDatabase();
		
		ContentValues values = new ContentValues();
		
		values.put(MOID, moid);
		
		return db.update(TABLE_HISTORY, values, _POINT_ID + " = ?", new String[] { footprintId });
	}
	
	public int setFootprintMoidInDB(long footprintId, long moid)
	{
		Log.w(TAG, "IN footprintIdr = " + footprintId);
		Log.w(TAG, "IN moid = " + moid);
		
		return setFootprintMoidInDB( String.valueOf(footprintId), String.valueOf(moid)); 
	}
	
	public int clearFootprintMoids(String trackerId)
	{
		Log.w(TAG, ">>>> clearFootprintMoids(" + trackerId + ")");
		
		SQLiteDatabase db = getReadableDatabase();
		
		ContentValues values = new ContentValues();
		
		values.put(MOID, 0);
		
		return db.update(TABLE_HISTORY, values, TRACKER_ID + " = ?", new String[] { trackerId });
	}
	
	@Override
	public void onCreate(SQLiteDatabase db)
	{
		Log.w(TAG, ">>>> onCreate");
		
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRACKERS);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
		
		db.execSQL("PRAGMA foreign_keys = ON;");
		
		db.execSQL("CREATE TABLE " + TABLE_TRACKERS + " (" + _TRACKER_ID + " INTEGER PRIMARY KEY," 
													   + MOID + " INTEGER," 
													   + IMEI + " TEXT," 
													   + SENDER + " TEXT NOT NULL UNIQUE," 
													   + TITLE + " TEXT," 
													   + ICON + " TEXT"  
											      + ");");
		
		db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" + _POINT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
													   + TRACKER_ID + " INTEGER NOT NULL," 
													   + MOID + " INTEGER,"
													   + LATITUDE + " REAL," 
													   + LONGITUDE + " REAL," 
													   + SPEED + " REAL," 
													   + BATTERY + " INTEGER," 
													   + SIGNAL + " INTEGER," 
												       + TIME + " INTEGER," 
												       + "FOREIGN KEY (" + TRACKER_ID + ") REFERENCES " + TABLE_TRACKERS +"(" + _TRACKER_ID + ") ON DELETE CASCADE"
											      + ");");
		
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		Log.w(TAG, " --- onUpgrade database from " + oldVersion
		          + " to " + newVersion + " version --- ");
		
		db.beginTransaction();
		
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRACKERS);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
		onCreate(db);
		
		db.endTransaction();
	}
}
