package org.fitchfamily.android.wifi_backend;

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
    private static final String TAG = configuration.TAG_PREFIX + "service";

    private static final int MIN_SIGNAL_LEVEL = -200;

    private samplerDatabase sDb;
    private WifiReceiver wifiReceiver;
    private boolean networkAllowed;
    private WiFiSamplerService collectorService;
    private boolean checkVar = false;
    private Location lastReportLocation = null;
    private long lastReportTime = 0;
    private float lastReportAcc = (float)100.0;

    @Override
    protected void onOpen() {
        if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "onOpen()");

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
            if (configuration.debug >= configuration.DEBUG_SPARSE) Log.i(TAG, "No collectorService?\n");
        }
    }

    @Override
    protected void onClose() {
        if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "onClose()");
        unregisterReceiver(wifiReceiver);
        unbindService(mConnection);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(configuration.listener);
        wifiReceiver = null;
    }

    @Override
    protected Location update() {
        if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "update()");

        if (wifiReceiver != null) {
            if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "update(): Starting scan for WiFi APs");
            wifiReceiver.startScan();
        } else {
            if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "update(): no wifiReceiver???");
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
            if (sDb != null) {

                Set<Location> locations = new HashSet<Location>(foundBssids.size());

                for (Bundle extras : foundBssids) {
                    Location result = sDb.ApLocation(extras.getString(configuration.EXTRA_MAC_ADDRESS));
                    if (result != null) {
                        result.setExtras(extras);
                        locations.add(result);
                    }
                }

                if (locations.isEmpty()) {
                    if (configuration.debug >= configuration.DEBUG_SPARSE) Log.i(TAG, "WifiDBResolver.process(): No APs with known locations");
                    doReport(null);
                    return;
                }

                // Find largest group of AP locations. If we don't have at
                // least two near each other then we don't have enough
                // information to get a good location.
                locations = culledAPs(locations);
                if (locations.size() < 2) {
                    if (configuration.debug >= configuration.DEBUG_SPARSE) Log.i(TAG, "WifiDBResolver.process(): Insufficient number of WiFi hotspots to resolve location");
                    doReport(null);
                    return;
                }

                Location avgLoc = weightedAverage("wifi", locations);

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
            if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "mConnection.onServiceConnected()");
            WiFiSamplerService.MyBinder b = (WiFiSamplerService.MyBinder) binder;
            collectorService = b.getService();
        }
        public void onServiceDisconnected(ComponentName className) {
            if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "mConnection.onServiceDisconnected()");
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
            if (configuration.debug >= configuration.DEBUG_VERBOSE)
                Log.i(TAG, "locationCompatibleWithGroup():"+
                           " To other=" + location.distanceTo(other) +
                           " this.acc=" + location.getAccuracy() +
                           " other.acc=" + other.getAccuracy() +
                           " testDist=" + testDistance);
            if (testDistance > accuracy)
                result = false;
        }
        if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "locationCompatibleWithGroup(): coverage range=" + accuracy + " result=" + result);
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
                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "divideInGroups(): Creating new group");
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
    //
    // If we are at the extreme limit of possible coverage (apMovedThreshold)
    // from two APs then those APs could be a distance of 2*apMovedThreshold apart.
    // So we will group the APs based on that large distance.
    //
    public Set<Location> culledAPs(Collection<Location> locations) {
        Set<Set<Location>> locationGroups = divideInGroups(locations,
                                                           2*configuration.apMovedThreshold);
        List<Set<Location>> clsList = new ArrayList<Set<Location>>(locationGroups);
        Collections.sort(clsList, new Comparator<Set<Location>>() {
            @Override
            public int compare(Set<Location> lhs, Set<Location> rhs) {
                return rhs.size() - lhs.size();
            }
        });

        if (configuration.debug >= configuration.DEBUG_VERBOSE) {
            int i = 1;
            for (Set<Location> set : clsList) {
                Log.i(TAG, "culledAPs(): group[" + i + "] = "+set.size());
                i++;
            }
        }
        if (!clsList.isEmpty())
            return clsList.get(0);
        else
            return null;
    }

    private int getSignalLevel(Location location) {
        return Math.abs(location.getExtras().getInt(configuration.EXTRA_SIGNAL_LEVEL) -
                MIN_SIGNAL_LEVEL);
    }

    // estimated range is based on the signal level and estimated coverage radius
    // of the AP. Basically get a linear percentage (display type) version of the
    // received signal strength and multiply that times the range. Could all be
    // done in one expression but want to make it clear.
    private double estRange(Location location) {
        int dBm = location.getExtras().getInt(configuration.EXTRA_SIGNAL_LEVEL);
        double sigPercent = WifiManager.calculateSignalLevel(dBm, 100)/100.0;
        double apRange = Math.min(location.getAccuracy(), configuration.apAssumedAccuracy);
        return sigPercent * apRange;
    }

    // Perform weighted average of the locations of the APs where the weight is
    // inversely porportional to the estimated range. We also collect the weighted
    // average of the range for a range estimate when the number of samples is
    // less than 3. If we have 3 or more samples we estimate the position error
    // based on the variance of our AP location averaging.
    public Location weightedAverage(String source, Collection<Location> locations) {
        Location rslt = null;

        if (locations == null || locations.size() == 0) {
            return null;
        }
        int num = locations.size();
        double totalWeight = 0;
        double latitude = 0;
        double longitude = 0;
        double accuracy = 0;
        double altitudeWeight = 0;
        double altitude = 0;

        // For variance calculation
        double m2_lat = 0;
        double m2_lon = 0;

        int samples = 0;

        for (Location value : locations) {
            if (value != null) {
                samples++;
                String bssid = value.getExtras().getString(configuration.EXTRA_MAC_ADDRESS);

                // We weight our average based on a linear value based on signal strength.
                int dBm = value.getExtras().getInt(configuration.EXTRA_SIGNAL_LEVEL);
                double wgt = WifiManager.calculateSignalLevel(dBm, 100)/100.0;

                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG,
                        String.format("Using with weight=%f mac=%s signal=%d accuracy=%f " +
                                        "latitude=%f longitude=%f",
                                wgt, bssid,
                                value.getExtras().getInt(configuration.EXTRA_SIGNAL_LEVEL),
                                value.getAccuracy(), value.getLatitude(),
                                value.getLongitude()));

                double temp = totalWeight + wgt;
                double delta = value.getLatitude() - latitude;
                double rVal = delta * wgt / temp;
                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "lat: delta="+delta+", R="+rVal);
                latitude += rVal;
                m2_lat += totalWeight * delta * rVal;

                delta = value.getLongitude() - longitude;
                rVal = delta * wgt / temp;
                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "lon: delta="+delta+", R="+rVal);
                longitude += rVal;
                m2_lon += totalWeight * delta * rVal;

//                delta = value.getAccuracy() - accuracy;
//                rVal = delta * wgt / temp;
//                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "accuracy: delta="+delta+", R="+rVal);
//                accuracy += rVal;
                double thisAcc = value.getAccuracy();
                accuracy += thisAcc * thisAcc;
                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "accuracy="+Math.sqrt(accuracy)/samples);

                totalWeight = temp;
                if (configuration.debug >= configuration.DEBUG_VERBOSE) {
                    Log.i(TAG, "Location est (lat="+ latitude + ", m2="+m2_lat+"), " +
                               "(lon="+ latitude + ", m2="+m2_lon+"), " +
                               "(acc="+ accuracy + ")");
                    Log.i(TAG, "wgt="+wgt+", totalWeight="+totalWeight);
                }
            }
        }

        accuracy = Math.sqrt(accuracy)/samples;
        Bundle extras = new Bundle();
        extras.putInt("AVERAGED_OF", num);
        if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "Final Location est (lat="+ latitude + ", lng=" + longitude + ", acc=" + accuracy+")");
        rslt = LocationHelper.create(source, latitude, longitude, (float)accuracy, extras);

        rslt.setTime(System.currentTimeMillis());
        if (configuration.debug >= configuration.DEBUG_SPARSE) Log.i(TAG, rslt.toString());
        return rslt;
    }

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
