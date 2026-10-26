// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Callback protocol for background sync events.
/// All callbacks deliver raw structured data — no UI text formatting is applied by the library.
public protocol SyncCallbackDelegate: AnyObject {
    func onSyncStarted(deviceId: String)
    /// Called just before fetching begins for a specific data type.
    func onDataTypeStarted(deviceId: String, dataType: SyncableDataType)
    func onDataSynced(result: SyncResult)
    func onSyncCompleted(deviceId: String)
    func onSyncSummary(summary: SyncSummary)
    func onSyncError(deviceId: String, error: SyncError)
    /// Called when the first offline recording data type begins fetching.
    func onOfflineRecordingGroupStarted(deviceId: String)
    /// Called after the last offline recording data type finishes fetching.
    func onOfflineRecordingGroupCompleted(deviceId: String)
    func onConnectionStateChanged(deviceId: String, state: ConnectionState)
    /// Optional diagnostic messages for debugging (BLE power state, SDK events, etc.)
    func onDiagnosticMessage(message: String)
    /// Battery level received from device (0-100%).
    func onBatteryLevelReceived(deviceId: String, level: UInt)
    /// Called when device data cleanup (deletion of old synced data types).
    func onDeviceCleanupStarted(deviceId: String)
    func onDeviceCleanupCompleted(deviceId: String, deletedTypes: Int)
}

/// Default implementations for optional callbacks.
public extension SyncCallbackDelegate {
    func onDataTypeStarted(deviceId: String, dataType: SyncableDataType) {}
    func onOfflineRecordingGroupStarted(deviceId: String) {}
    func onOfflineRecordingGroupCompleted(deviceId: String) {}
    func onDeviceCleanupStarted(deviceId: String) {}
    func onDeviceCleanupCompleted(deviceId: String, deletedTypes: Int) {}
}
