package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException
import com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException.Companion.toHex
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.PmdDataFieldEncoding
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrameUtils
import java.lang.Float.intBitsToFloat

internal class PressureData {

    data class PressureSample internal constructor(
        val timeStamp: ULong = 0uL,
        // Sample contains signed pressure value in bar
        val pressure: Float
    )

    val pressureSamples: MutableList<PressureSample> = mutableListOf()

    companion object {
        private const val TYPE_0_SAMPLE_SIZE_IN_BYTES = 4
        private const val TYPE_0_SAMPLE_SIZE_IN_BITS = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_0_CHANNELS_IN_SAMPLE = 1

        fun parseDataFromDataFrame(frame: PmdDataFrame): PressureData {
            return if (frame.isCompressedFrame) {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromCompressedType0(frame)
                    else -> throw PmdDataParseException(
                        "Pressure compressed frame type ${frame.frameType} is not supported"
                    )
                }
            } else {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromRawType0(frame)
                    else -> throw PmdDataParseException(
                        "Pressure raw frame type ${frame.frameType} is not supported"
                    )
                }
            }
        }

        private fun dataFromCompressedType0(frame: PmdDataFrame): PressureData {
            if (frame.dataContent.isEmpty()) {
                throw PmdDataParseException(
                    "Pressure compressed TYPE_0 dataContent is empty"
                )
            }
            val pressureData = PressureData()
            val samples = BlePMDClient.parseDeltaFramesAll(frame.dataContent, TYPE_0_CHANNELS_IN_SAMPLE, TYPE_0_SAMPLE_SIZE_IN_BITS, PmdDataFieldEncoding.FLOAT_IEEE754)

            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samples.size, frame.sampleRate)
            for ((index, sample) in samples.withIndex()) {
                val pressure = if (frame.factor != 1.0f) intBitsToFloat(sample[0]) * frame.factor else intBitsToFloat(sample[0])
                pressureData.pressureSamples.add(PressureSample(timeStamp = timeStamps[index], pressure))
            }
            return pressureData
        }

        private fun dataFromRawType0(frame: PmdDataFrame): PressureData {
            if (frame.dataContent.isEmpty() || frame.dataContent.size % TYPE_0_SAMPLE_SIZE_IN_BYTES != 0) {
                throw PmdDataParseException(
                    "Pressure raw TYPE_0 dataContent size ${frame.dataContent.size} is not a " +
                    "non-zero multiple of expected sample size $TYPE_0_SAMPLE_SIZE_IN_BYTES. " +
                    "Data: ${frame.dataContent.toHex()}"
                )
            }
            val pressureData = PressureData()
            var offset = 0
            val step = TYPE_0_SAMPLE_SIZE_IN_BYTES

            val samplesSize = frame.dataContent.size / (step * TYPE_0_CHANNELS_IN_SAMPLE)
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0

            while (offset < frame.dataContent.size) {
                val pressure = PmdDataFrameUtils.parseFrameDataField(frame.dataContent.sliceArray(offset until (offset + TYPE_0_SAMPLE_SIZE_IN_BYTES)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += TYPE_0_SAMPLE_SIZE_IN_BYTES
                pressureData.pressureSamples.add(PressureSample(timeStamp = timeStamps[timeStampIndex], pressure))
                timeStampIndex++
            }
            return pressureData
        }
    }
}