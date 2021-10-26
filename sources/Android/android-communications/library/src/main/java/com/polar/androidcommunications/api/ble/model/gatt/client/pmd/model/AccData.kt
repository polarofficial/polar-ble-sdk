package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.Companion.parseDeltaFramesAll
import com.polar.androidcommunications.common.ble.BleUtils
import java.util.*
import kotlin.math.ceil

class AccData internal constructor(@JvmField val timeStamp: Long) {
    data class AccSample internal constructor(
        // Sample contains signed x,y,z axis values in milliG
        val x: Int,
        val y: Int,
        val z: Int
    )

    @JvmField
    val accSamples: MutableList<AccSample> = ArrayList()

    companion object {
        fun parseDataFromDataFrame(isCompressed: Boolean, frameType: BlePMDClient.PmdDataFrameType, frame: ByteArray, factor: Float, timeStamp: Long): AccData {
            return if (isCompressed) {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataCompressedType0(frame, factor, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_1 -> dataCompressedType1(frame, factor, timeStamp)
                    else -> throw java.lang.Exception("Compressed FrameType: $frameType is not supported by ACC data parser")
                }
            } else {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromRawType0(frame, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_1 -> dataFromRawType1(frame, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_2 -> dataFromRawType2(frame, timeStamp)
                    else -> throw java.lang.Exception("Raw FrameType: $frameType is not supported by ACC data parser")
                }
            }
        }

        private fun dataFromRawType0(value: ByteArray, timeStamp: Long): AccData {
            val accData = AccData(timeStamp)
            var offset = 0
            val resolution = 8
            val step = ceil(resolution.toDouble() / 8.0).toInt()
            while (offset < value.size) {
                val x = BleUtils.convertArrayToSignedInt(value, offset, step)
                offset += step
                val y = BleUtils.convertArrayToSignedInt(value, offset, step)
                offset += step
                val z = BleUtils.convertArrayToSignedInt(value, offset, step)
                offset += step
                accData.accSamples.add(AccSample(x, y, z))
            }
            return accData
        }

        private fun dataFromRawType1(value: ByteArray, timeStamp: Long): AccData {
            val accData = AccData(timeStamp)
            var offset = 0
            val resolution = 16
            val step = ceil(resolution.toDouble() / 8.0).toInt()
            while (offset < value.size) {
                val x = BleUtils.convertArrayToSignedInt(value, offset, step)
                offset += step
                val y = BleUtils.convertArrayToSignedInt(value, offset, step)
                offset += step
                val z = BleUtils.convertArrayToSignedInt(value, offset, step)
                offset += step
                accData.accSamples.add(AccSample(x, y, z))
            }
            return accData
        }

        private fun dataFromRawType2(value: ByteArray, timeStamp: Long): AccData {
            val accData = AccData(timeStamp)
            var offset = 0
            val resolution = 24
            val step = ceil(resolution.toDouble() / 8.0).toInt()
            while (offset < value.size) {
                val x = BleUtils.convertArrayToSignedInt(value, offset, step)
                offset += step
                val y = BleUtils.convertArrayToSignedInt(value, offset, step)
                offset += step
                val z = BleUtils.convertArrayToSignedInt(value, offset, step)
                offset += step
                accData.accSamples.add(AccSample(x, y, z))
            }
            return accData
        }

        private fun dataCompressedType0(value: ByteArray, factor: Float, timeStamp: Long): AccData {
            val accData = AccData(timeStamp)
            val accFactor = factor * 1000 // Modify the factor to get data in milliG
            val samples = parseDeltaFramesAll(value, 3, 16, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            for (sample in samples) {
                val x = (sample[0] * accFactor).toInt()
                val y = (sample[1] * accFactor).toInt()
                val z = (sample[2] * accFactor).toInt()
                accData.accSamples.add(AccSample(x, y, z))
            }
            return accData
        }

        private fun dataCompressedType1(value: ByteArray, factor: Float, timeStamp: Long): AccData {
            val accData = AccData(timeStamp)
            val samples = parseDeltaFramesAll(value, 3, 16, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            for (sample in samples) {
                val x = if (factor != 1.0f) (sample[0].toFloat() * factor).toInt() else sample[0]
                val y = if (factor != 1.0f) (sample[1].toFloat() * factor).toInt() else sample[1]
                val z = if (factor != 1.0f) (sample[2].toFloat() * factor).toInt() else sample[2]
                accData.accSamples.add(AccSample(x, y, z))
            }
            return accData
        }
    }
}