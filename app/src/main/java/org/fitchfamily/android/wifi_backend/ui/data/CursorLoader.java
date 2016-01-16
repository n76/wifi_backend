package org.fitchfamily.android.wifi_backend.ui.data;

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
import android.database.Cursor;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.LocalBroadcastManager;

import org.fitchfamily.android.wifi_backend.database.SamplerDatabase;

public class CursorLoader extends AsyncTaskLoader<Cursor> {
    private String table;
    private String[] columns;
    private String selection;
    private String[] selectionArgs;
    private String sortOrder;
    private Cursor cursor;
    private BroadcastReceiver changeReceiver;

    public CursorLoader(Context context) {
        super(context);
    }

    @Override
    public Cursor loadInBackground() {
        return SamplerDatabase.getInstance(getContext()).getReadableDatabase().query(table, columns, selection, selectionArgs, null, null, sortOrder);
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

        if (cursor != null) {
            // deliver old data (if available)
            deliverResult(cursor);
        }

        forceLoad();
    }

    @Override
    protected void onStopLoading() {
        super.deliverResult(null);
        super.onStopLoading();
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();

        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(changeReceiver);
        changeReceiver = null;

        // stop loader
        onStopLoading();

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        cursor = null;
    }

    @Override
    public void deliverResult(Cursor cursor) {
        if (isReset()) {
            if (cursor != null) {
                cursor.close();
            }
        } else {
            Cursor oldCursor = this.cursor;
            this.cursor = cursor;

            if (isStarted()) {
                super.deliverResult(cursor);
            }

            if (oldCursor != null && !oldCursor.isClosed()) {
                oldCursor.close();
            }
        }
    }

    @Override
    public void onCanceled(Cursor cursor) {
        super.onCanceled(cursor);

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    public String table() {
        return table;
    }

    public CursorLoader table(String table) {
        this.table = table;
        return this;
    }

    public String[] columns() {
        return columns;
    }

    public CursorLoader columns(String[] columns) {
        this.columns = columns;
        return this;
    }

    public String selection() {
        return selection;
    }

    public CursorLoader selection(String selection) {
        this.selection = selection;
        return this;
    }

    public String[] selectionArgs() {
        return selectionArgs;
    }

    public CursorLoader selectionArgs(String[] selectionArgs) {
        this.selectionArgs = selectionArgs;
        return this;
    }

    public String sortOrder() {
        return sortOrder;
    }

    public CursorLoader sortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public CursorLoader load() {
        forceLoad();
        return this;
    }
}
