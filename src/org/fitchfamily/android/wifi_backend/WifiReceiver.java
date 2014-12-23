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
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiReceiver extends BroadcastReceiver {

    private boolean scanStarted = false;
    private WifiManager wifi;
    private String TAG = configuration.TAG_PREFIX+"WiFiReceiver";
    private final static int DEBUG = configuration.DEBUG;
    private WifiReceivedCallback callback;

    public WifiReceiver(Context ctx, WifiReceivedCallback aCallback) {
        if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "WifiReceiver() constructor");
        wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        callback = aCallback;
    }

    public void onReceive(Context c, Intent intent) {
        if (!isScanStarted())
            return;
        setScanStarted(false);
        List<ScanResult> configs = wifi.getScanResults();

        if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "Got " + configs.size() + " wifi access points");

        if (configs.size() > 0) {

            List<String> foundBssids = new ArrayList<String>(configs.size());

            for (ScanResult config : configs) {
                // some strange devices use a dot instead of :
                final String canonicalBSSID = config.BSSID.toUpperCase(Locale.US).replace(".",":");
                // ignore APs that have _nomap suffix on SSID
                if (config.SSID.endsWith("_nomap")) {
                    if (DEBUG >= configuration.DEBUG_SPARSE) Log.d(TAG, "Ignoring AP '" + config.SSID + "' BSSID: " + canonicalBSSID);
                } else {
                    foundBssids.add(canonicalBSSID);
                }
            }

            callback.process(foundBssids);
        }

    }

    public boolean isScanStarted() {
        return scanStarted;
    }

    public void setScanStarted(boolean scanStarted) {
        this.scanStarted = scanStarted;
    }

    public interface WifiReceivedCallback {

        void process(List<String> foundBssids);

    }

    public void startScan() {
        setScanStarted(true);
        boolean scanAlwaysAvailable = false;
        try {
            scanAlwaysAvailable = wifi.isScanAlwaysAvailable();
        } catch (NoSuchMethodError e) {
            scanAlwaysAvailable = false;
        }
        if (!wifi.isWifiEnabled() && !scanAlwaysAvailable) {
            Log.d(TAG, "Wifi is disabled and we can't scan either. Not doing anything.");
        }
        wifi.startScan();
    }
}
