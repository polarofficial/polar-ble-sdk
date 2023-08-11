package com.polar.androidcommunications.api.ble.model.gatt.client.psftp

import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC77_PFTP_SERVICE
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.reactivex.rxjava3.schedulers.TestScheduler
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.TimeUnit

internal class BlePsFtpClientTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @MockK
    lateinit var mockGattTxInterface: BleGattTxInterface

    private lateinit var blePsFtpClient: BlePsFtpClient
    private lateinit var testScheduler: TestScheduler

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        blePsFtpClient = BlePsFtpClient(mockGattTxInterface)
        every { mockGattTxInterface.isConnected } returns true
        testScheduler = TestScheduler()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test psftp client request`() {
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
        val result = blePsFtpClient.request(randomRequestData, testScheduler).toFlowable()
        val testObserver = TestSubscriber<ByteArrayOutputStream>()
        result.subscribe(testObserver)
        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        //Assert
        verify(exactly = 1) { mockGattTxInterface.gattClientRequestStopScanning() }
        verify(exactly = 1) { mockGattTxInterface.gattClientResumeScanning() }

        assertTrue(RFC77_PFTP_SERVICE.equals(transmitService.captured))
        assertTrue(RFC77_PFTP_MTU_CHARACTERISTIC.equals(transmitCharacteristics.captured))

        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        testObserver.assertComplete()
        val emittedValue = testObserver.values()[0].toByteArray()[0]
        assertEquals(responsePayload, emittedValue)
    }

    @Test
    fun `test psftp client read response`() {
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
        val timeoutSeconds = 90L

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
        blePsFtpClient.readResponse(output, timeoutSeconds)

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
}