package org.fitchfamily.android.wifi_backend.sampler;

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

import java.util.Locale;

public abstract class WifiBlacklist {
    private WifiBlacklist() {

    }

    public static boolean ignore(String SSID) {
        String SSIDLower = SSID.toLowerCase(Locale.US);

        return (SSIDLower.endsWith("_nomap") ||            // Google unsubscibe option
                SSID.startsWith("Audi") ||          // some cars seem to have this AP on-board
                SSIDLower.contains("iphone") ||            // mobile AP
                SSIDLower.contains("ipad") ||              // mobile AP
                SSIDLower.contains("android") ||           // mobile AP
                SSIDLower.contains("motorola") ||          // mobile AP
                SSIDLower.contains("deinbus.de") ||        // WLAN network on board of German bus
                SSIDLower.contains("db ic bus") ||         // WLAN network on board of German bus
                SSIDLower.contains("fernbus") ||           // WLAN network on board of German bus
                SSIDLower.contains("flixbus") ||           // WLAN network on board of German bus
                SSIDLower.contains("postbus") ||           // WLAN network on board of bus line
                SSIDLower.contains("ecolines") ||          // WLAN network on board of German bus
                SSIDLower.contains("eurolines_wifi") ||    // WLAN network on board of German bus
                SSIDLower.contains("contiki-wifi") ||      // WLAN network on board of bus
                SSIDLower.contains("muenchenlinie") ||     // WLAN network on board of bus
                SSIDLower.contains("guest@ms ") ||         // WLAN network on Hurtigruten ships
                SSIDLower.contains("admin@ms ") ||         // WLAN network on Hurtigruten ships
                SSIDLower.contains("telekom_ice") ||       // WLAN network on DB trains
                SSIDLower.contains("nsb_interakti") ||
                SSIDLower.contentEquals("amtrackconnect") ||    // WLAN network on USA Amtrak trains
                SSIDLower.contentEquals("megabus")              // MegaBus on board of bus
        );
    }
}
