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
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.ViewById;
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.database.AccessPoint;
import org.fitchfamily.android.wifi_backend.database.Database;

/**
 * An activity representing a single WiFi detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link WifiListActivity}.
 */
@EActivity(R.layout.activity_wifi_detail)
public class WifiDetailActivity extends AppCompatActivity {
    @ViewById
    protected Toolbar toolbar;

    @Extra
    protected String rfId;

    @InstanceState
    protected boolean initialized;

    @AfterViews
    protected void init() {
        setSupportActionBar(toolbar);

        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (!initialized) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            getSupportFragmentManager().beginTransaction()
                    .replace(
                            R.id.container,
                            WifiDetailFragment_.builder()
                                    .rfId(rfId)
                                    .build()
                    )
                    .commit();

            initialized = true;
        }

        getSupportLoaderManager().initLoader(0, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                return new CursorLoader(WifiDetailActivity.this)
                        .table(Database.TABLE_SAMPLES)
                        .columns(new String[]{Database.COL_SSID})
                        .selection(Database.COL_RFID + " = ?")
                        .selectionArgs(new String[]{rfId});
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                if(data != null && data.moveToFirst()) {
                    getSupportActionBar().setTitle(data.getString(0));
                    getSupportActionBar().setSubtitle(AccessPoint.readableBssid(rfId));
                } else {
                    getSupportActionBar().setTitle(AccessPoint.readableBssid(rfId));
                    getSupportActionBar().setSubtitle(null);
                }
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
                getSupportActionBar().setTitle(rfId);
                getSupportActionBar().setSubtitle(null);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();

        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            navigateUpTo(new Intent(this, WifiListActivity_.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
