package org.fitchfamily.android.wifi_backend.ui.statistic;

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.LocalBroadcastManager;

import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;

public class DatabaseStatisticLoader extends AsyncTaskLoader<DatabaseStatistic> {
    private BroadcastReceiver changeReceiver;

    public DatabaseStatisticLoader(Context context) {
        super(context);
    }

    @Override
    public DatabaseStatistic loadInBackground() {
        return DatabaseStatistic.builder()
                .accessPointCount(SamplerDatabase.getInstance(getContext()).getAccessPointCount())
                .build();
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        if(changeReceiver == null) {
            changeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onContentChanged();
                }
            };

            LocalBroadcastManager.getInstance(getContext()).registerReceiver(changeReceiver, new IntentFilter(SamplerDatabase.ACTION_DATA_CHANGED));
        }

        forceLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();

        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(changeReceiver);
        changeReceiver = null;
    }
}
