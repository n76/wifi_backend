package org.fitchfamily.android.wifi_backend.data;

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

import android.text.TextUtils;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.fitchfamily.android.wifi_backend.database.AccessPoint;
import org.fitchfamily.android.wifi_backend.database.Location;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AccessPointAdapter extends TypeAdapter<AccessPoint> {
    public static final AccessPointAdapter instance = new AccessPointAdapter();

    private AccessPointAdapter() {

    }

    @Override
    public void write(JsonWriter out, AccessPoint value) throws IOException {
        out.beginObject();

        out.name("bssid").value(value.bssid());

        if(!TextUtils.isEmpty(value.ssid())) {
            out.name("ssid").value(value.ssid());
        }

        if(value.moveGuard() != 0) {
            out.name("move_guard").value(value.moveGuard());
        }

        out.name("samples").beginArray();
        for(Location sample : value.samples()) {
            out.beginObject()
                    .name("lat").value(sample.latitude())
                    .name("lon").value(sample.longitude())
                    .endObject();
        }
        out.endArray();

        out.endObject();
    }

    @Override
    public AccessPoint read(JsonReader in) throws IOException {
        AccessPoint.Builder builder = AccessPoint.builder()
                .moveGuard(0);

        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();

            if(TextUtils.equals(name, "bssid")) {
                builder.bssid(in.nextString());
            } else if (TextUtils.equals(name, "ssid")) {
                builder.ssid(in.nextString());
            } else if (TextUtils.equals(name, "move_guard")) {
                builder.moveGuard(in.nextInt());
            } else if (TextUtils.equals(name, "samples")) {
                List<Location> samples = new ArrayList<>();
                in.beginArray();

                while (in.hasNext()) {
                    in.beginObject();
                    Location.Builder sample = Location.builder();

                    while (in.hasNext()) {
                        name = in.nextName();

                        if(TextUtils.equals(name, "lon")) {
                            sample.longitude(in.nextDouble());
                        } else if (TextUtils.equals(name, "lat")) {
                            sample.latitude(in.nextDouble());
                        } else {
                            in.skipValue();
                        }
                    }

                    samples.add(sample.build());
                    in.endObject();
                }

                in.endArray();
                builder.samples(samples);
            } else {
                in.skipValue();
            }
        }
        in.endObject();

        return builder.build();
    }
}
