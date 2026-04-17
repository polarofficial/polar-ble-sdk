package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.polar.androidcommunications.api.ble.BleLogger
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
        private const val CONNECTION_PARAMETER_NEGOTIATION_WAIT_DELAY = 500L
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val deviceSession = sessions.getSession(gatt)
        BleLogger.d(TAG, "GATT state changed device newState: $newState status: $status")
        if (deviceSession != null) {
            indicatesPairingProblem = Pair(false, -1)
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    scope.launch {
                        delay(CONNECTION_PARAMETER_NEGOTIATION_WAIT_DELAY)
                        connectionHandler.connectionInitialized(deviceSession)
                    }
                } else {
                    scope.launch { connectionHandler.deviceDisconnected(deviceSession) }
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                scope.launch {
                    if (status != 0) {
                        indicatesPairingProblem = Pair(true, status)
                    }
                    connectionHandler.deviceDisconnected(deviceSession)
                }
            }
        } else {
            BleLogger.e(TAG, "Dead gatt object received")
            gatt.close()
        }
    }

    fun getIndicatesPairingProblem(): Pair<Boolean, Int> {
        return indicatesPairingProblem
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