package org.fitchfamily.android.wifi_backend;

/*
 *  WiFi Backend for Unified Network Location
 *  Copyright (C) 2014  Tod Fitch
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

import java.io.File;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.util.Log;

/*
 * We estimate the AP location by keeping three samples that form a triangle.
 * Our best guess for the AP location is then the average of the lat/lon values
 * for each triangle vertice.
 *
 * We select the samples that form the triangle by trying to maximize the
 * perimeter distance. To avoid recomputing linear distance between samples
 * we store them along with the selected samples.
 *
 * Field naming conventions are:
 *      bssid       - Basically the MAC address of the AP
 *      latitude    - Latitude estimate for the AP
 *      longitude   - Longitude estimate for the AP
 *      lat1        - Latitude measure for sample 1
 *      lon1        - Longitude measure for sample 1
 *      lat2        - Latitude measure for sample 2
 *      lon2        - Longitude measure for sample 2
 *      lat3        - Latitude measure for sample 3
 *      lon3        - Longitude measure for sample 3
 *      d12         - Distance, in meters, between sample 1 and sample 2
 *      d23         - Distance, in meters, between sample 2 and sample 3
 *      d31         - Distance, in meters, between sample 3 and sample 1
 */
public class samplerDatabase {
    private final static String TAG = constants.TAG_PREFIX + "samplerDatabase";
    private final static boolean DEBUG = constants.DEBUG;

    private static samplerDatabase mInstance;

    private SQLiteDatabase database;
    private static final String TABLE_SAMPLES = "APs";
    private static final String COL_BSSID = "bssid";
    private static final String COL_LATITUDE = "latitude";
    private static final String COL_LONGITUDE = "longitude";
    private static final String COL_LAT1 = "lat1";
    private static final String COL_LON1 = "lon1";
    private static final String COL_LAT2 = "lat2";
    private static final String COL_LON2 = "lon2";
    private static final String COL_LAT3 = "lat3";
    private static final String COL_LON3 = "lon3";
    private static final String COL_D12 = "d12";
    private static final String COL_D23 = "d23";
    private static final String COL_D31 = "d31";

    private SQLiteStatement sqlSampleInsert;
    private SQLiteStatement sqlSampleUpdate;
    private SQLiteStatement sqlAPdrop;
    private SQLiteStatement sqlLocationQuery;

    private samplerDatabase() {}

    private samplerDatabase(Context context) {
        if (DEBUG) Log.d(TAG, "samplerDatabase.samplerDatabase()");
        File dbFile = new File(context.getFilesDir(), constants.DB_NAME);
        context.getFilesDir().mkdirs();
//         if (constants.DB_FILE.exists())
//             constants.DB_FILE.delete();
//         File jFile = new File(constants.DB_FILE.getAbsolutePath() + "-journal");
//         if (jFile.exists())
//             jFile.delete();
        database = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(),
                                               null,
                                               SQLiteDatabase.NO_LOCALIZED_COLLATORS +
                                               SQLiteDatabase.OPEN_READWRITE +
                                               SQLiteDatabase.CREATE_IF_NECESSARY
                                               );
        database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SAMPLES + "(" +
                         COL_BSSID + " STRING PRIMARY KEY, " +
                         COL_LATITUDE + " REAL, " +
                         COL_LONGITUDE + " REAL, "+
                         COL_LAT1 + " REAL, " +
                         COL_LON1 + " REAL, " +
                         COL_LAT2 + " REAL, " +
                         COL_LON2 + " REAL, " +
                         COL_LAT3 + " REAL, " +
                         COL_LON3 + " REAL, " +
                         COL_D12 + " REAL, " +
                         COL_D23 + " REAL, " +
                         COL_D31 + " REAL);");

        sqlSampleInsert = database.compileStatement("INSERT INTO " +
                                                    TABLE_SAMPLES + "("+
                                                    COL_BSSID + ", " +
                                                    COL_LATITUDE + ", " +
                                                    COL_LONGITUDE + ", " +
                                                    COL_LAT1 + ", " +
                                                    COL_LON1 + ", " +
                                                    COL_LAT2 + ", " +
                                                    COL_LON2 + ", " +
                                                    COL_LAT3 + ", " +
                                                    COL_LON3 + ", " +
                                                    COL_D12 + ", " +
                                                    COL_D23 + ", " +
                                                    COL_D31 + ") " +
                                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");

        sqlSampleUpdate = database.compileStatement("UPDATE " +
                                                    TABLE_SAMPLES + " SET "+
                                                    COL_LATITUDE + "=?, " +
                                                    COL_LONGITUDE + "=?, " +
                                                    COL_LAT1 + "=?, " +
                                                    COL_LON1 + "=?, " +
                                                    COL_LAT2 + "=?, " +
                                                    COL_LON2 + "=?, " +
                                                    COL_LAT3 + "=?, " +
                                                    COL_LON3 + "=?, " +
                                                    COL_D12 + "=?, " +
                                                    COL_D23 + "=?, " +
                                                    COL_D31 + "=? " +
                                                    "WHERE " + COL_BSSID + "=?;");

        sqlAPdrop = database.compileStatement("DELETE FROM " +
                                                    TABLE_SAMPLES +
                                                    " WHERE " + COL_BSSID + "=?;");

        sqlLocationQuery = database.compileStatement("SELECT " +
                                                    COL_BSSID + ", " +
                                                    COL_LATITUDE + ", " +
                                                    COL_LONGITUDE +
                                                    " FROM " +
                                                    TABLE_SAMPLES +
                                                    " WHERE " + COL_BSSID + "=?;");

    }

    public synchronized static samplerDatabase getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new samplerDatabase(context);
        }
        return mInstance;
    }

    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public synchronized void addSample( String bssid, double lat, double lon ) {
        final String canonicalBSSID = bssid.replace(":","");
        final Location sampleLoc = new Location("wifi");
        apInfo curInfo;

        sampleLoc.setLatitude(lat);
        sampleLoc.setLongitude(lon);

        database.beginTransaction();

        Cursor cursor =
        database.query(TABLE_SAMPLES,
                       new String[]{COL_BSSID,
                                    COL_LATITUDE,
                                    COL_LONGITUDE,
                                    COL_LAT1,
                                    COL_LON1,
                                    COL_LAT2,
                                    COL_LON2,
                                    COL_LAT3,
                                    COL_LON3,
                                    COL_D12,
                                    COL_D23,
                                    COL_D31
                                    },
                       COL_BSSID+"=?",
                       new String[]{canonicalBSSID},
                       null,
                       null,
                       null);
        if (cursor != null) {
            if (cursor.getCount() == 0) {
                new apInfo(bssid, sampleLoc).insert();
            } else if (cursor.getCount() == 1) {
                updateAP(new apInfo(cursor), sampleLoc );
            } else {
                if (DEBUG) Log.d(TAG, "Unexpected number of samples (" + cursor.getCount() + ") in db" );
            }
            cursor.close();
        }
        database.setTransactionSuccessful();
        database.endTransaction();
    }

    public synchronized void dropAP( String bssid ) {
        final String canonicalBSSID = bssid.replace(":","");
        if (DEBUG) Log.d(TAG, "Dropping " + canonicalBSSID + " from db" );
        sqlAPdrop.bindString(1, canonicalBSSID);
        long newID = sqlAPdrop.executeInsert();
        sqlAPdrop.clearBindings();
    }

    // We attempt to estimate the position of the AP by making as
    // large a triangle around it as possible. At this point we
    // have three points already in the database describing a triangle.
    // We will take the new sample an see if we can make a triangle with
    // a larger perimeter by replacing one of our current samples with
    // the new one.
    private void updateAP(apInfo curInfo, Location sampleLoc ) {
        apInfo best = new apInfo(curInfo);
        float bestPerimeter = best.perimeter();
        boolean changed = false;

        float diff = curInfo.distanceTo(sampleLoc);
        if (diff >= constants.MOVED_THRESHOLD) {
            best.reset(sampleLoc);
            changed = true;
            if (DEBUG) Log.d(TAG, "Sample is " + diff + "from AP, assume AP " + curInfo.getBSSID() + " has moved.");
        } else {
            for (int i=0; i<3; i++) {
                apInfo guess = new apInfo(curInfo);
                guess.setSample(i, sampleLoc);
                float guessPerimeter = guess.perimeter();
                if (guessPerimeter > bestPerimeter) {
                    best = guess;
                    bestPerimeter = guessPerimeter;
                    changed = true;
                }
            }
        }
        if (changed) {
            best.update();
        }
    }

    public Location ApLocation( String bssid ) {
        final String canonicalBSSID = bssid.replace(":","");
        Cursor c = database.query(TABLE_SAMPLES,
                                       new String[]{COL_BSSID,
                                                    COL_LATITUDE,
                                                    COL_LONGITUDE
                                                    },
                                       COL_BSSID+"=?",
                                       new String[]{canonicalBSSID},
                                       null,
                                       null,
                                       null);
        if (c != null) {
            if (c.getCount() == 1) {
                c.moveToNext();
                Location result = new Location("wifi");
                result.setLatitude(c.getDouble(c.getColumnIndexOrThrow(COL_LATITUDE)));
                result.setLongitude(c.getDouble(c.getColumnIndexOrThrow(COL_LONGITUDE)));
                result.setAccuracy(constants.ASSUMED_ACCURACY);
                c.close();
                return result;
            }
        }
        return null;
    }


    // In memory representation of a APs table record
    private class apInfo {
        private String bssid;
        private Location estimate;
        private Location[] sample;
        private float[] distance;

        private apInfo() {};

        public apInfo(String bssid, Location s1) {
            this.bssid = bssid.replace(":","");
            this.reset(s1);
        }

        public apInfo(Cursor c) {
            if (!c.isLast()) {
                c.moveToNext();
                bssid = c.getString(c.getColumnIndexOrThrow(COL_BSSID));
                estimate = new Location("wifi");
                estimate.setLatitude(c.getDouble(c.getColumnIndexOrThrow(COL_LATITUDE)));
                estimate.setLongitude(c.getDouble(c.getColumnIndexOrThrow(COL_LONGITUDE)));
                sample = new Location[3];
                sample[0] = new Location("wifi");
                sample[0].setLatitude(c.getDouble(c.getColumnIndexOrThrow(COL_LAT1)));
                sample[0].setLongitude(c.getDouble(c.getColumnIndexOrThrow(COL_LON1)));
                sample[1] = new Location("wifi");
                sample[1].setLatitude(c.getDouble(c.getColumnIndexOrThrow(COL_LAT2)));
                sample[1].setLongitude(c.getDouble(c.getColumnIndexOrThrow(COL_LON2)));
                sample[2] = new Location("wifi");
                sample[2].setLatitude(c.getDouble(c.getColumnIndexOrThrow(COL_LAT3)));
                sample[2].setLongitude(c.getDouble(c.getColumnIndexOrThrow(COL_LON3)));
                distance = new float[3];
                distance[0] = c.getFloat(c.getColumnIndexOrThrow(COL_D12));
                distance[1] = c.getFloat(c.getColumnIndexOrThrow(COL_D23));
                distance[2] = c.getFloat(c.getColumnIndexOrThrow(COL_D31));
            }
        }

        public apInfo(apInfo x) {
            this.bssid = x.bssid;
            estimate = new Location("wifi");
            sample = new Location[3];
            distance = new float[3];
            for (int i=0; i<3; i++) {
                this.sample[i] = new Location(x.sample[i]);
                this.distance[i] = x.distance[i];
            }
        }

        public void reset(Location s1) {
            estimate = s1;
            sample = new Location[3];
            distance = new float[3];
            for (int i=0; i<3; i++) {
                sample[i] = s1;
                distance[i] = 0.0f;
            }
        }

        public String getBSSID() {
            return bssid;
        }

        public float distanceTo(Location loc) {
            float result = estimate.distanceTo(loc);
            return result;
        }

        public float perimeter() {
            float result = 0.0f;
            for (int i=0; i<3; i++)  result += distance[i];
            return result;
        }

        public Location getSample(int index) {
            if ((index >=0) && (index < 3)) {
                return sample[index];
            }
            return null;
        }

        public void setSample(int index, Location newLoc) {
            if ((index >=0) && (index < 3)) {
                double newLat = 0.0;
                double newLon = 0.0;

                sample[index] = newLoc;
                for (int i=0; i<3; i++) {
                    int next = (i+1) % 3;
                    distance[i] = sample[i].distanceTo(sample[next]);
                    newLat += sample[i].getLatitude();
                    newLon += sample[i].getLongitude();
                }
                estimate.setLatitude(newLat/3.0);
                estimate.setLongitude(newLon/3.0);
            }
        }

        public void insert() {
            if (DEBUG) Log.d(TAG, "apInfo.insert(): adding " + bssid + " to database" );

            sqlSampleInsert.bindString(1, bssid);
            sqlSampleInsert.bindString(2, String.valueOf(estimate.getLatitude()));
            sqlSampleInsert.bindString(3, String.valueOf(estimate.getLongitude()));
            sqlSampleInsert.bindString(4, String.valueOf(sample[0].getLatitude()));
            sqlSampleInsert.bindString(5, String.valueOf(sample[0].getLongitude()));
            sqlSampleInsert.bindString(6, String.valueOf(sample[1].getLatitude()));
            sqlSampleInsert.bindString(7, String.valueOf(sample[1].getLongitude()));
            sqlSampleInsert.bindString(8, String.valueOf(sample[2].getLatitude()));
            sqlSampleInsert.bindString(9, String.valueOf(sample[2].getLongitude()));
            sqlSampleInsert.bindString(10, String.valueOf(distance[0]));
            sqlSampleInsert.bindString(11, String.valueOf(distance[1]));
            sqlSampleInsert.bindString(12, String.valueOf(distance[2]));
            long newID = sqlSampleInsert.executeInsert();
            sqlSampleInsert.clearBindings();
        }

        public void update() {
            if (DEBUG) Log.d(TAG, "apInfo.update(): updating " + bssid + " in database" );

            sqlSampleUpdate.bindString(1, String.valueOf(estimate.getLatitude()));
            sqlSampleUpdate.bindString(2, String.valueOf(estimate.getLongitude()));
            sqlSampleUpdate.bindString(3, String.valueOf(sample[0].getLatitude()));
            sqlSampleUpdate.bindString(4, String.valueOf(sample[0].getLongitude()));
            sqlSampleUpdate.bindString(5, String.valueOf(sample[1].getLatitude()));
            sqlSampleUpdate.bindString(6, String.valueOf(sample[1].getLongitude()));
            sqlSampleUpdate.bindString(7, String.valueOf(sample[2].getLatitude()));
            sqlSampleUpdate.bindString(8, String.valueOf(sample[2].getLongitude()));
            sqlSampleUpdate.bindString(9, String.valueOf(distance[0]));
            sqlSampleUpdate.bindString(10, String.valueOf(distance[1]));
            sqlSampleUpdate.bindString(11, String.valueOf(distance[2]));
            sqlSampleUpdate.bindString(12, bssid);
            long newID = sqlSampleUpdate.executeInsert();
            sqlSampleUpdate.clearBindings();
        }

        public String toString() {
            return "apInfo{" + bssid + ", est(" + estimate.toString() + "), " +
                   "s1(" + sample[0].toString() + "), " +
                   "s2(" + sample[1].toString() + "), " +
                   "s3(" + sample[2].toString() + "), " +
                   "dist(" + distance[0] + ","+ distance[1] + ","+ distance[2] + ")}";
        }
    }
}
