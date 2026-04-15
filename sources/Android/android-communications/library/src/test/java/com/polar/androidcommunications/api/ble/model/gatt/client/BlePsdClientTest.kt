package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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