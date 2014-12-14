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

import java.util.ArrayList;
import java.util.List;

import org.microg.nlp.api.LocationBackendService;
import org.microg.nlp.api.LocationHelper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.fitchfamily.android.wifi_backend.WifiReceiver.WifiReceivedCallback;

public class BackendService extends LocationBackendService {
    private static final String TAG = constants.TAG_PREFIX + "backend-service";
    private final static boolean DEBUG = constants.DEBUG;

    private samplerDatabase sDb;
    private WifiReceiver wifiReceiver;
    private boolean networkAllowed;
    private WiFiSamplerService collectorService;

    @Override
    protected void onOpen() {
        if (DEBUG) Log.d(TAG, "onOpen()");
        sDb = samplerDatabase.getInstance(this);

        if (wifiReceiver == null) {
            wifiReceiver = new WifiReceiver(this, new WifiDBResolver());
        }
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        bindService(new Intent(this, WiFiSamplerService.class), mConnection, Context.BIND_AUTO_CREATE);
        if (collectorService == null) {
            if (DEBUG) Log.d(TAG, "No collectorService?\n");
        }
    }

    @Override
    protected void onClose() {
        if (DEBUG) Log.d(TAG, "onClose()");
        unregisterReceiver(wifiReceiver);
        wifiReceiver = null;
    }

    @Override
    protected Location update() {
//        if (DEBUG) Log.d(TAG, "update()");

        if (wifiReceiver != null) {
//            if (DEBUG) Log.d(TAG, "update(): Starting scan for WiFi APs");
            wifiReceiver.startScan();
        } else {
            if (DEBUG) Log.d(TAG, "update(): no wifiReceiver???");
        }
        return null;
    }

    private class WifiDBResolver implements WifiReceivedCallback {

        @Override
        public void process(List<String> foundBssids) {

            if (foundBssids == null || foundBssids.isEmpty()) {
                return;
            }
            if (sDb != null) {

                List<Location> locations = new ArrayList<Location>(foundBssids.size());

                for (String bssid : foundBssids) {
                    Location result = sDb.ApLocation(bssid);
                    if (result != null) {
                        locations.add(result);
                    }
                }

                if (locations.isEmpty()) {
                    return;
                }

                //TODO fix LocationHelper:average to not calculate with null values
                //TODO sort out wifis obviously in the wrong spot
                Location avgLoc = LocationHelper.average("wifi", locations);

                if (avgLoc == null) {
                    Log.e(TAG, "Averaging locations did not work.");
                    return;
                }
                report(avgLoc);
            }
        }
    }

    // Stuff for binding to (basically starting) background AP location
    // collection
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder binder) {
            if (DEBUG) Log.e(TAG, "mConnection.ServiceConnection()");
            WiFiSamplerService.MyBinder b = (WiFiSamplerService.MyBinder) binder;
            collectorService = b.getService();
        }
        public void onServiceDisconnected(ComponentName className) {
            if (DEBUG) Log.e(TAG, "mConnection.onServiceDisconnected()");
            collectorService = null;
        }
    };
}
