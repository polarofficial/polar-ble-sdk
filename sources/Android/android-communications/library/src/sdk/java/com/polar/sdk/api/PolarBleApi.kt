// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import androidx.annotation.IntRange
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.time.LocalDate
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Polar BLE API.
 *
 * @property features the set of the features API is used for. By giving only the needed features the SDK may reserve only the required resources
 */
abstract class PolarBleApi(val features: Set<PolarBleSdkFeature>) : PolarOnlineStreamingApi,
    PolarOfflineRecordingApi, PolarH10OfflineExerciseApi, PolarSdkModeApi, PolarFirmwareUpdateApi,
    PolarActivityApi, PolarSleepApi, PolarRestServiceApi, PolarTemperatureApi {

    /**
     * Features available in Polar BLE SDK library
     */
    enum class PolarBleSdkFeature {
        /**
         * Hr feature to receive hr and rr data from Polar or any other BLE device via standard HR BLE service
         */
        FEATURE_HR,

        /**
         * Device information feature to receive sw information from Polar or any other BLE device
         */
        FEATURE_DEVICE_INFO,

        /**
         * Feature to receive battery level info from Polar or any other BLE device
         */
        FEATURE_BATTERY_INFO,

        /**
         * Polar sensor streaming feature to stream live online data. For example hr, ecg, acc, ppg, ppi, etc...
         */
        FEATURE_POLAR_ONLINE_STREAMING,

        /**
         * Polar offline recording feature to record offline data to Polar device without continuous BLE connection.
         */
        FEATURE_POLAR_OFFLINE_RECORDING,

        /**
         * H10 exercise recording feature to record exercise data to Polar H10 device without continuous BLE connection.
         */
        FEATURE_POLAR_H10_EXERCISE_RECORDING,

        /**
         * Feature to read and set device time in Polar device
         */
        FEATURE_POLAR_DEVICE_TIME_SETUP,

        /**
         * In SDK mode the wider range of capabilities are available for the online stream or offline recoding
         * than in normal operation mode.
         */
        FEATURE_POLAR_SDK_MODE,

        /**
         * Polar PFTP communication is required for Polar applications.
        */
        FEATURE_POLAR_FILE_TRANSFER,

        /**
         * Health Thermometer client
        */
        FEATURE_HTS,

        FEATURE_POLAR_LED_ANIMATION,

        /**
         * Firmware update for Polar device.
         */
        FEATURE_POLAR_FIRMWARE_UPDATE,

        /**
         * Feature to receive activity data from Polar device.
         */
        FEATURE_POLAR_ACTIVITY_DATA,

        /**
         * Feature to receive sleep data from Polar device.
         */
        FEATURE_POLAR_SLEEP_DATA,

        /**
         * Feature to receive temperature data from Polar device.
         */
        FEATURE_POLAR_TEMPERATURE_DATA
    }

    /**
     * The data types in Polar devices that are available for cleanup.
     */
    enum class PolarStoredDataType(val type: String) {
        ACTIVITY("ACT"),
        AUTO_SAMPLE("AUTOS") ,
        DAILY_SUMMARY("DSUM"),
        NIGHTLY_RECOVERY("NR"),
        SDLOGS("SDLOGS"),
        SLEEP("SLEEP"),
        SLEEP_SCORE("SLEEPSCO"),
        SKIN_CONTACT_CHANGES("SKINCONT"),
        SKIN_TEMP("SKINTEMP")
    }

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
     * The data types available in Polar devices for online streaming or offline recording.
     */
    enum class PolarDeviceDataType {
        HR, ECG, ACC, PPG, PPI, GYRO, MAGNETOMETER, PRESSURE, LOCATION, TEMPERATURE, SKIN_TEMPERATURE
    }

    /**
     * The activity recording data types available in Polar devices.
     */
    enum class PolarActivityDataType {
        SLEEP, STEPS, DISTANCE, CALORIES, HR_SAMPLES, NIGHTLY_RECHARGE, SKIN_TEMPERATURE, PPI_SAMPLES
    }

    /**
     * set mtu to lower than default (232 is the default for polar devices, minimum for H10 is 70 and for OH1 is 140)
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
     * When enabled only Polar devices are found by the [searchForDevice], if set to false
     * any BLE devices with HR services are returned by the [searchForDevice]. The default setting for
     * Polar filter is true.
     *
     * @param enable false disables polar filter
     */
    abstract fun setPolarFilter(enable: Boolean)

    /**
     * Check if the feature is ready.
     *
     * @param deviceId polar device id or bt address
     * @param feature  feature to be requested
     * @return true if feature is ready for use,
     */
    abstract fun isFeatureReady(deviceId: String, feature: PolarBleSdkFeature): Boolean

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
     * When enabled the reconnection is attempted if device connection is lost. By default automatic reconnection is enabled.
     *
     * @param enable true = automatic reconnection is enabled, false = automatic reconnection is disabled
     */
    abstract fun setAutomaticReconnection(enable: Boolean)

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
     * Set the device time. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP]
     *
     * @param identifier polar device id or bt address
     * @param calendar   time to set
     * @return Completable stream
     */
    abstract fun setLocalTime(identifier: String, calendar: Calendar): Completable

    /**
     * Get current time in device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP].
     * Note, the H10 is not supporting time read.
     *
     * @param identifier polar device id or bt address
     * @return Single observable which emits device time in Calendar instance when observable is subscribed
     */
    abstract fun getLocalTime(identifier: String): Single<Calendar>

    /**
     * Start listening the heart rate from Polar devices when subscribed. This observable listens BLE
     * broadcast and parses heart rate from BLE broadcast. The BLE device don't need to be connected when
     * using this function, the heart rate is parsed from the BLE advertisement
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
     * Get file as [ByteArray] from device.
     *
     * @param identifier polar device id or bt address
     * @param path filesystem file path
     * @return Single observable which emits file bytes or error
     */
    abstract fun getFile(identifier: String, path: String): Single<ByteArray>

    /**
     * Get [PolarDiskSpaceData] from device.
     *
     * @param identifier Polar device ID or BT address
     * @return [Single] which emits [PolarDiskSpaceData]
     */
    abstract fun getDiskSpace(identifier: String): Single<PolarDiskSpaceData>

    /**
     * Set [LedConfig] for device (Verity Sense 2.2.1+).
     *
     * @param identifier Polar device ID or BT address
     * @param ledConfig new [LedConfig]
     + @return [Completable] emitting success or error
     */
    abstract fun setLedConfig(identifier: String, ledConfig: LedConfig): Completable

    /**
     * Perform factory reset to given device.
     *
     * @param identifier Polar device ID or BT address
     * @param preservePairingInformation preserve pairing information during factory reset
     * @return [Completable] emitting success or error
     */
    abstract fun doFactoryReset(identifier: String, preservePairingInformation: Boolean): Completable

    /**
     * Perform restart device.
     *
     * @param identifier Polar device ID or BT address
     * @return [Completable] emitting success or error
     */
    abstract fun doRestart(identifier: String): Completable

    /**
     * Get [LogConfig] from device.
     *
     * @param identifier Polar device ID or BT address
    + @return [Single] emitting [LogConfig] or error
     */
    abstract fun getLogConfig(identifier: String): Single<LogConfig>

    /**
     * Set [LogConfig] for device.
     *
     * @param identifier Polar device ID or BT address
     * @param logConfig new [LogConfig]
    + @return [Completable] emitting success or error
     */
    abstract fun setLogConfig(identifier: String, logConfig: LogConfig): Completable

    /**
     * Set warehouse sleep setting to a given device. Warehouse sleep does factory reset to the device
     * and makes it sleep.
     *
     * @param identifier Polar device ID or BT address
     * @return [Completable] emitting success or error
     */
    abstract fun setWareHouseSleep(identifier: String): Completable

    /**
     * Configure the Polar device with first-time use settings and user identifier.
     *
     * @param identifier Polar device ID or Bluetooth address.
     * @param ftuConfig Configuration data for the first-time use, encapsulated in [PolarFirstTimeUseConfig].
     * @return [Completable] emitting success or error.
     *
     * [PolarFirstTimeUseConfig] class requires valid values for each parameter within specific ranges:
     * - Gender: "Male" or "Female"
     * - Height: 90 to 240 cm
     * - Weight: 15 to 300 kg
     * - Max heart rate: 100 to 240 bpm
     * - Resting heart rate: 20 to 120 bpm
     * - VO2 max: 10 to 95
     * - Training background: One of the predefined levels (10, 20, 30, 40, 50, 60)
     * - Sleep goal: In minutes between 300 to 660
     * - Typical day: "MOSTLY_SITTING", "MOSTLY_STANDING", or "MOSTLY_MOVING"
     */
    abstract fun doFirstTimeUse(identifier: String, ftuConfig: PolarFirstTimeUseConfig): Completable

    /**
     * Check if the First Time Use has been done for the given device.
     *
     * @param identifier Polar device ID or Bluetooth address.
     * @return [Single] emitting success with "true" or "false" response, or error.
     *
     */
    abstract fun isFtuDone(identifier: String): Single<Boolean>

    /**
     * Set [PolarUserDeviceSettings] for device. Currently only 'user device location' is supported.
     *
     * @param identifier Polar device ID or BT address.
     * @param deviceUserSettings Currently only device location for the user is supported.
     * @return [Completable] emitting success or error.
     */
    abstract fun setUserDeviceSettings(identifier: String, deviceUserSetting: PolarUserDeviceSettings): Completable

    /**
     * Get [PolarUserDeviceSettings] from device.
     *
     * @param identifier Polar device ID or BT address
    + @return [Single] emitting [PolarUserDeviceSettings] or error
     */
    abstract fun getUserDeviceSettings(identifier: String): Single<PolarUserDeviceSettings>

    /**
     * Delete data [PolarStoredDataType] from a device. Note that you will need to await for completion.
     *
     * @param identifier, Polar device ID or BT address
     * @param dataType, [PolarStoredDataType] A specific data type that shall be deleted
     * @param until, Data will be deleted from device from history until this date.
     * @return [Flowable] success with the paths of the deleted data or error
     */
    abstract fun deleteStoredDeviceData(identifier: String, dataType: PolarStoredDataType, until: LocalDate?): Flowable<ConcurrentLinkedQueue<String>>
}