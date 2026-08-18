package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

import androidx.annotation.VisibleForTesting
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
        ADVERTISEMENT_HEAD_RECEIVED,
        DISCONNECT_DEVICE,
        DEVICE_DISCONNECTED,
        DEVICE_CONNECTION_INITIALIZED,
        PHY_UPDATED,
        SERVICES_DISCOVERED,
        MTU_UPDATED
    }

    @VisibleForTesting
    var state: ConnectionHandlerState = ConnectionHandlerState.FREE
    private var current: BDDeviceSessionImpl? = null
    private var automaticReconnection = true

    private val reconnectAttempts = mutableMapOf<String, Int>()
    private val reconnectNotBeforeMs = mutableMapOf<String, Long>()
    private var servicesDiscoveredReceived = false

    private var phySafeGuardJob: Job? = null
    private var mtuSafeGuardJob: Job? = null
    private var firstAttributeOperationJob: Job? = null
    private var connectionWatchdogJob: Job? = null
    private val disconnectSafeGuardJobs = mutableMapOf<String, Job>()
    private val mutex = Object()

    fun setAutomaticReconnection(automaticReconnection: Boolean) {
        this.automaticReconnection = automaticReconnection
    }

    fun getAutomaticReconnection(): Boolean {
        return this.automaticReconnection
    }

    fun cancel() {
        cancelAllSafeGuardJobs()
        scope.cancel()
    }

    private fun cancelAllSafeGuardJobs() {
        phySafeGuardJob?.cancel()
        phySafeGuardJob = null
        mtuSafeGuardJob?.cancel()
        mtuSafeGuardJob = null
        firstAttributeOperationJob?.cancel()
        firstAttributeOperationJob = null
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = null
        disconnectSafeGuardJobs.values.forEach { it.cancel() }
        disconnectSafeGuardJobs.clear()
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
            if (content.containsKey(AD_TYPE.GAP_ADTYPE_16BIT_MORE) ||
                content.containsKey(AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE)
            ) {
                val uuids = if (content.containsKey(AD_TYPE.GAP_ADTYPE_16BIT_MORE)) content[AD_TYPE.GAP_ADTYPE_16BIT_MORE] else content[AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE]
                var i = 0
                if (uuids != null) {
                    while (i < uuids.size) {
                        val hexUUid = String.format("%02X%02X", uuids[i + 1], uuids[i])
                        if (session.connectionUuids.contains(hexUUid)) {
                            return true
                        }
                        i += 2
                    }
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
            ConnectionHandlerAction.ADVERTISEMENT_HEAD_RECEIVED -> {
                if (session.sessionState == DeviceSessionState.SESSION_OPEN_PARK) {
                    val cooldownUntil = reconnectNotBeforeMs[session.address] ?: 0L
                    if (System.currentTimeMillis() < cooldownUntil) {
                        BleLogger.d(TAG, "Skipping reconnect for ${session.address}, backoff cooldown active (${cooldownUntil - System.currentTimeMillis()}ms remaining)")
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
                    updateSessionState(session, DeviceSessionState.SESSION_OPENING)
                    connectionInterface.connectDevice(session)
                    connectionWatchdogJob?.cancel()
                    connectionWatchdogJob = scope.launch {
                        delay(CONNECTION_WATCHDOG_TIMEOUT_MS)
                        BleLogger.w(TAG, "Connection watchdog triggered: SESSION_OPENING timed out after ${CONNECTION_WATCHDOG_TIMEOUT_MS}ms, forcing disconnect")
                        // Go through the normal disconnectDevice path so the state machine and
                        // mutex are respected, rather than mutating state directly from a coroutine.
                        disconnectDevice(session)
                    }
                } else {
                    // TODO set state to PARK
                    BleLogger.w(TAG, "ble not powered exiting connecting state")
                    changeState(session, ConnectionHandlerState.FREE)
                }
            }
            ConnectionHandlerAction.EXIT -> {
                scannerInterface.connectionHandlerResumeScanning()
            }
            ConnectionHandlerAction.DEVICE_CONNECTION_INITIALIZED -> {
                connectionInterface.startServiceDiscovery(session)
            }
            ConnectionHandlerAction.PHY_UPDATED -> {
                mtuSafeGuardJob?.cancel()
                mtuSafeGuardJob = scope.launch {
                    delay(GUARD_TIME_MS)
                    mtuUpdated(session)
                }

                connectionInterface.setMtu(session)
            }

            ConnectionHandlerAction.MTU_UPDATED -> {
                if (!servicesDiscoveredReceived) {
                    BleLogger.w(TAG, "Ignoring premature MTU_UPDATED for ${session.address} - SERVICES_DISCOVERED not yet received, likely stale BLE link")
                    return
                }
                // Connection completed successfully – disarm the opening watchdog so it does not
                // fire and disconnect an already-open session 30 s from now.
                connectionWatchdogJob?.cancel()
                connectionWatchdogJob = null
                // There are devices needing a delay after connection parameters are negotiated and first attribute operation is done
                firstAttributeOperationJob?.cancel()
                firstAttributeOperationJob = scope.launch {
                    delay(FIRST_ATTRIBUTE_OPERATION_TIMEOUT)
                    // First attribute operation
                    session.processNextAttributeOperation(false)
                }

                // Successful connection — reset backoff state
                reconnectAttempts.remove(session.address)
                reconnectNotBeforeMs.remove(session.address)

                updateSessionState(session, DeviceSessionState.SESSION_OPEN)
                changeState(session, ConnectionHandlerState.FREE)
            }

            ConnectionHandlerAction.SERVICES_DISCOVERED -> {
                servicesDiscoveredReceived = true
                phySafeGuardJob?.cancel()
                phySafeGuardJob = scope.launch {
                    delay(GUARD_TIME_MS)
                    phyUpdated(session)
                }

                connectionInterface.setPhy(session)
            }

            ConnectionHandlerAction.CONNECT_DEVICE -> {
                if (session.sessionState == DeviceSessionState.SESSION_CLOSED) {
                    updateSessionState(session, DeviceSessionState.SESSION_OPEN_PARK)
                }
            }

            ConnectionHandlerAction.DISCONNECT_DEVICE -> {
                if (session != current) {
                    handleDisconnectDevice(session)
                } else {
                    // cancel pending connection
                    cancelAllSafeGuardJobs()
                    cancelDisconnectSafeGuard(session)
                    connectionInterface.cancelDeviceConnection(session)
                    observer.deviceConnectionCancelled(session)
                    updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                    changeState(session, ConnectionHandlerState.FREE)
                }
            }
            ConnectionHandlerAction.DEVICE_DISCONNECTED -> {
                if (current === session) {
                    cancelAllSafeGuardJobs()
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
        when (session.sessionState) {
            DeviceSessionState.SESSION_OPEN -> {
                if (automaticReconnection) {
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
}