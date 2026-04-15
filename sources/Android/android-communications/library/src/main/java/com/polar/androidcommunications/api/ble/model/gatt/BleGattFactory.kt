package com.polar.androidcommunications.api.ble.model.gatt

import com.polar.androidcommunications.api.ble.BleLogger.Companion.e
import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleDisClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePsdClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleRscClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient

class BleGattFactory(clients: Set<Class<out BleGattBase>>) {
    private val classesRemote: MutableSet<Class<out BleGattBase>> = HashSet()

    init {
        classesRemote.addAll(clients)
    }

    fun getRemoteServices(txInterface: BleGattTxInterface): Set<BleGattBase> {
        val serviceBases: MutableSet<BleGattBase> = HashSet()
        for (classObject in classesRemote) {
            try {
                val cArg: Array<Class<*>> = arrayOf(BleGattTxInterface::class.java)
                val serviceBase = classObject.getDeclaredConstructor(*cArg)
                    .newInstance(txInterface) as BleGattBase
                serviceBases.add(serviceBase)
            } catch (e: Exception) {
                e(TAG, "remote services reflections usage failed: " + e.localizedMessage)
                serviceBases.clear()
                if (classesRemote.contains(BlePsFtpClient::class.java)) {
                    serviceBases.add(BlePsFtpClient(txInterface))
                }
                if (classesRemote.contains(BleHrClient::class.java)) {
                    serviceBases.add(BleHrClient(txInterface))
                }
                if (classesRemote.contains(BlePsdClient::class.java)) {
                    serviceBases.add(BlePsdClient(txInterface))
                }
                if (classesRemote.contains(BlePfcClient::class.java)) {
                    serviceBases.add(BlePfcClient(txInterface))
                }
                if (classesRemote.contains(BleDisClient::class.java)) {
                    serviceBases.add(BleDisClient(txInterface))
                }
                if (classesRemote.contains(BleBattClient::class.java)) {
                    serviceBases.add(BleBattClient(txInterface))
                }
                if (classesRemote.contains(BleRscClient::class.java)) {
                    serviceBases.add(BleRscClient(txInterface))
                }
                if (classesRemote.contains(BlePMDClient::class.java)) {
                    serviceBases.add(BlePMDClient(txInterface))
                }
                return serviceBases
            }
        }
        return serviceBases
    }

    companion object {
        private val TAG: String = BleGattFactory::class.java.simpleName
    }
}
