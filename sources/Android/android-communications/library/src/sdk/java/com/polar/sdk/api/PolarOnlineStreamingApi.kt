// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING
import com.polar.sdk.api.model.*
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

/**
 * Online steaming API.
 *
 * Online streaming makes it possible to stream live online data from Polar device.
 *
 * Requires features [FEATURE_POLAR_ONLINE_STREAMING]
 *
 * Note, online streaming is supported by VeritySense, H10 and OH1 devices
 */
interface PolarOnlineStreamingApi {
    /**
     * Get the data types available in this device for online streaming
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return [Single]
     * Produces:
     * <BR></BR> - onSuccess the set of available online streaming data types in this device
     * <BR></BR> - onError status request failed
     */
    fun getAvailableOnlineStreamDataTypes(identifier: String): Single<Set<PolarBleApi.PolarDeviceDataType>>

    /**
     * Request the online stream settings available in current operation mode. This request shall be used before the stream is started
     * to decide currently available settings. The available settings depend on the state of the device. For
     * example, if any stream(s) or optical heart rate measurement is already enabled, then
     * the device may limit the offer of possible settings for other stream feature.
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return Single stream
     */
    fun requestStreamSettings(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): Single<PolarSensorSetting>

    /**
     * Request the settings available in the device. The request returns the all capabilities of the
     * requested streaming feature not limited by the current operation mode.
     *
     * Note, This request is supported only by Polar Verity Sense (starting from firmware 1.1.5)
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return Single stream
     */
    fun requestFullStreamSettings(
        identifier: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): Single<PolarSensorSetting>

    /**
     * Start heart rate stream. Heart rate stream is stopped if
     * the connection is closed, error occurs or stream is disposed.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @return Flowable stream of heart rate data.
     * Produces:
     * <BR></BR> - onNext [PolarHrData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    fun startHrStreaming(identifier: String): Flowable<PolarHrData>

    /**
     * Start the ECG (Electrocardiography) stream. ECG stream is stopped if the connection is closed,
     * error occurs or stream is disposed.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of [PolarEcgData]
     * Produces:
     * <BR></BR> - onNext [PolarEcgData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless stream is further configured
     */
    fun startEcgStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarEcgData>

    /**
     * Start ACC (Accelerometer) stream. ACC stream is stopped if the connection is closed, error
     * occurs or stream is disposed.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of [PolarAccelerometerData]
     * Produces:
     * <BR></BR> - onNext [PolarAccelerometerData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless stream is further configured
     */
    fun startAccStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarAccelerometerData>

    /**
     * Start optical sensor PPG (Photoplethysmography) stream. PPG stream is stopped if
     * the connection is closed, error occurs or stream is disposed.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of OHR PPG data.
     * Produces:
     * <BR></BR> - onNext [PolarPpgData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    fun startPpgStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarPpgData>

    /**
     * Start PPI (Pulse to Pulse interval) stream. PPI stream is stopped if
     * the connection is closed, error occurs or stream is disposed. Notice that there is a
     * delay before PPI data stream starts.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Flowable stream of OHR PPI data.
     * Produces:
     * <BR></BR> - onNext [PolarPpiData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    fun startPpiStreaming(identifier: String): Flowable<PolarPpiData>

    /**
     * Start magnetometer stream. Magnetometer stream is stopped if the connection is closed, error
     * occurs or stream is disposed.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of magnetometer data.
     * Produces:
     * <BR></BR> - onNext [PolarMagnetometerData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    fun startMagnetometerStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarMagnetometerData>

    /**
     * Start Gyro stream. Gyro stream is stopped if the connection is closed, error occurs during
     * start or stream is disposed.
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of gyroscope data.
     * Produces:
     * <BR></BR> - onNext [PolarGyroData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    fun startGyroStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarGyroData>

    /**
     * Start Pressure data stream. Pressure data stream is stopped if the connection is closed, error occurs during
     * start or stream is disposed. Requires feature [.FEATURE_POLAR_ONLINE_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [.requestStreamSettings]
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of barometer data.
     * Produces:
     * <BR></BR> - onNext [PolarPressureData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    fun startPressureStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarPressureData>

    /**
     * Start Location data stream. Location stream is stopped if the connection is closed, error occurs during
     * start or stream is disposed. Requires feature [.FEATURE_POLAR_ONLINE_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [.requestStreamSettings]
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of location data.
     * Produces:
     * <BR></BR> - onNext [PolarLocationData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    fun startLocationStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarLocationData>

    /**
     * Start Temperature data stream. Temperature stream is stopped if the connection is closed, error occurs during
     * start or stream is disposed. Requires feature [.FEATURE_POLAR_ONLINE_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [.requestStreamSettings]
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of temperature data.
     * Produces:
     * <BR></BR> - onNext [PolarTemperatureData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    fun startTemperatureStreaming(
            identifier: String,
            sensorSetting: PolarSensorSetting
    ): Flowable<PolarTemperatureData>

    /**
     * Start Skin Temperature data stream. SkinTemperature stream is stopped if the connection is closed, error occurs during
     * start or stream is disposed. Requires feature [.FEATURE_POLAR_ONLINE_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [.requestStreamSettings]
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of temperature data.
     * Produces:
     * <BR></BR> - onNext [PolarTemperatureData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    fun startSkinTemperatureStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarTemperatureData>
}