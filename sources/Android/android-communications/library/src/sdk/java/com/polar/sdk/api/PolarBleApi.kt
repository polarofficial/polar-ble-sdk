// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import androidx.annotation.IntRange
import androidx.annotation.Size
import androidx.core.util.Pair
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Polar BLE API.
 *
 * @param features bitmask of feature(s) or [.ALL_FEATURES]
 */
abstract class PolarBleApi(val features: Int) {
    /**
     * Logger interface for logging events from SDK. Shall be used only for tracing and debugging purposes.
     */
    fun interface PolarBleApiLogger {
        /**
         * message from sdk logging
         *
         * @param str formatted string message
         */
        fun message(str: String)
    }

    /**
     * Device stream features in Polar devices. The device streaming features requires the
     * [.FEATURE_POLAR_SENSOR_STREAMING]
     *
     * @see PolarBleApiCallback.streamingFeaturesReady
     */
    enum class DeviceStreamingFeature {
        ECG, ACC, PPG, PPI, GYRO, MAGNETOMETER
    }

    /**
     * Recoding intervals for H10 recording start
     */
    enum class RecordingInterval(val value: Int) {
        INTERVAL_1S(1), /*!< 1 second interval */
        INTERVAL_5S(5); /*!< 5 second interval */
    }

    /**
     * Sample types for H10 recording start
     */
    enum class SampleType {
        HR, /*!< HeartRate in BPM */
        RR, /*!< RR interval in milliseconds */
    }

    /**
     * set mtu to lower than default(232 is the default for polar devices, minimum for H10 is 70 and for OH1 is 140)
     * to minimize latency
     *
     * @param mtu value between 70-512 to be set
     */
    abstract fun setMtu(@IntRange(from = 70, to = 512) mtu: Int)

    /**
     * Releases the SDK resources. When the SDK is used on scope of
     * the android component (e.g. Activity or Service) then the shutDown may be called
     * on component destroy function. After shutDown the new instance of the SDK is needed:
     *
     * @see PolarBleApiDefaultImpl.defaultImplementation
     */
    abstract fun shutDown()

    /**
     * removes all known devices which are not in use currently
     */
    abstract fun cleanup()

    /**
     * When enabled only Polar devices are found by the [.searchForDevice], if set to false
     * any BLE devices with HR services are returned by the [.searchForDevice]. The default setting for
     * Polar filter is true.
     *
     * @param enable false disables polar filter
     */
    abstract fun setPolarFilter(enable: Boolean)

    /**
     * Check if the feature is ready. Only the check for the [.FEATURE_POLAR_SENSOR_STREAMING]
     * and [.FEATURE_POLAR_FILE_TRANSFER] is supported by this api function
     *
     * @param deviceId polar device id or bt address
     * @param feature  feature to be requested
     * @return true if feature is ready for use,
     */
    abstract fun isFeatureReady(deviceId: String, feature: Int): Boolean

    /**
     * enables scan filter while on background
     *
     */
    @Deprecated("in release 3.2.8. Move to the background is not relevant information for SDK starting from release 3.2.8")
    abstract fun backgroundEntered()

    /**
     * Optionally call when application enters to the foreground. By calling foregroundEntered() you make
     * sure BLE scan is restarted. BLE scan start is not working when Android device display is off
     * (related to Android power save). By calling foregroundEntered() helps in some rare situations
     * e.g. if connection is lost to the device and [.setAutomaticReconnection] is enabled,
     * reconnection is created when application is back in foreground.
     */
    abstract fun foregroundEntered()

    /**
     * Sets the API callback
     *
     * @param callback instance of [PolarBleApiCallbackProvider]
     */
    abstract fun setApiCallback(callback: PolarBleApiCallbackProvider)

    /**
     * Sets the API logger
     *
     * @param logger instance of [PolarBleApiLogger]
     */
    abstract fun setApiLogger(logger: PolarBleApiLogger)

    /**
     * When enabled the reconnection is attempted if device connection is lost. By default automatic reconnection is enabled.
     *
     * @param enable true = automatic reconnection is enabled, false = automatic reconnection is disabled
     */
    abstract fun setAutomaticReconnection(enable: Boolean)

    /**
     * Set time to device affects on sensor data stream(s) timestamps
     * requires feature [.FEATURE_POLAR_FILE_TRANSFER]
     *
     * @param identifier polar device id or bt address
     * @param calendar   time to set
     * @return Completable stream
     */
    abstract fun setLocalTime(identifier: String, calendar: Calendar): Completable

    /**
     * Get current time in device. To use this function feature [.FEATURE_POLAR_FILE_TRANSFER] is required
     *
     * @param identifier polar device id or bt address
     * @return [Single]
     * Produces:
     * <BR></BR> - onSuccess the current local time in device
     * <BR></BR> - onError status request failed
     */
    abstract fun getLocalTime(identifier: String): Single<Calendar>

    /**
     * Request the stream settings available in current operation mode. This request shall be used before the stream is started
     * to decide currently available. The available settings depend on the state of the device. For
     * example, if any stream(s) or optical heart rate measurement is already enabled, then
     * the device may limit the offer of possible settings for other stream feature. Requires feature
     * [.FEATURE_POLAR_SENSOR_STREAMING]
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return Single stream
     */
    abstract fun requestStreamSettings(
        identifier: String,
        feature: DeviceStreamingFeature
    ): Single<PolarSensorSetting>

    /**
     * Request full steam settings capabilities. The request returns the all capabilities of the
     * requested streaming feature not limited by the current operation mode. Requires feature
     * [.FEATURE_POLAR_SENSOR_STREAMING]. This request is supported only by Polar Verity Sense (starting from firmware 1.1.5)
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return Single stream
     */
    abstract fun requestFullStreamSettings(
        identifier: String,
        feature: DeviceStreamingFeature
    ): Single<PolarSensorSetting>

    /**
     * Start connecting to a nearby Polar device. [PolarBleApiCallback.deviceConnected] callback is
     * invoked when connection to a nearby device is established.
     *
     * @param rssiLimit       RSSI (Received Signal Strength Indication) value is typically from -40 to -60 (dBm), depends on the used Bluetooth chipset and/or antenna tuning
     * @param service         in hex string format like "180D" PolarInvalidArgument invoked if not in correct format
     * @param timeout         min time to search nearby device default = 2s
     * @param unit            time unit to be used
     * @param polarDeviceType like H10, OH1 etc... or null for any polar device
     * @return rx Completable, complete invoked when nearby device found, and connection attempt started.
     * deviceConnecting callback invoked to inform connection attempt
     */
    abstract fun autoConnectToDevice(rssiLimit: Int, service: String?, timeout: Int, unit: TimeUnit, polarDeviceType: String?): Completable
    abstract fun autoConnectToDevice(rssiLimit: Int, service: String?, polarDeviceType: String?): Completable

    /**
     * Request a connection to a BLE device. Invokes [PolarBleApiCallback.deviceConnected] callback.
     *
     * @param identifier Polar device id found printed on the sensor/device (in format "12345678")
     * or bt address (in format "00:11:22:33:44:55")
     * @throws PolarInvalidArgument if identifier is invalid formatted mac address or polar device id
     */
    @Throws(PolarInvalidArgument::class)
    abstract fun connectToDevice(identifier: String)

    /**
     * Request disconnecting from a BLE device. Invokes [PolarBleApiCallback.deviceDisconnected] callback.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address (in format "00:11:22:33:44:55")
     * @throws PolarInvalidArgument if identifier is invalid formatted mac address or polar device id
     */
    @Throws(PolarInvalidArgument::class)
    abstract fun disconnectFromDevice(identifier: String)

    /**
     * Request start recording. Supported only by Polar H10. Requires feature
     * [.FEATURE_POLAR_FILE_TRANSFER]
     *
     * @param identifier polar device id or bt address
     * @param exerciseId unique id for exercise entry
     * @param interval   recording interval to be used, parameter has no effect if the `type` parameter is SampleType.RR
     * @param type       sample type to be used
     * @return Completable stream
     */
    abstract fun startRecording(
        identifier: String,
        @Size(min = 1, max = 64) exerciseId: String,
        interval: RecordingInterval?,
        type: SampleType
    ): Completable

    /**
     * Request to stop recording. Supported only by Polar H10. Requires feature
     * [.FEATURE_POLAR_FILE_TRANSFER]
     *
     * @param identifier polar device id or bt address
     * @return Completable stream
     */
    abstract fun stopRecording(identifier: String): Completable

    /**
     * Request current recording status. Supported only by Polar H10. Requires feature
     * [.FEATURE_POLAR_FILE_TRANSFER]
     *
     * @param identifier polar device id or bt address
     * @return Single stream Pair first recording status, second entryId if available
     */
    abstract fun requestRecordingStatus(identifier: String): Single<Pair<Boolean, String>>

    /**
     * List exercises stored in the device Polar H10 device. Requires feature
     * [.FEATURE_POLAR_FILE_TRANSFER]. This API is working for Polar OH1 and
     * Polar Verity Sense devices too, however in those devices recording of exercise requires
     * that sensor is registered to Polar Flow account.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Flowable stream of [PolarExerciseEntry] entries
     */
    abstract fun listExercises(identifier: String): Flowable<PolarExerciseEntry>

    /**
     * Api for fetching a single exercise from Polar H10 device. Requires feature
     * [.FEATURE_POLAR_FILE_TRANSFER]. This API is working for Polar OH1 and
     * Polar Verity Sense devices too, however in those devices recording of exercise requires
     * that sensor is registered to Polar Flow account.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry      [PolarExerciseEntry] object
     * @return Single stream of [PolarExerciseData]
     */
    abstract fun fetchExercise(identifier: String, entry: PolarExerciseEntry): Single<PolarExerciseData>

    /**
     * Api for removing single exercise from Polar H10 device. Requires feature
     * [.FEATURE_POLAR_FILE_TRANSFER]. This API is working for Polar OH1 and
     * Polar Verity Sense devices too, however in those devices recording of exercise requires
     * that sensor is registered to Polar Flow account.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry      entry to be removed
     * @return Completable stream
     */
    abstract fun removeExercise(identifier: String, entry: PolarExerciseEntry): Completable

    /**
     * Starts searching for BLE devices when subscribed. Search continues as long as observable is
     * subscribed or error. Each found device is emitted only once. By default searches only for Polar devices,
     * but can be controlled by [.setPolarFilter]. If [.setPolarFilter] is false
     * then searches for any BLE heart rate capable devices
     *
     * @return Flowable stream of [PolarDeviceInfo]
     * Produces:
     * <BR></BR> - onNext for any new Polar (or BLE) device detected
     * <BR></BR> - onError if scan start fails
     * <BR></BR> - onComplete non produced unless stream is further configured
     */
    abstract fun searchForDevice(): Flowable<PolarDeviceInfo>

    /**
     * Start listening the heart rate from Polar devices when subscribed. This observable listens BLE
     * broadcast and parses heart rate from BLE broadcast. The BLE device is not connected when
     * using this function.
     *
     * @param deviceIds set of Polar device ids to filter or null for a any Polar device
     * @return Flowable stream of [PolarHrBroadcastData]
     * Produces:
     * <BR></BR> - onNext when new advertisement is detected based on deviceId list as filter
     * <BR></BR> - onError if scan start fails
     * <BR></BR> - onComplete non produced unless stream is further configured
     */
    abstract fun startListenForPolarHrBroadcasts(deviceIds: Set<String>?): Flowable<PolarHrBroadcastData>

    /**
     * Start the ECG (Electrocardiography) stream. ECG stream is stopped if the connection is closed,
     * error occurs or stream is disposed. Requires feature [.FEATURE_POLAR_SENSOR_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [.requestStreamSettings]
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of [PolarEcgData]
     * Produces:
     * <BR></BR> - onNext [PolarEcgData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless stream is further configured
     */
    abstract fun startEcgStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarEcgData>

    /**
     * Start ACC (Accelerometer) stream. ACC stream is stopped if the connection is closed, error
     * occurs or stream is disposed. Requires feature [.FEATURE_POLAR_SENSOR_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [.requestStreamSettings]
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of [PolarAccelerometerData]
     * Produces:
     * <BR></BR> - onNext [PolarAccelerometerData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless stream is further configured
     */
    abstract fun startAccStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarAccelerometerData>

    /**
     * Start OHR (Optical heart rate) PPG (Photoplethysmography) stream. PPG stream is stopped if
     * the connection is closed, error occurs or stream is disposed. Requires feature
     * [.FEATURE_POLAR_SENSOR_STREAMING]. Before starting the stream it is recommended to
     * query the available settings using [.requestStreamSettings]
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of OHR PPG data.
     * Produces:
     * <BR></BR> - onNext [PolarOhrData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    abstract fun startOhrStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarOhrData>

    /**
     * Start OHR (Optical heart rate) PPI (Pulse to Pulse interval) stream. PPI stream is stopped if
     * the connection is closed, error occurs or stream is disposed. Notice that there is a
     * delay before PPI data stream starts. Requires feature [.FEATURE_POLAR_SENSOR_STREAMING].
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Flowable stream of OHR PPI data.
     * Produces:
     * <BR></BR> - onNext [PolarOhrPPIData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    abstract fun startOhrPPIStreaming(identifier: String): Flowable<PolarOhrPPIData>

    /**
     * Start magnetometer stream. Magnetometer stream is stopped if the connection is closed, error
     * occurs or stream is disposed. Requires feature [.FEATURE_POLAR_SENSOR_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [.requestStreamSettings]
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of magnetometer data.
     * Produces:
     * <BR></BR> - onNext [PolarMagnetometerData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    abstract fun startMagnetometerStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarMagnetometerData>

    /**
     * Start Gyro stream. Gyro stream is stopped if the connection is closed, error occurs during
     * start or stream is disposed. Requires feature [.FEATURE_POLAR_SENSOR_STREAMING].
     * Before starting the stream it is recommended to query the available settings using [.requestStreamSettings]
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of gyroscope data.
     * Produces:
     * <BR></BR> - onNext [PolarGyroData]
     * <BR></BR> - onError error for possible errors invoked
     * <BR></BR> - onComplete non produced unless the stream is further configured
     */
    abstract fun startGyroStreaming(
        identifier: String,
        sensorSetting: PolarSensorSetting
    ): Flowable<PolarGyroData>

    /**
     * Enables SDK mode. In SDK mode the wider range of capabilities is available for the stream
     * than in normal operation mode. SDK mode is only supported by Polar Verity Sense (starting from firmware 1.1.5).
     * Requires feature [.FEATURE_POLAR_SENSOR_STREAMING].
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Completable stream produces:
     * success if SDK mode is enabled or device is already in SDK mode
     * error if SDK mode enable failed
     */
    abstract fun enableSDKMode(identifier: String): Completable

    /**
     * Disables SDK mode. SDK mode is only supported by Polar Verity Sense (starting from firmware 1.1.5).
     * Requires feature [.FEATURE_POLAR_SENSOR_STREAMING].
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Completable stream produces:
     * success if SDK mode is disabled or SDK mode was already disabled
     * error if SDK mode disable failed
     */
    abstract fun disableSDKMode(identifier: String): Completable

    companion object {
        /**
         * hr feature to receive hr and rr data.
         */
        const val FEATURE_HR = 1

        /**
         * dis feature to receive sw information.
         */
        const val FEATURE_DEVICE_INFO = 2

        /**
         * bas feature to receive battery level info.
         */
        const val FEATURE_BATTERY_INFO = 4

        /**
         * polar sensor streaming feature for ecg, acc, ppg, ppi, etc...
         */
        const val FEATURE_POLAR_SENSOR_STREAMING = 8

        /**
         * polar file transfer feature to read exercises from device
         */
        const val FEATURE_POLAR_FILE_TRANSFER = 16

        /**
         * all features mask
         */
        const val ALL_FEATURES = 0xff
    }
}