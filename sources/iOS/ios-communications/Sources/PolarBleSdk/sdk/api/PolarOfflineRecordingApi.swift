/// Copyright © 2023 Polar Electro Oy. All rights reserved.

import Foundation
import RxSwift

/// API.
public protocol PolarOfflineRecordingApi {
    ///  Get the data types available in this device for offline recording
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: Single stream
    ///   - success: he set of available offline recording data types in this device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func getAvailableOfflineRecordingDataTypes(_ identifier: String) -> Single<Set<DeviceStreamingFeature>>
    
    ///  Request the offline recording settings available in current operation mode. This request shall be used before the offline recording is started
    ///  to decide currently available settings. The available settings depend on the state of the device.
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: selected feature from`DeviceStreamingFeature`
    /// - Returns: Single stream
    ///   - success: once after settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestOfflineRecordingSettings(_ identifier: String, feature: DeviceStreamingFeature) -> Single<PolarSensorSetting>
    
    ///  Request all the settings available in the device. The request returns the all capabilities of the requested streaming feature not limited by the current operation mode.
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: selected feature from`DeviceStreamingFeature`
    /// - Returns: Single stream
    ///   - success: once after settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestFullOfflineRecordingSettings(_ identifier: String, feature: DeviceStreamingFeature) -> Single<PolarSensorSetting>
    
    /// Get status of offline recordings.
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: Single stream
    ///   - success: the dictionary indicating the offline recording status, if the value in dictionary is true the offline recording is currently recording
    ///   - error: see `PolarErrors` for possible errors invoked
    func getOfflineRecordingStatus(_ identifier: String)-> Single<[DeviceStreamingFeature:Bool]>
    
    /// List offline recordings stored in the device.
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: Completable
    ///   - next :  the found offline recording entry
    ///   - completed: the listing completed
    ///   - error: see `PolarErrors` for possible errors invoked
    func listOfflineRecordings(_ identifier: String) -> Observable<PolarOfflineRecordingEntry>
    
    /// Fetch recording from the  device.
    ///
    /// Note, the fetching of the recording may take several seconds if the recording is big.
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - entry:  The offline recording to be fetched
    ///   - secret: If the secret is provided in `startOfflineRecording` or `setOfflineRecordingTrigger` then the same secret must be provided when fetching the offline record
    /// - Returns: Single
    ///   - success :  the offline recording data
    ///   - error: fetch recording request failed. see `PolarErrors` for possible errors invoked
    func getOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret?) -> Single< PolarOfflineRecordingData>
    
    /// Removes offline recording from the device
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - entry: entry to be removed
    /// - Returns: Completable
    ///   - completed :  offline record is removed
    ///   - error:  offline record removal failed, see `PolarErrors` for possible errors invoked
    func removeOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry) -> Completable
    
    /// Start offline recording.
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: the feature to be started
    ///   - settings: optional settings used for offline recording. `DeviceStreamingFeature.hr` and `DeviceStreamingFeature.ppi` do not require settings
    /// - Returns: Completable
    ///   - completed :  offline recording is started successfully
    ///   - error: see `PolarErrors` for possible errors invoked
    func startOfflineRecording(_ identifier: String, feature: DeviceStreamingFeature, settings: PolarSensorSetting?) -> Completable
    
    /// Request to stop offline recording.
    ///
    /// - Parameters:
    ///   - identifier:  polar device id
    ///   - feature: the feature to be stopped
    /// - Returns: Completable
    ///   - completed :  offline recording is stop successfully
    ///   - error: offline recording stop failed. see `PolarErrors` for possible errors invoked
    func stopOfflineRecording(_ identifier: String, feature: DeviceStreamingFeature) -> Completable
}
