package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.common.ble.RxUtils
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Implementation of Android [BluetoothGattCallback]
 */
internal class GattCallback(
    private val connectionHandler: ConnectionHandler,
    private val sessions: BDDeviceList
) : BluetoothGattCallback() {

    companion object {
        private const val TAG = "GattCallback"
        private const val CONNECTION_PARAMETER_NEGOTIATION_WAIT_DELAY = 500L
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val deviceSession = sessions.getSession(gatt)
        BleLogger.d(TAG, "GATT state changed device newState: $newState status: $status")
        if (deviceSession != null) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // There are devices needing a delay before connection parameters negotiation is started, e.g. Motorola Moto E40
                    Completable.timer(CONNECTION_PARAMETER_NEGOTIATION_WAIT_DELAY, TimeUnit.MILLISECONDS)
                        .observeOn(Schedulers.io())
                        .subscribe { connectionHandler.connectionInitialized(deviceSession) }
                } else {
                    Completable.fromAction { connectionHandler.deviceDisconnected(deviceSession) }
                        .subscribeOn(Schedulers.io())
                        .subscribe()
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Completable.fromAction { connectionHandler.deviceDisconnected(deviceSession) }
                    .subscribeOn(Schedulers.io())
                    .subscribe()
            }
        } else {
            BleLogger.e(TAG, "Dead gatt object received")
            gatt.close()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        BleLogger.d(TAG, "GATT onServicesDiscovered. Status: $status")
        val deviceSession = sessions.getSession(gatt) ?: kotlin.run {
            BleLogger.e(TAG, "services discovered on non known gatt")
            return
        }

        if (deviceSession.serviceDiscovery != null) {
            deviceSession.serviceDiscovery.dispose()
            deviceSession.serviceDiscovery = null
        }
        if (status == BluetoothGatt.GATT_SUCCESS) {
            deviceSession.handleServicesDiscovered()
            // There are devices needing a delay before connection parameters negotiation is continued, e.g. Nokia G11
            Completable.timer(CONNECTION_PARAMETER_NEGOTIATION_WAIT_DELAY, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.io())
                .subscribe { connectionHandler.servicesDiscovered(deviceSession) }
        } else {
            BleLogger.e(TAG, "service discovery failed: $status")
            Completable.fromAction { connectionHandler.disconnectDevice(deviceSession) }
                .subscribeOn(Schedulers.io())
                .subscribe()
        }
    }

    @Deprecated("Deprecated in Java")
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
            RxUtils.emitNext(deviceSession.rssiObservers) { emitter: SingleEmitter<in Int> -> emitter.onSuccess(rssi) }
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
            Completable.fromAction { connectionHandler.mtuUpdated(deviceSession) }
                .subscribeOn(Schedulers.io())
                .subscribe()
        } else {
            BleLogger.e(TAG, "Dead gatt event?")
            gatt.close()
        }
    }

    override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        BleLogger.d(TAG, " phy updated tx: $txPhy rx: $rxPhy status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            Completable.fromAction { connectionHandler.phyUpdated(deviceSession) }
                .subscribeOn(Schedulers.io())
                .subscribe()
        }
    }

    override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        BleLogger.d(TAG, " phy read tx: $txPhy rx: $rxPhy status: $status")
        val deviceSession = sessions.getSession(gatt)
        if (deviceSession != null) {
            Completable.fromAction { connectionHandler.phyUpdated(deviceSession) }
                .subscribeOn(Schedulers.io())
                .subscribe()
        }
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        super.onServiceChanged(gatt)
        BleLogger.d(TAG, " onServiceChanged")
    }
}