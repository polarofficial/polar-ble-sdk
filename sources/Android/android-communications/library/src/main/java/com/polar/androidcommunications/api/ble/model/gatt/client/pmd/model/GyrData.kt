package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.PmdDataFieldEncoding
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import java.lang.Float.intBitsToFloat

internal class GyrData {

    internal data class GyrSample internal constructor(
        val timeStamp: ULong,
        // Sample contains signed x,y,z axis values in deg/sec
        val x: Float,
        val y: Float,
        val z: Float
    )

    val gyrSamples: MutableList<GyrSample> = mutableListOf()

    companion object {
        private const val TYPE_0_SAMPLE_SIZE_IN_BYTES = 2
        private const val TYPE_0_SAMPLE_SIZE_IN_BITS = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_0_CHANNELS_IN_SAMPLE = 3

        private const val TYPE_1_SAMPLE_SIZE_IN_BYTES = 4
        private const val TYPE_1_SAMPLE_SIZE_IN_BITS = TYPE_1_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_1_CHANNELS_IN_SAMPLE = 3

        fun parseDataFromDataFrame(frame: PmdDataFrame): GyrData {
            return if (frame.isCompressedFrame) {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromCompressedType0(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_1 -> dataFromCompressedType1(frame)
                    else -> throw java.lang.Exception("Compressed FrameType: ${frame.frameType} is not supported by Gyro data parser")
                }
            } else {
                throw java.lang.Exception("Raw FrameType: ${frame.frameType} is not supported by Gyro data parser")
            }
        }

        private fun dataFromCompressedType0(frame: PmdDataFrame): GyrData {
            val samples = BlePMDClient.parseDeltaFramesAll(frame.dataContent, TYPE_0_CHANNELS_IN_SAMPLE, TYPE_0_SAMPLE_SIZE_IN_BITS, PmdDataFieldEncoding.SIGNED_INT)
            val gyrData = GyrData()

            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samples.size, frame.sampleRate)

            for ((index, sample) in samples.withIndex()) {
                val x = if (frame.factor != 1.0f) (sample[0] * frame.factor) else sample[0].toFloat()
                val y = if (frame.factor != 1.0f) (sample[1] * frame.factor) else sample[1].toFloat()
                val z = if (frame.factor != 1.0f) (sample[2] * frame.factor) else sample[2].toFloat()
                gyrData.gyrSamples.add(GyrSample(timeStamp = timeStamps[index], x, y, z))

            }
            return gyrData
        }

        private fun dataFromCompressedType1(frame: PmdDataFrame): GyrData {
            val samples = BlePMDClient.parseDeltaFramesAll(frame.dataContent, TYPE_1_CHANNELS_IN_SAMPLE, TYPE_1_SAMPLE_SIZE_IN_BITS, PmdDataFieldEncoding.FLOAT_IEEE754)
            val gyrData = GyrData()

            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samples.size, frame.sampleRate)

            for ((index, sample) in samples.withIndex()) {
                val x = if (frame.factor != 1.0f) intBitsToFloat(sample[0]) * frame.factor else intBitsToFloat(sample[0])
                val y = if (frame.factor != 1.0f) intBitsToFloat(sample[1]) * frame.factor else intBitsToFloat(sample[1])
                val z = if (frame.factor != 1.0f) intBitsToFloat(sample[2]) * frame.factor else intBitsToFloat(sample[2])
                gyrData.gyrSamples.add(GyrSample(timeStamp = timeStamps[index], x, y, z))
            }
            return gyrData
        }
    }
}