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

import android.content.Context;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.Configuration;
import org.microg.nlp.api.LocationHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class LocationUtil {
    private static final String TAG = "LocationUtil";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private LocationUtil() {

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
    static Set<Location> culledAPs(Collection<Location> locations, Context context) {
        Set<Set<Location>> locationGroups = divideInGroups(locations,
                2 * Configuration.with(context).accessPointMoveThresholdInMeters());

        List<Set<Location>> clsList = new ArrayList<Set<Location>>(locationGroups);
        Collections.sort(clsList, new Comparator<Set<Location>>() {
            @Override
            public int compare(Set<Location> lhs, Set<Location> rhs) {
                return rhs.size() - lhs.size();
            }
        });

        if (DEBUG) {
            int i = 1;

            for (Set<Location> set : clsList) {
                Log.i(TAG, "culledAPs(): group[" + i + "] = " + set.size());
                i++;
            }
        }

        if (!clsList.isEmpty()) {
            return clsList.get(0);
        } else {
            return null;
        }
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
                if (DEBUG) {
                    Log.i(TAG, "divideInGroups(): Creating new group");
                }

                Set<Location> locGroup = new HashSet<Location>();
                locGroup.add(location);
                bins.add(locGroup);
            }
        }
        return bins;
    }

    // Perform weighted average of the locations of the APs where the weight is
    // inversely proportional to the estimated range. We also collect the weighted
    // average of the range for a range estimate when the number of samples is
    // less than 3. If we have 3 or more samples we estimate the position error
    // based on the variance of our AP location averaging.
    static Location weightedAverage(String source, Collection<Location> locations) {
        Location rslt;

        if (locations == null || locations.size() == 0) {
            return null;
        }

        int num = locations.size();
        double totalWeight = 0;
        double latitude = 0;
        double longitude = 0;
        double accuracy = 0;

        // For variance calculation
        double m2_lat = 0;
        double m2_lon = 0;

        int samples = 0;

        for (Location value : locations) {
            if (value != null) {
                samples++;
                String bssid = value.getExtras().getString(Configuration.EXTRA_MAC_ADDRESS);

                // We weight our average based on a linear value based on signal strength.
                int dBm = value.getExtras().getInt(Configuration.EXTRA_SIGNAL_LEVEL);
                double wgt = WifiManager.calculateSignalLevel(dBm, 100)/100.0;

                if (DEBUG) {
                    Log.i(TAG,
                            String.format("Using with weight=%f mac=%s signal=%d accuracy=%f " +
                                            "latitude=%f longitude=%f",
                                    wgt, bssid,
                                    value.getExtras().getInt(Configuration.EXTRA_SIGNAL_LEVEL),
                                    value.getAccuracy(), value.getLatitude(),
                                    value.getLongitude()));
                }

                double temp = totalWeight + wgt;
                double delta = value.getLatitude() - latitude;
                double rVal = delta * wgt / temp;

                if (DEBUG) {
                    Log.i(TAG, "lat: delta="+delta+", R="+rVal);
                }

                latitude += rVal;
                m2_lat += totalWeight * delta * rVal;

                delta = value.getLongitude() - longitude;
                rVal = delta * wgt / temp;

                if (DEBUG) {
                    Log.i(TAG, "lon: delta="+delta+", R="+rVal);
                }

                longitude += rVal;
                m2_lon += totalWeight * delta * rVal;

//                delta = value.getAccuracy() - accuracy;
//                rVal = delta * wgt / temp;
//                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "accuracy: delta="+delta+", R="+rVal);
//                accuracy += rVal;
                double thisAcc = value.getAccuracy();
                accuracy += thisAcc * thisAcc;

                if (DEBUG) {
                    Log.i(TAG, "accuracy="+Math.sqrt(accuracy)/samples);
                }

                totalWeight = temp;

                if (DEBUG) {
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

        if (DEBUG) {
            Log.i(TAG, "Final Location is (lat="+ latitude + ", lng=" + longitude + ", acc=" + accuracy+")");
        }

        rslt = LocationHelper.create(source, latitude, longitude, (float) accuracy, extras);

        rslt.setTime(System.currentTimeMillis());

        if (DEBUG) {
            Log.i(TAG, rslt.toString());
        }

        return rslt;
    }

    private static boolean locationCompatibleWithGroup(Location location,
                                                       Set<Location> locGroup,
                                                       double accuracy) {
        boolean result = true;

        for (Location other : locGroup) {
            double testDistance = (location.distanceTo(other) -
                    location.getAccuracy() -
                    other.getAccuracy());

            if (DEBUG) {
                Log.i(TAG, "locationCompatibleWithGroup():" +
                        " To other=" + location.distanceTo(other) +
                        " this.acc=" + location.getAccuracy() +
                        " other.acc=" + other.getAccuracy() +
                        " testDist=" + testDistance);
            }

            if (testDistance > accuracy) {
                result = false;
            }
        }

        if (DEBUG) {
            Log.i(TAG, "locationCompatibleWithGroup(): coverage range=" + accuracy + " result=" + result);
        }

        return result;
    }
}
