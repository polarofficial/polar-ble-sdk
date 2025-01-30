package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import org.junit.Assert
import org.junit.Test

class SkinTemperatureDataTest {

    @Test
    fun `dataFromRawType0_parse-data_with-not-compresed-data`() {

        // 10 first values form the header part of the data frame. The rest is temperature data.
        // 0x07(decimal 7) = measurement type for skin temp
        // 0x40...0x0A = timestamp
        // 0x00(decimal 0) = uncompressed, raw data. Results 0 (false) for check compressed mask (0x80, decimal 128)
        // 0x00...0x41 the uncompressed skin temperature value
        val temperatureDataFrame = byteArrayOf(
            0x07.toByte(), 0x40.toByte(), 0xAE.toByte(), 0x21.toByte(), 0xAE.toByte(), 0x31.toByte(),
            0xB2.toByte(), 0xEE.toByte(), 0x0A.toByte(), 0x00.toByte(), 0x00.toByte(), 0x60.toByte(),
            0xEE.toByte(), 0x41.toByte()
        )
        val timeStamp = 787762911281000000uL
        val previousTimeStamp = 787762910281000000uL

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = temperatureDataFrame,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val temperatureData = SkinTemperatureData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(1, temperatureData.skinTemperatureSamples.size)
        Assert.assertEquals(29.796875f, temperatureData.skinTemperatureSamples[0].skinTemperature)
        Assert.assertEquals(timeStamp, temperatureData.skinTemperatureSamples[0].timeStamp)
    }

    @Test
    fun `dataFromCompressedType0_parse-data_with-compressed-data`() {

        // 10 first values form the header part of the data frame. The rest is temperature data.
        // 0x07(decimal 7) = measurement type for skin temp
        // 0x40...0x0A = timestamp
        // 0x80(decimal 128) = compressed data. Results 1 (true for check compressed mask (0x80, decimal 128)
        // 0xEC...0x00 the delta compressed skin temperature values
        val temperatureDataFrame = byteArrayOf(
            0x07.toByte(), 0x40.toByte(), 0xAE.toByte(), 0x21.toByte(), 0xAE.toByte(), 0x31.toByte(),
            0xB2.toByte(), 0xEE.toByte(), 0x0A.toByte(), 0x80.toByte(), 0xEC.toByte(), 0x51.toByte(),
            0xDC.toByte(), 0x41.toByte(), 0x03.toByte(), 0x02.toByte(), 0x00.toByte()
        )
        val dataFrame = PmdDataFrame(
            data = temperatureDataFrame,
            getPreviousTimeStamp = {  pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> 1000uL },
            getFactor = { 1.0f }
        ) { 13 }

        // Act
        val temperatureData = SkinTemperatureData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(3, temperatureData.skinTemperatureSamples.size)
        Assert.assertEquals(27.54f, temperatureData.skinTemperatureSamples[0].skinTemperature)
        Assert.assertEquals(27.54f, temperatureData.skinTemperatureSamples[1].skinTemperature)
        Assert.assertEquals(27.54f, temperatureData.skinTemperatureSamples[2].skinTemperature)
    }
}