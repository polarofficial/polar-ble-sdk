package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrameUtils
import com.polar.androidcommunications.common.ble.TypeUtils
import kotlin.experimental.and

/**
 * Sealed class to represent Ppg data sample
 */
internal sealed class PpgDataSample

internal class PpgData {
    // PPG Data Sample 0
    data class PpgDataFrameType0 internal constructor(
        val timeStamp: ULong,
        val ppgDataSamples: List<Int>,
        val ambientSample: Int
    ) : PpgDataSample()

    // PPG Data Sample 3
    data class PpgDataFrameType8 internal constructor(
        val timeStamp: ULong,
        val ppgDataSamples: List<Int>,
        val status: UInt
    ) : PpgDataSample()

    // PPG Data frame type 4
    data class PpgDataFrameType4 internal constructor(
        val timeStamp: ULong,
        val numIntTs: List<UInt>,
        val channel1GainTs: List<UInt>,
        val channel2GainTs: List<UInt>
    ) : PpgDataSample()

    // PPG Data frame type 5
    data class PpgDataFrameType5 internal constructor(
        val timeStamp: ULong,
        val operationMode: UInt
    ) : PpgDataSample()

    // PPG Data Sample 2
    data class PpgDataFrameType7 internal constructor(
        val timeStamp: ULong,
        val ppgDataSamples: List<Int>,
        val status: UInt
    ) : PpgDataSample()

    data class PpgDataFrameType9 internal constructor(
        val timeStamp: ULong,
        val numIntTs: List<UInt>,
        val channel1GainTs: List<UInt>,
        val channel2GainTs: List<UInt>
    ) : PpgDataSample()

    data class PpgDataFrameType10 internal constructor(
        val timeStamp: ULong,
        val greenSamples: List<Int>,
        val redSamples: List<Int>,
        val irSamples: List<Int>,
        val status: Int
    ) : PpgDataSample()

    // PPG Data Sport Id
    data class PpgDataSampleSportId internal constructor(
        val timeStamp: ULong,
        val sportId: ULong
    ) : PpgDataSample()

    val ppgSamples: MutableList<PpgDataSample> = ArrayList()

    companion object {
        private const val TAG = "PpgData"

        private const val TYPE_0_SAMPLE_SIZE_IN_BYTES = 3
        private const val TYPE_0_SAMPLE_SIZE_IN_BITS = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_0_CHANNELS_IN_SAMPLE = 4

        private const val TYPE_4_NUM_INTS_SIZE = 12
        private const val TYPE_4_CHANNEL_0_AND_1_SIZE = 24
        private const val TYPE_4_SAMPLE_SIZE_IN_BYTES =
            TYPE_4_NUM_INTS_SIZE + TYPE_4_CHANNEL_0_AND_1_SIZE

        private const val TYPE_5_SAMPLE_SIZE_IN_BYTES = 4

        private const val TYPE_6_SAMPLE_SIZE_IN_BYTES = 8

        private const val TYPE_7_SAMPLE_SIZE_IN_BYTES = 3
        private const val TYPE_7_SAMPLE_SIZE_IN_BITS = TYPE_7_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_7_CHANNELS_IN_SAMPLE = 17

        private const val TYPE_8_SAMPLE_SIZE_IN_BYTES = 3
        private const val TYPE_8_SAMPLE_SIZE_IN_BITS = TYPE_8_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_8_CHANNELS_IN_SAMPLE = 25

        private const val TYPE_9_NUM_INTS_SIZE = 12
        private const val TYPE_9_CHANNEL_0_AND_1_SIZE = 24
        private const val TYPE_9_SAMPLE_SIZE_IN_BYTES =
            TYPE_9_NUM_INTS_SIZE + TYPE_9_CHANNEL_0_AND_1_SIZE

        private const val TYPE_10_SAMPLE_SIZE_IN_BYTES = 3
        private const val TYPE_10_SAMPLE_SIZE_IN_BITS = TYPE_10_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_10_STATUS_SIZE = 20
        private const val TYPE_10_CHANNELS_IN_SAMPLE = 21

        fun parseDataFromDataFrame(frame: PmdDataFrame): PpgData {
            return if (frame.isCompressedFrame) {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromCompressedType0(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_7 -> dataFromCompressedType7(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_8 -> dataFromCompressedType8(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_10 -> dataFromCompressedType10(frame)
                    else -> throw java.lang.Exception("Compressed FrameType: ${frame.frameType} is not supported by PPG data parser")
                }
            } else {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromRawType0(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_4 -> dataFromRawType4(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_5 -> dataFromRawType5(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_6 -> dataFromRawType6(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_9 -> dataFromRawType9(frame)
                    else -> throw java.lang.Exception("Raw FrameType: ${frame.frameType} is not supported by PPG data parser")
                }
            }
        }

        private fun dataFromRawType0(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()
            val step = TYPE_0_SAMPLE_SIZE_IN_BYTES

            val samplesSize = frame.dataContent.size / (step * TYPE_0_CHANNELS_IN_SAMPLE)
            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samplesSize,
                frame.sampleRate
            )
            var timeStampIndex = 0

            var i = 0
            while (i < frame.dataContent.size) {
                val samples: MutableList<Int> = mutableListOf()
                for (ch in 0 until TYPE_0_CHANNELS_IN_SAMPLE) {
                    samples.add(TypeUtils.convertArrayToSignedInt(frame.dataContent, i, step))
                    i += step
                }
                ppgData.ppgSamples.add(
                    PpgDataFrameType0(
                        timeStamp = timeStamps[timeStampIndex],
                        samples.subList(0, 3),
                        samples[3]
                    )
                )
                timeStampIndex++
            }
            return ppgData
        }

        private fun dataFromRawType4(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()

            val samplesSize = frame.dataContent.size / TYPE_4_SAMPLE_SIZE_IN_BYTES
            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samplesSize,
                frame.sampleRate
            )
            var timeStampIndex = 0

            var offset = 0
            while (offset < frame.dataContent.size) {
                val numIntTs =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_4_NUM_INTS_SIZE))
                        .map { it.toUByte().toUInt() }
                offset += TYPE_4_NUM_INTS_SIZE
                val channel1GainTs =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_4_CHANNEL_0_AND_1_SIZE))
                        .toList()
                        .mapIndexedNotNull { idx, v -> if (idx % 2 == 0) (v and 0x07).toUInt() else null }
                val channel2GainTs =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_4_CHANNEL_0_AND_1_SIZE))
                        .toList()
                        .mapIndexedNotNull { idx, v -> if (idx % 2 == 1) (v and 0x07).toUInt() else null }
                offset += TYPE_4_CHANNEL_0_AND_1_SIZE
                ppgData.ppgSamples.add(
                    PpgDataFrameType4(
                        timeStamp = timeStamps[timeStampIndex],
                        numIntTs = numIntTs,
                        channel1GainTs = channel1GainTs,
                        channel2GainTs = channel2GainTs
                    )
                )
                timeStampIndex++
            }
            return ppgData
        }

        private fun dataFromRawType5(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()
            var offset = 0

            val samplesSize = frame.dataContent.size / TYPE_5_SAMPLE_SIZE_IN_BYTES
            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samplesSize,
                frame.sampleRate
            )
            var timeStampIndex = 0

            while (offset < frame.dataContent.size) {
                val operationMode = PmdDataFrameUtils.parseFrameDataField(
                    frame.dataContent.sliceArray(offset until (offset + TYPE_5_SAMPLE_SIZE_IN_BYTES)),
                    BlePMDClient.PmdDataFieldEncoding.UNSIGNED_INT
                ) as UInt
                offset += TYPE_5_SAMPLE_SIZE_IN_BYTES
                ppgData.ppgSamples.add(
                    PpgDataFrameType5(
                        timeStamp = timeStamps[timeStampIndex],
                        operationMode = operationMode
                    )
                )
                timeStampIndex++
            }
            return ppgData
        }

        private fun dataFromRawType6(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()
            val sportId = PmdDataFrameUtils.parseFrameDataField(
                frame.dataContent.sliceArray(0 until TYPE_6_SAMPLE_SIZE_IN_BYTES),
                BlePMDClient.PmdDataFieldEncoding.UNSIGNED_LONG
            ) as ULong

            val samplesSize = frame.dataContent.size / TYPE_6_SAMPLE_SIZE_IN_BYTES
            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samplesSize,
                frame.sampleRate
            )

            ppgData.ppgSamples.add(
                PpgDataSampleSportId(
                    timeStamp = timeStamps.first(),
                    sportId = sportId
                )
            )
            return ppgData
        }

        private fun dataFromRawType9(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()

            val samplesSize = frame.dataContent.size / TYPE_9_SAMPLE_SIZE_IN_BYTES
            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samplesSize,
                frame.sampleRate
            )
            var timeStampIndex = 0

            var offset = 0
            while (offset < frame.dataContent.size) {
                val numIntTs =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_9_NUM_INTS_SIZE))
                        .map { it.toUByte().toUInt() }
                offset += TYPE_9_NUM_INTS_SIZE
                val channel1GainTs =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_9_CHANNEL_0_AND_1_SIZE))
                        .toList()
                        .mapIndexedNotNull { idx, v -> if (idx % 2 == 0) (v and 0x07).toUInt() else null }
                val channel2GainTs =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_9_CHANNEL_0_AND_1_SIZE))
                        .toList()
                        .mapIndexedNotNull { idx, v -> if (idx % 2 == 1) (v and 0x07).toUInt() else null }
                offset += TYPE_9_CHANNEL_0_AND_1_SIZE
                ppgData.ppgSamples.add(
                    PpgDataFrameType9(
                        timeStamp = timeStamps[timeStampIndex],
                        numIntTs = numIntTs,
                        channel1GainTs = channel1GainTs,
                        channel2GainTs = channel2GainTs
                    )
                )
                timeStampIndex++
            }
            return ppgData
        }

        private fun dataFromRawType10(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()

            val samplesSize = frame.dataContent.size / TYPE_10_SAMPLE_SIZE_IN_BYTES
            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samplesSize,
                frame.sampleRate
            )
            var timeStampIndex = 0

            var offset = 0
            while (offset < frame.dataContent.size) {
                val grn =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_10_SAMPLE_SIZE_IN_BYTES))
                        .map { it.toUByte().toInt() }
                offset += TYPE_10_SAMPLE_SIZE_IN_BYTES
                val red =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_10_SAMPLE_SIZE_IN_BYTES))
                        .map { it.toUByte().toInt() }
                offset += TYPE_10_SAMPLE_SIZE_IN_BYTES
                val ir =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_10_SAMPLE_SIZE_IN_BYTES))
                        .map { it.toUByte().toInt() }
                offset += TYPE_10_SAMPLE_SIZE_IN_BYTES
                val status =
                    frame.dataContent.sliceArray(offset until (offset + TYPE_10_STATUS_SIZE))
                        .map { it.toUByte().toInt() }.last()
                offset += TYPE_10_STATUS_SIZE

                ppgData.ppgSamples.add(
                    PpgDataFrameType10(
                        timeStamp = timeStamps[timeStampIndex],
                        redSamples = red,
                        greenSamples = grn,
                        irSamples = ir,
                        status = status
                    )
                )
                timeStampIndex++
            }
            return ppgData
        }

        private fun dataFromCompressedType0(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()
            val samples = BlePMDClient.parseDeltaFramesAll(
                frame.dataContent,
                TYPE_0_CHANNELS_IN_SAMPLE,
                TYPE_0_SAMPLE_SIZE_IN_BITS,
                BlePMDClient.PmdDataFieldEncoding.SIGNED_INT
            )

            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samples.size,
                frame.sampleRate
            )

            for ((index, sample) in samples.withIndex()) {
                val ppg0 = (sample[0].toFloat() * frame.factor).toInt()
                val ppg1 = (sample[1].toFloat() * frame.factor).toInt()
                val ppg2 = (sample[2].toFloat() * frame.factor).toInt()
                val ambient = (sample[3].toFloat() * frame.factor).toInt()
                ppgData.ppgSamples.add(
                    PpgDataFrameType0(
                        timeStamp = timeStamps[index],
                        ppgDataSamples = listOf(ppg0, ppg1, ppg2),
                        ambient
                    )
                )
            }
            return ppgData
        }

        private fun dataFromCompressedType7(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()
            val samples = BlePMDClient.parseDeltaFramesAll(
                frame.dataContent,
                TYPE_7_CHANNELS_IN_SAMPLE,
                TYPE_7_SAMPLE_SIZE_IN_BITS,
                BlePMDClient.PmdDataFieldEncoding.SIGNED_INT
            )

            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samples.size,
                frame.sampleRate
            )

            for ((index, sample) in samples.withIndex()) {
                val channels = sample.subList(0, 16).map {
                    if (frame.factor != 1.0f) (it.toFloat() * frame.factor).toInt() else it
                }
                val status = (sample[16] and 0xFFFFFF).toUInt()
                ppgData.ppgSamples.add(
                    PpgDataFrameType7(
                        timeStamp = timeStamps[index],
                        ppgDataSamples = channels,
                        status
                    )
                )
            }
            return ppgData
        }

        private fun dataFromCompressedType8(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()
            val samples = BlePMDClient.parseDeltaFramesAll(
                frame.dataContent,
                TYPE_8_CHANNELS_IN_SAMPLE,
                TYPE_8_SAMPLE_SIZE_IN_BITS,
                BlePMDClient.PmdDataFieldEncoding.SIGNED_INT
            )

            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samples.size,
                frame.sampleRate
            )

            for ((index, sample) in samples.withIndex()) {
                val channels = sample.subList(0, 24).map {
                    if (frame.factor != 1.0f) (it.toFloat() * frame.factor).toInt() else it
                }
                val status = (sample[24] and 0xFFFFFF).toUInt()
                ppgData.ppgSamples.add(
                    PpgDataFrameType8(
                        timeStamp = timeStamps[index],
                        ppgDataSamples = channels,
                        status
                    )
                )
            }
            return ppgData
        }

        private fun dataFromCompressedType10(frame: PmdDataFrame): PpgData {
            val ppgData = PpgData()
            val samples = BlePMDClient.parseDeltaFramesAll(
                frame.dataContent,
                TYPE_10_CHANNELS_IN_SAMPLE,
                TYPE_10_SAMPLE_SIZE_IN_BITS,
                BlePMDClient.PmdDataFieldEncoding.SIGNED_INT
            )

            val timeStamps = PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = frame.previousTimeStamp,
                frameTimeStamp = frame.timeStamp,
                samplesSize = samples.size,
                frame.sampleRate
            )

            for ((index, sample) in samples.withIndex()) {
                val greenSamples = sample.subList(0, 8).map {
                    if (frame.factor != 1.0f) (it.toFloat() * frame.factor).toInt() else it
                }

                val redSamples = sample.subList(8, 14).map {
                    if (frame.factor != 1.0f) (it.toFloat() * frame.factor).toInt() else it
                }

                val irSamples = sample.subList(14, 20).map {
                    if (frame.factor != 1.0f) (it.toFloat() * frame.factor).toInt() else it
                }

                val status = sample.last()

                ppgData.ppgSamples.add(
                    PpgDataFrameType10(
                        timeStamp = timeStamps[index],
                        greenSamples = greenSamples,
                        redSamples = redSamples,
                        irSamples = irSamples,
                        status
                    )
                )
            }
            return ppgData
        }
    }
}