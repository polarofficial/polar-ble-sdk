package com.polar.androidcommunications.api.ble.model.polar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePolarDeviceIdUtilityTests {

    @Test
    fun isValidDeviceId_whenNull_returnsFalse() {
        // Act
        val result = BlePolarDeviceIdUtility.isValidDeviceId(null)

        // Assert
        assertFalse(result)
    }

    @Test
    fun isValidDeviceId_whenAssembledFrom6Digits_returnsTrue() {
        // Arrange
        val assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId("123456")

        // Act
        val result = BlePolarDeviceIdUtility.isValidDeviceId(assembled)

        // Assert
        assertTrue(result)
    }

    @Test
    fun isValidDeviceId_whenAssembledFrom7Digits_returnsTrue() {
        // Arrange
        val assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId("1A2B3C4")

        // Act
        val result = BlePolarDeviceIdUtility.isValidDeviceId(assembled)

        // Assert
        assertTrue(result)
    }

    @Test
    fun isValidDeviceId_whenEightDigitsWithWrongChecksum_returnsFalse() {
        // Arrange
        val valid = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId("1A2B3C4")
        val wrongLastNibble = if (valid.last() == '0') '1' else '0'
        val invalid = valid.dropLast(1) + wrongLastNibble

        // Act
        val result = BlePolarDeviceIdUtility.isValidDeviceId(invalid)

        // Assert
        assertFalse(result)
    }

    @Test(expected = NumberFormatException::class)
    fun isValidDeviceId_whenNonHex_throwsNumberFormatException() {
        // Act
        BlePolarDeviceIdUtility.isValidDeviceId("ZZZZZZ")
    }

    @Test
    fun assemblyFullPolarDeviceId_whenLength6_returnsLength8AndInsertsOneBeforeCrc() {
        // Arrange
        val base = "123456"

        // Act
        val assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(base)

        // Assert
        assertEquals(8, assembled.length)
        assertTrue(assembled.startsWith(base))
        assertEquals('1', assembled[6])
    }

    @Test
    fun assemblyFullPolarDeviceId_whenLength7_returnsLength8AndAppendsCrc() {
        // Arrange
        val base = "1A2B3C4"

        // Act
        val assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(base)

        // Assert
        assertEquals(8, assembled.length)
        assertTrue(assembled.startsWith(base))
    }

    @Test
    fun assemblyFullPolarDeviceId_whenLengthOtherThan6Or7_returnsInputUnchanged() {
        // Arrange
        val input = "ABCDEF12"

        // Act
        val assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(input)

        // Assert
        assertEquals(input, assembled)
    }

    @Test
    fun assemblyFullPolarDeviceId_whenNonHexInput_returnsEmptyString() {
        // Arrange
        val input = "GHIJKL"

        // Act
        val assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(input)

        // Assert
        assertEquals("", assembled)
    }

    @Test
    fun assemblyFullPolarDeviceId_whenAssembled_thenIdIsValid() {
        // Arrange
        val assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId("ABCDEF")

        // Act
        val result = BlePolarDeviceIdUtility.isValidDeviceId(assembled)

        // Assert
        assertTrue(result)
    }
}

