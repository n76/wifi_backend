package org.fitchfamily.android.wifi_backend.util;

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
import android.location.Location;
import android.support.v4.util.LruCache;
import android.util.Log;

import org.fitchfamily.android.wifi_backend.BuildConfig;

public class distanceCache {
    private static final String TAG = "WiFiBackendDistCache";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private  class cacheKey {
        double lat1;
        double lon1;
        double lat2;
        double lon2;

        cacheKey(Location first, Location second) {
            lat1 = first.getLatitude();
            lon1 = first.getLongitude();
            lat2 = second.getLatitude();
            lon2 = second.getLongitude();
        }

        private int longHash(long f) {
            return (int)(f ^ (f >>> 32));
        }

        public int hashCode() {
            int result = 101;
            result = 37 * result + longHash(Double.doubleToLongBits(lat1));
            result = 37 * result + longHash(Double.doubleToLongBits(lon1));
            result = 37 * result + longHash(Double.doubleToLongBits(lat2));
            result = 37 * result + longHash(Double.doubleToLongBits(lon2));
            return result;
        }

        public boolean equals(Object obj) {
            cacheKey other = (cacheKey)obj;

            return (lat1 == other.lat1) && (lon1 == other.lon1) &&
                    (lat2 == other.lat2) && (lon2 == other.lon2);
        }
    }
    protected class distanceRec {
        public float distance;
    }

    private static final LruCache<cacheKey, distanceRec> distanceCache = new LruCache<>(1000);
    private static long myHits = 0;
    private static long myMisses = 0;

    public synchronized float distanceBetween(Location loc1, Location loc2) {
        cacheKey key = new cacheKey(loc1, loc2);
        cacheKey key1 = new cacheKey(loc2, loc1);
        distanceRec cachedValue = distanceCache.get(key);
        if (cachedValue == null) {
            cachedValue = distanceCache.get(key1);
        }
        if (cachedValue == null) {
            myMisses++;
            cachedValue = new distanceRec();
            cachedValue.distance = loc1.distanceTo(loc2);
            distanceCache.put(key,cachedValue);
        } else
            myHits++;
        return cachedValue.distance;
    }

    public void logCacheStats() {
        Log.i(TAG, "LRU stats: Hits=" + distanceCache.hitCount() +
                ", Misses=" + distanceCache.missCount() +
                ", Entries=" + distanceCache.size() +
                ", MyHits=" + myHits +
                ", MyMisses=" + myMisses);
    }

}
