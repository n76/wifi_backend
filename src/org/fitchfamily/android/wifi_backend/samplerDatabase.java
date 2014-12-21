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
import android.util.LruCache;

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
 *      move_guard  - Count down of times to ignore moved AP
 *      radius      - Estimated coverage radius of AP
 */
public class samplerDatabase {
    private final static String TAG = configuration.TAG_PREFIX + "samplerDatabase";
    private final static boolean DEBUG = configuration.DEBUG;

    private static samplerDatabase mInstance;

    private SQLiteDatabase database;
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
    private static final String COL_D12 = "d12";
    private static final String COL_D23 = "d23";
    private static final String COL_D31 = "d31";
    private static final String COL_MOVED_GUARD = "move_guard";

    private SQLiteStatement sqlSampleInsert;
    private SQLiteStatement sqlSampleUpdate;
    private SQLiteStatement sqlAPdrop;

    private samplerDatabase() {}


    /**
     * DB negative query cache (not found in db).
     */
    private LruCache<String, Boolean> queryResultNegativeCache =
            new LruCache<String, Boolean>(100);
    /**
     * DB positive query cache (found in the db).
     */
    private LruCache<String, Location> queryResultCache =
            new LruCache<String, Location>(100);

    private samplerDatabase(Context context) {
        if (DEBUG) Log.d(TAG, "samplerDatabase.samplerDatabase()");
        File dbFile = new File(context.getFilesDir(), configuration.DB_NAME);
        context.getFilesDir().mkdirs();
//         if (configuration.DB_FILE.exists())
//             configuration.DB_FILE.delete();
//         File jFile = new File(configuration.DB_FILE.getAbsolutePath() + "-journal");
//         if (jFile.exists())
//             jFile.delete();
//         jFile = new File(configuration.DB_FILE.getAbsolutePath() + "-shm");
//         if (jFile.exists())
//             jFile.delete();
//         jFile = new File(configuration.DB_FILE.getAbsolutePath() + "-wal");
//         if (jFile.exists())
//             jFile.delete();
        setupDatabase(dbFile);

        sqlSampleInsert = database.compileStatement("INSERT INTO " +
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
                                                    COL_D12 + ", " +
                                                    COL_D23 + ", " +
                                                    COL_D31 + ", " +
                                                    COL_MOVED_GUARD + ") " +
                                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");

        sqlSampleUpdate = database.compileStatement("UPDATE " +
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
                                                    COL_D12 + "=?, " +
                                                    COL_D23 + "=?, " +
                                                    COL_D31 + "=?, " +
                                                    COL_MOVED_GUARD + "=? " +
                                                    "WHERE " + COL_BSSID + "=?;");

        sqlAPdrop = database.compileStatement("DELETE FROM " +
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


    private void setupDatabase(File dbFile) {
        database = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(),
                                               null,
                                               SQLiteDatabase.NO_LOCALIZED_COLLATORS +
                                               SQLiteDatabase.OPEN_READWRITE +
                                               SQLiteDatabase.CREATE_IF_NECESSARY +
                                               SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
                                               );

        // Always create version 0 of database, then update the schema
        // in the same order it might occur "in the wild". Avoids having
        // to check to see if the table exists (may be old version)
        // or not (can be new version).
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

        // First release of database does not have COL_MOVED_GUARD so see
        // if we need to update
        Integer curVer = database.getVersion();
        if (DEBUG) Log.d(TAG, "setupDatabase() version was " + curVer);
        if (curVer < 2) { // upgrade to 2
            database.execSQL("ALTER TABLE " + TABLE_SAMPLES +
                             " ADD COLUMN " + COL_MOVED_GUARD +
                             " INTEGER;");
            database.execSQL("UPDATE " + TABLE_SAMPLES +
                             " SET " + COL_MOVED_GUARD + "=0;");
            database.setVersion(2);
        }
        if (curVer < 3) { // upgrade to 3
            database.execSQL("ALTER TABLE " + TABLE_SAMPLES +
                             " ADD COLUMN " + COL_RADIUS +
                             " REAL;");
            database.execSQL("UPDATE " + TABLE_SAMPLES +
                             " SET " + COL_RADIUS + "=-1.0;");
            database.setVersion(3);
        }
        if (DEBUG) Log.d(TAG, "setupDatabase() version is " + database.getVersion());
    }

    public void addSample( String bssid, Location sampleLoc ) {
        final long entryTime = System.currentTimeMillis();
        final String canonicalBSSID = bssid.replace(":","");
        apInfo curInfo;

        database.beginTransaction();

        Cursor cursor =
            database.query(TABLE_SAMPLES,
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
        if (DEBUG) Log.d(TAG,"addSample time: "+(System.currentTimeMillis()-entryTime)+"ms");
    }

    public void dropAP( String bssid ) {
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
        apInfo bestAP = new apInfo(curInfo);
        float bestPerimeter = bestAP.perimeter();

        float diff = curInfo.distanceTo(sampleLoc);
        if (diff >= configuration.apMovedThreshold) {
            bestAP.reset(sampleLoc);
            bestAP.setMoved();
            if (DEBUG) Log.d(TAG, "Sample is " + diff + " from AP, assume AP " + bestAP.getBSSID() + " has moved.");
        } else {
            bestAP.decMoved();
            for (int i=0; i<3; i++) {
                apInfo guess = new apInfo(curInfo);
                guess.setSample(i, sampleLoc);
                float guessPerimeter = guess.perimeter();
                if (guessPerimeter > bestPerimeter) {
                    bestAP = guess;
                    bestPerimeter = guessPerimeter;
                }
            }
        }
//        if (DEBUG) Log.d(TAG, "Sample: " + bestAP.toString());
        bestAP.update();
    }

    private synchronized void resetLocationCache() {
        if (DEBUG) Log.d(TAG,"resetLocationCache");
        queryResultCache = new LruCache<String, Location>(100);
        queryResultNegativeCache = new LruCache<String, Boolean>(100);
    }

    public synchronized Location ApLocation( String bssid ) {
        final long entryTime = System.currentTimeMillis();
        final String canonicalBSSID = bssid.replace(":","");

        Boolean negative = queryResultNegativeCache.get(canonicalBSSID);
        if (negative != null && negative.booleanValue()) {
            if (DEBUG) Log.d(TAG,"ApLocation negative cache: "+(System.currentTimeMillis()-entryTime)+"ms");
            return null;
        }
        Location cached = queryResultCache.get(canonicalBSSID);
        if (cached != null) {
            if (DEBUG) Log.d(TAG,"ApLocation positive cache: "+(System.currentTimeMillis()-entryTime)+"ms");
            return cached;
        }


        Cursor c = database.query(TABLE_SAMPLES,
                                       new String[]{COL_BSSID,
                                                    COL_LATITUDE,
                                                    COL_LONGITUDE,
                                                    COL_RADIUS
                                                    },
                                       COL_BSSID+"=? AND " +
                                       COL_MOVED_GUARD + "=0",
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
                float radius = c.getFloat(c.getColumnIndexOrThrow(COL_RADIUS));
                if (radius < configuration.apAssumedAccuracy)
                    radius = configuration.apAssumedAccuracy;
                result.setAccuracy(radius);
                c.close();
                if (DEBUG) Log.d(TAG, bssid + " at " + result.toString());
                if (DEBUG) Log.d(TAG,"ApLocation time: "+(System.currentTimeMillis()-entryTime)+"ms");
                queryResultCache.put(canonicalBSSID, result);
                return result;
            }
            c.close();
        }
//        if (DEBUG) Log.d(TAG, "AP not found in database: " + bssid );
        queryResultNegativeCache.put(canonicalBSSID, true);
        return null;
    }


    // In memory representation of a APs table record
    private class apInfo {
        private String bssid;
        private Location estimate;      // Guess on AP location
        private float radius;           // Coverage range for AP
        private Location[] sample;      // Our three saved samples
        private float[] distance;       // Distance betweeen saved samples
        private int moved_guard;        // Countdown for ignoring moved AP
        private boolean changed = true; // If true we need to update this record in Db

        private apInfo() {};

        public apInfo(String bssid, Location s1) {
//            if (DEBUG) Log.d(TAG, "apInfo(" + bssid + ", " + s1 + ")" );
            this.bssid = bssid.replace(":","");
            this.reset(s1);
            this.changed = true;
        }

        public apInfo(Cursor c) {
//            if (DEBUG) Log.d(TAG, "apInfo( Cursor c )" );
            if (!c.isLast()) {
                c.moveToNext();
                bssid = c.getString(c.getColumnIndexOrThrow(COL_BSSID));
                estimate = new Location("wifi");
                estimate.setLatitude(c.getDouble(c.getColumnIndexOrThrow(COL_LATITUDE)));
                estimate.setLongitude(c.getDouble(c.getColumnIndexOrThrow(COL_LONGITUDE)));
                radius = c.getFloat(c.getColumnIndexOrThrow(COL_RADIUS));
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
                changed = false;
            } else {
                if (DEBUG) Log.d(TAG, "apInfo( Cursor c ) failed!" );
            }
        }

        public apInfo(apInfo x) {
//            if (DEBUG) Log.d(TAG, "apInfo( apInfo x )" );
            this.bssid = x.bssid;
            estimate = new Location(x.estimate);
            radius = x.radius;
            sample = new Location[3];
            distance = new float[3];
            for (int i=0; i<3; i++) {
                this.sample[i] = new Location(x.sample[i]);
                this.distance[i] = x.distance[i];
            }
            changed = false;
        }

        public void reset(Location s1) {
            estimate = s1;
            sample = new Location[3];
            radius = -1;
            distance = new float[3];
            for (int i=0; i<3; i++) {
                sample[i] = s1;
                distance[i] = 0.0f;
            }
            changed = true;
        }

        public void setMoved() {
            moved_guard = configuration.apMovedGuardCount;
            changed = true;
        }

        public void decMoved() {
            if (moved_guard > 0) {
                moved_guard--;
                changed = true;
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

        public float calcRadius() {
            float result = 0.0f;
            for (int i=0; i<3; i++) {
                float thisRadius = estimate.distanceTo(sample[i]);
//                if (DEBUG) Log.d(TAG, "apInfo.calcRadius("+bssid+"): sample[" + i + "] = " + thisRadius );
                if (thisRadius > result) {
//                    if (DEBUG) Log.d(TAG, "apInfo.calcRadius("+bssid+"): sample[" + i + "] = " + sample[i].toString() );
                    result = thisRadius;
                }
            }
            if (DEBUG) Log.d(TAG, "apInfo.calcRadius(): " + result );
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
                radius = -1.0f;
                changed = true;
            }
        }

        public void insert() {
            if (DEBUG) Log.d(TAG, "apInfo.insert(): adding " + bssid + " to database" );
            radius = calcRadius();
            sqlSampleInsert.bindString(1, bssid);
            sqlSampleInsert.bindString(2, String.valueOf(estimate.getLatitude()));
            sqlSampleInsert.bindString(3, String.valueOf(estimate.getLongitude()));
            sqlSampleInsert.bindString(4, String.valueOf(radius));
            sqlSampleInsert.bindString(5, String.valueOf(sample[0].getLatitude()));
            sqlSampleInsert.bindString(6, String.valueOf(sample[0].getLongitude()));
            sqlSampleInsert.bindString(7, String.valueOf(sample[1].getLatitude()));
            sqlSampleInsert.bindString(8, String.valueOf(sample[1].getLongitude()));
            sqlSampleInsert.bindString(9, String.valueOf(sample[2].getLatitude()));
            sqlSampleInsert.bindString(10, String.valueOf(sample[2].getLongitude()));
            sqlSampleInsert.bindString(11, String.valueOf(distance[0]));
            sqlSampleInsert.bindString(12, String.valueOf(distance[1]));
            sqlSampleInsert.bindString(13, String.valueOf(distance[2]));
            sqlSampleInsert.bindString(14, String.valueOf(moved_guard));
            long newID = sqlSampleInsert.executeInsert();
            sqlSampleInsert.clearBindings();
            resetLocationCache();
            changed = false;
        }

        public void update() {
            if (radius < 0.0f) {
                radius = calcRadius();
                changed = true;
            }
            if (changed) {
                if (DEBUG) Log.d(TAG, "apInfo.update(): updating " + bssid + " in database" );

                sqlSampleUpdate.bindString(1, String.valueOf(estimate.getLatitude()));
                sqlSampleUpdate.bindString(2, String.valueOf(estimate.getLongitude()));
                sqlSampleUpdate.bindString(3, String.valueOf(radius));
                sqlSampleUpdate.bindString(4, String.valueOf(sample[0].getLatitude()));
                sqlSampleUpdate.bindString(5, String.valueOf(sample[0].getLongitude()));
                sqlSampleUpdate.bindString(6, String.valueOf(sample[1].getLatitude()));
                sqlSampleUpdate.bindString(7, String.valueOf(sample[1].getLongitude()));
                sqlSampleUpdate.bindString(8, String.valueOf(sample[2].getLatitude()));
                sqlSampleUpdate.bindString(9, String.valueOf(sample[2].getLongitude()));
                sqlSampleUpdate.bindString(10, String.valueOf(distance[0]));
                sqlSampleUpdate.bindString(11, String.valueOf(distance[1]));
                sqlSampleUpdate.bindString(12, String.valueOf(distance[2]));
                sqlSampleUpdate.bindString(13, String.valueOf(moved_guard));
                sqlSampleUpdate.bindString(14, bssid);
                long newID = sqlSampleUpdate.executeInsert();
                sqlSampleUpdate.clearBindings();
                resetLocationCache();
            }
            changed = false;
        }

        public String toString() {
            return "apInfo{" + bssid + ", est(" + estimate.toString() + "), " +
                   "s1(" + sample[0].toString() + "), " +
                   "s2(" + sample[1].toString() + "), " +
                   "s3(" + sample[2].toString() + "), " +
                   "dist(" + distance[0] + ","+ distance[1] + ","+ distance[2] + ") " +
                   "moved_guard" + moved_guard + ", " +
                   "changed" + changed + "}";
        }
    }
}
