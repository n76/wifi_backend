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
import android.os.Environment;

class constants {

    // Logging related values
    public static final String TAG_PREFIX = "wifi-backend-";
    public static boolean DEBUG = true;

    // Location of database
    public static final String DB_NAME = "wifi.db";

    // How accurate should our GPS position be to bother recording WiFi signals?
    public static final float MIN_ACCURACY = 10.0f; // meters

    // Minimum time and distance for new GPS report
    public static final long MIN_TIME = 5000;       // ms
    public static final float MIN_DISTANCE = 0.0f;  // meters

    // If new report is too far away from our current estimate then
    // we assume the AP has moved. MOVED_THRESHOLD sets the value for that
    // check.
    //
    // We set a guard against using the moved AP until we get a number
    // of samples confirming that it has a stable location. We get a new
    // GPS sample every MIN_TIME and we decrease the move guard count
    // by one for each good sample for the specific AP. Set this value
    // so big enough so that if we are near a parked WiFi AP equipped bus
    // it is likely to move before we count down to zero.
    public static final float MOVED_THRESHOLD = 250.0f; // meters
    public static int MOVED_GUARD_COUNT = 100;      // samples

    // For reporting our results to the network backend we will
    // guess about the accuracy
    public static final float ASSUMED_ACCURACY = 50.0f; // meters

}
