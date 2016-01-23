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

import com.google.gson.stream.JsonReader;
import com.octo.android.robospice.request.SpiceRequest;

import org.fitchfamily.android.wifi_backend.data.util.CountingInputStream;
import org.fitchfamily.android.wifi_backend.database.AccessPoint;
import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ImportSpiceRequest extends SpiceRequest<ImportSpiceRequest.Result> {
    public static final String TAG = "ImportSpiceRequest";
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
        final long size = getFileSize(uri, context);

        InputStream inputStream = context.getContentResolver().openInputStream(uri);

        if(inputStream == null) {
            throw new IOException();
        }

        CountingInputStream countingInputStream = new CountingInputStream(inputStream);

        try {
            JsonReader reader = new JsonReader(new InputStreamReader(countingInputStream, "UTF-8"));
            reader.beginArray();

            while (reader.hasNext()) {
                AccessPoint newAccessPoint = AccessPointAdapter.instance.read(reader);
                AccessPoint oldAccessPoint = SamplerDatabase.getInstance(context).query(newAccessPoint.bssid());

                if(oldAccessPoint == null) {
                    SamplerDatabase.getInstance(context).insert(newAccessPoint);
                } else {
                    if(newAccessPoint.perimeter() >= oldAccessPoint.perimeter()) {
                        // only import if there is a higher perimeter

                        SamplerDatabase.getInstance(context).update(newAccessPoint);
                    }
                }

                if(size != 0) {
                    publishProgress(countingInputStream.getBytesRead() * MAX_PROGRESS / size);
                }
            }

            reader.endArray();
        } finally {
            inputStream.close();
        }

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
