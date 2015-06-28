package org.fitchfamily.android.wifi_backend;

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

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;

/**
 * Created by tfitch on 6/20/15.
 */
public class settingsPrefsFragment extends PreferenceFragment {
    protected String TAG = configuration.TAG_PREFIX+"settings";

    public settingsPrefsFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        setPrefSummary("gps_accuracy_preference", getString(R.string.meters));
        setPrefSummary("gps_min_distance_preference", getString(R.string.meters));
        setPrefSummary("gps_min_time_preference", getString(R.string.seconds));
        setPrefSummary("ap_min_range_preference", getString(R.string.meters));
        setPrefSummary("ap_moved_range_preference", getString(R.string.meters));
        setPrefSummary("ap_moved_guard_preference", getString(R.string.samples));
        setPrefSummary("db_size_preference", String.valueOf(configuration.dbRecords) + " " + getString(R.string.records));
    }

    private void setPrefSummary(String prefKey, String suffix) {
        EditTextPreference myPreference = (EditTextPreference) this.findPreference(prefKey);
        if (myPreference != null) {
            if(myPreference.getText()==null) {      //Assure no null values
                myPreference.setText("");
            }
            if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "setPrefSummary(): " + prefKey + " is " + myPreference.getText());
            myPreference.setSummary(myPreference.getText() + " " + suffix);
            myPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary(newValue.toString());
                    return true;
                }
            });
        } else {
            if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "setPrefSummary(): " + prefKey + " is null");
        }
    }

}