/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import Combine

/// Data types available in Polar devices for online streaming or offline recording.
public enum PolarDeviceDataType: CaseIterable {
    case ecg
    case acc
    case ppg
    case ppi
    case gyro
    case magnetometer
    case hr
    case temperature
    case pressure
    case skinTemperature
}

/// Features available in Polar BLE SDK library
public enum PolarBleSdkFeature: CaseIterable {
    /// Hr feature to receive hr and rr data from Polar or any other BLE device via standard HR BLE service
    case feature_hr
    
    /// Device information feature to receive sw information from Polar or any other BLE device
    case feature_device_info
    
    /// Feature to receive battery level info from Polar or any other BLE device
    case feature_battery_info
    
    ///  Polar sensor streaming feature to stream live online data. For example hr, ecg, acc, ppg, ppi, etc...
    case feature_polar_online_streaming
    
    /// Polar offline recording feature to record offline data to Polar device without continuous BLE connection.
    case feature_polar_offline_recording
    
    ///  H10 exercise recording feature to record exercise data to Polar H10 device without continuous BLE connection.
    case feature_polar_h10_exercise_recording
    
    /// Offline Exercise V2 feature to record exercise data on supported devices
    /// using the Data Merge protocol.
    ///
    /// This feature enables offline exercise recording when the device
    /// is not connected to the host device.
    ///
    /// Requires the device to support the `dm_exercise` capability.
    case feature_polar_offline_exercise_v2

    /// Feature to read and set device time in Polar device
    case feature_polar_device_time_setup
    
    /// In SDK mode the wider range of capabilities are available for the online stream or offline recoding than in normal operation mode.
    case feature_polar_sdk_mode

    /// Feature to enable or disable SDK mode blinking LED animation.
    case feature_polar_led_animation

    /// Firmware update for Polar device.
    case feature_polar_firmware_update

    /// Feature to receive activity data from Polar device.
    case feature_polar_activity_data

    /// Feature to access training session data and exercise session controls.
    case feature_polar_training_data

    /// Feature to access sleep recording control and sleep related data APIs.
    case feature_polar_sleep_data

    /// Feature to control device power/reset behavior via device control notifications.
    case feature_polar_device_control

    /// Polar PFTP communication is required for Polar applications.
    case feature_polar_file_transfer

    /// Health Thermometer Service feature to receive temperature measurements.
    case feature_hts

    /// Feature to receive skin temperature data from Polar device.
    case feature_polar_temperature_data

    /// Feature to read and set device configuration through Polar Features Configuration Service.
    case feature_polar_features_configuration_service
    
    /// Feature to receive SPO2 test data from Polar device.
    case feature_polar_spo2_test_data

    /// Feature to configure watch face complications on PolarOS watches.
    case feature_polar_watch_faces_configuration
}

///
///The activity recording data types available in Polar devices.
///
public enum PolarActivityDataType: String, CaseIterable {
    case SLEEP
    case STEPS
    case CALORIES
    case HR_SAMPLES
    case NIGHTLY_RECHARGE
    case SKINTEMPERATURE
    case PEAKTOPEAKINTERVAL
    case ACTIVE_TIME
    case ACTIVITY_SAMPLES
    case DAILY_SUMMARY
    case SPO2_TEST
    case NONE
   }

/// Polar device info
///
///     - deviceId = polar device id or UUID for 3rd party sensors
///     - rssi = RSSI (Received Signal Strength Indicator) value from advertisement
///     - name = local name from advertisement
///     - connectable = true adv type is connectable
///     - hasSAGRFCFileSystem = true, device has wider range of settings and user actions available.
public typealias PolarDeviceInfo = (deviceId: String, address: UUID, rssi: Int, name: String, connectable: Bool, hasSAGRFCFileSystem: Bool)

/// deviceInfo: see #PolarDeviceInfo ,
///
///     - hr: in BPM
///     - batteryStatus: true battery ok
public typealias PolarHrBroadcastData = (deviceInfo: PolarDeviceInfo, hr: UInt8, batteryStatus: Bool)

/// Polar hr data
///
///     - hr in BPM
///     - ppgQuality PPG signal quality of the real time HR between 0 and 100
///     - correctedHr Corrected value of the real time HR value. 0 if unavailable.
///     - rrsMs RR interval in ms. R is a the top highest peak in the QRS complex of the ECG wave and RR is the interval between successive Rs.
///     - contactStatus true if the sensor has contact (with a measurable surface e.g. skin)
///     - contactStatusSupported true if the sensor supports contact status
///     - rrAvailable true if RR data is available.
public typealias PolarHrData = [(hr: UInt8, ppgQuality: UInt8, correctedHr: UInt8, rrsMs: [Int], rrAvailable: Bool, contactStatus: Bool, contactStatusSupported: Bool)]

/// Polar Ecg data
///
///     - samples: Acceleration samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - voltage value in µVolts
public typealias PolarEcgData = ([(timeStamp: UInt64, voltage: Int32)])

/// Polar acc data
///
///     - samples: Acceleration samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - x axis value in millig (including gravity)
///         - y axis value in millig (including gravity)
///         - z axis value in millig (including gravity)
public typealias PolarAccData = ([(timeStamp: UInt64, x: Int32, y: Int32, z: Int32)])

/// Polar gyro data
///
///     - samples: Gyroscope samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - x axis value in deg/sec
///         - y axis value in deg/sec
///         - z axis value in deg/sec
public typealias PolarGyroData = ([(timeStamp: UInt64, x: Float, y: Float, z: Float)])

/// Polar magnetometer data
///
///     - samples: Magnetometer samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - x axis value in Gauss
///         - y axis value in Gauss
///         - z axis value in Gauss
public typealias PolarMagnetometerData = ([(timeStamp: UInt64, x: Float, y: Float, z: Float)])

/// Polar Temperature data
///
///     - timestamp: Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
///     - samples: Temperature samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - temperature value in celsius
public typealias PolarTemperatureData = (timeStamp: UInt64, samples: [(timeStamp: UInt64, temperature: Float)])

/// Polar Pressure data
///
///     - timestamp: Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
///     - samples: Pressure samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - pressure value in bar
public typealias PolarPressureData = (timeStamp: UInt64, samples: [(timeStamp: UInt64, pressure: Float)])


/// PPG data source enum
public enum PpgDataType: Int, CaseIterable {
    // 1 ppg, sport id (frame type 6)
    case ppg1 = 1
    /// 3 ppg + 1 ambient
    case ppg3_ambient1 = 4
    /// 2 ppg  + status channel
    case ppg2 = 3
    /// 3 ppg (NUMINT_TSx, TIA_GAIN_CH1_TSx, TIA_GAIN_CH2_TSx)
    case ppg3 = 7
    /// 16 ppg + 1 status
    case ppg17 = 5
    ///  8 green + 6 red + 6 ir channels + 1 status channel
    case ppg21 = 6
    case unknown = 18
}

/// Polar PPG data
///
///     - type: type of data, which varies based on what is type of optical sensor used in the device
///     - samples: Photoplethysmography samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - channelSamples is the PPG (Photoplethysmography) raw value received from the optical sensor. Based on [OhrDataType] the amount of channels varies. Typically ppg(n) channel + n ambient(s).
///     - status: List of statuses for the PPG samples, available for frametypes 7, 8, 10 and 13. Status bits of each sample: 0 for no valid data, 1 for valid data.
///
public typealias PolarPpgData = (type: PpgDataType, samples: [(timeStamp:UInt64, channelSamples: [Int32], statusBits: [Int8]?)])

/// Polar ppi data
///
///     - Deprecated: timestamp always 0
///     - samples: PPI samples
///         - hr in BPM
///         - ppInMs Pulse to Pulse interval in milliseconds. The value indicates the quality of PP-intervals. When error estimate is below 10ms the PP-intervals are probably very accurate. Error estimate values over 30ms may be caused by movement artefact or too loose sensor-skin contact.
///         - ppErrorEstimate estimate of the expected absolute error in PP-interval in milliseconds
///         - blockerBit = 1 if PP measurement was invalid due to acceleration or other reason
///         - skinContactStatus = 0 if the device detects poor or no contact with the skin
///         - skinContactSupported = 1 if the Sensor Contact feature is supported.
public typealias PolarPpiData = (timeStamp: UInt64, samples: [(timeStamp: UInt64, hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)])

/// Polar Recording status
///
///     - ongoing: true recording running
///     - entryId: unique identifier
public typealias PolarRecordingStatus = (ongoing: Bool, entryId: String)

/// API.
public protocol PolarBleApi: PolarOfflineRecordingApi, PolarOnlineStreamingApi, PolarH10OfflineExerciseApi, PolarSdkModeApi, PolarFirmwareUpdateApi, PolarActivityApi, PolarTemperatureApi, PolarSleepApi, PolarTrainingSessionApi, PolarDeviceToHostNotificationsApi, PolarBleLowLevelApi, PolarRestServiceApi, PolarOfflineExerciseV2Api, PolarTestApi, PolarWatchFaceApi {
    
    /// remove all known devices, which are not in use
    ///
    /// - Requires SDK feature(s): None (core API).
    ///
    func cleanup()
    
    /// Enable or disable polar filter.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameter enable: false disable polar filter
    ///
    func polarFilter(_ enable: Bool)
    
    /// Start connecting to a nearby device. `PolarBleApiObservers` polarDeviceConnected is
    /// invoked when a connection is established.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameter rssi: (Received Signal Strength Indicator) value is typically between -40 to -55 dBm.
    /// - Parameter service: optional service to contain in device advertisement prior to connection attempt
    /// - Parameter polarDeviceType: like H10, OH1 etc... or nil for any polar device
    /// - Returns: Completes when scan for nearby device has ended and connection attempt is started and deviceConnecting callback will be invoked.
    ///
    func startAutoConnectToDevice(_ rssi: Int, service: CBUUID?, polarDeviceType: String?) async throws
    
    /// Request a connection to a Polar device. Invokes `PolarBleApiObservers` polarDeviceConnected.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameter identifier: Polar device id printed on the sensor/device or UUID.
    /// - Throws: InvalidArgument if identifier is invalid polar device id or invalid uuid
    ///
    func connectToDevice(_ identifier: String) throws
    
    /// Disconnect from the current Polar device.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameter identifier: Polar device id
    /// - Throws: InvalidArgument if identifier is invalid polar device id or invalid uuid
    ///
    func disconnectFromDevice(_ identifier: String) throws
    
    /// Start searching for Polar device(s).
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Returns: `AsyncThrowingStream` emitting `PolarDeviceInfo` for every new Polar device found.
    ///
    func searchForDevice() -> AsyncThrowingStream<PolarDeviceInfo, Error>

    /// Start searching for compatible device(s) with a given device name prefix.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameter withRequiredDeviceNamePrefix: Returned devices are filtered based on the given device name prefix. Pass `nil` to return all devices. Default: `"Polar"`
    /// - Returns: `AsyncThrowingStream` emitting `PolarDeviceInfo` for every new device found.
    ///
    @available(*, deprecated, message: "Use searchForDevice(withNameContaining: String) instead.")
    func searchForDevice(withRequiredDeviceNamePrefix: String?) -> AsyncThrowingStream<PolarDeviceInfo, Error>
    
    /// Start searching for compatible device(s) with a given device name prefix.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameter withNameContaining: Returned devices are filtered based on the given device name prefix. Pass `nil` to return all devices. Default: `"Polar"`
    /// - Returns: `AsyncThrowingStream` emitting `PolarDeviceInfo` for every new device found.
    ///
    func searchForDevice(withNameContaining: String?) -> AsyncThrowingStream<PolarDeviceInfo, Error>
    

    /// Start listening the heart rate from Polar devices when subscribed.
    /// This observable listens BLE broadcast and parses heart rate from BLE broadcast. The
    /// BLE device don't need to be connected when using this function, the heart rate is parsed
    /// from the BLE advertisement
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameter identifiers: set of Polar device ids to filter or null for a any Polar device
    /// - Returns: AnyPublisher emitting multiple values
    ///
    func startListenForPolarHrBroadcasts(_ identifiers: Set<String>?) -> AsyncThrowingStream<PolarHrBroadcastData, Error>
    
    /// Check if the feature is ready.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameters:
    ///   - identifier: the identifier of the device to check.
    ///   - feature: the feature to check for readiness.
    /// - Returns: a boolean indicating whether a specific feature is ready for use on a given device.
    ///
    func isFeatureReady(_ identifier: String, feature: PolarBleSdkFeature) -> Bool
    
    /// Set local time to device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_time_setup`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - time: time to set
    ///   - zone: time zone to set
    /// - Throws: see `PolarErrors` for possible errors invoked
    ///
    func setLocalTime(_ identifier: String, time: Date, zone: TimeZone) async throws
    
    /// Get current time in device. Note, the H10 is not supporting time read.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_time_setup`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Returns: `Date` representing the current device time.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    @available(*, deprecated, message: "Use getLocalTimeWithZone() instead to also get timezone")
    func getLocalTime(_ identifier: String) async throws -> Date
    
    /// Get current time and timezone from device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_time_setup`
    /// - Parameter identifier: Polar device id or UUID
    /// - Returns: `(Date, TimeZone)` representing the current device time and timezone.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func getLocalTimeWithZone(_ identifier: String) async throws -> (Date, TimeZone)
    
    /// Get `PolarDiskSpaceData` from device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Returns: `PolarDiskSpaceData` with disk space information from the device.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func getDiskSpace(_ identifier: String) async throws -> PolarDiskSpaceData

    /// Set [LedConfig] to enable or disable blinking LEDs (Verity Sense 2.2.1+).
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_led_animation`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - ledConfig: to enable or disable LEDs blinking
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setLedConfig(_ identifier: String, ledConfig: LedConfig) async throws
    
    ///
    /// Enable or disable telemetry (trace logging / diagnostics) on the device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameter identifier: Polar device ID or BT address
    /// - Parameter enabled: true = telemetry on, false = off
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setTelemetryEnabled(_ identifier: String, enabled: Bool) async throws

    /// Perform factory reset to given device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - preservePairingInformation: preserve pairing information during factory reset
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    @available(*, deprecated, message: "Use doFactoryReset(_ identifier: String) instead.")
    func doFactoryReset(_ identifier: String, preservePairingInformation: Bool) async throws

    /// Perform factory reset to given device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func doFactoryReset(_ identifier: String) async throws

    /// Perform restart to given device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - preservePairingInformation: preserve pairing information during restart
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    @available(*, deprecated, message: "Use doRestart(_ identifier: String) instead.")
    func doRestart(_ identifier: String, preservePairingInformation: Bool) async throws

    /// Perform restart to given device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func doRestart(_ identifier: String) async throws

    /// Get SD log configuration from a device (SDLOGS.BPB)
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Returns: `SDLogConfig` describing the current SD log configuration on the device.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func getSDLogConfiguration(_ identifier: String) async throws -> SDLogConfig
    
    /// Set SD log configuration to a device (SDLOGS.BPB)
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - logConfiguration: A motley crew of boolean values describing the SD log configuration
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setSDLogConfiguration(_ identifier: String, logConfiguration: SDLogConfig) async throws

    ///Set [FtuConfig] for device
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - ftuConfig: Configuration data for the first-time use, encapsulated in [PolarFirstTimeUseConfig].
    /// - Throws: See `PolarErrors` for possible errors invoked.
    /// - [PolarFirstTimeUseConfig] class enforces specific ranges and valid values for each parameter:
    ///   - Gender: "Male" or "Female"
    ///   - Height: 90 to 240 cm
    ///   - Weight: 15 to 300 kg
    ///   - Max heart rate: 100 to 240 bpm
    ///   - Resting heart rate: 20 to 120 bpm
    ///   - VO2 max: 10 to 95
    ///   - Training background: One of the predefined levels (10, 20, 30, 40, 50, 60)
    ///   - Typical day: One of [TypicalDay] values
    ///   - Sleep goal: Minutes, valid range [300-660]
    ///
    func doFirstTimeUse(_ identifier: String, ftuConfig: PolarFirstTimeUseConfig) async throws
    
    /// Check if the First Time Use has been done for the given Polar device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Returns: `true` when FTU has been done, `false` otherwise.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func isFtuDone(_ identifier: String) async throws -> Bool

    /// Get the user's physical data from the given Polar device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device ID or UUID
    /// - Returns: `PolarPhysicalConfiguration` if available, or `nil` if not set on device.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    func getUserPhysicalConfiguration(_ identifier: String) async throws -> PolarPhysicalConfiguration?

    /// Set the device to warehouse sleep state. Factory reset will be performed in order to enable the setting.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setWarehouseSleep(_ identifier: String) async throws
    
    /// Turn of device by setting the device to sleep state.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func turnDeviceOff(_ identifier: String) async throws

    /// Get Device User Settings to a device from proto in device (UDEVSET.BPB)
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Returns: `PolarUserDeviceSettings.PolarUserDeviceSettingsResult` containing the user device settings.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func getPolarUserDeviceSettings(identifier: String) async throws -> PolarUserDeviceSettings.PolarUserDeviceSettingsResult
    
    /// Set Device User Settings to a device (UDEVSET.BPB)
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - polarUserDeviceSettings: Collection of user device settings, like device location on user.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setPolarUserDeviceSettings(_ identifier: String, polarUserDeviceSettings: PolarUserDeviceSettings) async throws

    /// Delete data [PolarStoredDataType] from a device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device ID or BT address
    ///   - dataType: `PolarStoredDataType` — a specific data type that shall be deleted
    ///   - until: Data will be deleted from device from history until this date.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func deleteStoredDeviceData(_ identifier: String, dataType: PolarStoredDataType.StoredDataType, until: Date?) async throws
    
    /// Delete device date folders from a device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - fromDate: The starting date to delete date folders from
    ///   - toDate: The ending date of last date to delete folders from
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func deleteDeviceDateFolders(_ identifier: String, fromDate: Date?, toDate: Date?) async throws
    
    /// Delete telemetry data files from a device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func deleteTelemetryData(_ identifier: String) async throws

    /// Waits for the device to establish a connection.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameters:
    ///  - identifier: Polar device ID or Bluetooth UUID
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func waitForConnection(_ identifier: String) async throws
    
    /// Set user device location on a device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - location: Device location as an integer value (see `PolarUserDeviceSettings.DeviceLocation`)
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setUserDeviceLocation(_ identifier: String, location: Int) async throws

    /// Set USB connection mode on a device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - enabled: Boolean flag to enable or disable USB connection mode
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setUsbConnectionMode(_ identifier: String, enabled: Bool) async throws

    /// Set automatic training detection settings on a device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - mode: Boolean flag to enable or disable automatic training detection
    ///   - sensitivity: Sensitivity level as integer, range [0, 100]. Higher values cause training to trigger more easily
    ///   - minimumDuration: Minimum training duration in seconds
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setAutomaticTrainingDetectionSettings(
        _ identifier: String,
        mode: Bool,
        sensitivity: Int,
        minimumDuration: Int
    ) async throws
    
    /// Set the next Daylight Saving Time (DST) settings on the device in the current timezone.
    /// Gets the current timezone from the device and sets DST value based on that.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setDaylightSavingTime(_ identifier: String) async throws

    /// Request multi BLE connection mode status from device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_features_configuration_service`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Returns: `true` if multi BLE connection mode is enabled, `false` otherwise.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func getMultiBLEConnectionMode(identifier: String) async throws -> Bool
    
    /// Enable multi BLE connection mode on a given device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_features_configuration_service`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - enable: Boolean flag to enable or disable multi BLE connection mode
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setMultiBLEConnectionMode(identifier: String, enable: Bool) async throws
    
    /// Notify device of the incoming data transfer operation(s). By using this method the device will handle data transfer operations more efficiently by setting it to faster data transfer mode.
    /// It also will cause the device to flush the latest data to files giving you the most up-to-date data.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func sendInitializationAndStartSyncNotifications(identifier: String) async throws

    /// Notify device that data transfer operations are completed. By calling this API device will set itself back to normal data transfer mode that will use less battery.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func sendTerminateAndStopSyncNotifications(identifier: String) async throws
    
    /// Enable or disable AUTOS file generation on the device.
    /// AUTOS files contain 24/7 OHR data.
    /// Status of this setting can be read with getUserDeviceSettings().
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_device_control`
    /// - parameter identifier: Polar device ID or BT address
    /// - parameter enabled: true = AUTOS files enabled, false = disabled
    /// - Throws: See `PolarErrors` for possible errors invoked.
    ///
    func setAutomaticOHRMeasurementEnabled(_ identifier: String, enabled: Bool) async throws

    /// Get last received RSSI (Received Signal Strength Indicator) value for the connected device.
    /// The value is obtained from iOS BLE in 1 second interval.
    /// The value is typically between -40 to -55 dBm when the device is nearby.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameter identifier: Polar device ID or BT address
    /// - Returns: Integer value of the last received RSSI or error if the device is not connected or identifier is invalid.
    ///
    func getRSSIValue(_ identifier: String) throws -> Int

    /// Check if the device did disconnect from BLE due to removed pairing. If the device did disconnect due to removed pairing,
    /// the device will not be available for connection until it is paired again. It may be required to forget the device from iOS Bluetooth
    /// settings and pair it again.
    ///
    /// - Requires SDK feature(s): None (core API).
    /// - Parameter identifier: Polar device ID or BT address
    /// - Returns: True if was disconnected due to removed pairing, false otherwise (BLE connection is OK).
    ///
    func checkIfDeviceDisconnectedDueRemovedPairing(_ identifier: String) throws -> Bool

    /// Common GAP (Generic access profile) observer
    var observer: PolarBleApiObserver? { get set }
    
    /// Device info observer for DIS (Device information service) and BAS (Battery service) GATT (Generic attributes) client
    var deviceInfoObserver: PolarBleApiDeviceInfoObserver? { get set }
    
    /// Device observer for HR GATT client
    @available(*, deprecated, message: "The functionality has changed. Please use the startHrStreaming API to get the heart rate data ")
    var deviceHrObserver: PolarBleApiDeviceHrObserver? { get set }
    
    /// Bluetooth power state observer
    var powerStateObserver: PolarBleApiPowerStateObserver? { get set }
    
    /// Device features ready observer
    var deviceFeaturesObserver: PolarBleApiDeviceFeaturesObserver? { get set }

    
    /// Helper to check if Ble is currently powered
    /// - Returns: current power state
    var isBlePowered: Bool { get }
    
    /// Optional logger set to get traces from sdk
    var logger: PolarBleApiLogger? { get set }
    
    /// Optional disable or enable automatic reconnection, by default it is enabled.
    ///
    /// Note that firmware update (FWU) turns on automatic reconnection automatically, and restores the setting
    /// automatically when operation completes. One should not change this setting during FWU.
    var automaticReconnection: Bool { get set }
    
    /// Request last observed battery level value from device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_battery_info`
    /// - Parameters:
    ///  - identifier Polar device ID or BT address
    /// - Returns: Returns the level of battery charging percentage 0 - 100%
    /// Will return -1 if battery battery level is not available.
    ///
    func getBatteryLevel(identifier: String) throws -> Int

    /// Request last observed charging status value from device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_battery_info`
    /// - Parameters:
    ///  - identifier Polar device ID or BT address
    /// - Returns: Returns `ChargeState` value indicating the last observed charging status of the device.
    /// Will return -1 if battery battery charging percentage level is not available.
    ///
    func getChargerState(identifier: String) throws -> BleBasClient.ChargeState
}
