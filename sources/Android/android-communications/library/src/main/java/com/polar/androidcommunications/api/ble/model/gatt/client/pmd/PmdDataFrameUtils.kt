package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.common.ble.BleUtils
import com.polar.androidcommunications.common.ble.TypeUtils
import java.lang.Double.longBitsToDouble
import java.lang.Float.intBitsToFloat

internal object PmdDataFrameUtils {
    fun parseFrameDataField(data: ByteArray, coding: BlePMDClient.PmdDataFieldEncoding): Any {
        when (coding) {
            BlePMDClient.PmdDataFieldEncoding.FLOAT_IEEE754 -> {
                BleUtils.validate(data.size == 4, "PMD parser expects data size 4 when FLOAT_IEEE754 parsed. Input data size was " + data.size)
                val intIEEE754 = TypeUtils.convertArrayToUnsignedInt(data, 0, data.size).toInt()
                return intBitsToFloat(intIEEE754)
            }
            BlePMDClient.PmdDataFieldEncoding.DOUBLE_IEEE754 -> {
                BleUtils.validate(data.size == 8, "PMD parser expects data size 8 when DOUBLE_IEEE754 parsed. Input data size was " + data.size)
                val longIEEE754 = TypeUtils.convertArrayToUnsignedLong(data, 0, data.size).toLong()
                return longBitsToDouble(longIEEE754)
            }
            BlePMDClient.PmdDataFieldEncoding.SIGNED_INT -> {
                BleUtils.validate(data.size <= 4, "PMD parser expects data size smaller than 4 when SIGNED_INT parsed. Input data size was " + data.size)
                return TypeUtils.convertArrayToSignedInt(data, 0, data.size)
            }
            BlePMDClient.PmdDataFieldEncoding.UNSIGNED_BYTE -> {
                BleUtils.validate(data.size == 1, "PMD parser expects data size 1 when UNSIGNED_BYTE parsed. Input data size was " + data.size)
                return TypeUtils.convertArrayToUnsignedByte(data)
            }

            BlePMDClient.PmdDataFieldEncoding.UNSIGNED_INT -> {
                BleUtils.validate(data.size <= 4, "PMD parser expects data size smaller than 4 when UNSIGNED_INT parsed. Input data size was " + data.size)
                return TypeUtils.convertArrayToUnsignedInt(data)
            }
            BlePMDClient.PmdDataFieldEncoding.UNSIGNED_LONG -> {
                BleUtils.validate(data.size <= 8, "PMD parser expects data size smaller than 8 when UNSIGNED_LONG parsed. Input data size was " + data.size)
                return TypeUtils.convertArrayToUnsignedLong(data)
            }
            BlePMDClient.PmdDataFieldEncoding.BOOLEAN -> {
                BleUtils.validate(data.size == 1, "PMD parser expects data size 1 when BOOLEAN parsed. Input data size was " + data.size)
                return (data[0].toInt() != 0)
            }
        }
    }
}