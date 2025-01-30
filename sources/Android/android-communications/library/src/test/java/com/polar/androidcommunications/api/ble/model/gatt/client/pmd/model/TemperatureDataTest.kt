package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import org.junit.Assert
import org.junit.Test
import java.lang.Float.intBitsToFloat

class TemperatureDataTest {

    @Test
    fun `process compressed temperature data type 0`() {
        // Arrange
        // HEX: 0C 00 94 35 77 00 00 00 00 80
        // index                                                   data:
        // 0        type                                           0C (Temperature)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     80 (compressed, type 0)
        val temperatureDataFrameHeader = byteArrayOf(
            0x0C.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: EC 51 DC 41 03 02 00
        // index    type                                data
        // 0..3     Sample 1  (ref. sample)             EC 51 DC 41 (0x41DC51EC)
        // 4        Delta size                          03 (3 bits)
        // 5        Sample amount                       01 (2 samples)
        // 6..      Delta data                          00
        // Delta sample 1                               000b (0)
        // Delta sample 2                               000b (0)
        val expectedSamplesSize = 1 + 2 // reference sample + delta samples
        val sample0 = intBitsToFloat(0x41DC51EC)
        val sample1 = intBitsToFloat(0x41DC51EC + 0x0)
        val sample2 = intBitsToFloat(0x41DC51EC + 0x0)

        val temperatureDataFrameContent = byteArrayOf(
            0xEC.toByte(), 0x51.toByte(), 0xDC.toByte(), 0x41.toByte(), 0x03.toByte(), 0x02.toByte(), 0x00.toByte()
        )
        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = temperatureDataFrameHeader + temperatureDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val temperatureData = TemperatureData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, temperatureData.temperatureSamples.size)
        Assert.assertEquals(sample0, temperatureData.temperatureSamples[0].temperature)
        Assert.assertEquals(sample1, temperatureData.temperatureSamples[1].temperature)
        Assert.assertEquals(sample2, temperatureData.temperatureSamples[2].temperature)
        Assert.assertEquals(timeStamp, temperatureData.temperatureSamples[2].timeStamp)
    }

    @Test
    fun `process raw temperature data type 0`() {
        // Arrange
        // HEX: 0C 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0C (Temperature)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     00 (raw, type 0)
        val temperatureDataFrameHeader = byteArrayOf(
            0x0C.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX:   F6 28 C0 41
        // index    type                                data
        // 0..3     Temperature data                    F6 28 C0 41 (0x41C028F6)
        val expectedSamplesSize = 1
        val sample0 = intBitsToFloat(0x41C028F6)
        val temperatureDataFrameContent = byteArrayOf(0xF6.toByte(), 0x28.toByte(), 0xC0.toByte(), 0x41.toByte())
        val factor = 1.0f

        val dataFrame = PmdDataFrame(
            data = temperatureDataFrameHeader + temperatureDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val temperatureData = TemperatureData.parseDataFromDataFrame(dataFrame)
        // Assert
        Assert.assertEquals(expectedSamplesSize, temperatureData.temperatureSamples.size)
        Assert.assertEquals(sample0, temperatureData.temperatureSamples[0].temperature)
        Assert.assertEquals(timeStamp, temperatureData.temperatureSamples[0].timeStamp)
    }
}