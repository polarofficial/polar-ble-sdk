package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import org.junit.Assert
import org.junit.Test

class PpgDataTest {
    @Test
    fun `test raw PPG frame type 0`() {
        // Arrange
        val frameType = BlePMDClient.PmdDataFrameType.TYPE_0
        val timeStamp: Long = 0
        val measurementFrame = byteArrayOf(
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(),  //PPG0 (197121)
            0x04.toByte(), 0x05.toByte(), 0x06.toByte(),  //PPG1 (394500)
            0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte(),  //PPG2 (8388607)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(),  //ambient (0)
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),  //PPG0 (-1)
            0x0F.toByte(), 0xEF.toByte(), 0xEF.toByte(),  //PPG1 (-1052913)
            0x00.toByte(), 0x00.toByte(), 0x80.toByte(),  //PPG2 (-8388608)
            0x0F.toByte(), 0xEF.toByte(), 0xEF.toByte()   //ambient (-1052913)
        )
        val ppg0Sample0 = 197121
        val ppg1Sample0 = 394500
        val ppg2Sample0 = 8388607
        val ambientSample0 = 0
        val ppg0Sample1 = -1
        val ppg1Sample1 = -1052913
        val ppg2Sample1 = -8388608
        val ambientSample1 = -1052913

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(
            isCompressed = false,
            frameType = frameType,
            frame = measurementFrame,
            factor = 1.0f,
            timeStamp = timeStamp
        )

        // Assert
        Assert.assertEquals(2, ppgData.ppgSamples.size)
        Assert.assertEquals(3, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ppgDataSamples.size)
        Assert.assertEquals(ppg0Sample0, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ppgDataSamples[0])
        Assert.assertEquals(ppg1Sample0, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ppgDataSamples[1])
        Assert.assertEquals(ppg2Sample0, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ppgDataSamples[2])
        Assert.assertEquals(ambientSample0, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ambientSample)

        Assert.assertEquals(3, (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ppgDataSamples.size)
        Assert.assertEquals(ppg0Sample1, (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ppgDataSamples[0])
        Assert.assertEquals(ppg1Sample1, (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ppgDataSamples[1])
        Assert.assertEquals(ppg2Sample1, (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ppgDataSamples[2])
        Assert.assertEquals(ambientSample1, (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ambientSample)
    }

    @Test
    fun `test raw PPG frame type 4`() {
        // Arrange
        // HEX: 00 01 02 03 04 05 06 07 08 09 0A 0B
        //      F8 FF 00 01 00 02 02 00 02 03 00 07
        //      06 02 06 02 06 03 03 04 02 03 01 01
        // index    type                    data:
        // 0..11:   channel1 Gain Ts        00 01 02 03 04 05 06 07 08 09 0A 0B
        // 12..23:  channel2 Gain Ts        F8 FF 00 01 00 02 02 00 02 03 00 07
        // 24..35:  num Int Ts              00 7F 00 00 00 02 02 00 02 03 00 FF

        val frameType = BlePMDClient.PmdDataFrameType.TYPE_4
        val isCompressed = false
        val timeStamp: Long = 0
        val expectedChannel1GainTs0 = 0
        val expectedChannel1GainTs11 = 0x3
        val expectedChannel2GainTs0 = 0
        val expectedChannel2GainTs1 = 7
        val expectedChannel2GainTs11 = 7
        val expectedNumIntTs0 = 0u
        val expectedNumIntTs1 = 127u
        val expectedNumIntTs11 = 0xFFu

        val measurementFrame = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(),
            0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(),
            0xF8.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x02.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x02.toByte(), 0x03.toByte(), 0x00.toByte(), 0x07.toByte(),
            0x00.toByte(), 0x7F.toByte(), 0x06.toByte(), 0x02.toByte(), 0x06.toByte(), 0x03.toByte(), 0x03.toByte(),
            0x04.toByte(), 0x02.toByte(), 0x03.toByte(), 0x01.toByte(), 0xFF.toByte()
        )

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(
            isCompressed = isCompressed,
            frameType = frameType,
            frame = measurementFrame,
            factor = 1.0f,
            timeStamp = timeStamp
        )

        // Assert
        Assert.assertEquals(1, ppgData.ppgSamples.size)
        Assert.assertEquals(12, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).channel1GainTs.size)
        Assert.assertEquals(expectedChannel1GainTs0, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).channel1GainTs[0])
        Assert.assertEquals(expectedChannel1GainTs11, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).channel1GainTs[11])

        Assert.assertEquals(12, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).channel2GainTs.size)
        Assert.assertEquals(expectedChannel2GainTs0, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).channel2GainTs[0])
        Assert.assertEquals(expectedChannel2GainTs1, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).channel2GainTs[1])
        Assert.assertEquals(expectedChannel2GainTs11, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).channel2GainTs[11])

        Assert.assertEquals(12, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).numIntTs.size)
        Assert.assertEquals(expectedNumIntTs0, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).numIntTs[0])
        Assert.assertEquals(expectedNumIntTs1, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).numIntTs[1])
        Assert.assertEquals(expectedNumIntTs11, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType4).numIntTs[11])
    }

    @Test
    fun `test raw PPG frame type 5`() {
        // Arrange
        // HEX: FF FF FF FF
        // index    type                    data:
        // 0..3:    operation mode          FF FF FF FF

        val frameType = BlePMDClient.PmdDataFrameType.TYPE_5
        val isCompressed = false
        val timeStamp: Long = 0
        val expectedOperationMode = 0xFFFFFFFFu

        val measurementFrame = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),)

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(
            isCompressed = isCompressed,
            frameType = frameType,
            frame = measurementFrame,
            factor = 1.0f,
            timeStamp = timeStamp
        )

        // Assert
        Assert.assertEquals(1, ppgData.ppgSamples.size)
        Assert.assertEquals(expectedOperationMode, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleFrameType5).operationMode)
    }

    @Test
    fun `test compressed PPG frame type 0`() {
        // Arrange
        // HEX: 2C 2D 00 C2 77 00 D3 D2 FF 3D 88 FF 0A 29 B2 F0 EE 34 11 B2 EC EE 74 11 B1 E8 FE B4 11 B1 E8 FE B4 11 B1 E0 FE 34 12 B0 DC 0E 75 12 B0 D8 0E B5 12 AF D4 1E F5 12 AF D0 1E 35 13 AE CC 2E 75 13 AE C8 2E B5 13 AD C4 3E F5 13 AD BC 3E 75 14 AD BC 3E 75 14 AC B8 4E B5 14 AC B4 4E F5 14 AB B0 5E 35 15 AA AC 6E 75 15 AA A8 6E B5 15 AA A4 6E F5 15 A9 A0 7E 35 16 A9 9C 7E 75 16 A8 98 8E B5 16 A7 94 9E F5 16 A7 90 9E 35 17 A7 8C 9E 75 17 A6 88 AE B5 17 A5 88 BE B5 17 A5 80 BE 35 18 A4 7C CE 75 18 A4 78 CE B5 18 A3 78 DE B5 18 A2 70 EE 35 19 A2 6C EE 75 19 A2 6C EE 75 19 A1 68 FE B5 19 A0 60 0E 36 1A 9F 60 1E 36 1A 9F 5C 1E 76 1A 9F 58 1E B6 1A 9D 54 3E F6 1A
        // index    type                                    data:
        // 0-11:    Reference sample                        0x2C 0x2D 0x00 0xC2 0x77 0x00 0xD3 0xD2 0xFF 0x3D 0x88 0xFF
        //      Sample 0 (aka. reference sample):
        //      channel 0: 2C 2D 00 => 0x002D2C => 11564
        val refSample0Channel0 = 11564
        //      channel 1: C2 77 00 => 0x0077C2 => 30658
        val refSample0Channel1 = 30658
        //      channel 2: D3 D2 FF => 0xFFD2D3 => -11565
        val refSample0Channel2 = -11565
        //      channel 3: 3D 88 FF => 0xFF883D => -30659
        val refSample0Channel3 = -30659
        // Delta dump: 0A 29 | B2 F0 EE 34 11 B2 EC EE 74 11 B1 E8 FE B4 11 B1 ...
        // 12:      Delta size                           size 1:    0x0A (10 bits)
        // 13:      Sample amount                        size 1:    0x29 (Delta block contains 41 samples)
        // 14:                                                      0xB2 (binary: 1011 0010)
        // 15:                                                      0xF0 (binary: 1111 00 | 00)
        // 16:                                                     0xEE (binary: 1110 | 1110)
        //      Sample 1 - channel 0, size 10 bits: 00 1011 0010
        //      Sample 1 - channel 1, size 10 bits: 11 1011 1100
        // 17:                                                     0x34 (binary: 00 | 11 0100)
        //      Sample 1 - channel 2, size 10 bits: 11 0100 1110
        // 18:
        //      Sample 1 - channel 3, size 10 bits: 00 0100 0100   0x11 (binary: 0001 0001)
        val refSample1Channel0 = 178
        val refSample1Channel1 = -68
        val refSample1Channel2 = -178
        val refSample1Channel3 = 68
        val amountOfSamples = 1 + 41 // reference sample + delta samples
        val measurementFrame = byteArrayOf(
            0x2C.toByte(), 0x2D.toByte(), 0x00.toByte(), 0xC2.toByte(), 0x77.toByte(), 0x00.toByte(), 0xD3.toByte(), 0xD2.toByte(), 0xFF.toByte(),
            0x3D.toByte(), 0x88.toByte(), 0xFF.toByte(), 0x0A.toByte(), 0x29.toByte(), 0xB2.toByte(), 0xF0.toByte(), 0xEE.toByte(), 0x34.toByte(),
            0x11.toByte(), 0xB2.toByte(), 0xEC.toByte(), 0xEE.toByte(), 0x74.toByte(), 0x11.toByte(), 0xB1.toByte(), 0xE8.toByte(), 0xFE.toByte(),
            0xB4.toByte(), 0x11.toByte(), 0xB1.toByte(), 0xE8.toByte(), 0xFE.toByte(), 0xB4.toByte(), 0x11.toByte(), 0xB1.toByte(), 0xE0.toByte(),
            0xFE.toByte(), 0x34.toByte(), 0x12.toByte(), 0xB0.toByte(), 0xDC.toByte(), 0x0E.toByte(), 0x75.toByte(), 0x12.toByte(), 0xB0.toByte(),
            0xD8.toByte(), 0x0E.toByte(), 0xB5.toByte(), 0x12.toByte(), 0xAF.toByte(), 0xD4.toByte(), 0x1E.toByte(), 0xF5.toByte(), 0x12.toByte(),
            0xAF.toByte(), 0xD0.toByte(), 0x1E.toByte(), 0x35.toByte(), 0x13.toByte(), 0xAE.toByte(), 0xCC.toByte(), 0x2E.toByte(), 0x75.toByte(),
            0x13.toByte(), 0xAE.toByte(), 0xC8.toByte(), 0x2E.toByte(), 0xB5.toByte(), 0x13.toByte(), 0xAD.toByte(), 0xC4.toByte(), 0x3E.toByte(),
            0xF5.toByte(), 0x13.toByte(), 0xAD.toByte(), 0xBC.toByte(), 0x3E.toByte(), 0x75.toByte(), 0x14.toByte(), 0xAD.toByte(), 0xBC.toByte(),
            0x3E.toByte(), 0x75.toByte(), 0x14.toByte(), 0xAC.toByte(), 0xB8.toByte(), 0x4E.toByte(), 0xB5.toByte(), 0x14.toByte(), 0xAC.toByte(),
            0xB4.toByte(), 0x4E.toByte(), 0xF5.toByte(), 0x14.toByte(), 0xAB.toByte(), 0xB0.toByte(), 0x5E.toByte(), 0x35.toByte(), 0x15.toByte(),
            0xAA.toByte(), 0xAC.toByte(), 0x6E.toByte(), 0x75.toByte(), 0x15.toByte(), 0xAA.toByte(), 0xA8.toByte(), 0x6E.toByte(), 0xB5.toByte(),
            0x15.toByte(), 0xAA.toByte(), 0xA4.toByte(), 0x6E.toByte(), 0xF5.toByte(), 0x15.toByte(), 0xA9.toByte(), 0xA0.toByte(), 0x7E.toByte(),
            0x35.toByte(), 0x16.toByte(), 0xA9.toByte(), 0x9C.toByte(), 0x7E.toByte(), 0x75.toByte(), 0x16.toByte(), 0xA8.toByte(), 0x98.toByte(),
            0x8E.toByte(), 0xB5.toByte(), 0x16.toByte(), 0xA7.toByte(), 0x94.toByte(), 0x9E.toByte(), 0xF5.toByte(), 0x16.toByte(), 0xA7.toByte(),
            0x90.toByte(), 0x9E.toByte(), 0x35.toByte(), 0x17.toByte(), 0xA7.toByte(), 0x8C.toByte(), 0x9E.toByte(), 0x75.toByte(), 0x17.toByte(),
            0xA6.toByte(), 0x88.toByte(), 0xAE.toByte(), 0xB5.toByte(), 0x17.toByte(), 0xA5.toByte(), 0x88.toByte(), 0xBE.toByte(), 0xB5.toByte(),
            0x17.toByte(), 0xA5.toByte(), 0x80.toByte(), 0xBE.toByte(), 0x35.toByte(), 0x18.toByte(), 0xA4.toByte(), 0x7C.toByte(), 0xCE.toByte(),
            0x75.toByte(), 0x18.toByte(), 0xA4.toByte(), 0x78.toByte(), 0xCE.toByte(), 0xB5.toByte(), 0x18.toByte(), 0xA3.toByte(), 0x78.toByte(),
            0xDE.toByte(), 0xB5.toByte(), 0x18.toByte(), 0xA2.toByte(), 0x70.toByte(), 0xEE.toByte(), 0x35.toByte(), 0x19.toByte(), 0xA2.toByte(),
            0x6C.toByte(), 0xEE.toByte(), 0x75.toByte(), 0x19.toByte(), 0xA2.toByte(), 0x6C.toByte(), 0xEE.toByte(), 0x75.toByte(), 0x19.toByte(),
            0xA1.toByte(), 0x68.toByte(), 0xFE.toByte(), 0xB5.toByte(), 0x19.toByte(), 0xA0.toByte(), 0x60.toByte(), 0x0E.toByte(), 0x36.toByte(),
            0x1A.toByte(), 0x9F.toByte(), 0x60.toByte(), 0x1E.toByte(), 0x36.toByte(), 0x1A.toByte(), 0x9F.toByte(), 0x5C.toByte(), 0x1E.toByte(),
            0x76.toByte(), 0x1A.toByte(), 0x9F.toByte(), 0x58.toByte(), 0x1E.toByte(), 0xB6.toByte(), 0x1A.toByte(), 0x9D.toByte(), 0x54.toByte(),
            0x3E.toByte(), 0xF6.toByte(), 0x1A.toByte()
        )
        val factor = 1.0f
        val timeStamp: Long = 0

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(true, BlePMDClient.PmdDataFrameType.TYPE_0, measurementFrame, factor, timeStamp)

        // Assert
        Assert.assertEquals(amountOfSamples, ppgData.ppgSamples.size)
        Assert.assertEquals(3, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ppgDataSamples.size)
        Assert.assertEquals((factor * refSample0Channel0).toInt(), (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ppgDataSamples[0])
        Assert.assertEquals((factor * refSample0Channel1).toInt(), (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ppgDataSamples[1])
        Assert.assertEquals((factor * refSample0Channel2).toInt(), (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ppgDataSamples[2])
        Assert.assertEquals((factor * refSample0Channel3).toInt(), (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType0).ambientSample)

        Assert.assertEquals(3, (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ppgDataSamples.size)
        Assert.assertEquals((factor * (refSample0Channel0 + refSample1Channel0)).toInt(), (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ppgDataSamples[0])
        Assert.assertEquals((factor * (refSample0Channel1 + refSample1Channel1)).toInt(), (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ppgDataSamples[1])
        Assert.assertEquals((factor * (refSample0Channel2 + refSample1Channel2)).toInt(), (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ppgDataSamples[2])
        Assert.assertEquals((factor * (refSample0Channel3 + refSample1Channel3)).toInt(), (ppgData.ppgSamples[1] as PpgData.PpgDataSampleType0).ambientSample)
    }

    @Test
    fun `test compressed PPG frame type 7`() {
        //HEX: 09 AE 20
        // 8A 7C 23   0
        // 58 3B 19   1
        // 80 B9 18   2
        // DB 2E 22   3
        // FA 88 1D   4
        // D7 BB 18   5
        // C2 B8 1F   6
        // 7A 44 26   7
        // 48 DF 23   8
        // A1 F2 17   9
        // 3A 37 1B   10
        // FF FF 00   11
        // 7C 08 00   12
        // 70 F5 02   13
        // FF FF 00   15
        // 28 C4 11   16
        // 18
        // 01
        // 87
        // FE FF EC 02 00 01 FE FF FB 00 00 8D F9 FF B1 FF FF 0C FE FF 26 FE FF EE FE FF 89 02 00 DF 02 00 51 FE FF 00 00 00 00 00 00 00 00 00 00 00 00 AE A6 8F
        // index    type                                            data:
        // 0..3  Sample 0 - channel 0 (ref. sample)                 09 AE 20 (0x20AE09 = 2141705)
        // 0..3  Sample 0 - channel 15 (ref. sample)                FF FF 00 (0x00FFFF = 65535)
        // 0..3  Sample 0 - status (ref. sample)                    28 C4 11 (0x11C428 = 1164328)
        val amountOfSamples = 1 + 1 // reference sample + delta samples
        val refSample0Channel0 = 2141705
        val refSample0Channel15 = 65535
        val refSample0ChannelStatus = 1164328u

        val factor = 1.0f
        val timeStamp = 0xFFFL
        val measurementFrame = byteArrayOf(
            0x09.toByte(),
            0xAE.toByte(),
            0x20.toByte(),
            0x8A.toByte(),
            0x7C.toByte(),
            0x23.toByte(),
            0x58.toByte(),
            0x3B.toByte(),
            0x19.toByte(),
            0x80.toByte(),
            0xB9.toByte(),
            0x18.toByte(),
            0xDB.toByte(),
            0x2E.toByte(),
            0x22.toByte(),
            0xFA.toByte(),
            0x88.toByte(),
            0x1D.toByte(),
            0xD7.toByte(),
            0xBB.toByte(),
            0x18.toByte(),
            0xC2.toByte(),
            0xB8.toByte(),
            0x1F.toByte(),
            0x7A.toByte(),
            0x44.toByte(),
            0x26.toByte(),
            0x48.toByte(),
            0xDF.toByte(),
            0x23.toByte(),
            0xA1.toByte(),
            0xF2.toByte(),
            0x17.toByte(),
            0x3A.toByte(),
            0x37.toByte(),
            0x1B.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x00.toByte(),
            0x7C.toByte(),
            0x08.toByte(),
            0x00.toByte(),
            0x70.toByte(),
            0xF5.toByte(),
            0x02.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x00.toByte(),
            0x28.toByte(),
            0xC4.toByte(),
            0x11.toByte(),
            0x18.toByte(),
            0x01.toByte(),
            0x87.toByte(),
            0xFE.toByte(),
            0xFF.toByte(),
            0xEC.toByte(),
            0x02.toByte(),
            0x00.toByte(),
            0x01.toByte(),
            0xFE.toByte(),
            0xFF.toByte(),
            0xFB.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x8D.toByte(),
            0xF9.toByte(),
            0xFF.toByte(),
            0xB1.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x0C.toByte(),
            0xFE.toByte(),
            0xFF.toByte(),
            0x26.toByte(),
            0xFE.toByte(),
            0xFF.toByte(),
            0xEE.toByte(),
            0xFE.toByte(),
            0xFF.toByte(),
            0x89.toByte(),
            0x02.toByte(),
            0x00.toByte(),
            0xDF.toByte(),
            0x02.toByte(),
            0x00.toByte(),
            0x51.toByte(),
            0xFE.toByte(),
            0xFF.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0xAE.toByte(),
            0xA6.toByte(),
            0x8F.toByte()
        )
        // Act
        val ppgData = PpgData.parseDataFromDataFrame(true, BlePMDClient.PmdDataFrameType.TYPE_7, measurementFrame, factor, timeStamp)

        // Assert
        Assert.assertEquals(amountOfSamples, ppgData.ppgSamples.size)
        Assert.assertEquals(16, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType2).ppgDataSamples.size)
        Assert.assertEquals(refSample0Channel0, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType2).ppgDataSamples[0])
        Assert.assertEquals(refSample0Channel15, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType2).ppgDataSamples[15])
        Assert.assertEquals(refSample0ChannelStatus, (ppgData.ppgSamples[0] as PpgData.PpgDataSampleType2).status)
    }
}