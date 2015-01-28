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
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Environment;
import android.util.Log;

class configuration {

    // Logging related values
    public static final String TAG_PREFIX = "wifi-backend-";
    public static final int DEBUG_NONE = 0;
    public static final int DEBUG_SPARSE = 1;
    public static final int DEBUG_NORMAL = 2;
    public static final int DEBUG_VERBOSE = 3;
    public static final int DEBUG = DEBUG_NONE;

    // Location of database
    public static final String DB_NAME = "wifi.db";

    // How accurate should our GPS position be to bother recording WiFi signals?
    public static float gpsMinAccuracy = 15.0f; // meters

    // Minimum time and distance for new GPS report
    public static long gpsMinTime = 5000;       // ms
    public static float gpsMinDistance = 0.0f;  // meters

    // If new report is too far away from our current estimate then
    // we assume the AP has moved. apMovedThreshold sets the value for that
    // check.
    //
    // We set a guard against using the moved AP until we get a number
    // of samples confirming that it has a stable location. We get a new
    // GPS sample every gpsMinTime and we decrease the move guard count
    // by one for each good sample for the specific AP. Set this value
    // so big enough so that if we are near a parked WiFi AP equipped bus
    // it is likely to move before we count down to zero.
    public static float apMovedThreshold = 250.0f; // meters
    public static int apMovedGuardCount = 100;      // samples

    // For reporting our results to the network backend we will
    // guess about the minimum accuracy (coverage radius) for an AP
    public static float apAssumedAccuracy = 50.0f; // meters

    // Identifiers for extra fields in Location records
    public static final String EXTRA_MAC_ADDRESS = "MAC_ADDRESS";
    public static final String EXTRA_SIGNAL_LEVEL = "SIGNAL_LEVEL";

    private static final String TAG = TAG_PREFIX+"configuation";
    public static ConfigChangedListener listener = new ConfigChangedListener();
    private static gpsSamplingCallback mCallback = null;

    public static void fillFromPrefs(SharedPreferences sharedPrefs) {
        gpsMinAccuracy = Float.valueOf(sharedPrefs.getString("gps_accuracy_preference", "15.0"));
        final long newGpsMinTime = Integer.valueOf(sharedPrefs.getString("gps_min_time_preference", "5")) * 1000;
        final float newGpsMinDistance = Float.valueOf(sharedPrefs.getString("gps_min_distance_preference", "5"));
        final boolean samplingChanged = (newGpsMinTime != gpsMinTime) ||
                                        (newGpsMinDistance != gpsMinDistance);
        gpsMinTime = newGpsMinTime;
        gpsMinDistance = newGpsMinDistance;

        apAssumedAccuracy = Float.valueOf(sharedPrefs.getString("ap_min_range_preference", "50.0"));
        apMovedThreshold = Float.valueOf(sharedPrefs.getString("ap_moved_range_preference", "50.0"));
        apMovedGuardCount = Integer.valueOf(sharedPrefs.getString("ap_moved_guard_preference", "100"));

        Log.d(TAG, "fillFromPrefs(): Min GPS accuracy: " + gpsMinAccuracy);
        Log.d(TAG, "fillFromPrefs(): Min GPS time: " + gpsMinTime);
        Log.d(TAG, "fillFromPrefs(): Min GPS distance: " + gpsMinDistance);
        Log.d(TAG, "fillFromPrefs(): AP min range: " + apAssumedAccuracy);
        Log.d(TAG, "fillFromPrefs(): AP moved threshold: " + apMovedThreshold);
        Log.d(TAG, "fillFromPrefs(): AP moved guard count: " + apMovedGuardCount);
        if (mCallback != null)
            mCallback.updateSamplingConf(gpsMinTime, gpsMinDistance);
    }

    public static class ConfigChangedListener implements OnSharedPreferenceChangeListener {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                String key) {
            fillFromPrefs(sharedPreferences);
        }
    }

    public interface gpsSamplingCallback {
        void updateSamplingConf(long sampleTime, float sampleDistance);
    }

    public static void setGpsSamplingCallback(gpsSamplingCallback newCallback) {
        mCallback = newCallback;
    }

}
