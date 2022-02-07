package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClientUtils
import com.polar.androidcommunications.common.ble.BleUtils
import java.util.*
import kotlin.experimental.and

/**
 * Sealed class to represent Ppg data sample
 */
sealed class PpgDataSample

class PpgData internal constructor(val timeStamp: Long) {

    // PPG Data Sample 0
    data class PpgDataSampleType0 internal constructor(
        val ppgDataSamples: List<Int>,
        val ambientSample: Int
    ) : PpgDataSample()

    // PPG Data Sample 2
    data class PpgDataSampleType2 internal constructor(
        val ppgDataSamples: List<Int>,
        val status: UInt
    ) : PpgDataSample()

    // PPG Data frame type 4
    data class PpgDataSampleFrameType4 internal constructor(
        val numIntTs: List<UInt>,
        val channel1GainTs: List<UInt>,
        val channel2GainTs: List<UInt>
    ) : PpgDataSample()

    // PPG Data frame type 5
    data class PpgDataSampleFrameType5 internal constructor(
        val operationMode: UInt
    ) : PpgDataSample()

    // PPG Data Sport Id
    data class PpgDataSampleSportId internal constructor(
        val sportId: ULong
    ) : PpgDataSample()

    val ppgSamples: MutableList<PpgDataSample> = ArrayList()

    companion object {
        const val TAG = "PpgData"
        fun parseDataFromDataFrame(isCompressed: Boolean, frameType: BlePMDClient.PmdDataFrameType, frame: ByteArray, factor: Float, timeStamp: Long): PpgData {
            return if (isCompressed) {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromCompressedType0(frame, factor, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_7 -> dataFromCompressedType7(frame, factor, timeStamp)
                    else -> throw java.lang.Exception("Compressed FrameType: $frameType is not supported by PPG data parser")
                }
            } else {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromRawType0(frame, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_4 -> dataFromRawType4(frame, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_5 -> dataFromRawType5(frame, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_6 -> dataFromRawType6(frame, timeStamp)
                    else -> throw java.lang.Exception("Raw FrameType: $frameType is not supported by PPG data parser")
                }
            }
        }

        private fun dataFromRawType0(value: ByteArray, timeStamp: Long): PpgData {
            val ppgData = PpgData(timeStamp)
            val step = 3
            var i = 0
            while (i < value.size) {
                val samples: MutableList<Int> = ArrayList()
                for (ch in 0 until 4) {
                    samples.add(BleUtils.convertArrayToSignedInt(value, i, step))
                    i += step
                }
                ppgData.ppgSamples.add(PpgDataSampleType0(samples.subList(0, 3), samples[3]))
            }
            return ppgData
        }

        var dataType4Counter = 0
        private fun dataFromRawType4(frame: ByteArray, timeStamp: Long): PpgData {
            dataType4Counter++

            val ppgData = PpgData(timeStamp)
            var offset = 0
            while (offset < frame.size) {
                val numIntTs = frame.sliceArray(offset..(offset + 11)).map { it.toUByte().toUInt() }
                offset += 12
                val channel1GainTs = frame.sliceArray(offset..(offset + 23)).toList().mapIndexedNotNull { idx, v -> if (idx % 2 == 0) (v and 0x07).toUInt() else null }
                val channel2GainTs = frame.sliceArray(offset..(offset + 23)).toList().mapIndexedNotNull { idx, v -> if (idx % 2 == 1) (v and 0x07).toUInt() else null }
                offset += 24
                ppgData.ppgSamples.add(
                    PpgDataSampleFrameType4(
                        numIntTs = numIntTs,
                        channel1GainTs = channel1GainTs,
                        channel2GainTs = channel2GainTs
                    )
                )
            }
            return ppgData
        }

        private fun dataFromRawType5(frame: ByteArray, timeStamp: Long): PpgData {
            val ppgData = PpgData(timeStamp)
            var offset = 0
            while (offset < frame.size) {
                val operationMode = BlePMDClientUtils.parseFrameDataField(frame.sliceArray(offset..(offset + 3)), BlePMDClient.PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 4
                ppgData.ppgSamples.add(PpgDataSampleFrameType5(operationMode = operationMode))
            }
            return ppgData
        }

        private fun dataFromCompressedType0(value: ByteArray, factor: Float, timeStamp: Long): PpgData {
            val samples = BlePMDClient.parseDeltaFramesAll(value, 4, 24, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            val ppgData = PpgData(timeStamp)
            for (sample in samples) {
                val ppg0 = (sample[0].toFloat() * factor).toInt()
                val ppg1 = (sample[1].toFloat() * factor).toInt()
                val ppg2 = (sample[2].toFloat() * factor).toInt()
                val ambient = (sample[3].toFloat() * factor).toInt()
                ppgData.ppgSamples.add(PpgDataSampleType0(ppgDataSamples = listOf(ppg0, ppg1, ppg2), ambient))
            }
            return ppgData
        }

        private fun dataFromRawType6(frame: ByteArray, timeStamp: Long): PpgData {
            val ppgData = PpgData(timeStamp)
            val sportId = BlePMDClientUtils.parseFrameDataField(frame.sliceArray(0..7), BlePMDClient.PmdDataFieldEncoding.UNSIGNED_LONG) as ULong
            ppgData.ppgSamples.add(PpgDataSampleSportId(sportId = sportId))
            return ppgData
        }

        private fun dataFromCompressedType7(value: ByteArray, factor: Float, timeStamp: Long): PpgData {
            val samples = BlePMDClient.parseDeltaFramesAll(value, 17, 24, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            val ppgData = PpgData(timeStamp)
            for (sample in samples) {
                val channels = sample.subList(0, 16).map {
                    if (factor != 1.0f) (it.toFloat() * factor).toInt() else it
                }
                val status = sample[16].toUInt()

                ppgData.ppgSamples.add(PpgDataSampleType2(ppgDataSamples = channels, status))
            }
            return ppgData
        }
    }
}