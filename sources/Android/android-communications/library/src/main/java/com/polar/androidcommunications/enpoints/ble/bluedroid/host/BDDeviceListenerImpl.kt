package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.os.Build
import androidx.core.util.Pair
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.api.ble.BleLogger.Companion.e
import com.polar.androidcommunications.api.ble.BleLogger.Companion.w
import com.polar.androidcommunications.api.ble.exceptions.BleInvalidMtu
import com.polar.androidcommunications.api.ble.exceptions.BleNotAvailableInDevice
import com.polar.androidcommunications.api.ble.exceptions.BleStartScanError
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.common.ble.AndroidBuildUtils.Companion.getBrand
import com.polar.androidcommunications.common.ble.AndroidBuildUtils.Companion.getBuildVersion
import com.polar.androidcommunications.common.ble.AndroidBuildUtils.Companion.getModel
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.BleUtils
import com.polar.androidcommunications.common.ble.BleUtils.AD_TYPE
import com.polar.androidcommunications.common.ble.BleUtils.EVENT_TYPE
import com.polar.androidcommunications.common.ble.PhoneUtils.isMtuNegotiationBroken
import com.polar.androidcommunications.common.ble.ChannelUtils
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDPowerListener.BlePowerState
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDScanCallback.BDScanCallbackInterface
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandlerObserver
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionInterface
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ScannerInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BDDeviceListenerImpl(
    private val context: Context,
    clients: MutableSet<Class<out BleGattBase>>
) : BleDeviceListener(clients.toSet()) {

    companion object {
        private val TAG = BDDeviceListenerImpl::class.java.simpleName

        /**
         * Delay before retrying a direct (advertisement-free) connection after a disconnect.
         */
        private const val DIRECT_RECONNECT_DELAY_MS = 5_000L

        /**
         * Maximum number of consecutive automatic retry attempts after a direct-connect
         * session drops to SESSION_OPEN_PARK. After this many failures the library clears
         * the directConnect flag and reverts to normal scan-based reconnect.
         */
        private const val MAX_DIRECT_CONNECT_RETRIES = 3
    }

    var advertisingDeviceNamePrefix: String = "Polar"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val sessions = BDDeviceList()
    private lateinit var gattCallback: GattCallback
    private lateinit var scanCallback: BDScanCallback
    private var btManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val powerManager: BDPowerListener
    private lateinit var bondingManager: BDBondingListener
    private val observers = AtomicSet<Channel<BleDeviceSession>>()
    private lateinit var connectionHandler: ConnectionHandler
    // Replay latest state so late subscribers (e.g. monitor started right before/after openSessionDirect)
    // still receive the current session status.
    private val _deviceSessionStateFlow = MutableSharedFlow<Pair<BleDeviceSession, DeviceSessionState>>(replay = 1, extraBufferCapacity = 64)
    private var changedCallback: BleDeviceSessionStateChangedCallback? = null
    private var powerStateChangedCallback: BlePowerStateChangedCallback? = null
    private var preferredMTU = ConnectionHandler.POLAR_PREFERRED_MTU
    private val scope = CoroutineScope(Dispatchers.IO)
    private var indicatesPairingProblem: kotlin.Pair<Boolean, Int> = kotlin.Pair(false, -1)

    override fun bleActive(): Boolean {
        return bluetoothAdapter.isEnabled
    }

    override fun scanRestart() {
        scanCallback.scanRestart()
    }

    override fun setScanFilters(filters: List<ScanFilter>) {
        scanCallback.setScanFilters(filters)
    }

    override fun setScanPreFilter(filter: BleSearchPreFilter?) {
        this.preFilter = filter
    }

    override fun setOpportunisticScan(enable: Boolean) {
        scanCallback.opportunistic = enable
    }

    override fun getIndicatesPairingProblem(identifier: String): kotlin.Pair<Boolean, Int> {
        val session = sessions.getSession(identifier)
        // If connection is lost the session has likely been nulled already, but check just in case
        if (session != null) {
            val pairingProblem = session.getIndicatesPairingProblem()
            if (pairingProblem.first) {
                return pairingProblem
            }
        }
        val gattPairingProblem = gattCallback.getIndicatesPairingProblem()
        if (gattPairingProblem.first) {
            return gattPairingProblem
        }
        return if (indicatesPairingProblem.first &&
            indicatesPairingProblem.second == BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION
        ) {
            indicatesPairingProblem
        } else {
            kotlin.Pair(false, -1)
        }
    }

    @SuppressLint("MissingPermission")
    override fun search(fetchKnownDevices: Boolean): Flow<BleDeviceSession> {
        return flow {
            val channel = Channel<BleDeviceSession>(Channel.UNLIMITED)
            observers.add(channel)
            try {
                scanCallback.clientAdded()
                if (fetchKnownDevices) {
                    val devices = btManager.getDevicesMatchingConnectionStates(
                        BluetoothProfile.GATT,
                        intArrayOf(BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING)
                    )
                    for (device in devices + bluetoothAdapter.bondedDevices.orEmpty()) {
                        if (device.type != BluetoothDevice.DEVICE_TYPE_LE && device.type != BluetoothDevice.DEVICE_TYPE_DUAL) continue
                        val session = sessions.getSession(device) ?: BDDeviceSessionImpl(
                            context, device, scanCallback, bondingManager, factory
                        ).also { sessions.addSession(it) }
                        // Fill missing identity only; cached names must not replace advertised names.
                        if (session.name.isEmpty()) {
                            session.advertisementContent.advertisementDeviceNamePrefix = advertisingDeviceNamePrefix
                            device.name?.let { session.advertisementContent.processName(it) }
                        }
                    }
                    for (session in sessions.copyDeviceList()) {
                        emit(session)
                    }
                }
                for (item in channel) {
                    emit(item)
                }
            } finally {
                observers.remove(channel)
                channel.close()
                scanCallback.clientRemoved()
            }
        }
    }

    @Throws(BleInvalidMtu::class)
    override fun setPreferredMtu(mtu: Int) {
        if (mtu >= 0) {
            preferredMTU = mtu
        } else {
            throw BleInvalidMtu()
        }
    }

    override fun getPreferredMtu(): Int {
        return preferredMTU
    }

    override fun shutDown() {
        bondingManager.stopBroadcastReceiver()
        powerManager.stopBroadcastReceiver()
        // Stop the handler from scheduling new work before the sessions disappear underneath it.
        connectionHandler.setAutomaticReconnection(false)
        // Gate callbacks and release every session's GATT through the handler, so its own state
        // (current session, watchdogs, queued retries) is torn down in step with the sessions.
        connectionHandler.blePoweredOff(sessions.sessions.objects())
        scanCallback.stopScan()
        sessions.sessions.clear()
        gattCallback.cancel()
        connectionHandler.cancel()
        scope.cancel()
    }

    override fun deviceSessions(): Set<BleDeviceSession> {
        return sessions.copyDeviceList()
    }

    override fun sessionByAddress(address: String?): BleDeviceSession {
        var session = address?.let { sessions.getSession(it) }
        if (session == null) {
            session = BDDeviceSessionImpl(
                context,
                bluetoothAdapter.getRemoteDevice(address),
                scanCallback,
                bondingManager,
                factory
            )
            sessions.addSession(session)
        }
        return session
    }

    override fun connectToBondedDevice(address: String) {
        val session = sessionByAddress(address) as BDDeviceSessionImpl
        // Reset the retry counter so the caller always gets a full retry budget,
        // then clear any stale UUIDs so service discovery runs fresh.
        session.resetDirectConnect()
        session.connectionUuids = ArrayList()
        connectionHandler.connectDeviceDirect(session, bleActive())
    }

    override fun removeSession(deviceSession: BleDeviceSession): Boolean {
        if (deviceSession.sessionState == DeviceSessionState.SESSION_CLOSED
            && !deviceSession.isAdvertising(30, TimeUnit.SECONDS)
            && sessions.sessions.contains(deviceSession as BDDeviceSessionImpl)
        ) {
            sessions.sessions.remove(deviceSession)
            // Ensure ConnectionHandler does not keep pointing at a removed session as 'current',
            // which would orphan it and wedge CONNECTING forever.
            connectionHandler.sessionRemoved(deviceSession)
            return true
        }
        return false
    }

    override fun removeAllSessions(): Int {
        return removeAllSessions(HashSet(listOf(DeviceSessionState.SESSION_CLOSED)))
    }

    override fun removeAllSessions(inStates: Set<DeviceSessionState?>): Int {
        val removedSessions = ArrayList<BDDeviceSessionImpl>()
        val list = sessions.copyDeviceList()
        for (session in list) {
            if (inStates.contains(session.sessionState)
                && sessions.sessions.contains(session as BDDeviceSessionImpl)
            ) {
                sessions.sessions.remove(session)
                removedSessions.add(session)
            }
        }
        connectionHandler.sessionsRemoved(removedSessions)
        return removedSessions.size
    }

    override fun setPowerMode(@PowerMode mode: Int) {
        when (mode) {
            POWER_MODE_NORMAL -> {
                scanCallback.stopScan()
                scanCallback.lowPowerEnabled = false
                scanCallback.startScan()
            }
            POWER_MODE_LOW -> {
                scanCallback.stopScan()
                scanCallback.lowPowerEnabled = true
                scanCallback.startScan()
            }
        }
    }

    private val connectionInterface: ConnectionInterface = object : ConnectionInterface {
        @SuppressLint("NewApi", "MissingPermission")
        override fun connectDevice(session: BDDeviceSessionImpl?) {
            var gatt: BluetoothGatt? = null
            if (getBuildVersion() >= Build.VERSION_CODES.O) {
                var mask = BluetoothDevice.PHY_LE_1M_MASK
                if (bluetoothAdapter.isLe2MPhySupported) mask = mask or BluetoothDevice.PHY_LE_2M_MASK
                d(TAG, "Attempt connect to device " + session?.name + " with the mask " + mask)
                if (session != null) {
                    gatt = session.bluetoothDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, mask)
                }
            } else {
                d(TAG, "Attempt connect to device " + session?.name)
                if (session != null) {
                    gatt = session.bluetoothDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                }
            }
            if (session != null) {
                session.gatt = gatt
            }
        }

        @SuppressLint("MissingPermission", "NewApi")
        override fun setPhy(session: BDDeviceSessionImpl?) {
            if (session?.gatt != null) {
                if (getBuildVersion() >= Build.VERSION_CODES.O) {
                    synchronized(session.gattMutex) {
                        session.gatt?.setPreferredPhy(
                            BluetoothDevice.PHY_LE_2M_MASK,
                            BluetoothDevice.PHY_LE_2M_MASK,
                            BluetoothDevice.PHY_OPTION_NO_PREFERRED
                        )
                    }
                } else {
                    gattCallback.onPhyUpdate(session.gatt!!, BluetoothDevice.PHY_LE_1M_MASK, BluetoothDevice.PHY_LE_1M_MASK, 0)
                }
            }
        }

        @SuppressLint("MissingPermission", "NewApi")
        override fun readPhy(session: BDDeviceSessionImpl?) {
            if (session?.gatt != null) {
                if (getBuildVersion() >= Build.VERSION_CODES.O) {
                    session.gatt!!.readPhy()
                } else {
                    gattCallback.onPhyUpdate(session.gatt!!, BluetoothDevice.PHY_LE_1M_MASK, BluetoothDevice.PHY_LE_1M_MASK, 0)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun disconnectDevice(session: BDDeviceSessionImpl?) {
            synchronized(session!!.gattMutex) {
                if (session.gatt != null) session.gatt!!.disconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun cancelDeviceConnection(session: BDDeviceSessionImpl?) {
            // Disconnect then close immediately to free the GATT client_if slot without
            // waiting for STATE_DISCONNECTED, which may never arrive in the no-callback scenario.
            synchronized(session!!.gattMutex) {
                if (session.gatt != null) session.gatt!!.disconnect()
            }
            session.resetGatt()
        }

        @SuppressLint("MissingPermission")
        override fun setMtu(session: BDDeviceSessionImpl?): Boolean {
            var result = false
            if (preferredMTU == ConnectionHandler.MTU_SKIP_NEGOTIATION || isMtuNegotiationBroken(getBrand(), getModel())) {
                if (session!!.gatt != null) {
                    gattCallback.onMtuChanged(session.gatt!!, BleGattBase.DEFAULT_ATT_MTU_SIZE, 0)
                }
                result = true
            } else {
                synchronized(session!!.gattMutex) {
                    if (session.gatt != null) {
                        result = session.gatt!!.requestMtu(preferredMTU)
                    }
                }
            }
            return result
        }

        @SuppressLint("MissingPermission")
        override fun startServiceDiscovery(session: BDDeviceSessionImpl?): Boolean {
            var result = false
            synchronized(session!!.gattMutex) {
                if (session.gatt != null) {
                    session.gatt!!.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                    result = session.gatt!!.discoverServices()
                }
            }

            // Protection mechanism if service discovery won't complete
            session.serviceDiscovery?.cancel()
            // Capture the GATT this discovery was started on. If a reconnect swapped it in the
            // meantime the timeout must not fabricate a failure for the new connection.
            val discoveryGatt = session.gatt
            session.serviceDiscovery = scope.launch {
                delay(10_000L)
                e(TAG, "service discovery timed out")
                if (discoveryGatt != null) {
                    connectionHandler.dispatchGattCallback(session, discoveryGatt) {
                        session.markDisconnect(
                            BleDeviceSession.DisconnectReason.SERVICE_DISCOVERY_FAILED,
                            BluetoothGatt.GATT_FAILURE
                        )
                        indicatesPairingProblem = kotlin.Pair(false, -1)
                        gattCallback.onServicesDiscovered(discoveryGatt, BluetoothGatt.GATT_FAILURE)
                    }
                }
            }
            return result
        }

        override val isPowered: Boolean
            get() = bleActive()
    }

    private val scanCallbackInterface: BDScanCallbackInterface = object : BDScanCallbackInterface {
        override fun isScanningNeeded(): Boolean {
            return observers.size() != 0 || sessions.fetch(
                { session: BDDeviceSessionImpl? -> session?.sessionState == DeviceSessionState.SESSION_OPEN_PARK }
            ) != null
        }

        override fun scanStartError(error: String) {
            e(TAG, "scanStartError $error")
            ChannelUtils.postError(observers, BleStartScanError(error))
        }

        @SuppressLint("MissingPermission")
        override fun deviceDiscovered(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray, type: EVENT_TYPE) {
            var deviceSession = sessions.getSession(device)
            val advData = BleUtils.advertisementBytes2Map(scanRecord)
            val manufacturer = Build.MANUFACTURER
            val name = device.name
            if (name != null && manufacturer.equals("samsung", ignoreCase = true)) {
                advData.remove(AD_TYPE.GAP_ADTYPE_LOCAL_NAME_SHORT)
                advData[AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE] = name.toByteArray()
            }

            if (deviceSession == null) {
                val content = BleAdvertisementContent()
                content.advertisementDeviceNamePrefix = advertisingDeviceNamePrefix
                content.processAdvertisementData(advData, type, rssi)
                if (preFilter == null || preFilter!!.process(content)) {
                    if (content.polarHrAdvertisement.isPresent
                        && content.polarDeviceIdInt != 0L
                        && (content.polarDeviceType == "H10" || content.polarDeviceType == "H9")
                    ) {
                        val oldSession = sessions.fetch { s: BDDeviceSessionImpl? ->
                            s?.getAdvertisementContent?.polarDeviceId == content.polarDeviceId
                        }
                        if (oldSession != null
                            && (oldSession.sessionState == DeviceSessionState.SESSION_CLOSED
                                    || oldSession.sessionState == DeviceSessionState.SESSION_OPEN_PARK)
                        ) {
                            d(TAG, "old polar device found name: " + oldSession.advertisementContent.name)
                            oldSession.bluetoothDevice = device
                            deviceSession = oldSession
                            deviceSession.getAdvertisementContent.processAdvertisementData(advData, type, rssi)
                        }
                    }
                    if (deviceSession == null) {
                        deviceSession = BDDeviceSessionImpl(context, device,
                            scanCallback, bondingManager, factory)
                        deviceSession.getAdvertisementContent.advertisementDeviceNamePrefix = advertisingDeviceNamePrefix
                        deviceSession.getAdvertisementContent.processAdvertisementData(advData, type, rssi)
                        d(TAG, "new device allocated name: " + deviceSession.getAdvertisementContent.name)
                        sessions.addSession(deviceSession)
                    }
                } else {
                    return
                }
            } else {
                deviceSession.advertisementContent.processAdvertisementData(advData, type, rssi)
                d(TAG, "adv update addr=${device.address} type=$type sessionState=${deviceSession.sessionState} connectable=${deviceSession.isConnectableAdvertisement}")
            }

            connectionHandler.advertisementHeadReceived(deviceSession)
            val finalDeviceSession = deviceSession
            ChannelUtils.emitNext(observers) { observer ->
                observer.trySend(finalDeviceSession)
            }
        }
    }

    private val scannerInterface: ScannerInterface = object : ScannerInterface {
        override fun connectionHandlerResumeScanning() {
            scanCallback.startScan()
        }

        override fun connectionHandlerRequestStopScanning() {
            scanCallback.stopScan()
        }
    }

    private val connectionHandlerObserver: ConnectionHandlerObserver = object : ConnectionHandlerObserver {
        override fun deviceSessionStateChanged(session: BDDeviceSessionImpl) {
            if (sessions.fetch { s: BDDeviceSessionImpl? -> s?.sessionState == DeviceSessionState.SESSION_OPEN_PARK } != null) {
                scanCallback.clientAdded()
            } else {
                scanCallback.clientRemoved()
            }

            // When a direct-connect session drops to SESSION_OPEN_PARK, schedule a re-connection
            // attempt without waiting for an advertisement. After MAX_DIRECT_CONNECT_RETRIES
            // consecutive failures we stop retrying and revert to scan-based reconnect.
            if (session.sessionState == DeviceSessionState.SESSION_OPEN_PARK && session.directConnect) {
                val attempt = session.incrementDirectConnectRetryCount()
                if (attempt > MAX_DIRECT_CONNECT_RETRIES) {
                    w(TAG, "Direct-connect retry budget exhausted for ${session.address} — reverting to scan-based reconnect")
                    session.resetDirectConnect()
                } else {
                    d(TAG, "Direct-connect session parked, scheduling retry $attempt/$MAX_DIRECT_CONNECT_RETRIES for ${session.address}")
                    // Queue through the handler instead of a free-standing timer, so the retry is
                    // cancelled by shutdown, session removal, power-off or disabling reconnection.
                    connectionHandler.scheduleDirectReconnect(session, DIRECT_RECONNECT_DELAY_MS)
                }
            }

            if (session.sessionState == DeviceSessionState.SESSION_OPEN_PARK &&
                session.previousState == DeviceSessionState.SESSION_OPEN
            ) {
                changedCallback?.stateChanged(session, DeviceSessionState.SESSION_CLOSED)
                _deviceSessionStateFlow.tryEmit(Pair(session, DeviceSessionState.SESSION_CLOSED))
                if (session.sessionState == DeviceSessionState.SESSION_OPEN_PARK) {
                    changedCallback?.stateChanged(session, DeviceSessionState.SESSION_OPEN_PARK)
                    _deviceSessionStateFlow.tryEmit(Pair(session, DeviceSessionState.SESSION_OPEN_PARK))
                }
            } else {
                changedCallback?.stateChanged(session, session.sessionState)
                _deviceSessionStateFlow.tryEmit(Pair(session, session.sessionState))
            }
        }

        override fun deviceConnected(session: BDDeviceSessionImpl) {}

        override fun deviceDisconnected(session: BDDeviceSessionImpl) {
            session.handleDisconnection()
            session.reset()
        }

        override fun deviceConnectionCancelled(session: BDDeviceSessionImpl) {
            session.reset()
        }
    }

    private val blePowerStateListener: BlePowerState = object : BlePowerState {
        override fun blePoweredOff() {
            e(TAG, "BLE powered off")
            scanCallback.powerOff()
            powerStateChangedCallback?.stateChanged(false)
            indicatesPairingProblem = kotlin.Pair(true, BluetoothGatt.STATE_DISCONNECTED)
            // Handled inside the handler: the tracked list cannot reach a session that is still
            // pinned as 'current' after being removed, and driving synthetic disconnects through
            // the GATT callback races with the handler's own cleanup.
            connectionHandler.blePoweredOff(sessions.sessions.objects())
        }

        override fun blePoweredOn() {
            d(TAG, "BLE powered on")
            connectionHandler.blePoweredOn(sessions.sessions.objects())
            scanCallback.powerOn()
            powerStateChangedCallback?.stateChanged(true)
        }
    }

    init {
        btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter
        connectionHandler = ConnectionHandler(connectionInterface, scannerInterface, connectionHandlerObserver)
        gattCallback = GattCallback(connectionHandler, sessions)
        bondingManager = BDBondingListener(context)
        scanCallback = BDScanCallback(context, bluetoothAdapter, scanCallbackInterface)
        powerManager = BDPowerListener(bluetoothAdapter, context, blePowerStateListener)
    }

    override fun setBlePowerStateCallback(cb: BlePowerStateChangedCallback) {
        this.powerStateChangedCallback = cb
        cb.stateChanged(this.bleActive())
    }

    @SuppressLint("MissingPermission")
    override fun openSessionDirect(session: BleDeviceSession) {
        session.connectionUuids = ArrayList()
        val bdSession = session as BDDeviceSessionImpl
        val powered = bleActive()
        if (powered &&
            btManager.getConnectionState(bdSession.bluetoothDevice, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED) {
            // Share an existing Android link without waiting for advertisements.
            // A bond alone does not establish that the device is currently reachable.
            bdSession.resetDirectConnect()
            connectionHandler.connectDeviceDirect(bdSession, powered)
        } else {
            connectionHandler.connectDevice(bdSession, powered)
        }
    }

    override fun openSessionDirect(session: BleDeviceSession, uuids: List<String>) {
        session.connectionUuids = uuids.toMutableList()
        connectionHandler.connectDevice(session as BDDeviceSessionImpl, bleActive())
    }

    override fun closeSessionDirect(session: BleDeviceSession) {
        // Stop any pending direct-reconnect loop before disconnecting.
        (session as BDDeviceSessionImpl).resetDirectConnect()
        connectionHandler.disconnectDevice(session)
    }

    override fun setAutomaticReconnection(automaticReconnection: Boolean) {
        connectionHandler.setAutomaticReconnection(automaticReconnection)
    }

    override fun getAutomaticReconnection(): Boolean? {
        return connectionHandler.getAutomaticReconnection()
    }

    /**
     * Monitor device session state changes as a coroutine Flow.
     *
     * @return Flow emitting Pair of BleDeviceSession and its new DeviceSessionState
     */
    override fun monitorDeviceSessionState(): Flow<Pair<BleDeviceSession, DeviceSessionState>> {
        return _deviceSessionStateFlow.asSharedFlow()
    }
}