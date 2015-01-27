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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static final String TAG = configuration.TAG_PREFIX + "backend-service";
    private final static int DEBUG = configuration.DEBUG;

    private samplerDatabase sDb;
    private WifiReceiver wifiReceiver;
    private boolean networkAllowed;
    private WiFiSamplerService collectorService;

    @Override
    protected void onOpen() {
        if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "onOpen()");

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        configuration.fillFromPrefs(sharedPrefs);
        sharedPrefs.registerOnSharedPreferenceChangeListener(configuration.listener);

        sDb = samplerDatabase.getInstance(this);

        if (wifiReceiver == null) {
            wifiReceiver = new WifiReceiver(this, new WifiDBResolver());
        }
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        bindService(new Intent(this, WiFiSamplerService.class), mConnection, Context.BIND_AUTO_CREATE);
        if (collectorService == null) {
            if (DEBUG >= configuration.DEBUG_SPARSE) Log.d(TAG, "No collectorService?\n");
        }
    }

    @Override
    protected void onClose() {
        if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "onClose()");
        unregisterReceiver(wifiReceiver);
        unbindService(mConnection);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(configuration.listener);
        wifiReceiver = null;
    }

    @Override
    protected Location update() {
        if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "update()");

        if (wifiReceiver != null) {
            if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "update(): Starting scan for WiFi APs");
            wifiReceiver.startScan();
        } else {
            if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "update(): no wifiReceiver???");
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
                Location avgLoc = weightedAverage("wifi", culledAPs(locations));

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
            if (DEBUG >= configuration.DEBUG_VERBOSE) Log.e(TAG, "mConnection.ServiceConnection()");
            WiFiSamplerService.MyBinder b = (WiFiSamplerService.MyBinder) binder;
            collectorService = b.getService();
        }
        public void onServiceDisconnected(ComponentName className) {
            if (DEBUG >= configuration.DEBUG_VERBOSE) Log.e(TAG, "mConnection.onServiceDisconnected()");
            collectorService = null;
        }
    };

    private static boolean locationCompatibleWithGroup(Location location,
                                                       Set<Location> locGroup,
                                                       double accuracy) {
        boolean result = true;
        for (Location other : locGroup) {
            double testDistance = (location.distanceTo(other) -
                                   location.getAccuracy() -
                                   other.getAccuracy());
            if (DEBUG >= configuration.DEBUG_VERBOSE)
                Log.e(TAG, "locationCompatibleWithGroup():"+
                           " To other=" + location.distanceTo(other) +
                           " this.acc=" + location.getAccuracy() +
                           " other.acc=" + other.getAccuracy() +
                           " testDist=" + testDistance);
            if (testDistance > accuracy)
                result = false;
        }
        if (DEBUG >= configuration.DEBUG_VERBOSE) Log.e(TAG, "locationCompatibleWithGroup(): accuracy=" + accuracy + " result=" + result);
        return result;
    }

    private static Set<Set<Location>> divideInGroups(Collection<Location> locations,
                                                     double accuracy) {
        Set<Set<Location>> bins = new HashSet<Set<Location>>();
        for (Location location : locations) {
            boolean used = false;
            for (Set<Location> locGroup : bins) {
                if (locationCompatibleWithGroup(location, locGroup, accuracy)) {
                    locGroup.add(location);
                    used = true;
                }
            }
            if (!used) {
                if (DEBUG >= configuration.DEBUG_VERBOSE) Log.e(TAG, "divideInGroups(): Creating new group");
                Set<Location> locGroup = new HashSet<Location>();
                locGroup.add(location);
                bins.add(locGroup);
            }
        }
        return bins;
    }

    //
    // The collector service attempts to detect and not report moved/moving APs.
    // But it can't be perfect. This routine looks at all the APs and returns the
    // largest subset (group) that are within a reasonable distance of one another.
    //
    // The hope is that a single moved/moving AP that is seen now but whose
    // location was detected miles away can be excluded from the set of APs
    // we use to determine where the phone is at this moment.
    //
    // We do this by creating collections of APs where all the APs in a group
    // are within a plausible distance of one another. A single AP may end up
    // in multiple groups. When done, we return the largest group.
    public Set<Location> culledAPs(Collection<Location> locations) {
        Set<Set<Location>> locationGroups = divideInGroups(locations,
                                                           configuration.apMovedThreshold);
        List<Set<Location>> clsList = new ArrayList<Set<Location>>(locationGroups);
        Collections.sort(clsList, new Comparator<Set<Location>>() {
            @Override
            public int compare(Set<Location> lhs, Set<Location> rhs) {
                return rhs.size() - lhs.size();
            }
        });

        if (DEBUG >= configuration.DEBUG_VERBOSE) {
            int i = 1;
            for (Set<Location> set : clsList) {
                Log.e(TAG, "culledAPs(): group[" + i + "] = "+set.size());
                i++;
            }
        }
        if (!clsList.isEmpty())
            return clsList.get(0);
        else
            return null;
    }

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

                if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "(lat="+ latitude + ", lng=" + longitude + ", acc=" + accuracy + ") / wgt=" + totalWeight );

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
        if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "Location est (lat="+ latitude + ", lng=" + longitude + ", acc=" + accuracy);
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
        // rng        +---------------------->|
        //                             EstLoc +
        //                                    |---------------------------->|
        //                                          Estimated Accuracy
        //
        // Average all of the estimated AP accuracy values thus determined
        // for a new overall accuracy estimate..
        float accAvg = 0.0f;
        for (Location value : locations) {
            final float rng = value.distanceTo(rslt);
            final float xmitRng = value.getAccuracy();
            float thisEst = xmitRng - rng;
            if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "xmitRng="+xmitRng+", rng="+rng+", accEst="+thisEst);
            if (thisEst <= 0.0f) {
                if (DEBUG >= configuration.DEBUG_SPARSE) Log.d(TAG, "location ("+thisEst+") is beyond AP range ("+xmitRng+")");
                thisEst = accuracy;     // Beyond estimated AP range, use weighted avg for accuracy
            }
            accAvg += thisEst;
        }
        accAvg = accAvg/locations.size();
        if (DEBUG >= configuration.DEBUG_VERBOSE) Log.d(TAG, "Revised accuracy estimate: "+accAvg);
        rslt.setAccuracy(accAvg);
        rslt.setTime(System.currentTimeMillis());
        if (DEBUG >= configuration.DEBUG_SPARSE) Log.d(TAG, rslt.toString());
        return rslt;
    }

}
