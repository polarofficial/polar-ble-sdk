// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api;

import android.content.Context;

import polar.com.sdk.impl.BDBleApiImpl;

/**
 * Simply returns a new default implementation of the API.
 */
public class PolarBleApiDefaultImpl {
    /**
     * Default implementation constructor for the API.
     * @param context where API implementation callbacks are run
     * @param features @see polar.com.sdk.api.PolarBleApi feature flags
     * @return  default Polar API implementation
     */
    public static PolarBleApi defaultImplementation(final Context context, int features){
        return new BDBleApiImpl(context,features);
    }

    /**
     * @return  SDK version number in format major.minor.patch
     */
    public static String versionInfo(){
        return "2.3.0";
    }
}
