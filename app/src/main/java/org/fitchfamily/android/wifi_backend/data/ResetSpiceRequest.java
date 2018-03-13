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

import com.octo.android.robospice.request.SpiceRequest;

import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;

public class ResetSpiceRequest extends SpiceRequest<ResetSpiceRequest.Result> {
    public static final String TAG = "WiFiBackendReset";

    private final Context context;

    public abstract static class Result {
        // nothing
    }

    public ResetSpiceRequest(Context context) {
        super(Result.class);
        this.context = context.getApplicationContext();
    }

    @Override
    public Result loadDataFromNetwork() throws Exception {
        SamplerDatabase.getInstance(context).dropAll();

        return null;
    }
}
