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

enum class ChargeState {
    UNKNOWN, CHARGING, DISCHARGING_ACTIVE, DISCHARGING_INACTIVE
}

class BleBattClient(txInterface: BleGattTxInterface?) : BleGattBase(txInterface, BATTERY_SERVICE) {
    private val batteryStatusObservers = AtomicSet<FlowableEmitter<in Int>>()
    private val cachedBatteryPercentage = AtomicInteger(UNDEFINED_BATTERY_PERCENTAGE)

    private val batteryChargeStateObservers = AtomicSet<FlowableEmitter<in ChargeState>>()
    private var cachedChargeState: ChargeState = ChargeState.UNKNOWN

    companion object {
        @JvmField
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHARACTERISTIC: UUID =
                UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_STATUS_CHARACTERISTIC: UUID =
                UUID.fromString("00002bed-0000-1000-8000-00805f9b34fb")
    }

    init {
        addCharacteristicNotification(BATTERY_LEVEL_CHARACTERISTIC)
        addCharacteristicRead(BATTERY_LEVEL_CHARACTERISTIC)
        addCharacteristicNotification(BATTERY_LEVEL_STATUS_CHARACTERISTIC)
        addCharacteristicRead(BATTERY_LEVEL_STATUS_CHARACTERISTIC)
    }

    override fun reset() {
        super.reset()
        cachedBatteryPercentage.set(UNDEFINED_BATTERY_PERCENTAGE)
        RxUtils.postDisconnectedAndClearList(batteryStatusObservers)

        cachedChargeState = ChargeState.UNKNOWN
        RxUtils.postDisconnectedAndClearList(batteryChargeStateObservers)
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (status == ATT_SUCCESS) {
            when (characteristic) {
                BATTERY_LEVEL_CHARACTERISTIC -> {
                    cachedBatteryPercentage.set(data[0].toInt())
                    RxUtils.emitNext(batteryStatusObservers) { emitter: FlowableEmitter<in Int> ->
                        emitter.onNext(cachedBatteryPercentage.get())
                    }
                }
                BATTERY_LEVEL_STATUS_CHARACTERISTIC -> {
                    cachedChargeState = parseBatteryStatus(data)
                    RxUtils.emitNext(batteryChargeStateObservers) { emitter: FlowableEmitter<in ChargeState> ->
                        emitter.onNext(cachedChargeState)
                    }
                }
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

    /**
     * Get observable for monitoring charging status updates on connected device.
     * Requires BLE BAS v1.1
     *
     * @param checkConnection false = connection is not check before observer added, true = connection is check
     * @return Flowable stream emitting [ChargeState]
     */
    fun monitorChargingStatus(checkConnection: Boolean): Flowable<ChargeState> {
        return RxUtils.monitorNotifications(batteryChargeStateObservers, txInterface, checkConnection)
                .startWith(Flowable.just(cachedChargeState))
    }

    private fun parseBatteryStatus(data: ByteArray): ChargeState {
        val chargeStateValue = data[1].toInt() and 0xF3

        return when (chargeStateValue) {
            0xA3 -> ChargeState.CHARGING
            0xC3 -> ChargeState.DISCHARGING_INACTIVE
            0xC1 -> ChargeState.DISCHARGING_ACTIVE
            else -> ChargeState.UNKNOWN
        }
    }

    private fun isValidBatteryPercentage(batteryPercentage: Int): Boolean {
        return batteryPercentage in 0..100
    }
}