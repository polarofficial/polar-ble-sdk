package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.common.ble.TypeUtils

internal class PpiData {
    data class PpiSample internal constructor(
        val hr: Int,
        val ppInMs: Int,
        val ppErrorEstimate: Int,
        val blockerBit: Int,
        val skinContactStatus: Int,
        val skinContactSupported: Int,
        var timeStamp: ULong
    )

    val ppiSamples: MutableList<PpiSample> = mutableListOf()

    companion object {
        private const val PPI_SAMPLE_SIZE_IN_BYTES = 6

        fun parseDataFromDataFrame(frame: PmdDataFrame): PpiData {
            return if (frame.isCompressedFrame) {
                throw PmdDataParseException(
                    "PPI compressed frame type ${frame.frameType} is not supported"
                )
            } else {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromType0(frame)
                    else -> throw PmdDataParseException(
                        "PPI raw frame type ${frame.frameType} is not supported"
                    )
                }
            }
        }

        private fun dataFromType0(frame: PmdDataFrame): PpiData {
            // PPI data contains HR values — raw bytes are NOT included in exception messages
            // as they may contain sensitive health information.
            if (frame.dataContent.isEmpty() || frame.dataContent.size % PPI_SAMPLE_SIZE_IN_BYTES != 0) {
                throw PmdDataParseException(
                    "PPI raw TYPE_0 dataContent size ${frame.dataContent.size} is not a " +
                    "non-zero multiple of expected sample size $PPI_SAMPLE_SIZE_IN_BYTES"
                )
            }
            val ppiData = PpiData()
            var offset = 0
            while (offset < frame.dataContent.size) {
                val finalOffset = offset
                val sample = frame.dataContent.copyOfRange(finalOffset, finalOffset + PPI_SAMPLE_SIZE_IN_BYTES)

                val hr = sample[0].toInt() and 0xFF
                val ppInMs = TypeUtils.convertArrayToUnsignedLong(sample, 1, 2).toInt()
                val ppErrorEstimate = TypeUtils.convertArrayToUnsignedLong(sample, 3, 2).toInt()
                val blockerBit: Int = sample[5].toInt() and 0x01
                val skinContactStatus: Int = sample[5].toInt() and 0x02 shr 1
                val skinContactSupported: Int = sample[5].toInt() and 0x04 shr 2

                ppiData.ppiSamples.add(
                    PpiSample(
                        hr = hr,
                        ppInMs = ppInMs,
                        ppErrorEstimate = ppErrorEstimate,
                        blockerBit = blockerBit,
                        skinContactStatus = skinContactStatus,
                        skinContactSupported = skinContactSupported,
                        timeStamp = 0u // Set actual timestamp later.
                    )
                )
                offset += PPI_SAMPLE_SIZE_IN_BYTES
            }

            if (frame.timeStamp != 0uL) {
                var currentTimestamp = frame.timeStamp

                for (i in ppiData.ppiSamples.indices.reversed()) {
                    val sample = ppiData.ppiSamples[i]
                    sample.timeStamp = currentTimestamp
                    currentTimestamp -= (sample.ppInMs.toULong() * 1_000_000UL)
                }
            }

            return ppiData
        }
    }
}