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
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.database.EstimateLocation;
import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;
import org.fitchfamily.android.wifi_backend.sampler.WifiSamplerService_;
import org.fitchfamily.android.wifi_backend.ui.MainActivity;
import org.fitchfamily.android.wifi_backend.ui.MainActivity_;
import org.fitchfamily.android.wifi_backend.wifi.WifiAccessPoint;
import org.fitchfamily.android.wifi_backend.wifi.WifiReceiver;
import org.fitchfamily.android.wifi_backend.wifi.WifiReceiver.WifiReceivedCallback;
import org.microg.nlp.api.LocationBackendService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EService
public class BackendService extends LocationBackendService implements WifiReceivedCallback {
    private static final String TAG = "BackendService";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int NOTIFICATION = 42;

    private SamplerDatabase database;
    private WifiReceiver wifiReceiver;
    private Location lastReportLocation = null;
    private long lastReportTime = 0;
    private float lastReportAcc = (float) 100.0;
    private boolean wifiSamplerServiceRunning = false;
    private boolean permissionNotificationShown = false;

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

        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        if(Configuration.with(this).hasLocationAccess()) {
            setWifiSamplerServiceRunning(true);
        }
    }

    @Override
    protected void onClose() {
        if (DEBUG) {
            Log.i(TAG, "onClose()");
        }

        unregisterReceiver(wifiReceiver);
        setWifiSamplerServiceRunning(false);
        setShowPermissionNotification(false);

        wifiReceiver = null;
    }

    @Override
    protected Location update() {
        if (DEBUG) {
            Log.i(TAG, "update()");
        }

        if(!Configuration.with(this).hasLocationAccess()) {
            if (DEBUG) {
                Log.i(TAG, "update(): Permission missing");
            }

            setWifiSamplerServiceRunning(false);
            setShowPermissionNotification(true);
        } else if (wifiReceiver != null) {
            setWifiSamplerServiceRunning(true);
            setShowPermissionNotification(false);

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

    @Override
    public void process(@NonNull List<WifiAccessPoint> accessPoints) {
        if (accessPoints.isEmpty()) {
            doReport(null);
        } else {
            Set<Location> locations = new HashSet<>(accessPoints.size());

            for (WifiAccessPoint accessPoint : accessPoints) {
                EstimateLocation result = database.getLocation(accessPoint.bssid());

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

                doReport(null);
            } else {
                // Find largest group of AP locations. If we don't have at
                // least two near each other then we don't have enough
                // information to get a good location.
                locations = LocationUtil.culledAPs(locations, BackendService.this);

                if (locations == null || locations.size() < 2) {
                    if (DEBUG) {
                        Log.i(TAG, "WifiDBResolver.process(): Insufficient number of WiFi hotspots to resolve location");
                    }

                    doReport(null);
                } else {
                    Location avgLoc = LocationUtil.weightedAverage("wifi", locations);

                    if (avgLoc == null) {
                        if (DEBUG) {
                            Log.e(TAG, "Averaging locations did not work.");
                        }

                        doReport(null);
                    } else {
                        doReport(avgLoc);
                    }
                }
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
        }

        public void onServiceDisconnected(ComponentName className) {
            if (DEBUG) {
                Log.i(TAG, "mConnection.onServiceDisconnected()");
            }
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

                if (DEBUG) {
                    Log.i(TAG, "acc=" + lastReportAcc + ", sec=" + sec + ", scaled accuracy = " + (float) (lastReportAcc + 28.0*sec));
                }
            }

            report(locGuess);
        }
    }

    private void setWifiSamplerServiceRunning(boolean enable) {
        if(enable != wifiSamplerServiceRunning) {
            if (enable) {
                bindService(new Intent(this, WifiSamplerService_.class), mConnection, Context.BIND_AUTO_CREATE);
            } else {
                unbindService(mConnection);
            }

            wifiSamplerServiceRunning = enable;
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
                                .setContentText(getString(R.string.notification_permission))
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
}
