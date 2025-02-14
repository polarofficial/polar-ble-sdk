package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.Companion.parseDeltaFramesAll
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.common.ble.TypeUtils

internal class AccData {
    data class AccSample internal constructor(
        val timeStamp: ULong,
        // Sample contains signed x,y,z axis values in milliG
        val x: Int,
        val y: Int,
        val z: Int
    )

    val accSamples: MutableList<AccSample> = mutableListOf()

    companion object {
        private const val TYPE_0_SAMPLE_SIZE_IN_BYTES = 1
        private const val TYPE_0_SAMPLE_SIZE_IN_BITS = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_0_CHANNELS_IN_SAMPLE = 3

        private const val TYPE_1_SAMPLE_SIZE_IN_BYTES = 2
        private const val TYPE_1_SAMPLE_SIZE_IN_BITS = TYPE_1_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_1_CHANNELS_IN_SAMPLE = 3

        private const val TYPE_2_SAMPLE_SIZE_IN_BYTES = 3
        private const val TYPE_2_SAMPLE_SIZE_IN_BITS = TYPE_2_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_2_CHANNELS_IN_SAMPLE = 3

        fun parseDataFromDataFrame(frame: PmdDataFrame): AccData {
            return if (frame.isCompressedFrame) {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromCompressedType0(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_1 -> dataFromCompressedType1(frame)
                    else -> throw java.lang.Exception("Compressed FrameType: ${frame.frameType} is not supported by ACC data parser")
                }
            } else {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromRawType0(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_1 -> dataFromRawType1(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_2 -> dataFromRawType2(frame)
                    else -> throw java.lang.Exception("Raw FrameType: ${frame.frameType} is not supported by ACC data parser")
                }
            }
        }

        private fun dataFromRawType0(frame: PmdDataFrame): AccData {
            val accData = AccData()
            var offset = 0
            val step = TYPE_0_SAMPLE_SIZE_IN_BYTES
            val samplesSize = frame.dataContent.size / (step * TYPE_0_CHANNELS_IN_SAMPLE)
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0

            while (offset < frame.dataContent.size) {
                val x = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, step)
                offset += step
                val y = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, step)
                offset += step
                val z = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, step)
                offset += step
                accData.accSamples.add(AccSample(timeStamp = timeStamps[timeStampIndex], x = x, y = y, z = z))
                timeStampIndex++
            }
            return accData
        }

        private fun dataFromRawType1(frame: PmdDataFrame): AccData {
            val accData = AccData()
            var offset = 0
            val step = TYPE_1_SAMPLE_SIZE_IN_BYTES
            val samplesSize = frame.dataContent.size / (step * TYPE_1_CHANNELS_IN_SAMPLE)
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0

            while (offset < frame.dataContent.size) {
                val x = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, step)
                offset += step
                val y = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, step)
                offset += step
                val z = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, step)
                offset += step
                accData.accSamples.add(AccSample(timeStamp = timeStamps[timeStampIndex], x = x, y = y, z = z))
                timeStampIndex++
            }
            return accData
        }

        private fun dataFromRawType2(frame: PmdDataFrame): AccData {
            val accData = AccData()
            var offset = 0

            val step = TYPE_2_SAMPLE_SIZE_IN_BYTES

            val samplesSize = frame.dataContent.size / (step * TYPE_2_CHANNELS_IN_SAMPLE)
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0

            while (offset < frame.dataContent.size) {
                val x = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, step)
                offset += step
                val y = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, step)
                offset += step
                val z = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, step)
                offset += step
                accData.accSamples.add(AccSample(timeStamp = timeStamps[timeStampIndex], x = x, y = y, z = z))
                timeStampIndex++
            }
            return accData
        }

        private fun dataFromCompressedType0(frame: PmdDataFrame): AccData {
            //Note, special Wolfi type. See SAGRFC85.3
            val accData = AccData()
            val accFactor = frame.factor * 1000 // type 0 data arrives in G units, convert to milliG
            val samples = parseDeltaFramesAll(frame.dataContent, 3, 16, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samples.size, frame.sampleRate)
            for ((index, sample) in samples.withIndex()) {
                val x = (sample[0] * accFactor).toInt()
                val y = (sample[1] * accFactor).toInt()
                val z = (sample[2] * accFactor).toInt()
                accData.accSamples.add(AccSample(timeStamp = timeStamps[index], x = x, y = y, z = z))
            }
            return accData
        }

        private fun dataFromCompressedType1(frame: PmdDataFrame): AccData {
            val accData = AccData()
            val samples = parseDeltaFramesAll(frame.dataContent, TYPE_1_CHANNELS_IN_SAMPLE, TYPE_1_SAMPLE_SIZE_IN_BITS, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samples.size, frame.sampleRate)
            for ((index, sample) in samples.withIndex()) {
                val x = if (frame.factor != 1.0f) (sample[0].toFloat() * frame.factor).toInt() else sample[0]
                val y = if (frame.factor != 1.0f) (sample[1].toFloat() * frame.factor).toInt() else sample[1]
                val z = if (frame.factor != 1.0f) (sample[2].toFloat() * frame.factor).toInt() else sample[2]
                accData.accSamples.add(AccSample(timeStamp = timeStamps[index], x = x, y = y, z = z))
            }
            return accData
        }
    }
}