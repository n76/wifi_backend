package org.fitchfamily.android.wifi_backend.backend;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.EService;
import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.configuration;
import org.fitchfamily.android.wifi_backend.database.EstimateLocation;
import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;
import org.fitchfamily.android.wifi_backend.sampler.WiFiSamplerService;
import org.fitchfamily.android.wifi_backend.sampler.WiFiSamplerService_;
import org.microg.nlp.api.LocationBackendService;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.fitchfamily.android.wifi_backend.backend.WifiReceiver.WifiReceivedCallback;

@EService
public class BackendService extends LocationBackendService {
    private static final String TAG = "BackendService";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private SamplerDatabase database;
    private WifiReceiver wifiReceiver;
    private WiFiSamplerService collectorService;
    private Location lastReportLocation = null;
    private long lastReportTime = 0;
    private float lastReportAcc = (float) 100.0;

    @AfterInject
    protected void init() {
        database = SamplerDatabase.getInstance(this);
        wifiReceiver = new WifiReceiver(this, new WifiDBResolver());
    }

    @Override
    protected void onOpen() {
        if (DEBUG) {
            Log.i(TAG, "onOpen()");
        }

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        configuration.fillFromPrefs(sharedPrefs);
        sharedPrefs.registerOnSharedPreferenceChangeListener(configuration.listener);

        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        bindService(new Intent(this, WiFiSamplerService_.class), mConnection, Context.BIND_AUTO_CREATE);

        if (collectorService == null) {
            if (DEBUG) {
                Log.i(TAG, "No collectorService?\n");
            }
        }
    }

    @Override
    protected void onClose() {
        if (DEBUG) {
            Log.i(TAG, "onClose()");
        }

        unregisterReceiver(wifiReceiver);
        unbindService(mConnection);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(configuration.listener);
        wifiReceiver = null;
    }

    @Override
    protected Location update() {
        if (DEBUG) {
            Log.i(TAG, "update()");
        }

        if (wifiReceiver != null) {
            if (DEBUG) {
                Log.i(TAG, "update(): Starting scan for WiFi APs");
            }

            wifiReceiver.startScan();
        } else {
            if (DEBUG) {
                Log.i(TAG, "update(): no wifiReceiver???");
            }
        }

        return null;
    }

    private class WifiDBResolver implements WifiReceivedCallback {
        @Override
        public void process(List<Bundle> foundBssids) {
            if (foundBssids == null || foundBssids.isEmpty()) {
                doReport(null);
                return;
            }

            if (database != null) {
                Set<Location> locations = new HashSet<Location>(foundBssids.size());

                for (Bundle extras : foundBssids) {
                    EstimateLocation result = database.getLocation(extras.getString(configuration.EXTRA_MAC_ADDRESS));

                    if (result != null) {
                        Location location = result.toAndroidLocation();
                        location.setExtras(extras);
                        locations.add(location);
                    }
                }

                if (locations.isEmpty()) {
                    if (DEBUG) {
                        Log.i(TAG, "WifiDBResolver.process(): No APs with known locations");
                    }
                    doReport(null);
                    return;
                }

                // Find largest group of AP locations. If we don't have at
                // least two near each other then we don't have enough
                // information to get a good location.
                locations = LocationUtil.culledAPs(locations);

                if (locations.size() < 2) {
                    if (DEBUG) {
                        Log.i(TAG, "WifiDBResolver.process(): Insufficient number of WiFi hotspots to resolve location");
                    }

                    doReport(null);
                    return;
                }

                Location avgLoc = LocationUtil.weightedAverage("wifi", locations);

                if (avgLoc == null) {
                    Log.e(TAG, "Averaging locations did not work.");
                    doReport(null);
                    return;
                }

                doReport(avgLoc);
            }
        }
    }

    // Stuff for binding to (basically starting) background AP location
    // collection
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder binder) {

            if (DEBUG) {
                Log.i(TAG, "mConnection.onServiceConnected()");
            }

            WiFiSamplerService.MyBinder b = (WiFiSamplerService.MyBinder) binder;
            collectorService = b.getService();
        }
        public void onServiceDisconnected(ComponentName className) {
            if (DEBUG) {
                Log.i(TAG, "mConnection.onServiceDisconnected()");
            }

            collectorService = null;
        }
    };

    //
    // Report location to UnifiedNLP - If current report is null (location unknown) then
    // take the last report and increase its positional error. Assumption is 100km/hr
    // (reasonable driving speed) from point last seen.
    // 100km/hr => 100,000 m/hr ==> ~28m/sec
    //
    private void doReport(Location locReport) {
        if (locReport != null) {
            lastReportLocation = locReport;
            lastReportTime = System.currentTimeMillis();
            lastReportAcc = locReport.getAccuracy();
            report(locReport);
        } else {
            Location locGuess = lastReportLocation;

            if (locGuess != null) {
                long sec = (System.currentTimeMillis() - lastReportTime + 500)/1000;
                locGuess.setAccuracy((float) (lastReportAcc + 28.0 * sec));

                if (configuration.debug >= configuration.DEBUG_VERBOSE) {
                    Log.i(TAG, "acc=" + lastReportAcc + ", sec=" + sec + ", scaled accuracy = " + (float) (lastReportAcc + 28.0*sec));
                }
            }

            report(locGuess);
        }
    }
}
