package com.polar.androidcommunications.common.ble

object TypeUtils {

    fun convertArrayToUnsignedByte(data: ByteArray): UByte {
        BleUtils.validate(data.size == 1, "Array other than 1 cannot be converted to UByte. Input data size was " + data.size)
        return data[0].toUByte()
    }

    fun convertArrayToUnsignedInt(data: ByteArray, offset: Int, length: Int): UInt {
        return convertArrayToUnsignedInt(data.copyOfRange(offset, offset + length))
    }

    fun convertArrayToUnsignedInt(data: ByteArray): UInt {
        BleUtils.validate(data.size in 1..4, "Array bigger than 4 cannot be converted to UInt. Input data size was " + data.size)
        var result = 0u
        for (i in data.indices) {
            result = result or (data[i].toUByte().toUInt() shl i * 8)
        }
        return result
    }

    fun convertArrayToUnsignedLong(data: ByteArray, offset: Int, length: Int): ULong {
        return convertArrayToUnsignedLong(data.copyOfRange(offset, offset + length))
    }

    fun convertArrayToUnsignedLong(data: ByteArray): ULong {
        BleUtils.validate(data.size in 1..8, "Array bigger than 8 cannot be converted to ULong. Input data size was " + data.size)
        var result = 0u.toULong()
        for (i in data.indices) {
            result = result or (data[i].toUByte().toULong() shl i * 8)
        }
        return result
    }

    fun convertArrayToSignedInt(data: ByteArray, offset: Int, length: Int): Int {
        return convertArrayToSignedInt(data.copyOfRange(offset, offset + length))
    }

    fun convertArrayToSignedInt(data: ByteArray): Int {
        BleUtils.validate(data.size in 1..4, "Array bigger than 4 cannot be converted to Int. Input data size was " + data.size)
        var result = convertArrayToUnsignedInt(data)
        if (data.last() < 0) {
            val mask: UInt = 0xFFFFFFFFu shl data.size * 8
            result = (result or mask)
        }

        return result.toInt()
    }

    fun convertUnsignedByteToInt(byte: Byte): Int {
        return byte.toUByte().toInt()
    }
}