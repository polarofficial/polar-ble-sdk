package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.BleMdsClient
import com.polar.sdk.api.DeviceTelemetryConfiguration
import com.polar.sdk.api.PolarDeviceTelemetryType
import com.polar.sdk.impl.utils.PolarTelemetryMemfaultUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolarTelemetryMemfaultUtilsTest {

    @Test
    fun `getDeviceTelemetryConfiguration returns correct configuration when all fields are set`() {
        // Arrange
        val client = mockk<BleMdsClient>()
        every { client.deviceIdentifier } returns "DEVICE123"
        every { client.dataUri } returns "https://nrf-chunks.memfault.com/api/v0/chunks/DEVICE123"
        every { client.authorization } returns "Memfault-Project-Key:ABC"
        every { client.supportedFeatures } returns 0x01

        // Act
        val result = PolarTelemetryMemfaultUtils.getDeviceTelemetryConfiguration(client)

        // Assert
        assertEquals(
            DeviceTelemetryConfiguration(
                deviceIdentifier = "DEVICE123",
                dataUri = "https://nrf-chunks.memfault.com/api/v0/chunks/DEVICE123",
                authorization = "Memfault-Project-Key:ABC",
                supportedFeatures = 0x01
            ),
            result
        )
    }

    @Test
    fun `getDeviceTelemetryConfiguration returns empty string for dataUri when client dataUri is null`() {
        // Arrange
        val client = mockk<BleMdsClient>()
        every { client.deviceIdentifier } returns "DEVICE123"
        every { client.dataUri } returns null
        every { client.authorization } returns "token"
        every { client.supportedFeatures } returns null

        // Act
        val result = PolarTelemetryMemfaultUtils.getDeviceTelemetryConfiguration(client)

        // Assert
        assertEquals("", result.dataUri)
    }

    @Test
    fun `getDeviceTelemetryConfiguration returns empty string for authorization when client authorization is null`() {
        // Arrange
        val client = mockk<BleMdsClient>()
        every { client.deviceIdentifier } returns "DEVICE123"
        every { client.dataUri } returns "https://nrf-chunks.memfault.com/api/v0/chunks/DEVICE123"
        every { client.authorization } returns null
        every { client.supportedFeatures } returns null

        // Act
        val result = PolarTelemetryMemfaultUtils.getDeviceTelemetryConfiguration(client)

        // Assert
        assertEquals("", result.authorization)
    }

    @Test
    fun `getDeviceTelemetryConfiguration returns null deviceIdentifier when client deviceIdentifier is null`() {
        // Arrange
        val client = mockk<BleMdsClient>()
        every { client.deviceIdentifier } returns null
        every { client.dataUri } returns null
        every { client.authorization } returns null
        every { client.supportedFeatures } returns null

        // Act
        val result = PolarTelemetryMemfaultUtils.getDeviceTelemetryConfiguration(client)

        // Assert
        assertEquals(null, result.deviceIdentifier)
    }

    // endregion

    // region stopTelemetryStreaming

    @Test
    fun `stopTelemetryStreaming returns true when sendControlPointCommand returns 0`() {
        // Arrange
        val client = mockk<BleMdsClient>()
        every { client.sendControlPointCommand(0) } returns 0

        // Act
        val result = PolarTelemetryMemfaultUtils.stopTelemetryStreaming(client)

        // Assert
        assertTrue(result)
        verify(exactly = 1) { client.sendControlPointCommand(0) }
    }

    @Test
    fun `stopTelemetryStreaming returns false when sendControlPointCommand returns non-zero`() {
        // Arrange
        val client = mockk<BleMdsClient>()
        every { client.sendControlPointCommand(0) } returns 1

        // Act
        val result = PolarTelemetryMemfaultUtils.stopTelemetryStreaming(client)

        // Assert
        assertFalse(result)
    }

    // endregion

    // region startTelemetryStreaming

    @Test
    fun `startTelemetryStreaming emits DeviceTelemetryEvent with memfault_mds type and exportData as payload`() = runTest {
        // Arrange
        val exportData = byteArrayOf(0x01, 0x02, 0x03)
        val telemetryConfig = BleMdsClient.TelemetryConfiguration(
            deviceIdentifier = "DEVICE123",
            dataUri = "https://nrf-chunks.memfault.com/api/v0/chunks/DEVICE123",
            authorization = "token",
            exportData = exportData
        )
        val client = mockk<BleMdsClient>()
        every { client.startMdsNotifications(true) } returns flowOf(telemetryConfig)

        // Act
        val results = PolarTelemetryMemfaultUtils.startTelemetryStreaming(client).toList()

        // Assert
        assertEquals(1, results.size)
        assertEquals(PolarDeviceTelemetryType.memfault_mds, results[0].type)
        assertTrue(exportData.contentEquals(results[0].payload))
    }

    @Test
    fun `startTelemetryStreaming starts MDS notifications`() = runTest {
        // Arrange
        val client = mockk<BleMdsClient>()
        every { client.startMdsNotifications(true) } returns flowOf()

        // Act
        PolarTelemetryMemfaultUtils.startTelemetryStreaming(client).toList()

        // Assert
        verify(exactly = 1) { client.startMdsNotifications(true) }
    }

    @Test
    fun `startTelemetryStreaming emits one event per notification`() = runTest {
        // Arrange
        val client = mockk<BleMdsClient>()
        every { client.startMdsNotifications(true) } returns flowOf(
            BleMdsClient.TelemetryConfiguration(null, null, null, byteArrayOf(0x01)),
            BleMdsClient.TelemetryConfiguration(null, null, null, byteArrayOf(0x02)),
            BleMdsClient.TelemetryConfiguration(null, null, null, byteArrayOf(0x03))
        )

        // Act
        val results = PolarTelemetryMemfaultUtils.startTelemetryStreaming(client).toList()

        // Assert
        assertEquals(3, results.size)
        assertTrue(byteArrayOf(0x01).contentEquals(results[0].payload))
        assertTrue(byteArrayOf(0x02).contentEquals(results[1].payload))
        assertTrue(byteArrayOf(0x03).contentEquals(results[2].payload))
    }

    // endregion

    // region getAvailableTelemetryTypes

    @Test
    fun `getAvailableTelemetryTypes returns empty list when supportedFeatures is null`() {
        // Arrange — null means the MDS client has not yet read supported features from the device
        val client = mockk<BleMdsClient>()
        every { client.supportedFeatures } returns null

        // Act
        val result = PolarTelemetryMemfaultUtils.getAvailableTelemetryTypes(client)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAvailableTelemetryTypes returns memfault_mds when supportedFeatures is non-null`() {
        // Arrange — MDS always returns 0x0 for supportedFeatures; any non-null value indicates MDS is present
        val client = mockk<BleMdsClient>()
        every { client.supportedFeatures } returns 0x0

        // Act
        val result = PolarTelemetryMemfaultUtils.getAvailableTelemetryTypes(client)

        // Assert
        assertEquals(listOf(PolarDeviceTelemetryType.memfault_mds), result)
    }
}