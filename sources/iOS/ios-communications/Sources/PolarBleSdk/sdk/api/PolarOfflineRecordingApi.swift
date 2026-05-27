/// Copyright © 2023 Polar Electro Oy. All rights reserved.

import Foundation

/// Offline recording API.
///
/// Offline recording makes it possible to record `PolarBleApi.PolarDeviceDataType` data to device memory.
/// With Offline recording the Polar device and phone don't need to be connected all the time, as offline recording continues in Polar device even the BLE disconnects.
///
///  Offline records saved into the device can be encrypted. The  `PolarRecordingSecret` is provided for
///  `startOfflineRecording` and `setOfflineRecordingTrigger` when encryption is wanted.
///  The `PolarRecordingSecret` with same key must be provided in `getOfflineRecord` to correctly
///  decrypt the data in the device.
///
/// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
///
/// Note, offline recording is supported in Polar Verity Sense device (starting from firmware version 2.1.0)
///
public protocol PolarOfflineRecordingApi {
    ///  Get the data types available in this device for offline recording
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: set of available offline recording data types in this device
    /// - Throws: `PolarErrors` for possible errors invoked
    func getAvailableOfflineRecordingDataTypes(_ identifier: String) async throws -> Set<PolarDeviceDataType>
    
    ///  Request the offline recording settings available in current operation mode. This request shall be used before the offline recording is started
    ///  to decide currently available settings. The available settings depend on the state of the device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: selected feature from `PolarDeviceDataType`
    /// - Returns: `PolarSensorSetting` with the available settings received from the device
    /// - Throws: `PolarErrors` for possible errors invoked
    func requestOfflineRecordingSettings(_ identifier: String, feature: PolarDeviceDataType) async throws -> PolarSensorSetting
    
    ///  Request all the settings available in the device. The request returns the all capabilities of the requested streaming feature not limited by the current operation mode.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: selected feature from `PolarDeviceDataType`
    /// - Returns: `PolarSensorSetting` with all available settings received from the device
    /// - Throws: `PolarErrors` for possible errors invoked
    func requestFullOfflineRecordingSettings(_ identifier: String, feature: PolarDeviceDataType) async throws -> PolarSensorSetting
    
    /// Get status of offline recordings.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: dictionary mapping each `PolarDeviceDataType` to a `Bool` indicating whether offline recording is currently active for that type
    /// - Throws: `PolarErrors` for possible errors invoked
    func getOfflineRecordingStatus(_ identifier: String) async throws -> [PolarDeviceDataType: Bool]
    
    /// List offline recordings stored in the device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: `AsyncThrowingStream` emitting `PolarOfflineRecordingEntry` values as they are found, or throwing an error
    func listOfflineRecordings(_ identifier: String) -> AsyncThrowingStream<PolarOfflineRecordingEntry, Error>
    
    /// Fetch recjiöording from the device.
    ///
    /// Note, the fetching of the recording may take several seconds if the recording is big.
    /// Note, if a faulty data block is encountered while parsing offline data from device that particular data block will be discarded. This will lead to gaps in the data.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - entry: the offline recording to be fetched
    ///   - secret: if the secret is provided in `startOfflineRecording` or `setOfflineRecordingTrigger` then the same secret must be provided when fetching the offline record
    /// - Returns: `PolarOfflineRecordingData` containing the fetched offline recording data
    /// - Throws: `PolarErrors` for possible errors invoked
    func getOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret?) async throws -> PolarOfflineRecordingData
    
    /// Fetch recording from the device with progress updates.
    ///
    /// Note, the fetching of the recording may take several seconds if the recording is big.
    /// Note, if a faulty data block is encountered while parsing offline data from device that particular data block will be discarded. This will lead to gaps in the data.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - entry: the offline recording to be fetched
    ///   - secret: if the secret is provided in `startOfflineRecording` or `setOfflineRecordingTrigger` then the same secret must be provided when fetching the offline record
    /// - Returns: `AsyncThrowingStream` emitting `PolarOfflineRecordingResult` values containing either progress updates or the complete recording data, or throwing an error
    func getOfflineRecordWithProgress(_ identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret?) -> AsyncThrowingStream<PolarOfflineRecordingResult, Error>

    /// Fetch number of sub recordings in recording from the device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - entry: the offline recording whose subrecording count will be checked
    /// - Returns: the number of sub recordings in the offline recording
    /// - Throws: `PolarErrors` for possible errors invoked
    @available(*, deprecated, message:  "Getting subrecordings has been deprecated. Use getOfflineRecord to get full recording instead.")
    func getSubRecordingCount(identifier: String, entry: PolarOfflineRecordingEntry) async throws -> Int

    /// List split offline recordings stored in the device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: `AsyncThrowingStream` emitting `PolarOfflineRecordingEntry` values as they are found, or throwing an error
    @available(*, deprecated, message:  "Listing split offline recordings has been deprecated. Use getOfflineRecord to get full recording instead.")
    func listSplitOfflineRecordings(_ identifier: String) -> AsyncThrowingStream<PolarOfflineRecordingEntry, Error>

    /// Fetch split recording from the device.
    ///
    /// Note, the fetching of the recording may take several seconds if the recording is big.
    /// Note, if a faulty data block is encountered while parsing offline data from device that particular data block will be discarded. This will lead to gaps in the data.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - entry: the split offline recording to be fetched
    ///   - secret: if the secret is provided in `startOfflineRecording` or `setOfflineRecordingTrigger` then the same secret must be provided when fetching the offline record
    /// - Returns: `PolarOfflineRecordingData` containing the fetched offline recording data
    /// - Throws: `PolarErrors` for possible errors invoked
    @available(*, deprecated, message:  "Getting split offline records has been deprecated. Use getOfflineRecord to get full recording instead.")
    func getSplitOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret?) async throws -> PolarOfflineRecordingData

    /// Removes offline recording from the device. Empty parent directories are removed up to day directory.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - entry: entry to be removed
    /// - Throws: `PolarErrors` for possible errors invoked
    func removeOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry) async throws

    /// Removes offline recording with all the subrecordings from the device. Empty parent directories are removed up to day directory.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - entry: entry with the path to the offline recording
    /// - Returns: `true` if the offline record and its subrecords were successfully removed
    /// - Throws: `PolarErrors` for possible errors invoked
    @available(*, deprecated, message:  "Use removeOfflineRecord to remove recording including subrecords instead.")
    func removeOfflineRecords(_ identifier: String, entry: PolarOfflineRecordingEntry) async throws -> Bool

    /// Start offline recording.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: the feature to be started
    ///   - settings: optional settings used for offline recording. `PolarDeviceDataType.hr` and `PolarDeviceDataType.ppi` do not require settings
    ///   - secret: if the secret is provided the offline recordings are encrypted in device
    /// - Throws: `PolarErrors` for possible errors invoked
    func startOfflineRecording(_ identifier: String, feature: PolarDeviceDataType, settings: PolarSensorSetting?, secret: PolarRecordingSecret?) async throws
    
    /// Request to stop offline recording.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: the feature to be stopped
    /// - Throws: `PolarErrors` for possible errors invoked
    func stopOfflineRecording(_ identifier: String, feature: PolarDeviceDataType) async throws
    
    /// Sets the offline recording triggers for a given Polar device. The offline recording can be started automatically in the device by setting the triggers.
    /// The changes to the trigger settings will take effect on the next device startup.
    ///
    /// Automatically started offline recording can be stopped by `stopOfflineRecording()`. Also if user switches off the device power,
    /// the offline recording is stopped but starts again once power is switched on and the trigger event happens.
    ///
    /// Trigger functionality can be disabled by setting `PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED`, the already running offline
    /// recording is not stopped by disable.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: Polar device ID
    ///   - trigger: type of trigger to set
    ///   - secret: optional secret; if provided, the offline recordings are encrypted in the device
    /// - Throws: `PolarErrors` for possible errors invoked
    func setOfflineRecordingTrigger(
        _ identifier: String,
        trigger: PolarOfflineRecordingTrigger,
        secret: PolarRecordingSecret?
    ) async throws
    
    /// Retrieves the current offline recording trigger setup in the device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_offline_recording`
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: `PolarOfflineRecordingTrigger` describing the current trigger setup in the device
    /// - Throws: `PolarErrors` for possible errors invoked
    func getOfflineRecordingTriggerSetup(_ identifier: String) async throws -> PolarOfflineRecordingTrigger
}
