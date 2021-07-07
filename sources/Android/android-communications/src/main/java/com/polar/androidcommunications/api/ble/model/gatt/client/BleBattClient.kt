package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.RxUtils
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.FlowableEmitter
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

private const val UNDEFINED_BATTERY_PERCENTAGE = -1

class BleBattClient(txInterface: BleGattTxInterface?) : BleGattBase(txInterface, BATTERY_SERVICE) {
    private val batteryStatusObservers = AtomicSet<FlowableEmitter<in Int>>()
    private val cachedBatteryPercentage = AtomicInteger(UNDEFINED_BATTERY_PERCENTAGE)

    companion object {
        @JvmField
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHARACTERISTIC: UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }

    init {
        addCharacteristicNotification(BATTERY_LEVEL_CHARACTERISTIC)
        addCharacteristicRead(BATTERY_LEVEL_CHARACTERISTIC)
    }

    override fun reset() {
        super.reset()
        cachedBatteryPercentage.set(UNDEFINED_BATTERY_PERCENTAGE)
        RxUtils.postDisconnectedAndClearList(batteryStatusObservers)
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (status == ATT_SUCCESS && characteristic == BATTERY_LEVEL_CHARACTERISTIC) {
            cachedBatteryPercentage.set(data[0].toInt())
            RxUtils.emitNext(batteryStatusObservers) { `object`: FlowableEmitter<in Int> ->
                `object`.onNext(
                    data[0].toInt()
                )
            }
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        // do nothing
    }

    /**
     * Get observable for monitoring battery status updates on connected device
     *
     * @param checkConnection false = connection is not check before observer added, true = connection is check
     * @return Flowable stream
     * onNext, on every battery status update received from connected device. The value is the device battery level as a percentage from 0% to 100%
     * onError, if client is not initially connected or ble disconnect's
     * onCompleted, none except further configuration applied. If binded to fragment or activity life cycle this might be produced
     */
    fun monitorBatteryStatus(checkConnection: Boolean): Flowable<Int> {
        return RxUtils.monitorNotifications(batteryStatusObservers, txInterface, checkConnection)
            .startWith(Flowable.just(cachedBatteryPercentage.get()))
            .filter { level: Int -> isValidBatteryPercentage(level) }
    }

    private fun isValidBatteryPercentage(batteryPercentage: Int): Boolean {
        return batteryPercentage in 0..100
    }
}