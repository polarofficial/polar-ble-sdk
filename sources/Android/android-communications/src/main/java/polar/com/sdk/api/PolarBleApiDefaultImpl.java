// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api;

import android.content.Context;

import androidx.annotation.NonNull;

import polar.com.sdk.impl.BDBleApiImpl;

/**
 * Simply returns a new default implementation of the API.
 */
public class PolarBleApiDefaultImpl {
    /**
     * Default implementation constructor for the API.
     *
     * @param context  where API implementation callbacks are run
     * @param features @see polar.com.sdk.api.PolarBleApi feature flags
     * @return default Polar API implementation
     */
    @NonNull
    public static PolarBleApi defaultImplementation(final @NonNull Context context, int features) {
        return new BDBleApiImpl(context, features);
    }

    /**
     * @return SDK version number in format major.minor.patch
     */
    @NonNull
    public static String versionInfo() {
        return "3.1.1";
    }
}
