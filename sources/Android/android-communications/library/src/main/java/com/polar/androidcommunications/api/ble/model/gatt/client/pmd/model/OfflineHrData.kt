package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException
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
                throw PmdDataParseException(
                    "Offline HR compressed frame type ${frame.frameType} is not supported"
                )
            } else {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromType0(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_1 -> dataFromType1(frame)
                    else -> throw PmdDataParseException(
                        "Offline HR raw frame type ${frame.frameType} is not supported"
                    )
                }
            }
        }

        private fun dataFromType0(frame: PmdDataFrame): OfflineHrData {
            // Offline HR data contains HR values — raw bytes are NOT included in exception
            // messages as they may contain sensitive health information.
            if (frame.dataContent.isEmpty()) {
                throw PmdDataParseException(
                    "Offline HR raw TYPE_0 dataContent is empty"
                )
            }
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
            // Offline HR data contains HR values — raw bytes are NOT included in exception
            // messages as they may contain sensitive health information.
            if (frame.dataContent.isEmpty() || frame.dataContent.size % 3 != 0) {
                throw PmdDataParseException(
                    "Offline HR raw TYPE_1 dataContent size ${frame.dataContent.size} is not a " +
                    "non-zero multiple of expected sample size 3"
                )
            }
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