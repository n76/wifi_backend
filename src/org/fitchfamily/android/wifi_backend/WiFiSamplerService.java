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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;


public class WiFiSamplerService extends Service {
    private final static String TAG = configuration.TAG_PREFIX + "SamplerService";
    private final static boolean DEBUG = configuration.DEBUG;

    private boolean scanStarted = false;
    private long servicestartedat;
    private boolean currentlyTrying;

    private final IBinder mBinder = new MyBinder();

    /* message "what"s */
    private final int GOTFIX = 1;
    private final int GOTSCAN = 2;
    private final int DROP_AP = 3;

    /* and for the upload thread: */
    private final int UPLOADLOCATION = 2;

    private LocationListener locationListener;
    private LocationManager locationManager;

    private WifiManager mWifi;
    private WifiSampleReceiver mReceiverWifi;

    private Location mLocation;

    private samplerDatabase sDb;

    public class MyBinder extends Binder {
        WiFiSamplerService getService() {
            return WiFiSamplerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {

        super.onCreate();

        if (DEBUG) Log.d(TAG, "service started");

        sDb = samplerDatabase.getInstance(this);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                                               configuration.gpsMinTime,
                                               configuration.gpsMinDistance,
                                               new GPSLocationListener());
        mWifi = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        mReceiverWifi = new WifiSampleReceiver();
        registerReceiver(mReceiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (DEBUG) Log.d(TAG, "service destroyed");
    }

    public class GPSLocationListener implements LocationListener
    {
        @Override
        public void onLocationChanged(Location location)
        {
            if (DEBUG) Log.d(TAG, "GPS min accuracy from settings: " + configuration.gpsMinAccuracy);
            if (location.getProvider().equals("gps") &&
                (location.getAccuracy() <= configuration.gpsMinAccuracy)) {
                if (DEBUG) Log.d(TAG, "GPS accuracy: " + location.getAccuracy());

                // since this callback is asynchronous, we just pass the
                // message back to the handler thread, to avoid race conditions

                Message m = new Message();
                m.what = GOTFIX;
                m.obj = location;
                handler.sendMessage(m);
            }
        }

        @Override
        public void onProviderDisabled(String arg0)
        {
            if (DEBUG) Log.d(TAG, ":(");
        }

        @Override
        public void onProviderEnabled(String arg0)
        {
            if (DEBUG) Log.d(TAG, ":)");
        }

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2)
        {
            if (DEBUG) Log.d(TAG, ":/");
        }
    }

    class WifiSampleReceiver extends BroadcastReceiver {

        // This method call when number of wifi connections changed
        public void onReceive(Context c, Intent intent) {
            if (!isScanStarted())
                return;
            setScanStarted(false);
            List<ScanResult> configs = mWifi.getScanResults();

            if (configs.size() > 0) {

                List<String> foundBssids = new ArrayList<String>(configs.size());

                for (ScanResult config : configs) {
                    // some strange devices use a dot instead of :
                    final String canonicalBSSID = config.BSSID.toUpperCase(Locale.US).replace(".",":");
                    // ignore APs that have _nomap suffix on SSID
                    if (config.SSID.endsWith("_nomap")) {
                        if (DEBUG) Log.d(TAG, "Ignoring AP '" + config.SSID + "' BSSID: " + canonicalBSSID);
                        Message m = new Message();
                        m.what = DROP_AP;
                        m.obj = canonicalBSSID;
                        handler.sendMessage(m);
                    } else {
                        foundBssids.add(canonicalBSSID);
                    }
                }
                Message m = new Message();
                m.what = GOTSCAN;
                m.obj = foundBssids;
                handler.sendMessage(m);
            }
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

    final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            long currentTime = SystemClock.elapsedRealtime();
            Message logmessage;

            switch (msg.what) {
                case GOTFIX:
                    mLocation = (Location) msg.obj;

                    // If WiFi scanning is possible, kick off a scan
                    if (mWifi.isWifiEnabled() || mWifi.isScanAlwaysAvailable()) {
                        mWifi.startScan();
                        setScanStarted(true);
                    } else {
                        if (DEBUG) Log.d(TAG, "Unable to start WiFi scan");
                    }
                break;

                case GOTSCAN:
                    long entryTime = System.currentTimeMillis();
                    List<String> foundBssids = (List<String>) msg.obj;
                    for (String bssid : foundBssids) {
                        sDb.addSample( bssid, mLocation );
                    }
                    if (DEBUG) Log.d(TAG,"Scan process time: "+(System.currentTimeMillis()-entryTime)+"ms");
                break;

                case DROP_AP:
                    String bssid = (String) msg.obj;
                    sDb.dropAP(bssid);
                break;

                default:
                break;
            }
        }
    };
}
