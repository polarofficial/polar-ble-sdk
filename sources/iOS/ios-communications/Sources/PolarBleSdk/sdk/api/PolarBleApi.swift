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
///     - rrsMs RR interval in ms. R is a the top highest peak in the QRS complex of the ECG wave and RR is the interval between successive Rs.
///     - contactStatus true if the sensor has contact (with a measurable surface e.g. skin)
///     - contactStatusSupported true if the sensor supports contact status
///     - rrAvailable true if RR data is available.
public typealias PolarHrData = [(hr: UInt8, rrsMs: [Int], rrAvailable: Bool, contactStatus: Bool, contactStatusSupported: Bool)]

/// Polar Ecg data
///
///     - Deprecated: Timestamp: Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
///     - samples: Acceleration samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - voltage value in µVolts
public typealias PolarEcgData = (timeStamp: UInt64, samples: [(timeStamp: UInt64, voltage: Int32)])

/// Polar acc data
///
///     - Deprecated: Timestamp: Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
///     - samples: Acceleration samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - x axis value in millig (including gravity)
///         - y axis value in millig (including gravity)
///         - z axis value in millig (including gravity)
public typealias PolarAccData = (timeStamp: UInt64, samples: [(timeStamp: UInt64, x: Int32, y: Int32, z: Int32)])

/// Polar gyro data
///
///     - Deprecated: Timestamp: Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
///     - samples: Gyroscope samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - x axis value in deg/sec
///         - y axis value in deg/sec
///         - z axis value in deg/sec
public typealias PolarGyroData = (timeStamp: UInt64, samples: [(timeStamp: UInt64, x: Float, y: Float, z: Float)])

/// Polar magnetometer data
///
///     - Deprecated: Timestamp: Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
///     - samples: Magnetometer samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - x axis value in Gauss
///         - y axis value in Gauss
///         - z axis value in Gauss
public typealias PolarMagnetometerData = (timeStamp: UInt64, samples: [(timeStamp: UInt64, x: Float, y: Float, z: Float)])

/// OHR data source enum
@available(*, deprecated, renamed: "PpgDataType")
public enum OhrDataType: Int, CaseIterable {
    /// 3 ppg + 1 ambient
    case ppg3_ambient1 = 4
    case unknown = 18
}

/// Polar Ohr data
///
///     - Deprecated: Timestamp: Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
///     - type: type of data, which varies based on what is type of optical sensor used in the device
///     - samples: Photoplethysmography samples
///         - timeStamp: moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
///         - channelSamples is the PPG (Photoplethysmography) raw value received from the optical sensor. Based on [OhrDataType] the amount of channels varies. Typically ppg(n) channel + n ambient(s).
///
@available(*, deprecated, renamed: "PolarPpgData")
public typealias PolarOhrData = (timeStamp: UInt64, type: OhrDataType, samples: [(timeStamp:UInt64, channelSamples: [Int32])])


/// PPG data source enum
public enum PpgDataType: Int, CaseIterable {
    /// 3 ppg + 1 ambient
    case ppg3_ambient1 = 4
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
public protocol PolarBleApi: PolarOfflineRecordingApi, PolarOnlineStreamingApi, PolarH10OfflineExerciseApi, PolarSdkModeApi {
    
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
    
    /// SDK mode feature available in the device and ready observer
    @available(*, deprecated, message: "The functionality has changed. Please use the bleSdkFeatureReady to know if sdkModeFeature is available")
    var sdkModeFeatureObserver: PolarBleApiSdkModeFeatureObserver? { get set }
    
    /// Helper to check if Ble is currently powered
    /// - Returns: current power state
    var isBlePowered: Bool { get }
    
    /// optional logger set to get traces from sdk
    var logger: PolarBleApiLogger? { get set }
    
    /// optional disable or enable automatic reconnection, by default it is enabled
    var automaticReconnection: Bool { get set }
}
