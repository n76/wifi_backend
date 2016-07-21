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
import org.fitchfamily.android.wifi_backend.util.distanceCache;

@AutoValue
public abstract class SimpleLocation {

    public static Builder builder() {
        return new AutoValue_SimpleLocation.Builder();
    }

    public abstract double latitude();
    public abstract double longitude();
    public abstract float radius();
    public abstract boolean changed();

    private static distanceCache distance = new distanceCache();

    public static SimpleLocation fromAndroidLocation(android.location.Location location) {
        return builder()
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .radius(location.getAccuracy())
                .changed(true)
                .build();
    }

    public android.location.Location toAndroidLocation() {
        android.location.Location location = new android.location.Location("wifi");
        location.setLatitude(latitude());
        location.setLongitude(longitude());
        location.setAccuracy(radius());

        return location;
    }

    public static SimpleLocation fromLatLon(double lat, double lon, boolean changed) {
        return builder().latitude(lat).longitude(lon).radius(150f).changed(changed).build();
    }

    public static SimpleLocation fromLatLon(String lat, String lon, boolean changed) {
        return fromLatLon(Double.parseDouble(lat),Double.parseDouble(lon),changed);
    }

    public float distanceTo(SimpleLocation location) {
        return distanceTo(location.toAndroidLocation());
    }

    public float distanceTo(android.location.Location location) {
        return distance.distanceBetween(this.toAndroidLocation(), location);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        Builder() {
        }

        public abstract Builder latitude(double latitude);
        public abstract Builder longitude(double longitude);
        public abstract Builder radius(float radius);
        public abstract Builder changed(boolean changed);
        public abstract SimpleLocation build();
    }
}
