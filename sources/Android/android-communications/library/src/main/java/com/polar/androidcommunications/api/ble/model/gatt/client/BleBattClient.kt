package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
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

class BleBattClient(txInterface: BleGattTxInterface) : BleGattBase(txInterface, BATTERY_SERVICE) {
    private val batteryStatusObservers = AtomicSet<Channel<Int>>()
    private val cachedBatteryPercentage = AtomicInteger(UNDEFINED_BATTERY_PERCENTAGE)

    private val batteryChargeStateObservers = AtomicSet<Channel<ChargeState>>()
    private var cachedChargeState: ChargeState = ChargeState.UNKNOWN

    private var powerSourcesStateObservers = AtomicSet<Channel<PowerSourcesState>>()
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
        ChannelUtils.postDisconnectedAndClearList(batteryStatusObservers)

        cachedChargeState = ChargeState.UNKNOWN
        ChannelUtils.postDisconnectedAndClearList(batteryChargeStateObservers)

        cachedPowerSourcesState = PowerSourcesState(BatteryPresentState.UNKNOWN, PowerSourceState.UNKNOWN, PowerSourceState.UNKNOWN)
        ChannelUtils.postDisconnectedAndClearList(powerSourcesStateObservers)
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (status == ATT_SUCCESS && data.isNotEmpty()) {
            when (characteristic) {
                BATTERY_LEVEL_CHARACTERISTIC -> {
                    cachedBatteryPercentage.set(data[0].toInt())
                    ChannelUtils.emitNext(batteryStatusObservers) { channel ->
                        channel.trySend(cachedBatteryPercentage.get())
                    }
                }
                BATTERY_LEVEL_STATUS_CHARACTERISTIC -> {
                    cachedChargeState = parseBatteryStatus(data)
                    ChannelUtils.emitNext(batteryChargeStateObservers) { channel ->
                        channel.trySend(cachedChargeState)
                    }
                    cachedPowerSourcesState = parsePowerSourcesState(data)
                    ChannelUtils.emitNext(powerSourcesStateObservers) { channel ->
                        channel.trySend(cachedPowerSourcesState)
                    }
                }
            }
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        // do nothing
    }

    /**
     * Get flow for monitoring battery status updates on connected device
     *
     * @param checkConnection false = connection is not check before observer added, true = connection is check
     * @return Flow stream
     */
    fun monitorBatteryStatus(checkConnection: Boolean): Flow<Int> {
        return ChannelUtils.monitorNotifications(batteryStatusObservers, txInterface, checkConnection)
            .onStart { emit(cachedBatteryPercentage.get()) }
            .filter { level -> isValidBatteryPercentage(level) }
    }

    /**
     * Get flow for monitoring charging status updates on connected device.
     * Requires BLE BAS v1.1
     *
     * @param checkConnection false = connection is not check before observer added, true = connection is check
     * @return Flow stream emitting [ChargeState]
     */
    fun monitorChargingStatus(checkConnection: Boolean): Flow<ChargeState> {
        return ChannelUtils.monitorNotifications(batteryChargeStateObservers, txInterface, checkConnection)
            .onStart { emit(cachedChargeState) }
    }

    /**
     * Get flow for monitoring power sources updates on connected device.
     * Requires BLE BAS v1.1. Exposes battery present, wired and wireless power
     * source connected statuses.
     *
     * @param checkConnection false = connection is not check before observer added, true = connection is check
     * @return Flow stream emitting [PowerSourcesState]
     */
    fun monitorPowerSourcesState(checkConnection: Boolean): Flow<PowerSourcesState> {
        return ChannelUtils.monitorNotifications(powerSourcesStateObservers, txInterface, checkConnection)
            .onStart { emit(cachedPowerSourcesState) }
    }

    /**
     * Get last observed battery status on connected device
     * Requires BLE BAS v1.1
     *
     * @return Returns the last known battery level as a percentage from 0% to 100% or -1 if value is not set
     */
    fun getBatteryLevel(): Int {
        return cachedBatteryPercentage.get()
    }

    /**
     * Get last observed charge status on connected device
     * Requires BLE BAS v1.1
     *
     * @return Returns the last known charge status as [ChargeState]
     */
    fun getChargerStatus(): ChargeState {
        return cachedChargeState
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