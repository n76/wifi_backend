package org.fitchfamily.android.wifi_backend.ui.data.transfer;

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
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.widget.Toast;

import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.data.ExportSpiceRequest;
import org.fitchfamily.android.wifi_backend.ui.BaseDialogFragment;

@EFragment
public class ExportProgressDialog extends BaseDialogFragment implements
        RequestListener<ExportSpiceRequest.Result> {

    private static final String TAG = "ExportDialog";

    @FragmentArg
    protected Uri uri;

    public void show(FragmentManager fragmentManager) {
        show(fragmentManager, TAG);
    }

    @Override
    public void onStart() {
        super.onStart();
        getSpiceManager().execute(new ExportSpiceRequest(getActivity(), uri), ExportSpiceRequest.TAG, DurationInMillis.ALWAYS_EXPIRED, this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setCancelable(false);
        dialog.setIndeterminate(true);
        dialog.setMessage(getString(R.string.data_export));
        return dialog;
    }

    @Override
    public void onRequestSuccess(ExportSpiceRequest.Result result) {
        dismissAllowingStateLoss();
    }

    @Override
    public void onRequestFailure(SpiceException exception) {
        Toast.makeText(getActivity(), R.string.data_export_error, Toast.LENGTH_LONG).show();
        dismissAllowingStateLoss();
    }
}
