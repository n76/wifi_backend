package org.fitchfamily.android.wifi_backend.ui;

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

import android.app.LoaderManager;
import android.content.Loader;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import org.androidannotations.annotations.AfterPreferences;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.PreferenceScreen;
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.ui.statistic.DatabaseStatistic;
import org.fitchfamily.android.wifi_backend.ui.statistic.DatabaseStatisticLoader;

@EFragment
@PreferenceScreen(R.xml.main)
public class MainSettingsFragment extends PreferenceFragment {
    private Preference statistic;

    @AfterPreferences
    protected void init() {
        statistic = findPreference("db_size_preference");

        getLoaderManager().initLoader(0, null, new LoaderManager.LoaderCallbacks<DatabaseStatistic>() {
            @Override
            public Loader<DatabaseStatistic> onCreateLoader(int i, Bundle bundle) {
                return new DatabaseStatisticLoader(getActivity());
            }

            @Override
            public void onLoadFinished(Loader<DatabaseStatistic> loader, DatabaseStatistic databaseStatistic) {
                setRecords(databaseStatistic.accessPointCount());
            }

            @Override
            public void onLoaderReset(Loader<DatabaseStatistic> loader) {
                setRecords(0);
            }
        });
    }

    private void setRecords(int number) {
        statistic.setSummary(getResources().getString(R.string.statistic_records, number));
    }
}