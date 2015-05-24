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

import org.fitchfamily.android.wifi_backend.configuration.gpsSamplingCallback;

public class WiFiSamplerService extends Service implements gpsSamplingCallback {
    private final static String TAG = configuration.TAG_PREFIX + "SamplerService";

    private boolean scanStarted = false;
    private long servicestartedat;
    private boolean currentlyTrying;

    private final IBinder mBinder = new MyBinder();

    /* message "what"s */
    private final int GOTFIX = 1;
    private final int GOTSCAN = 2;
    private final int DROP_AP = 3;
    private final int CHANGE_SAMPLING = 4;

    /* and for the upload thread: */
    private final int UPLOADLOCATION = 2;

    private LocationListener locationListener;
    private LocationManager locationManager;

    private WifiManager mWifi;
    private WifiSampleReceiver mReceiverWifi;

    private Location mLocation;

    private samplerDatabase sDb;

    private long mSampleTime;
    private float mSampleDistance;
    private GPSLocationListener mGpsLocationListener = new GPSLocationListener();

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

        if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "service started");

        sDb = samplerDatabase.getInstance(this);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        mSampleTime = configuration.gpsMinTime;
        mSampleDistance = configuration.gpsMinDistance;
        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                                               mSampleTime,
                                               mSampleDistance,
                                               mGpsLocationListener);
        configuration.setGpsSamplingCallback(this);
        mWifi = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        mReceiverWifi = new WifiSampleReceiver();
        registerReceiver(mReceiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        unregisterReceiver(mReceiverWifi);
        mReceiverWifi = null;
        configuration.setGpsSamplingCallback(null);
        locationManager.removeUpdates(mGpsLocationListener);
        if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "service destroyed");
    }

    public void updateSamplingConf(long sampleTime, float sampleDistance) {
        if (configuration.debug >= configuration.DEBUG_SPARSE)
            Log.i(TAG, "updateSamplingConf(" + sampleTime + ", " + sampleDistance + ")");
        // We are in a call back so we can't change the sampling configuration
        // in the caller's thread context. Send a message to the main thread
        // for it to deal with the issue.
        Message m = new Message();
        m.what = CHANGE_SAMPLING;
        m.obj = null;
        handler.sendMessage(m);
    }

    public class GPSLocationListener implements LocationListener
    {
        @Override
        public void onLocationChanged(Location location)
        {
            if (configuration.debug >= configuration.DEBUG_NORMAL) Log.i(TAG, "onLocationChanged(" + location + ")");
            if (location.getProvider().equals("gps")) {
                if  (location.getAccuracy() <= configuration.gpsMinAccuracy) {
                    if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "Accurate GPS location.");

                    // since this callback is asynchronous, we just pass the
                    // message back to the handler thread, to avoid race conditions

                    Message m = new Message();
                    m.what = GOTFIX;
                    m.obj = location;
                    handler.sendMessage(m);
                } else {
                    if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "Ignoring inaccurate GPS location ("+location.getAccuracy()+" meters).");
                }
            } else {
                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "Ignoring position from \""+location.getProvider()+"\"");
            }
        }

        @Override
        public void onProviderDisabled(String arg0)
        {
            if (configuration.debug >= configuration.DEBUG_NORMAL) Log.i(TAG, "Provider Disabled.");
        }

        @Override
        public void onProviderEnabled(String arg0)
        {
            if (configuration.debug >= configuration.DEBUG_NORMAL) Log.i(TAG, "Provider Enabled.");
        }

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2)
        {
            if (configuration.debug >= configuration.DEBUG_NORMAL) Log.i(TAG, "Status Changed.");
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

                    // ignore APs that are likely to be moving around.
                    String SSIDlower = config.SSID.toLowerCase(Locale.US);
                    boolean noMap = (SSIDlower.endsWith("_nomap") ||            // Google unsubscibe option
                                     config.SSID.startsWith("Audi") ||          // some cars seem to have this AP on-board
                                     SSIDlower.contains("iphone") ||            // mobile AP
                                     SSIDlower.contains("ipad") ||              // mobile AP
                                     SSIDlower.contains("android") ||           // mobile AP
                                     SSIDlower.contains("motorola") ||          // mobile AP
                                     SSIDlower.contains("deinbus.de") ||        // WLAN network on board of German bus
                                     SSIDlower.contains("db ic bus") ||         // WLAN network on board of German bus
                                     SSIDlower.contains("fernbus") ||           // WLAN network on board of German bus
                                     SSIDlower.contains("flixbus") ||           // WLAN network on board of German bus
                                     SSIDlower.contains("postbus") ||           // WLAN network on board of bus line
                                     SSIDlower.contains("ecolines") ||          // WLAN network on board of German bus
                                     SSIDlower.contains("eurolines_wifi") ||    // WLAN network on board of German bus
                                     SSIDlower.contains("contiki-wifi") ||      // WLAN network on board of bus
                                     SSIDlower.contains("muenchenlinie") ||     // WLAN network on board of bus
                                     SSIDlower.contains("guest@ms ") ||         // WLAN network on Hurtigruten ships
                                     SSIDlower.contains("admin@ms ") ||         // WLAN network on Hurtigruten ships
                                     SSIDlower.contains("telekom_ice") ||       // WLAN network on DB trains
                                     SSIDlower.contains("nsb_interakti"));

                    if (noMap) {
                        if (configuration.debug >= configuration.DEBUG_SPARSE) Log.i(TAG, "Ignoring AP '" + config.SSID + "' BSSID: " + canonicalBSSID);
                        Message m = new Message();
                        m.what = DROP_AP;
                        m.obj = canonicalBSSID;
                        handler.sendMessage(m);
                    } else {
                        if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "Scan found: '" + config.SSID + "' BSSID: " + canonicalBSSID);
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
                    boolean scanAlwaysAvailable = false;
                    try {
                        scanAlwaysAvailable = mWifi.isScanAlwaysAvailable();
                    } catch (NoSuchMethodError e) {
                        scanAlwaysAvailable = false;
                    }
                    if (mWifi.isWifiEnabled() || scanAlwaysAvailable) {
                        setScanStarted(true);
                        mWifi.startScan();
                    } else {
                        if (configuration.debug >= configuration.DEBUG_SPARSE) Log.i(TAG, "Unable to start WiFi scan");
                    }
                break;

                case GOTSCAN:
                    long entryTime = System.currentTimeMillis();
                    List<String> foundBssids = (List<String>) msg.obj;
                    for (String bssid : foundBssids) {
                        sDb.addSample( bssid, mLocation );
                    }
                    if (configuration.debug >= configuration.DEBUG_NORMAL) Log.i(TAG,"Scan process time: "+(System.currentTimeMillis()-entryTime)+"ms");
                break;

                case DROP_AP:
                    String bssid = (String) msg.obj;
                    sDb.dropAP(bssid);
                break;

                case CHANGE_SAMPLING:
                    if ((mSampleTime != configuration.gpsMinTime) ||
                        (mSampleDistance != configuration.gpsMinDistance)) {
                        mSampleTime = configuration.gpsMinTime;
                        mSampleDistance = configuration.gpsMinDistance;
                        if (configuration.debug >= configuration.DEBUG_SPARSE)
                            Log.i(TAG,"Changing GPS sampling configuration: "+
                                      mSampleTime+" ms, "+ mSampleDistance+" meters");

                        locationManager.removeUpdates(mGpsLocationListener);
                        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                                                               mSampleTime,
                                                               mSampleDistance,
                                                               mGpsLocationListener);
                    }
                break;

                default:
                break;
            }
        }
    };
}
