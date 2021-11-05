package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.PmdDataFieldEncoding
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClientUtils
import java.lang.Float.intBitsToFloat
import java.util.*

/**
 * Temperature data
 * @param timeStamp ns in epoch time. The time stamp represent time of last sample in [temperatureSamples] list
 */
class TemperatureData internal constructor(val timeStamp: Long) {

    data class TemperatureSample internal constructor(
        // Sample contains signed temperature value in celcius
        val temperature: Float
    )

    @JvmField
    val temperatureSamples: MutableList<TemperatureSample> = ArrayList()

    companion object {
        fun parseDataFromDataFrame(isCompressed: Boolean, frameType: BlePMDClient.PmdDataFrameType, frame: ByteArray, factor: Float, timeStamp: Long): TemperatureData {
            return if (isCompressed) {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromCompressedType0(frame, factor, timeStamp)
                    else -> throw java.lang.Exception("Compressed FrameType: $frameType is not supported by Temperature data parser")
                }
            } else {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromRawType0(frame, timeStamp)
                    else -> throw java.lang.Exception("Raw FrameType: $frameType is not supported by Temperature data parser")
                }
            }
        }

        private fun dataFromCompressedType0(frame: ByteArray, factor: Float, timeStamp: Long): TemperatureData {
            val samples = BlePMDClient.parseDeltaFramesAll(frame, 1, 32, PmdDataFieldEncoding.FLOAT_IEEE754)
            val temperatureData = TemperatureData(timeStamp)
            for (sample in samples) {
                val pressure = if (factor != 1.0f) intBitsToFloat(sample[0]) * factor else intBitsToFloat(sample[0])
                temperatureData.temperatureSamples.add(TemperatureSample(pressure))
            }
            return temperatureData
        }

        private fun dataFromRawType0(frame: ByteArray, timeStamp: Long): TemperatureData {
            val temperatureData = TemperatureData(timeStamp)
            var offset = 0

            while (offset < frame.size) {
                val temperature = BlePMDClientUtils.parseFrameDataField(frame.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                temperatureData.temperatureSamples.add(TemperatureSample(temperature))
            }
            return temperatureData
        }
    }
}