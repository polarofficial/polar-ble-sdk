package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.exceptions.BleNotImplemented
import com.polar.androidcommunications.common.ble.TypeUtils

class PmdDataFrame(
    data: ByteArray,
    getPreviousTimeStamp: (PmdMeasurementType, PmdDataFrameType) -> ULong,
    getFactor: (PmdMeasurementType) -> Float,
    getSampleRate: (PmdMeasurementType) -> Int,
) {
    companion object {
        private const val DELTA_FRAME_BIT_MASK = 0x80.toByte()
        private const val FRAME_TYPE_BIT_MASK = 0x7F.toByte()
    }

    val measurementType: PmdMeasurementType = PmdMeasurementType.fromId(data[0])
    val timeStamp: ULong = TypeUtils.convertArrayToUnsignedLong(data, 1, 8)
    val frameType: PmdDataFrameType
    val isCompressedFrame: Boolean
    val dataContent: ByteArray

    val previousTimeStamp: ULong
    val factor: Float
    val sampleRate: Int

    init {
        val frameTypeField = data[9].toUByte()
        frameType = PmdDataFrameType.getTypeFromFrameDataByte(frameTypeField)
        isCompressedFrame = isCompressedFrame(frameTypeField)
        val content = ByteArray(data.size - 10)
        System.arraycopy(data, 10, content, 0, content.size)
        dataContent = content

        previousTimeStamp = getPreviousTimeStamp(measurementType, frameType)
        factor = getFactor(measurementType)
        sampleRate = getSampleRate(measurementType)
    }

    private fun isCompressedFrame(frameTypeByte: UByte): Boolean {
        return frameTypeByte and DELTA_FRAME_BIT_MASK.toUByte() > 0u
    }

    enum class PmdDataFrameType(val id: UByte) {
        TYPE_0(0u),
        TYPE_1(1u),
        TYPE_2(2u),
        TYPE_3(3u),
        TYPE_4(4u),
        TYPE_5(5u),
        TYPE_6(6u),
        TYPE_7(7u),
        TYPE_8(8u),
        TYPE_9(9u),
        TYPE_10(10u);

        companion object {
            fun getTypeFromFrameDataByte(byte: UByte): PmdDataFrameType {
                for (type in values()) {
                    if (type.id == (byte and FRAME_TYPE_BIT_MASK.toUByte())) {
                        return type
                    }
                }
                throw BleNotImplemented("PmdFrameType cannot be parsed from $byte")
            }
        }
    }
}