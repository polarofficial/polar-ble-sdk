package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import io.mockk.MockKAnnotations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlePmdClientControlPointResponseTest {

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `success control point response for acc stream settings`() {
        //Arrange
        // HEX: F0 01 02 00 00 FF FF FF
        // index    type                                               data:
        // 0:       Response code                          size 1:     0xF0
        val expectedResponseCode = 0xF0.toByte()
        // 1:       Op code                                size 1:     0x01 (Request stream settings)
        val expectedOpCode = PmdControlPointCommandClientToService.GET_MEASUREMENT_SETTINGS
        // 2:       Measurement Type                       size 1:     0x02 (Acc)
        val expectedMeasurementType = 0x02.toByte()
        // 3:       Error Code                             size 1:     0x00 (Success)
        val expectedStatus =
            PmdControlPointResponse.PmdControlPointResponseCode.SUCCESS
        // 4:       More                                   size 1:     0x00 (No more)
        val expectedMore = false
        // 5..n:    Parameters                             size 3:     0xFF 0xFF 0xFF (some data)
        val expectedParamsSize = 3
        val expectedParamsContent = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

        val cpResponse = byteArrayOf(
            0xF0.toByte(),
            0x01.toByte(),
            0x02.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte()
        )

        //Act
        val response = PmdControlPointResponse(cpResponse)

        //Assert
        assertEquals(expectedResponseCode, response.responseCode)
        assertEquals(expectedOpCode, response.opCode)
        assertEquals(expectedMeasurementType, response.measurementType)
        assertEquals(expectedStatus, response.status)
        assertEquals(expectedMore, response.more)
        assertEquals(expectedParamsSize, response.parameters.size)
        assertTrue(expectedParamsContent.contentEquals(response.parameters))
    }

    @Test
    fun `failing control point response for mag stream settings`() {
        //Arrange
        // HEX: F0 01 06 00 00
        // index    type                                               data:
        // 0:       Response code                          size 1:     0xF0
        val expectedResponseCode = 0xF0.toByte()
        // 1:       Op code                                size 1:     0x01 (Request stream settings)
        val expectedOpCode = PmdControlPointCommandClientToService.GET_MEASUREMENT_SETTINGS
        // 2:       Measurement Type                       size 1:     0x06 (mag)
        val expectedMeasurementType = 0x06.toByte()
        // 3:       Error Code                             size 1:     0x07 (Failure)
        val expectedStatus =
            PmdControlPointResponse.PmdControlPointResponseCode.ERROR_INVALID_RESOLUTION
        // 4:       More                                   size 1:     0x00 (No more)
        val expectedMore = false
        // 5..n:    Parameters                             size 3:     0xFF 0xFF 0xFF (some data)
        val expectedParamsSize = 0
        val expectedParamsContent = byteArrayOf()

        val cpResponse =
            byteArrayOf(0xF0.toByte(), 0x01.toByte(), 0x06.toByte(), 0x07.toByte(), 0x00.toByte())

        //Act
        val response = PmdControlPointResponse(cpResponse)

        //Assert
        assertEquals(expectedResponseCode, response.responseCode)
        assertEquals(expectedOpCode, response.opCode)
        assertEquals(expectedMeasurementType, response.measurementType)
        assertEquals(expectedStatus, response.status)
        assertEquals(expectedMore, response.more)
        assertEquals(expectedParamsSize, response.parameters.size)
        assertTrue(expectedParamsContent.contentEquals(response.parameters))
    }
}