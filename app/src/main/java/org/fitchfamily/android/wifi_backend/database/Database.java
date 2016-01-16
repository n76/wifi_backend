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
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Database extends SQLiteOpenHelper {
    public static String ACTION_DATA_CHANGED = "org.fitchfamily.android.wifi_backend.database.DATA_CHANGED";

    private static final String TAG = "Database";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int VERSION = 4;
    private static final String NAME = "wifi.db";

    private static final String TABLE_SAMPLES = "APs";
    private static final String COL_BSSID = "bssid";
    private static final String COL_LATITUDE = "latitude";
    private static final String COL_LONGITUDE = "longitude";
    private static final String COL_RADIUS = "radius";
    private static final String COL_LAT1 = "lat1";
    private static final String COL_LON1 = "lon1";
    private static final String COL_LAT2 = "lat2";
    private static final String COL_LON2 = "lon2";
    private static final String COL_LAT3 = "lat3";
    private static final String COL_LON3 = "lon3";
    // not used anymore
    @Deprecated
    private static final String COL_D12 = "d12";
    // not used anymore
    @Deprecated
    private static final String COL_D23 = "d23";
    // not used anymore
    @Deprecated
    private static final String COL_D31 = "d31";
    private static final String COL_MOVED_GUARD = "move_guard";
    private static final String COL_SSID = "ssid";

    private SQLiteStatement sqlSampleInsert;
    private SQLiteStatement sqlSampleUpdate;
    private SQLiteStatement sqlAPdrop;
    private final LocalBroadcastManager localBroadcastManager;
    private final Context context;

    public Database(Context context) {
        super(new ContextWrapper(context) {
            @Override
            public File getDatabasePath(String name) {
                return getFilesDir();
            }
        }, NAME, null, VERSION);

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
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);

        sqlSampleInsert = db.compileStatement("INSERT INTO " +
                TABLE_SAMPLES + "("+
                COL_BSSID + ", " +
                COL_LATITUDE + ", " +
                COL_LONGITUDE + ", " +
                COL_RADIUS + ", " +
                COL_LAT1 + ", " +
                COL_LON1 + ", " +
                COL_LAT2 + ", " +
                COL_LON2 + ", " +
                COL_LAT3 + ", " +
                COL_LON3 + ", " +
                COL_MOVED_GUARD + ", " +
                COL_SSID + ") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");

        sqlSampleUpdate = db.compileStatement("UPDATE " +
                TABLE_SAMPLES + " SET "+
                COL_LATITUDE + "=?, " +
                COL_LONGITUDE + "=?, " +
                COL_RADIUS + "=?, " +
                COL_LAT1 + "=?, " +
                COL_LON1 + "=?, " +
                COL_LAT2 + "=?, " +
                COL_LON2 + "=?, " +
                COL_LAT3 + "=?, " +
                COL_LON3 + "=?, " +
                COL_MOVED_GUARD + "=?, " +
                COL_SSID + "=? " +
                "WHERE " + COL_BSSID + "=?;");

        sqlAPdrop = db.compileStatement("DELETE FROM " +
                TABLE_SAMPLES +
                " WHERE " + COL_BSSID + "=?;");
    }


    public void dropAP(String bssid) {
        final String canonicalBSSID = AccessPoint.bssid(bssid);

        if (DEBUG) {
            Log.i(TAG, "Dropping " + canonicalBSSID + " from db");
        }

        sqlAPdrop.bindString(1, canonicalBSSID);
        sqlAPdrop.executeInsert();
        sqlAPdrop.clearBindings();

        onAccessPointCountChanged();
    }

    public int getAccessPointCount() {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_SAMPLES + ";", null);

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
    public EstimateLocation getLocation(String bssid) {
        final long entryTime = System.currentTimeMillis();
        final String canonicalBSSID = AccessPoint.bssid(bssid);

        Cursor c = getReadableDatabase().query(TABLE_SAMPLES,
                new String[]{COL_LATITUDE,
                        COL_LONGITUDE,
                        COL_RADIUS
                },
                COL_BSSID+"=? AND " +
                        COL_MOVED_GUARD + "=0",
                new String[]{canonicalBSSID},
                null,
                null,
                null);

        try {
            if (c != null && c.moveToFirst()) {
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

                EstimateLocation result = EstimateLocation.builder()
                        .latitude(c.getDouble(0))
                        .longitude(c.getDouble(1))
                        .radius(Math.max(Configuration.with(context).accessPointAssumedAccuracy(), c.getFloat(2)))
                        .build();

                if (DEBUG) {
                    Log.i(TAG, bssid + " at " + result.toString());
                }

                if (DEBUG) {
                    Log.i(TAG, "ApLocation time: " + (System.currentTimeMillis() - entryTime) + "ms");
                }

                return result;
            }

            return null;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @Nullable
    public AccessPoint query(String bssid) {
        Cursor cursor = getReadableDatabase().query(TABLE_SAMPLES,
                new String[]{COL_BSSID,
                        COL_LATITUDE,
                        COL_LONGITUDE,
                        COL_RADIUS,
                        COL_LAT1,
                        COL_LON1,
                        COL_LAT2,
                        COL_LON2,
                        COL_LAT3,
                        COL_LON3,
                        COL_MOVED_GUARD,
                        COL_SSID
                },
                COL_BSSID + "=?",
                new String[]{AccessPoint.bssid(bssid)},
                null,
                null,
                null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                List<org.fitchfamily.android.wifi_backend.database.Location> samples = new ArrayList<Location>();

                addLocation(cursor, 4, samples);
                addLocation(cursor, 6, samples);
                addLocation(cursor, 8, samples);

                return AccessPoint.builder()
                        .ssid(cursor.getString(11))
                        .bssid(AccessPoint.bssid(bssid))
                        .samples(samples)
                        .moveGuard(cursor.getInt(10))
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

    protected void update(AccessPoint accessPoint) {
        synchronized (sqlSampleUpdate) {
            bind(sqlSampleUpdate, accessPoint, 1);
            sqlSampleUpdate.bindString(11, accessPoint.ssid());
            sqlSampleUpdate.bindString(12, accessPoint.bssid());
            sqlSampleUpdate.executeInsert();
            sqlSampleUpdate.clearBindings();
        }

        onAccessPointDataChanged();
    }

    protected void insert(AccessPoint accessPoint) {
        synchronized (sqlSampleInsert) {
            sqlSampleInsert.bindString(1, accessPoint.bssid());
            bind(sqlSampleInsert, accessPoint, 2);
            sqlSampleInsert.bindString(12, accessPoint.ssid());
            sqlSampleInsert.executeInsert();
            sqlSampleInsert.clearBindings();
        }

        onAccessPointCountChanged();
    }

    private static SQLiteStatement bind(SQLiteStatement statement, AccessPoint accessPoint, int start) {
        statement.bindString(start, String.valueOf(accessPoint.estimateLocation().latitude()));
        statement.bindString(start + 1, String.valueOf(accessPoint.estimateLocation().longitude()));
        statement.bindString(start + 2, String.valueOf(accessPoint.estimateLocation().radius()));
        bind(statement, accessPoint.sample(0), start + 3);
        bind(statement, accessPoint.sample(1), start + 5);
        bind(statement, accessPoint.sample(2), start + 7);
        statement.bindString(start + 9, String.valueOf(accessPoint.moveGuard()));

        return statement;
    }

    private static void bind(SQLiteStatement statement, Location location, int index) {
        statement.bindString(index, String.valueOf(location == null ? 0.f : location.latitude()));
        statement.bindString(index + 1, String.valueOf(location == null ? 0.f : location.longitude()));
    }

    private static Location parse(Cursor cursor, int index) {
        if(cursor.getDouble(index) != 0.d || cursor.getDouble(index + 1) != 0.d) {
            return Location.builder()
                    .latitude(cursor.getDouble(index))
                    .longitude(cursor.getDouble(index + 1))
                    .build();
        } else {
            return null;
        }
    }

    private static List<Location> addLocation(Cursor cursor, int index, List<Location> list) {
        Location location = parse(cursor, index);

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
}
