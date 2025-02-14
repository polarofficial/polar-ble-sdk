// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import android.content.Context
import com.polar.sdk.impl.BDBleApiImpl

/**
 * Simply returns a new default implementation of the API.
 */
object PolarBleApiDefaultImpl {
    /**
     * Default implementation constructor for the API.
     *
     * @param context  where API implementation callbacks are run
     * @param features @see polar.com.sdk.api.PolarBleApi feature flags
     * @return default Polar API implementation
     */
    @JvmStatic
    fun defaultImplementation(context: Context, features: Set<PolarBleApi.PolarBleSdkFeature>): BDBleApiImpl {
        return BDBleApiImpl.getInstance(context, features)
    }

    /**
     * @return SDK version number in format major.minor.patch
     */
    @JvmStatic
    fun versionInfo(): String {
        return "5.14.0"
    }
}