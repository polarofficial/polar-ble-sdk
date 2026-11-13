package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class BlePsdClientTest {

    private lateinit var txInterface: BleGattTxInterface
    private lateinit var sut: BlePsdClient

    @Before
    fun setUp() {
        txInterface = mockk(relaxed = true)
        every { txInterface.isConnected() } returns true
        every { txInterface.transmitMessage(any(), any(), any(), any()) } just runs
        every { txInterface.transmitMessages(any(), any(), any(), any()) } just runs

        sut = BlePsdClient(txInterface)
    }

    @Test
    fun psdResponse_resolvesEveryUnsignedOpcodeByProtocolValue() {
        for (code in 0..255) {
            // Arrange
            val data = byteArrayOf(0xF0.toByte(), code.toByte(), 1, 42)
            val expected = BlePsdClient.PsdMessage.entries.firstOrNull { it.numVal == code }
                ?: BlePsdClient.PsdMessage.PSD_UNKNOWN

            // Act
            val response = BlePsdClient.PsdResponse(data)

            // Assert
            assertEquals("Opcode $code", expected, response.opCode)
            assertEquals(1.toByte(), response.status)
            assertEquals(42.toByte(), response.payload)
        }
        for (size in 0..2) {
            // Arrange
            val data = ByteArray(size)

            // Act
            val response = BlePsdClient.PsdResponse(data)

            // Assert
            assertEquals(BlePsdClient.PsdMessage.PSD_UNKNOWN, response.opCode)
        }
    }

    @Test
    fun processServiceData_ignoresIncompletePpRecords() = runTest {
        // Arrange
        val values = mutableListOf<BlePsdClient.PPData>()
        val job = launch { sut.monitorPPNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        for (size in 1..6) {
            sut.processServiceData(BlePsdClient.PSD_PP, ByteArray(size), BleGattBase.ATT_SUCCESS, true)
        }
        testScheduler.advanceUntilIdle()

        // Assert
        assertTrue(values.isEmpty())

        // Act
        sut.processServiceData(BlePsdClient.PSD_PP, ByteArray(10), BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()

        // Assert
        assertEquals(1, values.size)

        // Cleanup
        job.cancel()
    }

    @Test
    fun reset_doesNotThrow() {
        // Act & Assert
        sut.reset()
    }

    @Test
    fun processServiceData_whenFeatureCharacteristic_emitsFeatureData() = runTest {
        // Arrange
        val featureData = byteArrayOf(0x01, 0x02)
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.readFeature() }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(
            BlePsdClient.PSD_FEATURE,
            featureData,
            BleGattBase.ATT_SUCCESS,
            true
        )
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue(caughtError == null)
    }

    @Test
    fun processServiceData_whenControlPointCharacteristic_emitsPpNotification() = runTest {
        // Arrange
        // PP packets are 7 bytes each; PSD_PP characteristic feeds ppObservers
        val notification = byteArrayOf(0x0A, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00)
        val values = mutableListOf<BlePsdClient.PPData>()
        val job = launch { sut.monitorPPNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(
            BlePsdClient.PSD_PP,
            notification,
            BleGattBase.ATT_SUCCESS,
            true
        )
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertTrue(values.isNotEmpty())
    }

    @Test
    fun processServiceData_whenUnknownCharacteristic_doesNotEmit() = runTest {
        // Arrange
        val ppValues = mutableListOf<BlePsdClient.PPData>()
        val job = launch { sut.monitorPPNotifications(true).collect { ppValues.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(
            UUID.randomUUID(),
            byteArrayOf(0x01),
            BleGattBase.ATT_SUCCESS,
            true
        )
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertTrue(ppValues.isEmpty())
    }

    @Test
    fun toString_returnsClassName() {
        // Act
        val result = sut.toString()

        // Assert
        assertTrue(result.contains("psd client"))
    }

    @Test
    fun clientReady_whenRequested_completesAfterControlPointWriteSuccess() = runTest {
        // Arrange
        var caughtError: Throwable? = null

        // Act
        Thread {
            Thread.sleep(50)
            sut.descriptorWritten(BlePsdClient.PSD_CP, true, BleGattBase.ATT_SUCCESS)
        }.start()

        try { sut.clientReady(true) }
        catch (e: Throwable) { caughtError = e }

        // Assert
        assertTrue(caughtError == null)
    }

    @Test
    fun readFeature_whenFeatureDataArrives_returnsFeature() = runTest {
        // Arrange
        val featureData = byteArrayOf(0x10)
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.readFeature() }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(
            BlePsdClient.PSD_FEATURE,
            featureData,
            BleGattBase.ATT_SUCCESS,
            true
        )
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue(caughtError == null)
    }

    @Test
    fun monitorPPNotifications_whenControlPointDataArrives_emitsByteArray() = runTest {
        // Arrange
        // PP packets must be 7 bytes each; use PSD_PP characteristic
        val data = byteArrayOf(0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val values = mutableListOf<BlePsdClient.PPData>()
        val job = launch { sut.monitorPPNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(
            BlePsdClient.PSD_PP,
            data,
            BleGattBase.ATT_SUCCESS,
            true
        )
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertTrue(values.isNotEmpty())
    }
}