package com.polar.androidcommunications.api.ble

import android.bluetooth.le.ScanFilter
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.core.util.Pair
import com.polar.androidcommunications.api.ble.exceptions.BleInvalidMtu
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattFactory
import kotlinx.coroutines.flow.Flow

abstract class BleDeviceListener protected constructor(clients: Set<Class<out BleGattBase>>) {
    /**
     * Pre filter interface for search, to improve memory usage
     */
    fun interface BleSearchPreFilter {
        fun process(content: BleAdvertisementContent): Boolean
    }

    protected var factory: BleGattFactory = BleGattFactory(clients)

    protected var preFilter: BleSearchPreFilter? = null

    /**
     * @return true if bluetooth is active
     */
    abstract fun bleActive(): Boolean

    /**
     * @param cb callback
     */
    abstract fun setBlePowerStateCallback(cb: BlePowerStateChangedCallback)

    interface BlePowerStateChangedCallback {
        /**
         * @param power bt state
         */
        fun stateChanged(power: Boolean)
    }

    /**
     * Restarts the scan
     */
    abstract fun scanRestart()

    /**
     * @param filters scan filter list, android specific
     */
    abstract fun setScanFilters(filters: List<ScanFilter>)

    /**
     * enable to optimize memory usage or disable scan pre filter
     *
     * @param filter policy
     */
    abstract fun setScanPreFilter(filter: BleSearchPreFilter?)

    /**
     * @param enable true enables timer to avoid opportunistic scan, false disables. Default true.
     */
    abstract fun setOpportunisticScan(enable: Boolean)

    /**
     * Produces: onNext:      When a advertisement has been detected <BR></BR>
     * onError:     if scan start fails propagates BleStartScanError with error code <BR></BR>
     * onCompleted: Non produced <BR></BR>
     *
     * @param fetchKnownDevices, fetch known devices means bonded, already connected and already found devices <BR></BR>
     * @return Flow stream <BR></BR>
     */
    abstract fun search(fetchKnownDevices: Boolean): Flow<BleDeviceSession>

    /**
     * Set the preferred MTU. This value will be negotiated between the central and peripheral devices,
     * so it might not always take effect if peripheral is not capable
     *
     *
     * If set to 0 before the connection is created then MTU negotiation is skipped. Value 0 can be
     * used in cases we don't want MTU negotiation, this can be handy with phones we know the MTU
     * negotiation is not working.
     *
     * @param mtu preferred mtu
     */
    @Throws(BleInvalidMtu::class)
    abstract fun setPreferredMtu(@IntRange(from = 0, to = 512) mtu: Int)

    /**
     * Read the preferred MTU. This is not the negotiated MTU, but the value suggested by host
     * during MTU negotiation.
     */
    abstract fun getPreferredMtu(): Int

    /**
     * As java does not support destructor/RAII, Client/App should call this whenever the application is being destroyed
     */
    abstract fun shutDown()

    /**
     * Attempt connection establishment
     *
     * @param session device
     */
    abstract fun openSessionDirect(session: BleDeviceSession)

    /**
     * Acquire connection establishment, BleDeviceSessionStateChangedCallback callbacks are invoked
     *
     * @param session device
     * @param uuids   needed uuids to be found from advertisement data, when reconnecting
     */
    abstract fun openSessionDirect(session: BleDeviceSession, uuids: List<String>)

    /**
     * Produces: onNext: When a device session state has changed, Note use pair.second to check the state (see BleDeviceSession.DeviceSessionState)
     *
     * @return Flow stream
     */
    abstract fun monitorDeviceSessionState(): Flow<Pair<BleDeviceSession, DeviceSessionState>>


    interface BleDeviceSessionStateChangedCallback {
        /**
         * Invoked for all sessions and all state changes
         *
         * @param session check sessionState or session.getPreviousState() for actions
         */
        fun stateChanged(session: BleDeviceSession, sessionState: DeviceSessionState)
    }

    /**
     * Acquires disconnection directly without Observable returned
     *
     * @param session device
     */
    abstract fun closeSessionDirect(session: BleDeviceSession)

    /**
     * @return List of current device sessions known
     */
    abstract fun deviceSessions(): Set<BleDeviceSession?>?

    /**
     * @param address bt address in format 00:11:22:33:44:55
     * @return BleDeviceSession
     */
    abstract fun sessionByAddress(address: String?): BleDeviceSession?

    /**
     * Client app/lib can request to remove device from the list,
     *
     * @param deviceSession @see BleDeviceSession
     * @return true device was removed, false no( means device is considered to be alive )
     */
    abstract fun removeSession(deviceSession: BleDeviceSession): Boolean

    /**
     * @return count of sessions removed
     */
    abstract fun removeAllSessions(): Int

    abstract fun removeAllSessions(inStates: Set<DeviceSessionState?>): Int

    /**
     * enable or disable automatic reconnection, by default true.
     *
     * @param automaticReconnection
     */
    abstract fun setAutomaticReconnection(automaticReconnection: Boolean)

    /**
     * @return current state of automatic reconnection
     */
    abstract fun getAutomaticReconnection(): Boolean?

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(POWER_MODE_NORMAL, POWER_MODE_LOW)
    annotation class PowerMode

    abstract fun setPowerMode(@PowerMode mode: Int)

    companion object {
        const val POWER_MODE_NORMAL: Int = 0
        const val POWER_MODE_LOW: Int = 1
    }

    abstract fun getIndicatesPairingProblem(identifier: String): kotlin.Pair<Boolean, Int>
}