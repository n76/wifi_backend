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
import android.util.Log;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.Configuration;

/*
 * We estimate the AP location by keeping three samples that form a triangle.
 * Our best guess for the AP location is then the average of the lat/lon values
 * for each triangle vertice.
 *
 * We select the samples that form the triangle by trying to maximize the
 * perimeter distance.
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
 *      move_guard  - Count down of times to ignore moved AP
 *      radius      - Estimated coverage radius of AP
 */
public class SamplerDatabase extends Database {
    private final static String TAG = "SamplerDatabase";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static SamplerDatabase mInstance;
    private final Context context;

    private SamplerDatabase(Context context) {
        super(context);

        this.context = context;

        if (DEBUG) {
            Log.i(TAG, "samplerDatabase.samplerDatabase()");
        }
    }

    public synchronized static SamplerDatabase getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new SamplerDatabase(context);
        }

        return mInstance;
    }

    public void addSample(String ssid, String bssid, Location sampleLocation) {
        final long entryTime = System.currentTimeMillis();

        AccessPoint accessPoint = query(bssid);

        if (accessPoint != null) {
            // We attempt to estimate the position of the AP by making as
            // large a triangle around it as possible.
            // At this point we have the specified amount of points already
            // in the database describing a triangle.

            float diff = accessPoint.estimateLocation().distanceTo(sampleLocation);

            if (diff >= Configuration.with(context).accessPointMoveThresholdInMeters()) {
                accessPoint = accessPoint.buildUpon()
                        .ssid(ssid)
                        .clearSamples()
                        .addSample(sampleLocation)
                        .moved(Configuration.with(context).accessPointMoveGuardSampleCount())
                        .build();

                if (DEBUG) {
                    Log.i(TAG, "Sample is " + diff + " from AP, assume AP " + accessPoint.bssid() + " has moved.");
                }
            } else {
                accessPoint = accessPoint.buildUpon()
                        .ssid(ssid)
                        .decMoved()
                        .addSample(sampleLocation)
                        .build();
            }

            if (DEBUG) {
                Log.i(TAG, "Sample: " + accessPoint.toString());
            }

            update(accessPoint);
        } else {
            insert(
                    AccessPoint.builder()
                            .ssid(ssid)
                            .bssid(AccessPoint.bssid(bssid))
                            .moveGuard(0)
                            .addSample(sampleLocation)
                            .build()
            );
        }

        if (DEBUG) {
            Log.i(TAG,"addSample time: "+ (System.currentTimeMillis() - entryTime) + " ms");
        }
    }

    public SamplerDatabase dropAccessPoint(String bssid) {
        dropAP(bssid);

        return this;
    }
}
