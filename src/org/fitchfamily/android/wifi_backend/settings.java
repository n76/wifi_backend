package org.fitchfamily.android.wifi_backend;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import android.content.Intent;

import android.preference.EditTextPreference;
import android.preference.Preference.OnPreferenceChangeListener;

public class settings extends Activity {
    protected String TAG = configuration.TAG_PREFIX+"settings";

    class prefsFragment extends PreferenceFragment {

        public prefsFragment() {
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
                if (configuration.debug >= configuration.DEBUG_VERBOSE) Log.i(TAG, "setPrefSummary(): " + prefKey + " is "+myPreference.getText());
                myPreference.setSummary(myPreference.getText() + " " + suffix);
                myPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefsFragment myFrag = new prefsFragment();
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                                                        new prefsFragment()).commit();
    }

}
