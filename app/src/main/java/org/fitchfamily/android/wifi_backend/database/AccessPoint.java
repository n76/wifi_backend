package org.fitchfamily.android.wifi_backend.database;

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

import android.support.annotation.Nullable;
import android.util.Log;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.fitchfamily.android.wifi_backend.BuildConfig;

import java.util.ArrayList;
import java.util.List;

@AutoValue
public abstract class AccessPoint {
    private static final int MIN_SAMPLES = 3;
    private static final String TAG = "WiFiBackendAP";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    AccessPoint() {

    }

    public static String bssid(String bssid) {
        return bssid.replace(":", "");
    }

    public static String readableBssid(String bssid) {
        return bssid(bssid).replaceAll(".(?!$).(?!$)", "$0:");
    }

    public static Builder builder() {
        return new AutoValue_AccessPoint.Builder();
    }

    public abstract String bssid();
    @Nullable
    public abstract String ssid();
    public abstract ImmutableList<Location> samples();
    public abstract int moveGuard();

    public Location sample(int index) {
        if(index < samples().size()) {
            return samples().get(index);
        } else {
            return null;
        }
    }

    /**
     * Use this function to get the estimate location
     * @return the estimate location or null if no samples are available
     */
    public EstimateLocation estimateLocation() {
        if(samples().size() == 0) {
            return null;
        }

        // get center of points

        double latitude = 0.0;
        double longitude = 0.0;

        for(Location sample : samples()) {
            latitude += sample.latitude();
            longitude += sample.longitude();
        }

        latitude /= (double) samples().size();
        longitude /= (double) samples().size();

        Location center = Location.builder()
                .latitude(latitude)
                .longitude(longitude)
                .build();

        // get biggest distance

        float radius = 0.0f;

        for(Location sample : samples()) {
            radius = Math.max(radius, center.distanceTo(sample));
        }


        return EstimateLocation.builder()
                .latitude(latitude)
                .longitude(longitude)
                .radius(radius)
                .build();
    }

    public abstract Builder buildUpon();

    private static float perimeter(List<Location> samples) {
        float result = 0.0f;

        for (Location sample1 : samples) {
            for (Location sample2 : samples) {
                result += sample1.distanceTo(sample2);
            }
        }

        return result;
    }

    public float perimeter() {
        return perimeter(samples());
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bssid(String bssid);
        public abstract Builder ssid(String ssid);
        public abstract Builder samples(List<Location> samples);
        public abstract Builder moveGuard(int moveGuard);
        public abstract AccessPoint build();

        protected abstract String bssid();
        protected abstract int moveGuard();
        protected abstract ImmutableList<Location> samples();

        protected int samplesCount() {
            try {
                return samples().size();
            } catch (Exception ex) {
                return 0;
            }
        }

        public Builder moved(int movedGuardCount) {
            return moveGuard(movedGuardCount);
        }

        public Builder decMoved() {
            return moveGuard(Math.max(0, moveGuard()));
        }

        public Builder addSample(Location location) {
            return addSample(location, MIN_SAMPLES);
        }

        public Builder addSample(Location location, int maxSamples) {
            maxSamples = Math.max(maxSamples, MIN_SAMPLES);

            if(samplesCount() < maxSamples) {
                List<Location> samples = new ArrayList<>();

                if(samplesCount() != 0) {
                    samples.addAll(samples());
                }

                samples.add(location);

                return samples(samples);
            } else {
                // We will take the new sample an see if we can make a triangle with
                // a larger perimeter by replacing one of our current samples with
                // the new one.

                List<Location> bestSamples = samples();
                float bestPerimeter = perimeter(bestSamples);

                for (int i = 0; i < samples().size(); i++) {
                    List<Location> samples = new ArrayList<Location>(samples());
                    samples.set(i, location);

                    float guessPerimeter = perimeter(samples);

                    if (guessPerimeter > bestPerimeter) {
                        bestSamples = samples;
                        bestPerimeter = guessPerimeter;

                        if (DEBUG) {
                            Log.i(TAG, "Better perimeter point found on " + bssid() + ", i=" + i);
                        }
                    }
                }

                return samples(bestSamples);
            }
        }

        public Builder clearSamples() {
            return samples(new ArrayList<Location>());
        }
    }
}
