package com.polar.androidcommunications.api.ble.model.gatt.client.psftp

import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC77_PFTP_SERVICE
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import protocol.PftpRequest
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.system.measureTimeMillis

internal class BlePsFtpClientTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @MockK
    lateinit var mockGattTxInterface: BleGattTxInterface

    private lateinit var blePsFtpClient: BlePsFtpClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        blePsFtpClient = BlePsFtpClient(mockGattTxInterface)
        every { mockGattTxInterface.isConnected() } returns true
        blePsFtpClient.interruptDrainQuietMs = 20L
        blePsFtpClient.interruptDrainMaxMs = 200L
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test psftp client request`() = runTest {
        // Arrange
        val transmitService = slot<UUID>()
        val transmitCharacteristics = slot<UUID>()
        val responsePayload: Byte = 0x22

        every { mockGattTxInterface.transmitMessages(capture(transmitService), capture(transmitCharacteristics), any(), any()) } answers {
            // only after the request() has sent data to device, simulate the device responses
            blePsFtpClient.processServiceDataWritten(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
            val validDeviceResponse = byteArrayOf(0x02, responsePayload)
            blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, validDeviceResponse, 0, true)
        }
        every { mockGattTxInterface.gattClientRequestStopScanning() } returns Unit
        every { mockGattTxInterface.gattClientResumeScanning() } returns Unit

        val randomRequestData = byteArrayOf(0x01.toByte(), 0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(), 0x7F.toByte())

        // Enable the PsFTP
        blePsFtpClient.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

        // Act
        val result = blePsFtpClient.request(randomRequestData)

        // Assert
        verify(exactly = 1) { mockGattTxInterface.gattClientRequestStopScanning() }
        verify(exactly = 1) { mockGattTxInterface.gattClientResumeScanning() }
        assertTrue(RFC77_PFTP_SERVICE.equals(transmitService.captured))
        assertTrue(RFC77_PFTP_MTU_CHARACTERISTIC.equals(transmitCharacteristics.captured))
        assertEquals(responsePayload, result.toByteArray()[0])
    }

    @Test
    fun `request preserves typed PFTP errors for missing directories and other failures`() = runBlocking {
        every { mockGattTxInterface.gattClientRequestStopScanning() } just runs
        every { mockGattTxInterface.gattClientResumeScanning() } just runs
        blePsFtpClient.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

        // Cleanup checks a directory after REMOVE has already deleted it. Error 103 must
        // remain typed so callers can distinguish that expected case from real failures.
        for (errorCode in listOf(103, 201, 250)) {
            every { mockGattTxInterface.transmitMessages(any(), any(), any(), any()) } answers {
                repeat(thirdArg<List<ByteArray>>().size) {
                    blePsFtpClient.processServiceDataWritten(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
                }
                val response = byteArrayOf(0x00, errorCode.toByte(), (errorCode shr 8).toByte())
                blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, response, 0, true)
            }
            val request = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/U/0/20261020/R/114132/")
                .build().toByteArray()

            val error = Assert.assertThrows(BlePsFtpUtils.PftpResponseError::class.java) {
                runBlocking { blePsFtpClient.request(request) }
            }
            assertEquals(errorCode, error.error)
            assertTrue(error.message!!.contains(errorCode.toString()))
            error.errorName?.let { assertTrue(error.message!!.contains(it)) }
        }
        verify(exactly = 3) { mockGattTxInterface.gattClientResumeScanning() }
    }

    @Test
    fun `test psftp client read response`() = runTest {
        // Arrange
        val frame0 = byteArrayOf(0x06.toByte(), 0x0A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x02.toByte(), 0x10.toByte(), 0x04.toByte(), 0x18.toByte(), 0x03.toByte(), 0x12.toByte(), 0x06.toByte(), 0x08.toByte(), 0x00.toByte(), 0x10.toByte(), 0x09.toByte(), 0x18.toByte(), 0x05.toByte(), 0x1A.toByte(), 0x06.toByte(), 0x08.toByte())
        val frame1 = byteArrayOf(0x17.toByte(), 0x02.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x07.toByte(), 0x32.toByte(), 0x08.toByte(), 0x41.toByte(), 0x31.toByte(), 0x34.toByte(), 0x37.toByte(), 0x38.toByte(), 0x43.toByte(), 0x32.toByte(), 0x43.toByte(), 0x3A.toByte(), 0x0E.toByte(), 0x50.toByte(), 0x6F.toByte())
        val frame2 = byteArrayOf(0x27.toByte(), 0x50.toByte(), 0x6F.toByte(), 0x6C.toByte(), 0x61.toByte(), 0x72.toByte(), 0x20.toByte(), 0x49.toByte(), 0x4E.toByte(), 0x57.toByte(), 0x33.toByte(), 0x4E.toByte(), 0x5F.toByte(), 0x42.toByte(), 0x0B.toByte(), 0x30.toByte(), 0x30.toByte(), 0x37.toByte(), 0x38.toByte(), 0x35.toByte())
        val frame3 = byteArrayOf(0x37.toByte(), 0x30.toByte(), 0x30.toByte(), 0x37.toByte(), 0x38.toByte(), 0x35.toByte(), 0x36.toByte(), 0x4A.toByte(), 0x06.toByte(), 0x43.toByte(), 0x6F.toByte(), 0x70.toByte(), 0x70.toByte(), 0x65.toByte(), 0x72.toByte(), 0x52.toByte(), 0x06.toByte(), 0x55.toByte(), 0x6E.toByte(), 0x69.toByte())
        val frame4 = byteArrayOf(0x47.toByte(), 0x55.toByte(), 0x6E.toByte(), 0x69.toByte(), 0x5A.toByte(), 0x10.toByte(), 0x41.toByte(), 0x30.toByte(), 0x39.toByte(), 0x45.toByte(), 0x31.toByte(), 0x41.toByte(), 0x46.toByte(), 0x46.toByte(), 0x46.toByte(), 0x45.toByte(), 0x41.toByte(), 0x31.toByte(), 0x34.toByte(), 0x37.toByte())
        val frame5 = byteArrayOf(0x57.toByte(), 0x41.toByte(), 0x30.toByte(), 0x62.toByte(), 0x14.toByte(), 0xC1.toByte(), 0xA8.toByte(), 0x78.toByte(), 0xD5.toByte(), 0x02.toByte(), 0x4B.toByte(), 0x46.toByte(), 0x1D.toByte(), 0xC6.toByte(), 0x6D.toByte(), 0x38.toByte(), 0xED.toByte(), 0x0E.toByte(), 0x53.toByte(), 0xB5.toByte())
        val frame6 = byteArrayOf(0x67.toByte(), 0xC1.toByte(), 0xA8.toByte(), 0x78.toByte(), 0xD5.toByte(), 0x02.toByte(), 0x6A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x03.toByte(), 0x10.toByte(), 0x0B.toByte(), 0x18.toByte(), 0x00.toByte(), 0x72.toByte(), 0x10.toByte(), 0x0A.toByte(), 0x06.toByte(), 0x42.toByte(), 0x6C.toByte())
        val frame7 = byteArrayOf(0x77.toByte(), 0x42.toByte(), 0x6C.toByte(), 0x65.toByte(), 0x41.toByte(), 0x1A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x09.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x00.toByte(), 0x72.toByte(), 0x17.toByte(), 0x0A.toByte(), 0x0D.toByte(), 0x42.toByte(), 0x6C.toByte(), 0x65.toByte())
        val frame8 = byteArrayOf(0x87.toByte(), 0x42.toByte(), 0x6C.toByte(), 0x65.toByte(), 0x42.toByte(), 0x6F.toByte(), 0x6F.toByte(), 0x74.toByte(), 0x6C.toByte(), 0x6F.toByte(), 0x61.toByte(), 0x1A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x04.toByte(), 0x10.toByte(), 0x01.toByte(), 0x18.toByte(), 0x00.toByte(), 0x72.toByte())
        val frame9 = byteArrayOf(0x97.toByte(), 0x22.toByte(), 0x0A.toByte(), 0x03.toByte(), 0x47.toByte(), 0x50.toByte(), 0x53.toByte(), 0x1A.toByte(), 0x1B.toByte(), 0x08.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x00.toByte(), 0x22.toByte(), 0x13.toByte(), 0x61.toByte(), 0x32.toByte(), 0x30.toByte())
        val frame10 = byteArrayOf(0xA7.toByte(), 0x61.toByte(), 0x32.toByte(), 0x30.toByte(), 0x30.toByte(), 0x32.toByte(), 0x30.toByte(), 0x5F.toByte(), 0x66.toByte(), 0x34.toByte(), 0x64.toByte(), 0x33.toByte(), 0x38.toByte(), 0x36.toByte(), 0x38.toByte(), 0x5F.toByte(), 0x31.toByte(), 0x78.toByte(), 0x01.toByte(), 0x82.toByte())
        val frame11 = byteArrayOf(0xB7.toByte(), 0x82.toByte(), 0x08.toByte(), 0x0A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x03.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x02.toByte(), 0x8A.toByte(), 0x01.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x09.toByte(), 0x5A.toByte(), 0x48.toByte(), 0x5F.toByte(), 0x4A.toByte())
        val frame12 = byteArrayOf(0xC3.toByte(), 0x5A.toByte(), 0x48.toByte(), 0x5F.toByte(), 0x4A.toByte(), 0x41.toByte(), 0x10.toByte(), 0x09.toByte())
        val output = ByteArrayOutputStream()
        val timeoutMillis = 90_000L

        // Act
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame0, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame1, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame2, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame3, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame4, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame5, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame6, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame7, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame8, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame9, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame10, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame11, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame12, 0, true)
        blePsFtpClient.suspendReadResponse(output, timeoutMillis, CompletableDeferred())

        // Assert
        val expectedArray = (frame0.drop(1) +
                frame1.drop(1) +
                frame2.drop(1) +
                frame3.drop(1) +
                frame4.drop(1) +
                frame5.drop(1) +
                frame6.drop(1) +
                frame7.drop(1) +
                frame8.drop(1) +
                frame9.drop(1) +
                frame10.drop(1) +
                frame11.drop(1) +
                frame12.drop(1)).toByteArray()

        Assert.assertArrayEquals(expectedArray, output.toByteArray())
    }

    @Test
    fun `write propagates exception when device disconnects before response`() {
        // Arrange — MTU enabled, transmitMessage succeeds, but device is disconnected when
        // suspendReadResponse checks isConnected(), so BleDisconnected is thrown.
        val header = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
            .setPath("/test.bin")
            .build().toByteArray()

        every { mockGattTxInterface.isConnected() } returns false
        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } just runs
        every { mockGattTxInterface.gattClientRequestStopScanning() } just runs
        every { mockGattTxInterface.gattClientResumeScanning() } just runs

        blePsFtpClient.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

        // Act & Assert
        Assert.assertThrows(BleDisconnected::class.java) {
            runBlocking { blePsFtpClient.write(header, null).collect {} }
        }
    }

    @Test
    fun `write propagates exception thrown during packet transmission`() {
        // Arrange — transmitMessage itself throws; the exception must propagate from write().
        val header = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
            .setPath("/test.bin")
            .build().toByteArray()

        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } throws RuntimeException("transmit failed")
        every { mockGattTxInterface.gattClientRequestStopScanning() } just runs
        every { mockGattTxInterface.gattClientResumeScanning() } just runs

        blePsFtpClient.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

        // Act & Assert
        Assert.assertThrows(RuntimeException::class.java) {
            runBlocking { blePsFtpClient.write(header, null).collect {} }
        }
    }

    @Test
    fun `suspendReadResponse throws when disconnect signal is already completed exceptionally`() {
        // Directly verify that a pre-fired disconnect signal causes suspendReadResponse to throw
        // rather than hanging until the 90-second timeout.
        val output = ByteArrayOutputStream()
        val disconnectSignal = CompletableDeferred<Unit>()
        disconnectSignal.completeExceptionally(BleDisconnected("Device disconnected"))

        Assert.assertThrows(Exception::class.java) {
            runBlocking { blePsFtpClient.suspendReadResponse(output, 90_000L, disconnectSignal) }
        }
    }

    @Test
    fun `request unblocks immediately when reset() is called while waiting for device response`() {
        // Arrange — transmitMessages fires but no device response follows.
        // reset() (simulating BLE disconnect) must unblock request() immediately.
        val transmitLatch = java.util.concurrent.CountDownLatch(1)
        every { mockGattTxInterface.gattClientRequestStopScanning() } just runs
        every { mockGattTxInterface.gattClientResumeScanning() } just runs
        every { mockGattTxInterface.transmitMessages(any(), any(), any(), any()) } answers {
            blePsFtpClient.processServiceDataWritten(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
            transmitLatch.countDown()
        }
        blePsFtpClient.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

        val result = java.util.concurrent.atomic.AtomicReference<Result<*>>()
        val thread = Thread {
            result.set(runCatching { runBlocking { blePsFtpClient.request(byteArrayOf(0x01)) } })
        }
        thread.start()

        assertTrue("Timed out waiting for transmit", transmitLatch.await(5, java.util.concurrent.TimeUnit.SECONDS))
        Thread.sleep(20) // let request() reach the select inside suspendReadResponse
        blePsFtpClient.reset()

        thread.join(5000)
        Assert.assertFalse("request() should have returned after reset()", thread.isAlive)
        Assert.assertNotNull("request() should have thrown after reset()", result.get()?.exceptionOrNull())
    }

    @Test
    fun `waitNotificationEnabled cancellation does not wait for notification timeout`() = runBlocking {
        val job = CoroutineScope(Dispatchers.Default).launch {
            blePsFtpClient.waitNotificationEnabled(
                RFC77_PFTP_MTU_CHARACTERISTIC,
                checkConnection = true,
                timeoutMs = BleGattBase.NOTIFICATION_ENABLE_TIMEOUT_MS
            )
        }
        delay(200)

        val elapsedMillis = kotlin.system.measureTimeMillis { job.cancelAndJoin() }

        assertTrue(
            "Notification-enable cancellation should not wait for the timeout, took ${elapsedMillis}ms",
            elapsedMillis < 5_000L
        )
    }

    @Test
    fun `waitPsFtpClientReady cancellation does not wait for notification timeout`() = runBlocking {
        val job = CoroutineScope(Dispatchers.Default).launch {
            blePsFtpClient.waitPsFtpClientReady(checkConnection = true)
        }
        delay(200)

        val elapsedMillis = kotlin.system.measureTimeMillis { job.cancelAndJoin() }

        assertTrue(
            "PFTP readiness cancellation should not wait for the notification timeout, took ${elapsedMillis}ms",
            elapsedMillis < 5_000L
        )
    }

    private val interruptFrame = byteArrayOf(0x00, 0x00, 0x00)

    private fun cancelRequestSetup(
        sent: MutableList<Pair<UUID, List<ByteArray>>>,
        ackInterrupt: Boolean,
        requestSent: CompletableDeferred<Unit>
    ) {
        every { mockGattTxInterface.gattClientRequestStopScanning() } just runs
        every { mockGattTxInterface.gattClientResumeScanning() } just runs
        every { mockGattTxInterface.transmitMessages(any(), any(), any(), any()) } answers {
            val frames = thirdArg<List<ByteArray>>().map { it.copyOf() }
            sent.add(Pair(secondArg<UUID>(), frames))
            val isInterrupt = frames.size == 1 && frames[0].contentEquals(interruptFrame)
            if (!isInterrupt) {
                repeat(frames.size) { blePsFtpClient.processServiceDataWritten(RFC77_PFTP_MTU_CHARACTERISTIC, 0) }
                requestSent.complete(Unit)
            } else if (ackInterrupt) {
                blePsFtpClient.processServiceDataWrittenWithResponse(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
            }
        }
        blePsFtpClient.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)
    }

    @Test
    fun request_sendsInterruptFrame_whenCancelledAwaitingResponse() = runBlocking {
        // Arrange
        val sent = Collections.synchronizedList(mutableListOf<Pair<UUID, List<ByteArray>>>())
        val requestSent = CompletableDeferred<Unit>()
        cancelRequestSetup(sent, ackInterrupt = true, requestSent)

        // Act
        val job = launch(Dispatchers.Default) { blePsFtpClient.request(byteArrayOf(0x01, 0x02)) }
        requestSent.await()
        job.cancelAndJoin()

        // Assert
        assertEquals(2, sent.size)
        assertEquals(RFC77_PFTP_MTU_CHARACTERISTIC, sent[1].first)
        assertEquals(1, sent[1].second.size)
        assertTrue(sent[1].second[0].contentEquals(interruptFrame))
    }

    private fun ackAllWrites(
        sent: MutableList<ByteArray>,
        onInterrupt: () -> Unit = {},
        onRequest: (Int) -> Unit = {}
    ) {
        var operations = 0
        every { mockGattTxInterface.gattClientRequestStopScanning() } just runs
        every { mockGattTxInterface.gattClientResumeScanning() } just runs
        every { mockGattTxInterface.transmitMessages(any(), any(), any(), any()) } answers {
            val frames = thirdArg<List<ByteArray>>()
            frames.forEach { sent.add(it.copyOf()) }
            if (frames.size == 1 && frames[0].contentEquals(interruptFrame)) {
                blePsFtpClient.processServiceDataWrittenWithResponse(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
                onInterrupt()
            } else {
                repeat(frames.size) { blePsFtpClient.processServiceDataWritten(RFC77_PFTP_MTU_CHARACTERISTIC, 0) }
                onRequest(++operations)
            }
        }
        blePsFtpClient.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)
    }

    @Test
    fun query_returnsResponse_whenStaleFrameOfInterruptedRequestArrivesAfterInterruptAck() = runBlocking {
        // Arrange
        val sent = Collections.synchronizedList(mutableListOf<ByteArray>())
        val bg = CoroutineScope(Dispatchers.Default)
        val firstSent = CompletableDeferred<Unit>()
        blePsFtpClient.interruptDrainQuietMs = 250L
        ackAllWrites(
            sent,
            onInterrupt = {
                // device sent this frame before it processed the interrupt
                bg.launch {
                    delay(50)
                    blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, byteArrayOf(0x27, 0x55), 0, true)
                }
            },
            onRequest = { n ->
                if (n == 1) firstSent.complete(Unit)
                if (n == 2) bg.launch {
                    delay(150)
                    blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, byteArrayOf(0x00, 0x00, 0x00), 0, true)
                }
            }
        )
        val cancelled = bg.launch { blePsFtpClient.request(byteArrayOf(0x01, 0x02)) }
        firstSent.await()
        cancelled.cancelAndJoin()

        // Act
        val result = runCatching { blePsFtpClient.query(1, null) }

        // Assert
        assertEquals(null, result.exceptionOrNull()?.toString())
        assertEquals(1, sent.count { it.contentEquals(interruptFrame) })
        bg.cancel()
    }

    @Test
    fun request_endsInterruptDrain_whenDeviceSendsFramesLongerThanDrainMaximum() = runBlocking {
        // Arrange
        val bg = CoroutineScope(Dispatchers.Default)
        val firstSent = CompletableDeferred<Unit>()
        blePsFtpClient.interruptDrainQuietMs = 100L
        blePsFtpClient.interruptDrainMaxMs = 300L
        val sender = bg.launch(start = CoroutineStart.LAZY) {
            while (isActive) {
                blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, byteArrayOf(0x27, 0x55), 0, true)
                delay(20)
            }
        }
        ackAllWrites(
            mutableListOf(),
            onInterrupt = { sender.start() },
            onRequest = { firstSent.complete(Unit) }
        )
        val cancelled = bg.launch { blePsFtpClient.request(byteArrayOf(0x01, 0x02)) }
        firstSent.await()

        // Act
        val elapsed = measureTimeMillis { cancelled.cancelAndJoin() }
        sender.cancelAndJoin()

        // Assert
        assertTrue("took ${elapsed}ms", elapsed in 300L..1500L)
        bg.cancel()
    }

    @Test
    fun request_endsInterruptDrain_whenDisconnectedDuringDrain() = runBlocking {
        // Arrange
        val bg = CoroutineScope(Dispatchers.Default)
        val firstSent = CompletableDeferred<Unit>()
        blePsFtpClient.interruptDrainQuietMs = 2000L
        blePsFtpClient.interruptDrainMaxMs = 5000L
        ackAllWrites(
            mutableListOf(),
            onInterrupt = { bg.launch { delay(100); blePsFtpClient.reset() } },
            onRequest = { firstSent.complete(Unit) }
        )
        val cancelled = bg.launch { blePsFtpClient.request(byteArrayOf(0x01, 0x02)) }
        firstSent.await()

        // Act
        val elapsed = measureTimeMillis { cancelled.cancelAndJoin() }

        // Assert
        assertTrue("took ${elapsed}ms", elapsed < 1500L)
        bg.cancel()
    }

    @Test
    fun request_finishesInterruptCleanup_whenAcknowledgementNeverArrives() = runBlocking {
        // Arrange
        val sent = Collections.synchronizedList(mutableListOf<Pair<UUID, List<ByteArray>>>())
        val requestSent = CompletableDeferred<Unit>()
        blePsFtpClient.interruptTimeoutMs = 100L
        cancelRequestSetup(sent, ackInterrupt = false, requestSent)

        // Act
        val job = launch(Dispatchers.Default) { blePsFtpClient.request(byteArrayOf(0x01, 0x02)) }
        requestSent.await()
        job.cancelAndJoin()

        // Assert
        assertEquals(2, sent.size)
    }

    @Test
    fun write_waitsForOwnAck_whenLateInterruptAckArrives() = runBlocking {
        // Arrange
        val sent = Collections.synchronizedList(mutableListOf<Pair<UUID, List<ByteArray>>>())
        val writePackets = Collections.synchronizedList(mutableListOf<ByteArray>())
        val requestSent = CompletableDeferred<Unit>()
        val firstWriteSent = CompletableDeferred<Unit>()
        val secondWriteSent = CompletableDeferred<Unit>()
        blePsFtpClient.interruptTimeoutMs = 100L
        cancelRequestSetup(sent, ackInterrupt = false, requestSent)
        blePsFtpClient.setPacketsCount(1)
        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } answers {
            writePackets.add(thirdArg<ByteArray>().copyOf())
            if (writePackets.size == 1) firstWriteSent.complete(Unit)
            if (writePackets.size == 2) secondWriteSent.complete(Unit)
        }
        val cancelled = launch(Dispatchers.Default) { blePsFtpClient.request(byteArrayOf(0x01, 0x02)) }
        requestSent.await()
        cancelled.cancelAndJoin()
        val header = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.PUT).setPath("/a.bin").build().toByteArray()
        val writeJob = launch(Dispatchers.Default) {
            blePsFtpClient.write(header, java.io.ByteArrayInputStream(ByteArray(2000))).collect { }
        }
        firstWriteSent.await()

        // Act
        blePsFtpClient.processServiceDataWrittenWithResponse(RFC77_PFTP_MTU_CHARACTERISTIC, 0)

        // Assert
        Assert.assertNull(withTimeoutOrNull(100L) { secondWriteSent.await() })
        assertEquals(1, writePackets.size)

        // Act
        blePsFtpClient.processServiceDataWrittenWithResponse(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
        secondWriteSent.await()

        // Assert
        assertEquals(2, writePackets.size)
        every { mockGattTxInterface.isConnected() } returns false
        writeJob.cancelAndJoin()
    }

    @Test
    fun request_doesNotSendInterrupt_whenCancelledWhileDisconnected() = runBlocking {
        // Arrange
        val sent = Collections.synchronizedList(mutableListOf<Pair<UUID, List<ByteArray>>>())
        val requestSent = CompletableDeferred<Unit>()
        cancelRequestSetup(sent, ackInterrupt = true, requestSent)

        // Act
        val job = launch(Dispatchers.Default) { blePsFtpClient.request(byteArrayOf(0x01, 0x02)) }
        requestSent.await()
        every { mockGattTxInterface.isConnected() } returns false
        job.cancelAndJoin()

        // Assert
        assertEquals(1, sent.size)
    }

    @Test
    fun request_doesNotSendInterrupt_whenCancelledBehindActiveRequest() = runBlocking {
        // Arrange
        val sent = Collections.synchronizedList(mutableListOf<Pair<UUID, List<ByteArray>>>())
        val requestSent = CompletableDeferred<Unit>()
        val queuedReady = CompletableDeferred<Unit>()
        cancelRequestSetup(sent, ackInterrupt = true, requestSent)

        // Act
        val active = launch(Dispatchers.Default) { blePsFtpClient.request(byteArrayOf(0x01, 0x02)) }
        requestSent.await()
        every { mockGattTxInterface.gattClientRequestStopScanning() } answers {
            queuedReady.complete(Unit)
            Unit
        }
        val queued = launch(Dispatchers.Default) { blePsFtpClient.request(byteArrayOf(0x03, 0x04)) }
        queuedReady.await()
        queued.cancelAndJoin()

        // Assert
        assertEquals(1, sent.size)
        active.cancelAndJoin()
    }

    @Test
    fun write_waitsForInterruptAck_whenCancelledWithOutstandingPacketAck() = runBlocking {
        // Arrange
        val transmitted = Collections.synchronizedList(mutableListOf<ByteArray>())
        val firstWriteSent = CompletableDeferred<Unit>()
        val interruptSent = CompletableDeferred<Unit>()
        val querySent = CompletableDeferred<Unit>()
        every { mockGattTxInterface.gattClientRequestStopScanning() } just runs
        every { mockGattTxInterface.gattClientResumeScanning() } just runs
        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } answers {
            transmitted.add(thirdArg<ByteArray>().copyOf())
            firstWriteSent.complete(Unit)
        }
        every { mockGattTxInterface.transmitMessages(any(), any(), any(), any()) } answers {
            val packets = thirdArg<List<ByteArray>>()
            packets.forEach { transmitted.add(it.copyOf()) }
            if (packets.single().contentEquals(interruptFrame)) interruptSent.complete(Unit)
            else querySent.complete(Unit)
        }
        blePsFtpClient.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)
        blePsFtpClient.setPacketsCount(1)
        blePsFtpClient.interruptTimeoutMs = 2_000L

        val header = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.PUT).setPath("/a.bin").build().toByteArray()
        val data = java.io.ByteArrayInputStream(ByteArray(2000))

        // Act
        val writeJob = launch(Dispatchers.Default) { blePsFtpClient.write(header, data).collect { } }
        firstWriteSent.await()
        val next = launch(Dispatchers.Default) { blePsFtpClient.query(1, null) }
        writeJob.cancel()
        interruptSent.await()
        assertTrue(transmitted.last().contentEquals(interruptFrame))
        val countAfterInterrupt = transmitted.size

        blePsFtpClient.processServiceDataWrittenWithResponse(RFC77_PFTP_MTU_CHARACTERISTIC, 0)

        // Assert
        assertEquals(countAfterInterrupt, transmitted.size)
        Assert.assertFalse(querySent.isCompleted)

        blePsFtpClient.processServiceDataWrittenWithResponse(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
        querySent.await()
        assertTrue(transmitted.size > countAfterInterrupt)
        every { mockGattTxInterface.isConnected() } returns false
        next.cancelAndJoin()
    }
}