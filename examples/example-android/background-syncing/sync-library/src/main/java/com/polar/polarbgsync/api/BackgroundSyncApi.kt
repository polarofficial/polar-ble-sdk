// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.api

import android.content.IntentSender

/**
 * Background sync API for Polar BLE devices.
 *
 * Provides two simple methods to start and stop automatic background syncing
 * of selected data types from a Polar device. When enabled, the sync engine:
 *
 * 1. Connects to the device and waits for features to become ready
 * 2. Checks First Time Use (FTU) status and performs initialization if needed
 * 3. Fetches selected data types sequentially, saving each to local storage
 * 4. Optionally deletes synced data from the device (respecting retention)
 * 5. Listens for device-to-host notifications to trigger re-sync when new data appears
 * 6. Automatically reconnects and resumes syncing after BLE disconnections
 *
 * Usage:
 * ```
 * val syncApi = PolarBackgroundSync.create(context)
 * syncApi.startSync(
 *     deviceId = "B5F8B5C3",
 *     dataTypes = setOf(SyncableDataType.STEPS, SyncableDataType.SLEEP),
 *     config = SyncConfig(autoDeleteFromDevice = true),
 *     callback = mySyncCallback
 * )
 * // ... later
 * syncApi.stopSync("B5F8B5C3")
 * ```
 */
interface BackgroundSyncApi {

    /**
     * Start background syncing for the specified device and data types.
     *
     * The sync engine will connect to the device, check FTU status (performing
     * initialization with default values if needed), and begin fetching data.
     * When new data is detected on the device, it is automatically synced.
     * BLE disconnections are handled transparently — the engine reconnects
     * and resumes syncing from where it left off.
     *
     * @param deviceId Polar device ID (e.g. "B5F8B5C3") or BT address
     * @param dataTypes Set of data types to sync
     * @param config Sync configuration (auto-delete, retention days)
     * @param callback Callback for sync lifecycle events
     */
    fun startSync(
        deviceId: String,
        dataTypes: Set<SyncableDataType>,
        config: SyncConfig = SyncConfig(),
        callback: SyncCallback
    )

    /**
     * Update the set of data types to sync for an active session.
     * Takes effect on the next sync cycle.
     *
     * @param deviceId Polar device ID or BT address
     * @param dataTypes New set of data types to sync
     */
    fun updateDataTypes(deviceId: String, dataTypes: Set<SyncableDataType>)

    /**
     * Stop background syncing for the specified device.
     * Disconnects from the device and cancels all pending operations.
     *
     * @param deviceId Polar device ID or BT address
     */
    fun stopSync(deviceId: String)

    /**
     * Stop syncing and remove the device's CDM association and persisted address.
     * Does not delete locally synced data or the system Bluetooth bond.
     * Throws [IllegalStateException] if association removal could not be confirmed.
     */
    fun forgetDevice(deviceId: String)

    /**
     * Resolve a CDM MAC address to the saved sync identifier. Returns the address
     * unchanged if no mapping is known (including sessions started with a MAC).
     */
    fun getDeviceIdForCompanionAddress(address: String): String

    fun isCompanionDeviceAssociated(deviceId: String): Boolean

    /**
     * Explicit fallback for a previously connected device whose automatic SDK
     * association was skipped. Stop sync before calling; the host launches consent
     * with an Activity Result launcher. A null completion error means success.
     */
    fun associateCompanionDevice(
        deviceId: String,
        onConsentRequest: (IntentSender) -> Unit,
        onFinished: (error: String?) -> Unit
    )

    /** Call when the host's association consent Activity returns cancellation. */
    fun cancelCompanionAssociation(deviceId: String)

    /**
     * Check whether background sync is currently active for a device.
     */
    fun isSyncing(deviceId: String): Boolean

    /**
     * Get the current connection state for a device.
     */
    fun getConnectionState(deviceId: String): ConnectionState

    /**
     * Trigger an immediate sync cycle for the device, regardless of
     * whether new data has been detected. Useful for manual "refresh" actions.
     * Requires [startSync] to have been called first.
     */
    fun triggerSyncNow(deviceId: String)

    /**
     * Check whether a sync session exists for the given device.
     * Returns `true` if [startSync] was called and [stopSync] has not yet been called.
     */
    fun hasActiveSession(deviceId: String): Boolean

    /**
     * Replace the callback for an existing sync session. Use this when the UI
     * layer is recreated (e.g. Activity/ViewModel recreation) and needs to
     * re-attach to an engine session that is still running in the background.
     *
     * @return `true` if the session exists and the callback was updated
     */
    fun updateCallback(deviceId: String, callback: SyncCallback): Boolean

    /**
     * Shut down the sync engine and release all resources.
     * Must be called when the host application is being destroyed.
     */
    fun shutdown()

    /**
     * Ensure the device is connected. If not, attempts to connect.
     * No-op if already connected or connecting.
     */
    fun ensureConnected(deviceId: String)
}
