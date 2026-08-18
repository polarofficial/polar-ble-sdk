package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import org.junit.Assert
import org.junit.Test
import java.lang.Float.intBitsToFloat

class PressureDataTest {

    @Test
    fun `process compressed pressure data type 0`() {
        // Arrange
        // HEX: 0B 00 94 35 77 00 00 00 00 80
        // index                                                   data:
        // 0        type                                           0B (Pressure)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     80 (compressed, type 0)
        val pressureDataFrameHeader = byteArrayOf(
            0x0B.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: C2 87 80 44 0A 01 1F BF
        // index    type                                data
        // 0..3     Sample 1  (ref. sample)             C2 87 80 44 (0x448087C2)
        // 4        Delta size                          0A (10 bit)
        // 5        Sample amount                       01 (1 samples)
        // 6..      Delta data                          1F BF
        // Delta sample 1                               11 0001 1111b (- 0xE1)

        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val sample0 = intBitsToFloat(0x448087C2)
        val sample1 = intBitsToFloat(0x448087C2 - 0xE1)

        val pressureDataFrameContent = byteArrayOf(
            0xC2.toByte(), 0x87.toByte(), 0x80.toByte(), 0x44.toByte(),
            0x0A.toByte(), 0x01.toByte(), 0x1F.toByte(), 0xBF.toByte(),
        )
        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = pressureDataFrameHeader + pressureDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val pressureData = PressureData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, pressureData.pressureSamples.size)
        Assert.assertEquals(sample0, pressureData.pressureSamples[0].pressure)
        Assert.assertEquals(sample1, pressureData.pressureSamples[1].pressure)
        Assert.assertEquals(timeStamp, pressureData.pressureSamples[1].timeStamp)
    }

    @Test
    fun `process compressed pressure data type 0 with factor`() {
        // Arrange
        // HEX: 0B 00 94 35 77 00 00 00 00 80
        // index                                                   data:
        // 0        type                                           0B (Pressure)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     80 (compressed, type 0)
        val pressureDataFrameHeader = byteArrayOf(
            0x0B.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: C2 87 80 44 0A 01 1F BF
        // index    type                                data
        // 0..3     Sample 1  (ref. sample)             C2 87 80 44 (0x448087C2)
        // 4        Delta size                          0A (10 bit)
        // 5        Sample amount                       01 (1 samples)
        // 6..      Delta data                          1F BF
        // Delta sample 1                               11 0001 1111b (- 0xE1)
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val sample0 = intBitsToFloat(0x448087C2)
        val sample1 = intBitsToFloat(0x448087C2 - 0xE1)

        val pressureDataFrameContent = byteArrayOf(
            0xC2.toByte(), 0x87.toByte(), 0x80.toByte(), 0x44.toByte(),
            0x0A.toByte(), 0x01.toByte(), 0x1F.toByte(), 0xBF.toByte(),
        )

        val factor = 2.0f
        val dataFrame = PmdDataFrame(
            data = pressureDataFrameHeader + pressureDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }
        // Act
        val pressureData = PressureData.parseDataFromDataFrame(dataFrame)
        // Assert
        Assert.assertEquals(expectedSamplesSize, pressureData.pressureSamples.size)
        Assert.assertEquals(factor * sample0, pressureData.pressureSamples[0].pressure)
        Assert.assertEquals(factor * sample1, pressureData.pressureSamples[1].pressure)
        Assert.assertEquals(timeStamp, pressureData.pressureSamples[1].timeStamp)
    }

    @Test
    fun `process raw pressure data type 0`() {
        // Arrange
        // HEX: 0B 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0B (Pressure)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     00 (raw, type 0)
        val pressureDataFrameHeader = byteArrayOf(
            0x0B.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: AE 27 7B 44
        // index    type                                data
        // 0..3     Pressure data                       AE 27 7B 44 (0x447B27AE)
        val expectedSamplesSize = 1
        val sample0 = intBitsToFloat(0x447B27AE)
        val pressureDataFrameContent = byteArrayOf(0xAE.toByte(), 0x27.toByte(), 0x7B.toByte(), 0x44.toByte())
        val factor = 1.0f

        val dataFrame = PmdDataFrame(
            data = pressureDataFrameHeader + pressureDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val pressureData = PressureData.parseDataFromDataFrame(dataFrame)
        // Assert
        Assert.assertEquals(expectedSamplesSize, pressureData.pressureSamples.size)
        Assert.assertEquals(sample0, pressureData.pressureSamples[0].pressure)
        Assert.assertEquals(timeStamp, pressureData.pressureSamples[0].timeStamp)
    }

    // ── Bounds-checking / invalid-data tests ────────────────────────────────

    private fun pressureHeader(frameTypeByte: Byte) = byteArrayOf(
        0x0B.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
        frameTypeByte
    )

    @Test
    fun `Pressure raw type 0 throws PmdDataParseException when dataContent is empty`() {
        val frame = PmdDataFrame(pressureHeader(0x00), { _, _ -> 0uL }, { 1.0f }) { 100 }
        org.junit.Assert.assertThrows(com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException::class.java) {
            PressureData.parseDataFromDataFrame(frame)
        }
    }

    @Test
    fun `Pressure raw type 0 throws PmdDataParseException when dataContent size is not multiple of 4`() {
        // TYPE_0 sample = 4 bytes (IEEE 754 float); send 5 bytes
        val frame = PmdDataFrame(pressureHeader(0x00) + byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05), { _, _ -> 0uL }, { 1.0f }) { 100 }
        org.junit.Assert.assertThrows(com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException::class.java) {
            PressureData.parseDataFromDataFrame(frame)
        }
    }

    @Test
    fun `Pressure compressed type 0 throws PmdDataParseException when dataContent is empty`() {
        val frame = PmdDataFrame(pressureHeader(0x80.toByte()), { _, _ -> 100uL }, { 1.0f }) { 0 }
        org.junit.Assert.assertThrows(com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException::class.java) {
            PressureData.parseDataFromDataFrame(frame)
        }
    }
}