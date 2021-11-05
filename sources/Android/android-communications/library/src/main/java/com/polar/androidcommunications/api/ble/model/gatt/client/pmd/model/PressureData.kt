package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.PmdDataFieldEncoding
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClientUtils
import java.lang.Float.intBitsToFloat
import java.util.*

/**
 * Pressure data
 * @param timeStamp ns in epoch time. The time stamp represent time of last sample in pressureSamples list
 */
class PressureData internal constructor(val timeStamp: Long) {

    data class PressureSample internal constructor(
        // Sample contains signed pressure value in bar
        val pressure: Float
    )

    @JvmField
    val pressureSamples: MutableList<PressureSample> = ArrayList()

    companion object {
        fun parseDataFromDataFrame(isCompressed: Boolean, frameType: BlePMDClient.PmdDataFrameType, frame: ByteArray, factor: Float, timeStamp: Long): PressureData {
            return if (isCompressed) {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromCompressedType0(frame, factor, timeStamp)
                    else -> throw java.lang.Exception("Compressed FrameType: $frameType is not supported by Pressure data parser")
                }
            } else {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromRawType0(frame, timeStamp)
                    else -> throw java.lang.Exception("Raw FrameType: $frameType is not supported by Pressure data parser")
                }
            }
        }

        private fun dataFromCompressedType0(frame: ByteArray, factor: Float, timeStamp: Long): PressureData {
            val samples = BlePMDClient.parseDeltaFramesAll(frame, 1, 32, PmdDataFieldEncoding.FLOAT_IEEE754)
            val pressureData = PressureData(timeStamp)
            for (sample in samples) {
                val pressure = if (factor != 1.0f) intBitsToFloat(sample[0]) * factor else intBitsToFloat(sample[0])
                pressureData.pressureSamples.add(PressureSample(pressure))
            }
            return pressureData
        }

        private fun dataFromRawType0(frame: ByteArray, timeStamp: Long): PressureData {
            val pressureData = PressureData(timeStamp)
            var offset = 0

            while (offset < frame.size) {
                val pressure = BlePMDClientUtils.parseFrameDataField(frame.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                pressureData.pressureSamples.add(PressureSample(pressure))
            }
            return pressureData
        }
    }
}