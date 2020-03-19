/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import RxSwift

/// recording interval in seconds for H10
public enum RecordingInterval: Int {
    case interval_1s = 1
    case interval_5s = 5
}

/// sample type for H10 recording
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
    /// polarFileTransfer enables file transfer client to list and read stored exercises,
    /// H10 recording start, stop and status. Set local time to device.
    case polarFileTransfer = 16
    /// allFeatures enables all features available
    case allFeatures = 0xff
}

/// Polar device info
///
///     - deviceId = polar device id or UUID for 3rd party sensors
///     - rssi = RSSI (Received Signal Strenght Indicator) value from advertisement
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
///     - timestamp: Last sample timestamp in nanoseconds. Default epoch is 1.1.2000
///     - samples: ecg sample in µVolts
public typealias PolarEcgData = (timeStamp: UInt64,samples: [Int32])

/// Polar acc data
///
///     - Timestamp: Last sample timestamp in nanoseconds. Default epoch is 1.1.2000 for H10.
///     - samples: Acceleration samples list x,y,z in millig signed value
public typealias PolarAccData = (timeStamp: UInt64,samples: [(x: Int32,y: Int32,z: Int32)])

/// Polar ppg data
///
///     - Timestamp: Last sample timestamp in nanoseconds.
///     - samples: PPG samples list ppg0,ppg1,ppg2,ambient signed value
public typealias PolarPpgData = (timeStamp: UInt64,samples: [(ppg0: Int32,ppg1: Int32,ppg2: Int32,ambient: Int32)])

/// Polar ppi data
///           - timestamp N/A always 0
///           - hr in BPM,
///           - ppInMs in milliseconds,
///           - ppErrorEstimate in milliseconds,
///           - blockerBit 1 = if PP measurement was invalid due to acceleration or other reason
///           - skinContactStatus 0 = if the device detects poor or no contact with the skin, the Sensor
///           - Contact Status bit (bit 1 of the PP Flags field)
public typealias PolarPpiData = (timeStamp: UInt64,samples: [(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)])

public typealias PolarBiozData = (timeStamp: UInt64,samples: [Int32])

/// Polar exercise entry
///
///     - path: Resource location in the device,
///     - date: Entry date and time. Only OH1 supports date and time
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
    
    /// set local time to device, requires ftp to be ready
    ///
    /// - Parameters:
    ///   - identifier: polar device id or UUID
    ///   - time: time to set
    ///   - zone: time zone to set
    /// - Returns: Completable stream
    ///   - success: when time has been set to device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func setLocalTime(_ identifier: String, time: Date, zone: TimeZone) -> Completable
    
    /// request start recording, only H10 supported, requires ftp to be ready
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - exerciseId: unique identifier for for exercise entry length from 1-64 bytes
    ///   - interval: interval to be used
    ///   - sampleType: sample type to be used
    /// - Returns: Completable stream
    ///   - success: recording started
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startRecording(_ identifier: String, exerciseId: String, interval: RecordingInterval, sampleType: SampleType) -> Completable
    
    /// request stop for current recording, only H10 supported, requires ftp to be ready
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Returns: Completable stream
    ///   - success: recording stopped
    ///   - onError: see `PolarErrors` for possible errors invoked
    func stopRecording(_ identifier: String) -> Completable
    
    /// request current recording status, only H10 supported, requires ftp to be ready
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    /// - Returns: Single stream
    ///   - success: see `PolarRecordingStatus`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestRecordingStatus(_ identifier: String) -> Single<PolarRecordingStatus>
    
    /// Start listening to broadcasts from one or more Polar devices
    ///
    /// - Parameter identifiers: list of Polar device ids, or nil for a any device
    /// - Returns: Observable stream
    func startListenForPolarHrBroadcasts(_ identifiers: Set<String>?) -> Observable<PolarHrBroadcastData>
    
    /// request ecg settings available
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: Single stream
    ///   - success: once after settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestEcgSettings(_ identifier: String) -> Single<PolarSensorSetting>

    /// request acc settings available
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: Single stream
    ///   - success: once after settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestAccSettings(_ identifier: String) -> Single<PolarSensorSetting>

    /// request ppg settings available
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: Single stream
    ///   - success: once after settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestPpgSettings(_ identifier: String) -> Single<PolarSensorSetting>

    func requestBiozSettings(_ identifier: String) -> Single<PolarSensorSetting>

    /// Starts the ECG (Electrocardiography) stream.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarEcgData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startEcgStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarEcgData>

    /// Start ACC (accelerometer) stream.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarAccData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startAccStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarAccData>

    /// Start an OHR (optical heart rate) PPG (photoplethysmography) stream.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarPpgData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startOhrPPGStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarPpgData>
    
    /// Start PPI stream. Notice when using OH1 there is a delay before actual
    /// PPI measurement starts and timestamp produced to PolarPpiData is 0
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarPpiData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startOhrPPIStreaming(_ identifier: String) -> Observable<PolarPpiData>
    
    func startBiozStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarBiozData>

    /// Api for fetching stored exercises from Polar OH1/H10 device
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    /// - Returns: Observable stream
    ///   - onNext: see `PolarExerciseEntry`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func fetchStoredExerciseList(_ identifier: String) -> Observable<PolarExerciseEntry>
    
    /// Api for fetching a single exersice from Polar OH1/H10 device
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - entry: single exercise entry to be fetched
    /// - Returns: Single stream
    ///   - success: invoked after exercise data has been fetched from the device. see `PolarExerciseEntry`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func fetchExercise(_ identifier: String, entry: PolarExerciseEntry) -> Single<PolarExerciseData>
    
    /// Remove single exercise from device
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - entry: single exercise entry to be removed
    /// - Returns: Completable stream
    ///   - complete: entry successfully removed
    ///   - onError: see `PolarErrors` for possible errors invoked
    func removeExercise(_ identifier: String, entry: PolarExerciseEntry) ->Completable
    
    /// Common GAP (Generic access profile) observer
    var observer: PolarBleApiObserver? { get set }
    
    /// Device info observer for DIS (Device information servive) and BAS (Battery service) GATT (Generic attributes) client
    var deviceInfoObserver: PolarBleApiDeviceInfoObserver? { get set }

    /// Device observer for HR GATT client
    var deviceHrObserver: PolarBleApiDeviceHrObserver? { get set }
    
    /// Bluetooth power state observer
    var powerStateObserver: PolarBleApiPowerStateObserver? { get set }
    
    /// device features ready observer
    var deviceFeaturesObserver: PolarBleApiDeviceFeaturesObserver? { get set }
    
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
