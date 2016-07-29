package org.fitchfamily.android.wifi_backend.wifi;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.database.Database;

public class WifiReceiver extends BroadcastReceiver {
    private static final String TAG = "WiFiReceiver";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private boolean scanStarted = false;
    private static long scanStartTime;
    private final WifiManager wifiManager;
    private final WifiReceivedCallback callback;

    public WifiReceiver(WifiManager wifiManager, WifiReceivedCallback callback) {
        if (DEBUG) {
            Log.i(TAG, "WifiReceiver() constructor");
        }

        this.callback = callback;
        this.wifiManager = wifiManager;
    }

    public void onReceive(Context c, Intent intent) {
        if (!scanStarted()) {
            if(DEBUG) {
                Log.i(TAG, "Scan received but no scan started");
            }
        } else {
            scanStarted(false);
        }
        
        List<ScanResult> configs = wifiManager.getScanResults();

        if(configs == null) {
            if(DEBUG) {
                Log.d(TAG, "wifi.getScanResults() == null");
            }

            return;
        }

        if (DEBUG) {
            Log.i(TAG, "Got " + configs.size() + " wifi access points");
        }

        List<WifiAccessPoint> accessPoints = new ArrayList<>(configs.size());

        for (ScanResult config : configs) {
            accessPoints.add(
                    WifiAccessPoint.builder()
                            .ssid(config.SSID)
                                    // some strange devices use a dot instead of :
                            .rfId(config.BSSID.toUpperCase(Locale.US).replace(".", ":"))
                            .level(config.level)
                            .rfType(Database.TYPE_WIFI)
                            .build()
            );
        }

        callback.processWiFiScanResults(Collections.unmodifiableList(accessPoints));
    }

    public boolean scanStarted() {
        return scanStarted;
    }

    private void scanStarted(boolean scanStarted) {
        if (scanStarted)
            this.scanStartTime = System.currentTimeMillis();
        else {
            if (DEBUG)
                Log.i(TAG, "WiFi Scan time = " + (System.currentTimeMillis()-this.scanStartTime));
        }
        this.scanStarted = scanStarted;
    }

    public void startScan() {
        if (!wifiManager.isWifiEnabled() && !WifiCompat.isScanAlwaysAvailable(wifiManager)) {
            if(DEBUG) {
                Log.i(TAG, "Wifi is disabled and we can't scan either. Not doing anything.");
            }
            scanStarted(false);
        } else {
            if (scanStarted()) {
                if (DEBUG) {
                    Log.i(TAG, "startScan(): Scan already in progress.");
                }
            } else {
                if (DEBUG) {
                    Log.i(TAG, "startScan(): Starting scan.");
                }
                scanStarted(true);
                wifiManager.startScan();
            }
        }
    }

    public interface WifiReceivedCallback {
        void processWiFiScanResults(@NonNull List<WifiAccessPoint> accessPoints);
    }
}
