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

import android.Manifest;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;

import org.androidannotations.annotations.AfterPreferences;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.PreferenceScreen;
import org.fitchfamily.android.wifi_backend.Configuration;
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.ui.data.WifiListActivity_;
import org.fitchfamily.android.wifi_backend.ui.data.export.ExportProgressDialog_;
import org.fitchfamily.android.wifi_backend.ui.statistic.DatabaseStatistic;
import org.fitchfamily.android.wifi_backend.ui.statistic.DatabaseStatisticLoader;

@EFragment
@PreferenceScreen(R.xml.main)
public class MainSettingsFragment extends PreferenceFragment {
    private static final int EXPORT_REQUEST_CODE = 1;

    private Preference statistic;
    private Preference permission;

    @AfterPreferences
    protected void init() {
        statistic = findPreference("db_size_preference");
        permission = findPreference("grant_permission");
        final Preference export = findPreference("db_export");

        permission.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                requestPermission();
                return false;
            }
        });

        statistic.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                WifiListActivity_.intent(getActivity()).start();
                return true;
            }
        });

        export.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    startActivityForResult(
                            new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                    .setType("text/json")
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .putExtra(Intent.EXTRA_TITLE, "wifi.json"),
                            EXPORT_REQUEST_CODE
                    );
                }

                return true;
            }
        });

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

        checkPermission();
    }

    @OnActivityResult(EXPORT_REQUEST_CODE)
    protected void export(int resultCode, Intent intent) {
        if(resultCode == Activity.RESULT_OK) {
            ExportProgressDialog_.builder()
                    .uri(intent.getData())
                    .build()
                    .show(getFragmentManager());
        }
    }

    private void setRecords(int number) {
        statistic.setSummary(getResources().getString(R.string.statistic_records, number));
    }

    private void requestPermission() {
        if(!Configuration.with(getActivity()).hasLocationAccess()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        }
    }

    private void checkPermission() {
        if(Configuration.with(getActivity()).hasLocationAccess()) {
            getPreferenceScreen().removePreference(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkPermission();
        }
    }
}