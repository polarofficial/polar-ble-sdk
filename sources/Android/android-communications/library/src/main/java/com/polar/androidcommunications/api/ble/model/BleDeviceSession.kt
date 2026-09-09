package com.polar.androidcommunications.api.ble.model

import android.bluetooth.BluetoothDevice
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.advertisement.BlePolarHrAdvertisement
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import kotlinx.coroutines.Deferred
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Bluetooth le device class, contains all essential api's for sufficient usage of bluetooth le device
 */
abstract class BleDeviceSession
/**
 * Methods
 *
 *
 * Class constructor
 */
protected constructor() {
    enum class DisconnectReason {
        NONE,
        CONNECTION_LOST,
        INSUFFICIENT_ENCRYPTION,
        INSUFFICIENT_AUTHENTICATION,
        // Reserved for parity with iOS's CBError.encryptionTimedOut; no known Android GATT
        // status currently maps to this, so it is never produced by GattCallback today. If a
        // producer is added, mirror iOS: only RETRY_CONNECTION when the session never reached
        // markConnectionEstablished()-equivalent state, otherwise NONE (routine reconnect).
        ENCRYPTION_TIMEOUT,
        PAIRING_INFORMATION_REMOVED,
        PAIRING_NEGOTIATION_FAILED,
        SERVICE_DISCOVERY_FAILED,
        GATT_ERROR,
        // The remote device actively terminated the link (e.g. HCI "remote user terminated" /
        // "remote power off"), as opposed to the connection merely timing out.
        PEER_TERMINATED,
        // Disconnect is the expected outcome of a device-control command (restart, factory
        // reset, warehouse sleep, hibernate, turn off) whose success path was already reached.
        DEVICE_COMMAND,
        UNKNOWN
    }

    /** Device-control command that a DEVICE_COMMAND disconnect is expected to follow. */
    enum class DeviceCommand {
        RESTART,
        FACTORY_RESET,
        WAREHOUSE_SLEEP,
        HIBERNATE,
        TURN_OFF
    }

    var disconnectReason: DisconnectReason = DisconnectReason.NONE
        protected set
    var disconnectStatus: Int? = null
        protected set
    var isIntentionalDisconnect: Boolean = false
        protected set
    // Deadline until which a pending DEVICE_COMMAND marking is honored; prevents a stale mark
    // from mislabeling a later, unrelated disconnect if the expected one never arrives.
    var deviceCommandDisconnectDeadlineMs: Long = 0L
        protected set
    var pendingDeviceCommand: DeviceCommand? = null
        protected set
    var wasBondedAtConnection: Boolean = false
        protected set
    var hasEstablishedBond: Boolean = false
        protected set
    var securityRecoveryAttempts: Int = 0
        protected set
    var securityRecoveryExhausted: Boolean = false
        protected set

    fun markBondedAtConnection(bonded: Boolean) {
        wasBondedAtConnection = bonded
        if (bonded) {
            hasEstablishedBond = true
        }
        securityRecoveryAttempts = 0
        securityRecoveryExhausted = false
    }

    fun recordSecurityRecoveryAttempt(maxAttempts: Int = 2): Boolean {
        securityRecoveryAttempts += 1
        securityRecoveryExhausted = securityRecoveryAttempts >= maxAttempts
        return securityRecoveryExhausted
    }

    fun markDisconnect(reason: DisconnectReason, status: Int? = null) {
        disconnectReason = reason
        disconnectStatus = status
    }

    fun markIntentionalDisconnect() {
        isIntentionalDisconnect = true
        markDisconnect(DisconnectReason.CONNECTION_LOST)
    }

    /** Call right after a device-control command's success path (e.g. GATT write acked) to mark the disconnect that is expected to follow as non-actionable. */
    fun markExpectedDeviceCommandDisconnect(command: DeviceCommand, validForMs: Long = 60_000L) {
        // Once accepted, a reset-family command already has the device on its way out; a second
        // such command issued before that disconnect arrives is unlikely to be the real cause, so
        // keep attributing the upcoming disconnect to whichever command was marked first.
        if (isExpectedDeviceCommandDisconnectStillValid()) {
            return
        }
        pendingDeviceCommand = command
        deviceCommandDisconnectDeadlineMs = System.currentTimeMillis() + validForMs
        markDisconnect(DisconnectReason.DEVICE_COMMAND)
    }

    fun isExpectedDeviceCommandDisconnectStillValid(): Boolean =
        disconnectReason == DisconnectReason.DEVICE_COMMAND && System.currentTimeMillis() <= deviceCommandDisconnectDeadlineMs

    fun clearDisconnectReason() {
        disconnectReason = DisconnectReason.NONE
        disconnectStatus = null
        isIntentionalDisconnect = false
        deviceCommandDisconnectDeadlineMs = 0L
        pendingDeviceCommand = null
    }

    /**
     * Connection state
     */
    enum class DeviceSessionState {
        /**
         * Disconnected state
         */
        SESSION_CLOSED,

        /**
         * Connection attempting/connecting at the moment
         */
        SESSION_OPENING,

        /**
         * Device is disconnected, but is waiting for advertisement head or ble power on for reconnection
         */
        SESSION_OPEN_PARK,

        /**
         * Device is connected
         */
        SESSION_OPEN,

        /**
         * Disconnecting at the moment
         */
        SESSION_CLOSING,
    }

    /**
     * @return get current state (DeviceSessionState.SESSION_XXXXX)
     */
    /**
     * Members
     */
    var sessionState: DeviceSessionState = DeviceSessionState.SESSION_CLOSED

    /**
     * @return state before current one
     */
    var previousState: DeviceSessionState = DeviceSessionState.SESSION_CLOSED
        protected set
    protected lateinit var clients: Set<BleGattBase>

    // needs to be set by 'upper' class
    var advertisementContent: BleAdvertisementContent = BleAdvertisementContent()

    /**
     * @return advertisement content object
     */
    val getAdvertisementContent: BleAdvertisementContent
        get() = advertisementContent

    /**
     * Set advertisement content object
     */
    /*fun setAdvertisementContent(content: BleAdvertisementContent) {
        advertisementContent = content
    }*/

    /**
     * @return current connection uuid's needed for connection attempt
     */
    var connectionUuids: MutableList<String> = ArrayList()
        /**
         * @param uuids set connection uuid's
         */
        set(uuids) {
            field.clear()
            field.addAll(uuids)
        }

    /**
     * @param time     max time for the last advertisement event
     * @param timeUnit desired timeunit
     * @return true, device is still somewhat considered "alive"
     */
    fun isDeviceAlive(time: Long, timeUnit: TimeUnit): Boolean {
        val deltaTime =
            (System.currentTimeMillis() / 1000L) - advertisementContent.advertisementTimeStamp
        val sessionState = sessionState
        return (sessionState == DeviceSessionState.SESSION_OPEN || sessionState == DeviceSessionState.SESSION_OPENING || sessionState == DeviceSessionState.SESSION_CLOSING) ||
                (advertisementContent.getAdvertisementData().size != 0 && deltaTime <= timeUnit.toSeconds(
                    time
                ))
    }

    /**
     * @param time     max time for the last advertisement event
     * @param timeUnit desired timeunit
     * @return true if device is advertising in the required timespan
     */
    fun isAdvertising(time: Long, timeUnit: TimeUnit): Boolean {
        val deltaTime =
            (System.currentTimeMillis() / 1000L) - advertisementContent.advertisementTimeStamp
        return advertisementContent.getAdvertisementData().isNotEmpty() && deltaTime <= timeUnit.toSeconds(
            time
        )
    }

    /**
     * @return true if device is in non-connectable advertisement<BR></BR>
     * Notes: Depending on Android API version,it's impossible to know this.<BR></BR>
    * So this would work only for Polar Devices that follows Polar BLE Advertising data spec
     */
    abstract val isNonConnectableAdvertisement: Boolean

    val isConnectableAdvertisement: Boolean
        /**
         * @return true if device is connectable advertisement<BR></BR>
         * Notes: Depending on Android API version,it's impossible to know this.<BR></BR>
         * So this would work only for Polar Devices that follows Polar BLE Advertising data spec
         */
        get() = !isNonConnectableAdvertisement

    /**
     * @return bluetooth device address in string format
     */
    abstract val address: String?

    /**
     * start pairing and optionally bonding procedure with the device
     * @return Either produces complete for already bonded device or starts pairing/bonding procedure
     * or onError on failure case
     */
    abstract suspend fun authenticate()

    /**
     * @return true if session is bonded device
     */
    abstract val isAuthenticated: Boolean

    /**
     * Monitor services discovered
     *
     * @return Deferred result
     */
    abstract fun monitorServicesDiscovered(checkConnection: Boolean): Deferred<List<UUID>>

    /**
     * @return true if current gatt cache clear was ok
     */
    abstract fun clearGattCache(): Boolean

    /**
     * @return Deferred result for rssi values
     */
    abstract fun readRssiValue(): Deferred<Int>?

    /**
     * Checks if session indicates pairing problem.
     * This is a helper method to check if the session is in a state where pairing problems are likely, for example due to failed authentication attempts
     * or other issues that may arise during the pairing process.
     * @return Boolean indicating if the session indicates a pairing problem.
     * True if there are indications of a pairing problem, false otherwise. Int value contains Bluetooth error code if there is a pairing problem, otherwise it is -1 (not set).
     */
    abstract fun getIndicatesPairingProblem():  Pair<Boolean, Int>

    /**
     * @param uuid service uuid
     * @return get a specific client from the list
     */
    fun fetchClient(uuid: UUID): BleGattBase? {
        for (serviceBase in clients) {
            if (serviceBase.serviceBelongsToClient(uuid)) {
                return serviceBase
            }
        }
        return null
    }

    /**
     * Helper to combine all available/desired clients ready.
     *
     * @param checkConnection check initial connection
     * @throws Throwable if any client fails to become ready
     */
    suspend fun clientsReady(checkConnection: Boolean) {
        val uuids = monitorServicesDiscovered(checkConnection).await()
        for (uuid in uuids) {
            fetchClient(uuid)?.clientReady(checkConnection)
        }
    }

    /**
     * @return android bluetooth device instance
     */
    abstract val bluetoothDevice: BluetoothDevice?

    val name: String
        // adv data getter helpers
        get() = advertisementContent.name

    val polarDeviceId: String
        /**
         * @return polar device id
         */
        get() = advertisementContent.polarDeviceId

    open val polarDeviceType: String
        /**
         * @return polar device type
         */
        get() = advertisementContent.polarDeviceType

    val polarDeviceIdInt: Long
        /**
         * @return polar device id in int
         */
        get() = advertisementContent.polarDeviceIdInt

    val medianRssi: Int
        /**
         * @return current median rssi value
         */
        get() = advertisementContent.medianRssi

    val rssi: Int
        /**
         * @return instant rssi value
         */
        get() = advertisementContent.rssi

    val blePolarHrAdvertisement: BlePolarHrAdvertisement
        /**
         * @return polar hr advertisement content @see BlePolarHrAdvertisement
         */
        get() = advertisementContent.polarHrAdvertisement
}
