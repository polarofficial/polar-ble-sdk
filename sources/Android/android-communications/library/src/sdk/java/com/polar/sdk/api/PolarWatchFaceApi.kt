// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.model.PolarWatchFaceConfig

/**
 * Polar watch face configuration API.
 *
 * Allows reading and writing complications configuration on PolarOS-based Polar devices.
 * Complications are configurable UI elements on a watch face (e.g. SpO2, heart rate, steps).
 *
 * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_WATCH_FACES_CONFIGURATION].
 */
interface PolarWatchFaceApi {

    /**
     * Read the current watch face configuration from the device KVS.
     *
     * @param identifier Polar device ID or BT address.
     * @return [PolarWatchFaceConfig] reflecting what is currently on the device,
     *         or a config with an empty list if no complications are configured.
     * @throws Exception on connection / transfer error.
     */
    suspend fun getWatchFaceConfig(identifier: String): PolarWatchFaceConfig

    /**
     * Set the watch face complications on the device.
     *
     * Writes the provided complication list to the device KVS watch face configuration
     *
     * @param identifier Polar device ID or BT address.
     * @param config     Watch face configuration containing the ordered list of complications to enable.
     * @throws Exception if the operation fails (device not connected, not supported, transfer error, etc.)
     */
    suspend fun setWatchFaceConfig(identifier: String, config: PolarWatchFaceConfig)
}
