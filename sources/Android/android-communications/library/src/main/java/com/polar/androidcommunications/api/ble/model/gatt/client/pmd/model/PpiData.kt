package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

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
    )

    val ppiSamples: MutableList<PpiSample> = mutableListOf()

    companion object {
        fun parseDataFromDataFrame(frame: PmdDataFrame): PpiData {
            return if (frame.isCompressedFrame) {
                throw java.lang.Exception("Compressed FrameType: ${frame.frameType} is not supported by PPI data parser")
            } else {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromType0(frame)
                    else -> throw java.lang.Exception("Raw FrameType: ${frame.frameType} is not supported by PPI data parser")
                }
            }
        }

        private fun dataFromType0(frame: PmdDataFrame): PpiData {
            val ppiData = PpiData()
            var offset = 0
            while (offset < frame.dataContent.size) {
                val finalOffset = offset
                val sample = frame.dataContent.copyOfRange(finalOffset, finalOffset + 6)

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
                        skinContactSupported = skinContactSupported
                    )
                )
                offset += 6
            }
            return ppiData
        }
    }
}