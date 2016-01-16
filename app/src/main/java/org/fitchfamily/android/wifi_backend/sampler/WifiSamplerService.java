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

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.SystemService;
import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.Configuration;
import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;
import org.fitchfamily.android.wifi_backend.wifi.WifiAccessPoint;
import org.fitchfamily.android.wifi_backend.wifi.WifiCompat;
import org.fitchfamily.android.wifi_backend.wifi.WifiReceiver;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EService
public class WifiSamplerService extends Service implements LocationListener,
        SharedPreferences.OnSharedPreferenceChangeListener, WifiReceiver.WifiReceivedCallback {

    private final static String TAG = "WiFiSamplerService";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @SystemService
    protected LocationManager locationManager;

    @SystemService
    protected WifiManager wifi;

    private Location location;

    private SamplerDatabase database;

    private long sampleTime;
    private float sampleDistance;

    private WifiReceiver wifiReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    @AfterInject
    protected void init() {
        if (DEBUG) {
            Log.i(TAG, "service started");
        }

        database = SamplerDatabase.getInstance(this);
        sampleTime = Configuration.with(this).minimumGpsTimeInMilliseconds();
        sampleDistance = Configuration.with(this).minimumGpsDistanceInMeters();

        try {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                    sampleTime,
                    sampleDistance,
                    WifiSamplerService.this);
        } catch (SecurityException ex) {
            if(DEBUG) {
                Log.w(TAG, "init()", ex);
            }
        }

        Configuration.with(this).register(this);

        wifiReceiver = new WifiReceiver(wifi, this);
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Configuration.with(this).unregister(this);

        try {
            locationManager.removeUpdates(WifiSamplerService.this);
        } catch (SecurityException ex) {
            // ignore
        }

        unregisterReceiver(wifiReceiver);

        if (DEBUG) {
            Log.i(TAG, "service destroyed");
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(TextUtils.equals(key, Configuration.PREF_MIN_GPS_TIME) ||
                TextUtils.equals(key, Configuration.PREF_MIN_GPS_DISTANCE)) {

            updateSamplingConf(
                    Configuration.with(WifiSamplerService.this).minimumGpsTimeInMilliseconds(),
                    Configuration.with(WifiSamplerService.this).minimumGpsDistanceInMeters()
            );
        }
    }

    private void updateSamplingConf(final long sampleTime, final float sampleDistance) {
        if (DEBUG) {
            Log.i(TAG, "updateSamplingConf(" + sampleTime + ", " + sampleDistance + ")");
        }

        // We are in a call back so we can't change the sampling configuration
        // in the caller's thread context. Send a message to the processing thread
        // for it to deal with the issue.
        executor.submit(new Runnable() {
            @Override
            public void run() {
                if ((WifiSamplerService.this.sampleTime != sampleTime) ||
                        (WifiSamplerService.this.sampleDistance != sampleDistance)) {

                    WifiSamplerService.this.sampleTime = sampleTime;
                    WifiSamplerService.this.sampleDistance = sampleDistance;

                    if (DEBUG) {
                        Log.i(TAG, "Changing GPS sampling configuration: " +
                                WifiSamplerService.this.sampleTime + " ms, " + WifiSamplerService.this.sampleDistance + " meters");
                    }

                    try {
                        locationManager.removeUpdates(WifiSamplerService.this);
                        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                                WifiSamplerService.this.sampleTime,
                                WifiSamplerService.this.sampleDistance,
                                WifiSamplerService.this);
                    } catch (SecurityException ex) {
                        if(DEBUG) {
                            Log.w(TAG, "updateSamplingConf()", ex);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void process(@NonNull final List<WifiAccessPoint> accessPoints) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                long entryTime = System.currentTimeMillis();

                for (WifiAccessPoint accessPoint : accessPoints) {
                    if(WifiBlacklist.ignore(accessPoint.ssid())) {
                        database.dropAccessPoint(accessPoint.bssid());
                    } else {
                        database.addSample(accessPoint.bssid(), org.fitchfamily.android.wifi_backend.database.Location.fromAndroidLocation(location));
                    }
                }

                if (DEBUG) {
                    Log.i(TAG,"Scan process time: " + (System.currentTimeMillis() - entryTime) + " ms");
                }
            }
        });
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
            if (location.getAccuracy() <= Configuration.with(this).minimumGpsAccuracyInMeters()) {
                if (DEBUG) {
                    Log.i(TAG, "Accurate GPS location.");
                }

                // since this callback is asynchronous, we just pass the
                // message back to the processing thread, to avoid race conditions

                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        WifiSamplerService.this.location = location;

                        // If WiFi scanning is possible, kick off a scan
                        if (wifi.isWifiEnabled() || WifiCompat.isScanAlwaysAvailable(wifi)) {
                            wifiReceiver.setScanStarted(true);
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
}
