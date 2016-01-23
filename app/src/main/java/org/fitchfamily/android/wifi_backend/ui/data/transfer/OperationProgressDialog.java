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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.SpiceRequest;
import com.octo.android.robospice.request.listener.PendingRequestListener;
import com.octo.android.robospice.request.listener.RequestProgress;
import com.octo.android.robospice.request.listener.RequestProgressListener;

import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.InstanceState;
import org.fitchfamily.android.wifi_backend.ui.BaseDialogFragment;

@EFragment
public abstract class OperationProgressDialog<R> extends BaseDialogFragment implements RequestProgressListener, PendingRequestListener<R> {
    private ProgressDialog progressDialog;

    @InstanceState
    protected boolean hasStartedRequest;

    @Override
    public void onStart() {
        super.onStart();

        if(!hasStartedRequest) {
            getSpiceManager().execute(getRequest(), getCacheKey(), DurationInMillis.ALWAYS_EXPIRED, this);

            hasStartedRequest = true;
        } else {
            getSpiceManager().addListenerIfPending(getRequest().getResultType(), getCacheKey(), this);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final int maxProgress = getMaxProgress();

        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(maxProgress == 0);
        progressDialog.setTitle(getMessage());
        progressDialog.setMax(getMaxProgress());
        progressDialog.setProgressNumberFormat(null);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);

        return progressDialog;
    }

    protected abstract String getMessage();
    protected abstract String getFailureMessage();
    protected abstract SpiceRequest<R> getRequest();
    protected abstract Object getCacheKey();
    protected int getMaxProgress() {
        return 0;
    };

    @Override
    public void onRequestProgressUpdate(RequestProgress progress) {
        if(progressDialog != null) {
            progressDialog.setProgress((int) progress.getProgress());
        }
    }

    @Override
    public void onRequestNotFound() {
        dismissAllowingStateLoss();
    }

    @Override
    public void onRequestSuccess(R result) {
        dismissAllowingStateLoss();
    }

    @Override
    public void onRequestFailure(SpiceException exception) {
        Toast.makeText(getActivity(), getFailureMessage(), Toast.LENGTH_LONG).show();
        dismissAllowingStateLoss();
    }
}
