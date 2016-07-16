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

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NavUtils;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.database.Database;

/**
 * An activity representing a list of WiFis. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link WifiDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
@EActivity(R.layout.activity_wifi_list)
public class WifiListActivity extends AppCompatActivity {
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean twoPane;

    @ViewById(R.id.wifi_list)
    protected RecyclerView recyclerView;

    @ViewById
    protected Toolbar toolbar;

    private WifiListAdapter adapter = new WifiListAdapter().listener(new WifiListAdapter.Listener() {
        @Override
        public void onWifiClicked(String rfId) {
            if (twoPane) {
                getSupportFragmentManager().beginTransaction()
                        .replace(
                                R.id.wifi_detail_container,
                                WifiDetailFragment_.builder()
                                        .rfId(rfId)
                                        .build()
                        )
                        .commit();
            } else {
                WifiDetailActivity_.intent(WifiListActivity.this)
                        .rfId(rfId)
                        .start();
            }
        }
    });

    @AfterViews
    protected void init() {
        setSupportActionBar(toolbar);

        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        recyclerView.setAdapter(adapter);
        getSupportLoaderManager().initLoader(0, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                return new CursorLoader(WifiListActivity.this)
                        .table(Database.TABLE_SAMPLES)
                        .columns(new String[]{Database.COL_SSID, Database.COL_RFID})
                        .sortOrder(Database.COL_SSID + " ASC");
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                adapter.swap(data);
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
                adapter.swap(null);
            }
        });

        if (findViewById(R.id.wifi_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            twoPane = true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();

        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
