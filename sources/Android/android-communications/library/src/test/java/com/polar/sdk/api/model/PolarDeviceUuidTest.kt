package com.polar.sdk.api.model

import junit.framework.TestCase.assertEquals
import org.junit.Test

class PolarDeviceUuidTest {

    @Test
    fun `fromDeviceId() should generate Polar device UUID String`() {
        // Arrange
        val deviceId = "89643A20"
        val expectedUuid = "0e030000-0084-0000-0000-000089643A20"

        // Act
        val generatedUuid = PolarDeviceUuid.fromDeviceId(deviceId)

        // Assert
        assertEquals(expectedUuid, generatedUuid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromDeviceId() should throw exception if device ID is invalid`() {
        // Arrange
        val invalidDeviceId = "123456789"

        // Act & Assert
        PolarDeviceUuid.fromDeviceId(invalidDeviceId)
    }
}