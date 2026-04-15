// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Online streaming API.
 *
 * Online streaming makes it possible to stream live online data from Polar device.
 *
 * Requires features [FEATURE_POLAR_ONLINE_STREAMING] or [PolarBleApi.PolarBleSdkFeature.FEATURE_HR]
 *
 * Note, online streaming is supported by VeritySense, H10 and OH1 devices
 */
interface PolarOnlineStreamingApi {

    /**
     * Get the data types available in this device for online streaming.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return the set of available online streaming data types in this device
     * @throws Throwable if the status request fails
     */
    suspend fun getAvailableOnlineStreamDataTypes(identifier: String): Set<PolarBleApi.PolarDeviceDataType>

    /**
     * Find out if the HR service is available in the device.
     * Use this API method in a case where the device does not support Polar Measurement Data service.
     * In such a case using [getAvailableOnlineStreamDataTypes] will return error; use this method instead.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_HR]
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return the set with HR service, if available
     * @throws Throwable if the status request fails
     */
    suspend fun getAvailableHRServiceDataTypes(identifier: String): Set<PolarDeviceDataType>

    /**
     * Request the online stream settings available in current operation mode. This request shall be used before the stream is started
     * to decide currently available settings. The available settings depend on the state of the device. For
     * example, if any stream(s) or optical heart rate measurement is already enabled, then
     * the device may limit the offer of possible settings for other stream feature.
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return [PolarSensorSetting] with available settings
     * @throws Throwable if the request fails
     */
    suspend fun requestStreamSettings(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): PolarSensorSetting

    /**
     * Request the settings available in the device. The request returns all capabilities of the
     * requested streaming feature not limited by the current operation mode.
     *
     * Note, This request is supported only by Polar Verity Sense (starting from firmware 1.1.5)
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return [PolarSensorSetting] with full settings
     * @throws Throwable if the request fails
     */
    suspend fun requestFullStreamSettings(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): PolarSensorSetting

    /**
     * Start heart rate stream. Heart rate stream is stopped if
     * the connection is closed, error occurs or flow collection is cancelled.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_HR]
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return [Flow] of [PolarHrData]
     */
    fun startHrStreaming(identifier: String): Flow<PolarHrData>

    /**
     * Stops heart rate stream.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @throws Throwable if stopping the heart rate measurement fails
     */
    suspend fun stopHrStreaming(identifier: String)

    /**
     * Start the ECG (Electrocardiography) stream. ECG stream is stopped if the connection is closed,
     * error occurs or flow collection is cancelled.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return [Flow] of [PolarEcgData]
     */
    fun startEcgStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flow<PolarEcgData>

    /**
     * Start ACC (Accelerometer) stream. ACC stream is stopped if the connection is closed, error
     * occurs or flow collection is cancelled.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return [Flow] of [PolarAccelerometerData]
     */
    fun startAccStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flow<PolarAccelerometerData>

    /**
     * Start optical sensor PPG (Photoplethysmography) stream. PPG stream is stopped if
     * the connection is closed, error occurs or flow collection is cancelled.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return [Flow] of [PolarPpgData]
     */
    fun startPpgStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flow<PolarPpgData>

    /**
     * Start PPI (Pulse to Pulse interval) stream. PPI stream is stopped if
     * the connection is closed, error occurs or flow collection is cancelled.
     * Notice that there is a delay before PPI data stream starts.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return [Flow] of [PolarPpiData]
     */
    fun startPpiStreaming(identifier: String): Flow<PolarPpiData>

    /**
     * Start magnetometer stream. Magnetometer stream is stopped if the connection is closed, error
     * occurs or flow collection is cancelled.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return [Flow] of [PolarMagnetometerData]
     */
    fun startMagnetometerStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flow<PolarMagnetometerData>

    /**
     * Start Gyro stream. Gyro stream is stopped if the connection is closed, error occurs during
     * start or flow collection is cancelled.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return [Flow] of [PolarGyroData]
     */
    fun startGyroStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flow<PolarGyroData>

    /**
     * Start Pressure data stream. Pressure data stream is stopped if the connection is closed, error occurs during
     * start or flow collection is cancelled. Requires feature [FEATURE_POLAR_ONLINE_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [requestStreamSettings].
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return [Flow] of [PolarPressureData]
     */
    fun startPressureStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flow<PolarPressureData>

    /**
     * Start Location data stream. Location stream is stopped if the connection is closed, error occurs during
     * start or flow collection is cancelled. Requires feature [FEATURE_POLAR_ONLINE_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [requestStreamSettings].
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return [Flow] of [PolarLocationData]
     */
    fun startLocationStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flow<PolarLocationData>

    /**
     * Start Temperature data stream. Temperature stream is stopped if the connection is closed, error occurs during
     * start or flow collection is cancelled. Requires feature [FEATURE_POLAR_ONLINE_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [requestStreamSettings].
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return [Flow] of [PolarTemperatureData]
     */
    fun startTemperatureStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flow<PolarTemperatureData>

    /**
     * Start Skin Temperature data stream. SkinTemperature stream is stopped if the connection is closed, error occurs during
     * start or flow collection is cancelled. Requires feature [FEATURE_POLAR_ONLINE_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [requestStreamSettings].
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return [Flow] of [PolarTemperatureData]
     */
    fun startSkinTemperatureStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flow<PolarTemperatureData>

    fun stopStreaming(identifier: String, type: PmdMeasurementType)
}