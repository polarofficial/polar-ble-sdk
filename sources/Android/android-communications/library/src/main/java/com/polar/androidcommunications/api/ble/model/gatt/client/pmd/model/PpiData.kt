package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.common.ble.BleUtils
import java.util.*

class PpiData(@JvmField val timeStamp: Long) {
    data class PPSample internal constructor(
        @JvmField
        val hr: Int,

        @JvmField
        val ppInMs: Int,

        @JvmField
        val ppErrorEstimate: Int,

        @JvmField
        val blockerBit: Int,

        @JvmField
        val skinContactStatus: Int,

        @JvmField
        val skinContactSupported: Int,
    )

    @JvmField
    val ppSamples: MutableList<PPSample> = ArrayList()

    companion object {
        fun parseDataFromDataFrame(isCompressed: Boolean, frameType: BlePMDClient.PmdDataFrameType, frame: ByteArray, factor: Float, timeStamp: Long): PpiData {
            return if (isCompressed) {
                throw java.lang.Exception("Compressed FrameType: $frameType is not supported by PPI data parser")
            } else {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromType0(frame, timeStamp)
                    else -> throw java.lang.Exception("Raw FrameType: $frameType is not supported by PPI data parser")
                }
            }
        }

        private fun dataFromType0(value: ByteArray, timeStamp: Long): PpiData {
            val ppiData = PpiData(timeStamp)
            var offset = 0
            while (offset < value.size) {
                val finalOffset = offset
                val sample = value.copyOfRange(finalOffset, finalOffset + 6)

                val hr = sample[0].toInt() and 0xFF
                val ppInMs = BleUtils.convertArrayToUnsignedLong(sample, 1, 2).toInt()
                val ppErrorEstimate = BleUtils.convertArrayToUnsignedLong(sample, 3, 2).toInt()
                val blockerBit: Int = sample[5].toInt() and 0x01
                val skinContactStatus: Int = sample[5].toInt() and 0x02 shr 1
                val skinContactSupported: Int = sample[5].toInt() and 0x04 shr 2

                ppiData.ppSamples.add(
                    PPSample(
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