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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.octo.android.robospice.request.SpiceRequest;
import com.opencsv.CSVWriter;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.database.AccessPoint;
import org.fitchfamily.android.wifi_backend.database.Database;
import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;
import org.fitchfamily.android.wifi_backend.util.SimpleLocation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class ExportSpiceRequest extends SpiceRequest<ExportSpiceRequest.Result> {
    public static final String TAG = "WiFiBackendExport";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    public static final int MAX_PROGRESS = 1000;

    private final Context context;
    private final Uri uri;

    public abstract static class Result {

    }

    public ExportSpiceRequest(Context context, Uri uri) {
        super(Result.class);
        this.context = context.getApplicationContext();
        this.uri = uri;
    }

    @Override
    public Result loadDataFromNetwork() throws Exception {
        OutputStream outputStream = context.getContentResolver().openOutputStream(uri);

        if(outputStream == null) {
            throw new IOException();
        }

        try {
            CSVWriter writer = new CSVWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            writer.writeNext(new String[]{"bssid","lat","lon","ssid"});

            Cursor cursor = SamplerDatabase.getInstance(context).getReadableDatabase().query(
                    Database.TABLE_SAMPLES,
                    new String[]{Database.COL_RFID},
                    null, null, null, null, null
            );

            try {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        AccessPoint accessPoint = SamplerDatabase.getInstance(context).query(cursor.getString(0));

                        if(accessPoint != null) {
                            writeCSV(writer, accessPoint);
                        }

                        publishProgress(cursor.getPosition() * MAX_PROGRESS / cursor.getCount());
                    } while (cursor.moveToNext());
                }

            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            writer.close();
        } finally {
            outputStream.close();
        }

        return null;
    }

    private void writeCSV(CSVWriter out, AccessPoint value) throws IOException {

        if(value.moveGuard() != 0) {        // Don't export suspect (moved/moving) APs
            return;
        }

        final String rfId = value.rfId();
        String ssid = "";

        if(!TextUtils.isEmpty(value.ssid())) {
            ssid = value.ssid();
        }

        for(SimpleLocation sample : value.samples()) {
            out.writeNext(new String[]{rfId,
                    Double.toString(sample.latitude()),
                    Double.toString(sample.longitude()),
                    ssid});
        }
    }
}
