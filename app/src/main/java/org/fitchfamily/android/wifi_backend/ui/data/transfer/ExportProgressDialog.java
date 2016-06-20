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

import android.net.Uri;
import android.support.v4.app.FragmentManager;

import com.octo.android.robospice.request.SpiceRequest;

import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;
import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.data.ExportSpiceRequest;

@EFragment
public class ExportProgressDialog extends OperationProgressDialog<ExportSpiceRequest.Result> {

    private static final String TAG = "WiFiBackendExportDlg";

    @FragmentArg
    protected Uri uri;

    @Override
    protected String getMessage() {
        return getString(R.string.data_export);
    }

    @Override
    protected String getFailureMessage() {
        return getString(R.string.data_export_error);
    }

    @Override
    protected int getMaxProgress() {
        return ExportSpiceRequest.MAX_PROGRESS;
    }

    @Override
    protected SpiceRequest<ExportSpiceRequest.Result> getRequest() {
        return new ExportSpiceRequest(getContext(), uri);
    }

    @Override
    protected Object getCacheKey() {
        return uri.toString();
    }

    public void show(FragmentManager fragmentManager) {
        show(fragmentManager, TAG);
    }
}
