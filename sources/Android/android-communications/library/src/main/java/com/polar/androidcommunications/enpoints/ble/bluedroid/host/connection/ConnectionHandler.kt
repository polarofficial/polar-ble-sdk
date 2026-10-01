package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

import androidx.annotation.VisibleForTesting
import android.bluetooth.BluetoothGatt
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState
import com.polar.androidcommunications.common.ble.BleUtils.AD_TYPE
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connection handler handles connection states serialization, by using simple state pattern
 */
class ConnectionHandler(
    private val connectionInterface: ConnectionInterface,
    private val scannerInterface: ScannerInterface,
    private val observer: ConnectionHandlerObserver,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "ConnectionHandler"

        @VisibleForTesting
        const val GUARD_TIME_MS = 6000L
        private const val DISCONNECT_GUARD_TIME_MS = 15000L
        const val POLAR_PREFERRED_MTU = 512
        const val MTU_SKIP_NEGOTIATION = 0
        private const val FIRST_ATTRIBUTE_OPERATION_TIMEOUT = 1200L
        private const val RECONNECT_BACKOFF_BASE_MS = 2000L
        private const val RECONNECT_BACKOFF_MAX_MS = 16000L

        /**
         * Watchdog timeout for the entire SESSION_OPENING -> SESSION_OPEN transition.
         * If a connection attempt does not complete within this time (e.g. OS callback never fires),
         * the session is forcibly cancelled and moved back to SESSION_OPEN_PARK for reconnection.
         * Normal connection flow completes in a few seconds; 30s gives ample margin.
         */
        @VisibleForTesting
        const val CONNECTION_WATCHDOG_TIMEOUT_MS = 30_000L

        /**
         * Base backoff delay between no-callback (watchdog-triggered) connection attempts.
         */
        private const val INITIAL_CONNECT_BACKOFF_MS = 5_000L    // 5 s
        private const val MAX_CONNECT_BACKOFF_MS     = 120_000L  // 2 min cap
    }

    /**
     * Connection handler state's
     */
    @VisibleForTesting
    enum class ConnectionHandlerState {
        FREE, CONNECTING
    }

    /**
     * Connection handler state actions
     */
    private enum class ConnectionHandlerAction {
        ENTRY,
        EXIT,
        CONNECT_DEVICE,
        CONNECT_DEVICE_DIRECT,
        ADVERTISEMENT_HEAD_RECEIVED,
        DISCONNECT_DEVICE,
        DEVICE_DISCONNECTED,
        DEVICE_CONNECTION_INITIALIZED,
        PHY_UPDATED,
        SERVICES_DISCOVERED,
        MTU_UPDATED
    }

    /** Callbacks that form the ordered connection-negotiation sequence. */
    private val NEGOTIATION_ACTIONS = setOf(
        ConnectionHandlerAction.DEVICE_CONNECTION_INITIALIZED,
        ConnectionHandlerAction.SERVICES_DISCOVERED,
        ConnectionHandlerAction.PHY_UPDATED,
        ConnectionHandlerAction.MTU_UPDATED
    )

    @VisibleForTesting
    var state: ConnectionHandlerState = ConnectionHandlerState.FREE
    private var current: BDDeviceSessionImpl? = null
    private var automaticReconnection = true

    private val reconnectAttempts = mutableMapOf<String, Int>()
    private val reconnectNotBeforeMs = mutableMapOf<String, Long>()
    private var servicesDiscoveredReceived = false

    private var phySafeGuardJob: Job? = null
    private var mtuSafeGuardJob: Job? = null
    // Per-device address jobs so that attribute operations for different devices do not interfere with each other.
    private val firstAttributeOperationJobs = mutableMapOf<String, Job>()
    private var connectionWatchdogJob: Job? = null
    private val disconnectSafeGuardJobs = mutableMapOf<String, Job>()
    private val mutex = Object()

    /**
     * Monotonically increasing token identifying the current connection attempt. Guard timers and
     * watchdogs capture the value at arm time and act only while it still matches, so a timer
     * belonging to a superseded attempt can never disturb a newer one.
     */
    private var attempt = 0L

    /**
     * The next negotiation callback expected for [current]. Callbacks that arrive duplicated or out
     * of order (a stale guard firing after the real callback already advanced the stage) are
     * rejected instead of corrupting the sequence.
     */
    private var expectedConnectionEvent: ConnectionHandlerAction? = null

    /**
     * A connect request that could not be served immediately because the handler was busy.
     * [automatic] marks retries scheduled by the library itself, which must be suppressed when
     * automatic reconnection is disabled; explicit user requests are always preserved.
     */
    private class PendingConnect(var action: ConnectionHandlerAction, var automatic: Boolean) {
        var job: Job? = null
        var ready = false
        fun cancel() {
            job?.cancel()
            job = null
        }
    }

    /**
     * Connect requests queued while the handler was busy, in arrival order, for keeping
     * per-session connection attempts and retries working and not allowing them to interfere 
     * with each other.
     */
    private val retries = linkedMapOf<BDDeviceSessionImpl, PendingConnect>()
    private var commandDepth = 0
    private var drainingRetries = false

    /**
     * Set while Bluetooth is off. Queued GATT callbacks that were already in flight when the
     * adapter went down must not be executed against a GATT the stack has torn down, and new
     * connect requests must not be started until the adapter is back.
     */
    private var poweredOff = false

    fun setAutomaticReconnection(automaticReconnection: Boolean) {
        synchronized(mutex) {
            this.automaticReconnection = automaticReconnection
            if (!automaticReconnection) {
                // Disabling automatic reconnection must also drop retries the library scheduled
                // itself, otherwise a queued timer reconnects a device the caller just gave up on.
                val iterator = retries.entries.iterator()
                while (iterator.hasNext()) {
                    val request = iterator.next().value
                    if (request.automatic) {
                        request.cancel()
                        iterator.remove()
                    }
                }
            }
        }
    }

    fun getAutomaticReconnection(): Boolean {
        return this.automaticReconnection
    }

    fun cancel() {
        synchronized(mutex) {
            cancelAllSafeGuardJobs()
            firstAttributeOperationJobs.values.forEach { it.cancel() }
            firstAttributeOperationJobs.clear()
            disconnectSafeGuardJobs.values.forEach { it.cancel() }
            disconnectSafeGuardJobs.clear()
            retries.values.forEach { it.cancel() }
            retries.clear()
        }
        scope.cancel()
    }

    /**
     * Cancels guards that belong to the in-flight connection attempt only. Per-session state
     * (first-attribute timers, disconnect guards, queued connect requests) deliberately survives,
     * because those belong to other sessions or to work that must still be resumed.
     */
    private fun cancelAllSafeGuardJobs() {
        phySafeGuardJob?.cancel()
        phySafeGuardJob = null
        mtuSafeGuardJob?.cancel()
        mtuSafeGuardJob = null
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = null
    }

    private fun cancelFirstAttributeOperation(session: BDDeviceSessionImpl) {
        firstAttributeOperationJobs.remove(session.address)?.cancel()
    }

    private fun cancelDisconnectSafeGuard(session: BDDeviceSessionImpl) {
        disconnectSafeGuardJobs.remove(session.address)?.cancel()
    }

    private fun scheduleDisconnectSafeGuard(session: BDDeviceSessionImpl) {
        cancelDisconnectSafeGuard(session)
        disconnectSafeGuardJobs[session.address] = scope.launch {
            delay(DISCONNECT_GUARD_TIME_MS)
            synchronized(mutex) {
                if (session.sessionState == DeviceSessionState.SESSION_CLOSING) {
                    BleLogger.w(TAG, "Disconnect callback timeout for ${session.address}, forcing close via reset")
                    // Force reset GATT and mark session closed directly
                    // This ensures stuck GATT connections don't block future reconnections
                    session.reset()
                    updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                }
                disconnectSafeGuardJobs.remove(session.address)
            }
        }
    }

    fun advertisementHeadReceived(bleDeviceSession: BDDeviceSessionImpl) {
        commandState(bleDeviceSession, ConnectionHandlerAction.ADVERTISEMENT_HEAD_RECEIVED)
    }

    fun connectDevice(bleDeviceSession: BDDeviceSessionImpl, bluetoothEnabled: Boolean) {
        if (bluetoothEnabled) {
            commandState(bleDeviceSession, ConnectionHandlerAction.CONNECT_DEVICE)
        } else {
            when (bleDeviceSession.sessionState) {
                DeviceSessionState.SESSION_CLOSED,
                DeviceSessionState.SESSION_CLOSING -> {
                    updateSessionState(bleDeviceSession, DeviceSessionState.SESSION_OPEN_PARK)
                }
                else -> {
                    //Do nothing
                }
            }
        }
    }

    /**
     * Initiate a GATT connection directly by MAC address, bypassing the
     * advertisement-connectable guard. Call this when the peripheral is known
     * to be reachable (e.g. via CompanionDeviceManager) but has stopped advertising.
     */
    fun connectDeviceDirect(bleDeviceSession: BDDeviceSessionImpl, bluetoothEnabled: Boolean) {
        bleDeviceSession.directConnect = true
        // Clear any active watchdog backoff so the direct-connect is not gated
        bleDeviceSession.consecutiveNoCallbackConnects = 0
        bleDeviceSession.nextConnectAllowedTimeMs = 0L
        if (bluetoothEnabled) {
            commandState(bleDeviceSession, ConnectionHandlerAction.CONNECT_DEVICE_DIRECT)
        } else {
            when (bleDeviceSession.sessionState) {
                DeviceSessionState.SESSION_CLOSED,
                DeviceSessionState.SESSION_CLOSING -> {
                    updateSessionState(bleDeviceSession, DeviceSessionState.SESSION_OPEN_PARK)
                }
                else -> {
                    //Do nothing
                }
            }
        }
    }

    fun disconnectDevice(bleDeviceSession: BDDeviceSessionImpl) {
        commandState(bleDeviceSession, ConnectionHandlerAction.DISCONNECT_DEVICE)
    }

    fun connectionInitialized(bleDeviceSession: BDDeviceSessionImpl) {
        commandState(bleDeviceSession, ConnectionHandlerAction.DEVICE_CONNECTION_INITIALIZED)
    }

    fun phyUpdated(bleDeviceSession: BDDeviceSessionImpl) {
        phySafeGuardJob?.cancel()
        commandState(bleDeviceSession, ConnectionHandlerAction.PHY_UPDATED)
    }

    fun servicesDiscovered(bleDeviceSession: BDDeviceSessionImpl) {
        commandState(bleDeviceSession, ConnectionHandlerAction.SERVICES_DISCOVERED)
    }

    fun mtuUpdated(bleDeviceSession: BDDeviceSessionImpl) {
        mtuSafeGuardJob?.cancel()
        commandState(bleDeviceSession, ConnectionHandlerAction.MTU_UPDATED)
    }

    fun deviceDisconnected(bleDeviceSession: BDDeviceSessionImpl) {
        observer.deviceDisconnected(bleDeviceSession)
        commandState(bleDeviceSession, ConnectionHandlerAction.DEVICE_DISCONNECTED)
    }

    private fun commandState(bleDeviceSession: BDDeviceSessionImpl, action: ConnectionHandlerAction) {
        synchronized(mutex) {
            stateTransaction {
                // While the adapter is off nothing can be connected; accepting these would arm
                // watchdogs and issue GATT connects that can never complete.
                if (poweredOff && (action == ConnectionHandlerAction.CONNECT_DEVICE ||
                        action == ConnectionHandlerAction.CONNECT_DEVICE_DIRECT ||
                        action == ConnectionHandlerAction.ADVERTISEMENT_HEAD_RECEIVED)
                ) {
                    return@stateTransaction
                }
                // Negotiation callbacks are only meaningful for the session currently being
                // connected, and only when they are the stage we are actually waiting for. This
                // rejects duplicates and stale guard timers that would otherwise skip a stage.
                // Only enforced while CONNECTING; in FREE state these actions have their own
                // handling (e.g. closing an orphaned GATT that connected too late).
                if (state == ConnectionHandlerState.CONNECTING &&
                    action in NEGOTIATION_ACTIONS &&
                    (current !== bleDeviceSession || expectedConnectionEvent != action)
                ) {
                    BleLogger.d(TAG, "Ignoring out-of-order $action for ${bleDeviceSession.address} (expected $expectedConnectionEvent)")
                    return@stateTransaction
                }
                when (state) {
                    ConnectionHandlerState.FREE -> {
                        free(bleDeviceSession, action)
                    }
                    ConnectionHandlerState.CONNECTING -> {
                        BleLogger.d(TAG, "state: $state action: $action")
                        connecting(bleDeviceSession, action)
                    }
                }
            }
        }
    }

    /**
     * Tracks nesting of state-machine work. A queued connect must only start once the outermost
     * transition has fully settled, otherwise it would observe FREE while observers are still being
     * notified and `current` has not yet been cleared.
     */
    private inline fun stateTransaction(action: () -> Unit) {
        commandDepth++
        try {
            action()
        } finally {
            commandDepth--
            if (commandDepth == 0) drainPendingConnects()
        }
    }

    private fun changeState(bleDeviceSession: BDDeviceSessionImpl, newState: ConnectionHandlerState) {
        commandState(bleDeviceSession, ConnectionHandlerAction.EXIT)
        state = newState
        commandState(bleDeviceSession, ConnectionHandlerAction.ENTRY)
    }

    private fun updateSessionState(bleDeviceSession: BDDeviceSessionImpl, newState: DeviceSessionState) {
        BleLogger.d(TAG, " Session update from: " + bleDeviceSession.sessionState.toString() + " to: " + newState.toString())
        bleDeviceSession.setSessionStates(newState)
        observer.deviceSessionStateChanged(bleDeviceSession)
    }

    private fun containsRequiredUuids(session: BDDeviceSessionImpl): Boolean {
        if (session.connectionUuids.isNotEmpty()) {
            val content = session.advertisementContent.advertisementData
            // Check both list types: a device may advertise the required UUID in either, and
            // inspecting only one of them caused valid devices to be rejected.
            for (type in listOf(AD_TYPE.GAP_ADTYPE_16BIT_MORE, AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE)) {
                val uuids = content[type] ?: continue
                var i = 0
                // Stop before a truncated trailing byte instead of indexing past the payload,
                // which threw IndexOutOfBoundsException on odd-length advertisement data.
                while (i + 1 < uuids.size) {
                    val hexUUid = String.format("%02X%02X", uuids[i + 1], uuids[i])
                    if (session.connectionUuids.contains(hexUUid)) {
                        return true
                    }
                    i += 2
                }
            }
            return false
        }
        return true
    }

    private fun free(session: BDDeviceSessionImpl, action: ConnectionHandlerAction) {
        when (action) {
            ConnectionHandlerAction.ENTRY,
            ConnectionHandlerAction.EXIT -> {
                //Do nothing
            }
            ConnectionHandlerAction.DEVICE_CONNECTION_INITIALIZED -> {
                // The GATT connected after the handler already left CONNECTING state (e.g. a late
                // OS callback arriving after a disconnect cleaned up the session). The connection
                // is now orphaned – nobody will drive it to SESSION_OPEN, and without an explicit
                // disconnect it survives until the BLE supervision timeout (~2 min), blocking
                // any new connection attempt. Close it immediately so the OS can release the link.
                BleLogger.w(TAG, "Unexpected DEVICE_CONNECTION_INITIALIZED in FREE state – closing orphaned GATT connection")
                connectionInterface.disconnectDevice(session)
            }
            ConnectionHandlerAction.PHY_UPDATED,
            ConnectionHandlerAction.SERVICES_DISCOVERED,
            ConnectionHandlerAction.MTU_UPDATED -> {
                BleLogger.d(TAG, "Action $action in free state.")
            }
            ConnectionHandlerAction.CONNECT_DEVICE -> {
                // Manual connect always clears any active backoff so that the next
                // ADVERTISEMENT_HEAD_RECEIVED is not silently skipped.
                reconnectAttempts.remove(session.address)
                reconnectNotBeforeMs.remove(session.address)
                session.consecutiveNoCallbackConnects = 0
                session.nextConnectAllowedTimeMs = 0L
                when (session.sessionState) {
                    DeviceSessionState.SESSION_OPEN_PARK,
                    DeviceSessionState.SESSION_CLOSED -> {
                        // Always attempt connection on explicit user request — do not require a
                        // connectable advertisement. If the device has a zombie ACL (e.g. on
                        // OnePlus) connectGatt() reuses it immediately; if the device is truly
                        // unreachable the attempt will timeout and return to SESSION_OPEN_PARK.
                        BleLogger.d(TAG, "Connect requested for ${session.address} (connectable=${session.isConnectableAdvertisement}): attempting direct connection")
                        changeState(session, ConnectionHandlerState.CONNECTING)
                    }
                    DeviceSessionState.SESSION_CLOSING -> {
                        // Recovery path for devices that miss STATE_DISCONNECTED callback.
                        // Ensure stale GATT is closed so reconnect can proceed immediately.
                        BleLogger.w(TAG, "Reconnect requested while SESSION_CLOSING for ${session.address}, forcing stale connection reset")
                        cancelDisconnectSafeGuard(session)
                        observer.deviceConnectionCancelled(session)
                        updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                        changeState(session, ConnectionHandlerState.CONNECTING)
                    }
                    DeviceSessionState.SESSION_OPEN -> {
                        updateSessionState(session, DeviceSessionState.SESSION_OPEN)
                    }
                    DeviceSessionState.SESSION_OPENING -> { /* Do nothing */ }
                }
            }
            ConnectionHandlerAction.CONNECT_DEVICE_DIRECT -> {
                // Bypass the advertisement-connectable guard — the caller asserts the
                // device is reachable by MAC even though it may have stopped advertising.
                reconnectAttempts.remove(session.address)
                reconnectNotBeforeMs.remove(session.address)
                when (session.sessionState) {
                    DeviceSessionState.SESSION_OPEN_PARK,
                    DeviceSessionState.SESSION_CLOSED -> {
                        changeState(session, ConnectionHandlerState.CONNECTING)
                    }
                    DeviceSessionState.SESSION_CLOSING -> {
                        updateSessionState(session, DeviceSessionState.SESSION_OPEN_PARK)
                    }
                    DeviceSessionState.SESSION_OPEN -> {
                        updateSessionState(session, DeviceSessionState.SESSION_OPEN)
                    }
                    DeviceSessionState.SESSION_OPENING -> { /* Do nothing */ }
                }
            }
            ConnectionHandlerAction.ADVERTISEMENT_HEAD_RECEIVED -> {
                if (session.sessionState == DeviceSessionState.SESSION_OPEN_PARK) {
                    val now = System.currentTimeMillis()
                    // Respect per-session reconnect backoff set by the watchdog-cancel path.
                    if (now < session.nextConnectAllowedTimeMs) {
                        val remaining = session.nextConnectAllowedTimeMs - now
                        BleLogger.d(TAG, "Reconnect backoff in effect for ${session.address} – skipping advertisement (${remaining}ms remaining)")
                        return
                    }
                    // Direct-connect calls always bypass this gate; advertisement-triggered
                    // connects respect it so a wedged adapter isn't hammered endlessly.
                    val cooldownUntil = reconnectNotBeforeMs[session.address] ?: 0L
                    if (now < cooldownUntil) {
                        BleLogger.d(TAG, "Skipping reconnect for ${session.address}, backoff cooldown active (${cooldownUntil - now}ms remaining)")
                    } else if (session.isConnectableAdvertisement && containsRequiredUuids(session)) {
                        changeState(session, ConnectionHandlerState.CONNECTING)
                    } else {
                        BleLogger.d(TAG, "Skipped reconnect for ${session.address}: connectable=${session.isConnectableAdvertisement} uuids=${containsRequiredUuids(session)}")
                    }
                }
            }
            ConnectionHandlerAction.DISCONNECT_DEVICE -> {
                handleDisconnectDevice(session)
            }
            ConnectionHandlerAction.DEVICE_DISCONNECTED -> {
                handleDeviceDisconnected(session)
            }
        }
    }

    /**
     * connecting state. Connection is expected to happen in following order:
     * connection creation, set phy, set mtu and finally service discovery. Once service discovery is complete then
     * step back to free state.
     */
    private fun connecting(session: BDDeviceSessionImpl, action: ConnectionHandlerAction) {
        when (action) {
            ConnectionHandlerAction.ENTRY -> {
                scannerInterface.connectionHandlerRequestStopScanning()
                servicesDiscoveredReceived = false
                if (connectionInterface.isPowered) {
                    current = session
                    attempt++
                    val token = attempt
                    expectedConnectionEvent = ConnectionHandlerAction.DEVICE_CONNECTION_INITIALIZED
                    // A queued request for this session is now being served.
                    retries.remove(session)?.cancel()
                    updateSessionState(session, DeviceSessionState.SESSION_OPENING)
                    connectionInterface.connectDevice(session)
                    connectionWatchdogJob?.cancel()
                    connectionWatchdogJob = scope.launch {
                        delay(CONNECTION_WATCHDOG_TIMEOUT_MS)
                        synchronized(mutex) {
                            forAttempt(session, token) {
                                BleLogger.w(TAG, "Connection watchdog triggered: SESSION_OPENING timed out after ${CONNECTION_WATCHDOG_TIMEOUT_MS}ms, forcing disconnect")
                                // Mark as watchdog cancel so the handler applies reconnect backoff.
                                session.pendingWatchdogCancel = true
                                // Go through the normal disconnectDevice path so the state machine and
                                // mutex are respected, rather than mutating state directly from a coroutine.
                                disconnectDevice(session)
                            }
                        }
                    }
                } else {
                    // TODO set state to PARK
                    BleLogger.w(TAG, "ble not powered exiting connecting state")
                    changeState(session, ConnectionHandlerState.FREE)
                }
            }
            ConnectionHandlerAction.EXIT -> {
                expectedConnectionEvent = null
                scannerInterface.connectionHandlerResumeScanning()
            }
            ConnectionHandlerAction.DEVICE_CONNECTION_INITIALIZED -> {
                expectedConnectionEvent = ConnectionHandlerAction.SERVICES_DISCOVERED
                connectionInterface.startServiceDiscovery(session)
            }
            ConnectionHandlerAction.PHY_UPDATED -> {
                expectedConnectionEvent = ConnectionHandlerAction.MTU_UPDATED
                val token = attempt
                mtuSafeGuardJob?.cancel()
                mtuSafeGuardJob = scope.launch {
                    delay(GUARD_TIME_MS)
                    synchronized(mutex) { forAttempt(session, token) { mtuUpdated(session) } }
                }

                connectionInterface.setMtu(session)
            }

            ConnectionHandlerAction.MTU_UPDATED -> {
                if (!servicesDiscoveredReceived) {
                    BleLogger.w(TAG, "Ignoring premature MTU_UPDATED for ${session.address} - SERVICES_DISCOVERED not yet received, likely stale BLE link")
                    return
                }
                expectedConnectionEvent = null
                // Connection completed successfully – disarm the opening watchdog so it does not
                // fire and disconnect an already-open session 30 s from now.
                connectionWatchdogJob?.cancel()
                connectionWatchdogJob = null

                // Reset no-callback backoff state on successful connection.
                session.consecutiveNoCallbackConnects = 0
                session.nextConnectAllowedTimeMs = 0L
                session.pendingWatchdogCancel = false

                // There are devices needing a delay after connection parameters are negotiated and first attribute operation is done
                val token = attempt
                cancelFirstAttributeOperation(session)
                firstAttributeOperationJobs[session.address] = scope.launch {
                    delay(FIRST_ATTRIBUTE_OPERATION_TIMEOUT)
                    synchronized(mutex) {
                        if (firstAttributeOperationJobs.remove(session.address) != null &&
                            session.sessionState == DeviceSessionState.SESSION_OPEN
                        ) {
                            // First attribute operation
                            session.processNextAttributeOperation(false)
                        }
                    }
                }

                // Successful connection — reset backoff state
                reconnectAttempts.remove(session.address)
                reconnectNotBeforeMs.remove(session.address)

                updateSessionState(session, DeviceSessionState.SESSION_OPEN)
                // An observer may immediately close this session and start a new attempt. Never
                // free a newer attempt on the way out of this one.
                forAttempt(session, token) { changeState(session, ConnectionHandlerState.FREE) }
            }

            ConnectionHandlerAction.SERVICES_DISCOVERED -> {
                servicesDiscoveredReceived = true
                expectedConnectionEvent = ConnectionHandlerAction.PHY_UPDATED
                val token = attempt
                phySafeGuardJob?.cancel()
                phySafeGuardJob = scope.launch {
                    delay(GUARD_TIME_MS)
                    synchronized(mutex) { forAttempt(session, token) { phyUpdated(session) } }
                }

                connectionInterface.setPhy(session)
            }

            ConnectionHandlerAction.CONNECT_DEVICE,
            ConnectionHandlerAction.CONNECT_DEVICE_DIRECT -> {
                // Another session is mid-connection. Queue the request instead of discarding it —
                // previously it was dropped, so a device could stay disconnected indefinitely with
                // nothing left to retry it.
                if (session !== current &&
                    (session.sessionState == DeviceSessionState.SESSION_CLOSED ||
                        session.sessionState == DeviceSessionState.SESSION_OPEN_PARK)
                ) {
                    deferConnect(session, action)
                    if (session.sessionState == DeviceSessionState.SESSION_CLOSED) {
                        updateSessionState(session, DeviceSessionState.SESSION_OPEN_PARK)
                    }
                }
            }

            ConnectionHandlerAction.DISCONNECT_DEVICE -> {
                if (session != current) {
                    handleDisconnectDevice(session)
                } else {
                    // Determine whether this disconnect was triggered by our own watchdog timer
                    // (i.e. OS never delivered onConnectionStateChange) vs. a user-requested
                    // disconnect.  Watchdog-triggered cancels are counted for escalating backoff.
                    val isWatchdogCancel = session.pendingWatchdogCancel
                    session.pendingWatchdogCancel = false

                    // cancel pending connection
                    cancelAllSafeGuardJobs()
                    cancelDisconnectSafeGuard(session)
                    cancelFirstAttributeOperation(session)
                    connectionInterface.cancelDeviceConnection(session)
                    observer.deviceConnectionCancelled(session)

                    if (isWatchdogCancel) {
                        session.consecutiveNoCallbackConnects++
                        val backoffMs = minOf(
                            INITIAL_CONNECT_BACKOFF_MS shl minOf(session.consecutiveNoCallbackConnects - 1, 4),
                            MAX_CONNECT_BACKOFF_MS
                        )
                        session.nextConnectAllowedTimeMs = System.currentTimeMillis() + backoffMs
                        BleLogger.w(
                            TAG,
                            "Watchdog-triggered cancel #${session.consecutiveNoCallbackConnects} for ${session.address}" +
                                " – next advertisement-triggered connect gated for ${backoffMs}ms"
                        )
                    } else {
                        // User-requested disconnect: clear watchdog backoff so a fresh openSession works immediately.
                        session.consecutiveNoCallbackConnects = 0
                        session.nextConnectAllowedTimeMs = 0L
                    }

                    updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                    changeState(session, ConnectionHandlerState.FREE)
                }
            }
            ConnectionHandlerAction.DEVICE_DISCONNECTED -> {
                if (current === session) {
                    cancelAllSafeGuardJobs()
                    cancelFirstAttributeOperation(session)
                    if (isTerminalPairingFailure(session)) {
                        BleLogger.w(TAG, "Terminal pairing failure for ${session.address}; stopping automatic reconnection")
                        updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                        changeState(session, ConnectionHandlerState.FREE)
                        return
                    }
                    val attempts = (reconnectAttempts[session.address] ?: 0) + 1
                    reconnectAttempts[session.address] = attempts
                    val backoffMs = minOf(RECONNECT_BACKOFF_BASE_MS * (1L shl (attempts - 1)), RECONNECT_BACKOFF_MAX_MS)
                    reconnectNotBeforeMs[session.address] = System.currentTimeMillis() + backoffMs
                    BleLogger.w(TAG, "Connect attempt $attempts failed for ${session.address}, reconnect backoff ${backoffMs}ms")
                    updateSessionState(session, DeviceSessionState.SESSION_OPEN_PARK)
                    changeState(session, ConnectionHandlerState.FREE)
                } else {
                    handleDeviceDisconnected(session)
                }
            }
            ConnectionHandlerAction.ADVERTISEMENT_HEAD_RECEIVED -> {
                //DO NOTHING
            }
        }
    }

    private fun handleDisconnectDevice(session: BDDeviceSessionImpl) {
        val stackTrace = Throwable().stackTrace
        BleLogger.w(TAG, "handleDisconnectDevice for ${session.address} in state ${session.sessionState}. Call stack: ${stackTrace.take(6).joinToString(" <- ")}")
        when (session.sessionState) {
            DeviceSessionState.SESSION_OPEN_PARK -> {
                cancelDisconnectSafeGuard(session)
                updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
            }
            DeviceSessionState.SESSION_OPEN -> {
                BleLogger.w(TAG, "Disconnecting open session for ${session.address}")
                updateSessionState(session, DeviceSessionState.SESSION_CLOSING)
                connectionInterface.disconnectDevice(session)
                scheduleDisconnectSafeGuard(session)
            }
            DeviceSessionState.SESSION_OPENING -> {
                // Handler is in FREE state but the session is still stuck in SESSION_OPENING
                // (connection attempt started, OS never confirmed it, or late disconnect raced
                // with the connect callback). Cancel whatever GATT object may still exist and
                // move to CLOSED so the caller (e.g. app-side watchdog) gets a SESSION_CLOSED
                // event and scanning can restart cleanly.
                BleLogger.w(TAG, "Disconnect requested for SESSION_OPENING session in FREE state – cancelling GATT and moving to CLOSED")
                connectionInterface.cancelDeviceConnection(session)
                updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
            }
            DeviceSessionState.SESSION_CLOSED,
            DeviceSessionState.SESSION_CLOSING -> {
                //Do nothing
            }
        }
    }

    private fun handleDeviceDisconnected(session: BDDeviceSessionImpl) {
        cancelDisconnectSafeGuard(session)
        cancelFirstAttributeOperation(session)
        when (session.sessionState) {
            DeviceSessionState.SESSION_OPEN -> {
                if (isTerminalPairingFailure(session)) {
                    BleLogger.w(TAG, "Terminal pairing failure for ${session.address}; keeping session closed")
                    updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                } else if (automaticReconnection) {
                    updateSessionState(session, DeviceSessionState.SESSION_OPEN_PARK)
                } else {
                    updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                }
            }
            DeviceSessionState.SESSION_CLOSING -> {
                updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
            }
            DeviceSessionState.SESSION_CLOSED,
            DeviceSessionState.SESSION_OPENING,
            DeviceSessionState.SESSION_OPEN_PARK -> {
                // Do nothing
            }
        }
    }

    private fun isTerminalPairingFailure(session: BDDeviceSessionImpl): Boolean {
        return session.disconnectReason == com.polar.androidcommunications.api.ble.model.BleDeviceSession.DisconnectReason.PAIRING_INFORMATION_REMOVED ||
            session.disconnectReason == com.polar.androidcommunications.api.ble.model.BleDeviceSession.DisconnectReason.PAIRING_NEGOTIATION_FAILED
    }

    /**
     * Runs [action] only while [session] is still the session of connection attempt [token].
     * Guards a late timer from acting on a connection that has already been superseded.
     */
    private inline fun forAttempt(session: BDDeviceSessionImpl, token: Long, action: () -> Unit) {
        if (state == ConnectionHandlerState.CONNECTING && current === session && attempt == token) action()
    }

    /**
     * Queue a connect request that cannot be served right now. An explicit request upgrades a
     * pending automatic retry, but later automatic scheduling never downgrades a user request.
     */
    private fun deferConnect(
        session: BDDeviceSessionImpl,
        action: ConnectionHandlerAction,
        minimumDelayMs: Long = 0,
        automatic: Boolean = false
    ) {
        if (automatic && !automaticReconnection) return
        val existing = retries[session]
        if (existing != null) {
            if (!automatic) {
                existing.automatic = false
                existing.action = action
            }
            return
        }
        val request = PendingConnect(action, automatic)
        retries[session] = request
        armPendingConnect(session, request, minimumDelayMs)
    }

    private fun armPendingConnect(session: BDDeviceSessionImpl, request: PendingConnect, delayMs: Long) {
        request.ready = false
        request.job?.cancel()
        request.job = scope.launch {
            if (delayMs > 0) delay(delayMs)
            synchronized(mutex) {
                if (retries[session] === request) {
                    request.ready = true
                    drainPendingConnects()
                }
            }
        }
    }

    /**
     * Start queued connect requests once the handler is genuinely idle. Requests that are no longer
     * valid (session closed, or an automatic retry after automatic reconnection was disabled) are
     * discarded rather than executed.
     */
    private fun drainPendingConnects() {
        if (commandDepth != 0 || drainingRetries || state != ConnectionHandlerState.FREE) return
        drainingRetries = true
        try {
            while (state == ConnectionHandlerState.FREE && connectionInterface.isPowered) {
                val entry = retries.entries.firstOrNull { it.value.ready } ?: break
                val session = entry.key
                val request = entry.value
                if ((request.automatic && !automaticReconnection) ||
                    session.sessionState != DeviceSessionState.SESSION_OPEN_PARK
                ) {
                    retries.remove(session)?.cancel()
                    continue
                }
                val remaining = session.nextConnectAllowedTimeMs - System.currentTimeMillis()
                if (remaining > 0) {
                    // Still inside the reconnect backoff window – re-arm rather than connect.
                    armPendingConnect(session, request, remaining)
                    continue
                }
                retries.remove(session)?.cancel()
                commandState(session, request.action)
            }
        } finally {
            drainingRetries = false
        }
    }

    /** Bulk removal must not let one removed session's queued retry start midway through cleanup. */
    fun sessionsRemoved(sessions: Collection<BDDeviceSessionImpl>) {
        synchronized(mutex) {
            stateTransaction {
                sessions.forEach { sessionRemoved(it) }
            }
        }
    }

    /**
     * Called when a session object is dropped from the listener's tracking list. If it is still
     * pinned as [current] the handler would otherwise stay in CONNECTING forever referencing an
     * orphaned session, discarding every later request. Release its GATT and return to FREE.
     */
    fun sessionRemoved(session: BDDeviceSessionImpl) {
        synchronized(mutex) {
            stateTransaction {
                retries.remove(session)?.cancel()
                cancelFirstAttributeOperation(session)
                cancelDisconnectSafeGuard(session)
                reconnectAttempts.remove(session.address)
                reconnectNotBeforeMs.remove(session.address)
                if (current === session) {
                    BleLogger.w(TAG, "Current connecting session ${session.address} was removed – cancelling GATT and freeing handler")
                    cancelAllSafeGuardJobs()
                    connectionInterface.cancelDeviceConnection(session)
                    observer.deviceConnectionCancelled(session)
                    if (state == ConnectionHandlerState.CONNECTING) {
                        changeState(session, ConnectionHandlerState.FREE)
                    }
                    current = null
                }
                if (session.sessionState != DeviceSessionState.SESSION_CLOSED) {
                    updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                }
            }
        }
    }

    /**
     * Validate a GATT callback against the session's *current* GATT before running it.
     *
     * Android keeps delivering callbacks from a GATT object that has already been replaced by a
     * reconnect (or torn down by a power-off). Acting on those drives the state machine with events
     * that belong to a dead connection. The check and the action share the handler lock, so a
     * concurrent power-off or watchdog cleanup cannot slip in between them.
     */
    fun dispatchGattCallback(session: BDDeviceSessionImpl, gatt: BluetoothGatt, action: Runnable) {
        synchronized(mutex) {
            if (!poweredOff && session.gatt === gatt) {
                action.run()
            } else {
                BleLogger.d(TAG, "Dropping stale GATT callback for ${session.address} (poweredOff=$poweredOff)")
            }
        }
    }

    /**
     * Queue a direct reconnect for a parked session. Routing this through the retry queue instead of
     * a free-standing timer means it is cancelled by [cancel], [sessionRemoved], a power-off, or by
     * disabling automatic reconnection, rather than firing into a torn-down handler.
     */
    fun scheduleDirectReconnect(session: BDDeviceSessionImpl, delayMs: Long) {
        synchronized(mutex) {
            if (!poweredOff && automaticReconnection &&
                session.sessionState == DeviceSessionState.SESSION_OPEN_PARK && session.directConnect
            ) {
                deferConnect(session, ConnectionHandlerAction.CONNECT_DEVICE_DIRECT, delayMs, automatic = true)
            }
        }
    }

    /**
     * Escape hatch for a Bluetooth power-off. The listener's power-off callback iterates only its
     * tracked session list, so it cannot reach a [current] session that has already been removed
     * from that list. Reset the handler unconditionally so a Bluetooth toggle always clears a
     * wedged CONNECTING state instead of requiring an app restart.
     */
    @JvmOverloads
    fun blePoweredOff(trackedSessions: Collection<BDDeviceSessionImpl> = emptyList()) {
        synchronized(mutex) {
            poweredOff = true
            cancelAllSafeGuardJobs()
            retries.values.forEach { it.cancel() }
            retries.clear()
            firstAttributeOperationJobs.values.forEach { it.cancel() }
            firstAttributeOperationJobs.clear()
            disconnectSafeGuardJobs.values.forEach { it.cancel() }
            disconnectSafeGuardJobs.clear()

            val affected = (trackedSessions + listOfNotNull(current)).distinct()
            val connecting = current
            if (state == ConnectionHandlerState.CONNECTING && connecting != null) {
                changeState(connecting, ConnectionHandlerState.FREE)
            }
            current = null
            for (session in affected) {
                val oldState = session.sessionState
                connectionInterface.cancelDeviceConnection(session)
                observer.deviceDisconnected(session)
                session.pendingWatchdogCancel = false
                if (oldState == DeviceSessionState.SESSION_OPENING ||
                    oldState == DeviceSessionState.SESSION_OPEN ||
                    oldState == DeviceSessionState.SESSION_CLOSING
                ) {
                    // A session the caller explicitly closed must stay closed; only sessions that
                    // lost the link are parked for an automatic reconnect once power returns.
                    val next = if (automaticReconnection && oldState != DeviceSessionState.SESSION_CLOSING) {
                        DeviceSessionState.SESSION_OPEN_PARK
                    } else {
                        DeviceSessionState.SESSION_CLOSED
                    }
                    updateSessionState(session, next)
                }
            }
        }
    }

    @JvmOverloads
    fun blePoweredOn(trackedSessions: Collection<BDDeviceSessionImpl> = emptyList()) {
        synchronized(mutex) {
            poweredOff = false
            for (session in trackedSessions) {
                scheduleDirectReconnect(session, INITIAL_CONNECT_BACKOFF_MS)
            }
        }
    }
}
