package org.fitchfamily.android.wifi_backend.ui.data.reset;

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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.widget.Toast;

import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import org.androidannotations.annotations.EFragment;
import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.data.ResetSpiceRequest;
import org.fitchfamily.android.wifi_backend.ui.BaseDialogFragment;

@EFragment
public class ResetProgressDialog extends BaseDialogFragment implements RequestListener<ResetSpiceRequest.Result> {
    private static final String TAG = "WiFiBackendResetDlg";

    @Override
    public void onStart() {
        super.onStart();

        getSpiceManager().execute(
                new ResetSpiceRequest(getContext().getApplicationContext()),
                TAG,
                DurationInMillis.ALWAYS_EXPIRED,
                this
        );
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog progressDialog = new ProgressDialog(getActivity());
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage(getString(R.string.data_reset));
        progressDialog.setProgressNumberFormat(null);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);

        return progressDialog;
    }

    public void show(FragmentManager fragmentManager) {
        show(fragmentManager, TAG);
    }

    @Override
    public void onRequestSuccess(ResetSpiceRequest.Result result) {
        dismissAllowingStateLoss();
    }

    @Override
    public void onRequestFailure(SpiceException spiceException) {
        // should not happen
        Toast.makeText(getContext(), "reset failed", Toast.LENGTH_SHORT).show();

        if (BuildConfig.DEBUG) {
            Log.w(TAG, "error cleaning database", spiceException);
        }

        dismissAllowingStateLoss();
    }
}
