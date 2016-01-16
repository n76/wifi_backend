package org.fitchfamily.android.wifi_backend;

import android.content.Context;

import com.octo.android.robospice.UncachedSpiceService;
import com.octo.android.robospice.networkstate.NetworkStateChecker;

public class SpiceService extends UncachedSpiceService {
    @Override
    protected NetworkStateChecker getNetworkStateChecker() {
        return new NetworkStateChecker() {
            @Override
            public boolean isNetworkAvailable(Context context) {
                return true;
            }

            @Override
            public void checkPermissions(Context context) {

            }
        };
    }
}
