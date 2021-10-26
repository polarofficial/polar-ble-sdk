package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.PmdDataFieldEncoding
import java.lang.Float.intBitsToFloat
import java.util.*

/**
 * Gyro data
 * @param timeStamp ns in epoch time. The time stamp represent time of last sample in gyrSamples list
 */
class GyrData internal constructor(val timeStamp: Long) {

    data class GyrSample internal constructor(
        // Sample contains signed x,y,z axis values in deg/sec
        val x: Float,
        val y: Float,
        val z: Float
    )

    @JvmField
    val gyrSamples: MutableList<GyrSample> = ArrayList()

    companion object {
        fun parseDataFromDataFrame(isCompressed: Boolean, frameType: BlePMDClient.PmdDataFrameType, frame: ByteArray, factor: Float, timeStamp: Long): GyrData {
            return if (isCompressed) {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromType0(frame, factor, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_1 -> dataFromType1(frame, factor, timeStamp)
                    else -> throw java.lang.Exception("Compressed FrameType: $frameType is not supported by Gyro data parser")
                }
            } else {
                throw java.lang.Exception("Raw FrameType: $frameType is not supported by Gyro data parser")
            }
        }

        private fun dataFromType0(value: ByteArray, factor: Float, timeStamp: Long): GyrData {
            val samples = BlePMDClient.parseDeltaFramesAll(value, 3, 16, PmdDataFieldEncoding.SIGNED_INT)
            val gyrData = GyrData(timeStamp)
            for (sample in samples) {
                val x = if (factor != 1.0f) (sample[0] * factor) else sample[0].toFloat()
                val y = if (factor != 1.0f) (sample[1] * factor) else sample[1].toFloat()
                val z = if (factor != 1.0f) (sample[2] * factor) else sample[2].toFloat()
                gyrData.gyrSamples.add(GyrSample(x, y, z))
            }
            return gyrData
        }

        private fun dataFromType1(value: ByteArray, factor: Float, timeStamp: Long): GyrData {
            val samples = BlePMDClient.parseDeltaFramesAll(value, 3, 32, PmdDataFieldEncoding.FLOAT_IEEE754)
            val gyrData = GyrData(timeStamp)
            for (sample in samples) {
                val x = if (factor != 1.0f) intBitsToFloat(sample[0]) * factor else intBitsToFloat(sample[0])
                val y = if (factor != 1.0f) intBitsToFloat(sample[1]) * factor else intBitsToFloat(sample[1])
                val z = if (factor != 1.0f) intBitsToFloat(sample[2]) * factor else intBitsToFloat(sample[2])
                gyrData.gyrSamples.add(GyrSample(x, y, z))
            }
            return gyrData
        }
    }
}