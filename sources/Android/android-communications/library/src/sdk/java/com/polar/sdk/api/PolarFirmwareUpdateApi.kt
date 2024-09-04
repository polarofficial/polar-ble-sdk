// Copyright © 2024 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.model.FirmwareUpdateStatus
import io.reactivex.rxjava3.core.Flowable

/**
 * Polar firmware update API.
 *
 * Requires feature [FEATURE_POLAR_FIRMWARE_UPDATE]
 *
 */
interface PolarFirmwareUpdateApi {
    /**
     * Updates firmware to given device.
     *
     * @param identifier Polar device ID or BT address
     * @return [Flowable] emitting status of firmware update
     */
    fun updateFirmware(identifier: String): Flowable<FirmwareUpdateStatus>
}