package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.round

internal class AccDataTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `process acc raw data frame type 1`() {
        // Arrange
        // HEX: 02 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           02 (ACC)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     01

        val accDataFrameHeader = byteArrayOf(
            0x02.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x01.toByte(),
        )

        // HEX: 01 F7 FF FF FF E7 03 F8 FF FE FF E5 03 F9 FF FF FF E5 03 FA FF FF FF E6 03 FA FF FE FF E6 03 F9 FF FF FF E5 03 F8 FF FF FF E6 03 F8 FF FE FF E6 03 FA FF FF FF E5 03 FA FF FF FF E7 03 FA FF FF FF E5 03 F8 FF FF FF E6 03 F7 FF FF FF E6 03 F8 FF FE FF E6 03 F9 FF FE FF E7 03 F9 FF 00 00 E6 03 F9 FF FF FF E6 03 F7 FF FE FF E5 03 F9 FF FF FF E5 03 F9 FF FF FF E5 03 FA FF 00 00 E6 03 F9 FF FE FF E6 03 F8 FF FF FF E6 03 F8 FF FF FF E5 03 F9 FF FF FF E6 03 F9 FF FF FF E5 03 FA FF FF FF E6 03 F9 FF FF FF E5 03 F9 FF FF FF E5 03 F8 FF FE FF E6 03 F9 FF FF FF E6 03 F9 FF FF FF E6 03 F9 FF 00 00 E5 03 F9 FF FE FF E6 03 F8 FF FE FF E6 03 F7 FF FE FF E6 03
        // index                                                   data:
        // 0        frame type                                     01
        // 1..2     x value                                        F7 FF (-9)
        val xValue1 = -9
        // 3..4     y value                                        FF FF (-1)
        val yValue1 = -1
        // 5..6     z value                                        E7 03 (999)
        val zValue1 = 999
        // 7..8     x value                                        F8 FF (-8)
        val xValue2 = -8
        // 9..10    y value                                        FF FE (-2)
        val yValue2 = -2
        // 11..12   z value                                        E5 03 (997)
        val zValue2 = 997
        val accDataFrameContent = byteArrayOf(
            0xF7.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE7.toByte(), 0x03.toByte(), 0xF8.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(),
            0xE5.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xFA.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(), 0xFA.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(),
            0xF9.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xF8.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xE6.toByte(), 0x03.toByte(), 0xF8.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(), 0xFA.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xFA.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE7.toByte(), 0x03.toByte(),
            0xFA.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xF8.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xE6.toByte(), 0x03.toByte(), 0xF7.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(), 0xF8.toByte(), 0xFF.toByte(),
            0xFE.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xE7.toByte(), 0x03.toByte(),
            0xF9.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0xE6.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xE6.toByte(), 0x03.toByte(), 0xF7.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(),
            0xFA.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0xE6.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(),
            0xE6.toByte(), 0x03.toByte(), 0xF8.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(), 0xF8.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(),
            0xF9.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xFA.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xE6.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xF8.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(),
            0xF9.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xE6.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0xE5.toByte(), 0x03.toByte(), 0xF9.toByte(), 0xFF.toByte(),
            0xFE.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(), 0xF8.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte(),
            0xF7.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xE6.toByte(), 0x03.toByte()
        )

        val amountOfSamples = accDataFrameContent.size / 2 / 3 // measurement frame size / resolution in bytes / channels
        val sampleRate = 52
        val dataFrame = PmdDataFrame(
            data = accDataFrameHeader + accDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> 0uL },
            getFactor = { 1.0F }
        ) { sampleRate }

        // Act
        val accData = AccData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(xValue1, accData.accSamples[0].x)
        Assert.assertEquals(yValue1, accData.accSamples[0].y)
        Assert.assertEquals(zValue1, accData.accSamples[0].z)
        Assert.assertEquals(xValue2, accData.accSamples[1].x)
        Assert.assertEquals(yValue2, accData.accSamples[1].y)
        Assert.assertEquals(zValue2, accData.accSamples[1].z)
        Assert.assertEquals(timeStamp, accData.accSamples.last().timeStamp)

        // validate data size
        Assert.assertEquals(amountOfSamples, accData.accSamples.size)
    }

    @Test
    fun `process acc compressed data type 0`() {
        // Arrange
        // HEX: 02 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           02 (ACC)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     80 (compressed, type 0)

        val accDataFrameHeader = byteArrayOf(
            0x02.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: 71 07 F0 6A 9E 8D 0A 38 BE 5C BE BA 2F 96 B3 EE 4B E5 AD FB 42 B9 EB BE 4C FE BA 2F 92 BF EE 4B E4 B1 FB 12 B9 EC BD 3C 3E BB 2F 8F D3 DE 4B E3 B5 F7 D2 B8 ED BD 30 7E 7B 2F 8B E3 CE 8B E2 BA F7 A2 B8 EE BC 20 BE 7B 2F 88 F3 CE CB E1 BD EF 52 F8 EF BC 18 FE 3B 2F 84 03 BF CB E0 C2 EF 32 B8 F0 BB 04 4E BC 2E 81 13 AF 0B E0 C6 EF F2 F7 F1 B9 FC 7D BC 2E 7D 27 9F 4B DF CA EB C2 F7 F2 B8 EC CD 7C 2E 7B 37 8F 4B DE CE E3 92 F7 F3 B8 E0 0D FD 2D 77 4B 7F CB DD D2 DF 62 37 F5 B7 D4 4D BD 2D 74 5B 6F CB DC D7 D7 32 37 F6 B5 C8 8D 7D 2D 71 6B 4F 4B DC DC D3 F2 36 F7 B4 BC DD FD 2C 6F 7B 3F 4B DB E0 CF D2 36 F8 B2 B0 2D BE 2C 6C 8F 1F CB DA E3 C7 A2 76 F9
        // index    type                                            data:
        // 0-5:    Reference sample                                 0x71 0x07 0xF0 0x6A 0x9E 0x8D
        //      Sample 0 (aka. reference sample):
        //      channel 0: 71 07 => 0x0771 => 1905
        val sample0Channel0 = 1905
        //      channel 1: F0 6A => 0x6AF0 => 27376
        val sample0Channel1 = 27376
        //      channel 2: 9E 8D => 0x8D9E => -29282
        val sample0Channel2 = -29282
        // Delta dump: 0A 38 | BE 5C BE BA 2F 96 B3 EE 4B E5 AD ...
        // 6:      Delta size                           size 1:    0x0A (10 bits)
        // 7:      Sample amount                        size 1:    0x38 (Delta block contains 56 samples)
        // 8:                                                      0xBE (binary: 1011 1110)
        // 9:                                                      0x5C (binary: 0101 11 | 00)
        // 10:                                                     0xBE (binary: 1011 | 1110)
        //      Sample 1 - channel 0, size 10 bits: 00 1011 1110
        //      Sample 1 - channel 1, size 10 bits: 11 1001 0111
        // 11:                                                     0xBA (binary: 10 | 11 1010)
        //      Sample 1 - channel 2, size 10 bits: 11 1010 1011
        val sample1Channel0 = sample0Channel0 + 190
        val sample1Channel1 = sample0Channel1 - 105
        val sample1Channel2 = sample0Channel2 - 85
        val amountOfSamples = 1 + 56 // reference sample + delta samples

        val accDataFrameContent = byteArrayOf(
            0x71.toByte(), 0x07.toByte(), 0xF0.toByte(), 0x6A.toByte(), 0x9E.toByte(), 0x8D.toByte(), 0x0A.toByte(), 0x38.toByte(),
            0xBE.toByte(), 0x5C.toByte(), 0xBE.toByte(), 0xBA.toByte(), 0x2F.toByte(), 0x96.toByte(), 0xB3.toByte(), 0xEE.toByte(),
            0x4B.toByte(), 0xE5.toByte(), 0xAD.toByte(), 0xFB.toByte(), 0x42.toByte(), 0xB9.toByte(), 0xEB.toByte(), 0xBE.toByte(),
            0x4C.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0x2F.toByte(), 0x92.toByte(), 0xBF.toByte(), 0xEE.toByte(), 0x4B.toByte(),
            0xE4.toByte(), 0xB1.toByte(), 0xFB.toByte(), 0x12.toByte(), 0xB9.toByte(), 0xEC.toByte(), 0xBD.toByte(), 0x3C.toByte(),
            0x3E.toByte(), 0xBB.toByte(), 0x2F.toByte(), 0x8F.toByte(), 0xD3.toByte(), 0xDE.toByte(), 0x4B.toByte(), 0xE3.toByte(),
            0xB5.toByte(), 0xF7.toByte(), 0xD2.toByte(), 0xB8.toByte(), 0xED.toByte(), 0xBD.toByte(), 0x30.toByte(), 0x7E.toByte(),
            0x7B.toByte(), 0x2F.toByte(), 0x8B.toByte(), 0xE3.toByte(), 0xCE.toByte(), 0x8B.toByte(), 0xE2.toByte(), 0xBA.toByte(),
            0xF7.toByte(), 0xA2.toByte(), 0xB8.toByte(), 0xEE.toByte(), 0xBC.toByte(), 0x20.toByte(), 0xBE.toByte(), 0x7B.toByte(),
            0x2F.toByte(), 0x88.toByte(), 0xF3.toByte(), 0xCE.toByte(), 0xCB.toByte(), 0xE1.toByte(), 0xBD.toByte(), 0xEF.toByte(),
            0x52.toByte(), 0xF8.toByte(), 0xEF.toByte(), 0xBC.toByte(), 0x18.toByte(), 0xFE.toByte(), 0x3B.toByte(), 0x2F.toByte(),
            0x84.toByte(), 0x03.toByte(), 0xBF.toByte(), 0xCB.toByte(), 0xE0.toByte(), 0xC2.toByte(), 0xEF.toByte(), 0x32.toByte(),
            0xB8.toByte(), 0xF0.toByte(), 0xBB.toByte(), 0x04.toByte(), 0x4E.toByte(), 0xBC.toByte(), 0x2E.toByte(), 0x81.toByte(),
            0x13.toByte(), 0xAF.toByte(), 0x0B.toByte(), 0xE0.toByte(), 0xC6.toByte(), 0xEF.toByte(), 0xF2.toByte(), 0xF7.toByte(),
            0xF1.toByte(), 0xB9.toByte(), 0xFC.toByte(), 0x7D.toByte(), 0xBC.toByte(), 0x2E.toByte(), 0x7D.toByte(), 0x27.toByte(),
            0x9F.toByte(), 0x4B.toByte(), 0xDF.toByte(), 0xCA.toByte(), 0xEB.toByte(), 0xC2.toByte(), 0xF7.toByte(), 0xF2.toByte(),
            0xB8.toByte(), 0xEC.toByte(), 0xCD.toByte(), 0x7C.toByte(), 0x2E.toByte(), 0x7B.toByte(), 0x37.toByte(), 0x8F.toByte(),
            0x4B.toByte(), 0xDE.toByte(), 0xCE.toByte(), 0xE3.toByte(), 0x92.toByte(), 0xF7.toByte(), 0xF3.toByte(), 0xB8.toByte(),
            0xE0.toByte(), 0x0D.toByte(), 0xFD.toByte(), 0x2D.toByte(), 0x77.toByte(), 0x4B.toByte(), 0x7F.toByte(), 0xCB.toByte(),
            0xDD.toByte(), 0xD2.toByte(), 0xDF.toByte(), 0x62.toByte(), 0x37.toByte(), 0xF5.toByte(), 0xB7.toByte(), 0xD4.toByte(),
            0x4D.toByte(), 0xBD.toByte(), 0x2D.toByte(), 0x74.toByte(), 0x5B.toByte(), 0x6F.toByte(), 0xCB.toByte(), 0xDC.toByte(),
            0xD7.toByte(), 0xD7.toByte(), 0x32.toByte(), 0x37.toByte(), 0xF6.toByte(), 0xB5.toByte(), 0xC8.toByte(), 0x8D.toByte(),
            0x7D.toByte(), 0x2D.toByte(), 0x71.toByte(), 0x6B.toByte(), 0x4F.toByte(), 0x4B.toByte(), 0xDC.toByte(), 0xDC.toByte(),
            0xD3.toByte(), 0xF2.toByte(), 0x36.toByte(), 0xF7.toByte(), 0xB4.toByte(), 0xBC.toByte(), 0xDD.toByte(), 0xFD.toByte(),
            0x2C.toByte(), 0x6F.toByte(), 0x7B.toByte(), 0x3F.toByte(), 0x4B.toByte(), 0xDB.toByte(), 0xE0.toByte(), 0xCF.toByte(),
            0xD2.toByte(), 0x36.toByte(), 0xF8.toByte(), 0xB2.toByte(), 0xB0.toByte(), 0x2D.toByte(), 0xBE.toByte(), 0x2C.toByte(),
            0x6C.toByte(), 0x8F.toByte(), 0x1F.toByte(), 0xCB.toByte(), 0xDA.toByte(), 0xE3.toByte(), 0xC7.toByte(), 0xA2.toByte(),
            0x76.toByte(), 0xF9.toByte()
        )
        val delta = PmdTimeStampUtils.deltaFromTimeStamps(previousTimeStamp, timeStamp, amountOfSamples)
        val expectedFirstSampleTimeStamp = round(previousTimeStamp.toDouble() + delta).toULong()

        val range = 8
        val factor = 2.44E-4f
        val dataFrame = PmdDataFrame(
            data = accDataFrameHeader + accDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val accData = AccData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals((factor * sample0Channel0 * 1000f).toInt(), accData.accSamples[0].x)
        Assert.assertEquals((factor * sample0Channel1 * 1000f).toInt(), accData.accSamples[0].y)
        Assert.assertEquals((factor * sample0Channel2 * 1000f).toInt(), accData.accSamples[0].z)
        Assert.assertEquals((factor * sample1Channel0 * 1000f).toInt(), accData.accSamples[1].x)
        Assert.assertEquals((factor * sample1Channel1 * 1000f).toInt(), accData.accSamples[1].y)
        Assert.assertEquals((factor * sample1Channel2 * 1000f).toInt(), accData.accSamples[1].z)

        // validate data in range
        for (sample in accData.accSamples) {
            Assert.assertTrue(abs(sample.x) <= range * 1000)
            Assert.assertTrue(abs(sample.y) <= range * 1000)
            Assert.assertTrue(abs(sample.z) <= range * 1000)
        }

        // validate time stamps
        Assert.assertEquals(expectedFirstSampleTimeStamp, accData.accSamples.first().timeStamp)
        Assert.assertEquals(timeStamp, accData.accSamples.last().timeStamp)

        // validate data size
        Assert.assertEquals(amountOfSamples, accData.accSamples.size)
    }

    @Test
    fun `process acc compressed data type 1`() {
        // HEX: F1 FF 14 00 F0 03 06 01 7B 0F 08
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  F1 FF (0xFFF1 = -22)
        // 2..3     Sample 0 - channel 1 (ref. sample)  14 00 (0x0014 = 20)
        // 4..5     Sample 0 - channel 2 (ref. sample)  F0 03 (0x03F0 = 1008)
        // 6        Delta size                          06 (6 bit)
        // 7        Sample amount                       01 (10 samples)
        // 8..      Delta data                          7B (binary: 01 111011) 0F (binary: 0000 1111) 08 (binary: 0000 1000)
        // Delta channel 0                              111011b
        // Delta channel 1                              111101b
        // Delta channel 2                              000000b
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val sample0channel0 = -15
        val sample0channel1 = 20
        val sample0channel2 = 1008
        val sample1channel0 = sample0channel0 - 5
        val sample1channel1 = sample0channel1 - 3
        val sample1channel2 = sample0channel2 + 0

        val accDataFrameHeader = byteArrayOf(
            0x02.toByte(),
            0x65.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x81.toByte(),
        )

        val accDataFrameContent = byteArrayOf(
            0xF1.toByte(), 0xFF.toByte(), 0x14.toByte(), 0x00.toByte(), 0xF0.toByte(), 0x03.toByte(), 0x06.toByte(),
            0x01.toByte(), 0x7B.toByte(), 0x0F.toByte(), 0x08.toByte()
        )

        val dataFrame = PmdDataFrame(
            data = accDataFrameHeader + accDataFrameContent,
            getPreviousTimeStamp = {  pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> 100uL },
            getFactor = { 1.0f }
        ) { 0 }

        // Act
        val accData = AccData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, accData.accSamples.size)

        Assert.assertEquals(sample0channel0, accData.accSamples[0].x)
        Assert.assertEquals(sample0channel1, accData.accSamples[0].y)
        Assert.assertEquals(sample0channel2, accData.accSamples[0].z)

        Assert.assertEquals(sample1channel0, accData.accSamples[1].x)
        Assert.assertEquals(sample1channel1, accData.accSamples[1].y)
        Assert.assertEquals(sample1channel2, accData.accSamples[1].z)

        Assert.assertEquals(100uL, accData.accSamples[0].timeStamp)
        Assert.assertEquals(101uL, accData.accSamples[1].timeStamp)
    }
}

private val measurementFrameAccType0Short = byteArrayOf(
    0x5D.toByte(), 0xFF.toByte(), 0x98.toByte(), 0x00.toByte(), 0xFD.toByte(), 0x0F.toByte(), 0x06.toByte(), 0x2A.toByte(), 0xF2.toByte(),
    0xB0.toByte(), 0x13.toByte(), 0xEC.toByte(), 0xA1.toByte(), 0x1B.toByte(), 0x3E.toByte(), 0xC3.toByte(), 0xD0.toByte(), 0x40.toByte(),
    0x7F.toByte(), 0xCD.toByte(), 0x40.toByte(), 0x71.toByte(), 0xD8.toByte(), 0xF3.toByte(), 0x4D.toByte(), 0x00.toByte(), 0x46.toByte(),
    0x3F.toByte(), 0x08.toByte(), 0x7B.toByte(), 0x9F.toByte(), 0xD7.toByte(), 0x3A.toByte(), 0xAF.toByte(), 0x38.toByte(), 0x52.toByte(),
    0xA6.toByte(), 0x13.toByte(), 0xED.toByte(), 0x3E.toByte(), 0x13.toByte(), 0x09.toByte(), 0x91.toByte(), 0xDC.toByte(), 0xFA.toByte(),
    0xEE.toByte(), 0xEF.toByte(), 0x3E.toByte(), 0x54.toByte(), 0x10.toByte(), 0xE2.toByte(), 0x8F.toByte(), 0x77.toByte(), 0x85.toByte(),
    0xEE.toByte(), 0xF2.toByte(), 0x4C.toByte(), 0x7E.toByte(), 0x04.toByte(), 0x87.toByte(), 0x9C.toByte(), 0xFC.toByte(), 0xD2.toByte(),
    0x2F.toByte(), 0x10.toByte(), 0xFD.toByte(), 0x4F.toByte(), 0xEF.toByte(), 0xFE.toByte(), 0x21.toByte(), 0xFF.toByte(), 0xC2.toByte(),
    0x81.toByte(), 0x04.toByte(), 0x03.toByte(), 0x5F.toByte(), 0x13.toByte(), 0x45.toByte(), 0xD1.toByte(), 0x08.toByte(), 0x4A.toByte(),
    0xDF.toByte(), 0xBF.toByte(), 0xB5.toByte(), 0x51.toByte(), 0x44.toByte(), 0x7B.toByte(), 0x50.toByte(), 0xF7.toByte(), 0x39.toByte(),
    0xA1.toByte(), 0x10.toByte(), 0x2E.toByte(), 0x6F.toByte(), 0x18.toByte(), 0xF7.toByte(), 0x9F.toByte(), 0x1C.toByte(), 0x36.toByte(),
    0x4D.toByte(), 0x1B.toByte(), 0x0D.toByte(), 0x03.toByte(), 0x0C.toByte(), 0x14.toByte(), 0x06.toByte(), 0xD0.toByte(), 0xFE.toByte(),
    0xF7.toByte(), 0xAF.toByte(), 0xFF.toByte(), 0xFC.toByte(), 0xBF.toByte(), 0xFF.toByte(), 0xF4.toByte(), 0x3F.toByte(), 0x01.toByte(),
    0x17.toByte(), 0xF0.toByte(), 0x00.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0xF8.toByte(), 0xDF.toByte(), 0xFF.toByte(),
    0x00.toByte()


)

// index    type                                            data:
// 0-5:    Reference sample                                 5D FF 98 00 FD 0F
//      Sample 0 (aka. reference sample):
//      channel 0: 5D FF => 0xFF5D => -163
private const val accShortSample0Channel0 = -163