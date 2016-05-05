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

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Location {
    Location() {

    }

    public static Builder builder() {
        return new AutoValue_Location.Builder();
    }

    public static Location fromAndroidLocation(android.location.Location location) {
        return builder()
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .build();
    }

    public static Location fromLatLon(double lat, double lon) {
        return builder().latitude(lat).longitude(lon).build();
    }

    public static Location fromLatLon(String lat, String lon) {
        return fromLatLon(Double.parseDouble(lat),Double.parseDouble(lon));
    }

    public abstract double latitude();
    public abstract double longitude();

    public android.location.Location toAndroidLocation() {
        android.location.Location location = new android.location.Location("wifi");
        location.setLatitude(latitude());
        location.setLongitude(longitude());

        return location;
    }

    public float distanceTo(Location location) {
        return distanceTo(location.toAndroidLocation());
    }

    public float distanceTo(EstimateLocation location) {
        return distanceTo(location.toAndroidLocation());
    }

    public float distanceTo(android.location.Location location) {
        return toAndroidLocation().distanceTo(location);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        Builder() {

        }

        public abstract Builder latitude(double latitude);
        public abstract Builder longitude(double longitude);
        public abstract Location build();
    }
}
