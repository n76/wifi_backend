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
import java.util.Collection;
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
import android.os.Bundle;
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
                Location avgLoc = weightedAverage("wifi", locations);

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

        public Location weightedAverage(String source, Collection<Location> locations) {
        Location rslt = null;

        if (locations == null || locations.size() == 0) {
            return null;
        }
        int num = locations.size();
        int totalWeight = 0;
        double latitude = 0;
        double longitude = 0;
        float accuracy = 0;
        int altitudes = 0;
        double altitude = 0;
        for (Location value : locations) {
            if (value != null) {
                // Create weight value based on accuracy. Higher accuracy
                // (lower coverage radius/range) get higher weight.
                float thisAcc = (float) value.getAccuracy();
                if (thisAcc < 1f)
                    thisAcc = 1f;
                int wgt = (int) (100000f / thisAcc);
                if (wgt < 1)
                    wgt = 1;

                latitude += (value.getLatitude() * wgt);
                longitude += (value.getLongitude() * wgt);
                accuracy += (value.getAccuracy() * wgt);
                totalWeight += wgt;

//                if (DEBUG) Log.d(TAG, "(lat="+ latitude + ", lng=" + longitude + ", acc=" + accuracy + ") / wgt=" + totalWeight );

                if (value.hasAltitude()) {
                    altitude += value.getAltitude();
                    altitudes++;
                }
            }
        }
                latitude = latitude / totalWeight;
        longitude = longitude / totalWeight;
        accuracy = accuracy / totalWeight;
        altitude = altitude / altitudes;

        Bundle extras = new Bundle();
        extras.putInt("AVERAGED_OF", num);
//        if (DEBUG) Log.d(TAG, "Location est (lat="+ latitude + ", lng=" + longitude + ", acc=" + accuracy);
        if (altitudes > 0) {
            rslt = LocationHelper.create(source,
                          latitude,
                          longitude ,
                          altitude,
                          accuracy,
                          extras);
        } else {
            rslt = LocationHelper.create(source,
                          latitude,
                          longitude,
                          accuracy,
                          extras);
        }


        // Now that we have an estimated Lat/Lon, make a wild guess as to
        // our accuracy. If we have overlapping coverages and our lat/lon
        // is within the overlap then we will look at how far our lat/lon is
        // from the transmitter and do a delta based on the transmitter range:
        //
        // XMIT Range +---------------------------------------------------->|
        //                             EstLoc +
        //                                    |---------------------------->|
        //                                          Estimated Accuracy

        float accEst = accuracy;
        for (Location value : locations) {
            float rng = value.distanceTo(rslt);
            float xmitRng = value.getAccuracy();
//            if (DEBUG) Log.d(TAG, "xmitRng="+xmitRng+", rng="+rng);
            if (rng < xmitRng) {
                float thisEst = xmitRng - rng;
                if (thisEst < accEst) {
//                    if (DEBUG) Log.d(TAG, "New accEst="+thisEst);
                    accEst = thisEst;
                }
            }
        }
        rslt.setAccuracy(accEst);
        rslt.setTime(System.currentTimeMillis());
        return rslt;
    }

}
