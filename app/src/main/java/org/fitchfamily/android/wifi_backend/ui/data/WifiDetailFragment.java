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

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;
import org.androidannotations.annotations.ViewById;
import org.fitchfamily.android.wifi_backend.Configuration;
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.database.Database;

/**
 * A fragment representing a single WiFi detail screen.
 * This fragment is either contained in a {@link WifiListActivity}
 * in two-pane mode (on tablets) or a {@link WifiDetailActivity}
 * on handsets.
 */
@EFragment(R.layout.wifi_detail)
public class WifiDetailFragment extends Fragment {
    @FragmentArg
    protected String bssid;

    @ViewById
    protected TextView accuracy;

    @ViewById
    protected View container;

    @ViewById
    protected TextView samples;

    private double lat, lon, acc;
    private int smp;
    private String ssid;

    @AfterViews
    protected void init() {
        container.setVisibility(View.INVISIBLE);

        getLoaderManager().initLoader(0, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                return new CursorLoader(getContext())
                        .table(Database.TABLE_SAMPLES)
                        .selection(Database.COL_BSSID + " = ?")
                        .selectionArgs(new String[]{bssid})
                        .columns(new String[]{
                                Database.COL_LATITUDE,
                                Database.COL_LONGITUDE,
                                Database.COL_RADIUS,
                                Database.COL_SSID,
                                Database.COL_LAT1,
                                Database.COL_LON1,
                                Database.COL_LAT2,
                                Database.COL_LON2,
                                Database.COL_LAT3,
                                Database.COL_LON3
                        });
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
                if (cursor != null && cursor.moveToFirst()) {
                    container.setVisibility(View.VISIBLE);

                    lat = cursor.getDouble(0);
                    lon = cursor.getDouble(1);
                    acc = Math.max(Configuration.with(getContext()).accessPointAssumedAccuracy(), cursor.getFloat(2));
                    ssid = cursor.getString(3);
                    smp = countSamples(cursor, 4);

                    WifiDetailFragment.this.accuracy.setText(accuracy());
                    WifiDetailFragment.this.samples.setText(samples());
                } else {
                    container.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
                container.setVisibility(View.INVISIBLE);
            }
        });
    }

    private static boolean hasSample(Cursor cursor, int index) {
        return cursor.getDouble(index) != 0.d || cursor.getDouble(index + 1) != 0.d;
    }

    private static int countSamples(Cursor cursor, int index) {
        int result = 0;

        for(int i = 0; i < 3; i++) {
            if(hasSample(cursor, index + (i * 2))) {
                result++;
            }
        }

        return result;
    }

    private String accuracy() {
        return getString(R.string.wifi_detail_accuracy, getString(R.string.pref_meters, String.valueOf((int) acc)));
    }

    private String samples() {
        return getString(R.string.pref_samples, String.valueOf(smp));
    }

    @Click
    protected void map() {
        startActivity(new Intent(android.content.Intent.ACTION_VIEW,
                Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)
        ));
    }
}
