package org.fitchfamily.android.wifi_backend.sampler.util;

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

import android.os.SystemClock;

public class AgeValue<T> {
    public static <T> AgeValue<T> create() {
        return new AgeValue<T>();
    }

    private T value;
    private long time;

    private AgeValue() {

    }

    /**
     * Use this function to set the value
     * @param value the new value
     * @return this object
     */
    public AgeValue<T> value(T value) {
        this.value = value;
        this.time = now();

        return this;
    }

    /**
     * Use this function to get the last value
     * @return the last value
     */
    public T value() {
        return value;
    }

    /**
     * Use this function to get the age of the last value
     * @return the age of the last value in milliseconds
     */
    public long age() {
        if(value == null) {
            return Long.MAX_VALUE;
        } else {
            return now() - time;
        }
    }

    /**
     * Use this function to get the time which is never decreasing
     * @return the time in milliseconds
     */
    private static long now() {
        return SystemClock.elapsedRealtime();
    }
}
