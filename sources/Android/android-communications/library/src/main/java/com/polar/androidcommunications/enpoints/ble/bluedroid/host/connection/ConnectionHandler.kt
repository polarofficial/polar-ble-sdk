package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

import androidx.annotation.VisibleForTesting
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState
import com.polar.androidcommunications.common.ble.BleUtils
import com.polar.androidcommunications.common.ble.BleUtils.AD_TYPE
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Connection handler handles connection states serialization, by using simple state pattern
 */
class ConnectionHandler(
    private val connectionInterface: ConnectionInterface,
    private val scannerInterface: ScannerInterface,
    private val observer: ConnectionHandlerObserver
) {
    constructor(
        connectionInterface: ConnectionInterface,
        scannerInterface: ScannerInterface,
        observer: ConnectionHandlerObserver,
        guardTimerScheduler: Scheduler
    ) : this(connectionInterface, scannerInterface, observer) {
        this.guardTimerScheduler = guardTimerScheduler
    }

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

    private var guardTimerScheduler: Scheduler = Schedulers.computation()

    @VisibleForTesting
    var state: ConnectionHandlerState = ConnectionHandlerState.FREE
    private var current: BDDeviceSessionImpl? = null
    private var automaticReconnection = true

    private var phySafeGuardDisposable: Disposable? = null
    private var mtuSafeGuardDisposable: Disposable? = null
    private var firstAttributeOperationDisposable: Disposable? = null

    fun setAutomaticReconnection(automaticReconnection: Boolean) {
        this.automaticReconnection = automaticReconnection
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
        phySafeGuardDisposable?.dispose()
        commandState(bleDeviceSession, ConnectionHandlerAction.PHY_UPDATED)
    }

    fun servicesDiscovered(bleDeviceSession: BDDeviceSessionImpl) {
        commandState(bleDeviceSession, ConnectionHandlerAction.SERVICES_DISCOVERED)
    }

    fun mtuUpdated(bleDeviceSession: BDDeviceSessionImpl) {
        mtuSafeGuardDisposable?.dispose()
        commandState(bleDeviceSession, ConnectionHandlerAction.MTU_UPDATED)
    }

    fun deviceDisconnected(bleDeviceSession: BDDeviceSessionImpl) {
        observer.deviceDisconnected(bleDeviceSession)
        commandState(bleDeviceSession, ConnectionHandlerAction.DEVICE_DISCONNECTED)
    }

    private fun commandState(bleDeviceSession: BDDeviceSessionImpl, action: ConnectionHandlerAction) {
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

    private fun changeState(bleDeviceSession: BDDeviceSessionImpl, newState: ConnectionHandlerState) {
        commandState(bleDeviceSession, ConnectionHandlerAction.EXIT)
        state = newState
        commandState(bleDeviceSession, ConnectionHandlerAction.ENTRY)
    }

    private fun updateSessionState(bleDeviceSession: BDDeviceSessionImpl, newState: DeviceSessionState) {
        BleLogger.d(TAG, " Session update from: " + bleDeviceSession.sessionState.toString() + " to: " + newState.toString())
        bleDeviceSession.sessionState = newState
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
                while (i < uuids!!.size) {
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
                mtuSafeGuardDisposable = Completable.timer(GUARD_TIME_MS, TimeUnit.MILLISECONDS, guardTimerScheduler)
                    .subscribe { mtuUpdated(session) }

                connectionInterface.setMtu(session)
            }

            ConnectionHandlerAction.MTU_UPDATED -> {
                BleUtils.validate(current === session, "incorrect session object")

                // There are devices needing a delay after connection parameters are negotiated and first attribute operation is done
                firstAttributeOperationDisposable?.dispose()
                firstAttributeOperationDisposable = Completable.timer(FIRST_ATTRIBUTE_OPERATION_TIMEOUT, TimeUnit.MILLISECONDS)
                    .subscribe {
                        // First attribute operation
                        session.processNextAttributeOperation(false)
                    }

                updateSessionState(session, DeviceSessionState.SESSION_OPEN)
                changeState(session, ConnectionHandlerState.FREE)
            }

            ConnectionHandlerAction.SERVICES_DISCOVERED -> {
                phySafeGuardDisposable = Completable.timer(GUARD_TIME_MS, TimeUnit.MILLISECONDS, guardTimerScheduler)
                    .subscribe { phyUpdated(session) }

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
                    connectionInterface.cancelDeviceConnection(session)
                    observer.deviceConnectionCancelled(session)
                    updateSessionState(session, DeviceSessionState.SESSION_CLOSED)
                    changeState(session, ConnectionHandlerState.FREE)
                }
            }
            ConnectionHandlerAction.DEVICE_DISCONNECTED -> {
                if (current === session) {
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