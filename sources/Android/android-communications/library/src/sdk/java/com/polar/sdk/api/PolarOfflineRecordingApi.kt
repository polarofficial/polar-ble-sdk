// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
import com.polar.sdk.api.model.*
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

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
     * Get the data types available in this device for offline recording
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @return [Single]
     * Produces:
     * <BR></BR> - onSuccess the set of available offline recording data types in this device
     * <BR></BR> - onError status request failed
     */
    fun getAvailableOfflineRecordingDataTypes(identifier: String): Single<Set<PolarBleApi.PolarDeviceDataType>>

    /**
     * Request the offline recording settings available in current operation mode. This request shall be used before the offline recording is started
     * to decide currently available settings. The available settings depend on the state of the device.
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return Single stream
     */
    fun requestOfflineRecordingSettings(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): Single<PolarSensorSetting>

    /**
     * Request all the settings available in the device. The request returns the all capabilities of the
     * requested streaming feature not limited by the current operation mode.
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return Single stream
     */
    fun requestFullOfflineRecordingSettings(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): Single<PolarSensorSetting>

    /**
     * Get current offline recording status.
     *
     * @param identifier polar device id or bt address
     * @return [Single]
     * Produces:
     * <BR></BR> - onSuccess the list of currently recording offline recordings, if the list is empty no offline recordings currently recorded
     * <BR></BR> - onError status request failed
     */
    fun getOfflineRecordingStatus(
        identifier: String
    ): Single<List<PolarBleApi.PolarDeviceDataType>>

    /**
     * List offline recordings stored in the device.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return [Flowable] stream
     * <BR></BR> - onNext the found offline recording entry in [PolarOfflineRecordingEntry]
     * <BR></BR> - onComplete the listing completed
     * <BR></BR> - onError listing request failed
     */
    fun listOfflineRecordings(identifier: String): Flowable<PolarOfflineRecordingEntry>

    /**
     * Fetch recording from the Polar device.
     *
     * Note, the fetching of the recording may take several seconds if the recording is big.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry The offline recording to be fetched
     * @param secret If the secret is provided in [startOfflineRecording] or [setOfflineRecordingTrigger]
     * then the same secret must be provided when fetching the offline record
     *
     * @return [Single]
     * Produces:
     * <BR></BR> - onSuccess the offline recording data
     * <BR></BR> - onError fetch recording request failed
     */
    fun getOfflineRecord(identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret? = null): Single<PolarOfflineRecordingData>

    /**
     * List split offline recordings stored in the device.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return [Flowable] stream
     * <BR></BR> - onNext the found offline recording entry in [PolarOfflineRecordingEntry]
     * <BR></BR> - onComplete the listing completed
     * <BR></BR> - onError listing request failed
     */
    fun listSplitOfflineRecordings(identifier: String): Flowable<PolarOfflineRecordingEntry>

    /**
     * Fetch split recording from the Polar device.
     *
     * Note, the fetching of the recording may take several seconds if the recording is big.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry The offline recording to be fetched
     * @param secret If the secret is provided in [startOfflineRecording] or [setOfflineRecordingTrigger]
     * then the same secret must be provided when fetching the offline record
     *
     * @return [Single]
     * Produces:
     * <BR></BR> - onSuccess the offline recording data
     * <BR></BR> - onError fetch recording request failed
     */
    fun getSplitOfflineRecord(identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret? = null): Single<PolarOfflineRecordingData>

    /**
     * Removes offline recording from the device
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry      entry to be removed
     * @return [Completable]
     * Produces:
     * <BR></BR> - onComplete offline record is removed
     * <BR></BR> - onError  offline record removal failed
     */
    fun removeOfflineRecord(identifier: String, entry: PolarOfflineRecordingEntry): Completable

    /**
     * Start offline recording.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param feature the feature to be started
     * @param settings settings for the started offline recording. In case of
     * the feature [PolarBleApi.PolarDeviceDataType.PPI] or [PolarBleApi.PolarDeviceDataType.HR] the [PolarSensorSetting] is not needed
     * @param secret if the secret is provided the offline recordings are encrypted in device
     * @return [Completable]
     * Produces:
     * <BR></BR> - onComplete offline recording is started successfully
     * <BR></BR> - onError  offline recording start failed.
     */
    fun startOfflineRecording(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType,
        settings: PolarSensorSetting? = null,
        secret: PolarRecordingSecret? = null
    ): Completable

    /**
     * Request to stop offline recording.
     *
     * @param identifier polar device id
     * @param feature which is stopped
     * @return [Completable]
     * Produces:
     * <BR></BR> - onComplete offline recording is stop successfully
     * <BR></BR> - onError  offline recording stop failed.
     */
    fun stopOfflineRecording(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): Completable

    /**
     * Sets the offline recording triggers for a given Polar device. The offline recording can be started automatically in the device by setting the triggers.
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
     *
     * Produces:
     * <BR></BR> - onComplete offline recording trigger set successfully
     * <BR></BR> - onError  offline recording trigger set failed.
     */
    fun setOfflineRecordingTrigger(
        identifier: String,
        trigger: PolarOfflineRecordingTrigger,
        secret: PolarRecordingSecret? = null
    ): Completable

    /**
     * Retrieves the current offline recording trigger setup in the device.
     *
     * @param identifier  Polar device id found printed on the sensor/device or bt address
     *
     * Produces:
     * <BR></BR> - onSuccess the offline recording trigger setup in the device
     * <BR></BR> - onError fetch recording request failed
     */
    fun getOfflineRecordingTriggerSetup(identifier: String): Single<PolarOfflineRecordingTrigger>
}