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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.SystemService;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.Configuration;
import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.ui.MainActivity_;
import org.fitchfamily.android.wifi_backend.ui.MainActivity;
import org.fitchfamily.android.wifi_backend.util.AgeValue;
import org.fitchfamily.android.wifi_backend.util.distanceCache;
import org.fitchfamily.android.wifi_backend.util.LocationUtil;
import org.fitchfamily.android.wifi_backend.util.SimpleLocation;
import org.fitchfamily.android.wifi_backend.wifi.WifiAccessPoint;
import org.fitchfamily.android.wifi_backend.wifi.WifiBlacklist;
import org.fitchfamily.android.wifi_backend.wifi.WifiReceiver;
import org.fitchfamily.android.wifi_backend.wifi.WifiReceiver.WifiReceivedCallback;

import org.microg.nlp.api.LocationBackendService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EService
public class BackendService extends LocationBackendService implements WifiReceivedCallback {
    private static final String TAG = "WiFiBackendSrv";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int NOTIFICATION = 42;

    private static BackendService instance;
    private SamplerDatabase database;
    private WifiReceiver wifiReceiver;
    private Thread thread;
    private boolean gpsMonitorRunning = false;
    private boolean permissionNotificationShown = false;

    private static distanceCache distanceResults = new distanceCache();

    private AgeValue<SimpleLocation> gpsLocation = AgeValue.create();

    @SystemService
    protected NotificationManager notificationManager;

    @SystemService
    protected WifiManager wifiManager;

    @AfterInject
    protected void init() {
        database = SamplerDatabase.getInstance(this);
        wifiReceiver = new WifiReceiver(wifiManager, this);
    }

    @Override
    protected void onOpen() {
        if (DEBUG) {
            Log.i(TAG, "onOpen()");
        }
        instance = this;
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        if(Configuration.with(this).hasLocationAccess()) {
            setgpsMonitorRunning(true);
        }
    }

    @Override
    protected void onClose() {
        if (DEBUG) {
            Log.i(TAG, "onClose()");
        }

        unregisterReceiver(wifiReceiver);
        setgpsMonitorRunning(false);
        setShowPermissionNotification(false);

        wifiReceiver = null;
    }

    @Override
    protected Location update() {
        if(!Configuration.with(this).hasLocationAccess()) {
            if (DEBUG) {
                Log.i(TAG, "update(): Permission missing");
            }
            setgpsMonitorRunning(false);
            setShowPermissionNotification(true);
        } else if (wifiReceiver != null) {
            setgpsMonitorRunning(true);
            setShowPermissionNotification(false);
            if (DEBUG) {
                Log.i(TAG, "Starting scan for WiFi APs");
            }
            wifiReceiver.startScan();
        } else {
            if (DEBUG) {
                Log.i(TAG, "update(): no wifiReceiver???");
            }
        }
        return null;
    }

    private void gpsLocationUpdated(final android.location.Location locReport) {
        if (DEBUG) {
            Log.i(TAG, "GPS Location update: " + locReport.toString());
        }
        gpsLocation.value(SimpleLocation.fromAndroidLocation(locReport));
        wifiReceiver.startScan();
    }

    public static void instanceGpsLocationUpdated(final android.location.Location locReport) {
        if (instance != null) {
            instance.gpsLocationUpdated(locReport);
        }
    }
    @Override
    public synchronized void processWiFiScanResults(@NonNull List<WifiAccessPoint> apList) {
        if (thread != null) {
            Log.d(TAG, "processWiFiScanResults() : Thread busy?!");
            return;
        }
        final List<WifiAccessPoint> accessPoints = apList;

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long locationAge = gpsLocation.age();

                // always accept value which are not more than 500 milliseconds old because
                // scanning wifis (after receiving GPS) can take a short amount of time

                if(locationAge < 500 ||
                        (locationAge < Configuration.with(BackendService.this).validGpsTimeInMilliseconds())) {
                    Log.d(TAG,"GPS Location fresh.");
                    updateWiFiDatabase(gpsLocation.value(), accessPoints);
                }
                Location result = wiFiBasedLocation(accessPoints);
                report(result);
                thread = null;
            }
        });
        thread.start();
    }

    // Stuff for binding to (basically starting) background AP location
    // collection
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder binder) {

            if (DEBUG) {
                Log.i(TAG, "mConnection.onServiceConnected()");
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            if (DEBUG) {
                Log.i(TAG, "mConnection.onServiceDisconnected()");
            }
        }
    };

    private void setgpsMonitorRunning(boolean enable) {
        if(enable != gpsMonitorRunning) {
            if (enable) {
                bindService(new Intent(this, gpsMonitor_.class), mConnection, Context.BIND_AUTO_CREATE);
            } else {
                unbindService(mConnection);
            }

            gpsMonitorRunning = enable;
        }
    }

    private void setShowPermissionNotification(boolean visible) {
        if(visible != permissionNotificationShown) {
            if(visible) {
                if(DEBUG) {
                    Log.i(TAG, "setShowPermissionNotification(true)");
                }

                notificationManager.notify(
                        NOTIFICATION,
                        new NotificationCompat.Builder(this)
                                .setWhen(0)
                                .setShowWhen(false)
                                .setAutoCancel(false)
                                .setOngoing(true)
                                .setContentIntent(
                                        PendingIntent.getActivity(
                                                this,
                                                0,
                                                MainActivity_.intent(this).action(MainActivity.Action.request_permission).get(),
                                                PendingIntent.FLAG_UPDATE_CURRENT
                                        )
                                )
                                .setContentTitle(getString(R.string.app_title))
                                .setContentText(getString(R.string.preference_grant_permission))
                                .setSmallIcon(R.drawable.ic_stat_no_location)
                                .build()
                );
            } else {
                if(DEBUG) {
                    Log.i(TAG, "setShowPermissionNotification(false)");
                }

                notificationManager.cancel(NOTIFICATION);
            }

            permissionNotificationShown = visible;
        }
    }

    private Location wiFiBasedLocation(@NonNull List<WifiAccessPoint> accessPoints) {
        if (accessPoints.isEmpty()) {
            return null;
        } else {
            Set<Location> locations = new HashSet<>(accessPoints.size());

            for (WifiAccessPoint accessPoint : accessPoints) {
                SimpleLocation result = database.getLocation(accessPoint.rfId());

                if (result != null) {
                    Bundle extras = new Bundle();
                    extras.putInt(Configuration.EXTRA_SIGNAL_LEVEL, accessPoint.level());

                    Location location = result.toAndroidLocation();
                    location.setExtras(extras);
                    locations.add(location);
                }
            }

            if (locations.isEmpty()) {
                if (DEBUG) {
                    Log.i(TAG, "WifiDBResolver.process(): No APs with known locations");
                }
                return null;
            } else {
                // Find largest group of AP locations. If we don't have at
                // least two near each other then we don't have enough
                // information to get a good location.
                locations = LocationUtil.culledAPs(locations, BackendService.this);

                if (locations == null || locations.size() < 2) {
                    if (DEBUG) {
                        Log.i(TAG, "WifiDBResolver.process(): Insufficient number of WiFi hotspots to resolve location");
                    }
                    return null;
                } else {
                    Location avgLoc = LocationUtil.weightedAverage("wifi", locations);

                    if (avgLoc == null) {
                        if (DEBUG) {
                            Log.e(TAG, "Averaging locations did not work.");
                        }
                        return null;
                    } else {
                        avgLoc.setTime(System.currentTimeMillis());
                        return avgLoc;
                    }
                }
            }
        }
    }

    private void updateWiFiDatabase(SimpleLocation location, final List<WifiAccessPoint> accessPoints) {
        long entryTime = System.currentTimeMillis();


        database.beginTransaction();
        for (WifiAccessPoint accessPoint : accessPoints) {
            if (WifiBlacklist.ignore(accessPoint.ssid())) {
                database.dropAccessPoint(accessPoint.rfId());
            } else {
                database.addSample(accessPoint.rfType(), accessPoint.ssid(), accessPoint.rfId(), location);
            }
        }
        database.commitTransaction();

        if (DEBUG) {
            distanceResults.logCacheStats();
        }
    }
}
