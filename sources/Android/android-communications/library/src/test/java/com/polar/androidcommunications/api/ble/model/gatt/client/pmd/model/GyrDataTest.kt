package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import org.junit.Assert
import org.junit.Test
import java.lang.Float.intBitsToFloat

class GyrDataTest {

    @Test
    fun `process gyro compressed data type 0`() {
        // Arrange
        // HEX: EA FF 08 00 0D 00 03 01 DF 00
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  EA FF (0xFFEA = -22)
        // 2..3     Sample 0 - channel 1 (ref. sample)  08 00 (0x0008 = 8)
        // 4..5     Sample 0 - channel 2 (ref. sample)  0D 00 (0x000D = 13)
        // 6        Delta size                          03 (3 bit)
        // 7        Sample amount                       01 (1 samples)
        // 8..      Delta data                          DF (binary: 11 011 111) 00 (binary: 0000000 0)
        // Delta channel 0                              111b
        // Delta channel 1                              011b
        // Delta channel 2                              011b
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val expectedTimeStamp = 9223372036854775807L
        val sample0channel0 = -22.0f
        val sample0channel1 = 8.0f
        val sample0channel2 = 13.0f

        val sample1channel0 = sample0channel0 - 0x1
        val sample1channel1 = sample0channel1 + 0x3
        val sample1channel2 = sample0channel2 + 0x3

        val measurementFrame = byteArrayOf(
            0xEA.toByte(), 0xFF.toByte(),
            0x08.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x00.toByte(),
            0x03.toByte(), 0x01.toByte(), 0xDF.toByte(), 0x00.toByte()
        )

        // Act
        val gyroData = GyrData.parseDataFromDataFrame(isCompressed = true, frameType = BlePMDClient.PmdDataFrameType.TYPE_0, frame = measurementFrame, factor = 1.0f, timeStamp = expectedTimeStamp)

        // Assert
        Assert.assertEquals(expectedTimeStamp, gyroData.timeStamp)
        Assert.assertEquals(expectedSamplesSize, gyroData.gyrSamples.size)
        Assert.assertEquals(sample0channel0, gyroData.gyrSamples[0].x)
        Assert.assertEquals(sample0channel1, gyroData.gyrSamples[0].y)
        Assert.assertEquals(sample0channel2, gyroData.gyrSamples[0].z)

        Assert.assertEquals(sample1channel0, gyroData.gyrSamples[1].x)
        Assert.assertEquals(sample1channel1, gyroData.gyrSamples[1].y)
        Assert.assertEquals(sample1channel2, gyroData.gyrSamples[1].z)
    }

    @Test
    fun `process gyro data type 1`() {
        // Arrange
        // HEX: 00 00 80 3F 00 00 20 41 00 00 A0 41 1C 01 00 00 A0 01 00 00 08 CD CC EC 0D
        // index    type                                data
        // 10..13  Sample 0 - channel 0 (ref. sample)   00 00 80 3F (0x3F800000)
        // 14..17  Sample 0 - channel 1 (ref. sample)   00 00 20 41 (0x41200000)
        // 18..21  Sample 0 - channel 2 (ref. sample)   00 00 A0 41 (0x41A00000)
        // 22      Delta size                           1C (28 bit)
        // 23      Sample amount                        01 (1 samples)
        // 24..35 delta data: 00 00 A0 01 00 00 08 CD CC EC 0D
        //         Sample 1 - channel 0: (0x01A00000)
        //         Sample 1 - channel 1: (0x00800000)
        //         Sample 1 - channel 2  (0xFDECCCCD)

        val expectedTimeStamp = 2509038777
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val sample0channel0 = intBitsToFloat(0x3F800000)
        val sample0channel1 = intBitsToFloat(0x41200000)
        val sample0channel2 = intBitsToFloat(0x41A00000)

        val sample1channel0 = intBitsToFloat(0x3F800000 + 0x1A00000)
        val sample1channel1 = intBitsToFloat(0x41200000 + 0x0800000)
        val sample1channel2 = intBitsToFloat((0x41A00000 + 0xFDECCCCD).toInt())

        val measurementFrame = byteArrayOf(
            0x00.toByte(), 0x00.toByte(),
            0x80.toByte(), 0x3F.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x20.toByte(), 0x41.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xA0.toByte(), 0x41.toByte(), 0x1C.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xA0.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x08.toByte(), 0xCD.toByte(),
            0xCC.toByte(), 0xEC.toByte(), 0x0D.toByte()
        )

        // Act
        val gyroData = GyrData.parseDataFromDataFrame(isCompressed = true, frameType = BlePMDClient.PmdDataFrameType.TYPE_1, frame = measurementFrame, factor = 1.0f, timeStamp = expectedTimeStamp)

        // Assert
        Assert.assertEquals(expectedSamplesSize, gyroData.gyrSamples.size)
        Assert.assertEquals(expectedTimeStamp, gyroData.timeStamp)
        Assert.assertEquals(sample0channel0, gyroData.gyrSamples[0].x)
        Assert.assertEquals(sample0channel1, gyroData.gyrSamples[0].y)
        Assert.assertEquals(sample0channel2, gyroData.gyrSamples[0].z)

        Assert.assertEquals(sample1channel0, gyroData.gyrSamples[1].x)
        Assert.assertEquals(sample1channel1, gyroData.gyrSamples[1].y)
        Assert.assertEquals(sample1channel2, gyroData.gyrSamples[1].z)
    }

    @Test
    fun `process gyro data type 1 with factor`() {
        // Arrange
        // HEX: 00 00 80 3F 00 00 20 41 00 00 A0 41 1C 01 00 00 A0 01 00 00 08 CD CC EC 0D
        // index    type                                data
        // 10..13  Sample 0 - channel 0 (ref. sample)   00 00 80 3F (0x3F800000)
        // 14..17  Sample 0 - channel 1 (ref. sample)   00 00 20 41 (0x41200000)
        // 18..21  Sample 0 - channel 2 (ref. sample)   00 00 A0 41 (0x41A00000)
        // 22      Delta size                           1C (28 bit)
        // 23      Sample amount                        01 (1 samples)
        // 24..35 delta data: 00 00 A0 01 00 00 08 CD CC EC 0D
        //         Sample 1 - channel 0: (0x01A00000)
        //         Sample 1 - channel 1: (0x00800000)
        //         Sample 1 - channel 2  (0xFDECCCCD)

        val expectedTimeStamp = 2509038777
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val sample0channel0 = intBitsToFloat(0x3F800000)
        val sample0channel1 = intBitsToFloat(0x41200000)
        val sample0channel2 = intBitsToFloat(0x41A00000)

        val sample1channel0 = intBitsToFloat(0x3F800000 + 0x1A00000)
        val sample1channel1 = intBitsToFloat(0x41200000 + 0x0800000)
        val sample1channel2 = intBitsToFloat((0x41A00000 + 0xFDECCCCD).toInt())

        val measurementFrame = byteArrayOf(
            0x00.toByte(), 0x00.toByte(),
            0x80.toByte(), 0x3F.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x20.toByte(), 0x41.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xA0.toByte(), 0x41.toByte(), 0x1C.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xA0.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x08.toByte(), 0xCD.toByte(),
            0xCC.toByte(), 0xEC.toByte(), 0x0D.toByte()
        )
        val factor = 0.5f

        // Act
        val gyroData = GyrData.parseDataFromDataFrame(isCompressed = true, frameType = BlePMDClient.PmdDataFrameType.TYPE_1, frame = measurementFrame, factor = factor, timeStamp = expectedTimeStamp)

        // Assert
        Assert.assertEquals(expectedSamplesSize, gyroData.gyrSamples.size)
        Assert.assertEquals(expectedTimeStamp, gyroData.timeStamp)
        Assert.assertEquals(factor * sample0channel0, gyroData.gyrSamples[0].x)
        Assert.assertEquals(factor * sample0channel1, gyroData.gyrSamples[0].y)
        Assert.assertEquals(factor * sample0channel2, gyroData.gyrSamples[0].z)

        Assert.assertEquals(factor * sample1channel0, gyroData.gyrSamples[1].x)
        Assert.assertEquals(factor * sample1channel1, gyroData.gyrSamples[1].y)
        Assert.assertEquals(factor * sample1channel2, gyroData.gyrSamples[1].z)
    }
}