// Copyright © 2024 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.model.FirmwareUpdateStatus
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import com.polar.sdk.api.model.PolarFirmwareVersionInfo

/**
 * Polar firmware update API.
 *
 * Requires feature [FEATURE_POLAR_FIRMWARE_UPDATE]
 *
 */
interface PolarFirmwareUpdateApi {
    /**
     * Updates firmware to given device from local file.
     *
     * @param identifier Polar device ID or BT address
     * @param filePath path to the file
     * @param version firmware version
     * @return [Flowable] emitting status of firmware update
     */
    fun updateFirmwareLocal(identifier: String, filePath: String, version: String): Flowable<FirmwareUpdateStatus>

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

    /**
     * Get firmware info for given device.
     *
     * @param identifier Polar device ID or BT address
     * @return [Single] emitting firmware info
     */
    fun getFirmwareInfo(identifier: String): Single<PolarFirmwareVersionInfo>
}