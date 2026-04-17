// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE

/**
 * Polar SDK mode API
 *
 * In SDK mode the wider range of capabilities is available for the online streaming or
 * for the offline recording than in normal operation mode. The available capabilities can be
 * asked from device using [PolarOnlineStreamingApi.requestFullStreamSettings] or [PolarOfflineRecordingApi.requestFullOfflineRecordingSettings]
 *
 * Requires features [FEATURE_POLAR_SDK_MODE]
 *
 * Note, SDK mode supported by VeritySense starting from firmware 1.1.5
 */
interface PolarSdkModeApi {

    /**
     * Enables SDK mode. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE]
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @throws Throwable if SDK mode enable fails
     */
    suspend fun enableSDKMode(identifier: String)

    /**
     * Disables SDK mode. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE]
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @throws Throwable if SDK mode disable fails
     */
    suspend fun disableSDKMode(identifier: String)

    /**
     * Check if SDK mode currently enabled. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE]
     *
     * Note, SDK status check is supported by VeritySense starting from firmware 2.1.0
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return true if SDK mode is currently enabled
     * @throws Throwable if status request fails
     */
    suspend fun isSDKModeEnabled(identifier: String): Boolean
}