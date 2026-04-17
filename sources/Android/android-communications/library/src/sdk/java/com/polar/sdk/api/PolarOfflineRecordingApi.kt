// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
import com.polar.sdk.api.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Offline recording API.
 *
 * Offline recording makes it possible to record [PolarBleApi.PolarDeviceDataType] data to device memory.
 * With Offline recording the Polar device and phone don't need to be connected all the time, as offline recording
 * continues in Polar device even the BLE disconnects.
 *
 * Offline records saved into the device can be encrypted. The [PolarRecordingSecret] is provided for
 * [startOfflineRecording] and [setOfflineRecordingTrigger] when encryption is wanted. The [PolarRecordingSecret] with same key must be provided
 * in [getOfflineRecord] to correctly decrypt the data in the device.
 *
 * Requires features [FEATURE_POLAR_OFFLINE_RECORDING]
 *
 * Note, offline recording is supported in Polar Verity Sense device (starting from firmware version 2.1.0)
 */
interface PolarOfflineRecordingApi {

    /**
     * Get the data types available in this device for offline recording. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return the set of available offline recording data types in this device
     */
    suspend fun getAvailableOfflineRecordingDataTypes(identifier: String): Set<PolarBleApi.PolarDeviceDataType>

    /**
     * Request the offline recording settings available in current operation mode. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING] This request shall be used before the offline recording is started
     * to decide currently available settings. The available settings depend on the state of the device.
     *
     * @param identifier polar device id or bt address
     * @param feature the stream feature of interest
     * @return PolarSensorSetting with the settings available in current device state for the requested stream feature.
     * The available settings depend on the state of the device and may be empty if no settings are currently available for the requested stream feature.
     */
    suspend fun requestOfflineRecordingSettings(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): PolarSensorSetting

    /**
     * Request all the settings available in the device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING] The request returns the all capabilities of the
     * requested streaming feature not limited by the current operation mode.
     *
     * @param identifier polar device id or bt address
     * @param feature the stream feature of interest
     * @return PolarSensorSetting with the settings available in current device state for the requested stream feature.
     * The available settings depend on the state of the device and may be empty if no settings are currently available for the requested stream feature.
     */
    suspend fun requestFullOfflineRecordingSettings(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): PolarSensorSetting

    /**
     * Get current offline recording status. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     *
     * @param identifier polar device id or bt address
     * @return the list of currently recording offline recordings, if the list is empty no offline recordings currently recorded
     */
    suspend fun getOfflineRecordingStatus(identifier: String): List<PolarBleApi.PolarDeviceDataType>

    /**
     * List offline recordings stored in the device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return [Flow] stream of found offline recording entries in [PolarOfflineRecordingEntry]
     */
    fun listOfflineRecordings(identifier: String): Flow<PolarOfflineRecordingEntry>

    /**
     * Fetch recording from the Polar device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     *
     * Note, the fetching of the recording may take several seconds if the recording is big.
     * Note, if a faulty data block is encountered while parsing offline data from device that particular data block will be discarded. This will lead to gaps in the data.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry The offline recording to be fetched
     * @param secret If the secret is provided in [startOfflineRecording] or [setOfflineRecordingTrigger]
     * then the same secret must be provided when fetching the offline record
     * @return the offline recording data
     */
    suspend fun getOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret? = null
    ): PolarOfflineRecordingData

    /**
     * Fetch recording from the Polar device with progress tracking. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     *
     * Note, the fetching of the recording may take several seconds if the recording is big.
     * Note, if a faulty data block is encountered while parsing offline data from device that particular data block will be discarded. This will lead to gaps in the data.
     * This method provides progress updates during the download.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry The offline recording to be fetched
     * @param secret If the secret is provided in [startOfflineRecording] or [setOfflineRecordingTrigger]
     * then the same secret must be provided when fetching the offline record
     * @return [Flow] stream of [PolarOfflineRecordingResult]
     */
    fun getOfflineRecordWithProgress(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret? = null
    ): Flow<PolarOfflineRecordingResult>

    /**
     * Lists all parts of all offline recordings (split and non-split) stored in the device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     */
    @Deprecated("Use listOfflineRecordings instead")
    fun listSplitOfflineRecordings(identifier: String): Flow<PolarOfflineRecordingEntry>

    /**
     * Fetch split recording from the Polar device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     *
     * Note, the fetching of the recording may take several seconds if the recording is big.
     * Note, if a faulty data block is encountered while parsing offline data from device that particular data block will be discarded. This will lead to gaps in the data.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry The offline recording to be fetched
     * @param secret If the secret is provided in [startOfflineRecording] or [setOfflineRecordingTrigger]
     * then the same secret must be provided when fetching the offline record
     *
     * @return Success or error
     */
    @Deprecated("Use getOfflineRecordWithProgress method instead")
    suspend fun getSplitOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret? = null
    ): PolarOfflineRecordingData

    /**
     * Removes offline recording from the device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     */
    suspend fun removeOfflineRecord(identifier: String, entry: PolarOfflineRecordingEntry)

    /**
     * Start offline recording. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param feature the feature to be started
     * @param settings settings for the started offline recording. In case of
     * the feature [PolarBleApi.PolarDeviceDataType.PPI] or [PolarBleApi.PolarDeviceDataType.HR] the [PolarSensorSetting] is not needed
     * @param secret if the secret is provided the offline recordings are encrypted in device
     * @return Success or error.
     */
    suspend fun startOfflineRecording(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType,
        settings: PolarSensorSetting? = null,
        secret: PolarRecordingSecret? = null
    )

    /**
     * Request to stop offline recording. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     * @param identifier polar device id
     * @param feature which is stopped
     * @return Success or error.
     */
    suspend fun stopOfflineRecording(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    )

    /**
     * Sets the offline recording triggers for a given Polar device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING] The offline recording can be started automatically in the device by setting the triggers.
     * The changes to the trigger settings will take effect on the next device startup.
     *
     * Automatically started offline recording can be stopped by [stopOfflineRecording]. Also if user switches off the device power,
     * the offline recording is stopped but starts again once power is switched on and the trigger event happens.
     *
     * Trigger functionality can be disabled by setting [PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED], the already running offline
     * recording is not stopped by disable.
     *
     * @param identifier  Polar device id found printed on the sensor/device or bt address
     * @param trigger the type of trigger
     * @param secret if the secret is provided the offline recordings are encrypted in device
     * @return Success or error.
     */
    suspend fun setOfflineRecordingTrigger(
        identifier: String,
        trigger: PolarOfflineRecordingTrigger,
        secret: PolarRecordingSecret? = null
    )

    /**
     * Retrieves the current offline recording trigger setup in the device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING]
     * @param identifier  Polar device id found printed on the sensor/device or bt address
     * @return PolarOfflineRecordingTrigger, the current offline recording trigger setup in the device
     */
    suspend fun getOfflineRecordingTriggerSetup(identifier: String): PolarOfflineRecordingTrigger
}