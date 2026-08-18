package com.polar.androidcommunications.common.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BleUtilsTest {

    @Test
    fun validate_whenValid_doesNotThrow() {
        // Invocation should not throw
        BleUtils.validate(true, "should not fail")
    }

    @Test
    fun validate_whenInvalid_throwsIllegalArgumentExceptionWithMessage() {
        try {
            // method invocation
            BleUtils.validate(false, "validation failed")
            fail("Expected IllegalArgumentException to be thrown")
        } catch (e: IllegalArgumentException) {
            // required assertions
            assertEquals("validation failed", e.message)
        }
    }

    @Test
    fun advertisementBytes2Map_parsesFlagsAndCompleteLocalName() {

        // Arrange
        // 0x01 (GAP_ADTYPE_FLAGS)
        // 0x09 (GAP_ADTYPE_LOCAL_NAME_COMPLETE)
        // 0x02 (GAP_ADTYPE_16BIT_MORE)
        // 0xFF (GAP_ADTYPE_MANUFACTURER_SPECIFIC)

        val adv = byteArrayOf(
            0x02, 0x01, 0x06, 0x03, 0x02, 0xEE.toByte(), 0xFE.toByte(),
            0x0D, 0xFF.toByte(), 0x6B, 0x00, 0x72, 0x08, 0x1A, 0x17,
            0x8D.toByte(), 0x02, 0x00, 0x00, 0x00, 0x00, 0x1A, 0x09,
            0x50, 0x6F, 0x6C, 0x61, 0x72, 0x20, 0x56, 0x61, 0x6E,
            0x74, 0x61, 0x67, 0x65, 0x20, 0x4D, 0x33, 0x20, 0x30,
            0x30, 0x30, 0x34, 0x42, 0x46, 0x33, 0x44)

        // Act
        val result = BleUtils.advertisementBytes2Map(adv)

        // Assert
        assertEquals(4, result.size)
        assertTrue(result.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_FLAGS))
        assertTrue(result.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE))
        assertTrue(result.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE))
        assertTrue(result.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC))
    }

    @Test
    fun advertisementBytes2Map_withUnknownLargeAdType_returnsGapAdTypeUnknownWithoutCrash() {
        // Arrange – AD type byte 0x7F (127) is beyond the known enum entries and should
        // not cause an IndexOutOfBoundsException; it must be mapped to GAP_ADTYPE_UNKNOWN.
        val unknownAdType: Byte = 0x7F.toByte()  // value beyond last known AD_TYPE entry
        val adv = byteArrayOf(
            0x02, unknownAdType, 0x01  // length=2, type=0x7F, one data byte
        )

        // Act
        val result = BleUtils.advertisementBytes2Map(adv)

        // Assert
        assertTrue(result.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_UNKNOWN))
    }

}