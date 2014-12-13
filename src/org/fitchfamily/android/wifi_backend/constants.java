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

//     public static final File ROOT_DIR = Environment.getExternalStorageDirectory();
//     public static final File DB_DIR = new File(ROOT_DIR, ".nogapps");
//     public static final File DB_FILE = new File(DB_DIR, DB_NAME);

    // How accurate should our position be to bother recording WiFi signals?
    // From experience, GPS can claim 5m and we are trying to locate something
    // that has a coverage radius of maybe 50m, so set this to around 7.5
    // so that an accurate GPS reading will be accepted.
    public static final float MIN_ACCURACY = 20.0f; // meters

    // If new report is too far away from our current estimate then
    // we assume the AP has moved. This sets the threshold for that
    // check.
    public static final float MOVED_THRESHOLD = 200.0f; // meters

    // Minimum time and distance for new GPS report
    public static final long MIN_TIME = 5000;       // ms
    public static final float MIN_DISTANCE = 0.0f;  // meters

    // For reporting our results to the network backend we will
    // guess about the accuracy
    public static final float ASSUMED_ACCURACY = 50.0f; // meters

}
