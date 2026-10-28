// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import android.content.Context
import android.content.SharedPreferences
import android.content.IntentSender
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.bluetooth.le.ScanFilter
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.polar.polarbgsync.api.*
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarDeviceToHostNotification
import com.polar.sdk.api.PolarCompanionDeviceApi
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import com.polar.sdk.api.model.PolarHealthThermometerData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Core implementation of both [BackgroundSyncApi] and [LocalDataApi].
 * Manages PolarBleApi lifecycle, connection state, and sync orchestration.
 */
internal class PolarBackgroundSyncImpl private constructor(
    private val context: Context
) : BackgroundSyncApi, LocalDataApi {

    companion object {
        private const val TAG = "PolarBgSync"
        private const val PREFS_NAME = "polar_bg_sync"
        private const val COMPANION_ADDRESS_PREFIX = "companion_address_"
        private const val DEFERRED_RETRY_DELAY_MS = 15_000L
        /** Per-data-type fetch timeout (ms). Prevents the sync loop from hanging forever
         *  if the SDK never completes a call. */
        private const val PER_FETCH_TIMEOUT_MS = 120_000L
        /** Delay (ms) after all features are ready before issuing PsFTP commands.
         *  Gives the watch firmware time to fully stabilize its file-transfer session state. */
        private const val POST_FEATURES_READY_DELAY_MS = 3_000L

        /** Maximum number of retries for the FTU-check + sync-init sequence when timeouts occur. */
        private const val MAX_SYNC_INIT_RETRIES = 2
        /** Backoff delay (ms) before retrying after a timeout in FTU check or sync init. */
        private const val SYNC_INIT_RETRY_BACKOFF_MS = 5_000L

        /** Interval between periodic reconnection attempts for disconnected sessions. */
        private const val RECONNECTION_SCAN_RESTART_INTERVAL_MS = 30_000L
        /** Interval between direct connectToDevice() nudges for devices that don't advertise readily. */
        private const val RECONNECTION_NUDGE_INTERVAL_MS = 15_000L

        @Volatile
        private var instance: PolarBackgroundSyncImpl? = null

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
                else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            }
        }

        private fun logSyncSummary(
            label: String, session: SyncSession, summary: SyncSummary,
            startTime: LocalDateTime, endTime: LocalDateTime, durationSeconds: Long
        ) {
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            val sb = StringBuilder()
            sb.appendLine("── $label [${session.deviceId}] ──")
            sb.appendLine("  Start: ${startTime.format(timeFormatter)}  End: ${endTime.format(timeFormatter)}  Duration: ${durationSeconds}s")
            sb.appendLine("  Total: ${summary.totalItems} items, ${formatBytes(summary.totalBytes)}")
            for ((type, stats) in summary.perTypeStats) {
                sb.appendLine("    ${type.name}: ${stats.items} items, ${formatBytes(stats.bytes)}")
            }
            sb.append("──────────────────────────────")
            Log.i(TAG, sb.toString())
        }

        fun getInstance(context: Context): PolarBackgroundSyncImpl {
            return instance ?: synchronized(this) {
                instance ?: PolarBackgroundSyncImpl(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val gson: Gson = GsonBuilder()
        // Note: Save compact JSON; pretty-printing happens on read for consistency across platforms
        .registerTypeAdapter(LocalDate::class.java, object : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
            override fun serialize(src: LocalDate, typeOfSrc: Type, context: JsonSerializationContext) =
                JsonPrimitive(src.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString())

            override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDate {
                val raw = json.asString
                return try {
                    // Backward compatibility with legacy yyyy-MM-dd payloads
                    LocalDate.parse(raw)
                } catch (_: Exception) {
                    try {
                        // New format with offset, e.g. 2026-04-30T00:00:00+03:00
                        java.time.OffsetDateTime.parse(raw).toLocalDate()
                    } catch (_: Exception) {
                        // iOS-like/instant format with Z, e.g. 2026-04-29T21:00:00Z
                        java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                }
            }
        })
        .registerTypeAdapter(LocalDateTime::class.java, object : JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
            override fun serialize(src: LocalDateTime, typeOfSrc: Type, context: JsonSerializationContext) =
                JsonPrimitive(src.atZone(java.time.ZoneId.systemDefault()).toInstant().toString())

            override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDateTime {
                val raw = json.asString
                return try {
                    // New format with UTC instant, e.g. 2026-04-30T10:45:00Z
                    java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                } catch (_: Exception) {
                    try {
                        // Support offset datetime payloads, e.g. 2026-04-30T13:45:00+03:00
                        java.time.OffsetDateTime.parse(raw).toLocalDateTime()
                    } catch (_: Exception) {
                        // Backward compatibility with legacy local datetime payloads
                        LocalDateTime.parse(raw)
                    }
                }
            }
        })
        .create()

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val companionDevices = CompanionDeviceRegistry(
        prefs.all.entries.filter { it.key.startsWith(COMPANION_ADDRESS_PREFIX) }
            .associate {
                val address = it.value
                check(address is String) { "Invalid saved companion address for ${it.key}" }
                it.key.removePrefix(COMPANION_ADDRESS_PREFIX) to address
            }
    )

    private val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        context,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_COMPANION_DEVICE_MANAGEMENT
        )
    )

    private val syncStateTracker = SyncStateTracker(prefs)
    private val localDataStore = LocalDataStore(context, gson, syncStateTracker)
    private val activeSessions = ConcurrentHashMap<String, SyncSession>()
    private val pendingCompanionAssociations = ConcurrentHashMap<String, CompanionAssociationAttempt>()
    private val batteryMonitor = BatteryLevelMonitor(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Sequential connection queue. The SDK's ConnectionHandler serializes BLE connection
     * setup one-at-a-time (parking later requests until an advertisement is detected).
     * Rather than fighting this, we feed devices into connectToDevice() one by one,
     * waiting until each device's connection setup completes (features ready) or times
     * out before starting the next.
     */
    private val connectionQueue = Channel<SyncSession>(Channel.UNLIMITED)

    // ── PolarBleApiCallback ─────────────────────────────────────────────

    private val apiCallback = object : PolarBleApiCallback() {

        override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
            val session = sessionForDevice(polarDeviceInfo) ?: return
            val deviceId = session.deviceId
            rememberCompanionAddress(deviceId, polarDeviceInfo.address)
            session.callback.onDiagnosticMessage(
                "CDM association for $deviceId: ${api.isAssociated(polarDeviceInfo.address)}"
            )
            Log.i(TAG, "Device connected: $deviceId")
            session.state = ConnectionState.CONNECTED
            // Note: readyFeatures is NOT cleared here — it was already cleared in
            // deviceDisconnected(). Clearing here would race with bleSdkFeatureReady()
            // which can fire before deviceConnected on reconnection (cached services).
            session.callback.onConnectionStateChanged(deviceId, ConnectionState.CONNECTED)
        }

        override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
            val session = sessionForDevice(polarDeviceInfo) ?: return
            val deviceId = session.deviceId
            rememberCompanionAddress(deviceId, polarDeviceInfo.address)
            Log.i(TAG, "Device connecting: $deviceId (previous app state=${session.state})")
            session.state = ConnectionState.CONNECTING
            session.callback.onConnectionStateChanged(deviceId, ConnectionState.CONNECTING)
            startConnectionWatchdog(session)
        }

        override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
            val session = sessionForDevice(polarDeviceInfo) ?: return
            val deviceId = session.deviceId
            Log.i(TAG, "Device disconnected: $deviceId (previous app state=${session.state})")
            session.state = ConnectionState.DISCONNECTED
            // Unblock connection queue if this device was being set up
            session.connectionSetupDone?.complete(Unit)
            session.connectionSetupDone = null
            session.connectionWatchdogJob?.cancel()
            session.connectionWatchdogJob = null
            session.sessionScope?.cancel()
            session.sessionScope = null
            session.timedSyncJob?.cancel()
            session.timedSyncJob = null
            session.readyFeatures.clear()
            session.ftuAndSyncTriggered = false
            session.callback.onConnectionStateChanged(deviceId, ConnectionState.DISCONNECTED)
        }

        override fun bleSdkFeatureReady(
            identifier: String,
            feature: PolarBleApi.PolarBleSdkFeature
        ) {
            val session = sessionForIdentifier(identifier) ?: return
            Log.i(TAG, "Feature ready for $identifier: $feature")
            session.readyFeatures.add(feature)
            checkAllFeaturesReady(session)
        }

        override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
            Log.d(TAG, "DIS info: $uuid = $value")
        }

        override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
            Log.d(TAG, "DIS info: ${disInfo.key} = ${disInfo.value}")
        }

        override fun htsNotificationReceived(
            identifier: String,
            data: PolarHealthThermometerData
        ) {
            Log.d(TAG, "HTS notification from $identifier: $data")
        }

        override fun blePowerStateChanged(powered: Boolean) {
            Log.i(TAG, "BLE power state: $powered")
        }

        override fun batteryLevelReceived(identifier: String, level: Int) {
            Log.d(TAG, "Battery level for $identifier: $level%")
            batteryMonitor.updateDeviceBattery(identifier, level)
            sessionForIdentifier(identifier)?.let {
                it.callback.onBatteryLevelReceived(it.deviceId, level)
            }
        }

        override fun batteryChargingStatusReceived(identifier: String, chargingStatus: ChargeState) {
            Log.d(TAG, "Battery charging status for $identifier: $chargingStatus")
            batteryMonitor.updateDeviceChargingState(identifier, chargingStatus)
            sessionForIdentifier(identifier)?.let {
                it.callback.onBatteryChargingStatusReceived(it.deviceId, chargingStatus.name)
            }
        }

        override fun polarCompanionDeviceAppeared(identifier: String) {
            val session = sessionForIdentifier(identifier) ?: return
            Log.i(TAG, "Companion device appeared: ${session.deviceId} ($identifier)")
            session.callback.onDiagnosticMessage("CDM appeared: ${session.deviceId} ($identifier)")
            ensureConnected(session.deviceId)
        }

        override fun polarCompanionDeviceDisappeared(identifier: String) {
            val session = sessionForIdentifier(identifier) ?: return
            Log.i(TAG, "Companion device disappeared: ${session.deviceId} ($identifier)")
            session.callback.onDiagnosticMessage("CDM disappeared: ${session.deviceId} ($identifier)")
        }
    }

    private fun sessionForIdentifier(identifier: String): SyncSession? =
        activeSessions[identifier] ?: activeSessions[companionDevices.resolve(identifier)]

    private fun sessionForDevice(info: PolarDeviceInfo): SyncSession? =
        sessionForIdentifier(info.deviceId) ?: sessionForIdentifier(info.address)

    private fun rememberCompanionAddress(deviceId: String, address: String) {
        companionDevices.remember(deviceId, address)
        prefs.edit().putString(COMPANION_ADDRESS_PREFIX + deviceId, address.uppercase()).apply()
    }

    init {
        api.setApiLogger { message ->
            if (shouldLogSdkMessage(message)) {
                Log.d(TAG, message)
            }
        }
        api.setAutomaticReconnection(true)
        api.setApiCallback(apiCallback)
        // Launch the single connection-queue consumer
        scope.launch { processConnectionQueue() }
        // Continuously scan for disconnected devices coming back into range
        scope.launch { reconnectionScanLoop() }
        // Periodic direct connection nudge + foregroundEntered() for devices that
        // don't advertise readily (e.g. sport watches in power-save mode)
        scope.launch { reconnectionNudgeLoop() }
    }

    // ── BackgroundSyncApi ───────────────────────────────────────────────

    override fun startSync(
        deviceId: String,
        dataTypes: Set<SyncableDataType>,
        config: SyncConfig,
        callback: SyncCallback
    ) {
        if (activeSessions.containsKey(deviceId)) {
            Log.w(TAG, "Sync already active for $deviceId, stopping and restarting")
            stopSync(deviceId)
        }

        val session = SyncSession(
            deviceId = deviceId,
            dataTypes = dataTypes,
            config = config,
            callback = callback,
            sessionScope = null,
            state = ConnectionState.DISCONNECTED,
            readyFeatures = mutableSetOf()
        )
        activeSessions[deviceId] = session

        Log.i(TAG, "Starting sync for $deviceId with types: $dataTypes")
        ensureConnected(deviceId)
    }

    override fun stopSync(deviceId: String) {
        val session = activeSessions.remove(deviceId)
        val address = companionDevices.address(deviceId)
        if (api.isAssociated(address) && !api.stopObservingDevicePresence(address)) {
            val message = "Failed to stop companion presence observation for $deviceId"
            Log.e(TAG, message)
            session?.callback?.onSyncError(deviceId, SyncError(null, message))
        }
        if (session == null) return
        Log.i(TAG, "Stopping sync for $deviceId")
        // Unblock connection queue in case this device is the one being awaited
        session.connectionSetupDone?.complete(Unit)
        session.connectionSetupDone = null
        session.connectionWatchdogJob?.cancel()
        session.connectionWatchdogJob = null
        session.sessionScope?.cancel()
        session.sessionScope = null
        session.timedSyncJob?.cancel()
        session.timedSyncJob = null
        try {
            api.disconnectFromDevice(deviceId)
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting $deviceId", e)
        }
        session.callback.onConnectionStateChanged(deviceId, ConnectionState.DISCONNECTED)
        session.state = ConnectionState.DISCONNECTED
    }

    override fun getDeviceIdForCompanionAddress(address: String): String =
        companionDevices.resolve(address)

    override fun isCompanionDeviceAssociated(deviceId: String): Boolean =
        api.isAssociated(companionDevices.address(deviceId))

    override fun associateCompanionDevice(
        deviceId: String,
        onConsentRequest: (IntentSender) -> Unit,
        onFinished: (error: String?) -> Unit
    ) {
        check(!hasActiveSession(deviceId)) { "Stop sync before requesting companion association" }
        check(api.isCompanionDeviceManagerAvailable()) { "Companion Device Manager is unavailable" }
        val address = companionDevices.address(deviceId)
        check(address.matches(Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))) {
            "Connect to $deviceId once before requesting companion association"
        }
        if (api.isAssociated(address)) {
            onFinished(null)
            return
        }
        val attempt = CompanionAssociationAttempt(onFinished)
        check(pendingCompanionAssociations.putIfAbsent(deviceId, attempt) == null) {
            "Companion association is already pending for $deviceId"
        }

        val request = AssociationRequest.Builder()
            .addDeviceFilter(
                BluetoothLeDeviceFilter.Builder()
                    .setScanFilter(ScanFilter.Builder().setDeviceAddress(address).build())
                    .build()
            )
            .setSingleDevice(true)
            .build()
        val callback = object : PolarCompanionDeviceApi.PolarCompanionDeviceCallback {
            override fun onAssociationSucceeded(macAddress: String) {
                if (attempt.cancelled) {
                    try {
                        api.removeAssociation(macAddress)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Cannot remove late cancelled association for $deviceId", e)
                    }
                    return
                }
                if (pendingCompanionAssociations[deviceId] !== attempt) return
                if (!macAddress.equals(address, ignoreCase = true)) {
                    api.removeAssociation(macAddress)
                    finishCompanionAssociation(deviceId, attempt, "Association returned an unexpected device: $macAddress")
                    return
                }
                rememberCompanionAddress(deviceId, macAddress)
                finishCompanionAssociation(deviceId, attempt, null)
            }

            override fun onError(message: String?) {
                finishCompanionAssociation(deviceId, attempt, message ?: "Companion association failed")
            }
        }
        try {
            api.associate(request, onConsentRequest, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot request companion association for $deviceId", e)
            finishCompanionAssociation(deviceId, attempt, e.message ?: "Companion association permission denied")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid companion association request for $deviceId", e)
            finishCompanionAssociation(deviceId, attempt, e.message ?: "Invalid companion association request")
        }
    }

    private fun finishCompanionAssociation(
        deviceId: String,
        attempt: CompanionAssociationAttempt,
        error: String?,
        cancelled: Boolean = false
    ) {
        if (!attempt.tryFinish(cancelled)) return
        pendingCompanionAssociations.remove(deviceId, attempt)
        if (error != null) Log.w(TAG, "Companion association for $deviceId: $error")
        attempt.onFinished(error)
    }

    override fun cancelCompanionAssociation(deviceId: String) {
        pendingCompanionAssociations[deviceId]?.let {
            finishCompanionAssociation(
                deviceId, it,
                "Companion association cancelled; ordinary BLE sync remains available",
                cancelled = true
            )
        }
    }

    override fun forgetDevice(deviceId: String) {
        check(!pendingCompanionAssociations.containsKey(deviceId)) {
            "Finish or cancel companion association before forgetting $deviceId"
        }
        stopSync(deviceId)
        val address = companionDevices.address(deviceId)
        api.removeAssociation(address)
        check(!api.isAssociated(address)) {
            "Failed to remove companion association for $deviceId ($address)"
        }
        companionDevices.forget(deviceId)
        prefs.edit().remove(COMPANION_ADDRESS_PREFIX + deviceId).apply()
    }

    override fun isSyncing(deviceId: String): Boolean {
        return activeSessions[deviceId]?.state == ConnectionState.SYNCING
    }

    override fun updateDataTypes(deviceId: String, dataTypes: Set<SyncableDataType>) {
        val session = activeSessions[deviceId] ?: return
        Log.i(TAG, "Updating data types for $deviceId: $dataTypes")
        session.dataTypes = dataTypes
    }

    override fun getConnectionState(deviceId: String): ConnectionState {
        return activeSessions[deviceId]?.state ?: ConnectionState.DISCONNECTED
    }

    override fun hasActiveSession(deviceId: String): Boolean {
        return activeSessions.containsKey(deviceId)
    }

    override fun updateCallback(deviceId: String, callback: SyncCallback): Boolean {
        val session = activeSessions[deviceId] ?: return false
        session.callback = callback
        return true
    }

    override fun triggerSyncNow(deviceId: String) {
        val session = activeSessions[deviceId] ?: return
        if (session.state == ConnectionState.CONNECTED || session.state == ConnectionState.SYNCING) {
            Log.i(TAG, "Manual sync trigger for $deviceId")
            executeSyncCycle(session)
        } else if (session.state == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Device $deviceId is disconnected — requesting reconnection (sync will run on connect)")
            ensureConnected(deviceId)
        } else {
            Log.w(TAG, "Cannot trigger sync for $deviceId in state ${session.state}")
        }
    }

    override fun shutdown() {
        Log.i(TAG, "Shutting down PolarBackgroundSync")
        activeSessions.keys.toList().forEach { stopSync(it) }
        pendingCompanionAssociations.keys.toList().forEach { cancelCompanionAssociation(it) }
        scope.cancel()
        api.shutDown()
        instance = null
    }

    // ── LocalDataApi ────────────────────────────────────────────────────

    override fun getSyncedData(
        deviceId: String,
        dataType: SyncableDataType,
        fromDate: LocalDate?,
        toDate: LocalDate?
    ): List<SyncedDataEntry> {
        return localDataStore.listEntries(deviceId, dataType, fromDate, toDate)
    }

    override fun readSyncedData(entry: SyncedDataEntry): String? {
        val fileContent = if (entry.dataType.isOfflineRecording) {
            val bytes = localDataStore.readRawFile(entry.localFilePath) ?: return null
            String(bytes)
        } else {
            localDataStore.readFile(entry.localFilePath) ?: return null
        }
        
        // Pretty-print all JSON for consistent display across platforms
        return try {
            val jsonElement = gson.fromJson(fileContent, com.google.gson.JsonElement::class.java)
            val prettyGson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            prettyGson.toJson(jsonElement)
        } catch (e: Exception) {
            fileContent
        }
    }

    override fun getDeviceDataPath(deviceId: String): String {
        return localDataStore.getDeviceDir(deviceId)
    }

    override fun setLocalRetention(config: LocalRetentionConfig?) {
        localDataStore.setRetentionConfig(config)
    }

    override fun getLocalRetention(): LocalRetentionConfig? {
        return localDataStore.getRetentionConfig()
    }

    override fun purgeExpiredData(): Int {
        return localDataStore.purgeExpired()
    }

    override fun deleteAllDeviceData(deviceId: String) {
        localDataStore.deleteDeviceData(deviceId)
    }

    // ── Connection management ─────────────────────────────────────────

    /**
     * Processes the connection queue sequentially. For each device:
     * 1. Calls connectToDevice() and starts the watchdog
     * 2. Waits until the device reaches feature-ready (CompletableDeferred completes)
     *    or the watchdog timeout fires (deferred also completes)
     * 3. Only then proceeds to the next device in the queue
     *
     * This prevents the SDK's ConnectionHandler from parking devices, which is
     * the primary cause of devices getting stuck in CONNECTING state.
     */
    private suspend fun processConnectionQueue() {
        for (session in connectionQueue) {
            if (activeSessions[session.deviceId] !== session) continue
            if (session.ftuAndSyncTriggered) continue

            Log.i(TAG, "Connection queue: connecting ${session.deviceId}")
            session.connectionSetupDone = CompletableDeferred()
            if (session.ftuAndSyncTriggered) {
                session.connectionSetupDone?.complete(Unit)
                continue
            }
            val waitForAssociation = api.isCompanionDeviceManagerAvailable() &&
                !api.isAssociated(companionDevices.address(session.deviceId))
            // SDK 8.4 may defer GATT for 60s while CDM consent is pending.
            startConnectionWatchdog(session, connectionSetupTimeoutMillis(waitForAssociation))

            try {
                api.connectToDevice(session.deviceId)
                session.connectionSetupDone?.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Connection request failed for ${session.deviceId}", e)
                session.connectionWatchdogJob?.cancel()
                session.state = ConnectionState.DISCONNECTED
                session.callback.onConnectionStateChanged(session.deviceId, ConnectionState.DISCONNECTED)
                session.callback.onSyncError(session.deviceId, SyncError(null, "Connection request failed", e))
            }
            Log.i(TAG, "Connection queue: ${session.deviceId} setup phase done, proceeding to next")
        }
    }

    /**
     * Starts a watchdog timer for the connection setup phase.
     * If the device doesn't reach the sync phase (all required features ready)
     * within the setup timeout, forces a disconnect + reconnect.
     */
    private fun startConnectionWatchdog(
        session: SyncSession,
        timeoutMillis: Long = connectionSetupTimeoutMillis(false)
    ) {
        session.connectionWatchdogJob?.cancel()
        session.connectionWatchdogJob = scope.launch {
            delay(timeoutMillis)

            if (activeSessions[session.deviceId] !== session) return@launch
            if (session.ftuAndSyncTriggered) return@launch

            val currentState = session.state
            if (currentState == ConnectionState.CONNECTING || currentState == ConnectionState.CONNECTED) {
                val requiredCount = requiredFeaturesFor(session.dataTypes).size
                Log.w(TAG, "Connection setup watchdog fired for ${session.deviceId} " +
                    "(state=$currentState, readyFeatures=${session.readyFeatures.size}/$requiredCount)")
                // Unblock the connection queue so the next device can proceed
                session.connectionSetupDone?.complete(Unit)
                session.connectionSetupDone = null
                // Null the ref before disconnecting so that the deviceDisconnected()
                // callback (which cancels the watchdog) doesn't cancel our forceReconnect.
                session.connectionWatchdogJob = null
                scope.launch { forceReconnect(session) }
            }
        }
    }

    /**
     * Forces a disconnect + reconnect cycle. Used by the connection watchdog
     * when the normal connection setup stalls.
     */
    private suspend fun forceReconnect(session: SyncSession) {
        try {
            api.disconnectFromDevice(session.deviceId)
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog: error disconnecting ${session.deviceId}", e)
        }

        delay(1000)

        if (activeSessions[session.deviceId] === session) {
            Log.i(TAG, "Watchdog: reconnecting ${session.deviceId}")
            session.state = ConnectionState.DISCONNECTED
            ensureConnected(session.deviceId)
        }
    }

    /**
     * Continuously scans for BLE devices and triggers reconnection when a known
     * disconnected session's device is detected advertising nearby.
     * The searchForDevice() flow emits each device only once per subscription,
     * so we restart it periodically to re-detect devices that come back into range.
     */
    private suspend fun reconnectionScanLoop() {
        while (true) {
            // Only scan when there are disconnected sessions that need reconnection
            val hasDisconnected = activeSessions.values.any { it.state == ConnectionState.DISCONNECTED }
            if (hasDisconnected) {
                Log.d(TAG, "Reconnection scan: starting BLE scan for disconnected devices")
                try {
                    api.searchForDevice()
                        .collect { deviceInfo ->
                            val session = sessionForDevice(deviceInfo)
                            if (session != null && session.state == ConnectionState.DISCONNECTED) {
                                rememberCompanionAddress(session.deviceId, deviceInfo.address)
                                Log.i(TAG, "Reconnection scan: detected ${deviceInfo.deviceId} advertising, triggering connect")
                                ensureConnected(session.deviceId)
                            }
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnection scan error", e)
                }
            }
            delay(RECONNECTION_SCAN_RESTART_INTERVAL_MS)
        }
    }

    /**
     * Periodically calls [PolarBleApi.foregroundEntered] and [PolarBleApi.connectToDevice]
     * for disconnected sessions. This handles devices that don't advertise readily
     * (e.g. sport watches in power-save mode) where the scan-based approach may not
     * detect them. foregroundEntered() forces the SDK to restart its internal BLE scan,
     * and connectToDevice() ensures the device is in the SDK's reconnection queue.
     */
    private suspend fun reconnectionNudgeLoop() {
        while (true) {
            delay(RECONNECTION_NUDGE_INTERVAL_MS)
            val disconnected = activeSessions.filter { it.value.state == ConnectionState.DISCONNECTED }
            if (disconnected.isNotEmpty()) {
                // Force the SDK to restart its internal BLE scan
                api.foregroundEntered()
                for ((deviceId, _) in disconnected) {
                    Log.d(TAG, "Reconnection nudge: connectToDevice($deviceId) + foregroundEntered()")
                    ensureConnected(deviceId)
                }
            }
        }
    }

    // ── Internal sync orchestration ─────────────────────────────────────

    private val baseRequiredFeatures = setOf(
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER
    )

    private fun requiredFeaturesFor(dataTypes: Set<SyncableDataType>): Set<PolarBleApi.PolarBleSdkFeature> {
        val required = baseRequiredFeatures.toMutableSet()

        if (dataTypes.contains(SyncableDataType.SLEEP)) {
            required.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA)
        }
        if (dataTypes.contains(SyncableDataType.TRAINING_SESSIONS)) {
            required.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA)
        }
        if (dataTypes.contains(SyncableDataType.SKIN_TEMPERATURE) ||
            dataTypes.contains(SyncableDataType.OFFLINE_TEMPERATURE) ||
            dataTypes.contains(SyncableDataType.OFFLINE_SKIN_TEMPERATURE)
        ) {
            required.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA)
        }
        if (dataTypes.any { it.isOfflineRecording }) {
            required.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING)
        }

        return required
    }

    private fun checkAllFeaturesReady(session: SyncSession) {
        val requiredFeatures = requiredFeaturesFor(session.dataTypes)
        if (session.ftuAndSyncTriggered) return
        if (session.readyFeatures.containsAll(requiredFeatures)) {
            session.ftuAndSyncTriggered = true
            Log.i(TAG, "All required features ready for ${session.deviceId}, waiting ${POST_FEATURES_READY_DELAY_MS}ms for PsFTP stabilization")
            // Delay before issuing PsFTP commands to let the watch firmware stabilize
            val scope = session.sessionScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { session.sessionScope = it }
            scope.launch {
                delay(POST_FEATURES_READY_DELAY_MS)
                if (session.state == ConnectionState.DISCONNECTED) {
                    Log.w(TAG, "Device ${session.deviceId} disconnected during stabilization delay, aborting")
                    return@launch
                }
                performFtuCheckAndSync(session)
            }
        }
    }

    private fun performFtuCheckAndSync(session: SyncSession) {
        // Connection setup succeeded — cancel the watchdog and unblock the queue
        session.connectionWatchdogJob?.cancel()
        session.connectionWatchdogJob = null
        session.connectionSetupDone?.complete(Unit)
        session.connectionSetupDone = null

        // Cancel any stale session scope from a previous connection
        session.sessionScope?.cancel()
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        session.sessionScope = sessionScope

        session.state = ConnectionState.FTU_CHECK
        session.callback.onConnectionStateChanged(session.deviceId, ConnectionState.FTU_CHECK)

        sessionScope.launch {
            try {
                Log.i(TAG, "[${session.deviceId}] FTU check (attempt ${session.syncInitAttempt}/${MAX_SYNC_INIT_RETRIES + 1})")
                val isFtuDone = api.isFtuDone(session.deviceId)
                if (isFtuDone) {
                    Log.i(TAG, "FTU already done for ${session.deviceId}")
                } else {
                    Log.i(TAG, "FTU not done for ${session.deviceId}, performing initialization")
                    performFtu(session)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isTimeoutError(e) && session.syncInitAttempt <= MAX_SYNC_INIT_RETRIES) {
                    Log.w(TAG, "[${session.deviceId}] FTU check timeout (attempt ${session.syncInitAttempt}), retrying after backoff…", e)
                    retrySyncInitAfterBackoff(session)
                    return@launch
                }
                Log.e(TAG, "Error checking FTU status for ${session.deviceId}", e)
            }
            startD2HListeningAndSync(session)
        }
    }

    private fun isTimeoutError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: e.toString().lowercase()
        return msg.contains("timeout") || msg.contains("waittimeout") || msg.contains("timed out")
    }

    private suspend fun retrySyncInitAfterBackoff(session: SyncSession) {
        val attempt = session.syncInitAttempt
        Log.i(TAG, "[${session.deviceId}] RETRY — disconnecting for clean retry (attempt ${attempt + 1})…")
        try {
            api.disconnectFromDevice(session.deviceId)
        } catch (e: Exception) {
            Log.w(TAG, "[${session.deviceId}] RETRY — disconnect failed: $e")
        }
        delay(SYNC_INIT_RETRY_BACKOFF_MS)
        if (activeSessions[session.deviceId] !== session ||
            session.state != ConnectionState.DISCONNECTED) return
        Log.i(TAG, "[${session.deviceId}] RETRY — reconnecting…")
        // Reset the feature-ready gate so the flow re-triggers on reconnection
        session.ftuAndSyncTriggered = false
        session.readyFeatures.clear()
        session.syncInitAttempt = attempt + 1
        ensureConnected(session.deviceId)
    }
    override fun ensureConnected(deviceId: String) {
        val session = sessionForIdentifier(deviceId) ?: return
        synchronized(session) {
            if (activeSessions[session.deviceId] !== session ||
                session.state != ConnectionState.DISCONNECTED) {
                Log.d(TAG, "ensureConnected(${session.deviceId}) skipped, app state=${session.state}")
                return
            }
            session.state = ConnectionState.CONNECTING
        }
        session.callback.onConnectionStateChanged(session.deviceId, ConnectionState.CONNECTING)
        val queued = connectionQueue.trySend(session)
        if (queued.isFailure) {
            session.state = ConnectionState.DISCONNECTED
            val message = "Failed to queue connection for ${session.deviceId}"
            Log.e(TAG, message, queued.exceptionOrNull())
            session.callback.onSyncError(session.deviceId, SyncError(null, message, queued.exceptionOrNull()))
        }
    }

    private suspend fun performFtu(session: SyncSession) {
        val nowUtc = ZonedDateTime.now().withZoneSameInstant(java.time.ZoneOffset.UTC)
        val nowLocal = LocalDateTime.now() // phone local time
        // SDK expects format "yyyy-MM-ddTHH:mm:ssZ" — literal Z, no fractional seconds
        val ftuTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ENGLISH)
        val ftuConfig = PolarFirstTimeUseConfig(
            gender = PolarFirstTimeUseConfig.Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 1),
            height = 175.0f,
            weight = 75.0f,
            maxHeartRate = 190,
            vo2Max = 40,
            restingHeartRate = 65,
            trainingBackground = 20,
            deviceTime = nowUtc.format(ftuTimeFormatter),
            typicalDay = PolarFirstTimeUseConfig.TypicalDay.MOSTLY_MOVING,
            sleepGoalMinutes = 480
        )

        try {
            api.doFirstTimeUse(session.deviceId, ftuConfig)
            // Set device local time to phone time (local timezone)
            api.setLocalTime(session.deviceId, nowLocal)
            Log.i(TAG, "FTU completed for ${session.deviceId} (system time set to UTC, local time set to phone time)")
        } catch (e: Exception) {
            Log.e(TAG, "FTU failed for ${session.deviceId}", e)
            session.callback.onSyncError(
                session.deviceId,
                SyncError(null, "First Time Use initialization failed", e)
            )
        }
    }

    private suspend fun startD2HListeningAndSync(session: SyncSession) {
        val sessionScope = session.sessionScope ?: return
        // Launch D2H notification collection in a separate coroutine
        sessionScope.launch {
            try {
                api.observeDeviceToHostNotifications(session.deviceId)
                    .catch { e ->
                        Log.w(TAG, "[${session.deviceId}] D2H notification subscription error", e)
                    }
                    .collect { notification ->
                        Log.d(TAG, "[${session.deviceId}] D2H notification: ${notification.notificationType}")
                        when (notification.notificationType) {
                            PolarDeviceToHostNotification.SYNC_REQUIRED,
                            PolarDeviceToHostNotification.FILESYSTEM_MODIFIED -> {
                                Log.i(TAG, "[${session.deviceId}] D2H new data detected, triggering sync for this device only")
                                executeSyncCycle(session)
                            }
                            else -> { /* ignore other notifications */ }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[${session.deviceId}] D2H notification error", e)
            }
        }

        // Execute initial sync cycle
        executeSyncCycle(session)
    }

    private fun executeSyncCycle(session: SyncSession) {
        if (activeSessions[session.deviceId] !== session) return
        if (session.state == ConnectionState.SYNCING) {
            Log.d(TAG, "Sync already in progress for ${session.deviceId}, skipping")
            return
        }

        val sessionScope = session.sessionScope ?: return
        val newCycle = session.fetchPlan.beginCycle(session.dataTypes.toList())
        session.state = ConnectionState.SYNCING
        if (newCycle) {
            session.syncStartTime = LocalDateTime.now()
            session.offlineSyncStartTime = null
            session.offlineSyncEndTime = null
            session.syncCycleResults.clear()
            batteryMonitor.logEvent(session.deviceId, "SYNC_STARTED").also { session.callback.onDiagnosticMessage(it) }
            session.callback.onSyncStarted(session.deviceId)
        }
        session.callback.onConnectionStateChanged(session.deviceId, ConnectionState.SYNCING)

        sessionScope.launch {
            try {
                api.sendInitializationAndStartSyncNotifications(session.deviceId)
                // Reset attempt counter on success for future D2H-triggered cycles
                session.syncInitAttempt = 1
                syncAllDataTypes(session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isTimeoutError(e) && session.syncInitAttempt <= MAX_SYNC_INIT_RETRIES) {
                    Log.w(TAG, "[${session.deviceId}] Sync init timeout (attempt ${session.syncInitAttempt}), will disconnect and retry…", e)
                    session.state = ConnectionState.CONNECTED  // Allow executeSyncCycle to re-enter
                    retrySyncInitAfterBackoff(session)
                } else {
                    Log.e(TAG, "Failed to send sync init for ${session.deviceId}", e)
                    session.callback.onSyncError(
                        session.deviceId,
                        SyncError(null, "Failed to initialize sync", e)
                    )
                    finishSyncCycle(session)
                }
            }
        }
    }

    private suspend fun syncAllDataTypes(session: SyncSession) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val dataType = session.fetchPlan.next ?: break
            if (session.state == ConnectionState.DISCONNECTED) return
            if (activeSessions[session.deviceId] !== session) return
            if (session.fetchPlan.nextIsRetry) {
                delay(DEFERRED_RETRY_DELAY_MS)
                if (activeSessions[session.deviceId] !== session ||
                    session.state != ConnectionState.SYNCING) return
            }

            // Fire offline recording group start on first offline type
            if (dataType.isOfflineRecording && session.offlineSyncStartTime == null) {
                session.offlineSyncStartTime = LocalDateTime.now()
                session.callback.onOfflineRecordingGroupStarted(session.deviceId)
            }

            // Notify app that this data type is about to be fetched
            session.callback.onDataTypeStarted(session.deviceId, dataType)

            val fetcher = DataTypeFetcher(api, localDataStore, gson, prefs, syncStateTracker, session.dayCursors,
                autoDeleteFromDevice = session.config.autoDeleteFromDevice,
                offlineRecordingRetentionDays = session.config.offlineRecordingRetentionDays)
            try {
                withTimeout(PER_FETCH_TIMEOUT_MS) {
                    fetcher.fetchAndSave(session.deviceId, dataType) { results ->
                        session.syncCycleResults.addAll(results)
                        results.forEach { result -> session.callback.onDataSynced(result) }
                    }
                }
                session.fetchPlan.complete(dataType)
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                reportFetchFailure(session, dataType, "Timeout syncing $dataType", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                reportFetchFailure(session, dataType, "Failed to sync $dataType", e)
            }

            if (dataType.isOfflineRecording) {
                session.offlineSyncEndTime = LocalDateTime.now()
                // Fire offline group completed when moving to a non-offline type (or last type)
                val nextType = session.fetchPlan.next
                if (nextType == null || !nextType.isOfflineRecording) {
                    session.callback.onOfflineRecordingGroupCompleted(session.deviceId)
                }
            }
        }

        performDeviceCleanupAndFinish(session)
    }

    private fun reportFetchFailure(
        session: SyncSession, dataType: SyncableDataType, reason: String, error: Exception
    ) {
        val retry = session.fetchPlan.recordFailure(dataType)
        val action = if (retry) "retrying after remaining data types"
            else "retry limit reached; skipping until next notification, timed or foreground sync"
        val message = "$reason; $action"
        Log.e(TAG, "[${session.deviceId}] $message", error)
        session.callback.onDiagnosticMessage(message)
        session.callback.onSyncError(session.deviceId, SyncError(dataType, message, error))
    }

    private suspend fun performDeviceCleanupAndFinish(session: SyncSession) {
        // Finish the sync cycle first — emit summary, notify completion, release sync channel.
        // Device cleanup runs independently afterwards so it doesn't block multi-device sync.
        finishSyncCycle(session)

        // Run cleanup asynchronously after sync is done
        // Never delete device data after an exhausted type: it may not be fully synced.
        if (session.config.autoDeleteFromDevice && session.fetchPlan.exhaustedTypes.isEmpty()) {
            val sessionScope = session.sessionScope ?: return
            sessionScope.launch {
                performDeviceCleanup(session)
            }
        }
    }

    /** Runs device data cleanup (deletion of old synced data) independently from the sync cycle. */
    private suspend fun performDeviceCleanup(session: SyncSession) {
        if (!activeSessions.containsKey(session.deviceId)) return

        Log.i(TAG, "PHASE:CLEANUP — deleting old data from device ${session.deviceId} (retention: ${session.config.deviceRetentionDays} days)")
        batteryMonitor.logEvent(session.deviceId, "CLEANUP_STARTED").also { session.callback.onDiagnosticMessage(it) }
        session.callback.onDeviceCleanupStarted(session.deviceId)

        val deleter = DeviceDataDeleter(api, localDataStore, prefs)
        try {
            deleter.deleteOldSyncedData(
                session.deviceId,
                session.dataTypes,
                session.config.deviceRetentionDays
            )
            Log.i(TAG, "PHASE:CLEANUP — device cleanup completed for ${session.deviceId}")
            batteryMonitor.logEvent(session.deviceId, "CLEANUP_COMPLETED").also { session.callback.onDiagnosticMessage(it) }
            session.callback.onDeviceCleanupCompleted(session.deviceId, session.dataTypes.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "PHASE:CLEANUP — device cleanup failed for ${session.deviceId}", e)
            session.callback.onSyncError(
                session.deviceId,
                SyncError(null, "Device data cleanup failed", e)
            )
            session.callback.onDeviceCleanupCompleted(session.deviceId, 0)
        }
    }

    private suspend fun finishSyncCycle(session: SyncSession) {
        try {
            api.sendTerminateAndStopSyncNotifications(session.deviceId)
            Log.i(TAG, "Sync cycle complete for ${session.deviceId}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send sync terminate for ${session.deviceId}", e)
        }

        session.fetchPlan.finishCycle()
        if (session.fetchPlan.exhaustedTypes.isNotEmpty()) {
            val message = "Exhausted data types: ${session.fetchPlan.exhaustedTypes}; " +
                "keeping session active for next notification, timed or foreground sync"
            Log.w(TAG, "[${session.deviceId}] $message")
            session.callback.onDiagnosticMessage(message)
        }
        if (activeSessions.containsKey(session.deviceId)) {
            // Build sync summary
            val endTime = LocalDateTime.now()
            val startTime = session.syncStartTime ?: endTime
            val durationSeconds = java.time.Duration.between(startTime, endTime).seconds

            // Split results into activity data and offline recordings
            val (offlineResults, activityResults) = session.syncCycleResults.partition { it.dataType.isOfflineRecording }
            val requestedOfflineTypes = session.dataTypes.filter { it.isOfflineRecording }

            // Activity data summary
            if (activityResults.isNotEmpty()) {
                val perTypeStats = activityResults
                    .groupBy { it.dataType }
                    .mapValues { (_, results) ->
                        TypeSyncStats(items = results.size, bytes = results.sumOf { it.sizeBytes })
                    }
                val summary = SyncSummary(
                    deviceId = session.deviceId,
                    startTime = startTime,
                    endTime = endTime,
                    durationSeconds = durationSeconds,
                    totalItems = activityResults.size,
                    totalBytes = activityResults.sumOf { it.sizeBytes },
                    perTypeStats = perTypeStats
                )
                logSyncSummary("Sync Summary", session, summary, startTime, endTime, durationSeconds)
                session.callback.onSyncSummary(summary)
            }

            // Offline recordings summary (separate notification) — always emit if offline types were requested
            if (requestedOfflineTypes.isNotEmpty()) {
                val offlineStart = session.offlineSyncStartTime ?: startTime
                val offlineEnd = session.offlineSyncEndTime ?: endTime
                val offlineDuration = java.time.Duration.between(offlineStart, offlineEnd).seconds
                // Start with zero-count entries for all requested offline types
                val perTypeStats = requestedOfflineTypes.associateWith { type ->
                    val matching = offlineResults.filter { it.dataType == type }
                    TypeSyncStats(items = matching.size, bytes = matching.sumOf { it.sizeBytes })
                }
                val summary = SyncSummary(
                    deviceId = session.deviceId,
                    startTime = offlineStart,
                    endTime = offlineEnd,
                    durationSeconds = offlineDuration,
                    totalItems = offlineResults.size,
                    totalBytes = offlineResults.sumOf { it.sizeBytes },
                    perTypeStats = perTypeStats
                )
                logSyncSummary("Offline Recordings Summary", session, summary, offlineStart, offlineEnd, offlineDuration)
                session.callback.onSyncSummary(summary)
            }

            // If nothing was synced and no offline types requested, still emit an empty summary
            if (activityResults.isEmpty() && requestedOfflineTypes.isEmpty()) {
                val summary = SyncSummary(
                    deviceId = session.deviceId,
                    startTime = startTime,
                    endTime = endTime,
                    durationSeconds = durationSeconds,
                    totalItems = 0,
                    totalBytes = 0,
                    perTypeStats = emptyMap()
                )
                logSyncSummary("Sync Summary", session, summary, startTime, endTime, durationSeconds)
                session.callback.onSyncSummary(summary)
            }

            session.state = ConnectionState.CONNECTED
            batteryMonitor.logEvent(session.deviceId, "SYNC_COMPLETED").also { session.callback.onDiagnosticMessage(it) }
            session.callback.onSyncCompleted(session.deviceId)
            session.callback.onConnectionStateChanged(session.deviceId, ConnectionState.CONNECTED)
        }

        // Purge expired local data if retention is configured
        localDataStore.purgeExpired()

        // Schedule timed sync if configured
        scheduleTimedSync(session)
    }

    private fun scheduleTimedSync(session: SyncSession) {
        session.timedSyncJob?.cancel()
        session.timedSyncJob = null
        val intervalMinutes = session.config.syncIntervalMinutes
        if (intervalMinutes <= 0) return
        val sessionScope = session.sessionScope ?: return
        session.timedSyncJob = sessionScope.launch {
            Log.i(TAG, "Timed sync scheduled in $intervalMinutes min for ${session.deviceId}")
            delay(intervalMinutes * 60_000L)
            if (activeSessions.containsKey(session.deviceId) && session.state != ConnectionState.DISCONNECTED) {
                Log.i(TAG, "Timed sync firing for ${session.deviceId}")
                executeSyncCycle(session)
            }
        }
    }
}

/**
 * Holds state for an active sync session.
 */
internal data class SyncSession(
    val deviceId: String,
    var dataTypes: Set<SyncableDataType>,
    val config: SyncConfig,
    @Volatile var callback: SyncCallback,
    var sessionScope: CoroutineScope?,
    @Volatile var state: ConnectionState,
    val readyFeatures: MutableSet<PolarBleApi.PolarBleSdkFeature>,
    @Volatile var ftuAndSyncTriggered: Boolean = false,
    @Volatile var connectionSetupDone: CompletableDeferred<Unit>? = null,
    var connectionWatchdogJob: Job? = null,
    var timedSyncJob: Job? = null,
    var syncStartTime: LocalDateTime? = null,
    var offlineSyncStartTime: LocalDateTime? = null,
    var offlineSyncEndTime: LocalDateTime? = null,
    val syncCycleResults: MutableList<SyncResult> = mutableListOf(),
    val fetchPlan: SyncFetchPlan = SyncFetchPlan(maxRetries = 3),
    var syncInitAttempt: Int = 1,
    val dayCursors: DayCursors = DayCursors()
)
