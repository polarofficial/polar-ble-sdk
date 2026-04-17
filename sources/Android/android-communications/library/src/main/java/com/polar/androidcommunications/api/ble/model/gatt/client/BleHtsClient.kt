package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

class HealthThermometer {
    companion object {
        val HTS_SERVICE: UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
        val TEMPERATURE_MEASUREMENT: UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")
        val TEMPERATURE_TYPE: UUID = UUID.fromString("00002A1D-0000-1000-8000-00805f9b34fb")
    }
}

class BleHtsClient(txInterface: BleGattTxInterface) :
    BleGattBase(txInterface, HealthThermometer.HTS_SERVICE) {
    data class TemperatureMeasurement(
        val temperatureCelsius: Float,
        val temperatureFahrenheit: Float
    )

    companion object {
        val TAG: String = ::javaClass.name
        const val TEMP_ACCURACY: Int = 100
    }

    private val htsObserverAtomicList = AtomicSet<Channel<TemperatureMeasurement>>()

    override fun reset() {
        super.reset()
        ChannelUtils.postDisconnectedAndClearList(htsObserverAtomicList)
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        BleLogger.d(
            TAG, "processServiceData uuid=$characteristic status=$status" +
                    "notifying=$notifying len(data)=${data?.size}"
        )
        if (status == ATT_SUCCESS && data.isNotEmpty()) {
            if (characteristic == HealthThermometer.TEMPERATURE_MEASUREMENT) {
                BleLogger.d(
                    TAG,
                    "TEMPERATURE_MEASUREMENT ${data.let { BleLogger.byteArrayToHex(it) }}"
                )
                val flags = data[0].toInt() and 0xFF
                val isFahrenheit = (flags and 0x01) != 0
                val exponent = data[4].toInt()
                val mantissaBytes = byteArrayOf(0x00) + data.sliceArray(1..3).reversedArray()
                val mantissa = ByteBuffer.wrap(mantissaBytes).int
                val temperature = ((mantissa * 10.toFloat()
                    .pow(exponent) * TEMP_ACCURACY).roundToInt() / TEMP_ACCURACY.toFloat())

                val celsius =
                    if (!isFahrenheit) temperature else (temperature - 32.0f) * 5.0f / 9.0f
                val fahrenheit =
                    if (isFahrenheit) temperature else temperature * 9.0f / 5.0f + 32.0f
                ChannelUtils.emitNext(htsObserverAtomicList) { observer ->
                    observer.trySend(TemperatureMeasurement(celsius, fahrenheit))
                }
            }
            if (characteristic == HealthThermometer.TEMPERATURE_TYPE) {
                BleLogger.d(TAG, "TEMPERATURE_TYPE ${data.let { BleLogger.byteArrayToHex(it) }}")
            }
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        BleLogger.d(TAG, "processServiceDataWritten TODO")
    }

    fun observeHtsNotifications(checkConnection: Boolean): Flow<TemperatureMeasurement> {
        return ChannelUtils.monitorNotifications(htsObserverAtomicList, txInterface, checkConnection)
            .onStart {
                d(TAG, "Start observing HTS")
                addCharacteristicNotification(HealthThermometer.TEMPERATURE_MEASUREMENT)
                txInterface.setCharacteristicNotify(
                    HealthThermometer.HTS_SERVICE,
                    HealthThermometer.TEMPERATURE_MEASUREMENT,
                    true
                )
            }
            .onCompletion {
                d(TAG, "Stop observing HTS")
                removeCharacteristicNotification(HealthThermometer.TEMPERATURE_MEASUREMENT)
                try {
                    txInterface.setCharacteristicNotify(
                        HealthThermometer.HTS_SERVICE,
                        HealthThermometer.TEMPERATURE_MEASUREMENT,
                        false
                    )
                } catch (e: Exception) {
                    // this may happen if connection is already closed, no need sent the exception to downstream
                    d(
                        TAG,
                        "HTS client is not able to set characteristic notify to false. Reason $e"
                    )
                }
            }
    }
}