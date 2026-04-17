package com.polar.androidcommunications.api.ble.model.gatt.client.pfc

import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class BlePfcClientTest {

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @MockK
    lateinit var mockGattTxInterface: BleGattTxInterface

    private lateinit var blePfcClient: BlePfcClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        blePfcClient = BlePfcClient(mockGattTxInterface)
        every { mockGattTxInterface.isConnected() } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `PfcFeature parses security mode supported bit correctly when enabled`() {
        // Arrange
        // Bit 9 is security mode supported (bit 1 of byte[1])
        // byte[0] = 0x00, byte[1] = 0x02 (bit 1 set)
        val featureData = byteArrayOf(0x00.toByte(), 0x02.toByte())

        // Act
        val pfcFeature = BlePfcClient.PfcFeature(featureData)

        // Assert
        Assert.assertTrue(pfcFeature.securityModeSupported)
    }

    @Test
    fun `PfcFeature parses security mode supported bit correctly when disabled`() {
        // Arrange
        // byte[0] = 0x00, byte[1] = 0x00 (no bits set)
        val featureData = byteArrayOf(0x00.toByte(), 0x00.toByte())

        // Act
        val pfcFeature = BlePfcClient.PfcFeature(featureData)

        // Assert
        Assert.assertFalse(pfcFeature.securityModeSupported)
    }

    @Test
    fun `PfcFeature parses all features correctly`() {
        // Arrange
        // byte[0] = 0xFF (all lower 8 bits set)
        // byte[1] = 0x03 (ANT+ bit 0 and security mode bit 1 set)
        val featureData = byteArrayOf(0xFF.toByte(), 0x03.toByte())

        // Act
        val pfcFeature = BlePfcClient.PfcFeature(featureData)

        // Assert
        Assert.assertTrue(pfcFeature.broadcastSupported)
        Assert.assertTrue(pfcFeature.khzSupported)
        Assert.assertTrue(pfcFeature.otaUpdateSupported)
        Assert.assertTrue(pfcFeature.whisperModeSupported)
        Assert.assertTrue(pfcFeature.bleModeConfigureSupported)
        Assert.assertTrue(pfcFeature.multiConnectionSupported)
        Assert.assertTrue(pfcFeature.antSupported)
        Assert.assertTrue(pfcFeature.securityModeSupported)
    }

    @Test
    fun `PfcResponse parses security mode request response correctly`() {
        // Arrange
        // Response: F0 0C 01 01
        val responseData = byteArrayOf(
            0xF0.toByte(),
            0x0C.toByte(),
            0x01.toByte(),
            0x01.toByte()
        )

        // Act
        val pfcResponse = BlePfcClient.PfcResponse(responseData)

        // Assert
        Assert.assertEquals(BlePfcClient.RESPONSE_CODE, pfcResponse.responseCode)
        Assert.assertEquals(
            BlePfcClient.PfcMessage.PFC_REQUEST_SECURITY_MODE,
            pfcResponse.opCode
        )
        Assert.assertEquals(BlePfcClient.SUCCESS, pfcResponse.status)
        Assert.assertNotNull(pfcResponse.payload)
        Assert.assertEquals(1, pfcResponse.payload?.size ?: 0)
        Assert.assertEquals(0x01.toByte(), pfcResponse.payload!![0])
    }

    @Test
    fun `PfcResponse parses security mode configure response correctly`() {
        // Arrange
        // Response: F0 0E 01
        val responseData = byteArrayOf(
            0xF0.toByte(),
            0x0E.toByte(),
            0x01.toByte()
        )

        // Act
        val pfcResponse = BlePfcClient.PfcResponse(responseData)

        // Assert
        Assert.assertEquals(BlePfcClient.RESPONSE_CODE, pfcResponse.responseCode)
        Assert.assertEquals(
            BlePfcClient.PfcMessage.PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE,
            pfcResponse.opCode
        )
        Assert.assertEquals(BlePfcClient.SUCCESS, pfcResponse.status)
    }

    @Test
    fun `PfcResponse parses security mode not supported error correctly`() {
        // Arrange
        // Response: F0 0C 02 (not supported)
        val responseData = byteArrayOf(
            0xF0.toByte(),
            0x0C.toByte(),
            0x02.toByte()
        )

        // Act
        val pfcResponse = BlePfcClient.PfcResponse(responseData)

        // Assert
        Assert.assertEquals(BlePfcClient.RESPONSE_CODE, pfcResponse.responseCode)
        Assert.assertEquals(
            BlePfcClient.PfcMessage.PFC_REQUEST_SECURITY_MODE,
            pfcResponse.opCode
        )
        Assert.assertEquals(BlePfcClient.ERROR_NOT_SUPPORTED, pfcResponse.status)
    }

    @Test
    fun `PfcResponse parses security mode disabled correctly`() {
        // Arrange
        // Response: F0 0C 01 00 (success, security mode disabled)
        val responseData = byteArrayOf(
            0xF0.toByte(),
            0x0C.toByte(),
            0x01.toByte(),
            0x00.toByte()
        )

        // Act
        val pfcResponse = BlePfcClient.PfcResponse(responseData)

        // Assert
        Assert.assertEquals(BlePfcClient.SUCCESS, pfcResponse.status)
        Assert.assertEquals(0x00.toByte(), pfcResponse.payload!![0])
    }

    @Test
    fun `PfcResponse parses sensor initiated security mode request correctly`() {
        // Arrange
        // Response: F0 0F 01 01
        val responseData = byteArrayOf(
            0xF0.toByte(),
            0x0F.toByte(),
            0x01.toByte(),
            0x01.toByte()
        )

        // Act
        val pfcResponse = BlePfcClient.PfcResponse(responseData)

        // Assert
        Assert.assertEquals(BlePfcClient.RESPONSE_CODE, pfcResponse.responseCode)
        Assert.assertEquals(
            BlePfcClient.PfcMessage.PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE,
            pfcResponse.opCode
        )
        Assert.assertEquals(BlePfcClient.SUCCESS, pfcResponse.status)
        Assert.assertNotNull(pfcResponse.payload)
        Assert.assertEquals(1, pfcResponse.payload!!.size)
        Assert.assertEquals(0x01.toByte(), pfcResponse.payload!![0])
    }
}