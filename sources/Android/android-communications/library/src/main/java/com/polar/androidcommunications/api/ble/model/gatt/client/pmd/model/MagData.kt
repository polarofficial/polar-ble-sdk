package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import java.util.*

/**
 * Magnetometer data
 * @param timeStamp ns in epoch time. The time stamp represent time of last sample in magSamples list
 */
class MagData(val timeStamp: Long) {

    enum class CalibrationStatus(val id: Int) {
        NOT_AVAILABLE(-1),
        UNKNOWN(0),
        POOR(1),
        OK(2),
        GOOD(3);

        companion object {
            fun getById(id: Int): CalibrationStatus {
                return values().first { it.id == id }
            }
        }
    }

    data class MagSample internal constructor(
        // Sample contains signed x,y,z axis values in Gauss
        val x: Float,
        val y: Float,
        val z: Float,
        val calibrationStatus: CalibrationStatus = CalibrationStatus.NOT_AVAILABLE
    )

    @JvmField
    val magSamples: MutableList<MagSample> = ArrayList()

    companion object {
        fun parseDataFromDataFrame(isCompressed: Boolean, frameType: BlePMDClient.PmdDataFrameType, frame: ByteArray, factor: Float, timeStamp: Long): MagData {
            return if (isCompressed) {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromType0(frame, factor, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_1 -> dataFromType1(frame, factor, timeStamp)
                    else -> throw java.lang.Exception("Compressed FrameType: $frameType is not supported by Magnetometer data parser")
                }
            } else {
                throw java.lang.Exception("Raw FrameType: $frameType is not supported by Magnetometer data parser")
            }
        }

        private fun dataFromType0(value: ByteArray, factor: Float, timeStamp: Long): MagData {
            val samples = BlePMDClient.parseDeltaFramesAll(value, 3, 16, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            val magData = MagData(timeStamp)
            for (sample in samples) {
                val x = if (factor != 1.0f) sample[0] * factor else sample[0].toFloat()
                val y = if (factor != 1.0f) sample[1] * factor else sample[1].toFloat()
                val z = if (factor != 1.0f) sample[2] * factor else sample[2].toFloat()
                magData.magSamples.add(MagSample(x, y, z))
            }
            return magData
        }

        private fun dataFromType1(value: ByteArray, factor: Float, timeStamp: Long): MagData {
            val samples = BlePMDClient.parseDeltaFramesAll(value, 4, 16, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            val magData = MagData(timeStamp)
            val unitConversionFactor = 1000 // type 1 data arrives in milliGauss units
            for (sample in samples) {
                val x = (if (factor != 1.0f) sample[0] * factor else sample[0].toFloat()) / unitConversionFactor
                val y = (if (factor != 1.0f) sample[1] * factor else sample[1].toFloat()) / unitConversionFactor
                val z = (if (factor != 1.0f) sample[2] * factor else sample[2].toFloat()) / unitConversionFactor
                val status = CalibrationStatus.getById(sample[3])
                magData.magSamples.add(MagSample(x = x, y = y, z = z, calibrationStatus = status))
            }
            return magData
        }
    }
}