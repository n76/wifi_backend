package org.fitchfamily.android.wifi_backend;

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
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;

public class Configuration {
    // Identifiers for extra fields in Location records
    public static final String EXTRA_MAC_ADDRESS = "MAC_ADDRESS";
    public static final String EXTRA_SIGNAL_LEVEL = "SIGNAL_LEVEL";

    public static final String PREF_MIN_GPS_TIME = "gps_min_time_preference";
    public static final String PREF_MIN_GPS_ACCURACY = "gps_accuracy_preference";
    public static final String PREF_MIN_GPS_DISTANCE = "gps_min_distance_preference";

    public static final String PREF_AP_ACCURACY = "ap_min_range_preference";
    public static final String PREF_MOVE_GUARD = "ap_moved_guard_preference";
    public static final String PREF_MOVE_RANGE = "ap_moved_range_preference";

    private static final Object lock = new Object();
    private static Configuration instance;

    public static Configuration with(Context context) {
        if(context == null) {
            throw new NullPointerException();
        }

        if(instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new Configuration(context.getApplicationContext());
                }
            }
        }

        return instance;
    }

    private SharedPreferences preferences;
    private Resources resources;

    private Configuration(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        resources = context.getResources();
    }

    // How accurate should our GPS position be to bother recording WiFi signals?
    public float minimumGpsAccuracyInMeters() {
        return parseFloat(PREF_MIN_GPS_ACCURACY, R.string.gps_accuracy_default);
    }

    public long minimumGpsTimeInMilliseconds() {
        return parseLong(PREF_MIN_GPS_TIME, R.string.gps_min_time_default) * 1000;
    }

    public float minimumGpsDistanceInMeters() {
        return parseFloat(PREF_MIN_GPS_DISTANCE, R.string.gps_min_distance_default);
    }

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

    public float accessPointMoveThresholdInMeters() {
        return parseFloat(PREF_MOVE_RANGE, R.string.ap_moved_range_default);
    }

    public int accessPointMoveGuardSampleCount() {
        return parseInt(PREF_MOVE_GUARD, R.string.ap_moved_guard_default);
    }

    // For reporting our results to the network backend we will
    // guess about the minimum accuracy for an individual AP
    public float accessPointAssumedAccuracy() {
        return parseFloat(PREF_AP_ACCURACY, R.string.ap_min_range_default);
    }

    public Configuration register(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);

        return this;
    }

    public Configuration unregister(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);

        return this;
    }

    private float parseFloat(String preferenceKey, int defaultResource) {
        String defaultValue = resources.getString(defaultResource);

        try {
            return Float.parseFloat(preferences.getString(preferenceKey, defaultValue));
        } catch (NumberFormatException ex) {
            return Float.parseFloat(defaultValue);
        }
    }

    private long parseLong(String preferenceKey, int defaultResource) {
        String defaultValue = resources.getString(defaultResource);

        try {
            return Long.parseLong(preferences.getString(preferenceKey, defaultValue));
        } catch (NumberFormatException ex) {
            return Long.parseLong(defaultValue);
        }
    }

    private int parseInt(String preferenceKey, int defaultResource) {
        String defaultValue = resources.getString(defaultResource);

        try {
            return Integer.parseInt(preferences.getString(preferenceKey, defaultValue));
        } catch (NumberFormatException ex) {
            return Integer.parseInt(defaultValue);
        }
    }
}
