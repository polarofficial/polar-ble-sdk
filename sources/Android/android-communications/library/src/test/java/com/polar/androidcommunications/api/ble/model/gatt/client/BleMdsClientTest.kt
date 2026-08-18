package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class BleMdsClientTest {

    @get:Rule
    val bleLoggerTestRule = BleLoggerTestRule()

    private lateinit var txInterface: BleGattTxInterface
    private lateinit var sut: BleMdsClient

    @Before
    fun setUp() {
        txInterface = mockk(relaxed = true)
        every { txInterface.isConnected() } returns true
        every { txInterface.transmitMessages(any(), any(), any(), any()) } just runs
        sut = BleMdsClient(txInterface)
    }

    // --- reset() ---

    @Test
    fun reset_clearsDeviceContext() {
        // Arrange
        sut.processServiceData(BleMdsClient.MDS_DEVICE_IDENTIFIER, "device-001".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        sut.processServiceData(BleMdsClient.MDS_DATA_URI, "https://nrf-chunks.memfault.com/api/v0/chunks/".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        sut.processServiceData(BleMdsClient.MDS_AUTHORIZATION, "token123".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        sut.processServiceData(BleMdsClient.MDS_SUPPORTED_FEATURES, byteArrayOf(0x03), BleGattBase.ATT_SUCCESS, false)

        // Act
        sut.reset()

        // Assert
        assertNull(sut.deviceIdentifier)
        assertNull(sut.dataUri)
        assertNull(sut.authorization)
        assertNull(sut.supportedFeatures)
    }

    @Test
    fun reset_clearsResponseQueue() {
        // Arrange
        sut.mdsResponseQueue.add(Pair(byteArrayOf(0x01), BleGattBase.ATT_SUCCESS))

        // Act
        sut.reset()

        // Assert
        assertTrue(sut.mdsResponseQueue.isEmpty())
    }

    @Test
    fun reset_resetsSequenceCounterSoDuplicateDetectionRestarts() = runTest {
        // Arrange: send a notification to set lastSeqCounter
        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(true).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, byteArrayOf(0x05, 0xAA.toByte()), BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Act: reset and then collect again
        sut.reset()
        val emittedAfterReset = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job2 = launch { sut.monitorMdsNotifications(true).collect { emittedAfterReset.add(it) } }
        testScheduler.advanceUntilIdle()

        // Sending same seq=5 should be accepted again (seq counter was reset to -1)
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, byteArrayOf(0x05, 0xBB.toByte()), BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job2.cancel()

        // Assert
        assertEquals(1, emittedAfterReset.size)
    }

    // --- processServiceData: MDS_SUPPORTED_FEATURES ---

    @Test
    fun processServiceData_supportedFeatures_attSuccess_setsSingleByteFeaturesCorrectly() {
        // Arrange
        val data = byteArrayOf(0x07)

        // Act
        sut.processServiceData(BleMdsClient.MDS_SUPPORTED_FEATURES, data, BleGattBase.ATT_SUCCESS, false)

        // Assert: single byte little-endian value
        assertEquals(0x07, sut.supportedFeatures)
    }

    @Test
    fun processServiceData_supportedFeatures_attSuccess_parsesMultiByteLittleEndian() {
        // Arrange: [0x01, 0x02] little-endian => 0x0201 = 513
        val data = byteArrayOf(0x01, 0x02)

        // Act
        sut.processServiceData(BleMdsClient.MDS_SUPPORTED_FEATURES, data, BleGattBase.ATT_SUCCESS, false)

        // Assert
        assertEquals(0x0201, sut.supportedFeatures)
    }

    @Test
    fun processServiceData_supportedFeatures_attError_doesNotSetFeatures() {
        // Act
        sut.processServiceData(BleMdsClient.MDS_SUPPORTED_FEATURES, byteArrayOf(0x01), BleGattBase.ATT_READ_NOT_PERMITTED, false)

        // Assert
        assertNull(sut.supportedFeatures)
    }

    @Test
    fun clientReady_waitsForSupportedFeaturesAfterNotificationEnabled() = runTest {
        // Arrange
        sut.descriptorWritten(BleMdsClient.MDS_DATA_EXPORT, true, BleGattBase.ATT_SUCCESS)
        var caughtError: Throwable? = null
        val job = launch {
            try {
                sut.clientReady(true)
            } catch (error: Throwable) {
                caughtError = error
            }
        }
        testScheduler.advanceUntilIdle()

        // Act
        assertFalse(job.isCompleted)
        sut.processServiceData(
            BleMdsClient.MDS_SUPPORTED_FEATURES,
            byteArrayOf(0x01),
            BleGattBase.ATT_SUCCESS,
            false
        )
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertNull(caughtError)
    }

    @Test
    fun clientReady_supportedFeaturesReadError_throwsBleAttributeError() = runTest {
        // Arrange
        sut.descriptorWritten(BleMdsClient.MDS_DATA_EXPORT, true, BleGattBase.ATT_SUCCESS)
        var caughtError: Throwable? = null
        val job = launch {
            try {
                sut.clientReady(true)
            } catch (error: Throwable) {
                caughtError = error
            }
        }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(
            BleMdsClient.MDS_SUPPORTED_FEATURES,
            byteArrayOf(),
            BleGattBase.ATT_READ_NOT_PERMITTED,
            false
        )
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue(caughtError is BleAttributeError)
        assertEquals(BleGattBase.ATT_READ_NOT_PERMITTED, (caughtError as BleAttributeError).error)
    }

    // --- processServiceData: MDS_DEVICE_IDENTIFIER ---

    @Test
    fun processServiceData_deviceIdentifier_attSuccess_setsDeviceIdentifier() {
        // Arrange
        val deviceId = "polarH10-001"

        // Act
        sut.processServiceData(BleMdsClient.MDS_DEVICE_IDENTIFIER, deviceId.toByteArray(Charsets.UTF_8), BleGattBase.ATT_SUCCESS, false)

        // Assert
        assertEquals(deviceId, sut.deviceIdentifier)
    }

    @Test
    fun processServiceData_deviceIdentifier_attError_doesNotChangeDeviceIdentifier() {
        // Arrange: set a valid identifier first
        sut.processServiceData(BleMdsClient.MDS_DEVICE_IDENTIFIER, "original-id".toByteArray(), BleGattBase.ATT_SUCCESS, false)

        // Act
        sut.processServiceData(BleMdsClient.MDS_DEVICE_IDENTIFIER, "other-id".toByteArray(), BleGattBase.ATT_READ_NOT_PERMITTED, false)

        // Assert: identifier unchanged
        assertEquals("original-id", sut.deviceIdentifier)
    }

    @Test
    fun processServiceData_deviceIdentifier_attError_leavesIdentifierNull() {
        // Act
        sut.processServiceData(BleMdsClient.MDS_DEVICE_IDENTIFIER, "id".toByteArray(), BleGattBase.ATT_READ_NOT_PERMITTED, false)

        // Assert
        assertNull(sut.deviceIdentifier)
    }

    // --- processServiceData: MDS_DATA_URI ---

    @Test
    fun processServiceData_dataUri_attSuccess_setsDataUri() {
        // Arrange
        val uri = "https://nrf-chunks.memfault.com/api/v0/chunks/"

        // Act
        sut.processServiceData(BleMdsClient.MDS_DATA_URI, uri.toByteArray(Charsets.UTF_8), BleGattBase.ATT_SUCCESS, false)

        // Assert
        assertEquals(uri, sut.dataUri)
    }

    @Test
    fun processServiceData_dataUri_attError_doesNotSetDataUri() {
        // Act
        sut.processServiceData(BleMdsClient.MDS_DATA_URI, "uri".toByteArray(), BleGattBase.ATT_READ_NOT_PERMITTED, false)

        // Assert
        assertNull(sut.dataUri)
    }

    // --- processServiceData: MDS_AUTHORIZATION ---

    @Test
    fun processServiceData_authorization_attSuccess_setsAuthorization() {
        // Arrange
        val auth = "Memfault-Project-Key:PROJECT_KEY_123"

        // Act
        sut.processServiceData(BleMdsClient.MDS_AUTHORIZATION, auth.toByteArray(Charsets.UTF_8), BleGattBase.ATT_SUCCESS, false)

        // Assert
        assertEquals(auth, sut.authorization)
    }

    @Test
    fun processServiceData_authorization_attError_doesNotSetAuthorization() {
        // Act
        sut.processServiceData(BleMdsClient.MDS_AUTHORIZATION, "auth".toByteArray(), BleGattBase.ATT_READ_NOT_PERMITTED, false)

        // Assert
        assertNull(sut.authorization)
    }

    // --- processServiceData: MDS_DATA_EXPORT ---

    @Test
    fun processServiceData_dataExport_notifying_doesNotAddToResponseQueue() {
        // Arrange
        val data = byteArrayOf(0x05, 0x01, 0x02, 0x03)

        // Act
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data, BleGattBase.ATT_SUCCESS, true)

        // Assert
        assertTrue(sut.mdsResponseQueue.isEmpty())
    }

    @Test
    fun processServiceData_dataExport_notifying_emptyData_doesNotAddToQueueOrEmitTelemetry() = runTest {
        // Arrange
        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(true).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, byteArrayOf(), BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertTrue(emitted.isEmpty())
        assertTrue(sut.mdsResponseQueue.isEmpty())
    }

    @Test
    fun processServiceData_dataExport_notifying_emitsCorrectTelemetryWithContext() = runTest {
        // Arrange: populate device context first
        sut.processServiceData(BleMdsClient.MDS_DEVICE_IDENTIFIER, "device-001".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        sut.processServiceData(BleMdsClient.MDS_DATA_URI, "https://nrf-chunks.memfault.com/api/v0/chunks/".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        sut.processServiceData(BleMdsClient.MDS_AUTHORIZATION, "Memfault-Project-Key:key".toByteArray(), BleGattBase.ATT_SUCCESS, false)

        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(true).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        // seq=5 in bits[0-4], payload = rest of bytes
        val data = byteArrayOf(0x05, 0x01, 0x02)

        // Act
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(1, emitted.size)
        val telemetry = emitted[0]
        assertEquals("device-001", telemetry.deviceIdentifier)
        assertEquals("https://nrf-chunks.memfault.com/api/v0/chunks/", telemetry.dataUri)
        assertEquals("Memfault-Project-Key:key", telemetry.authorization)
        assertArrayEquals(byteArrayOf(0x01, 0x02), telemetry.exportData)
    }

    @Test
    fun processServiceData_dataExport_notifying_payloadStripsFirstByte() = runTest {
        // Arrange
        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(true).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        // First byte is the sequence+flags header; payload starts at index 1
        val data = byteArrayOf(0x01, 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())

        // Act
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert: exportData excludes the first byte
        assertArrayEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()), emitted[0].exportData)
    }

    @Test
    fun processServiceData_dataExport_notifying_duplicateSeqCounter_dropsSecond() = runTest {
        // Arrange
        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(true).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        val data = byteArrayOf(0x0A, 0x01, 0x02) // seq = 10

        // Act: send twice with same seq counter
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data, BleGattBase.ATT_SUCCESS, true)
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert: second (duplicate) notification was dropped
        assertEquals(1, emitted.size)
    }

    @Test
    fun processServiceData_dataExport_notifying_sequenceWraparound_bothAccepted() = runTest {
        // Arrange
        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(true).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        // seq=31 (0x1F & 0x1F), then seq=0 (0x00 & 0x1F) — consecutive wraparound, 0 dropped
        val data31 = byteArrayOf(0x1F.toByte(), 0xAA.toByte())
        val data0 = byteArrayOf(0x00.toByte(), 0xBB.toByte())

        // Act
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data31, BleGattBase.ATT_SUCCESS, true)
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, data0, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert: both accepted (no missed chunks across the 31→0 boundary)
        assertEquals(2, emitted.size)
    }

    @Test
    fun processServiceData_dataExport_notifyingFalse_doesNotEmitAndDoesNotAddToQueue() = runTest {
        // Arrange
        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(true).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act: notifying=false
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, byteArrayOf(0x01, 0x02), BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertTrue(emitted.isEmpty())
        assertTrue(sut.mdsResponseQueue.isEmpty())
    }

    @Test
    fun processServiceData_unknownCharacteristic_doesNotAffectAnyState() = runTest {
        // Arrange
        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(true).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(UUID.randomUUID(), byteArrayOf(0x01, 0x02), BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert: no state changes
        assertNull(sut.deviceIdentifier)
        assertNull(sut.dataUri)
        assertNull(sut.authorization)
        assertNull(sut.supportedFeatures)
        assertTrue(emitted.isEmpty())
        assertTrue(sut.mdsResponseQueue.isEmpty())
    }

    // --- processServiceDataWritten ---

    @Test
    fun processServiceDataWritten_dataExportWithError_addsEmptyPayloadAndErrorStatusToResponseQueue() {
        // Act
        sut.processServiceDataWritten(BleMdsClient.MDS_DATA_EXPORT, BleGattBase.ATT_WRITE_NOT_PERMITTED)

        // Assert
        assertEquals(1, sut.mdsResponseQueue.size)
        val item = sut.mdsResponseQueue.peek()!!
        assertTrue(item.first.isEmpty())
        assertEquals(BleGattBase.ATT_WRITE_NOT_PERMITTED, item.second)
    }

    @Test
    fun processServiceDataWritten_dataExportSuccess_addsToResponseQueue() {
        // Act
        sut.processServiceDataWritten(BleMdsClient.MDS_DATA_EXPORT, BleGattBase.ATT_SUCCESS)

        // Assert
        assertEquals(1, sut.mdsResponseQueue.size)
        val item = sut.mdsResponseQueue.peek()!!
        assertTrue(item.first.isEmpty())
        assertEquals(BleGattBase.ATT_SUCCESS, item.second)
    }

    @Test
    fun processServiceDataWritten_otherCharacteristic_doesNotAddToResponseQueue() {
        // Act
        sut.processServiceDataWritten(BleMdsClient.MDS_DEVICE_IDENTIFIER, BleGattBase.ATT_WRITE_NOT_PERMITTED)

        // Assert
        assertTrue(sut.mdsResponseQueue.isEmpty())
    }

    // --- sendControlPointCommand ---

    @Test
    fun sendControlPointCommand_attSuccess_returnsZero() {
        // Arrange
        respondToDataExportWrite(BleGattBase.ATT_SUCCESS)

        // Act
        val result = sut.sendControlPointCommand(0x01)

        // Assert
        assertEquals(0, result)
    }

    @Test
    fun sendControlPointCommand_transmitsCommandToDataExport() {
        // Arrange
        respondToDataExportWrite(BleGattBase.ATT_SUCCESS)

        // Act
        sut.sendControlPointCommand(0x42)

        // Assert
        verify {
            txInterface.transmitMessages(
                BleMdsClient.MDS_SERVICE,
                BleMdsClient.MDS_DATA_EXPORT,
                match { packets -> packets.size == 1 && packets[0].contentEquals(byteArrayOf(0x42)) },
                true
            )
        }
    }

    @Test
    fun sendControlPointCommand_attError_throwsBleAttributeErrorWithStatus() {
        // Arrange
        respondToDataExportWrite(BleGattBase.ATT_WRITE_NOT_PERMITTED)

        // Act & Assert
        val ex = assertThrows(BleAttributeError::class.java) {
            sut.sendControlPointCommand(0x02)
        }
        assertEquals(BleGattBase.ATT_WRITE_NOT_PERMITTED, ex.error)
    }

    @Test
    fun startMdsNotifications_successfulWriteWithoutChunkReturnsFlow() = runTest {
        // Arrange
        respondToDataExportWrite(BleGattBase.ATT_SUCCESS)

        // Act
        val flow = sut.startMdsNotifications(true)
        val job = launch { flow.collect {} }
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        verify(exactly = 1) {
            txInterface.transmitMessages(
                BleMdsClient.MDS_SERVICE,
                BleMdsClient.MDS_DATA_EXPORT,
                match { packets -> packets.single().contentEquals(byteArrayOf(0x01)) },
                true
            )
        }
    }

    @Test
    fun startMdsNotifications_registersObserverBeforeEnablingDataExport() = runTest {
        // Arrange
        val notification = byteArrayOf(0x05, 0x11, 0x22)
        every {
            txInterface.transmitMessages(
                BleMdsClient.MDS_SERVICE,
                BleMdsClient.MDS_DATA_EXPORT,
                any(),
                true
            )
        } answers {
            sut.processServiceData(
                BleMdsClient.MDS_DATA_EXPORT,
                notification,
                BleGattBase.ATT_SUCCESS,
                true
            )
            sut.processServiceDataWritten(BleMdsClient.MDS_DATA_EXPORT, BleGattBase.ATT_SUCCESS)
        }

        // Act
        val emitted = sut.startMdsNotifications(true).take(1).toList()

        // Assert
        assertEquals(1, emitted.size)
        assertArrayEquals(byteArrayOf(0x11, 0x22), emitted.single().exportData)
    }

    // --- monitorMdsNotifications ---

    @Test
    fun monitorMdsNotifications_multipleNotifications_emitsAll() = runTest {
        // Arrange
        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(true).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act: three notifications with distinct seq counters
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, byteArrayOf(0x01, 0xAA.toByte()), BleGattBase.ATT_SUCCESS, true)
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, byteArrayOf(0x02, 0xBB.toByte()), BleGattBase.ATT_SUCCESS, true)
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, byteArrayOf(0x03, 0xCC.toByte()), BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(3, emitted.size)
        assertArrayEquals(byteArrayOf(0xAA.toByte()), emitted[0].exportData)
        assertArrayEquals(byteArrayOf(0xBB.toByte()), emitted[1].exportData)
        assertArrayEquals(byteArrayOf(0xCC.toByte()), emitted[2].exportData)
    }

    @Test
    fun monitorMdsNotifications_disconnected_throwsBleDisconnected() = runTest {
        // Arrange
        every { txInterface.isConnected() } returns false
        var caughtError: Throwable? = null

        // Act
        try {
            sut.monitorMdsNotifications(checkConnection = true).collect {}
        } catch (e: Throwable) {
            caughtError = e
        }

        // Assert
        assertTrue(caughtError is BleDisconnected)
    }

    @Test
    fun monitorMdsNotifications_checkConnectionFalse_emitsEvenWhenDisconnected() = runTest {
        // Arrange: disconnected but checkConnection=false
        every { txInterface.isConnected() } returns false
        val emitted = mutableListOf<BleMdsClient.TelemetryConfiguration>()
        val job = launch { sut.monitorMdsNotifications(checkConnection = false).collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        // Act
        sut.processServiceData(BleMdsClient.MDS_DATA_EXPORT, byteArrayOf(0x01, 0x55), BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert: subscription was established despite not being connected
        assertEquals(1, emitted.size)
    }

    // --- TelemetryConfiguration equals / hashCode ---

    @Test
    fun telemetryConfiguration_equals_identicalContent_returnsTrue() {
        val a = BleMdsClient.TelemetryConfiguration("id", "uri", "auth", byteArrayOf(0x01, 0x02))
        val b = BleMdsClient.TelemetryConfiguration("id", "uri", "auth", byteArrayOf(0x01, 0x02))
        assertEquals(a, b)
    }

    @Test
    fun telemetryConfiguration_equals_differentExportData_returnsFalse() {
        val a = BleMdsClient.TelemetryConfiguration("id", "uri", "auth", byteArrayOf(0x01))
        val b = BleMdsClient.TelemetryConfiguration("id", "uri", "auth", byteArrayOf(0x02))
        assertNotEquals(a, b)
    }

    @Test
    fun telemetryConfiguration_equals_differentDeviceIdentifier_returnsFalse() {
        val a = BleMdsClient.TelemetryConfiguration("id-A", "uri", "auth", byteArrayOf(0x01))
        val b = BleMdsClient.TelemetryConfiguration("id-B", "uri", "auth", byteArrayOf(0x01))
        assertNotEquals(a, b)
    }

    @Test
    fun telemetryConfiguration_equals_nullFields_returnsTrue() {
        val a = BleMdsClient.TelemetryConfiguration(null, null, null, byteArrayOf(0x01))
        val b = BleMdsClient.TelemetryConfiguration(null, null, null, byteArrayOf(0x01))
        assertEquals(a, b)
    }

    @Test
    fun telemetryConfiguration_equals_sameInstance_returnsTrue() {
        val a = BleMdsClient.TelemetryConfiguration("id", "uri", "auth", byteArrayOf(0x01))
        assertEquals(a, a)
    }

    @Test
    fun telemetryConfiguration_equals_differentType_returnsFalse() {
        val a = BleMdsClient.TelemetryConfiguration("id", "uri", "auth", byteArrayOf(0x01))
        assertFalse(a.equals("not a TelemetryConfiguration"))
    }

    @Test
    fun telemetryConfiguration_hashCode_sameContent_sameHash() {
        val a = BleMdsClient.TelemetryConfiguration("id", "uri", "auth", byteArrayOf(0x01, 0x02))
        val b = BleMdsClient.TelemetryConfiguration("id", "uri", "auth", byteArrayOf(0x01, 0x02))
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun telemetryConfiguration_hashCode_nullFields_doesNotThrow() {
        val a = BleMdsClient.TelemetryConfiguration(null, null, null, byteArrayOf())
        // Assert no exception; hashCode must return consistently
        assertEquals(a.hashCode(), a.hashCode())
    }

    private fun respondToDataExportWrite(status: Int) {
        every {
            txInterface.transmitMessages(
                BleMdsClient.MDS_SERVICE,
                BleMdsClient.MDS_DATA_EXPORT,
                any(),
                true
            )
        } answers {
            sut.processServiceDataWritten(BleMdsClient.MDS_DATA_EXPORT, status)
        }
    }
}