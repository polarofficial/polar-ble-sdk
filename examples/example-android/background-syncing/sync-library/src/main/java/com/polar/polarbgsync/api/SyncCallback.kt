// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.api

/**
 * Callback interface for background sync events.
 * Implement this to receive notifications about the sync process.
 * All callbacks deliver raw structured data — no UI text formatting is applied by the library.
 */
interface SyncCallback {
    /** Called when a sync cycle starts for the given device. */
    fun onSyncStarted(deviceId: String)

    /** Called just before fetching begins for a specific data type. */
    fun onDataTypeStarted(deviceId: String, dataType: SyncableDataType) {}

    /** Called when a specific data type/date has been synced and saved locally. */
    fun onDataSynced(result: SyncResult)

    /** Called when a full sync cycle completes successfully. */
    fun onSyncCompleted(deviceId: String)

    /** Called with a summary of the completed sync cycle. */
    fun onSyncSummary(summary: SyncSummary) {}

    /** Called when a sync error occurs. The sync may continue with other data types. */
    fun onSyncError(deviceId: String, error: SyncError)

    /**
     * Called when the first offline recording data type begins fetching.
     * All offline data types are fetched as a group; this fires once at group start.
     */
    fun onOfflineRecordingGroupStarted(deviceId: String) {}

    /**
     * Called after the last offline recording data type finishes fetching.
     * Fires once at group end, after the final [onDataSynced] for offline types.
     */
    fun onOfflineRecordingGroupCompleted(deviceId: String) {}

    /** Called when the device connection state changes. */
    fun onConnectionStateChanged(deviceId: String, state: ConnectionState)

    /** Called when a battery level update is received from the device. */
    fun onBatteryLevelReceived(deviceId: String, level: Int) {}

    /** Called when a battery charging status update is received from the device. */
    fun onBatteryChargingStatusReceived(deviceId: String, chargingState: String) {}

    /** Called when device data cleanup (deletion of old synced data) starts. */
    fun onDeviceCleanupStarted(deviceId: String) {}

    /** Called when device data cleanup completes. */
    fun onDeviceCleanupCompleted(deviceId: String, deletedTypes: Int) {}

    /** Optional diagnostic messages for debugging (BLE state, SDK events, etc.) */
    fun onDiagnosticMessage(message: String) {}
}
