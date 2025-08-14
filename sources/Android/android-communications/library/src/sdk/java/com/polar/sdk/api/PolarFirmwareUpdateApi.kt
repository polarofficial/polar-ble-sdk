// Copyright © 2024 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.model.CheckFirmwareUpdateStatus
import com.polar.sdk.api.model.FirmwareUpdateStatus
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable

/**
 * Polar firmware update API.
 *
 * Requires feature [FEATURE_POLAR_FIRMWARE_UPDATE]
 *
 */
interface PolarFirmwareUpdateApi {

    /**
     * Checks if a firmware update is available for the given device identifier.
     *
     * @param identifier Polar device ID or Bluetooth address
     * @return [Observable] emitting [CheckFirmwareUpdateStatus]
     */
    fun checkFirmwareUpdate(identifier: String): Observable<CheckFirmwareUpdateStatus>

    /**
     * Updates firmware to given device.
     *
     * @param identifier Polar device ID or BT address
     * @return [Flowable] emitting status of firmware update
     */
    fun updateFirmware(identifier: String): Flowable<FirmwareUpdateStatus>

    /**
     * Updates firmware to given device from specific URL.
     *
     * @param identifier Polar device ID or BT address
     * @param firmwareUrl URL to firmware file. Firmware file must be compatible with target device
     * @return [Flowable] emitting status of firmware update
     */
    fun updateFirmware(identifier: String, firmwareUrl: String): Flowable<FirmwareUpdateStatus>
}