package com.polar.androidcommunications.common.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TypeUtilsTest {

    @Test
    fun `test array conversion to unsigned byte max value`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte())
        val expectedValue = 0xFFu.toUByte()

        // Act
        val result = TypeUtils.convertArrayToUnsignedByte(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int max value`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val expectedValue = 0xFFFFFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int min value`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val expectedValue = 0x00000000u

        // Act
        val result = TypeUtils.convertArrayToUnsignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int max positive int`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte())
        val expectedValue = 0x7FFFFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int small array`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val expectedValue = 0xFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int too big array`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00)

        // Act & Assert
        assertThrows(AssertionError::class.java) {
            TypeUtils.convertArrayToUnsignedInt(byteArray)
        }
    }

    @Test
    fun `test array conversion to unsigned long max value`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val expectedValue = 0xFFFFFFFFFFFFFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedLong(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned long min value`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val expectedValue = 0x0000000000000000u.toULong()

        // Act
        val result = TypeUtils.convertArrayToUnsignedLong(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned long max positive int`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte())
        val expectedValue = 0x7FFFFFFFFFFFFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedLong(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned long small array`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val expectedValue = 0xFFFFu.toULong()

        // Act
        val result = TypeUtils.convertArrayToUnsignedLong(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned long too big array`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        // Act & Assert
        assertThrows(AssertionError::class.java) {
            TypeUtils.convertArrayToUnsignedLong(byteArray)
        }
    }

    @Test
    fun `test array conversion to signed int max value`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val expectedValue = -1

        // Act
        val result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to signed int min value`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val expectedValue = 0

        // Act
        val result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to signed int max positive int`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte())
        val expectedValue = Int.MAX_VALUE

        // Act
        val result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to signed int small array`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte())
        val expectedValue = -1

        // Act
        val result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }


    @Test
    fun `test array conversion to signed int too big array`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00)

        // Act & Assert
        assertThrows(AssertionError::class.java) {
            TypeUtils.convertArrayToSignedInt(byteArray)
        }
    }

    @Test
    fun `test conversion unsigned byte to int`() {
        // Arrange
        val testByte1: Byte = 0x00.toByte()
        val testByte2: Byte = 0x80.toByte()
        val testByte3: Byte = 0xFF.toByte()
        val testByte4: Byte = 0x55.toByte()

        // Act & Assert
        assertEquals(0, TypeUtils.convertUnsignedByteToInt(testByte1))
        assertEquals(128, TypeUtils.convertUnsignedByteToInt(testByte2))
        assertEquals(255, TypeUtils.convertUnsignedByteToInt(testByte3))
        assertEquals(85, TypeUtils.convertUnsignedByteToInt(testByte4))

    }
}
