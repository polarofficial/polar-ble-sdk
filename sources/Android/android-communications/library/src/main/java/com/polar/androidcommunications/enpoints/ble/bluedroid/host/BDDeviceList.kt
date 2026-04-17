package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.common.ble.AtomicSet

internal class BDDeviceList {
    val sessions: AtomicSet<BDDeviceSessionImpl> = AtomicSet()

    fun getSession(device: BluetoothDevice): BDDeviceSessionImpl? {
        return sessions.fetch( {
                item: BDDeviceSessionImpl? -> item?.bluetoothDevice?.address == device.address
        })
    }

    fun addSession(smartPolarDeviceSession: BDDeviceSessionImpl) {
        d(TAG, "new session added: " + smartPolarDeviceSession.advertisementContent.name)
        sessions.add(smartPolarDeviceSession)
    }

    fun copyDeviceList(): Set<BleDeviceSession> {
        return HashSet<BleDeviceSession>(sessions.objects())
    }


    fun getSession(gatt: BluetoothGatt): BDDeviceSessionImpl? {
        return sessions.fetch(object : AtomicSet.CompareFunction<BDDeviceSessionImpl?> {
            override fun compare(item: BDDeviceSessionImpl?): Boolean {
                if (item != null) {
                    return item.gatt != null && item.gatt == gatt
                }
                return false
            }
        })
    }

    fun getSession(address: String): BDDeviceSessionImpl? {
        return sessions.fetch(object : AtomicSet.CompareFunction<BDDeviceSessionImpl?> {
            override fun compare(item: BDDeviceSessionImpl?): Boolean {
                if (item != null) {
                    return item.address == address
                }
                return false
            }
        })
    }

    fun interface CompareFunction<T> {
        fun compare(smartPolarDeviceSession1: BDDeviceSessionImpl?): Boolean
    }

    fun fetch(function: CompareFunction<Any>): BDDeviceSessionImpl? {
        return sessions.fetch { smartPolarDeviceSession1: BDDeviceSessionImpl? ->
            function.compare(
                smartPolarDeviceSession1
            )
        }
    }

    companion object {
        private val TAG: String = BDDeviceList::class.java.simpleName
    }
}
