package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import com.polar.androidcommunications.BuildConfig
import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.api.ble.BleLogger.Companion.e
import com.polar.androidcommunications.api.ble.BleLogger.Companion.w
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotFound
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.exceptions.BleGattNotInitialized
import com.polar.androidcommunications.api.ble.exceptions.BleNotSupported
import com.polar.androidcommunications.api.ble.exceptions.BleServiceNotFound
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattFactory
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AndroidBuildUtils.Companion.getBrand
import com.polar.androidcommunications.common.ble.AndroidBuildUtils.Companion.getBuildVersion
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.AttributeOperation.AttributeOperationCommand
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDBondingListener.BondingObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Objects
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BDDeviceSessionImpl internal constructor(
    private val context: Context,
    bluetoothDeviceParam: BluetoothDevice,
    private val bleScanCallback: BDScanCallback,
    private val bondingManager: BDBondingListener,
    factory: BleGattFactory
) : BleDeviceSession(),
    BleGattTxInterface {
    override var bluetoothDevice: BluetoothDevice = bluetoothDeviceParam
        set(value) {
            field = value
            resetGatt()
        }
    // gatt is the only shared object between threads
    val gattMutex: Any = Any()
    var serviceDiscovery: Job? = null

    private val attOperations = LinkedBlockingDeque<AttributeOperation>()

    var gatt: BluetoothGatt? = null
        get() {
            synchronized(gattMutex) {
                return field
            }
        }
        set(value) {
            synchronized(gattMutex) {
                field = value
            }
        }
    private val servicesSubscriberAtomicList = AtomicSet<Channel<List<UUID>>>()
    val rssiObservers: AtomicSet<Channel<Int>> = AtomicSet()
    private var scope = CoroutineScope(Dispatchers.IO)
    private var indicatesPairingProblem: Pair<Boolean, Int> = Pair(false, -1)
    private val handler = Handler(context.mainLooper)

    private val brandsNotImplementingAndroid13Api: List<String> =
        mutableListOf("OnePlus", "Oppo", "Realme")

    init {
        indicatesPairingProblem = Pair(false, -1)
        this.clients = factory.getRemoteServices(this)
    }

    @SuppressLint("MissingPermission")
    fun resetGatt() {
        synchronized(gattMutex) {
            if (gatt != null) {
                try {
                    //gatt.disconnect();
                    if (getBuildVersion() > 34) {
                        clearGattCache()
                    }
                    gatt?.close()
                } catch (e: Exception) {
                    e(TAG, "gatt error: $e")
                }
                gatt = null
            }
        }
    }

    /**
     * Internal use only
     *
     * @param sessionState @see BleDeviceSession.DeviceSessionState
     */
    fun setSessionStates(sessionState: DeviceSessionState) {
        this.previousState = this.sessionState
        this.sessionState = sessionState
    }

    private fun logIfError(message: String, status: Int) {
        if (status != 0) {
            e(TAG, "$message Failed with error: $status")
        }
    }

    fun reset() {
        d(TAG, "reset")
        resetGatt()
    }

    override val isNonConnectableAdvertisement: Boolean
        get() = advertisementContent.isNonConnectableAdvertisement

    override val address: String
        get() = bluetoothDevice.address

    @SuppressLint("MissingPermission")
    override suspend fun authenticate() {
        val observer = arrayOf<BondingObserver?>()
        try {
            suspendCancellableCoroutine { continuation ->
                if (sessionState == DeviceSessionState.SESSION_OPEN) {
                    when (bluetoothDevice.bondState) {
                        BluetoothDevice.BOND_NONE -> {
                            if (!bluetoothDevice.createBond()) {
                                continuation.resumeWithException(Throwable("BD bonding start failed"))
                                return@suspendCancellableCoroutine
                            }
                            observer[0] = object : BondingObserver(bluetoothDevice) {
                                override fun bonding() {}
                                override fun bonded() { continuation.resume(Unit) }
                                override fun bondNone() {}
                            }
                            bondingManager.addObserver(observer[0]!!)
                        }
                        BluetoothDevice.BOND_BONDING -> {
                            observer[0] = object : BondingObserver(bluetoothDevice) {
                                override fun bonding() {}
                                override fun bonded() { continuation.resume(Unit) }
                                override fun bondNone() {}
                            }
                            bondingManager.addObserver(observer[0]!!)
                        }
                        BluetoothDevice.BOND_BONDED -> continuation.resume(Unit)
                    }
                    continuation.invokeOnCancellation {
                        observer[0]?.let { bondingManager.removeObserver(it) }
                    }
                } else {
                    continuation.resumeWithException(BleDisconnected())
                }
            }
        } finally {
            observer[0]?.let { bondingManager.removeObserver(it) }
        }
    }

    @get:SuppressLint("MissingPermission")
    override val isAuthenticated: Boolean
        get() = bluetoothDevice.bondState == BluetoothDevice.BOND_BONDED

    override fun clearGattCache(): Boolean {
        var result = false
        synchronized(gattMutex) {
            if (gatt != null) {
                try {
                    val localMethod = gatt!!.javaClass.getMethod("refresh")
                    result = (localMethod.invoke(gatt, *arrayOfNulls(0)) as Boolean)
                } catch (localException: Exception) {
                    e(TAG, "An exception occurred while refreshing device")
                }
            }
        }
        return result
    }

    @SuppressLint("MissingPermission")
    override fun readRssiValue(): kotlinx.coroutines.Deferred<Int> {
        val deferred = kotlinx.coroutines.CompletableDeferred<Int>()
        val channel = Channel<Int>(1)
        rssiObservers.add(channel)
        if (sessionState == DeviceSessionState.SESSION_OPEN) {
            synchronized(gattMutex) {
                if (gatt != null) {
                    if (!gatt!!.readRemoteRssi()) {
                        rssiObservers.remove(channel)
                        channel.close(Throwable("Failed to read rssi"))
                        deferred.completeExceptionally(Throwable("Failed to read rssi"))
                        return deferred
                    }
                } else {
                    rssiObservers.remove(channel)
                    channel.close(Throwable("Gatt not initialized"))
                    deferred.completeExceptionally(Throwable("Gatt not initialized"))
                    return deferred
                }
            }
        } else {
            rssiObservers.remove(channel)
            channel.close(BleDisconnected())
            deferred.completeExceptionally(BleDisconnected())
            return deferred
        }
        scope.launch {
            try {
                val result = withTimeoutOrNull(10_000L) { channel.receive() }
                if (result != null) {
                    deferred.complete(result)
                } else {
                    deferred.completeExceptionally(Throwable("RSSI read timed out"))
                }
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            } finally {
                rssiObservers.remove(channel)
            }
        }
        return deferred
    }

    override fun getIndicatesPairingProblem(): Pair<Boolean, Int> {
        return indicatesPairingProblem
    }

    @SuppressLint("NewApi", "MissingPermission")
    @Throws(Throwable::class)
    private fun sendNextAttributeOperation(operation: AttributeOperation): Boolean {
        val characteristic = operation.characteristic
        synchronized(gattMutex) {
            if (gatt != null) {
                when (operation.attributeOperation) {
                    AttributeOperationCommand.CHARACTERISTIC_READ -> {
                        return gatt!!.readCharacteristic(characteristic)
                    }
                    AttributeOperationCommand.CHARACTERISTIC_WRITE -> {
                        val writeType: Int =
                            if (operation.isWithResponse && (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            } else if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            } else {
                                e(TAG, "Undefined state. BluetoothGattCharacteristic write type cannot be defined.")
                                return false
                            }
                        if (getBuildVersion() >= Build.VERSION_CODES.TIRAMISU) {
                            val status = gatt!!.writeCharacteristic(characteristic, operation.getData()!!, writeType)
                            if (status == BluetoothStatusCodes.SUCCESS) return true
                            e(TAG, "Error: characteristic write failed. Reason: $status")
                            return false
                        } else {
                            characteristic.writeType = writeType
                            @Suppress("DEPRECATION")
                            characteristic.setValue(operation.getData())
                            @Suppress("DEPRECATION")
                            return gatt!!.writeCharacteristic(characteristic)
                        }
                    }
                    AttributeOperationCommand.DESCRIPTOR_WRITE -> {
                        val descriptor = operation.characteristic.getDescriptor(DESCRIPTOR_CCC)
                        val value =
                            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                if (operation.isEnable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                            } else if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                                if (operation.isEnable) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                            } else {
                                return false
                            }
                        gatt!!.setCharacteristicNotification(characteristic, operation.isEnable)

                        //Note, some manufacturers, e.g. OnePlus haven't properly implemented the new API.
                        //Ignore with third party devices.
                        val isThirdPartyDevice = polarDeviceType.isEmpty()
                        if (getBuildVersion() >= Build.VERSION_CODES.TIRAMISU
                            && (isBrandImplementingAndroid13Api(getBrand()) || isThirdPartyDevice)
                        ) {
                            val status = gatt!!.writeDescriptor(descriptor, value)
                            if (status == BluetoothStatusCodes.SUCCESS) return true
                            e(TAG, "Error: descriptor write failed. Reason: $status")
                            indicatesPairingProblem = Pair(true, status)
                            return false
                        } else {
                            d(TAG, "using deprecated descriptor write")
                            @Suppress("DEPRECATION")
                            descriptor.setValue(value)
                            @Suppress("DEPRECATION")
                            return gatt!!.writeDescriptor(descriptor)
                        }
                    }
                    else -> throw BleNotSupported("not supported")
                }
            } else {
                throw BleGattNotInitialized("Attribute operation tried while gatt is uninitialized")
            }
        }
    }

    private fun isBrandImplementingAndroid13Api(brandName: String): Boolean {
        return brandsNotImplementingAndroid13Api.stream()
            .noneMatch { name: String -> name.equals(brandName, ignoreCase = true) }
    }

    override fun gattClientRequestStopScanning() {
        d(TAG, "GATT client request stop scanning")
        handler.post { bleScanCallback.stopScan() }
    }

    override fun gattClientResumeScanning() {
        d(TAG, "GATT client request continue scanning")
        handler.post { bleScanCallback.startScan() }
    }

    override fun transportQueueSize(): Int {
        return attOperations.size
    }

    fun handleDisconnection() {
        d(TAG, "disconnected")
        advertisementContent.resetAdvertisementData()
        attOperations.clear()
        for (gattClient in clients) {
            gattClient.reset()
        }
        ChannelUtils.postDisconnectedAndClearList(servicesSubscriberAtomicList)
        ChannelUtils.postDisconnectedAndClearList(rssiObservers)
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO)
        serviceDiscovery?.cancel()
        serviceDiscovery = null
    }

    // GATT
    @Throws(Exception::class)
    override fun transmitMessages(serviceUuid: UUID, characteristicUuid: UUID, packets: List<ByteArray>, withResponse: Boolean) {
        // note most likely this comes from a different thread
        if (packets != null) {
            for (packet in packets) {
                transmitMessage(serviceUuid, characteristicUuid, packet, withResponse)
            }
        }
    }

    @Throws(Exception::class)
    override fun transmitMessage(serviceUuid: UUID, characteristicUuid: UUID, packet: ByteArray, withResponse: Boolean) {
        // note most likely this comes from a different thread
        synchronized(gattMutex) {
            if (gatt != null && packet != null) {
                for (service in gatt!!.services) {
                    if (service.uuid == serviceUuid) {
                        for (characteristic in service.characteristics) {
                            if (characteristic.uuid == characteristicUuid) {
                                attOperations.add(AttributeOperation(AttributeOperationCommand.CHARACTERISTIC_WRITE, packet, characteristic, withResponse))
                                if (attOperations.size == 1) processNextAttributeOperation(false)
                                return
                            }
                        }
                        throw BleCharacteristicNotFound()
                    }
                }
                throw BleServiceNotFound()
            }
            throw BleGattNotInitialized()
        }
    }

    @Throws(Exception::class)
    override fun readValue(serviceUuid: UUID, characteristicUuid: UUID) {
        synchronized(gattMutex) {
            if (gatt != null) {
                for (service in gatt!!.services) {
                    if (service.uuid == serviceUuid) {
                        for (characteristic in service.characteristics) {
                            if (characteristic.uuid == characteristicUuid) {
                                attOperations.add(AttributeOperation(AttributeOperationCommand.CHARACTERISTIC_READ, characteristic))
                                if (attOperations.size == 1) processNextAttributeOperation(false)
                                return
                            }
                        }
                        throw BleCharacteristicNotFound()
                    }
                }
                throw BleServiceNotFound()
            }
            throw BleGattNotInitialized()
        }
    }

    @Throws(BleCharacteristicNotFound::class, BleServiceNotFound::class, BleGattNotInitialized::class)
    override fun setCharacteristicNotify(serviceUuid: UUID, characteristicUuid: UUID, enable: Boolean) {
        synchronized(gattMutex) {
            if (gatt != null) {
                for (service in gatt!!.services) {
                    if (service.uuid == serviceUuid) {
                        for (characteristic in service.characteristics) {
                            if (characteristic.uuid == characteristicUuid) {
                                if ((characteristic.properties and BleGattBase.PROPERTY_NOTIFY) > 0
                                    || (characteristic.properties and BleGattBase.PROPERTY_INDICATE) > 0
                                ) {
                                    attOperations.add(AttributeOperation(AttributeOperationCommand.DESCRIPTOR_WRITE, characteristic, enable))
                                    if (attOperations.size == 1) processNextAttributeOperation(false)
                                }
                                return
                            }
                        }
                        throw BleCharacteristicNotFound()
                    }
                }
                throw BleServiceNotFound()
            }
            throw BleGattNotInitialized()
        }
    }

    /**
     * Monitor services discovered — suspends until services are discovered or disconnection occurs.
     */
    override fun monitorServicesDiscovered(checkConnection: Boolean): kotlinx.coroutines.Deferred<List<UUID>> {
        val deferred = kotlinx.coroutines.CompletableDeferred<List<UUID>>()
        if (checkConnection && sessionState != DeviceSessionState.SESSION_OPEN) {
            deferred.completeExceptionally(BleDisconnected())
            return deferred
        }
        val channel = Channel<List<UUID>>(1)
        servicesSubscriberAtomicList.add(channel)
        synchronized(gattMutex) {
            if (gatt != null && gatt!!.services.isNotEmpty()) {
                val uuids = gatt!!.services.map { it.uuid }
                channel.trySend(uuids)
            }
        }
        scope.launch {
            try {
                val result = withTimeoutOrNull(30_000L) { channel.receive() }
                if (result != null) {
                    indicatesPairingProblem = Pair(false, -1)
                    deferred.complete(result)
                } else {
                    indicatesPairingProblem = Pair(true, BluetoothGatt.STATE_DISCONNECTED)
                    deferred.completeExceptionally(Throwable("Service discovery timed out"))
                }
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            } finally {
                servicesSubscriberAtomicList.remove(channel)
            }
        }
        return deferred
    }

    override fun isConnected(): Boolean {
        return sessionState == DeviceSessionState.SESSION_OPEN
    }

    val isAuthenticationNeeded: Boolean
        get() {
            synchronized(gattMutex) {
                if (gatt != null) {
                    for (service in gatt!!.services) {
                        val client = fetchClient(service.uuid)
                        if (client != null && client.isEncryptionRequired) return true
                    }
                }
            }
            return false
        }

    fun handleServicesDiscovered() {
        val operations: MutableList<AttributeOperation> = ArrayList()
        val serviceUuids: MutableList<UUID> = ArrayList()
        synchronized(gattMutex) {
            if (gatt != null) {
                for (service in gatt!!.services) {
                    serviceUuids.add(service.uuid)
                    handleServiceDiscovered(operations, service)
                    for (includedService in service.includedServices) {
                        d(TAG, " INCLUDED SERVICE: " + includedService.uuid)
                        serviceUuids.add(includedService.uuid)
                        handleServiceDiscovered(operations, includedService)
                    }
                }
            }
        }
        val uuidList: List<UUID> = ArrayList(serviceUuids)
        ChannelUtils.emitNext(servicesSubscriberAtomicList) { channel ->
            channel.trySend(uuidList)
        }
        operations.sort()
        attOperations.clear()
        attOperations.addAll(operations)
    }

    private fun handleServiceDiscovered(operations: MutableList<AttributeOperation>, service: BluetoothGattService) {
        val client = fetchClient(service.uuid)
        d(TAG, " SERVICE: " + service.uuid.toString())
        if (client != null) {
            client.setServiceDiscovered(true)
            for (characteristic in service.characteristics) {
                d(TAG, "     CHARACTERISTIC: " + characteristic.uuid.toString() + " PROPERTIES: " + characteristic.properties)
                client.processCharacteristicDiscovered(characteristic.uuid, characteristic.properties)
                if (client.containsNotifyCharacteristic(characteristic.uuid)
                    && ((characteristic.properties and BleGattBase.PROPERTY_NOTIFY) != 0 || (characteristic.properties and BleGattBase.PROPERTY_INDICATE) != 0)
                    && client.isAutomatic(characteristic.uuid)
                ) {
                    val operation = AttributeOperation(AttributeOperationCommand.DESCRIPTOR_WRITE, characteristic, true)
                    operation.isPartOfPrimaryService = client.isPrimaryService
                    operations.add(operation)
                }
                if (client.containsCharacteristicRead(characteristic.uuid)
                    && (characteristic.properties and BleGattBase.PROPERTY_READ) != 0
                    && client.isAutomaticRead(characteristic.uuid)
                ) {
                    val operation = AttributeOperation(AttributeOperationCommand.CHARACTERISTIC_READ, characteristic)
                    operation.isPartOfPrimaryService = client.isPrimaryService
                    operations.add(operation)
                }
            }
        } else {
            d(TAG, "No client found for SERVICE: " + service.uuid.toString() + " chrs: " + service.characteristics.size)
        }
    }

    fun handleCharacteristicWrite(service: BluetoothGattService, characteristic: BluetoothGattCharacteristic, status: Int) {
        logIfError("handleCharacteristicWrite uuid: " + characteristic.uuid.toString(), status)
        when (status) {
            BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION, BleGattBase.ATT_INSUFFICIENT_ENCRYPTION -> {
                e(TAG, "Attribute operation write failed due the reason: $status")
                startAuthentication { this.handleAuthenticationComplete() }
                val client = fetchClient(service.uuid)
                if (client != null && client.containsCharacteristic(characteristic.uuid)) {
                    if (!attOperations.isEmpty() && attOperations.peek()?.isWithResponse == true) {
                        client.processServiceDataWrittenWithResponse(characteristic.uuid, status)
                    } else {
                        client.processServiceDataWritten(characteristic.uuid, status)
                    }
                }
            }
            else -> {
                val client = fetchClient(service.uuid)
                if (client != null && client.containsCharacteristic(characteristic.uuid)) {
                    if (!attOperations.isEmpty() && attOperations.peek()?.isWithResponse == true) {
                        client.processServiceDataWrittenWithResponse(characteristic.uuid, status)
                    } else {
                        client.processServiceDataWritten(characteristic.uuid, status)
                    }
                }
                processNextAttributeOperation(true)
            }
        }
    }

    fun handleCharacteristicRead(service: BluetoothGattService, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        logIfError("handleCharacteristicRead uuid: " + characteristic.uuid.toString(), status)
        when (status) {
            BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION, BleGattBase.ATT_INSUFFICIENT_ENCRYPTION -> {
                e(TAG, "Attribute operation read failed due the reason: $status")
                startAuthentication { this.handleAuthenticationComplete() }
                val client = fetchClient(service.uuid)
                if (client != null && client.containsCharacteristic(characteristic.uuid)) {
                    client.processServiceData(characteristic.uuid, value, status, false)
                }
            }
            else -> {
                processNextAttributeOperation(true)
                val client = fetchClient(service.uuid)
                if (client != null && client.containsCharacteristic(characteristic.uuid)) {
                    client.processServiceData(characteristic.uuid, value, status, false)
                }
            }
        }
    }

    fun handleCharacteristicValueUpdated(service: BluetoothGattService, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val client = fetchClient(service.uuid)
        if (client != null && client.containsCharacteristic(characteristic.uuid)) {
            client.processServiceData(characteristic.uuid, value, BleGattBase.ATT_SUCCESS, true)
        } else {
            e(TAG, "Unhandled notification received")
        }
    }

    fun handleDescriptorRead(descriptor: BluetoothGattDescriptor?, value: ByteArray?, status: Int) {
        d(TAG, "onDescriptorRead status: $status")
        processNextAttributeOperation(true)
    }

    fun handleDescriptorWrite(service: BluetoothGattService, characteristic: BluetoothGattCharacteristic, value: ByteArray?, status: Int) {
        d(TAG, "onDescriptorWrite uuid: " + characteristic.uuid.toString() + " status: " + status)
        when (status) {
            BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION, BleGattBase.ATT_INSUFFICIENT_ENCRYPTION -> {
                e(TAG, "Attribute operation descriptor write failed due the reason: $status")
                startAuthentication { this.handleAuthenticationComplete() }
                val disable = byteArrayOf(0x00, 0x00)
                var activated = !disable.contentEquals(value)
                if (status != BleGattBase.ATT_SUCCESS) activated = false
                val client = fetchClient(service.uuid)
                if (client != null && client.containsCharacteristic(characteristic.uuid)) {
                    client.descriptorWritten(characteristic.uuid, activated, status)
                }
            }
            else -> {
                processNextAttributeOperation(true)
                val disable = byteArrayOf(0x00, 0x00)
                var activated = !disable.contentEquals(value)
                if (status != BleGattBase.ATT_SUCCESS) activated = false
                val client = fetchClient(service.uuid)
                if (client != null && client.containsCharacteristic(characteristic.uuid)) {
                    client.descriptorWritten(characteristic.uuid, activated, status)
                }
            }
        }
    }

    fun handleMtuChanged(mtu: Int, status: Int) {
        d(TAG, "handleMtuChanged status: $status mtu: $mtu")
        if (status == BleGattBase.ATT_SUCCESS) {
            for (gattClient in clients) {
                gattClient.setMtuSize(mtu)
            }
        }
    }

    fun handleAuthenticationComplete() {
        processNextAttributeOperation(false)
        for (gattClient in clients) {
            gattClient.authenticationCompleted()
        }
    }

    private fun handleAuthenticationFailed(e: Throwable) {
        processNextAttributeOperation(false)
        for (gattClient in clients) {
            gattClient.authenticationFailed(e)
        }
    }

    fun processNextAttributeOperation(remove: Boolean) {
        if (!attOperations.isEmpty()) {
            try {
                if (remove) {
                    attOperations.take()
                }
                if (!attOperations.isEmpty()) {
                    val operation = Objects.requireNonNull(attOperations.peek())
                    if (BuildConfig.DEBUG) {
                        d(TAG, "send next: " + operation.characteristic.uuid + " op: " + operation.attributeOperation.toString())
                    }
                    try {
                        if (!sendNextAttributeOperation(operation)) {
                            w(TAG, "Attribute operation still pending")
                            // pending operation still in progress, ok case basically
                        }
                    } catch (bleNotSupported: BleNotSupported) {
                        e(TAG, "attribute operation failed due to reason: " + bleNotSupported.localizedMessage)
                        processNextAttributeOperation(true)
                    } catch (gattNotInitialized: BleGattNotInitialized) {
                        e(TAG, "attribute operation failed due to reason gatt not initialized, ALL att operations will be removed")
                        attOperations.clear()
                    } catch (throwable: Throwable) {
                        // fatal / unknown
                        attOperations.clear()
                    }
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun startAuthentication(complete: () -> Unit) {
        scope.launch {
            try {
                delay(500)
                authenticate()
                handler.post { complete() }
            } catch (e: Throwable) {
                handler.post { handleAuthenticationFailed(e) }
            }
        }
    }

    fun handleRssiRead(rssi: Int, status: Int) {
        if (status == BleGattBase.ATT_SUCCESS) {
            ChannelUtils.emitNext(rssiObservers) { channel -> channel.trySend(rssi) }
        } else {
            ChannelUtils.postError(rssiObservers, Throwable("RSSI read failed with status $status"))
        }
    }

    companion object {
        private val DESCRIPTOR_CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val TAG: String = BDDeviceSessionImpl::class.java.simpleName
    }
}