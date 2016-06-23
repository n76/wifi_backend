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
import android.provider.OpenableColumns;
import android.util.Log;

import com.octo.android.robospice.request.SpiceRequest;
import com.opencsv.CSVReader;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.data.util.CountingInputStream;
import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;
import org.fitchfamily.android.wifi_backend.util.SimpleLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ImportSpiceRequest extends SpiceRequest<ImportSpiceRequest.Result> {
    public static final String TAG = "WiFiBackendImport";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    public static final int MAX_PROGRESS = 1000;

    private final Context context;
    private final Uri uri;

    public abstract static class Result {

    }

    public ImportSpiceRequest(Context context, Uri uri) {
        super(Result.class);
        this.context = context.getApplicationContext();
        this.uri = uri;
    }

    @Override
    public Result loadDataFromNetwork() throws Exception {
        long entryTime = System.currentTimeMillis();
        int recCount = 0;

        final long size = getFileSize(uri, context);

        InputStream inputStream = context.getContentResolver().openInputStream(uri);

        if(inputStream == null) {
            throw new IOException();
        }

        CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        SamplerDatabase database = SamplerDatabase.getInstance(context);

        try {
            CSVReader reader = new CSVReader(new InputStreamReader(countingInputStream, "UTF-8"));
            final String[] headerLine = reader.readNext();
            if (headerLine == null) {
                throw new IOException();
            }

            int bssidIndex = -1;
            int latIndex = -1;
            int lonIndex = -1;
            int ssidIndex = -1;
            int idx = 0;
            for (String s : headerLine) {
                if (s.equals("bssid"))
                    bssidIndex = idx;
                else if (s.equals("lat"))
                    latIndex = idx;
                else if (s.equals("lon"))
                    lonIndex = idx;
                else if (s.equals("ssid"))
                    ssidIndex = idx;
                idx++;
            }
            Log.i(TAG, "bssidIndex=" + bssidIndex +
                    ", latIndex=" + latIndex +
                    ", lonIndex=" + lonIndex +
                    ", ssidIndex=" + ssidIndex);
            if ((bssidIndex < 0) || (latIndex < 0) || (lonIndex < 0)) {
                throw new IOException();
            }
            String[] nextLine;

            database.beginTransaction();
            while ((nextLine = reader.readNext()) != null) {
                String bssid = nextLine[bssidIndex];
                String latString = nextLine[latIndex];
                String lonString = nextLine[lonIndex];
                String ssid = "";
                if (ssidIndex >= 0)
                    ssid = nextLine[ssidIndex];

                database.addSample(ssid, bssid, SimpleLocation.fromLatLon(latString,lonString));
                recCount++;
                if ((recCount % 100) == 0) {
                    // Log.i(TAG, "recCount="+recCount+", committing transaction.");
                    database.commitTransaction();
                    database.beginTransaction();
                }

                if(size != 0) {
                    publishProgress(countingInputStream.getBytesRead() * MAX_PROGRESS / size);
                }
            }

        } finally {
            inputStream.close();
            database.commitTransaction();
        }
        Log.i(TAG, "Total Records processed: " + recCount);
        Log.i(TAG, "Import data elapsed time: " + (System.currentTimeMillis() - entryTime) + " ms");

        return null;
    }

    private static int getFileSize(Uri uri, Context context) {
        Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);

        try {
            if(cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getInt(0);
            }
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return 0;
    }
}
