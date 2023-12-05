package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.RxUtils
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.FlowableEmitter
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

class BleHtsClient(txInterface: BleGattTxInterface?) :
    BleGattBase(txInterface, HealthThermometer.HTS_SERVICE) {
    data class TemperatureMeasurement(
        val temperatureCelsius: Float,
        val temperatureFahrenheit: Float
    )

    companion object {
        val TAG: String = ::javaClass.name
        const val TEMP_ACCURACY: Int = 100
    }

    private val htsObserverAtomicList = AtomicSet<FlowableEmitter<in TemperatureMeasurement>>()

    override fun reset() {
        super.reset()
        RxUtils.postDisconnectedAndClearList(htsObserverAtomicList)
    }

    override fun processServiceData(
        characteristic: UUID?,
        data: ByteArray?,
        status: Int,
        notifying: Boolean
    ) {
        BleLogger.d(
            TAG, "processServiceData uuid=$characteristic status=$status" +
                    "notifying=$notifying len(data)=${data?.size}"
        )
        if (characteristic != null && status == 0 && data != null) {
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
                RxUtils.emitNext<FlowableEmitter<in TemperatureMeasurement>>(htsObserverAtomicList,
                    RxUtils.Emitter<FlowableEmitter<in TemperatureMeasurement>> { `object`: FlowableEmitter<in TemperatureMeasurement> ->
                        `object`.onNext(
                            TemperatureMeasurement(celsius, fahrenheit)
                        )
                    })

            }
            if (characteristic == HealthThermometer.TEMPERATURE_TYPE) {
                BleLogger.d(TAG, "TEMPERATURE_TYPE ${data.let { BleLogger.byteArrayToHex(it) }}")
            }
        }
    }

    override fun processServiceDataWritten(characteristic: UUID?, status: Int) {
        BleLogger.d(TAG, "processServiceDataWritten TODO")
    }

    fun observeHtsNotifications(checkConnection: Boolean): Flowable<TemperatureMeasurement> {
        return RxUtils.monitorNotifications(htsObserverAtomicList, txInterface, checkConnection)
            .startWith(Completable.fromAction {
                d(TAG, "Start observing HTS")
                addCharacteristicNotification(HealthThermometer.TEMPERATURE_MEASUREMENT)
                getTxInterface().setCharacteristicNotify(
                    HealthThermometer.HTS_SERVICE,
                    HealthThermometer.TEMPERATURE_MEASUREMENT,
                    true
                )
            })
            .doFinally {
                d(TAG, "Stop observing HTS")
                removeCharacteristicNotification(HealthThermometer.TEMPERATURE_MEASUREMENT)
                try {
                    getTxInterface().setCharacteristicNotify(
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