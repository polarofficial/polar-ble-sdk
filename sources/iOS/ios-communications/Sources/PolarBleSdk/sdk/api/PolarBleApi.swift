/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import RxSwift

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
    
    /// Feature to read and set device time in Polar device
    case feature_polar_device_time_setup
    
    ///  In SDK mode the wider range of capabilities are available for the online stream or offline recoding than in normal operation mode.
    case feature_polar_sdk_mode

    /// Feature to enable or disable SDK mode blinking LED animation.
    case feature_polar_led_animation

    /// Firmware update for Polar device.
    case feature_polar_firmware_update

    /// Feature to receive activity data from Polar device.
    case feature_polar_activity_data
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
    case SKIN_TEMPERATURE
    case NONE
   }

/// Polar device info
///
///     - deviceId = polar device id or UUID for 3rd party sensors
///     - rssi = RSSI (Received Signal Strength Indicator) value from advertisement
///     - name = local name from advertisement
///     - connectable = true adv type is connectable
public typealias PolarDeviceInfo = (deviceId: String, address: UUID, rssi: Int, name: String, connectable: Bool)

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
///
public typealias PolarPpgData = (type: PpgDataType, samples: [(timeStamp:UInt64, channelSamples: [Int32])])


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
public typealias PolarPpiData = (timeStamp: UInt64, samples: [(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)])

/// Polar exercise entry
///
///     - path: Resource location in the device,
///     - date: Entry date and time. Only OH1 and Polar Verity Sense supports date and time
///     - entryId: unique identifier
public typealias PolarExerciseEntry = (path: String, date: Date, entryId: String)
/// Polar Exercise Data
///
///     - interval: in seconds
///     - samples: List of HR or RR samples in BPM
public typealias PolarExerciseData = (interval: UInt32, samples: [UInt32])
/// Polar Recording status
///
///     - ongoing: true recording running
///     - entryId: unique identifier
public typealias PolarRecordingStatus = (ongoing: Bool, entryId: String)

/// API.
public protocol PolarBleApi: PolarOfflineRecordingApi, PolarOnlineStreamingApi, PolarH10OfflineExerciseApi, PolarSdkModeApi, PolarFirmwareUpdateApi, PolarActivityApi, PolarSleepApi {
    
    /// remove all known devices, which are not in use
    func cleanup()
    
    /// Enable or disable polar filter.
    ///
    /// - Parameter enable: false disable polar filter
    func polarFilter(_ enable: Bool)
    
    /// Start connecting to a nearby device. `PolarBleApiObservers` polarDeviceConnected is
    /// invoked when a connection is established.
    /// - Parameter rssi: (Received Signal Strength Indicator) value is typically between -40 to -55 dBm.
    /// - Parameter service: optional service to contain in device advertisement prior to connection attempt
    /// - Parameter polarDeviceType: like H10, OH1 etc... or nil for any polar device
    /// - Returns: Completable. Complete called when scan for nearby device has ended and connection attempt is started and deviceConnecting callback will be invoked.
    func startAutoConnectToDevice(_ rssi: Int, service: CBUUID?, polarDeviceType: String?) -> Completable
    
    /// Request a connection to a Polar device. Invokes `PolarBleApiObservers` polarDeviceConnected.
    /// - Parameter identifier: Polar device id printed on the sensor/device or UUID.
    /// - Throws: InvalidArgument if identifier is invalid polar device id or invalid uuid
    func connectToDevice(_ identifier: String) throws
    
    /// Disconnect from the current Polar device.
    ///
    /// - Parameter identifier: Polar device id
    /// - Throws: InvalidArgument if identifier is invalid polar device id or invalid uuid
    func disconnectFromDevice(_ identifier: String) throws
    
    /// Start searching for Polar device(s)
    ///
    /// - Parameter onNext: Invoked once for each device
    /// - Returns: Observable stream
    ///  - onNext: for every new polar device found
    func searchForDevice() -> Observable<PolarDeviceInfo>
    
    /// Start listening the heart rate from Polar devices when subscribed.
    /// This observable listens BLE broadcast and parses heart rate from BLE broadcast. The
    /// BLE device don't need to be connected when using this function, the heart rate is parsed
    /// from the BLE advertisement
    ///
    /// - Parameter identifiers: set of Polar device ids to filter or null for a any Polar device
    /// - Returns: Observable stream
    func startListenForPolarHrBroadcasts(_ identifiers: Set<String>?) -> Observable<PolarHrBroadcastData>
    
    /// Check if the feature is ready.
    ///
    /// - Parameters:
    ///   - identifier: the identifier of the device to check.
    ///   - feature: the feature to check for readiness.
    /// - Returns: a boolean indicating whether a specific feature is ready for use on a given device.
    func isFeatureReady(_ identifier: String, feature: PolarBleSdkFeature) -> Bool
    
    /// Set local time to device.
    ///
    /// Requires feature `PolarBleSdkFeature.feature_polar_device_time_setup`.
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - time: time to set
    ///   - zone: time zone to set
    /// - Returns: Completable stream
    ///   - success: when time has been set to device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func setLocalTime(_ identifier: String, time: Date, zone: TimeZone) -> Completable
    
    /// Get current time in device. Note, the H10 is not supporting time read.
    ///
    /// Requires feature `PolarBleSdkFeature.feature_polar_device_time_setup`
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - time: time to set
    ///   - zone: time zone to set
    /// - Returns: Single stream
    ///   - success: once after settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func getLocalTime(_ identifier: String) -> Single<Date>
    
    /// Get `PolarDiskSpaceData` from device.
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Returns: Single stream
    ///   - success: once after disk space received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func getDiskSpace(_ identifier: String) -> Single<PolarDiskSpaceData>

    /// Set [LedConfig] to enable or disable blinking LEDs (Verity Sense 2.2.1+).
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - ledConfig: to enable or disable LEDs blinking
    /// - Returns: Completable stream
    ///   - success: when enable or disable sent to device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func setLedConfig(_ identifier: String, ledConfig: LedConfig) -> Completable

    /// Perform factory reset to given device.
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - preservePairingInformation: preserve pairing information during factory reset
    /// - Returns: Completable stream
    ///   - success: when factory reset notification sent to device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func doFactoryReset(_ identifier: String, preservePairingInformation: Bool) -> Completable
    
    /// Perform restart to given device.
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - preservePairingInformation: preserve pairing information during restart
    /// - Returns: Completable stream
    ///   - success: when restart notification sent to device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func doRestart(_ identifier: String, preservePairingInformation: Bool) -> Completable
    
    /// Get SD log configuration from a device (SDLOGS.BPB)
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Returns: Single stream
    ///   - success: A motley crew of boolean values describing the SD log configuration
    ///   - onError: see `PolarErrors` for possible errors invoked
    func getSDLogConfiguration(_ identifier: String) -> Single<SDLogConfig>
    
    /// Set SD log configuration to a device (SDLOGS.BPB)
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - logConfiguration: A motley crew of boolean values describing the SD log configuration
    /// - Returns: Completable stream
    ///   - success: When SD log configuration has been written to the device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func setSDLogConfiguration(_ identifier: String, logConfiguration: SDLogConfig) -> Completable

    ///Set [FtuConfig] for device
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - ftuConfig: Configuration data for the first-time use, encapsulated in [PolarFirstTimeUseConfig].
    /// - Returns: Completable stream
    ///   - success: when enable or disable sent to device
    ///   - onError: see `PolarErrors` for possible errors invoked
    ///- [PolarFirstTimeUseConfig] class enforces specific ranges and valid values for each parameter:
    ///   - Gender: "Male" or "Female"
    ///   - Height: 90 to 240 cm
    ///   - Weight: 15 to 300 kg
    ///   - Max heart rate: 100 to 240 bpm
    ///   - Resting heart rate: 20 to 120 bpm
    ///   - VO2 max: 10 to 95
    ///   - Training background: One of the predefined levels (10, 20, 30, 40, 50, 60)
    ///   - Typical day: One of [TypicalDay] values
    ///   - Sleep goal: Minutes, valid range [300-660]
    func doFirstTimeUse(_ identifier: String, ftuConfig: PolarFirstTimeUseConfig) -> Completable
    
    /// Check if the First Time Use has been done for the given Polar device.
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Returns: Boolean
    ///   - success: true when FTU has been done, false otherwise
    ///   - onError: see `PolarErrors` for possible errors invoked
    func isFtuDone(_ identifier: String) -> Single<Bool>

    /// Set the device to warehouse sleep state. Factory reset will be performed in order to enable the setting.
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Returns: Completable stream
    ///   - success: when warehouse sleep has been set together with  factory reset
    ///   - onError: see `PolarErrors` for possible errors invoked
    func setWarehouseSleep(_ identifier: String) -> Completable
    
    /// Get Device User Settings to a device from proto in device (UDEVSET.BPB)
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    /// - Returns: Single stream
    ///   - success: Collection of user device settings, like device location on user.
    ///   - onError: see `PolarErrors` for possible errors invoked
    func getPolarUserDeviceSettings(identifier: String) -> Single<PolarUserDeviceSettings.PolarUserDeviceSettingsResult>
    
    /// Set Device User Settings to a device (UDEVSET.BPB)
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - polarUserDeviceSettings: Collection of user device settings, like device location on user.
    /// - Returns: Completable stream
    ///   - success: When Device User Settings configuration has been written to the device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func setPolarUserDeviceSettings(_ identifier: String, polarUserDeviceSettings: PolarUserDeviceSettings?) -> Completable
    
    /// Delete data [PolarStoredDataType] from a device.
    ///
    /// @param identifier, Polar device ID or BT address
    /// @param dataType, [PolarStoredDataType] A specific data type that shall be deleted
    /// @param until, Data will be deleted from device from history until this date.
    /// @return [Completable] emitting success or error
    func deleteStoredDeviceData(_ identifier: String, dataType: PolarStoredDataType.StoredDataType, until: Date?) -> Completable

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
    
    /// optional logger set to get traces from sdk
    var logger: PolarBleApiLogger? { get set }
    
    /// optional disable or enable automatic reconnection, by default it is enabled
    var automaticReconnection: Bool { get set }
}
