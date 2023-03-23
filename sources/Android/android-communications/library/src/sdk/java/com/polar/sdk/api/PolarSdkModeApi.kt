// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

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
     * Enables SDK mode.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Completable stream produces:
     * success if SDK mode is enabled or device is already in SDK mode
     * error if SDK mode enable failed
     */
    fun enableSDKMode(identifier: String): Completable

    /**
     * Disables SDK mode.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Completable stream produces:
     * success if SDK mode is disabled or SDK mode was already disabled
     * error if SDK mode disable failed
     */
    fun disableSDKMode(identifier: String): Completable

    /**
     * Check if SDK mode currently enabled.
     *
     * Note, SDK status check is supported by VeritySense starting from firmware 2.1.0
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return [Single]
     * Produces:
     * <BR></BR> - onSuccess true, if the SDK mode is currently enabled
     * <BR></BR> - onError status request failed
     */
    fun isSDKModeEnabled(identifier: String): Single<Boolean>
}