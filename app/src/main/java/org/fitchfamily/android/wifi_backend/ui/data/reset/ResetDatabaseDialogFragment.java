package org.fitchfamily.android.wifi_backend.ui.data.reset;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;

import org.fitchfamily.android.wifi_backend.R;

public class ResetDatabaseDialogFragment extends DialogFragment {
    private static final String TAG = "ResetDatabaseDialogFragment";

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.data_reset)
                .setMessage(R.string.data_reset_warning)
                .setNegativeButton(R.string.data_reset_no, null)
                .setPositiveButton(R.string.data_reset_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ResetProgressDialog_.builder().build().show(getFragmentManager());
                    }
                })
                .create();
    }

    public void show(FragmentManager fragmentManager) {
        show(fragmentManager, TAG);
    }
}
