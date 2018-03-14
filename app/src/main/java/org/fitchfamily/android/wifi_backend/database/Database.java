package org.fitchfamily.android.wifi_backend.database;

/*
 *  WiFi Backend for Unified Network Location
 *  Copyright (C) 2014,2015  Tod Fitch
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.Configuration;
import org.fitchfamily.android.wifi_backend.util.SimpleLocation;

import java.util.ArrayList;
import java.util.List;

public class Database extends SQLiteOpenHelper {
    public static String ACTION_DATA_CHANGED = "org.fitchfamily.android.wifi_backend.database.DATA_CHANGED";

    private static final String TAG = "WiFiBackendDB";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int VERSION = 5;
    private static final String NAME = "wifi.db";

    public static final String TABLE_SAMPLES = "APs";
    @Deprecated
    public static final String COL_BSSID = "bssid";
    public static final String COL_RFID = "rfID";
    public static final String COL_MOVED_GUARD = "move_guard";
    public static final String COL_SSID = "ssid";
    public static final String COL_TYPE = "type";
    public static final String COL_LATITUDE = "latitude";
    public static final String COL_LONGITUDE = "longitude";
    public static final String COL_RADIUS = "radius";
    public static final String COL_CHANGED = "changed";
    public static final String COL_LAT1 = "lat1";
    public static final String COL_LON1 = "lon1";
    public static final String COL_LAT2 = "lat2";
    public static final String COL_LON2 = "lon2";
    public static final String COL_LAT3 = "lat3";
    public static final String COL_LON3 = "lon3";
    // not used anymore
    @Deprecated
    private static final String COL_D12 = "d12";
    // not used anymore
    @Deprecated
    private static final String COL_D23 = "d23";
    // not used anymore
    @Deprecated
    private static final String COL_D31 = "d31";

    public static final Integer TYPE_WIFI = 0;
    public static final Integer TYPE_CELL = 1;

    public static final Integer CHANGED_NONE = 0;
    public static final Integer CHANGED_AP1 = 1;
    public static final Integer CHANGED_AP2 = 2;
    public static final Integer CHANGED_AP3 = 4;

    private SQLiteStatement sqlSampleInsert;
    private SQLiteStatement sqlSampleUpdate;
    private SQLiteStatement sqlAPdrop;
    private SQLiteStatement sqlAPdropAll;
    private SQLiteDatabase db = null;
    private final LocalBroadcastManager localBroadcastManager;
    private final Context context;

    public Database(Context context) {
        super(context, NAME, null, VERSION);

        this.context = context;
        localBroadcastManager = LocalBroadcastManager.getInstance(context.getApplicationContext());
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        // Always create version 0 of database, then update the schema
        // in the same order it might occur "in the wild". Avoids having
        // to check to see if the table exists (may be old version)
        // or not (can be new version).
        sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SAMPLES + "(" +
                COL_BSSID + " STRING PRIMARY KEY, " +
                COL_LATITUDE + " REAL, " +
                COL_LONGITUDE + " REAL, " +
                COL_LAT1 + " REAL, " +
                COL_LON1 + " REAL, " +
                COL_LAT2 + " REAL, " +
                COL_LON2 + " REAL, " +
                COL_LAT3 + " REAL, " +
                COL_LON3 + " REAL, " +
                COL_D12 + " REAL, " +
                COL_D23 + " REAL, " +
                COL_D31 + " REAL);");

        onUpgrade(sqLiteDatabase, 1, VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion < 2) { // upgrade to 2
            sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_SAMPLES +
                    " ADD COLUMN " + COL_MOVED_GUARD +
                    " INTEGER;");

            sqLiteDatabase.execSQL("UPDATE " + TABLE_SAMPLES +
                    " SET " + COL_MOVED_GUARD + "=0;");
        }

        if (oldVersion < 3) { // upgrade to 3
            sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_SAMPLES +
                    " ADD COLUMN " + COL_RADIUS +
                    " REAL;");

            sqLiteDatabase.execSQL("UPDATE " + TABLE_SAMPLES +
                    " SET " + COL_RADIUS + "=-1.0;");
        }

        if(oldVersion < 4) {  // upgrade to 4
            sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_SAMPLES + " ADD COLUMN " + COL_SSID + " TEXT;");
        }

        if(oldVersion < 5) {  // upgrade to 5
            // Sqlite3 does not support dropping columns so we create a new table with our
            // current fields and copy the old data into it.
            sqLiteDatabase.execSQL("BEGIN TRANSACTION;");
            sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_SAMPLES + " RENAME TO " + TABLE_SAMPLES + "_old;");
            sqLiteDatabase.execSQL("CREATE TABLE " + TABLE_SAMPLES + "(" +
                    COL_RFID + " STRING PRIMARY KEY, " +
                    COL_TYPE + " INTEGER, " +
                    COL_SSID + " TEXT, " +
                    COL_LATITUDE + " REAL, " +
                    COL_LONGITUDE + " REAL, " +
                    COL_RADIUS + " REAL, " +
                    COL_MOVED_GUARD + " INTEGER, " +
                    COL_CHANGED + " INTEGER, " +
                    COL_LAT1 + " REAL, " +
                    COL_LON1 + " REAL, " +
                    COL_LAT2 + " REAL, " +
                    COL_LON2 + " REAL, " +
                    COL_LAT3 + " REAL, " +
                    COL_LON3 + " REAL);");

            sqLiteDatabase.execSQL("INSERT INTO " + TABLE_SAMPLES + "(" +
                    COL_RFID + ", " +
                    COL_SSID + ", " +
                    COL_LATITUDE + ", " +
                    COL_LONGITUDE + ", " +
                    COL_RADIUS + ", " +
                    COL_MOVED_GUARD + ", " +
                    COL_LAT1 + ", " +
                    COL_LON1 + ", " +
                    COL_LAT2 + ", " +
                    COL_LON2 + ", " +
                    COL_LAT3 + ", " +
                    COL_LON3 +
                ") SELECT " +
                    COL_BSSID + ", " +
                    COL_SSID + ", " +
                    COL_LATITUDE + ", " +
                    COL_LONGITUDE + ", " +
                    COL_RADIUS + ", " +
                    COL_MOVED_GUARD + ", " +
                    COL_LAT1 + ", " +
                    COL_LON1 + ", " +
                    COL_LAT2 + ", " +
                    COL_LON2 + ", " +
                    COL_LAT3 + ", " +
                    COL_LON3 +
                    " FROM " + TABLE_SAMPLES + "_old;");
            sqLiteDatabase.execSQL("DROP TABLE " + TABLE_SAMPLES + "_old;");
            sqLiteDatabase.execSQL("UPDATE " + TABLE_SAMPLES + " SET " + COL_TYPE + "=" + TYPE_WIFI + ";");
            sqLiteDatabase.execSQL("UPDATE " + TABLE_SAMPLES + " SET " + COL_CHANGED + "=" + (CHANGED_AP1+CHANGED_AP2+CHANGED_AP3) + ";");
            sqLiteDatabase.execSQL("COMMIT;");
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);

        this.db = db;
        sqlSampleInsert = db.compileStatement("INSERT INTO " +
                TABLE_SAMPLES + "("+
                COL_RFID + ", " +
                COL_TYPE + ", " +
                COL_SSID + ", " +
                COL_LATITUDE + ", " +
                COL_LONGITUDE + ", " +
                COL_RADIUS + ", " +
                COL_MOVED_GUARD + ", " +
                COL_CHANGED + ", " +
                COL_LAT1 + ", " +
                COL_LON1 + ", " +
                COL_LAT2 + ", " +
                COL_LON2 + ", " +
                COL_LAT3 + ", " +
                COL_LON3 + ") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");

        sqlSampleUpdate = db.compileStatement("UPDATE " +
                TABLE_SAMPLES + " SET "+
                COL_SSID + "=?, " +
                COL_LATITUDE + "=?, " +
                COL_LONGITUDE + "=?, " +
                COL_RADIUS + "=?, " +
                COL_MOVED_GUARD + "=?, " +
                COL_CHANGED + "=?, " +
                COL_LAT1 + "=?, " +
                COL_LON1 + "=?, " +
                COL_LAT2 + "=?, " +
                COL_LON2 + "=?, " +
                COL_LAT3 + "=?, " +
                COL_LON3 + "=? " +
                "WHERE " + COL_RFID + "=?;");

        sqlAPdrop = db.compileStatement("DELETE FROM " +
                TABLE_SAMPLES +
                " WHERE " + COL_RFID + "=?;");

        sqlAPdropAll = db.compileStatement("DELETE FROM " + TABLE_SAMPLES);
    }

    public void exportComplete() {
        ensureOpened();
        db.execSQL("UPDATE " + TABLE_SAMPLES + " SET " + COL_CHANGED + "=" + CHANGED_NONE + ";");
        onAccessPointCountChanged();
    }
    
    public void dropAP(String rfId) {
        final String canonicalBSSID = AccessPoint.bssid(rfId);

        if (DEBUG) {
            Log.i(TAG, "Dropping " + canonicalBSSID + " from db");
        }

        sqlAPdrop.bindString(1, canonicalBSSID);
        sqlAPdrop.executeInsert();
        sqlAPdrop.clearBindings();

        onAccessPointCountChanged();
    }

    protected void dropAllAPs() {
        sqlAPdropAll.execute();

        onAccessPointCountChanged();
    }

    public int getAccessPointCount(boolean changed) {
        String whereClause = "";
        if (changed) {
            whereClause = " WHERE " + COL_CHANGED + "<> 0";
        }
        Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_SAMPLES + whereClause + ";", null);

        try {
            if(cursor.moveToFirst()) {
                return (int) cursor.getLong(0);
            } else {
                return 0;
            }
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    @Nullable
    public SimpleLocation getLocation(String rfId) {
        final long entryTime = System.currentTimeMillis();
        final String canonicalBSSID = AccessPoint.bssid(rfId);

        Cursor c = getReadableDatabase().query(TABLE_SAMPLES,
                new String[]{COL_LATITUDE,
                        COL_LONGITUDE,
                        COL_RADIUS,
                        COL_LAT1,
                        COL_LON1,
                        COL_LAT2,
                        COL_LON2,
                        COL_LAT3,
                        COL_LON3
                },
                COL_RFID+"=? AND " +
                        COL_MOVED_GUARD + "=0",
                new String[]{canonicalBSSID},
                null,
                null,
                null);

        try {
            if (c != null && c.moveToFirst()) {
                // We only want to return location data for APs that we have received at least
                // three samples.
                Integer sampleCount = 0;
                if (c.getDouble(3) != 0.d || c.getDouble(4) != 0.d)
                    sampleCount++;
                if (c.getDouble(5) != 0.d || c.getDouble(6) != 0.d)
                    sampleCount++;
                if (c.getDouble(7) != 0.d || c.getDouble(8) != 0.d)
                    sampleCount++;
                if (sampleCount == 3) {

                    // radius is our observed coverage but it can be quite small, as little as
                    // zero if we have only one sample. We want to report an accuracy value that
                    // is likely to actually contain the AP's real location and no matter how
                    // many samples we have collected systemic/sampling errors will mean we dont
                    // know the actual coverage radius.
                    //
                    // Web search indicates that 40m coverage on older WiFi protocols and 100m
                    // coverage on newer APs (both ranges for outdoor conditions).
                    //
                    // So we will take the greater value (assumed max range) or actual measured
                    // range as our assumed accuracy.

                    SimpleLocation result = SimpleLocation.builder()
                            .latitude(c.getDouble(0))
                            .longitude(c.getDouble(1))
                            .radius(Math.max(Configuration.with(context).accessPointAssumedAccuracy(), c.getFloat(2)))
                            .changed(false)
                            .build();

                    if (DEBUG) {
                        Log.i(TAG, rfId + " at " + result.toString());
                    }

                    if (DEBUG) {
                        Log.i(TAG, "getLocation time: " + (System.currentTimeMillis() - entryTime) + "ms");
                    }
                    c.close();
                    c = null;
                    return result;
                } else {
                    if (DEBUG) {
                        Log.i(TAG, "getLocation(): Insufficient samples (" + sampleCount + ")");
                        Log.i(TAG, "getLocation time: " + (System.currentTimeMillis() - entryTime) + "ms");
                    }
                }
            }
            c.close();
            c = null;
            return null;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @Nullable
    public AccessPoint query(String rfId) {
        Cursor cursor = getReadableDatabase().query(TABLE_SAMPLES,
                new String[]{COL_RFID,      //0
                        COL_LATITUDE,       //1
                        COL_LONGITUDE,      //2
                        COL_RADIUS,         //3
                        COL_LAT1,           //4
                        COL_LON1,           //5
                        COL_LAT2,           //6
                        COL_LON2,           //7
                        COL_LAT3,           //8
                        COL_LON3,           //9
                        COL_MOVED_GUARD,    //10
                        COL_SSID,           //11
                        COL_TYPE,           //12
                        COL_CHANGED         //13
                },
                COL_RFID + "=?",
                new String[]{AccessPoint.bssid(rfId)},
                null,
                null,
                null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                List<SimpleLocation> samples = new ArrayList<SimpleLocation>();

                Integer changed = cursor.getInt(13);
                addLocation(cursor, 4, ((changed & CHANGED_AP1)!= 0), samples);
                addLocation(cursor, 6, ((changed & CHANGED_AP2)!= 0), samples);
                addLocation(cursor, 8, ((changed & CHANGED_AP3)!= 0), samples);
                if (DEBUG) {
                    Log.i(TAG, "query("+rfId+"): change="+changed+", samples="+samples);
                }

                return AccessPoint.builder()
                        .ssid(cursor.getString(11))
                        .rfId(AccessPoint.bssid(rfId))
                        .samples(samples)
                        .moveGuard(cursor.getInt(10))
                        .rfType(cursor.getInt(12))
                        .build();
            } else {
                return null;
            }
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    public void update(AccessPoint accessPoint) {
        synchronized (sqlSampleUpdate) {
            bind(sqlSampleUpdate, accessPoint, 1);
            sqlSampleUpdate.bindString(13, accessPoint.rfId());  // where clause
            sqlSampleUpdate.executeInsert();
            sqlSampleUpdate.clearBindings();
        }

        onAccessPointDataChanged();
    }

    public void insert(AccessPoint accessPoint) {
        synchronized (sqlSampleInsert) {
            sqlSampleInsert.bindString(1, accessPoint.rfId());
            sqlSampleInsert.bindLong(2, accessPoint.rfType());
            bind(sqlSampleInsert, accessPoint, 3);
            sqlSampleInsert.executeInsert();
            sqlSampleInsert.clearBindings();
        }

        onAccessPointCountChanged();
    }

    private static Integer changedValue(AccessPoint accessPoint) {
        Integer result = CHANGED_NONE;
        for (Integer i=0; i <= 2; i++) {
            if ((accessPoint.sample(i) != null) && accessPoint.sample(i).changed()) {
                result |= (1 << i);
            }
        }
        if (DEBUG) {
            Log.i(TAG, "changedValue="+result+", accessPoint="+accessPoint);
        }
        return result;
    }
    private static SQLiteStatement bind(SQLiteStatement statement, AccessPoint accessPoint, int start) {
        if(!TextUtils.isEmpty(accessPoint.ssid())) {
            statement.bindString(start, accessPoint.ssid());
        }
        statement.bindString(start + 1, String.valueOf(accessPoint.estimateLocation().latitude()));
        statement.bindString(start + 2, String.valueOf(accessPoint.estimateLocation().longitude()));
        statement.bindString(start + 3, String.valueOf(accessPoint.estimateLocation().radius()));
        statement.bindString(start + 4, String.valueOf(accessPoint.moveGuard()));
        statement.bindLong(start + 5 ,changedValue(accessPoint));
        bind(statement, accessPoint.sample(0), start + 6);
        bind(statement, accessPoint.sample(1), start + 8);
        bind(statement, accessPoint.sample(2), start + 10);
        return statement;
    }

    private static void bind(SQLiteStatement statement, SimpleLocation location, int index) {
        statement.bindString(index, String.valueOf(location == null ? 0.f : location.latitude()));
        statement.bindString(index + 1, String.valueOf(location == null ? 0.f : location.longitude()));
    }

    private SimpleLocation parse(Cursor cursor, int index, boolean changed) {
        if(cursor.getDouble(index) != 0.d || cursor.getDouble(index + 1) != 0.d) {
            return SimpleLocation.builder()
                    .latitude(cursor.getDouble(index))
                    .longitude(cursor.getDouble(index + 1))
                    .radius(Configuration.with(context).accessPointAssumedAccuracy())
                    .changed(changed)
                    .build();
        } else {
            return null;
        }
    }

    private List<SimpleLocation> addLocation(Cursor cursor, int index, boolean changed, List<SimpleLocation> list) {
        SimpleLocation location = parse(cursor, index, changed);

        if(location != null) {
            list.add(location);
        }

        return list;
    }

    protected void onAccessPointCountChanged() {
        dataChanged();
    }

    protected void onAccessPointDataChanged() {
        dataChanged();
    }

    private void dataChanged() {
        localBroadcastManager.sendBroadcast(new Intent(ACTION_DATA_CHANGED));
    }

    public void beginTransaction() {
        ensureOpened();

        db.beginTransaction();

        return;
    }
    public void commitTransaction() {
        ensureOpened();

        db.setTransactionSuccessful();
        db.endTransaction();

    }

    /**
     * @throws UnsupportedOperationException if the database isn't opened
     */
    private void ensureOpened() {
        if(db == null) {
            throw new UnsupportedOperationException("Database is not opened");
        }
    }
}
