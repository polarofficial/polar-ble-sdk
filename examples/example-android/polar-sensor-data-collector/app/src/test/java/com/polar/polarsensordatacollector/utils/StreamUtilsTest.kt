package com.polar.polarsensordatacollector.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.errors.BleOnlineStreamClosed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamUtilsTest {

    @Test
    fun `is device initiated stop returns true when error is stream closed`() {
        // Arrange
        val error = BleOnlineStreamClosed("closed")

        // Act
        val result = StreamUtils.isDeviceInitiatedStop(error)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `is device initiated stop returns true when stream closed is nested cause`() {
        // Arrange
        val error = RuntimeException("outer", IllegalStateException("middle", BleOnlineStreamClosed("closed")))

        // Act
        val result = StreamUtils.isDeviceInitiatedStop(error)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `is device initiated stop returns false when no stream closed in chain`() {
        // Arrange
        val error = RuntimeException("outer", IllegalStateException("inner"))

        // Act
        val result = StreamUtils.isDeviceInitiatedStop(error)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `calculate sample rate returns zero when any timestamp is zero`() {
        // Act and Assert
        assertEquals(0.0, StreamUtils.calculateSampleRate(0L, 1_000L), 0.0)
        assertEquals(0.0, StreamUtils.calculateSampleRate(1_000L, 0L), 0.0)
    }

    @Test
    fun `calculate sample rate returns zero when delta is not positive`() {
        // Act and Assert
        assertEquals(0.0, StreamUtils.calculateSampleRate(2_000L, 1_000L), 0.0)
        assertEquals(0.0, StreamUtils.calculateSampleRate(1_000L, 1_000L), 0.0)
    }

    @Test
    fun `calculate sample rate returns hertz when delta is positive`() {
        // Arrange
        val delta = 10_000_000L // 10 ms in nanoseconds

        // Act
        val rate = StreamUtils.calculateSampleRate(1_000_000_000L, 1_000_000_000L + delta)

        // Assert
        assertEquals(100.0, rate, 0.0001)
    }

    @Test
    fun `calculate sample delta uses next packet when no previous packet`() {
        // Act
        val delta = StreamUtils.calculateSampleDelta(0L, 1_000L, 1_900L, 3, 9)

        // Assert
        assertEquals(100L, delta)
    }

    @Test
    fun `calculate sample delta uses previous packet when previous packet exists`() {
        // Act
        val delta = StreamUtils.calculateSampleDelta(400L, 1_000L, 1_900L, 3, 9)

        // Assert
        assertEquals(200L, delta)
    }

    @Test
    fun `estimate first sample time stamp subtracts all but last sample deltas`() {
        // Act
        val first = StreamUtils.estimateFirstSampleTimeStamp(delta = 10L, packetTimeStamp = 1_000L, packetSize = 5)

        // Assert
        assertEquals(960L, first)
    }

    @Test
    fun `estimate first sample time stamp returns packet time stamp when packet has one sample`() {
        // Act
        val first = StreamUtils.estimateFirstSampleTimeStamp(delta = 10L, packetTimeStamp = 1_000L, packetSize = 1)

        // Assert
        assertEquals(1_000L, first)
    }
}
