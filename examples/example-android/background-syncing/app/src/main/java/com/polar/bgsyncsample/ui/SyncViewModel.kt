// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.ui

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import com.polar.bgsyncsample.log.SyncLogStore
import com.polar.bgsyncsample.service.BgSyncForegroundService
import com.polar.bgsyncsample.service.SavedSyncSessions
import com.polar.polarbgsync.api.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class DeviceSyncStatus { NONE, SYNCING, CLEANUP, SUCCESS, ERROR }

data class DeviceSyncInfo(
    val lastSyncTime: LocalDateTime? = null,
    val status: DeviceSyncStatus = DeviceSyncStatus.NONE
)

data class BatteryInfo(
    val level: Int,
    val receivedAt: LocalDateTime = LocalDateTime.now()
)

data class SyncUiState(
    val deviceIds: List<String> = emptyList(),
    val deviceIdInput: String = "",
    val connectionStates: Map<String, ConnectionState> = emptyMap(),
    val deviceSyncInfo: Map<String, DeviceSyncInfo> = emptyMap(),
    val batteryLevels: Map<String, BatteryInfo> = emptyMap(),
    val isSyncing: Boolean = false,
    val enabledDataTypes: Set<SyncableDataType> = SyncableDataType.entries.toSet(),
    val autoDeleteFromDevice: Boolean = false,
    val deviceRetentionDays: Int = 2,
    val offlineRecordingRetentionDays: Int = 0,
    val syncIntervalMinutes: Int = 0,
    val localRetentionDays: Int = 30,
    val localRetentionEnabled: Boolean = false,
    val syncLog: List<SyncLogStore.DisplayLogEntry> = emptyList(),
    val syncOnForeground: Boolean = true,
    val failedSyncDevice: String? = null,
    val companionAssociatedDevices: Set<String> = emptySet(),
    val companionAssociationDeviceId: String? = null
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val application: Application,
    private val syncApi: BackgroundSyncApi,
    private val localDataApi: LocalDataApi
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SyncViewModel"
        private const val PREFS_NAME = "bg_sync_prefs"
        private const val KEY_DEVICE_IDS = "saved_device_ids"
        private const val KEY_PREVIOUSLY_CONNECTED = "previously_connected"
        private const val KEY_ENABLED_DATA_TYPES = "enabled_data_types"
        private const val KEY_AUTO_DELETE = "auto_delete_from_device"
        private const val KEY_DEVICE_RETENTION_DAYS = "device_retention_days"
        private const val KEY_OFFLINE_RECORDING_RETENTION_DAYS = "offline_recording_retention_days"
        private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        private const val KEY_LOCAL_RETENTION_DAYS = "local_retention_days"
        private const val KEY_LOCAL_RETENTION_ENABLED = "local_retention_enabled"
        private const val KEY_SYNC_ON_FOREGROUND = "sync_on_foreground"
        private const val SYNC_SUMMARY_CHANNEL_ID = "polar_sync_summary_channel"
        private const val MAX_VISIBLE_NOTIFICATIONS = 25 // Max notifications per app on Android
        private var nextNotificationId = 2000
        private val activeNotificationIds = ArrayDeque<Int>()
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val savedSessions = SavedSyncSessions(prefs)
    private val logStore = SyncLogStore.getInstance(application)

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    /** Devices that were just added and haven't completed their first sync yet. */
    private val pendingFirstSync = mutableSetOf<String>()

    // ── Per-device accumulators: track item counts between onDataTypeStarted calls ──
    private val currentSyncDataType = mutableMapOf<String, SyncableDataType>()
    private val currentSyncTypeItems = mutableMapOf<String, Int>()
    private val currentSyncTypeBytes = mutableMapOf<String, Long>()
    private val currentSyncTotalBytes = mutableMapOf<String, Long>()
    private val currentOfflineGroupBytes = mutableMapOf<String, Long>()
    private val pendingOfflineGroupEnd = mutableSetOf<String>()
    private val deviceChargingStates = mutableMapOf<String, String>()
    private val lastPlogBatteryParamsByDevice = mutableMapOf<String, String>()

    private fun logBatterySnapshot(deviceId: String) {
        val phoneBat = getPhoneBatteryLevel()
        val phoneCharging = getPhoneChargingState()
        val deviceBat = _uiState.value.batteryLevels[deviceId]?.level
        val deviceCharging = deviceChargingStates[deviceId] ?: "UNKNOWN"

        if (phoneBat < 0 || deviceBat == null || deviceCharging.uppercase() == "UNKNOWN") {
            return
        }

        val params = "phone=$phoneBat phone_charging=$phoneCharging device=$deviceBat device_charging=$deviceCharging"
        if (lastPlogBatteryParamsByDevice[deviceId] == params) {
            return
        }
        lastPlogBatteryParamsByDevice[deviceId] = params
        addPlog(deviceId, "BAT", params)
    }

    private fun closeCurrentDataType(deviceId: String) {
        val previous = currentSyncDataType.remove(deviceId) ?: return
        val itemCount = currentSyncTypeItems.remove(deviceId) ?: 0
        val byteCount = currentSyncTypeBytes.remove(deviceId) ?: 0L
        val event = if (previous.isOfflineRecording) "SYNC_OFFLINE_${previous.name}_END" else "SYNC_${previous.name}_END"
        addPlog(deviceId, event, "items=$itemCount bytes=$byteCount")
        if (previous.isOfflineRecording && pendingOfflineGroupEnd.remove(deviceId)) {
            val groupBytes = currentOfflineGroupBytes.remove(deviceId) ?: byteCount
            addPlog(deviceId, "SYNC_OFFLINE_END", "bytes=$groupBytes")
        }
    }

    private val syncCallback = object : SyncCallback {
        override fun onSyncStarted(deviceId: String) {
            currentSyncDataType.remove(deviceId)
            currentSyncTypeItems.remove(deviceId)
            currentSyncTypeBytes.remove(deviceId)
            currentSyncTotalBytes[deviceId] = 0L
            currentOfflineGroupBytes[deviceId] = 0L
            pendingOfflineGroupEnd.remove(deviceId)
            addPlog(deviceId, "SYNC_START")
            logBatterySnapshot(deviceId)
            _uiState.update { state ->
                val info = state.deviceSyncInfo[deviceId] ?: DeviceSyncInfo()
                state.copy(deviceSyncInfo = state.deviceSyncInfo + (deviceId to info.copy(status = DeviceSyncStatus.SYNCING)))
            }
        }

        override fun onDataTypeStarted(deviceId: String, dataType: SyncableDataType) {
            // Close the previous type with its accumulated count
            closeCurrentDataType(deviceId)
            currentSyncDataType[deviceId] = dataType
            currentSyncTypeItems[deviceId] = 0
            currentSyncTypeBytes[deviceId] = 0L
            val event = if (dataType.isOfflineRecording) "SYNC_OFFLINE_${dataType.name}_START" else "SYNC_${dataType.name}_START"
            addPlog(deviceId, event)
        }

        override fun onDataSynced(result: SyncResult) {
            currentSyncTypeItems[result.deviceId] = (currentSyncTypeItems[result.deviceId] ?: 0) + 1
            currentSyncTypeBytes[result.deviceId] = (currentSyncTypeBytes[result.deviceId] ?: 0L) + result.sizeBytes
            currentSyncTotalBytes[result.deviceId] = (currentSyncTotalBytes[result.deviceId] ?: 0L) + result.sizeBytes
            if (currentSyncDataType[result.deviceId]?.isOfflineRecording == true) {
                currentOfflineGroupBytes[result.deviceId] = (currentOfflineGroupBytes[result.deviceId] ?: 0L) + result.sizeBytes
            }
        }

        override fun onSyncCompleted(deviceId: String) {
            // Close the last data type
            closeCurrentDataType(deviceId)
            val totalBytes = currentSyncTotalBytes.remove(deviceId) ?: 0L
            addPlog(deviceId, "SYNC_END", "bytes=$totalBytes")
            logBatterySnapshot(deviceId)
            pendingFirstSync.remove(deviceId)
            _uiState.update { state ->
                val info = DeviceSyncInfo(lastSyncTime = LocalDateTime.now(), status = DeviceSyncStatus.SUCCESS)
                state.copy(deviceSyncInfo = state.deviceSyncInfo + (deviceId to info))
            }
            currentOfflineGroupBytes.remove(deviceId)
        }

        override fun onOfflineRecordingGroupStarted(deviceId: String) {
            addPlog(deviceId, "SYNC_OFFLINE_START")
        }

        override fun onOfflineRecordingGroupCompleted(deviceId: String) {
            if (currentSyncDataType[deviceId]?.isOfflineRecording == true) {
                pendingOfflineGroupEnd.add(deviceId)
            } else {
                val groupBytes = currentOfflineGroupBytes.remove(deviceId) ?: 0L
                addPlog(deviceId, "SYNC_OFFLINE_END", "bytes=$groupBytes")
            }
        }

        override fun onSyncSummary(summary: SyncSummary) {
            val breakdown = summary.perTypeStats.entries
                .joinToString(",") { (type, stats) -> "${type.name}:${stats.items}:${stats.bytes}" }
            addPlog(summary.deviceId, "SYNC_SUMMARY",
                "items=${summary.totalItems} bytes=${summary.totalBytes} duration=${summary.durationSeconds} breakdown=$breakdown")
            postSyncNotification(summary)
            val isOffline = summary.perTypeStats.keys.any { it.isOfflineRecording }
            val label = if (isOffline) "Offline recordings" else "Sync complete"
            addPlog(summary.deviceId, "NOTIFICATION_POSTED",
                "title=$label [${summary.deviceId}] msg=${summary.totalItems} items, ${formatBytes(summary.totalBytes)} in ${summary.durationSeconds}s")
        }

        override fun onSyncError(deviceId: String, error: SyncError) {
            val typeName = error.dataType?.name ?: "general"
            addPlog(deviceId, "SYNC_ERROR", "type=$typeName msg=${error.message}")
            _uiState.update { state ->
                val info = state.deviceSyncInfo[deviceId] ?: DeviceSyncInfo()
                var updated = state.copy(deviceSyncInfo = state.deviceSyncInfo + (deviceId to info.copy(status = DeviceSyncStatus.ERROR)))
                if (deviceId in pendingFirstSync) {
                    updated = updated.copy(failedSyncDevice = deviceId)
                }
                updated
            }
        }

        override fun onConnectionStateChanged(deviceId: String, state: ConnectionState) {
            addPlog(deviceId, "CONNECTION", "state=$state")
            _uiState.update { current ->
                var updated = current.copy(connectionStates = current.connectionStates + (deviceId to state))
                if (state == ConnectionState.CONNECTED || state == ConnectionState.DISCONNECTED) {
                    val info = updated.deviceSyncInfo[deviceId]
                    if (info?.status == DeviceSyncStatus.SYNCING || info?.status == DeviceSyncStatus.CLEANUP) {
                        updated = updated.copy(
                            deviceSyncInfo = updated.deviceSyncInfo + (deviceId to info.copy(status = DeviceSyncStatus.NONE))
                        )
                    }
                }
                updated
            }
            if (state == ConnectionState.CONNECTED || state == ConnectionState.SYNCING) {
                refreshCompanionAssociation(deviceId)
                saveDeviceIds()
            }
        }

        override fun onBatteryLevelReceived(deviceId: String, level: Int) {
            _uiState.update { current ->
                current.copy(batteryLevels = current.batteryLevels + (deviceId to BatteryInfo(level)))
            }
            logBatterySnapshot(deviceId)
        }

        override fun onBatteryChargingStatusReceived(deviceId: String, chargingState: String) {
            deviceChargingStates[deviceId] = chargingState
            logBatterySnapshot(deviceId)
        }

        override fun onDeviceCleanupStarted(deviceId: String) {
            addPlog(deviceId, "CLEANUP_START")
            _uiState.update { state ->
                val info = state.deviceSyncInfo[deviceId] ?: DeviceSyncInfo()
                state.copy(deviceSyncInfo = state.deviceSyncInfo + (deviceId to info.copy(status = DeviceSyncStatus.CLEANUP)))
            }
        }

        override fun onDeviceCleanupCompleted(deviceId: String, deletedTypes: Int) {
            addPlog(deviceId, "CLEANUP_END", "deleted=$deletedTypes")
            _uiState.update { state ->
                val info = state.deviceSyncInfo[deviceId] ?: DeviceSyncInfo()
                state.copy(deviceSyncInfo = state.deviceSyncInfo + (deviceId to info.copy(status = DeviceSyncStatus.SUCCESS)))
            }
        }

        override fun onDiagnosticMessage(message: String) {
            // Diagnostic messages (e.g. POLARBAT) stored as-is for reference, not shown in UI
            logStore.addEntry(message)
        }
    }

    init {
        val savedIds = savedSessions.deviceIds.sorted()
        val wasConnected = savedSessions.enabled
        val savedTypes = readEnabledDataTypes(prefs.getStringSet(KEY_ENABLED_DATA_TYPES, null))

        val defaults = SyncUiState()
        val restoredState = defaults.copy(
            enabledDataTypes = savedTypes,
            autoDeleteFromDevice = if (prefs.contains(KEY_AUTO_DELETE)) prefs.getBoolean(KEY_AUTO_DELETE, defaults.autoDeleteFromDevice) else defaults.autoDeleteFromDevice,
            deviceRetentionDays = if (prefs.contains(KEY_DEVICE_RETENTION_DAYS)) prefs.getInt(KEY_DEVICE_RETENTION_DAYS, defaults.deviceRetentionDays) else defaults.deviceRetentionDays,
            offlineRecordingRetentionDays = if (prefs.contains(KEY_OFFLINE_RECORDING_RETENTION_DAYS)) prefs.getInt(KEY_OFFLINE_RECORDING_RETENTION_DAYS, defaults.offlineRecordingRetentionDays) else defaults.offlineRecordingRetentionDays,
            syncIntervalMinutes = if (prefs.contains(KEY_SYNC_INTERVAL_MINUTES)) prefs.getInt(KEY_SYNC_INTERVAL_MINUTES, defaults.syncIntervalMinutes) else defaults.syncIntervalMinutes,
            localRetentionDays = if (prefs.contains(KEY_LOCAL_RETENTION_DAYS)) prefs.getInt(KEY_LOCAL_RETENTION_DAYS, defaults.localRetentionDays) else defaults.localRetentionDays,
            localRetentionEnabled = if (prefs.contains(KEY_LOCAL_RETENTION_ENABLED)) prefs.getBoolean(KEY_LOCAL_RETENTION_ENABLED, defaults.localRetentionEnabled) else defaults.localRetentionEnabled,
            syncOnForeground = if (prefs.contains(KEY_SYNC_ON_FOREGROUND)) prefs.getBoolean(KEY_SYNC_ON_FOREGROUND, defaults.syncOnForeground) else defaults.syncOnForeground,
            syncLog = logStore.getDisplayLogEntries()
        )

        if (savedIds.isNotEmpty()) {
            _uiState.update { restoredState.copy(deviceIds = savedIds) }
            savedIds.forEach { refreshCompanionAssociation(it) }
            if (wasConnected) {
                // Check if the sync engine singleton still has active sessions
                // (process survived but Activity/ViewModel was recreated).
                try {
                    val existingSessions = savedIds.filter { syncApi.hasActiveSession(it) }
                    if (existingSessions.isNotEmpty()) {
                        addLog("Re-attaching to ${existingSessions.size} active session(s)")
                        for (deviceId in existingSessions) {
                            syncApi.updateCallback(deviceId, syncCallback)
                            syncApi.updateDataTypes(deviceId, restoredState.enabledDataTypes)
                            val connState = syncApi.getConnectionState(deviceId)
                            _uiState.update { it.copy(
                                connectionStates = it.connectionStates + (deviceId to connState)
                            )}
                        }
                        // Start fresh sessions for any devices that lost their session
                        val newDevices = savedIds - existingSessions.toSet()
                        if (newDevices.isNotEmpty()) {
                            addLog("Reconnecting ${newDevices.size} device(s) with no active session")
                            startSyncForDevices(newDevices)
                        }
                        _uiState.update { it.copy(isSyncing = true) }
                    } else {
                        addLog("Auto-connecting to ${savedIds.size} saved device(s)")
                        startSync()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring sync sessions during init", e)
                    addLog("Error restoring sessions: ${e.message}")
                }
            } else {
                // SDK creation resumes associations even when app sync was explicitly stopped.
                savedIds.forEach { syncApi.stopSync(it) }
            }
        } else {
            _uiState.update { restoredState }
        }
    }

    fun updateDeviceIdInput(text: String) {
        _uiState.update { it.copy(deviceIdInput = text) }
    }

    fun addDevice() {
        val id = _uiState.value.deviceIdInput.trim().uppercase()
        if (id.isBlank()) return
        val isNew = !_uiState.value.deviceIds.contains(id)
        _uiState.update { state ->
            if (state.deviceIds.contains(id)) state.copy(deviceIdInput = "")
            else state.copy(deviceIds = state.deviceIds + id, deviceIdInput = "")
        }
        if (isNew) {
            pendingFirstSync.add(id)
            addLog("[$id] Device added — starting sync")
            startSyncForDevices(listOf(id))
        }
    }

    fun removeDevice(deviceId: String) {
        pendingFirstSync.remove(deviceId)
        deviceChargingStates.remove(deviceId)
        lastPlogBatteryParamsByDevice.remove(deviceId)
        try {
            syncApi.forgetDevice(deviceId)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Could not forget $deviceId", e)
            addLog("[$deviceId] Could not remove companion association: ${e.message}")
            return
        }
        _uiState.update { state ->
            state.copy(
                deviceIds = state.deviceIds - deviceId,
                isSyncing = state.isSyncing && state.deviceIds.size > 1,
                connectionStates = state.connectionStates - deviceId,
                companionAssociatedDevices = state.companionAssociatedDevices - deviceId,
                failedSyncDevice = if (state.failedSyncDevice == deviceId) null else state.failedSyncDevice
            )
        }
        // Persist the updated device list
        val remainingIds = _uiState.value.deviceIds
        if (remainingIds.isEmpty()) {
            savedSessions.save(emptySet(), false)
            BgSyncForegroundService.stop(application)
        } else {
            saveDeviceIds()
        }
    }

    fun retrySyncForFailedDevice() {
        val deviceId = _uiState.value.failedSyncDevice ?: return
        _uiState.update { it.copy(failedSyncDevice = null) }
        addLog("[$deviceId] Retrying sync")
        startSyncForDevices(listOf(deviceId))
    }

    fun dismissSyncFailure() {
        val deviceId = _uiState.value.failedSyncDevice ?: return
        pendingFirstSync.remove(deviceId)
        _uiState.update { it.copy(failedSyncDevice = null) }
    }

    fun toggleDataType(dataType: SyncableDataType) {
        _uiState.update { state ->
            val types = state.enabledDataTypes.toMutableSet()
            if (types.contains(dataType)) types.remove(dataType) else types.add(dataType)
            state.copy(enabledDataTypes = types)
        }
        saveEnabledDataTypes()
        // Push updated data types to all active sync sessions
        if (_uiState.value.isSyncing) {
            val types = _uiState.value.enabledDataTypes
            _uiState.value.deviceIds.forEach { syncApi.updateDataTypes(it, types) }
        }
    }

    fun setAutoDeleteFromDevice(enabled: Boolean) {
        _uiState.update { it.copy(autoDeleteFromDevice = enabled) }
        prefs.edit().putBoolean(KEY_AUTO_DELETE, enabled).apply()
    }

    fun setDeviceRetentionDays(days: Int) {
        _uiState.update { it.copy(deviceRetentionDays = days) }
        prefs.edit().putInt(KEY_DEVICE_RETENTION_DAYS, days).apply()
    }

    fun setOfflineRecordingRetentionDays(days: Int) {
        _uiState.update { it.copy(offlineRecordingRetentionDays = days) }
        prefs.edit().putInt(KEY_OFFLINE_RECORDING_RETENTION_DAYS, days).apply()
    }

    fun setSyncIntervalMinutes(minutes: Int) {
        _uiState.update { it.copy(syncIntervalMinutes = minutes) }
        prefs.edit().putInt(KEY_SYNC_INTERVAL_MINUTES, minutes).apply()
    }

    fun setSyncOnForeground(enabled: Boolean) {
        _uiState.update { it.copy(syncOnForeground = enabled) }
        prefs.edit().putBoolean(KEY_SYNC_ON_FOREGROUND, enabled).apply()
    }

    fun onAppForegrounded() {
        refreshLog()
        // Attempt to connect to any device that is disconnected
        _uiState.value.deviceIds.forEach { deviceId ->
            val state = _uiState.value.connectionStates[deviceId]
            if (state == null || state == ConnectionState.DISCONNECTED) {
                syncApi.ensureConnected(deviceId)
            }
        }
        // Only trigger sync if toggled and syncing
        if (_uiState.value.syncOnForeground && _uiState.value.isSyncing) {
            _uiState.value.deviceIds.forEach { syncApi.triggerSyncNow(it) }
        }
    }

    fun setLocalRetentionEnabled(enabled: Boolean) {
        _uiState.update { it.copy(localRetentionEnabled = enabled) }
        prefs.edit().putBoolean(KEY_LOCAL_RETENTION_ENABLED, enabled).apply()
        updateLocalRetention()
    }

    fun setLocalRetentionDays(days: Int) {
        _uiState.update { it.copy(localRetentionDays = days) }
        prefs.edit().putInt(KEY_LOCAL_RETENTION_DAYS, days).apply()
        updateLocalRetention()
    }

    fun startSync() {
        if (_uiState.value.companionAssociationDeviceId != null) {
            addLog("Wait for companion association to finish before starting sync")
            return
        }
        val state = _uiState.value
        if (state.deviceIds.isEmpty()) {
            addLog("Error: Please add at least one device ID")
            return
        }

        startSyncForDevices(state.deviceIds)
        if (_uiState.value.isSyncing) {
            addLog("Sync started for ${state.deviceIds.size} device(s)")
        }
    }

    private fun startSyncForDevices(deviceIds: List<String>) {
        val state = _uiState.value
        savedSessions.save(state.deviceIds.toSet(), true)
        if (!BgSyncForegroundService.start(application)) {
            savedSessions.save(state.deviceIds.toSet(), state.isSyncing)
            addLog("Error: Background sync service could not be started")
            return
        }
        _uiState.update { it.copy(isSyncing = true) }

        val config = SyncConfig(
            autoDeleteFromDevice = state.autoDeleteFromDevice,
            deviceRetentionDays = state.deviceRetentionDays,
            offlineRecordingRetentionDays = state.offlineRecordingRetentionDays,
            syncIntervalMinutes = state.syncIntervalMinutes
        )

        for (deviceId in deviceIds) {
            if (syncApi.hasActiveSession(deviceId)) {
                syncApi.updateCallback(deviceId, syncCallback)
                syncApi.updateDataTypes(deviceId, state.enabledDataTypes)
                syncApi.ensureConnected(deviceId)
            } else {
                syncApi.startSync(
                    deviceId = deviceId,
                    dataTypes = state.enabledDataTypes,
                    config = config,
                    callback = syncCallback
                )
            }
        }
    }

    fun stopSync() {
        _uiState.update { it.copy(isSyncing = false) }
        saveDeviceIds()
        _uiState.value.deviceIds.forEach { syncApi.stopSync(it) }
        BgSyncForegroundService.stop(application)
        _uiState.update { it.copy(isSyncing = false) }
        addLog("Sync stopped for all devices")
    }

    fun disconnectAndForget() {
        if (_uiState.value.companionAssociationDeviceId != null) {
            addLog("Finish or cancel companion association before forgetting devices")
            return
        }
        _uiState.update { it.copy(isSyncing = false) }
        saveDeviceIds()
        for (deviceId in _uiState.value.deviceIds) {
            try {
                syncApi.forgetDevice(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Could not forget $deviceId", e)
                addLog("[$deviceId] Could not remove companion association: ${e.message}")
                BgSyncForegroundService.stop(application)
                return
            }
        }
        BgSyncForegroundService.stop(application)
        prefs.edit().remove(KEY_DEVICE_IDS).remove(KEY_PREVIOUSLY_CONNECTED).apply()
        _uiState.update {
            it.copy(
                deviceIds = emptyList(),
                deviceIdInput = "",
                isSyncing = false,
                connectionStates = emptyMap(),
                companionAssociatedDevices = emptySet(),
                deviceSyncInfo = emptyMap(),
                syncLog = emptyList()
            )
        }
        addLog("All devices forgotten")
    }

    private fun refreshCompanionAssociation(deviceId: String) {
        val associated = syncApi.isCompanionDeviceAssociated(deviceId)
        _uiState.update {
            it.copy(companionAssociatedDevices =
                if (associated) it.companionAssociatedDevices + deviceId
                else it.companionAssociatedDevices - deviceId)
        }
    }

    fun associateCompanionDevice(deviceId: String, launchConsent: (IntentSender) -> Unit) {
        if (_uiState.value.companionAssociationDeviceId != null) return
        val resumeSync = _uiState.value.isSyncing
        _uiState.update { it.copy(companionAssociationDeviceId = deviceId) }
        stopSync()
        addLog("[$deviceId] Sync paused for companion association")
        val finished: (String?) -> Unit = { error ->
            viewModelScope.launch {
                _uiState.update { it.copy(companionAssociationDeviceId = null) }
                refreshCompanionAssociation(deviceId)
                addLog("[$deviceId] ${error ?: "Companion association approved"}")
                if (resumeSync) startSync()
            }
        }
        try {
            syncApi.associateCompanionDevice(
                deviceId,
                onConsentRequest = { sender ->
                    viewModelScope.launch { launchConsent(sender) }
                },
                onFinished = finished
            )
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot associate $deviceId", e)
            finished(e.message ?: "Cannot start companion association")
        }
    }

    fun cancelCompanionAssociation() {
        _uiState.value.companionAssociationDeviceId?.let { syncApi.cancelCompanionAssociation(it) }
    }

    fun triggerSyncNow() {
        _uiState.value.deviceIds.forEach { syncApi.triggerSyncNow(it) }
        addLog("Manual sync triggered for all devices")
    }

    fun getSyncedData(
        deviceId: String,
        dataType: SyncableDataType,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null
    ): List<SyncedDataEntry> {
        return localDataApi.getSyncedData(deviceId, dataType, fromDate, toDate)
    }

    fun readSyncedData(entry: SyncedDataEntry): String? {
        return localDataApi.readSyncedData(entry)
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT stop syncs or the foreground service here. The sync engine
        // (PolarBackgroundSyncImpl) is a process-scoped singleton that outlives
        // the ViewModel. Android may destroy the Activity (and clear the ViewModel)
        // to reclaim memory while the app is backgrounded — stopping syncs here
        // would kill the BLE connections and foreground service, defeating the
        // purpose of background sync. Syncs are stopped only by explicit user
        // action (stopSync / disconnectAndForget).
    }

    private fun updateLocalRetention() {
        val state = _uiState.value
        if (state.localRetentionEnabled) {
            localDataApi.setLocalRetention(
                LocalRetentionConfig(
                    dataTypes = SyncableDataType.entries.toSet(),
                    maxAgeDays = state.localRetentionDays
                )
            )
        } else {
            localDataApi.setLocalRetention(null)
        }
    }

    fun clearLog() {
        logStore.clear()
        _uiState.update { it.copy(syncLog = emptyList()) }
    }

    fun getAllLogTextForSharing(): String {
        return logStore.getDisplayEntries()
            .asReversed() // Share oldest -> newest for easier reading.
            .joinToString(separator = "\n")
    }

    /**
     * Reload log entries from the persistent store into the UI state.
     * Call when the Activity resumes — picks up entries written by the
     * foreground service while the UI was not visible.
     */
    fun refreshLog() {
        _uiState.update { it.copy(syncLog = logStore.getDisplayLogEntries()) }
    }

    private fun addLog(message: String) {
        logStore.addEntry(message)
        Log.d(TAG, message)
        _uiState.update { state ->
            state.copy(syncLog = logStore.getDisplayLogEntries())
        }
    }

    /** Emit a PLOG structured event and refresh the UI log from the formatted PLOG store. */
    private fun addPlog(deviceId: String, event: String, params: String = "") {
        // Use System.currentTimeMillis() for maximum precision and consistency
        val instant = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
        val ts = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        val plogLine = "PLOG|$ts|$deviceId|$event|$params"
        logStore.addEntry(plogLine)
        // Also write to logcat for real-time monitoring
        Log.i("PLOG", plogLine)
        _uiState.update { state ->
            state.copy(syncLog = logStore.getDisplayLogEntries())
        }
    }

    /** Query phone battery level (0-100, or -1 if unavailable). */
    private fun getPhoneBatteryLevel(): Int {
        val bm = application.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        return bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    private fun getPhoneChargingState(): String {
        val bm = application.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        return if (bm?.isCharging == true) "CHARGING" else "UNPLUGGED"
    }

    // Graph view support
    private var currentGraphData: com.polar.bgsyncsample.ui.graph.LogGraphData? = null

    fun setCurrentGraphData(graphData: com.polar.bgsyncsample.ui.graph.LogGraphData) {
        currentGraphData = graphData
    }

    fun getCurrentGraphData(): com.polar.bgsyncsample.ui.graph.LogGraphData? {
        return currentGraphData
    }

    fun getAllDeviceIds(): List<String> {
        return _uiState.value.deviceIds
    }

    /** Returns raw log messages including machine-readable PLOG lines, for graph parsing. */
    fun getRawLogMessages(): List<String> = logStore.getRawMessages()

    /** Returns raw log messages for a specific device, for device-filtered graph parsing. */
    fun getRawLogMessagesForDevice(deviceId: String): List<String> = logStore.getRawMessagesForDevice(deviceId)

    private fun saveDeviceIds() {
        val ids = _uiState.value.deviceIds.toSet()
        savedSessions.save(ids, _uiState.value.isSyncing)
    }

    private fun saveEnabledDataTypes() {
        val names = _uiState.value.enabledDataTypes.map { it.name }.toSet()
        prefs.edit()
            .putStringSet(KEY_ENABLED_DATA_TYPES, names)
            .apply()
    }

    private fun readEnabledDataTypes(savedNames: Set<String>?): Set<SyncableDataType> {
        val parsed = savedNames
            ?.mapNotNull { name -> SyncableDataType.entries.find { it.name == name } }
            ?.toMutableSet()
            ?: return SyncableDataType.entries.toSet()

        // Migration: old saved selections had only OFFLINE_TEMPERATURE.
        // Keep equivalent behavior by auto-enabling OFFLINE_SKIN_TEMPERATURE too.
        if (parsed.contains(SyncableDataType.OFFLINE_TEMPERATURE)) {
            parsed.add(SyncableDataType.OFFLINE_SKIN_TEMPERATURE)
        }
        // Migration: derived ACC recordings now have a dedicated data type.
        if (parsed.contains(SyncableDataType.OFFLINE_ACC)) {
            parsed.add(SyncableDataType.OFFLINE_DERIVED_ACC)
        }

        return parsed
    }

    private fun postSyncNotification(summary: SyncSummary) {
        val manager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SYNC_SUMMARY_CHANNEL_ID,
                "Sync Summary",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifications when a sync cycle completes" }
            manager.createNotificationChannel(channel)
        }

        val isOffline = summary.perTypeStats.keys.any { it.isOfflineRecording }
        val label = if (isOffline) "Offline recordings" else "Sync complete"
        val dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val startStr = summary.startTime.format(dateFormatter)
        val endStr = summary.endTime.format(dateFormatter)
        val title = "$label [${summary.deviceId}]"

        val lines = mutableListOf<String>()
        lines.add("$startStr → $endStr (${summary.durationSeconds}s)")
        lines.add("Total: ${summary.totalItems} items, ${formatBytes(summary.totalBytes)}")
        for ((type, stats) in summary.perTypeStats) {
            lines.add("${type.name}: ${stats.items} items, ${formatBytes(stats.bytes)}")
        }
        val bigText = lines.joinToString("\n")

        val notification = NotificationCompat.Builder(application, SYNC_SUMMARY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText("${summary.totalItems} items, ${formatBytes(summary.totalBytes)} in ${summary.durationSeconds}s")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setAutoCancel(true)
            .build()

        val id = nextNotificationId++
        activeNotificationIds.addLast(id)
        while (activeNotificationIds.size > MAX_VISIBLE_NOTIFICATIONS) {
            manager.cancel(activeNotificationIds.removeFirst())
        }
        manager.notify(id, notification)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
