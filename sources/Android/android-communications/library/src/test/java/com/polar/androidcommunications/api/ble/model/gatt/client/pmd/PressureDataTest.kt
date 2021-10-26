package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PressureData
import org.junit.Assert
import org.junit.Test
import java.lang.Float.intBitsToFloat

class PressureDataTest {

    @Test
    fun `process pressure data type 0`() {
        // Arrange
        // HEX: C2 87 80 44 0A 01 1F BF
        // index    type                                data
        // 0..3     Sample 1  (ref. sample)             C2 87 80 44 (0x448087C2)
        // 4        Delta size                          0A (10 bit)
        // 5        Sample amount                       01 (1 samples)
        // 6..      Delta data                          1F BF
        // Delta sample 1                               11 0001 1111b (- 0xE1)

        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val expectedTimeStamp = 578437695752307201L
        val sample0 = intBitsToFloat(0x448087C2)
        val sample1 = intBitsToFloat(0x448087C2 - 0xE1)

        val measurementFrame = byteArrayOf(
            0xC2.toByte(), 0x87.toByte(), 0x80.toByte(), 0x44.toByte(),
            0x0A.toByte(), 0x01.toByte(), 0x1F.toByte(), 0xBF.toByte(),
        )
        val frameType = BlePMDClient.PmdDataFrameType.TYPE_0
        val factor = 1.0f

        // Act
        val pressureData = PressureData.parseDataFromDataFrame(isCompressed = true, frameType = frameType, frame = measurementFrame, factor = factor, timeStamp = expectedTimeStamp)

        // Assert
        Assert.assertEquals(expectedTimeStamp, pressureData.timeStamp)
        Assert.assertEquals(expectedSamplesSize, pressureData.pressureSamples.size)
        Assert.assertEquals(sample0, pressureData.pressureSamples[0].pressure)
        Assert.assertEquals(sample1, pressureData.pressureSamples[1].pressure)
    }

    @Test
    fun `process pressure data type 0 with factor`() {
        // Arrange
        // HEX: C2 87 80 44 0A 01 1F BF
        // index    type                                data
        // 0..3     Sample 1  (ref. sample)             C2 87 80 44 (0x448087C2)
        // 4        Delta size                          0A (10 bit)
        // 5        Sample amount                       01 (1 samples)
        // 6..      Delta data                          1F BF
        // Delta sample 1                               11 0001 1111b (- 0xE1)

        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val expectedTimeStamp = 578437695752307201L
        val sample0 = intBitsToFloat(0x448087C2)
        val sample1 = intBitsToFloat(0x448087C2 - 0xE1)

        val measurementFrame = byteArrayOf(
            0xC2.toByte(), 0x87.toByte(), 0x80.toByte(), 0x44.toByte(),
            0x0A.toByte(), 0x01.toByte(), 0x1F.toByte(), 0xBF.toByte(),
        )
        val frameType = BlePMDClient.PmdDataFrameType.TYPE_0
        val factor = 2.0f

        // Act
        val pressureData = PressureData.parseDataFromDataFrame(isCompressed = true, frameType = frameType, frame = measurementFrame, factor = factor, timeStamp = expectedTimeStamp)

        // Assert
        Assert.assertEquals(expectedTimeStamp, pressureData.timeStamp)
        Assert.assertEquals(expectedSamplesSize, pressureData.pressureSamples.size)
        Assert.assertEquals(factor * sample0, pressureData.pressureSamples[0].pressure)
        Assert.assertEquals(factor * sample1, pressureData.pressureSamples[1].pressure)
    }
}