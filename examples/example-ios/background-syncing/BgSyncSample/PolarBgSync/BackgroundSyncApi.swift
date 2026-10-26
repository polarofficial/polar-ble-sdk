// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Background sync API for Polar BLE devices.
///
/// Provides two simple methods to start and stop automatic background syncing
/// of selected data types. When enabled, the sync engine:
///
/// 1. Connects to the device and waits for features to become ready
/// 2. Checks First Time Use (FTU) status and initializes if needed
/// 3. Fetches selected data types sequentially, saving each to local storage
/// 4. Optionally deletes synced data from the device (respecting retention)
/// 5. Listens for device-to-host notifications to trigger re-sync
/// 6. Automatically reconnects after BLE disconnections
public protocol BackgroundSyncApi {

    /// Start background syncing for the specified device and data types.
    func startSync(
        deviceId: String,
        dataTypes: Set<SyncableDataType>,
        config: SyncConfig,
        callback: SyncCallbackDelegate
    )

    /// Stop background syncing for the specified device.
    func stopSync(deviceId: String)

    /// Update the set of data types to sync for an active session.
    /// Takes effect on the next sync cycle.
    func updateDataTypes(deviceId: String, dataTypes: Set<SyncableDataType>)

    /// Check whether background sync is currently active for a device.
    func isSyncing(deviceId: String) -> Bool

    /// Get the current connection state for a device.
    func getConnectionState(deviceId: String) -> ConnectionState

    /// Trigger an immediate sync cycle. Requires `startSync` to have been called first.
    func triggerSyncNow(deviceId: String)

    /// Check whether a sync session exists for the given device.
    /// Returns `true` if `startSync` was called and `stopSync` has not yet been called.
    func hasActiveSession(deviceId: String) -> Bool

    /// Replace the callback for an existing sync session. Use this when the UI
    /// layer is recreated and needs to re-attach to an engine session that is
    /// still running in the background.
    ///
    /// - Returns: `true` if the session exists and the callback was updated.
    @discardableResult
    func updateCallback(deviceId: String, callback: SyncCallbackDelegate) -> Bool

    /// Shut down the sync engine and release all resources.
    func shutdown()

    /// Ensure the device is connected if a sync session exists. If disconnected, attempts to reconnect.
    /// No-op if no session exists for the device.
    func ensureConnected(deviceId: String)
}

/// API for reading synced data from local storage and managing retention.
public protocol LocalDataApi {

    /// Get all locally synced data entries for a device/type within a date range.
    func getSyncedData(
        deviceId: String,
        dataType: SyncableDataType,
        fromDate: Date?,
        toDate: Date?
    ) -> [SyncedDataEntry]

    /// Read the raw JSON content of a synced data file.
    func readSyncedData(entry: SyncedDataEntry) -> String?

    /// Get the root directory path for a device's synced data.
    func getDeviceDataPath(deviceId: String) -> String

    /// Configure automatic local data retention.
    func setLocalRetention(config: LocalRetentionConfig?)

    /// Get the current local retention configuration.
    func getLocalRetention() -> LocalRetentionConfig?

    /// Manually trigger a local data purge.
    @discardableResult
    func purgeExpiredData() -> Int

    /// Delete all locally synced data for a device.
    func deleteAllDeviceData(deviceId: String)
}
