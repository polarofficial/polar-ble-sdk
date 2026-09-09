package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Implementation of Android [BluetoothGattCallback]
 */
internal class GattCallback(
    private val connectionHandler: ConnectionHandler,
    private val sessions: BDDeviceList
) : BluetoothGattCallback() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var indicatesPairingProblem: Pair<Boolean, Int> = Pair(false, -1)

    fun cancel() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "GattCallback"
        private const val GATT_CONNECTION_L2C_FAILURE = 22
        private const val CONNECTION_PARAMETER_NEGOTIATION_WAIT_DELAY = 500L

        // HCI disconnect reason codes (Bluetooth Core Spec Vol 2, Part D) that Android forwards
        // as the GATT status on STATE_DISCONNECTED.
        private const val HCI_CONNECTION_TIMEOUT = 8

        // Peer (remote device) actively terminated the link: user-terminated, low resources, power off.
        private val PEER_ACTIVE_TERMINATION_STATUSES = setOf(19, 20, 21)

        // Statuses observed in practice for a device-command-triggered disconnect. Includes the
        // connection timeout code alongside peer-termination, since some devices/chipsets report
        // a timeout rather than an explicit peer-terminated reason for a fast reboot.
        private val EXPECTED_DEVICE_COMMAND_STATUSES = PEER_ACTIVE_TERMINATION_STATUSES + HCI_CONNECTION_TIMEOUT

        // Android's onConnectionStateChange status is a single int that the stack fills from one
        // of two different code spaces depending on internal code path: the raw HCI disconnect
        // reason (Core Spec Vol 2, Part D) or an internal BTA/GATT-layer status. There is no way
        // to tell which space applies from the public API. Most values only exist in one of the
        // two spaces and are unambiguous; 8 and 19 are defined in both (with different meanings),
        // so both are listed for those.
        private val KNOWN_STATUS_NAMES = mapOf(
            0 to listOf("SUCCESS"),
            8 to listOf("HCI_CONNECTION_TIMEOUT", "GATT_INSUF_AUTHORIZATION"),
            19 to listOf("HCI_REMOTE_USER_TERMINATED_CONNECTION", "GATT_VALUE_NOT_ALLOWED"),
            20 to listOf("HCI_REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES"),
            21 to listOf("HCI_REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF"),
            22 to listOf("HCI_CONNECTION_TERMINATED_BY_LOCAL_HOST"),
            34 to listOf("HCI_LMP_RESPONSE_TIMEOUT"),
            62 to listOf("HCI_CONNECTION_FAILED_TO_BE_ESTABLISHED"),
            133 to listOf("GATT_ERROR"),
            145 to listOf("GATT_ALREADY_OPEN"),
            146 to listOf("GATT_CANCEL"),
            147 to listOf("GATT_CONNECTION_TIMEOUT")
        )

        private fun describeStatus(status: Int): String {
            val names = KNOWN_STATUS_NAMES[status] ?: return "$status (UNKNOWN)"
            return if (names.size == 1) "$status (${names[0]})" else "$status (possible: ${names.joinToString(" or ")})"
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val deviceSession = sessions.getSession(gatt)
        val stateStr = when (newState) {
            BluetoothGatt.STATE_CONNECTED -> "CONNECTED"
            BluetoothGatt.STATE_DISCONNECTED -> "DISCONNECTED"
            else -> newState.toString()
        }
        BleLogger.d(TAG, "GATT state changed addr=${gatt.device?.address} newState=$stateStr($newState) status=$status sessionFound=${deviceSession != null}")
        if (deviceSession != null) {
            indicatesPairingProblem = Pair(false, -1)
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    deviceSession.clearDisconnectReason()
                    scope.launch {
                        delay(CONNECTION_PARAMETER_NEGOTIATION_WAIT_DELAY)
                        deviceSession.markBondedAtConnection(
                            deviceSession.bluetoothDevice.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED
                        )
                        connectionHandler.connectionInitialized(deviceSession)
                    }
                } else {
                    scope.launch { connectionHandler.deviceDisconnected(deviceSession) }
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                val hadPendingDeviceCommand = deviceSession.isExpectedDeviceCommandDisconnectStillValid()
                val pendingCommand = deviceSession.pendingDeviceCommand
                val disconnectReason = when {
                    deviceSession.isIntentionalDisconnect ->
                        BleDeviceSession.DisconnectReason.CONNECTION_LOST
                    deviceSession.pairingInformationWasRemoved() ->
                        BleDeviceSession.DisconnectReason.PAIRING_INFORMATION_REMOVED
                    deviceSession.disconnectReason == BleDeviceSession.DisconnectReason.PAIRING_NEGOTIATION_FAILED ->
                        BleDeviceSession.DisconnectReason.PAIRING_NEGOTIATION_FAILED
                    hadPendingDeviceCommand ->
                        BleDeviceSession.DisconnectReason.DEVICE_COMMAND
                    status == GATT_CONNECTION_L2C_FAILURE ->
                        BleDeviceSession.DisconnectReason.PAIRING_NEGOTIATION_FAILED
                    status == HCI_CONNECTION_TIMEOUT ->
                        BleDeviceSession.DisconnectReason.CONNECTION_LOST
                    status in PEER_ACTIVE_TERMINATION_STATUSES ->
                        BleDeviceSession.DisconnectReason.PEER_TERMINATED
                    status != 0 ->
                        BleDeviceSession.DisconnectReason.GATT_ERROR
                    else ->
                        BleDeviceSession.DisconnectReason.CONNECTION_LOST
                }
                BleLogger.d(
                    TAG,
                    "Disconnect classified addr=${deviceSession.address} status=${describeStatus(status)} reason=$disconnectReason pendingDeviceCommand=$pendingCommand"
                )
                if (hadPendingDeviceCommand && status !in EXPECTED_DEVICE_COMMAND_STATUSES) {
                    BleLogger.w(
                        TAG,
                        "Disconnect for pending device command $pendingCommand on ${deviceSession.address} arrived with unexpected GATT status ${describeStatus(status)} (expected one of $EXPECTED_DEVICE_COMMAND_STATUSES)"
                    )
                }
                // Classify before the feature monitor can observe the disconnect;
                // otherwise a terminal pairing failure can be parked for reconnect.
                deviceSession.markDisconnect(disconnectReason, status)
                scope.launch {
                    delay(CONNECTION_PARAMETER_NEGOTIATION_WAIT_DELAY)
                    connectionHandler.deviceDisconnected(deviceSession)
                }
            }
        } else {
            BleLogger.e(TAG, "Dead gatt object received addr=${gatt.device?.address}")
            gatt.close()
        }
    }

    fun getIndicatesPairingProblem(): Pair<Boolean, Int> {
        return if (indicatesPairingProblem.first &&
            indicatesPairingProblem.second == BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION
        ) {
            indicatesPairingProblem
        } else {
            Pair(false, -1)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        BleLogger.d(TAG, "GATT onServicesDiscovered. Status: $status")
        val deviceSession = sessions.getSession(gatt) ?: kotlin.run {
            BleLogger.e(TAG, "services discovered on non known gatt")
            return
        }

        deviceSession.serviceDiscovery?.cancel()
        deviceSession.serviceDiscovery = null

        if (status == BluetoothGatt.GATT_SUCCESS) {
            deviceSession.handleServicesDiscovered()
            scope.launch {
                delay(CONNECTION_PARAMETER_NEGOTIATION_WAIT_DELAY)
                connectionHandler.servicesDiscovered(deviceSession)
            }
        } else {
            BleLogger.e(TAG, "service discovery failed: $status")
            scope.launch { connectionHandler.disconnectDevice(deviceSession) }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        BleLogger.d(TAG, "GATT onCharacteristicRead characteristic:${characteristic.uuid} status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleCharacteristicRead(characteristic.service, characteristic, characteristic.value, status)
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        BleLogger.d(TAG, "GATT onCharacteristicRead characteristic:${characteristic.uuid} status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleCharacteristicRead(characteristic.service, characteristic, value, status)
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        BleLogger.d(TAG, "GATT onCharacteristicWrite characteristic:${characteristic.uuid} status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleCharacteristicWrite(characteristic.service, characteristic, status)
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleCharacteristicValueUpdated(characteristic.service, characteristic, characteristic.value)
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleCharacteristicValueUpdated(characteristic.service, characteristic, value)
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        BleLogger.d(TAG, "GATT onDescriptorRead descriptor:${descriptor.uuid} status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleDescriptorRead(descriptor, descriptor.value, status)
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
        BleLogger.d(TAG, "GATT onDescriptorRead descriptor:${descriptor.uuid} status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleDescriptorRead(descriptor, value, status)
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        BleLogger.d(TAG, "GATT onDescriptorWrite descriptor: ${descriptor.uuid} status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleDescriptorWrite(descriptor.characteristic.service, descriptor.characteristic, descriptor.value, status)
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        BleLogger.d(TAG, "onReadRemoteRssi status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleRssiRead(rssi, status)
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        BleLogger.d(TAG, "onMtuChanged status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            deviceSession.handleMtuChanged(mtu, status)
            scope.launch { connectionHandler.mtuUpdated(deviceSession) }
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        BleLogger.d(TAG, " phy updated tx: $txPhy rx: $rxPhy status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            scope.launch { connectionHandler.phyUpdated(deviceSession) }
        }
    }

    override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        BleLogger.d(TAG, " phy read tx: $txPhy rx: $rxPhy status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            scope.launch { connectionHandler.phyUpdated(deviceSession) }
        }
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        super.onServiceChanged(gatt)
        BleLogger.d(TAG, " onServiceChanged")
    }
}