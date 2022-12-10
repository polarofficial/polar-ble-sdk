/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import RxSwift

/// device streaming features
public enum DeviceStreamingFeature: Int, CaseIterable {
    case ecg
    case acc
    case ppg
    case ppi
    case gyro
    case magnetometer
}

///  Recoding intervals for H10 recording start
public enum RecordingInterval: Int {
    case interval_1s = 1
    case interval_5s = 5
}

/// Sample types for H10 recording start
public enum SampleType: Int {
    /// recording type to use is hr in BPM
    case hr
    /// recording type to use is rr interval
    case rr
}

/// features available
public enum Features: Int, CaseIterable {
    /// hr feature enables hr client to receive hr and rr data from device
    case hr = 1
    /// deviceInfo enables dis client to receive fw information from device
    case deviceInfo = 2
    /// batteryStatus enables bas client to receive battery level info from device
    case batteryStatus = 4
    /// polarSensorStreaming enables stream client to start acc, ppg, ecg, ppi streams
    case polarSensorStreaming = 8
    /// polarFileTransfer enables the listing, read stored exercises and setup of  local time to device.
    /// Additionally enables the recording start, recoding stop and recording status request for Polar H10 .
    case polarFileTransfer = 16
    /// allFeatures enables all features available
    case allFeatures = 0xff
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
public typealias PolarOhrData = (timeStamp: UInt64, type: OhrDataType, samples: [(timeStamp:UInt64, channelSamples: [Int32])])

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
public typealias PolarRecordingStatus = (ongoing: Bool,entryId: String)

/// API.
public protocol PolarBleApi {
    
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
    
    /// helper to check is feature ready
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - feature: see `Features` only supported is polarSensorStreaming and polarFileTransfer
    /// - Returns: true if requested feature is ready for use
    func isFeatureReady(_ identifier: String, feature: Features) -> Bool
    
    /// Set local time to device. Requires `polarFileTransfer` feature.
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - time: time to set
    ///   - zone: time zone to set
    /// - Returns: Completable stream
    ///   - success: when time has been set to device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func setLocalTime(_ identifier: String, time: Date, zone: TimeZone) -> Completable
    
    ///  Get current time in device. Requires `polarFileTransfer` feature.  Not supported by Polar H10. 
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - time: time to set
    ///   - zone: time zone to set
    /// - Returns: Single stream
    ///   - success: once after settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func getLocalTime(_ identifier: String) -> Single<Date>
    
    /// Request start recording. Supported only by Polar H10. Requires `polarFileTransfer` feature.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - exerciseId: unique identifier for for exercise entry length from 1-64 bytes
    ///   - interval: recording interval to be used. Has no effect if `sampleType` is `SampleType.rr`
    ///   - sampleType: sample type to be used.
    /// - Returns: Completable stream
    ///   - success: recording started
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startRecording(_ identifier: String, exerciseId: String, interval: RecordingInterval, sampleType: SampleType) -> Completable
    
    /// Request stop for current recording. Supported only by Polar H10. Requires `polarFileTransfer` feature.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Returns: Completable stream
    ///   - success: recording stopped
    ///   - onError: see `PolarErrors` for possible errors invoked
    func stopRecording(_ identifier: String) -> Completable
    
    /// Request current recording status. Supported only by Polar H10. Requires `polarFileTransfer` feature.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    /// - Returns: Single stream
    ///   - success: see `PolarRecordingStatus`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestRecordingStatus(_ identifier: String) -> Single<PolarRecordingStatus>
    
    /// Api for fetching stored exercises list from Polar H10 device. Requires `polarFileTransfer` feature. This API is working for Polar OH1 and Polar Verity Sense devices too, however in those devices recording of exercise requires that sensor is registered to Polar Flow account.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    /// - Returns: Observable stream
    ///   - onNext: see `PolarExerciseEntry`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func fetchStoredExerciseList(_ identifier: String) -> Observable<PolarExerciseEntry>
    
    /// Api for fetching a single exercise from Polar H10 device. Requires `polarFileTransfer` feature. This API is working for Polar OH1 and Polar Verity Sense devices too, however in those devices recording of exercise requires that sensor is registered to Polar Flow account.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - entry: single exercise entry to be fetched
    /// - Returns: Single stream
    ///   - success: invoked after exercise data has been fetched from the device. see `PolarExerciseEntry`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func fetchExercise(_ identifier: String, entry: PolarExerciseEntry) -> Single<PolarExerciseData>
    
    /// Api for removing single exercise from Polar H10 device. Requires `polarFileTransfer` feature. This API is working for Polar OH1 and Polar Verity Sense devices too, however in those devices recording of exercise requires that sensor is registered to Polar Flow account.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - entry: single exercise entry to be removed
    /// - Returns: Completable stream
    ///   - complete: entry successfully removed
    ///   - onError: see `PolarErrors` for possible errors invoked
    func removeExercise(_ identifier: String, entry: PolarExerciseEntry) ->Completable
    
    /// Start listening to heart rate broadcasts from one or more Polar devices
    ///
    /// - Parameter identifiers: set of Polar device ids to filter or null for a any Polar device
    /// - Returns: Observable stream
    func startListenForPolarHrBroadcasts(_ identifiers: Set<String>?) -> Observable<PolarHrBroadcastData>
    
    ///  Request the stream settings available in current operation mode. This request shall be used before the stream is started
    ///  to decide currently available settings. The available settings depend on the state of the device. For example, if any stream(s)
    ///  or optical heart rate measurement is already enabled, then the device may limit the offer of possible settings for other stream feature.
    ///  Requires `polarSensorStreaming` feature.
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: selected feature from`DeviceStreamingFeature`
    /// - Returns: Single stream
    ///   - success: once after settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestStreamSettings(_ identifier: String, feature: DeviceStreamingFeature) -> Single<PolarSensorSetting>
    
    /// Request full steam settings capabilities. The request returns the all capabilities of the requested streaming feature not limited by the current operation mode.
    /// Requires `polarSensorStreaming` feature. This request is supported only by Polar Verity Sense firmware 1.1.5
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: selected feature from`DeviceStreamingFeature`
    /// - Returns: Single stream
    ///   - success: once after full settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestFullStreamSettings(_ identifier: String, feature: DeviceStreamingFeature) -> Single<PolarSensorSetting>
    
    /// Start the ECG (Electrocardiography) stream. ECG stream is stopped if the connection is closed, error occurs or stream is disposed.
    /// Requires `polarSensorStreaming` feature. Before starting the stream it is recommended to query the available settings using `requestStreamSettings`
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarEcgData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startEcgStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarEcgData>
    
    ///  Start ACC (Accelerometer) stream. ACC stream is stopped if the connection is closed, error occurs or stream is disposed.
    ///  Requires `polarSensorStreaming` feature. Before starting the stream it is recommended to query the available settings using `requestStreamSettings`
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarAccData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startAccStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarAccData>
    
    /// Start Gyro stream. Gyro stream is stopped if the connection is closed, error occurs during start or stream is disposed.
    /// Requires `polarSensorStreaming` feature. Before starting the stream it is recommended to query the available settings using `requestStreamSettings`
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    func startGyroStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarGyroData>
    
    /// Start magnetometer stream. Magnetometer stream is stopped if the connection is closed, error occurs or stream is disposed.
    /// Requires `polarSensorStreaming` feature. Before starting the stream it is recommended to query the available settings using `requestStreamSettings`
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    func startMagnetometerStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarMagnetometerData>
    
    /// Start OHR (Optical heart rate) PPG (Photoplethysmography) stream. PPG stream is stopped if the connection is closed, error occurs or stream is disposed.
    /// Requires `polarSensorStreaming` feature. Before starting the stream it is recommended to query the available settings using `requestStreamSettings`
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarOhrData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startOhrStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarOhrData>
    
    /// Start OHR (Optical heart rate) PPI (Pulse to Pulse interval) stream.
    /// PPI stream is stopped if the connection is closed, error occurs or stream is disposed.
    /// Notice that there is a delay before PPI data stream starts. Requires `polarSensorStreaming` feature.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarPpiData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startOhrPPIStreaming(_ identifier: String) -> Observable<PolarPpiData>
    
    ///  Enables SDK mode. In SDK mode the wider range of capabilities is available for the stream
    ///  than in normal operation mode. SDK mode is only supported by Polar Verity Sense (starting from firmware 1.1.5).
    ///  Requires `polarSensorStreaming` feature.
    ///
    /// - Parameter identifier: Polar device id or device address
    /// - Returns: Completable stream
    ///   - success: if SDK mode is enabled or device is already in SDK mode
    ///   - onError: if SDK mode enable failed
    func enableSDKMode(_ identifier: String) -> Completable
    
    /// Disables SDK mode. SDK mode is only supported by Polar Verity Sense (starting from firmware 1.1.5).
    /// Requires `polarSensorStreaming` feature.
    ///
    /// - Parameter identifier: Polar device id or device address
    /// - Returns: Completable stream
    ///   - success: if SDK mode is disabled or SDK mode was already disabled
    ///   - onError: if SDK mode disable failed
    func disableSDKMode(_ identifier: String) -> Completable
    
    /// Common GAP (Generic access profile) observer
    var observer: PolarBleApiObserver? { get set }
    
    /// Device info observer for DIS (Device information service) and BAS (Battery service) GATT (Generic attributes) client
    var deviceInfoObserver: PolarBleApiDeviceInfoObserver? { get set }
    
    /// Device observer for HR GATT client
    var deviceHrObserver: PolarBleApiDeviceHrObserver? { get set }
    
    /// Bluetooth power state observer
    var powerStateObserver: PolarBleApiPowerStateObserver? { get set }
    
    /// Device features ready observer
    var deviceFeaturesObserver: PolarBleApiDeviceFeaturesObserver? { get set }
    
    /// SDK mode feature available in the device and ready observer
    var sdkModeFeatureObserver: PolarBleApiSdkModeFeatureObserver? { get set }
    
    /// Helper to check if Ble is currently powered
    /// - Returns: current power state
    var isBlePowered: Bool { get }
    
    /// optional logger set to get traces from sdk
    var logger: PolarBleApiLogger? { get set }
    
    /// optional disable or enable automatic reconnection, by default it is enabled
    var automaticReconnection: Bool { get set }
    
    /// optional ccc write callback
    var cccWriteObserver: PolarBleApiCCCWriteObserver? { get set }
}
