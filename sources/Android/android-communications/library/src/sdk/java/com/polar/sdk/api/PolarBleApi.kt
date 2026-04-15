// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import androidx.annotation.IntRange
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*
import fi.polar.remote.representation.protobuf.UserDeviceSettings.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Polar BLE API.
 *
 * @property features the set of the features API is used for. By giving only the needed features the SDK may reserve only the required resources
 */
abstract class PolarBleApi(val features: Set<PolarBleSdkFeature>) : PolarOnlineStreamingApi,
    PolarOfflineRecordingApi, PolarH10OfflineExerciseApi, PolarSdkModeApi, PolarFirmwareUpdateApi,
    PolarActivityApi, PolarSleepApi, PolarRestServiceApi, PolarTemperatureApi, PolarTrainingSessionApi,
    PolarBleLowLevelApi, PolarDeviceToHostNotificationsApi {

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
         * Offline Exercise V2 feature to record exercise data on supported devices using the Data Merge protocol.
         * This feature enables offline exercise recording when the device is not connected.
         * Requires device to support dm_exercise capability.
         */
        FEATURE_POLAR_OFFLINE_EXERCISE_V2,

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
        FEATURE_POLAR_TEMPERATURE_DATA,

        /**
         * Feature to access training session data and exercise session controls.
         */
        FEATURE_POLAR_TRAINING_DATA,

        /**
         * Feature to control device power/reset behavior via device control notifications.
         */
        FEATURE_POLAR_DEVICE_CONTROL,

        /**
         * Feature to read and set device configuration through Polar Features Configuration Service.
         */
        FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE
    }

    /**
     * The data types in Polar devices that are available for cleanup.
     */
    enum class PolarStoredDataType(val type: String) {
        ACTIVITY("ACT"),
        AUTO_SAMPLE("AUTOS"),
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
        SLEEP, STEPS, DISTANCE, CALORIES, HR_SAMPLES, NIGHTLY_RECHARGE, SKIN_TEMPERATURE, PPI_SAMPLES, ACTIVE_TIME, ACTIVITY_SAMPLES, DAILY_SUMMARY
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
     * then searches for any BLE heart rate capable devices.
     *
     * @return Flowable stream of [PolarDeviceInfo]
     */
    abstract fun searchForDevice(): Flow<PolarDeviceInfo>

    /**
     * Starts searching for BLE devices when subscribed. Search continues as long as observable is
     * subscribed or error. Each found device is emitted only once. By default searches only for Polar devices,
     * but can be controlled by [.setPolarFilter]. If [.setPolarFilter] is false
     * then searches for any BLE heart rate capable devices.
     *
     * @param withDeviceNameFilterPrefix - returned devices are filtered on given device name prefix string. Default: "Polar".
     * @return Flowable stream of [PolarDeviceInfo]
     */
    abstract fun searchForDevice(withDeviceNameFilterPrefix: String? = "Polar"): Flow<PolarDeviceInfo>

    /**
     * When enabled the reconnection is attempted if device connection is lost. By default automatic reconnection is enabled.
     * Note that firmware update (FWU) turns on automatic reconnection automatically, and restores the setting
     * automatically when operation completes. One should not change this setting during FWU.
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
     * @return Returns when nearby device found, and connection attempt started.
     * deviceConnecting callback invoked to inform connection attempt
     */
    abstract suspend fun autoConnectToDevice(
        rssiLimit: Int,
        service: String?,
        timeout: Int,
        unit: TimeUnit,
        polarDeviceType: String?
    )

    /**
     * Start connecting to a nearby Polar device. [PolarBleApiCallback.deviceConnected] callback is
     * invoked when connection to a nearby device is established.
     *
     * @param rssiLimit       RSSI (Received Signal Strength Indication) value is typically from -40 to -60 (dBm), depends on the used Bluetooth chipset and/or antenna tuning
     * @param service         in hex string format like "180D" PolarInvalidArgument invoked if not in correct format
     * @param polarDeviceType like H10, OH1 etc... or null for any polar device
     * @return Returns when nearby device found, and connection attempt started.
     * deviceConnecting callback invoked to inform connection attempt
     */
    abstract suspend fun autoConnectToDevice(
        rssiLimit: Int,
        service: String?,
        polarDeviceType: String?
    )

    /**
     * Fetch device BLE name from BLE session.
     */
    abstract fun getDeviceName(deviceId: String): String

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
     * @param dateAndTime time to set
     * @return Success or error
     */
    abstract suspend fun setLocalTime(identifier: String, dateAndTime: LocalDateTime)

    /**
     * Get current time in device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP].
     * Note, the H10 is not supporting time read.
     *
     * @param identifier polar device id or bt address
     * @return Single observable which emits device time in LocalDateTime instance when observable is subscribed
     * @deprecated Use [getLocalTimeWithZone] instead to also get timezone
     */
    @Deprecated("Use getLocalTimeWithZone() instead to also get timezone", ReplaceWith("getLocalTimeWithZone(identifier)"))
    abstract suspend fun getLocalTime(identifier: String): LocalDateTime

    /**
     * Get current time and timezone from device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP].
     * Note, the H10 is not supporting time read.
     *
     * @param identifier polar device id or bt address
     * @return Single observable which emits device time as [ZonedDateTime] (including timezone offset)
     * when observable is subscribed
     */
    abstract suspend fun getLocalTimeWithZone(identifier: String): ZonedDateTime

    /**
     * Start listening the heart rate from Polar devices when subscribed. This observable listens BLE
     * broadcast and parses heart rate from BLE broadcast. The BLE device don't need to be connected when
     * using this function, the heart rate is parsed from the BLE advertisement
     *
     * @param deviceIds set of Polar device ids to filter or null for a any Polar device
     * @return Flow of [PolarHrBroadcastData]
     */
    abstract fun startListenForPolarHrBroadcasts(deviceIds: Set<String>?): Flow<PolarHrBroadcastData>

    /**
     * Get file as [ByteArray] from device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER]
     *
     * @param identifier polar device id or bt address
     * @param path filesystem file path
     * @return File bytes or error
     */
    abstract suspend fun getFile(identifier: String, path: String): ByteArray

    /**
     * Get [PolarDiskSpaceData] from device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address
     * @return PolarDiskSpaceData or error
     */
    abstract suspend fun getDiskSpace(identifier: String): PolarDiskSpaceData

    /**
     * Set [LedConfig] for device (Verity Sense 2.2.1+). Requires feature [PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION]
     *
     * @param identifier Polar device ID or BT address
     * @param ledConfig new [LedConfig]
    + @return Success or error
     */
    abstract suspend fun setLedConfig(identifier: String, ledConfig: LedConfig)

    /**
     * Perform factory reset to given device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address
     * @param preservePairingInformation preserve pairing information during factory reset
     * @return Success or error
     */
    @Deprecated("Use method doFactoryReset(identifier: String) instead.")
    abstract suspend fun doFactoryReset(
        identifier: String,
        preservePairingInformation: Boolean
    )

    /**
     * Perform factory reset to given device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address
     * @return Success or error
     */
    abstract suspend fun doFactoryReset(identifier: String)

    /**
     * Perform restart device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address
     * @return Success or error
     */
    abstract suspend fun doRestart(identifier: String)

    /**
     * Get [LogConfig] from device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address
    + @return [LogConfig] or error
     */
    abstract suspend fun getLogConfig(identifier: String): LogConfig

    /**
     * Set [LogConfig] for device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address
     * @param logConfig new [LogConfig]
    + @return Success or error
     */
    abstract suspend fun setLogConfig(identifier: String, logConfig: LogConfig)

    /**
     * Set warehouse sleep setting to a given device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL] Warehouse sleep does factory reset to the device
     * and makes it sleep.
     *
     * @param identifier Polar device ID or BT address
     * @return Success or error
     */
    abstract suspend fun setWareHouseSleep(identifier: String)

    /**
     * Turn of device by setting the device to sleep state. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address
     * @return Success or error
     */
    abstract suspend fun turnDeviceOff(identifier: String)

    /**
     * Configure the Polar device with first-time use settings and user identifier. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or Bluetooth address.
     * @param ftuConfig Configuration data for the first-time use, encapsulated in [PolarFirstTimeUseConfig].
     * @return Success or error
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
    abstract suspend fun doFirstTimeUse(identifier: String, ftuConfig: PolarFirstTimeUseConfig)

    /**
     * Check if the First Time Use has been done for the given device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or Bluetooth address.
     * @return Success with "true" or "false" response, or error.
     *
     */
    abstract suspend fun isFtuDone(identifier: String): Boolean

    /**
     * Get the user's physical data from the given device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or Bluetooth address
     * @return [PolarPhysicalConfiguration] if available, null if FTU not done or error
     */
    abstract suspend fun getUserPhysicalConfiguration(identifier: String): PolarPhysicalConfiguration?

    /**
     * Set [PolarUserDeviceSettings] for device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address.
     * @param deviceUserSetting New [PolarUserDeviceSettings]
     * @return Success or error
     */
    @Deprecated("Use setting specific methods instead, e.g. setUserDeviceLocation()")
    abstract suspend fun setUserDeviceSettings(
        identifier: String,
        deviceUserSetting: PolarUserDeviceSettings
    )

    /**
     * Get [PolarUserDeviceSettings] from device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address
    + @return [PolarUserDeviceSettings] or error
     */
    abstract suspend fun getUserDeviceSettings(identifier: String): PolarUserDeviceSettings

    /**
     * Set the user device location on the device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address.
     * @param location The location to be set (usually an enum value representing the location).
     * @return Success or error
     */
    abstract suspend fun setUserDeviceLocation(identifier: String, location: Int)

    /**
     * Set the USB connection mode on the device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address.
     * @param enabled Whether to enable or disable USB connection mode.
     * @return Success or error
     */
    abstract suspend fun setUsbConnectionMode(identifier: String, enabled: Boolean)

    /**
     * Set the automatic training detection settings on the device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address.
     * @param automaticTrainingDetectionMode Whether the automatic training detection should be enabled or disabled.
     * @param automaticTrainingDetectionSensitivity The sensitivity for automatic training detection.
     * @param minimumTrainingDurationSeconds The minimum duration in seconds required for automatic training detection.
     * @return Success or error
     */
    abstract suspend fun setAutomaticTrainingDetectionSettings(
        identifier: String,
        automaticTrainingDetectionMode: Boolean,
        automaticTrainingDetectionSensitivity: Int,
        minimumTrainingDurationSeconds: Int
    )

    /**
     * Set the next Daylight Saving Time (DST) settings on the device in the current timezone. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     * Gets the current timezone from the device and sets DST value based on that.
     *
     * @param identifier Polar device ID or BT address.
     * @return Success or error
     */
    abstract suspend fun setDaylightSavingTime(identifier: String)

    /**
     * Delete data [PolarStoredDataType] from a device. Note that you will need to await for completion. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier, Polar device ID or BT address
     * @param dataType, [PolarStoredDataType] A specific data type that shall be deleted
     * @param until, Data will be deleted from device from history until this date.
     * @return Success or error
     */
    abstract suspend fun deleteStoredDeviceData(
        identifier: String,
        dataType: PolarStoredDataType,
        until: LocalDate?
    )

    /**
     * Enable or disable telemetry (trace logging / diagnostics) on the device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier Polar device ID or BT address
     * @param enabled true = telemetry on, false = off
     * @return Success or error
     */
    abstract suspend fun setTelemetryEnabled(deviceId: String, enabled: Boolean)

    /**
     * Deletes device day (YYYMMDD) folders from the given date range from a device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     * The date range is inclusive.
     * Deletes the day folder (plus all sub-folders with any contents).
     * If date directories are not found for the given date range the operation is still successful and no error is emitted.
     *
     * @param identifier, Polar device ID or BT address
     * @param fromDate The starting date to delete date folders from
     * @param toDate The ending date of last date to delete folders from
     * @return Success or error
     */
    abstract suspend fun deleteDeviceDateFolders(
        identifier: String,
        fromDate: LocalDate?,
        toDate: LocalDate?
    )

    /**
     * Deletes all telemetry data files from a device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     *
     * @param identifier, Polar device ID or BT address
     * @return Success or error
     */
    abstract suspend fun deleteTelemetryData(identifier: String)

    /**
     * Waits for a connection to the specified device.
     * Emits success when the connection is established or an error if the connection fails.
     *
     * @param identifier Polar device ID or Bluetooth address
     * @return Success or error
     */
    abstract suspend fun waitForConnection(identifier: String)

    /**
     * Enable multi BLE connection mode on a given device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE]
     *
     * @param identifier Polar device ID or BT address
     * @param enable, set to true to enable, false to disable multi BLE connection mode.
     * @return Success or error
     */
    abstract suspend fun setMultiBLEConnectionMode(identifier: String, enable: Boolean)

    /**
     * Request multi BLE connection mode status from device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE]
     *
     * @param identifier Polar device ID or BT address
     * @return true if multi BLE connection has been enabled, false otherwise.
     */
    abstract suspend fun getMultiBLEConnectionMode(identifier: String): Boolean

    /**
     * Notify device of the incoming data transfer operation(s). By using this method the device will
     * handle data transfer operations more efficiently by setting it to faster data transfer mode.
     * It also will cause the device to flush the latest data to files giving you the most up-to-date data.
     * Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     * handle data transfer operations more efficiently by setting it to faster data transfer mode.
     * It also will cause the device to flush the latest data to files giving you the most up-to-date data.
     *
     * @param identifier Polar device ID or BT address
     * @return true if start sync notifications sending was successful, false otherwise.
     */
    abstract suspend fun sendInitializationAndStartSyncNotifications(identifier: String): Boolean

    /**
     * Notify device that data transfer operations are completed. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     * By calling this API device will set itself back to normal data transfer mode that will use
     * less battery.
     *
     * @param identifier Polar device ID or BT address
     * @return true if stop sync notifications sending was successful, false otherwise.
     */
    abstract suspend fun sendTerminateAndStopSyncNotifications(identifier: String)

    /**
     * Enable or disable AUTOS file generation on the device. Requires feature [PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL]
     * AUTOS files contain 24/7 OHR data.
     * Status of this setting can be read with getUserDeviceSettings().
     *
     * @param identifier Polar device ID or BT address
     * @param enabled true = AUTOS files enabled, false = disabled
     * @return Completable (success or error)
     */
    abstract suspend fun setAutomaticOHRMeasurementEnabled(
        identifier: String,
        enabled: Boolean
    )

    /**
     * Request last observed battery level value from device. Requires feature [PolarBleSdkFeature.FEATURE_BATTERY_INFO]
     *
     * @param identifier Polar device ID or BT address
     * @return Level of battery level percentage 0 - 100%.
     * Will return -1 if battery level is not available.
     */
    abstract fun getBatteryLevel(identifier: String): Int

    /**
     * Request last observed charging status value from device. Requires feature [PolarBleSdkFeature.FEATURE_BATTERY_INFO]
     *
     * @param identifier Polar device ID or BT address
     * @return [ChargeState] value indicating the last observed charging status of the device.
     */
    abstract fun getChargerState(identifier: String): ChargeState

    /* Check if the device did disconnect from BLE due to removed pairing. If the device did disconnect due to removed pairing,
    * the device will not be available for connection until it is paired again. It may be required to forget the device from Android Bluetooth
    * settings and pair it again.
    *
    * Requires SDK feature(s): None (core API).
    * @Param identifier: Polar device ID or BT address
    * @Return Pair<Boolean, Int> where Boolean True if device was disconnected due to removed pairing, false otherwise;  Int value denoting BluetoothGatt status, @see android.bluetooth.BluetoothGatt
    * Returned Int value will be -1 if the value has not been set.
    */
    abstract fun checkIfDeviceDisconnectedDueRemovedPairing(identifier: String): Pair<Boolean, Int>

    /**
     * Request last observed RSSI (Received Signal Strength Indication) value from device. Does not require any specific feature (core API)
     * @param identifier Polar device ID or BT address
     * @return Int value indicating the last observed BLE signal strength value from the device. If the RSSI value is not available returns -1.
     */
    abstract suspend fun  getRSSIValue(identifier: String): Int
}