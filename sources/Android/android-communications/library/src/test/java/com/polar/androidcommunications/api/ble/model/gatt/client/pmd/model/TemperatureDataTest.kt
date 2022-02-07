package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import org.junit.Assert
import org.junit.Test
import java.lang.Float.intBitsToFloat

class TemperatureDataTest {
    @Test
    fun `process compressed temperature data type 0`() {
        // Arrange
        // HEX: EC 51 DC 41 03 02 00
        // index    type                                data
        // 0..3     Sample 1  (ref. sample)             EC 51 DC 41 (0x41DC51EC)
        // 4        Delta size                          03 (3 bits)
        // 5        Sample amount                       01 (2 samples)
        // 6..      Delta data                          00
        // Delta sample 1                               000b (0)
        // Delta sample 2                               000b (0)

        val expectedSamplesSize = 1 + 2 // reference sample + delta samples
        val expectedTimeStamp = 578437695752307201L
        val sample0 = intBitsToFloat(0x41DC51EC)
        val sample1 = intBitsToFloat(0x41DC51EC + 0x0)
        val sample2 = intBitsToFloat(0x41DC51EC + 0x0)

        val measurementFrame = byteArrayOf(
            0xEC.toByte(), 0x51.toByte(), 0xDC.toByte(), 0x41.toByte(), 0x03.toByte(), 0x02.toByte(), 0x00.toByte()
        )
        val frameType = BlePMDClient.PmdDataFrameType.TYPE_0
        val isCompressed = true
        val factor = 1.0f

        // Act
        val temperatureData = TemperatureData.parseDataFromDataFrame(isCompressed = isCompressed, frameType = frameType, frame = measurementFrame, factor = factor, timeStamp = expectedTimeStamp)

        // Assert
        Assert.assertEquals(expectedTimeStamp, temperatureData.timeStamp)
        Assert.assertEquals(expectedSamplesSize, temperatureData.temperatureSamples.size)
        Assert.assertEquals(sample0, temperatureData.temperatureSamples[0].temperature)
        Assert.assertEquals(sample1, temperatureData.temperatureSamples[1].temperature)
        Assert.assertEquals(sample2, temperatureData.temperatureSamples[2].temperature)
    }

    @Test
    fun `process raw temperature data type 0`() {
        // Arrange
        // HEX:   F6 28 C0 41
        // index    type                                data
        // 0..3     Temperature data                    F6 28 C0 41 (0x41C028F6)
        val timeStamp: Long = 0
        val frameType = BlePMDClient.PmdDataFrameType.TYPE_0
        val isCompressed = false
        val expectedSamplesSize = 1
        val sample0 = intBitsToFloat(0x41C028F6)
        val measurementFrame = byteArrayOf(0xF6.toByte(), 0x28.toByte(), 0xC0.toByte(), 0x41.toByte())
        val factor = 1.0f

        // Act
        val temperatureData = TemperatureData.parseDataFromDataFrame(isCompressed = isCompressed, frameType = frameType, frame = measurementFrame, factor = factor, timeStamp = timeStamp)

        // Assert
        Assert.assertEquals(timeStamp, temperatureData.timeStamp)
        Assert.assertEquals(expectedSamplesSize, temperatureData.temperatureSamples.size)
        Assert.assertEquals(sample0, temperatureData.temperatureSamples[0].temperature)
    }
}