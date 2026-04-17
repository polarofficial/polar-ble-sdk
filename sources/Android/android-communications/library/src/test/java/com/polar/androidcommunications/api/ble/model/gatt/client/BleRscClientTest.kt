package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class BleRscClientTest {

    private lateinit var txInterface: BleGattTxInterface
    private lateinit var sut: BleRscClient

    @Before
    fun setUp() {
        mockkObject(ChannelUtils.Companion)
        every { ChannelUtils.postDisconnectedAndClearList(any<AtomicSet<Channel<Any>>>()) } answers {
            @Suppress("UNCHECKED_CAST")
            val list = firstArg<AtomicSet<Channel<Any>>>()
            val error = BleDisconnected()
            list.objects().forEach { it.close(error) }
            list.clear()
        }
        txInterface = mockk(relaxed = true)
        every { txInterface.isConnected() } returns true
        sut = BleRscClient(txInterface)
    }

    @Test
    fun reset_postsDisconnectedToNotificationObservers() = runTest {
        // Arrange
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.monitorRscNotifications().collect { } }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()

        // Act
        sut.reset()
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue(caughtError is BleDisconnected)
    }

    @Test
    fun processServiceData_whenMeasurementWithStrideAndDistance_emitsParsedNotification() = runTest {
        // Arrange
        val values = mutableListOf<BleRscClient.RscNotificationData>()
        val job = launch { sut.monitorRscNotifications().collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val data = byteArrayOf(
            0x07,
            0x34, 0x12,
            0x56,
            0x89.toByte(), 0x07,
            0x04, 0x03, 0x02, 0x01
        )

        // Act
        sut.processServiceData(BleRscClient.RSC_MEASUREMENT, data, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(1, values.size)
        val n = values[0]
        assertTrue(n.strideLengthPresent)
        assertTrue(n.totalDistancePresent)
        assertTrue(n.running)
        assertEquals(4660, n.speed.toInt())
        assertEquals(86, n.cadence.toInt())
        assertEquals(1929, n.strideLength.toInt())
        assertEquals(16909060, n.totalDistance.toInt())
    }

    @Test
    fun processServiceData_whenMeasurementWithoutOptionalFields_emitsParsedNotificationWithZeros() = runTest {
        // Arrange
        val values = mutableListOf<BleRscClient.RscNotificationData>()
        val job = launch { sut.monitorRscNotifications().collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val data = byteArrayOf(0x00, 0x0A, 0x00, 0x14)

        // Act
        sut.processServiceData(BleRscClient.RSC_MEASUREMENT, data, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(1, values.size)
        val n = values[0]
        assertTrue(!n.strideLengthPresent)
        assertTrue(!n.totalDistancePresent)
        assertTrue(!n.running)
        assertEquals(10L, n.speed)
        assertEquals(20L, n.cadence)
        assertEquals(0L, n.strideLength)
        assertEquals(0L, n.totalDistance)
    }

    @Test
    fun processServiceData_whenFeatureRead_success_doesNotEmitRscNotification() = runTest {
        // Arrange
        val values = mutableListOf<BleRscClient.RscNotificationData>()
        val job = launch { sut.monitorRscNotifications().collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(BleRscClient.RSC_FEATURE, byteArrayOf(0x01, 0x00), BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertTrue(values.isEmpty())
    }

    @Test
    fun processServiceData_whenStatusNotSuccess_doesNotEmitNotification() = runTest {
        // Arrange
        val values = mutableListOf<BleRscClient.RscNotificationData>()
        val job = launch { sut.monitorRscNotifications().collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(BleRscClient.RSC_MEASUREMENT, byteArrayOf(0x00, 0x01, 0x00, 0x02), BleGattBase.ATT_INVALID_HANDLE, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertTrue(values.isEmpty())
    }

    @Test
    fun processServiceData_whenDataNull_doesNotEmitNotification() = runTest {
        // Arrange
        val values = mutableListOf<BleRscClient.RscNotificationData>()
        val job = launch { sut.monitorRscNotifications().collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(BleRscClient.RSC_MEASUREMENT, byteArrayOf(), BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertTrue(values.isEmpty())
    }

    @Test
    fun processServiceData_whenUnknownCharacteristic_doesNotEmitNotification() = runTest {
        // Arrange
        val values = mutableListOf<BleRscClient.RscNotificationData>()
        val job = launch { sut.monitorRscNotifications().collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(UUID.randomUUID(), byteArrayOf(0x00, 0x01, 0x00, 0x02), BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertTrue(values.isEmpty())
    }

    @Test
    fun processServiceDataWritten_doesNothingAndDoesNotThrow() {
        sut.processServiceDataWritten(BleRscClient.RSC_MEASUREMENT, BleGattBase.ATT_SUCCESS)
        assertTrue(true)
    }

    @Test
    fun toString_returnsExpectedValue() {
        assertEquals("RSC service ", sut.toString())
    }

    @Test
    fun clientReady_completesAfterNotificationEnabled() = runTest {
        // Arrange
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.clientReady(true) }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()

        // Act — simulate descriptor write success enabling the notification
        sut.descriptorWritten(BleRscClient.RSC_MEASUREMENT, true, BleGattBase.ATT_SUCCESS)
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue(caughtError == null)
    }

    @Test
    fun monitorRscNotifications_emitsMeasurementEvents() = runTest {
        // Arrange
        val values = mutableListOf<BleRscClient.RscNotificationData>()
        val job = launch { sut.monitorRscNotifications().collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val data = byteArrayOf(0x00, 0x01, 0x00, 0x02)

        // Act
        sut.processServiceData(BleRscClient.RSC_MEASUREMENT, data, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(1, values.size)
    }
}
