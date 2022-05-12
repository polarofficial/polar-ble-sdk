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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.TimeUnit

class BlePsFtpClientTest {
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
}