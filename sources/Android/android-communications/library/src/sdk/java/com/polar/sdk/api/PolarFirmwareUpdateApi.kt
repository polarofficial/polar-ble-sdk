// Copyright © 2024 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.model.CheckFirmwareUpdateStatus
import com.polar.sdk.api.model.FirmwareUpdateStatus
import kotlinx.coroutines.flow.Flow

/**
 * Polar firmware update API.
 *
 * Requires feature [FEATURE_POLAR_FIRMWARE_UPDATE]
 *
 */
interface PolarFirmwareUpdateApi {

    /**
     * Checks if a firmware update is available for the given device identifier. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE]
     *
     * @param identifier Polar device ID or Bluetooth address
     * @return [Flow] emitting [CheckFirmwareUpdateStatus]
     */
    fun checkFirmwareUpdate(identifier: String): Flow<CheckFirmwareUpdateStatus>

    /**
     * Updates firmware to given device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE]
     *
     * @param identifier Polar device ID or BT address
     * @return [Flow] emitting status of firmware update
     */
    fun updateFirmware(identifier: String): Flow<FirmwareUpdateStatus>

    /**
     * Updates firmware to given device from specific URL. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE]
     *
     * @param identifier Polar device ID or BT address
     * @param firmwareUrl URL to firmware file. Firmware file must be compatible with target device
     * @return [Flow] emitting status of firmware update
     */
    fun updateFirmware(identifier: String, firmwareUrl: String): Flow<FirmwareUpdateStatus>
}