package com.polar.androidcommunications.api.ble.model.gatt

import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.api.ble.BleLogger.Companion.e
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotFound
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.common.ble.AtomicSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Container class holding information from current client or service
 * For client encapsulates all characteristic uuid's , properties and service uuid
 * contains helpers functions for asynchronously monitoring characteristic or service events
 */
abstract class BleGattBase {

    //
    protected var serviceUuid: UUID

    // List of all service characteristics, value is boolean if this is a automatic read/notification
    private val characteristics = HashMap<UUID, Boolean>()

    // List of all service characteristics to read automatically
    private val characteristicsRead = HashMap<UUID, Boolean>()

    // List of all service characteristics to be enabled automatically, pair contains property and atomic boolean
    private val mandatoryNotificationCharacteristics = HashMap<UUID, AtomicInteger>()

    // List of all characteristics that are available
    private val availableCharacteristics = AtomicSet<UUID>()

    // List of all readable characteristics that are available
    private val availableReadableCharacteristics = AtomicSet<UUID>()

    // List of all writable characteristics that are available
    private val availableWritableCharacteristics = AtomicSet<UUID>()

    // transport layer interface
    @JvmField
    val txInterface: BleGattTxInterface

    // current usable mtu size
    @JvmField
    protected val mtuSize: AtomicInteger = AtomicInteger(
        DEFAULT_MTU_SIZE
    )

    // mtu size with att layer
    private val attMtuSize = AtomicInteger(DEFAULT_ATT_MTU_SIZE)

    /**
     * @return true if the current service is the most primary one
     */
    // flag to set client as primary
    @JvmField
    var isPrimaryService: Boolean = false
    protected val serviceDiscovered: AtomicBoolean = AtomicBoolean(false)

    // sets flag that this client/service requires as a whole encryption
    var isEncryptionRequired: Boolean = false
        private set

    protected constructor(txInterface: BleGattTxInterface, serviceUuid: UUID) {
        this.txInterface = txInterface
        this.serviceUuid = serviceUuid
    }

    protected constructor(
        txInterface: BleGattTxInterface,
        serviceUuid: UUID,
        encryptionRequired: Boolean
    ) {
        this.txInterface = txInterface
        this.serviceUuid = serviceUuid
        this.isEncryptionRequired = encryptionRequired
    }

    open fun reset() {
        availableCharacteristics.clear()
        availableReadableCharacteristics.clear()
        availableWritableCharacteristics.clear()
        for (integer in mandatoryNotificationCharacteristics.values) {
            synchronized(integer) {
                integer.set(-1)
                (integer as Object).notifyAll()
            }
        }
        synchronized(serviceDiscovered) {
            serviceDiscovered.set(false)
            (serviceDiscovered as Object).notifyAll()
        }
        mtuSize.set(DEFAULT_MTU_SIZE)
        attMtuSize.set(DEFAULT_ATT_MTU_SIZE)
    }

    /**
     * Callback for GATT service characteristic data processing
     *
     * @param characteristic characteristic UUID
     * @param data           data in byte array
     * @param status         status code of processed data
     * @param notifying      if true data is notification data from GATT service
     */
    abstract fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    )

    abstract fun processServiceDataWritten(characteristic: UUID, status: Int)

    open suspend fun clientReady(checkConnection: Boolean) {
        // override in client if required
    }

    fun authenticationCompleted() {
        // NOTE this informal for client to know that authentication has been completed,
        // link might be encrypted, and device might be bonded
    }

    fun authenticationFailed(reason: Throwable) {
        // NOTE this informal for client to know that authentication has failed
        e(TAG, "authentication failed: $reason")
    }

    open fun processServiceDataWrittenWithResponse(characteristic: UUID, status: Int) {
        // optional, default forward to processServiceDataWritten
        processServiceDataWritten(characteristic, status)
    }

    // only for CCC
    fun descriptorWritten(characteristic: UUID, active: Boolean, status: Int) {
        val integer = getNotificationAtomicInteger(characteristic)
        if (integer != null) {
            synchronized(integer) {
                if (status == ATT_SUCCESS) {
                    if (active) {
                        integer.set(status)
                    } else {
                        integer.set(ATT_NOTIFY_OR_INDICATE_OFF)
                    }
                } else {
                    integer.set(status)
                }
                (integer as Object).notifyAll()
            }
        }
    }

    fun processCharacteristicDiscovered(characteristic: UUID, property: Int) {
        // implement if needed
        addAvailableCharacteristic(characteristic, property)
    }

    val isServiceDiscovered: Boolean
        get() = serviceDiscovered.get()

    fun setServiceDiscovered(discovered: Boolean) {
        synchronized(serviceDiscovered) {
            serviceDiscovered.set(discovered)
            (serviceDiscovered as Object).notifyAll()
        }
    }

    fun containsCharacteristicRead(characteristic: UUID): Boolean {
        return characteristicsRead.containsKey(characteristic)
    }

    fun containsCharacteristic(characteristic: UUID): Boolean {
        return characteristics.containsKey(characteristic)
    }

    fun isAutomaticRead(characteristic: UUID): Boolean {
        return characteristicsRead.containsKey(characteristic) && characteristicsRead[characteristic]!!
    }

    fun isAutomatic(characteristic: UUID): Boolean {
        return characteristics.containsKey(characteristic) && characteristics[characteristic]!!
    }

    fun serviceBelongsToClient(service: UUID): Boolean {
        return serviceUuid == service
    }

    fun containsNotifyCharacteristic(characteristic: UUID): Boolean {
        return mandatoryNotificationCharacteristics.containsKey(characteristic)
    }

    fun getNotificationAtomicInteger(characteristic: UUID): AtomicInteger? {
        if (mandatoryNotificationCharacteristics.containsKey(characteristic)) {
            return mandatoryNotificationCharacteristics[characteristic]
        }
        return null
    }

    private fun contains(characteristic: UUID, uuids: Set<UUID>): Boolean {
        return uuids.contains(characteristic)
    }

    fun getAvailableCharacteristics(): Set<UUID> {
        return availableCharacteristics.objects()
    }

    fun setMtuSize(mtuSize: Int) {
        attMtuSize.set(mtuSize)
        this.mtuSize.set(mtuSize - 3)
    }

    protected fun addCharacteristic(chr: UUID) {
        characteristics[chr] = true
    }

    /**
     * Adds characteristic uuid to be handled by this client, by calling this characteristic shall be auto read after connection establishment <BR></BR>
     *
     * @param characteristicRead <BR></BR>
     */
    protected fun addCharacteristicRead(characteristicRead: UUID) {
        addCharacteristic(characteristicRead, PROPERTY_READ)
    }

    /**
     * Adds notification characteristic uuid to be handled by this client. This will set
     * notification/indication automatic enabled after connection establishment
     *
     * @param characteristic characteristic which notification is set on
     */
    fun addCharacteristicNotification(characteristic: UUID) {
        // Note properties are just informal, as this is used by the client
        d(
            TAG,
            "Added notification characteristic for $characteristic"
        )
        addCharacteristic(characteristic, PROPERTY_NOTIFY or PROPERTY_INDICATE)
    }

    /**
     * Remove notification characteristic from this client. This will remove the
     * notification/indication automatic enable after connection establishment
     *
     * @param characteristic characteristic which notification is set off
     */
    fun removeCharacteristicNotification(characteristic: UUID) {
        d(
            TAG,
            "Remove notification characteristic for $characteristic"
        )
        if (containsNotifyCharacteristic(characteristic)) {
            mandatoryNotificationCharacteristics.remove(characteristic)
        }
        if (containsCharacteristic(characteristic)) {
            characteristics.remove(characteristic)
        }
    }

    protected fun addCharacteristic(characteristic: UUID, properties: Int) {
        if (((properties and PROPERTY_NOTIFY) != 0 || (properties and PROPERTY_INDICATE) != 0) && !containsNotifyCharacteristic(
                characteristic
            )
        ) {
            mandatoryNotificationCharacteristics[characteristic] =
                AtomicInteger(-1)
        }
        if ((properties and PROPERTY_READ) != 0 && !containsCharacteristicRead(characteristic)) {
            characteristicsRead[characteristic] = true
        }
        if (!characteristics.containsKey(characteristic)) {
            characteristics[characteristic] = true
        }
    }

    private fun addAvailableCharacteristic(chr: UUID, property: Int) {
        if (containsCharacteristic(chr) && !contains(chr, availableCharacteristics.objects())) {
            availableCharacteristics.add(chr)
        }
        if ((property and PROPERTY_READ) != 0 && !contains(
                chr,
                availableReadableCharacteristics.objects()
            )
        ) {
            availableReadableCharacteristics.add(chr)
        }
        if (((property and PROPERTY_WRITE) != 0 || (property and PROPERTY_WRITE_NO_RESPONSE) != 0) &&
            !contains(chr, availableWritableCharacteristics.objects())
        ) {
            availableWritableCharacteristics.add(chr)
        }
    }

    protected fun hasAllAvailableReadableCharacteristics(list: Set<UUID>): Boolean {
        return hasCharacteristics(list, availableReadableCharacteristics.objects())
    }

    private fun hasCharacteristics(set: Set<UUID>, list: Set<UUID>): Boolean {
        return list.isNotEmpty() && set.containsAll(list)
    }

    /**
     * Suspend until the service is discovered.
     *
     * @param checkConnection optionally check is currently connected
     * @throws BleDisconnected if connection is lost while waiting
     */
    suspend fun waitServiceDiscovered(checkConnection: Boolean) = withContext(Dispatchers.IO) {
        if (!checkConnection || txInterface.isConnected()) {
            synchronized(serviceDiscovered) {
                if (!serviceDiscovered.get()) {
                    (serviceDiscovered as Object).wait()
                }
                if (!txInterface.isConnected() || !serviceDiscovered.get()) {
                    throw BleDisconnected()
                }
            }
        } else {
            throw BleDisconnected()
        }
    }

    /**
     * Suspend until the notification for the given characteristic is enabled.
     *
     * @param uuid            chr uuid to wait for
     * @param checkConnection optionally check is currently connected
     * @throws BleDisconnected if connection is lost while waiting
     * @throws BleAttributeError if the notification/indication setup failed
     * @throws BleCharacteristicNotFound if the characteristic is not registered
     */
    suspend fun waitNotificationEnabled(uuid: UUID, checkConnection: Boolean) = withContext(Dispatchers.IO) {
        val integer = getNotificationAtomicInteger(uuid)
            ?: throw BleCharacteristicNotFound()
        if (!checkConnection || txInterface.isConnected()) {
            when {
                integer.get() == ATT_SUCCESS -> return@withContext
                integer.get() != -1 -> throw BleAttributeError(
                    "Failed to set characteristic notification or indication ", integer.get()
                )
                else -> {
                    synchronized(integer) {
                        (integer as Object).wait()
                    }
                    if (integer.get() != ATT_SUCCESS) {
                        if (integer.get() != -1) {
                            throw BleAttributeError(
                                "Failed to set characteristic notification or indication ", integer.get()
                            )
                        } else {
                            throw BleDisconnected()
                        }
                    }
                }
            }
        } else {
            throw BleDisconnected()
        }
    }

    companion object {
        private val TAG: String = BleGattBase::class.java.simpleName

        const val DEFAULT_ATT_MTU_SIZE: Int = 23
        private const val DEFAULT_MTU_SIZE = DEFAULT_ATT_MTU_SIZE - 3

        /**
         * Characteristic properties
         */
        const val PROPERTY_BROADCAST: Int = 0x01
        const val PROPERTY_READ: Int = 0x02
        const val PROPERTY_WRITE_NO_RESPONSE: Int = 0x04
        const val PROPERTY_WRITE: Int = 0x08
        const val PROPERTY_NOTIFY: Int = 0x10
        const val PROPERTY_INDICATE: Int = 0x20
        const val PROPERTY_SIGNED_WRITE: Int = 0x40
        const val PROPERTY_EXTENDED_PROPS: Int = 0x80

        /**
         * Permissions, note only in service role
         */
        const val PERMISSION_READ: Int = 0x01
        const val PERMISSION_READ_ENCRYPTED: Int = 0x02
        const val PERMISSION_READ_ENCRYPTED_MITM: Int = 0x04
        const val PERMISSION_WRITE: Int = 0x10
        const val PERMISSION_WRITE_ENCRYPTED: Int = 0x20
        const val PERMISSION_WRITE_ENCRYPTED_MITM: Int = 0x40
        const val PERMISSION_WRITE_SIGNED: Int = 0x80

        /**
         * ATT ERROR CODES, endpoint shall prefer these error codes when calling gatt client callbacks
         */
        const val ATT_SUCCESS: Int = 0
        const val ATT_INVALID_HANDLE: Int = 0x1
        const val ATT_READ_NOT_PERMITTED: Int = 0x2
        const val ATT_WRITE_NOT_PERMITTED: Int = 0x3
        const val ATT_INVALID_PDU: Int = 0x4
        const val ATT_INSUFFICIENT_AUTHENTICATION: Int = 0x5
        const val ATT_REQUEST_NOT_SUPPORTED: Int = 0x6
        const val ATT_INVALID_OFFSET: Int = 0x7
        const val ATT_INSUFFICIENT_AUTHOR: Int = 0x8
        const val ATT_PREPARE_QUEUE_FULL: Int = 0x9
        const val ATT_ATTR_NOT_FOUND: Int = 0xa
        const val ATT_ATTR_NOT_LONG: Int = 0xb
        const val ATT_INSUFFICIENT_KEY_SIZE: Int = 0xc
        const val ATT_INVALID_ATTRIBUTE_LENGTH: Int = 0xd
        const val ATT_UNLIKELY: Int = 0xe
        const val ATT_INSUFFICIENT_ENCRYPTION: Int = 0xf
        const val ATT_UNSUPPORTED_GRP_TYPE: Int = 0x10
        const val ATT_INSUFFICIENT_RESOURCES: Int = 0x11
        const val ATT_NOTIFY_OR_INDICATE_OFF: Int = 0xff

        //0x80-0x9F Application Errors
        //0xA0-0xDF Reserved for future use
        //0xE0-0xFF Common Profile and Service Error Codes
        //          Defined in Core Specification Supplement Part B.
        const val ATT_WRITE_REQUEST_REJECTED: Int = 0xFC
        const val ATT_CCCD_IMPROPERLY_CONFIGURED: Int = 0xFD
        const val ATT_PROCEDURE_ALREADY_IN_PROGRESS: Int = 0xFE
        const val ATT_OUT_OF_RANGE: Int = 0xFF

        const val ATT_UNKNOWN_ERROR: Int = 0x100
    }
}
