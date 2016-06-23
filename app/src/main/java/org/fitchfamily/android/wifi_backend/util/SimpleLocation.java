package org.fitchfamily.android.wifi_backend.util;

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

import com.google.auto.value.AutoValue;
import android.support.v4.util.LruCache;

@AutoValue
public abstract class SimpleLocation {
    SimpleLocation() {

    }

    public static Builder builder() {
        return new AutoValue_SimpleLocation.Builder();
    }

    public abstract double latitude();
    public abstract double longitude();
    public abstract float radius();

    private class distanceRec {
        public float distance;
    }

    private final LruCache<android.location.Location, distanceRec> distanceCache = new LruCache<>(10);

    public static SimpleLocation fromAndroidLocation(android.location.Location location) {
        return builder()
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .radius(location.getAccuracy())
                .build();
    }

    public android.location.Location toAndroidLocation() {
        android.location.Location location = new android.location.Location("wifi");
        location.setLatitude(latitude());
        location.setLongitude(longitude());
        location.setAccuracy(radius());

        return location;
    }

    public static SimpleLocation fromLatLon(double lat, double lon) {
        return builder().latitude(lat).longitude(lon).radius(150f).build();
    }

    public static SimpleLocation fromLatLon(String lat, String lon) {
        return fromLatLon(Double.parseDouble(lat),Double.parseDouble(lon));
    }

    public float distanceTo(SimpleLocation location) {
        return distanceTo(location.toAndroidLocation());
    }

    // In deciding whether to add a new data point into our AP we do a lot
    // of distance computations. Easy to code as it is just a single call.
    // But that call has to do a lot of spherical trig so it can be slow
    // and power hungry. We will attempt to reduce the load by taking advantage
    // of the fact that we are looking for the distance between the same set
    // of points time after time and cache the results.
    public float distanceTo(android.location.Location location) {
        distanceRec cachedValue= distanceCache.get(location);

        if (cachedValue == null) {
            cachedValue = new distanceRec();
            cachedValue.distance = toAndroidLocation().distanceTo(location);
            distanceCache.put(location,cachedValue);
        }
        return cachedValue.distance;
    }

    @AutoValue.Builder
    public abstract static class Builder {
        Builder() {

        }

        public abstract Builder latitude(double latitude);
        public abstract Builder longitude(double longitude);
        public abstract Builder radius(float radius);
        public abstract SimpleLocation build();
    }
}
