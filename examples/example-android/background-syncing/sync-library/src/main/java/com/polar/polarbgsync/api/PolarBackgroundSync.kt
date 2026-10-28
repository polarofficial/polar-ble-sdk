// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.api

import android.content.Context
import com.polar.polarbgsync.internal.PolarBackgroundSyncImpl

/**
 * Factory for creating background sync API instances.
 *
 * Usage:
 * ```
 * val syncApi = PolarBackgroundSync.create(context)
 * val localApi = PolarBackgroundSync.localDataApi(context)
 * ```
 */
object PolarBackgroundSync {

    /**
     * Create a [BackgroundSyncApi] instance.
     * Typically one instance per application lifetime.
     */
    fun create(context: Context): BackgroundSyncApi {
        return PolarBackgroundSyncImpl.getInstance(context.applicationContext)
    }

    /**
     * Get the [LocalDataApi] for reading synced data and managing retention.
     */
    fun localDataApi(context: Context): LocalDataApi {
        return PolarBackgroundSyncImpl.getInstance(context.applicationContext)
    }
}
