package org.fitchfamily.android.wifi_backend.sampler;

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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.Receiver;
import org.androidannotations.annotations.SystemService;
import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.configuration;
import org.fitchfamily.android.wifi_backend.configuration.gpsSamplingCallback;
import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;

@EService
public class WiFiSamplerService extends Service implements gpsSamplingCallback, LocationListener {
    private final static String TAG = "WiFiSamplerService";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private boolean scanStarted = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @SystemService
    protected LocationManager locationManager;

    @SystemService
    protected WifiManager wifi;

    private Location location;

    private SamplerDatabase database;

    private long sampleTime;
    private float sampleDistance;

    public class MyBinder extends Binder {
        public WiFiSamplerService getService() {
            return WiFiSamplerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder();
    }

    @AfterInject
    protected void init() {
        if (DEBUG) {
            Log.i(TAG, "service started");
        }

        database = SamplerDatabase.getInstance(this);
        sampleTime = configuration.gpsMinTime;
        sampleDistance = configuration.gpsMinDistance;
        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                sampleTime,
                sampleDistance,
                WiFiSamplerService.this);

        configuration.setGpsSamplingCallback(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        configuration.setGpsSamplingCallback(null);
        locationManager.removeUpdates(WiFiSamplerService.this);

        if (DEBUG) {
            Log.i(TAG, "service destroyed");
        }
    }

    public void updateSamplingConf(long sampleTime, float sampleDistance) {
        if (DEBUG) {
            Log.i(TAG, "updateSamplingConf(" + sampleTime + ", " + sampleDistance + ")");
        }

        // We are in a call back so we can't change the sampling configuration
        // in the caller's thread context. Send a message to the processing thread
        // for it to deal with the issue.
        executor.submit(new Runnable() {
            @Override
            public void run() {
                if ((WiFiSamplerService.this.sampleTime != configuration.gpsMinTime) ||
                        (WiFiSamplerService.this.sampleDistance != configuration.gpsMinDistance)) {

                    WiFiSamplerService.this.sampleTime = configuration.gpsMinTime;
                    WiFiSamplerService.this.sampleDistance = configuration.gpsMinDistance;
                    if (configuration.debug >= configuration.DEBUG_SPARSE)
                        Log.i(TAG, "Changing GPS sampling configuration: " +
                                WiFiSamplerService.this.sampleTime + " ms, " + WiFiSamplerService.this.sampleDistance + " meters");

                    locationManager.removeUpdates(WiFiSamplerService.this);
                    locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                            WiFiSamplerService.this.sampleTime,
                            WiFiSamplerService.this.sampleDistance,
                            WiFiSamplerService.this);
                }
            }
        });
    }

    // This method call when number of wifi connections changed
    @Receiver(actions = WifiManager.SCAN_RESULTS_AVAILABLE_ACTION, registerAt = Receiver.RegisterAt.OnCreateOnDestroy)
    public void onWifiNetworksChanged(Intent intent) {
        if (!isScanStarted()) {
            return;
        }

        setScanStarted(false);
        List<ScanResult> configs = wifi.getScanResults();

        if (!configs.isEmpty()) {
            final List<String> foundBssids = new ArrayList<String>(configs.size());

            for (final ScanResult config : configs) {
                // some strange devices use a dot instead of :
                final String canonicalBSSID = config.BSSID.toUpperCase(Locale.US).replace(".", ":");

                if (WiFiBlacklist.ignore(config.SSID)) {
                    // ignore APs that are likely to be moving around.
                    if (DEBUG) {
                        Log.i(TAG, "Ignoring AP '" + config.SSID + "' BSSID: " + canonicalBSSID);
                    }

                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            database.dropAccessPoint(canonicalBSSID);
                        }
                    });
                } else {
                    if (DEBUG) {
                        Log.i(TAG, "Scan found: '" + config.SSID + "' BSSID: " + canonicalBSSID);
                    }

                    foundBssids.add(canonicalBSSID);
                }
            }

            executor.submit(new Runnable() {
                @Override
                public void run() {
                    long entryTime = System.currentTimeMillis();

                    for (String bssid : foundBssids) {
                        database.addSample(bssid, org.fitchfamily.android.wifi_backend.database.Location.fromAndroidLocation(location));
                    }

                    if (DEBUG) {
                        Log.i(TAG,"Scan process time: " + (System.currentTimeMillis() - entryTime) + " ms");
                    }
                }
            });
        }
    }

    public boolean isScanStarted() {
        return scanStarted;
    }

    public void setScanStarted(boolean scanStarted) {
        this.scanStarted = scanStarted;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onLocationChanged(final Location location) {
        if (DEBUG) {
            Log.i(TAG, "onLocationChanged(" + location + ")");
        }

        if (location.getProvider().equals("gps")) {
            if (location.getAccuracy() <= configuration.gpsMinAccuracy) {
                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "Accurate GPS location.");

                // since this callback is asynchronous, we just pass the
                // message back to the processing thread, to avoid race conditions

                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        WiFiSamplerService.this.location = location;

                        // If WiFi scanning is possible, kick off a scan
                        if (wifi.isWifiEnabled() || isScanAlwaysAvailable()) {
                            setScanStarted(true);
                            wifi.startScan();
                        } else {
                            if (DEBUG) {
                                Log.i(TAG, "Unable to start WiFi scan");
                            }
                        }
                    }
                });
            } else {
                if (DEBUG) {
                    Log.i(TAG, "Ignoring inaccurate GPS location ("+location.getAccuracy()+" meters).");
                }
            }
        } else {
            if (DEBUG) {
                Log.i(TAG, "Ignoring position from \""+location.getProvider()+"\"");
            }
        }
    }

    @Override
    public void onProviderDisabled(String arg0) {
        if (DEBUG) {
            Log.i(TAG, "Provider Disabled.");
        }
    }

    @Override
    public void onProviderEnabled(String arg0) {
        if (DEBUG) {
            Log.i(TAG, "Provider Enabled.");
        }
    }

    @Override
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        if (DEBUG) {
            Log.i(TAG, "Status Changed.");
        }
    }

    private boolean isScanAlwaysAvailable() {
        try {
            return wifi.isScanAlwaysAvailable();
        } catch (NoSuchMethodError ex) {
            return false;
        }
    }
}
