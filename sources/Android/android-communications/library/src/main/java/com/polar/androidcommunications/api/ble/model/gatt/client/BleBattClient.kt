package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.RxUtils
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.FlowableEmitter
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

private const val UNDEFINED_BATTERY_PERCENTAGE = -1
private const val TAG = "BleBattClient"

enum class ChargeState {
    UNKNOWN, CHARGING, DISCHARGING_ACTIVE, DISCHARGING_INACTIVE
}

enum class PowerSourceState {
    NOT_CONNECTED, CONNECTED, UNKNOWN, RESERVED_FOR_FUTURE_USE
}

enum class BatteryPresentState {
    NOT_PRESENT, PRESENT, UNKNOWN
}

data class PowerSourcesState(
    val batteryPresent: BatteryPresentState,
    val wiredExternalPowerConnected: PowerSourceState,
    val wirelessExternalPowerConnected: PowerSourceState
)

class BleBattClient(txInterface: BleGattTxInterface?) : BleGattBase(txInterface, BATTERY_SERVICE) {
    private val batteryStatusObservers = AtomicSet<FlowableEmitter<in Int>>()
    private val cachedBatteryPercentage = AtomicInteger(UNDEFINED_BATTERY_PERCENTAGE)

    private val batteryChargeStateObservers = AtomicSet<FlowableEmitter<in ChargeState>>()
    private var cachedChargeState: ChargeState = ChargeState.UNKNOWN

    private var powerSourcesStateObservers = AtomicSet<FlowableEmitter<in PowerSourcesState>>()
    private var cachedPowerSourcesState =
        PowerSourcesState(BatteryPresentState.UNKNOWN, PowerSourceState.UNKNOWN, PowerSourceState.UNKNOWN)

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

        cachedPowerSourcesState = PowerSourcesState(BatteryPresentState.UNKNOWN, PowerSourceState.UNKNOWN, PowerSourceState.UNKNOWN)
        RxUtils.postDisconnectedAndClearList(powerSourcesStateObservers)
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
                    cachedPowerSourcesState = parsePowerSourcesState(data)
                    RxUtils.emitNext(powerSourcesStateObservers) { emitter: FlowableEmitter<in PowerSourcesState> ->
                        emitter.onNext(cachedPowerSourcesState)
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

    /**
     * Get observable for monitoring power sources updates on connected device.
     * Requires BLE BAS v1.1. Exposes battery present, wired and wireless power
     * source connected statuses.
     *
     * @param checkConnection false = connection is not check before observer added, true = connection is check
     * @return Flowable stream emitting [PowerSourcesState]
     */
    fun monitorPowerSourcesState(checkConnection: Boolean): Flowable<PowerSourcesState> {
        return RxUtils.monitorNotifications(powerSourcesStateObservers, txInterface, checkConnection)
            .startWith(Flowable.just(cachedPowerSourcesState))
    }

    private fun parseBatteryStatus(data: ByteArray): ChargeState {
        return when (val chargeStateValue = (data[1].toInt() and 0x60) shr 5) {
            1 -> ChargeState.CHARGING
            2 -> ChargeState.DISCHARGING_ACTIVE
            3 -> ChargeState.DISCHARGING_INACTIVE
            else -> {
                BleLogger.e(TAG, "Unknown charge state value: $chargeStateValue")
                ChargeState.UNKNOWN
            }
        }
    }

    private fun isValidBatteryPercentage(batteryPercentage: Int): Boolean {
        return batteryPercentage in 0..100
    }

    private fun parsePowerSourcesState(data: ByteArray): PowerSourcesState {
        val batteryPresent = when (val batteryPresentValue = (data[1].toInt() and 0x01)) {
            0 -> BatteryPresentState.NOT_PRESENT
            1 -> BatteryPresentState.PRESENT
            else -> {
                BleLogger.e(
                    TAG,
                    "Unknown wired battery present value: $batteryPresentValue"
                )
                BatteryPresentState.UNKNOWN
            }
        }
        val wiredExternalPowerConnected =
            when (val externalPowerConnectedValue = (data[1].toInt() and 0x06) shr 1) {
                0 -> PowerSourceState.NOT_CONNECTED
                1 -> PowerSourceState.CONNECTED
                3 -> PowerSourceState.RESERVED_FOR_FUTURE_USE
                else -> {
                    BleLogger.e(
                        TAG,
                        "Unknown wired power source state value: $externalPowerConnectedValue"
                    )
                    PowerSourceState.UNKNOWN
                }
            }
        val wirelessExternalPowerConnected =
            when (val externalPowerConnectedValue = data[1].toInt() and 0x18 shr 3) {
                0 -> PowerSourceState.NOT_CONNECTED
                1 -> PowerSourceState.CONNECTED
                3 -> PowerSourceState.RESERVED_FOR_FUTURE_USE
                else -> {
                    BleLogger.e(
                        TAG,
                        "Unknown wireless power source state value: $externalPowerConnectedValue"
                    )
                    PowerSourceState.UNKNOWN
                }
            }
        return PowerSourcesState(
            batteryPresent,
            wiredExternalPowerConnected,
            wirelessExternalPowerConnected
        )
    }
}