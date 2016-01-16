package org.fitchfamily.android.wifi_backend.wifi;

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

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class WifiAccessPoint {
    WifiAccessPoint() {

    }

    public static Builder builder() {
        return new AutoValue_WifiAccessPoint.Builder();
    }

    public abstract String ssid();
    public abstract String bssid();
    public abstract int level();

    @AutoValue.Builder
    public abstract static class Builder {
        Builder() {

        }

        public abstract Builder ssid(String ssid);
        public abstract Builder bssid(String bssid);
        public abstract Builder level(int level);
        public abstract WifiAccessPoint build();
    }
}
