package com.polar.androidcommunications.api.ble.model.gatt.client

import org.junit.Assert
import org.junit.Test

class BlePmdClientPpgTest {
    @Test
    fun test_PPG_DataSample_Type0() {
        // Arrange
        val frameType: Byte = 0 //PPG Data Sample 0
        val timeStamp: Long = 0
        val measurementFrame = byteArrayOf(
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(),  //PPG0 (197121)
            0x04.toByte(), 0x05.toByte(), 0x06.toByte(),  //PPG1 (394500)
            0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte(),  //PPG2 (8388607)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(),  //ambient (0)
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),  //PPG0 (-1)
            0x0F.toByte(), 0xEF.toByte(), 0xEF.toByte(),  //PPG1 (-1052913)
            0x00.toByte(), 0x00.toByte(), 0x80.toByte(),  //PPG2 (-8388608)
            0x0F.toByte(), 0xEF.toByte(), 0xEF.toByte()
        )  //ambient (-1052913)
        val ppg0_0 = 197121
        val ppg1_0 = 394500
        val ppg2_0 = 8388607
        val ambient_0 = 0
        val ppg0_1 = -1
        val ppg1_1 = -1052913
        val ppg2_1 = -8388608
        val ambient_1 = -1052913

        // Act
        val ppgData = BlePMDClient.PpgData(measurementFrame, timeStamp, frameType.toInt())

        // Assert
        Assert.assertEquals(2, ppgData.ppgSamples.size)
        Assert.assertEquals(4, ppgData.channels)

        Assert.assertEquals(4, ppgData.ppgSamples[0].ppgDataSamples.size)
        Assert.assertEquals(0, ppgData.ppgSamples[0].status)
        Assert.assertEquals(ppg0_0, ppgData.ppgSamples[0].ppgDataSamples[0])
        Assert.assertEquals(ppg1_0, ppgData.ppgSamples[0].ppgDataSamples[1])
        Assert.assertEquals(ppg2_0, ppgData.ppgSamples[0].ppgDataSamples[2])
        Assert.assertEquals(ambient_0, ppgData.ppgSamples[0].ppgDataSamples[3])

        Assert.assertEquals(4, ppgData.ppgSamples[1].ppgDataSamples.size)
        Assert.assertEquals(0, ppgData.ppgSamples[1].status)
        Assert.assertEquals(ppg0_1, ppgData.ppgSamples[1].ppgDataSamples[0])
        Assert.assertEquals(ppg1_1, ppgData.ppgSamples[1].ppgDataSamples[1])
        Assert.assertEquals(ppg2_1, ppgData.ppgSamples[1].ppgDataSamples[2])
        Assert.assertEquals(ambient_1, ppgData.ppgSamples[1].ppgDataSamples[3])
    }

    @Test
    fun test_PPG_DataSample_Delta() {
        // Arrange
        // HEX: 2C 2D 00 C2 77 00 D3 D2 FF 3D 88 FF 0A 29 B2 F0 EE 34 11 B2 EC EE 74 11 B1 E8 FE B4 11 B1 E8 FE B4 11 B1 E0 FE 34 12 B0 DC 0E 75 12 B0 D8 0E B5 12 AF D4 1E F5 12 AF D0 1E 35 13 AE CC 2E 75 13 AE C8 2E B5 13 AD C4 3E F5 13 AD BC 3E 75 14 AD BC 3E 75 14 AC B8 4E B5 14 AC B4 4E F5 14 AB B0 5E 35 15 AA AC 6E 75 15 AA A8 6E B5 15 AA A4 6E F5 15 A9 A0 7E 35 16 A9 9C 7E 75 16 A8 98 8E B5 16 A7 94 9E F5 16 A7 90 9E 35 17 A7 8C 9E 75 17 A6 88 AE B5 17 A5 88 BE B5 17 A5 80 BE 35 18 A4 7C CE 75 18 A4 78 CE B5 18 A3 78 DE B5 18 A2 70 EE 35 19 A2 6C EE 75 19 A2 6C EE 75 19 A1 68 FE B5 19 A0 60 0E 36 1A 9F 60 1E 36 1A 9F 5C 1E 76 1A 9F 58 1E B6 1A 9D 54 3E F6 1A
        // index    type                                            data:
        // 0-5:    Reference sample                     size 6:    0x2C 0x2D 0x00 0xC2 0x77 0x00 0xD3 0xD2 0xFF 0x3D 0x88 0xFF
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
        // 6:      Delta size                           size 1:    0x0A (10 bits)
        // 7:      Sample amount                        size 1:    0x29 (Delta block contains 41 samples)
        // 8:                                                      0xB2 (binary: 1011 0010)
        // 9:                                                      0xF0 (binary: 1111 00 | 00)
        // 10:                                                     0xEE (binary: 1110 | 1110)
        //      Sample 1 - channel 0, size 10 bits: 00 1011 0010
        //      Sample 1 - channel 1, size 10 bits: 11 1011 1100
        // 11:                                                     0x34 (binary: 00 | 11 0100)
        //      Sample 1 - channel 2, size 10 bits: 11 0100 1110
        // 12:
        //      Sample 1 - channel 3, size 10 bits: 00 0100 0100   0x11 (binary: 0001 0001)
        val refSample1Channel0 = 178
        val refSample1Channel1 = -68
        val refSample1Channel2 = -178
        val refSample1Channel3 = 68
        val amountOfSamples = 1 + 41 // reference sample + delta samples
        val measurementFrame = byteArrayOf(
            0x2C.toByte(),
            0x2D.toByte(),
            0x00.toByte(),
            0xC2.toByte(),
            0x77.toByte(),
            0x00.toByte(),
            0xD3.toByte(),
            0xD2.toByte(),
            0xFF.toByte(),
            0x3D.toByte(),
            0x88.toByte(),
            0xFF.toByte(),
            0x0A.toByte(),
            0x29.toByte(),
            0xB2.toByte(),
            0xF0.toByte(),
            0xEE.toByte(),
            0x34.toByte(),
            0x11.toByte(),
            0xB2.toByte(),
            0xEC.toByte(),
            0xEE.toByte(),
            0x74.toByte(),
            0x11.toByte(),
            0xB1.toByte(),
            0xE8.toByte(),
            0xFE.toByte(),
            0xB4.toByte(),
            0x11.toByte(),
            0xB1.toByte(),
            0xE8.toByte(),
            0xFE.toByte(),
            0xB4.toByte(),
            0x11.toByte(),
            0xB1.toByte(),
            0xE0.toByte(),
            0xFE.toByte(),
            0x34.toByte(),
            0x12.toByte(),
            0xB0.toByte(),
            0xDC.toByte(),
            0x0E.toByte(),
            0x75.toByte(),
            0x12.toByte(),
            0xB0.toByte(),
            0xD8.toByte(),
            0x0E.toByte(),
            0xB5.toByte(),
            0x12.toByte(),
            0xAF.toByte(),
            0xD4.toByte(),
            0x1E.toByte(),
            0xF5.toByte(),
            0x12.toByte(),
            0xAF.toByte(),
            0xD0.toByte(),
            0x1E.toByte(),
            0x35.toByte(),
            0x13.toByte(),
            0xAE.toByte(),
            0xCC.toByte(),
            0x2E.toByte(),
            0x75.toByte(),
            0x13.toByte(),
            0xAE.toByte(),
            0xC8.toByte(),
            0x2E.toByte(),
            0xB5.toByte(),
            0x13.toByte(),
            0xAD.toByte(),
            0xC4.toByte(),
            0x3E.toByte(),
            0xF5.toByte(),
            0x13.toByte(),
            0xAD.toByte(),
            0xBC.toByte(),
            0x3E.toByte(),
            0x75.toByte(),
            0x14.toByte(),
            0xAD.toByte(),
            0xBC.toByte(),
            0x3E.toByte(),
            0x75.toByte(),
            0x14.toByte(),
            0xAC.toByte(),
            0xB8.toByte(),
            0x4E.toByte(),
            0xB5.toByte(),
            0x14.toByte(),
            0xAC.toByte(),
            0xB4.toByte(),
            0x4E.toByte(),
            0xF5.toByte(),
            0x14.toByte(),
            0xAB.toByte(),
            0xB0.toByte(),
            0x5E.toByte(),
            0x35.toByte(),
            0x15.toByte(),
            0xAA.toByte(),
            0xAC.toByte(),
            0x6E.toByte(),
            0x75.toByte(),
            0x15.toByte(),
            0xAA.toByte(),
            0xA8.toByte(),
            0x6E.toByte(),
            0xB5.toByte(),
            0x15.toByte(),
            0xAA.toByte(),
            0xA4.toByte(),
            0x6E.toByte(),
            0xF5.toByte(),
            0x15.toByte(),
            0xA9.toByte(),
            0xA0.toByte(),
            0x7E.toByte(),
            0x35.toByte(),
            0x16.toByte(),
            0xA9.toByte(),
            0x9C.toByte(),
            0x7E.toByte(),
            0x75.toByte(),
            0x16.toByte(),
            0xA8.toByte(),
            0x98.toByte(),
            0x8E.toByte(),
            0xB5.toByte(),
            0x16.toByte(),
            0xA7.toByte(),
            0x94.toByte(),
            0x9E.toByte(),
            0xF5.toByte(),
            0x16.toByte(),
            0xA7.toByte(),
            0x90.toByte(),
            0x9E.toByte(),
            0x35.toByte(),
            0x17.toByte(),
            0xA7.toByte(),
            0x8C.toByte(),
            0x9E.toByte(),
            0x75.toByte(),
            0x17.toByte(),
            0xA6.toByte(),
            0x88.toByte(),
            0xAE.toByte(),
            0xB5.toByte(),
            0x17.toByte(),
            0xA5.toByte(),
            0x88.toByte(),
            0xBE.toByte(),
            0xB5.toByte(),
            0x17.toByte(),
            0xA5.toByte(),
            0x80.toByte(),
            0xBE.toByte(),
            0x35.toByte(),
            0x18.toByte(),
            0xA4.toByte(),
            0x7C.toByte(),
            0xCE.toByte(),
            0x75.toByte(),
            0x18.toByte(),
            0xA4.toByte(),
            0x78.toByte(),
            0xCE.toByte(),
            0xB5.toByte(),
            0x18.toByte(),
            0xA3.toByte(),
            0x78.toByte(),
            0xDE.toByte(),
            0xB5.toByte(),
            0x18.toByte(),
            0xA2.toByte(),
            0x70.toByte(),
            0xEE.toByte(),
            0x35.toByte(),
            0x19.toByte(),
            0xA2.toByte(),
            0x6C.toByte(),
            0xEE.toByte(),
            0x75.toByte(),
            0x19.toByte(),
            0xA2.toByte(),
            0x6C.toByte(),
            0xEE.toByte(),
            0x75.toByte(),
            0x19.toByte(),
            0xA1.toByte(),
            0x68.toByte(),
            0xFE.toByte(),
            0xB5.toByte(),
            0x19.toByte(),
            0xA0.toByte(),
            0x60.toByte(),
            0x0E.toByte(),
            0x36.toByte(),
            0x1A.toByte(),
            0x9F.toByte(),
            0x60.toByte(),
            0x1E.toByte(),
            0x36.toByte(),
            0x1A.toByte(),
            0x9F.toByte(),
            0x5C.toByte(),
            0x1E.toByte(),
            0x76.toByte(),
            0x1A.toByte(),
            0x9F.toByte(),
            0x58.toByte(),
            0x1E.toByte(),
            0xB6.toByte(),
            0x1A.toByte(),
            0x9D.toByte(),
            0x54.toByte(),
            0x3E.toByte(),
            0xF6.toByte(),
            0x1A.toByte()
        )
        val factor = 1.0f
        val channels = 4
        val resolution = 22
        val timeStamp: Long = 0

        // Act
        val ppgData =
            BlePMDClient.PpgData(measurementFrame, factor, resolution, channels, timeStamp)

        // Assert
        Assert.assertEquals(amountOfSamples, ppgData.ppgSamples.size)
        Assert.assertEquals(4, ppgData.channels)
        Assert.assertEquals(4, ppgData.ppgSamples[0].ppgDataSamples.size)
        Assert.assertEquals(0, ppgData.ppgSamples[0].status)
        Assert.assertEquals(
            (factor * refSample0Channel0).toInt(),
            ppgData.ppgSamples[0].ppgDataSamples[0]
        )
        Assert.assertEquals(
            (factor * refSample0Channel1).toInt(),
            ppgData.ppgSamples[0].ppgDataSamples[1]
        )
        Assert.assertEquals(
            (factor * refSample0Channel2).toInt(),
            ppgData.ppgSamples[0].ppgDataSamples[2]
        )
        Assert.assertEquals(
            (factor * refSample0Channel3).toInt(),
            ppgData.ppgSamples[0].ppgDataSamples[3]
        )
        Assert.assertEquals(
            (factor * (refSample0Channel0 + refSample1Channel0)).toInt(),
            ppgData.ppgSamples[1].ppgDataSamples[0]
        )
        Assert.assertEquals(
            (factor * (refSample0Channel1 + refSample1Channel1)).toInt(),
            ppgData.ppgSamples[1].ppgDataSamples[1]
        )
        Assert.assertEquals(
            (factor * (refSample0Channel2 + refSample1Channel2)).toInt(),
            ppgData.ppgSamples[1].ppgDataSamples[2]
        )
        Assert.assertEquals(
            (factor * (refSample0Channel3 + refSample1Channel3)).toInt(),
            ppgData.ppgSamples[1].ppgDataSamples[3]
        )
        Assert.assertEquals(4, ppgData.ppgSamples[1].ppgDataSamples.size)
        Assert.assertEquals(0, ppgData.ppgSamples[1].status)
    }
}