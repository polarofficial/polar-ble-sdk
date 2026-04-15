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
        const val GUARD_TIME_MS = 2000L
        const val POLAR_PREFERRED_MTU = 512
        const val MTU_SKIP_NEGOTIATION = 0
        private const val FIRST_ATTRIBUTE_OPERATION_TIMEOUT = 500L
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

    private var phySafeGuardJob: Job? = null
    private var mtuSafeGuardJob: Job? = null
    private var firstAttributeOperationJob: Job? = null
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
            ConnectionHandlerAction.DEVICE_CONNECTION_INITIALIZED,
            ConnectionHandlerAction.PHY_UPDATED,
            ConnectionHandlerAction.SERVICES_DISCOVERED,
            ConnectionHandlerAction.MTU_UPDATED -> {
                BleLogger.d(TAG, "Action $action in free state.")
            }
            ConnectionHandlerAction.CONNECT_DEVICE -> {
                when (session.sessionState) {
                    DeviceSessionState.SESSION_OPEN_PARK,
                    DeviceSessionState.SESSION_CLOSED -> {
                        if (session.isConnectableAdvertisement && containsRequiredUuids(session)) {
                            changeState(session, ConnectionHandlerState.CONNECTING)
                        } else {
                            updateSessionState(session, DeviceSessionState.SESSION_OPEN_PARK)
                        }
                    }
                    DeviceSessionState.SESSION_CLOSING -> {
                        updateSessionState(session, DeviceSessionState.SESSION_OPEN_PARK)
                    }
                    DeviceSessionState.SESSION_OPEN -> {
                        updateSessionState(session, DeviceSessionState.SESSION_OPEN)
                    }
                    DeviceSessionState.SESSION_OPENING -> {
                        //Do nothing
                    }
                }
            }
            ConnectionHandlerAction.ADVERTISEMENT_HEAD_RECEIVED -> {
                // fallback
                if (session.sessionState == DeviceSessionState.SESSION_OPEN_PARK) {
                    if (session.isConnectableAdvertisement && containsRequiredUuids(session)) {
                        changeState(session, ConnectionHandlerState.CONNECTING)
                    } else {
                        BleLogger.d(TAG, "Skipped connection attempt due to reason device is not in connectable advertisement or missing service")
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
                if (connectionInterface.isPowered) {
                    current = session
                    updateSessionState(session, DeviceSessionState.SESSION_OPENING)
                    connectionInterface.connectDevice(session)
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
                // There are devices needing a delay after connection parameters are negotiated and first attribute operation is done
                firstAttributeOperationJob?.cancel()
                firstAttributeOperationJob = scope.launch {
                    delay(FIRST_ATTRIBUTE_OPERATION_TIMEOUT)
                    // First attribute operation
                    session.processNextAttributeOperation(false)
                }

                updateSessionState(session, DeviceSessionState.SESSION_OPEN)
                changeState(session, ConnectionHandlerState.FREE)
            }

            ConnectionHandlerAction.SERVICES_DISCOVERED -> {
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
                    connectionInterface.cancelDeviceConnection(session)
                    observer.deviceConnectionCancelled(session)
                    updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                    changeState(session, ConnectionHandlerState.FREE)
                }
            }
            ConnectionHandlerAction.DEVICE_DISCONNECTED -> {
                if (current === session) {
                    cancelAllSafeGuardJobs()
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
        when (session.sessionState) {
            DeviceSessionState.SESSION_OPEN_PARK -> {
                updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
            }
            DeviceSessionState.SESSION_OPEN -> {
                updateSessionState(session, DeviceSessionState.SESSION_CLOSING)
                connectionInterface.disconnectDevice(session)
            }
            DeviceSessionState.SESSION_CLOSED,
            DeviceSessionState.SESSION_OPENING,
            DeviceSessionState.SESSION_CLOSING -> {
                //Do nothing
            }
        }
    }

    private fun handleDeviceDisconnected(session: BDDeviceSessionImpl) {
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