package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.common.ble.TypeUtils.convertUnsignedByteToInt

/**
 * Offline hr data
 */
internal class OfflineHrData {

    data class OfflineHrSample internal constructor(
        val hr: Int,
        val ppgQuality: Int,
        val correctedHr: Int
    )

    val hrSamples: MutableList<OfflineHrSample> = mutableListOf()

    companion object {
        fun parseDataFromDataFrame(frame: PmdDataFrame): OfflineHrData {
            return if (frame.isCompressedFrame) {
                throw java.lang.Exception("Compressed FrameType: ${frame.frameType} is not supported by Offline HR data parser")
            } else {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromType0(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_1 -> dataFromType1(frame)
                    else -> throw java.lang.Exception("Raw FrameType: ${frame.frameType} is not supported by Offline HR data parser")
                }
            }
        }

        private fun dataFromType0(frame: PmdDataFrame): OfflineHrData {
            var offlineHrData = OfflineHrData()
            var offset = 0
            while (offset < frame.dataContent.size) {
                val hr = convertUnsignedByteToInt(frame.dataContent[offset])
                offlineHrData.hrSamples.add(OfflineHrSample(hr, 0, 0))
                offset += 1
            }
            return offlineHrData
        }

        private fun dataFromType1(frame: PmdDataFrame): OfflineHrData {
            val offlineHrData = OfflineHrData()
            var offset = 0
            while (offset < frame.dataContent.size) {
                val hr = convertUnsignedByteToInt(frame.dataContent[offset])
                offset += 1
                val ppgQual = convertUnsignedByteToInt(frame.dataContent[offset])
                offset += 1
                val correctedHr = convertUnsignedByteToInt(frame.dataContent[offset])
                offlineHrData.hrSamples.add(OfflineHrSample(hr, ppgQual, correctedHr))
                offset += 1
            }
            return offlineHrData
        }
    }
}