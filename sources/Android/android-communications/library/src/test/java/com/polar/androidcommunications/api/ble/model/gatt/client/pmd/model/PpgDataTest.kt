package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import org.hamcrest.Matchers.equalTo
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class PpgDataTest {
    @Test
    fun `test raw PPG frame type 0`() {
        // Arrange
        // HEX: 01 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           01 (PPG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     00 (raw, type 0)
        val ppgDataFrameHeader = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 100uL
        val ppgDataFrameContent = byteArrayOf(
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

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = ppgDataFrameHeader + ppgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(2, ppgData.ppgSamples.size)
        Assert.assertEquals(3, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ppgDataSamples.size)
        Assert.assertEquals(ppg0Sample0, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ppgDataSamples[0])
        Assert.assertEquals(ppg1Sample0, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ppgDataSamples[1])
        Assert.assertEquals(ppg2Sample0, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ppgDataSamples[2])
        Assert.assertEquals(ambientSample0, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ambientSample)

        Assert.assertEquals(3, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ppgDataSamples.size)
        Assert.assertEquals(ppg0Sample1, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ppgDataSamples[0])
        Assert.assertEquals(ppg1Sample1, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ppgDataSamples[1])
        Assert.assertEquals(ppg2Sample1, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ppgDataSamples[2])
        Assert.assertEquals(ambientSample1, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ambientSample)

        Assert.assertEquals(timeStamp, (ppgData.ppgSamples.last() as PpgData.PpgDataFrameType0).timeStamp)
    }

    @Test
    fun `test raw PPG frame type 4`() {
        // Arrange
        // HEX: 01 00 94 35 77 00 00 00 00 04
        // index                                                   data:
        // 0        type                                           01 (PPG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     04 (raw, type 4)
        val ppgDataFrameHeader = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x04.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: 02 7F 06 02 06 04 02 06 03 02 01 FF
        //      01 07 FF 00 00 00 00 00 00 00 00 01
        //      00 7F 00 00 00 00 00 00 01 02 EF F8
        // index    type                    data:
        // 0..11:   num Int Ts1-12          02 7F 06 02 06 04 02 06 03 02 01 FF
        // 12:  channel1 Gain Ts1           01 => 1
        // 13:  channel2 Gain Ts1           07 => 7
        // 14:  channel1 Gain Ts2           FF => 7
        // 15:  channel2 Gain Ts2           00 => 0
        // ..
        // 32: channel1 Gain Ts11           01 => 1
        // 33: channel2 Gain Ts11           02 => 2
        // 34: channel1 Gain Ts12           EF => 7
        // 35: channel2 Gain Ts12           F8 => 0
        val expectedNumIntTs1 = 2u
        val expectedNumIntTs2 = 0x7Fu
        val expectedNumIntTs12 = 0xFFu

        val expectedChannel1GainTs1 = 1u
        val expectedChannel2GainTs1 = 7u
        val expectedChannel1GainTs2 = 7u
        val expectedChannel2GainTs2 = 0u
        val expectedChannel1GainTs11 = 1u
        val expectedChannel2GainTs11 = 2u
        val expectedChannel1GainTs12 = 7u
        val expectedChannel2GainTs12 = 0u

        val ppgDataFrameContent = byteArrayOf(
            0x02.toByte(), 0x7F.toByte(), 0x06.toByte(), 0x02.toByte(), 0x06.toByte(), 0x04.toByte(), 0x02.toByte(), 0x06.toByte(), 0x03.toByte(), 0x02.toByte(), 0x01.toByte(), 0xFF.toByte(),
            0x01.toByte(), 0x07.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0xF8.toByte(), 0x7F.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0xEF.toByte(), 0xF8.toByte(),
        )
        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = ppgDataFrameHeader + ppgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }


        // Act
        val ppgData = PpgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(1, ppgData.ppgSamples.size)

        Assert.assertEquals(12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).numIntTs.size)
        Assert.assertEquals(expectedNumIntTs1, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).numIntTs[0])
        Assert.assertEquals(expectedNumIntTs2, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).numIntTs[1])
        Assert.assertEquals(expectedNumIntTs12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).numIntTs[11])

        Assert.assertEquals(12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel1GainTs.size)
        Assert.assertEquals(expectedChannel1GainTs1, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel1GainTs[0])
        Assert.assertEquals(expectedChannel1GainTs2, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel1GainTs[1])
        Assert.assertEquals(expectedChannel1GainTs11, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel1GainTs[10])
        Assert.assertEquals(expectedChannel1GainTs12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel1GainTs[11])

        Assert.assertEquals(12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel2GainTs.size)
        Assert.assertEquals(expectedChannel2GainTs1, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel2GainTs[0])
        Assert.assertEquals(expectedChannel2GainTs2, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel2GainTs[1])
        Assert.assertEquals(expectedChannel2GainTs11, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel2GainTs[10])
        Assert.assertEquals(expectedChannel2GainTs12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType4).channel2GainTs[11])

        Assert.assertEquals(timeStamp, (ppgData.ppgSamples.last() as PpgData.PpgDataFrameType4).timeStamp)
    }

    @Test
    fun `test raw PPG frame type 5`() {
        // Arrange
        // HEX: 01 00 94 35 77 00 00 00 00 05
        // index                                                   data:
        // 0        type                                           01 (PPG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     05 (raw, type 5)
        val ppgDataFrameHeader = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x05.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: FF FF FF FF
        // index    type                    data:
        // 0..3:    operation mode          FF FF FF FF
        val expectedOperationMode = 0xFFFFFFFFu

        val ppgDataFrameContent = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = ppgDataFrameHeader + ppgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(1, ppgData.ppgSamples.size)
        Assert.assertEquals(expectedOperationMode, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType5).operationMode)

        Assert.assertEquals(timeStamp, (ppgData.ppgSamples.last() as PpgData.PpgDataFrameType5).timeStamp)

    }

    @Test
    fun `test raw PPG frame type 9`() {
        // Arrange
        // HEX: 01 00 94 35 77 00 00 00 00 09
        // index                                                   data:
        // 0        type                                           01 (PPG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     04 (raw, type 9)
        val ppgDataFrameHeader = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x09.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: 06 06 06 06 06 06 06 06 06 06 06 06
        //      FF 00 00 00 00 00 00 00 00 00 00 FF
        //      01 00 00 00 00 00 00 00 00 00 FF FF
        // index    type                    data:
        // 0..11:   num Int Ts1-12          06 06 06 06 06 06 06 06 06 06 06 06
        // 12:  channel1 Gain Ts1           01 => 1
        // 13:  channel2 Gain Ts1           07 => 7
        // 14:  channel1 Gain Ts2           FF => 7
        // 15:  channel2 Gain Ts2           00 => 0
        // ..
        // 32: channel1 Gain Ts11           01 => 1
        // 33: channel2 Gain Ts11           02 => 2
        // 34: channel1 Gain Ts12           EF => 7
        // 35: channel2 Gain Ts12           F8 => 0

        val expectedNumIntTs1 = 6u
        val expectedNumIntTs2 = 0x6u
        val expectedNumIntTs12 = 0x6u

        val expectedChannel1GainTs1 = 7u
        val expectedChannel2GainTs1 = 0u
        val expectedChannel1GainTs2 = 0u
        val expectedChannel2GainTs2 = 0u
        val expectedChannel1GainTs11 = 0u
        val expectedChannel2GainTs11 = 0u
        val expectedChannel1GainTs12 = 7u
        val expectedChannel2GainTs12 = 7u

        val ppgDataFrameContent = byteArrayOf(
            0x06.toByte(), 0x06.toByte(), 0x06.toByte(), 0x06.toByte(), 0x06.toByte(), 0x06.toByte(), 0x06.toByte(), 0x06.toByte(), 0x06.toByte(), 0x06.toByte(), 0x06.toByte(), 0x06.toByte(),
            0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = ppgDataFrameHeader + ppgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(1, ppgData.ppgSamples.size)

        Assert.assertEquals(12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).numIntTs.size)
        Assert.assertEquals(expectedNumIntTs1, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).numIntTs[0])
        Assert.assertEquals(expectedNumIntTs2, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).numIntTs[1])
        Assert.assertEquals(expectedNumIntTs12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).numIntTs[11])

        Assert.assertEquals(12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel1GainTs.size)
        Assert.assertEquals(expectedChannel1GainTs1, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel1GainTs[0])
        Assert.assertEquals(expectedChannel1GainTs2, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel1GainTs[1])
        Assert.assertEquals(expectedChannel1GainTs11, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel1GainTs[10])
        Assert.assertEquals(expectedChannel1GainTs12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel1GainTs[11])

        Assert.assertEquals(12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel2GainTs.size)
        Assert.assertEquals(expectedChannel2GainTs1, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel2GainTs[0])
        Assert.assertEquals(expectedChannel2GainTs2, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel2GainTs[1])
        Assert.assertEquals(expectedChannel2GainTs11, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel2GainTs[10])
        Assert.assertEquals(expectedChannel2GainTs12, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType9).channel2GainTs[11])

        Assert.assertEquals(timeStamp, (ppgData.ppgSamples.last() as PpgData.PpgDataFrameType9).timeStamp)
    }

    @Test
    fun `test compressed PPG frame type 0`() {
        // Arrange
        // HEX: 01 00 94 35 77 00 00 00 00 80
        // index                                                   data:
        // 0        type                                           01 (PPG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     80 (compressed, type 0)
        val ppgDataFrameHeader = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: 2C 2D 00 C2 77 00 D3 D2 FF 3D 88 FF 0A 29 B2 F0 EE 34 11 B2 EC EE 74 11 B1 E8 FE B4 11 B1 E8 FE B4 11 B1 E0 FE 34 12 B0 DC 0E 75 12 B0 D8 0E B5 12 AF D4 1E F5 12 AF D0 1E 35 13 AE CC 2E 75 13 AE C8 2E B5 13 AD C4 3E F5 13 AD BC 3E 75 14 AD BC 3E 75 14 AC B8 4E B5 14 AC B4 4E F5 14 AB B0 5E 35 15 AA AC 6E 75 15 AA A8 6E B5 15 AA A4 6E F5 15 A9 A0 7E 35 16 A9 9C 7E 75 16 A8 98 8E B5 16 A7 94 9E F5 16 A7 90 9E 35 17 A7 8C 9E 75 17 A6 88 AE B5 17 A5 88 BE B5 17 A5 80 BE 35 18 A4 7C CE 75 18 A4 78 CE B5 18 A3 78 DE B5 18 A2 70 EE 35 19 A2 6C EE 75 19 A2 6C EE 75 19 A1 68 FE B5 19 A0 60 0E 36 1A 9F 60 1E 36 1A 9F 5C 1E 76 1A 9F 58 1E B6 1A 9D 54 3E F6 1A
        // index    type                                    data:
        // 0..11:    Reference sample                       0x2C 0x2D 0x00 0xC2 0x77 0x00 0xD3 0xD2 0xFF 0x3D 0x88 0xFF
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
        val ppgDataFrameContent = byteArrayOf(
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
        val dataFrame = PmdDataFrame(
            data = ppgDataFrameHeader + ppgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(amountOfSamples, ppgData.ppgSamples.size)
        Assert.assertEquals(3, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ppgDataSamples.size)
        Assert.assertEquals((factor * refSample0Channel0).toInt(), (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ppgDataSamples[0])
        Assert.assertEquals((factor * refSample0Channel1).toInt(), (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ppgDataSamples[1])
        Assert.assertEquals((factor * refSample0Channel2).toInt(), (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ppgDataSamples[2])
        Assert.assertEquals((factor * refSample0Channel3).toInt(), (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType0).ambientSample)

        Assert.assertEquals(3, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ppgDataSamples.size)
        Assert.assertEquals((factor * (refSample0Channel0 + refSample1Channel0)).toInt(), (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ppgDataSamples[0])
        Assert.assertEquals((factor * (refSample0Channel1 + refSample1Channel1)).toInt(), (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ppgDataSamples[1])
        Assert.assertEquals((factor * (refSample0Channel2 + refSample1Channel2)).toInt(), (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ppgDataSamples[2])
        Assert.assertEquals((factor * (refSample0Channel3 + refSample1Channel3)).toInt(), (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType0).ambientSample)

        Assert.assertEquals(timeStamp, (ppgData.ppgSamples.last() as PpgData.PpgDataFrameType0).timeStamp)
    }

    @Test
    fun `test compressed PPG frame type 7`() {
        // Arrange
        // HEX: 01 00 94 35 77 00 00 00 00 87
        // index                                                   data:
        // 0        type                                           01 (PPG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     87 (compressed, type 7)
        val ppgDataFrameHeader = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x87.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX:
        // 09 AE 20   0
        // 8A 7C 23   1
        // 58 3B 19   2
        // 80 B9 18   3
        // DB 2E 22   4
        // FA 88 1D   5
        // D7 BB 18   6
        // C2 B8 1F   7
        // 7A 44 26   8
        // 48 DF 23   9
        // A1 F2 17   10
        // 3A 37 1B   11
        // FF FF 00   12
        // 7C 08 00   13
        // 70 F5 02   14
        // FF FF 00   15
        // 28 C4 11   16
        // 18
        // 01
        // 87 FE FF EC 02 00 01 FE FF FB 00 00 8D F9 FF B1 FF FF 0C FE FF 26 FE FF EE FE FF 89 02 00 DF 02 00 51 FE FF 00 00 00 00 00 00 00 00 00 00 00 00 AE A6 8F

        // index    type                                    data:
        // 0..50:   Reference sample                        09 AE 20 ...
        //      Sample 0 (aka. reference sample):
        //      channel 0: 09 AE 20 => 0x20AE09 = 2141705
        val sample0Channel0 = 2141705
        //      channel 1: 8A 7C 23 => 0x237C8A = 2325642
        val sample0Channel1 = 2325642
        //      channel 15: FF FF 00 => 0x00FFFF = 65535
        val sample0Channel15 = 65535
        //      status: 28 C4 11 => 0x11C428 = 1164328
        val sample0ChannelStatus = 1164328u
        // 51:      Delta size
        // 52:      Samples amount
        // Delta dump: 18 01 | 87 FE FF EC 02 00 01 FE ...
        // 51:      Delta size                           size 1:    0x18 (24 bits)
        val amountOfSamples = 1 + 1 // reference sample + delta samples
        // 52:      Sample amount                        size 1:    0x01 (Delta block contains 1 sample)
        // 53..55:                                                  0x87 0xFE 0xFF (0xFFFE87 = -377)
        val sample1Channel0 = sample0Channel0 - 377
        // 56..58:                                                  0xEC 0x02 0x00 (0x0002EC = 748)
        val sample1Channel1 = sample0Channel1 + 748

        val factor = 1.0f
        val ppgDataFrameContent = byteArrayOf(
            0x09.toByte(), 0xAE.toByte(), 0x20.toByte(), 0x8A.toByte(), 0x7C.toByte(), 0x23.toByte(), 0x58.toByte(), 0x3B.toByte(), 0x19.toByte(),
            0x80.toByte(), 0xB9.toByte(), 0x18.toByte(), 0xDB.toByte(), 0x2E.toByte(), 0x22.toByte(), 0xFA.toByte(), 0x88.toByte(), 0x1D.toByte(),
            0xD7.toByte(), 0xBB.toByte(), 0x18.toByte(), 0xC2.toByte(), 0xB8.toByte(), 0x1F.toByte(), 0x7A.toByte(), 0x44.toByte(), 0x26.toByte(),
            0x48.toByte(), 0xDF.toByte(), 0x23.toByte(), 0xA1.toByte(), 0xF2.toByte(), 0x17.toByte(), 0x3A.toByte(), 0x37.toByte(), 0x1B.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x7C.toByte(), 0x08.toByte(), 0x00.toByte(), 0x70.toByte(), 0xF5.toByte(), 0x02.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x28.toByte(), 0xC4.toByte(), 0x11.toByte(), 0x18.toByte(), 0x01.toByte(), 0x87.toByte(),
            0xFE.toByte(), 0xFF.toByte(), 0xEC.toByte(), 0x02.toByte(), 0x00.toByte(), 0x01.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xFB.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x8D.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xB1.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0C.toByte(),
            0xFE.toByte(), 0xFF.toByte(), 0x26.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xEE.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0x89.toByte(),
            0x02.toByte(), 0x00.toByte(), 0xDF.toByte(), 0x02.toByte(), 0x00.toByte(), 0x51.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xAE.toByte(), 0xA6.toByte(), 0x8F.toByte()
        )

        val dataFrame = PmdDataFrame(
            data = ppgDataFrameHeader + ppgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(amountOfSamples, ppgData.ppgSamples.size)
        Assert.assertEquals(16, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType7).ppgDataSamples.size)
        Assert.assertEquals(sample0Channel0, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType7).ppgDataSamples[0])
        Assert.assertEquals(sample0Channel1, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType7).ppgDataSamples[1])
        Assert.assertEquals(sample0Channel15, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType7).ppgDataSamples[15])
        Assert.assertEquals(sample0ChannelStatus, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType7).status)
        Assert.assertEquals(sample1Channel0, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType7).ppgDataSamples[0])
        Assert.assertEquals(sample1Channel1, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType7).ppgDataSamples[1])

        Assert.assertEquals(timeStamp, (ppgData.ppgSamples.last() as PpgData.PpgDataFrameType7).timeStamp)
    }

    @Test
    fun `test compressed PPG frame type 8`() {
        // Arrange
        // HEX: 01 00 94 35 77 00 00 00 00 88
        // index                                                   data:
        // 0        type                                           01 (PPG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     80 (compressed, type 8)
        val ppgDataFrameHeader = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x88.toByte(),
        )
        val previousTimeStamp = 100uL
        //HEX:
        // 03 10 01 //0
        // 05 10 02 //1
        // FF FF FF //2
        // 09 10 04 //3
        // 0B 10 05 //4
        // 03 10 06 //5
        // 05 10 07 //6
        // 07 10 08 //7
        // 09 10 09 //8
        // 0B 10 0A //9
        // 03 10 0B //10
        // 05 10 0C //11
        // 07 10 0D //12
        // 09 10 0E //13
        // 0B 10 0F //14
        // 03 10 10 //15
        // 05 10 11 //16
        // 07 10 12 //17
        // 09 10 13 //18
        // 0B 10 14 //19
        // 03 10 15 //20
        // 05 10 16 //21
        // 07 10 17 //22
        // 09 10 18 //23
        // FF FF FF //24
        // 03
        // 01
        // 49 92 24 49 ...

        // index    type                                    data:
        // 0..74:   Reference sample                        03 10 01 ...
        //      Sample 0 (aka. reference sample):
        //      channel 0: 03 10 01 => 0x011003 = 69635
        val sample0Channel0 = 69635
        //      channel 1: 05 10 02 => 0x021005 = 135173
        val sample0Channel1 = 135173
        //      channel 2: FF FF FF => 0xFFFFFF = -1
        val sample0Channel2 = -1
        //      channel 23: 09 10 18 => 0x181009 = 1576969
        val sample0Channel23 = 1576969
        //      status: FF FF FF => 0xFFFFFF = 16777215
        val sample0ChannelStatus = 16777215u
        // Delta dump: 03 01 | 49 92 24 49 92 ...
        // 75:      Delta size                           size 1:    0x03 (3 bits)
        // 76:      Sample amount                        size 1:    0x01 (Delta block contains 1 sample)
        val amountOfSamples = 1 + 1 // reference sample + delta samples
        // 77:                                                      0x49( 01 | 001 | 001
        val sample1Channel0 = sample0Channel0 + 1
        val sample1Channel1 = sample0Channel1 + 1

        val ppgDataFrameContent = byteArrayOf(
            0x03.toByte(), 0x10.toByte(), 0x01.toByte(), 0x05.toByte(), 0x10.toByte(), 0x02.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x09.toByte(), 0x10.toByte(), 0x04.toByte(), 0x0B.toByte(), 0x10.toByte(), 0x05.toByte(), 0x03.toByte(), 0x10.toByte(), 0x06.toByte(),
            0x05.toByte(), 0x10.toByte(), 0x07.toByte(), 0x07.toByte(), 0x10.toByte(), 0x08.toByte(), 0x09.toByte(), 0x10.toByte(), 0x09.toByte(),
            0x0B.toByte(), 0x10.toByte(), 0x0A.toByte(), 0x03.toByte(), 0x10.toByte(), 0x0B.toByte(), 0x05.toByte(), 0x10.toByte(), 0x0C.toByte(),
            0x07.toByte(), 0x10.toByte(), 0x0D.toByte(), 0x09.toByte(), 0x10.toByte(), 0x0E.toByte(), 0x0B.toByte(), 0x10.toByte(), 0x0F.toByte(),
            0x03.toByte(), 0x10.toByte(), 0x10.toByte(), 0x05.toByte(), 0x10.toByte(), 0x11.toByte(), 0x07.toByte(), 0x10.toByte(), 0x12.toByte(),
            0x09.toByte(), 0x10.toByte(), 0x13.toByte(), 0x0B.toByte(), 0x10.toByte(), 0x14.toByte(), 0x03.toByte(), 0x10.toByte(), 0x15.toByte(),
            0x05.toByte(), 0x10.toByte(), 0x16.toByte(), 0x07.toByte(), 0x10.toByte(), 0x17.toByte(), 0x09.toByte(), 0x10.toByte(), 0x18.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x03.toByte(), 0x01.toByte(), 0x49.toByte(), 0x92.toByte(), 0x24.toByte(), 0x49.toByte(),
            0x92.toByte(), 0x24.toByte(), 0x49.toByte(), 0x92.toByte(), 0x24.toByte(), 0x00.toByte(),
        )
        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = ppgDataFrameHeader + ppgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ppgData = PpgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(amountOfSamples, ppgData.ppgSamples.size)
        Assert.assertEquals(24, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType8).ppgDataSamples.size)
        Assert.assertEquals(sample0Channel0, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType8).ppgDataSamples[0])
        Assert.assertEquals(sample0Channel1, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType8).ppgDataSamples[1])
        Assert.assertEquals(sample0Channel2, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType8).ppgDataSamples[2])
        Assert.assertEquals(sample0Channel23, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType8).ppgDataSamples[23])
        Assert.assertEquals(sample0ChannelStatus, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType8).status)
        Assert.assertEquals(sample1Channel0, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType8).ppgDataSamples[0])
        Assert.assertEquals(sample1Channel1, (ppgData.ppgSamples[1] as PpgData.PpgDataFrameType8).ppgDataSamples[1])

        Assert.assertEquals(timeStamp, (ppgData.ppgSamples.last() as PpgData.PpgDataFrameType8).timeStamp)
    }

    @Test
    fun `test compressed PPG frame type 10`() {

        // Expected first sample values
        val expectedGreenSamples =
            intArrayOf(1575733, 1957739, 1740229, 1761644, 1807181, 1489480, 1577122, 1822779).toList()
        val expectedRedSamples = intArrayOf(1973554, 1752419, 1569544, 1126395, 256, 1312672).toList()
        val expectedIrSamples = intArrayOf(1671106, 2230896, 1670551, 2230476, 1312672, -5901481).toList()
        val expectedStatus = 249855
        val expectedTimeStamp = 112524943566143712uL

        // Frame data with timestamps, frame type data etc.
        val ppgDataFrameContent = byteArrayOf(
            0x01.toByte(), 0xC0.toByte(), 0x6E.toByte(), 0x6A.toByte(), 0x43.toByte(), 0xE1.toByte(),
            0x61.toByte(), 0xEE.toByte(), 0x0A.toByte(), 0x8A.toByte(), 0x35.toByte(), 0x0B.toByte(),
            0x18.toByte(), 0x6B.toByte(), 0xDF.toByte(), 0x1D.toByte(), 0xC5.toByte(), 0x8D.toByte(),
            0x1A.toByte(), 0x6C.toByte(), 0xE1.toByte(), 0x1A.toByte(), 0x4D.toByte(), 0x93.toByte(),
            0x1B.toByte(), 0x48.toByte(), 0xBA.toByte(), 0x16.toByte(), 0xA2.toByte(), 0x10.toByte(),
            0x18.toByte(), 0x3B.toByte(), 0xD0.toByte(), 0x1B.toByte(), 0x32.toByte(), 0x1D.toByte(),
            0x1E.toByte(), 0x63.toByte(), 0xBD.toByte(), 0x1A.toByte(), 0x08.toByte(), 0xF3.toByte(),
            0x17.toByte(), 0xFB.toByte(), 0x2F.toByte(), 0x11.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0xA0.toByte(), 0x07.toByte(), 0x14.toByte(), 0xC2.toByte(), 0x7F.toByte(),
            0x19.toByte(), 0x70.toByte(), 0x0A.toByte(), 0x22.toByte(), 0x97.toByte(), 0x7D.toByte(),
            0x19.toByte(), 0xCC.toByte(), 0x08.toByte(), 0x22.toByte(), 0xA0.toByte(), 0x07.toByte(),
            0x14.toByte(), 0x57.toByte(), 0xF3.toByte(), 0xA5.toByte(), 0xFF.toByte(), 0xCF.toByte(),
            0x03.toByte(), 0x18.toByte(), 0x06.toByte(), 0x52.toByte(), 0xB3.toByte(), 0xFF.toByte(),
            0x8E.toByte(), 0xF9.toByte(), 0xFF.toByte(), 0xAE.toByte(), 0xEF.toByte(), 0xFF.toByte(),
            0x5F.toByte(), 0xFB.toByte(), 0xFF.toByte(), 0xB1.toByte(), 0xA6.toByte(), 0xFF.toByte(),
            0x75.toByte(), 0xF8.toByte(), 0xFF.toByte(), 0xA7.toByte(), 0xF0.toByte(), 0xFF.toByte(),
            0x32.toByte(), 0xF1.toByte(), 0xFF.toByte(), 0xB8.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x1E.toByte(), 0x00.toByte(), 0x00.toByte(), 0x97.toByte(), 0x02.toByte(), 0x00.toByte(),
            0x08.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x60.toByte(), 0xF8.toByte(), 0xEB.toByte(), 0x8E.toByte(), 0x07.toByte(), 0x00.toByte(),
            0x8A.toByte(), 0x00.toByte(), 0x00.toByte(), 0xAE.toByte(), 0x08.toByte(), 0x00.toByte(),
            0x97.toByte(), 0x00.toByte(), 0x00.toByte(), 0x60.toByte(), 0xF8.toByte(), 0xEB.toByte(),
            0x32.toByte(), 0x8A.toByte(), 0xFD.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x4B.toByte(), 0x2A.toByte(), 0xFF.toByte(), 0xA7.toByte(), 0x04.toByte(), 0x00.toByte(),
            0xBC.toByte(), 0x0F.toByte(), 0x00.toByte(), 0xD0.toByte(), 0xFE.toByte(), 0xFF.toByte(),
            0x64.toByte(), 0xF5.toByte(), 0xFE.toByte(), 0x94.toByte(), 0xE8.toByte(), 0xFF.toByte(),
            0x5E.toByte(), 0x07.toByte(), 0x00.toByte(), 0x2F.toByte(), 0x17.toByte(), 0x00.toByte(),
            0x34.toByte(), 0x27.toByte(), 0x00.toByte(), 0xE7.toByte(), 0x11.toByte(), 0x00.toByte(),
            0x7B.toByte(), 0x08.toByte(), 0x00.toByte(), 0x2C.toByte(), 0x03.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0xA0.toByte(), 0x07.toByte(), 0x14.toByte(),
            0x09.toByte(), 0x3C.toByte(), 0x00.toByte(), 0xD0.toByte(), 0x86.toByte(), 0x00.toByte(),
            0xCE.toByte(), 0x3B.toByte(), 0x00.toByte(), 0xCA.toByte(), 0x86.toByte(), 0x00.toByte(),
            0xA0.toByte(), 0x07.toByte(), 0x14.toByte(), 0xCE.toByte(), 0x75.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF0.toByte(), 0xA4.toByte(), 0xFF.toByte(),
            0xAA.toByte(), 0x08.toByte(), 0x00.toByte(), 0x45.toByte(), 0x19.toByte(), 0x00.toByte(),
            0x8B.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x9A.toByte(), 0x8B.toByte(), 0xFF.toByte(),
            0x65.toByte(), 0xF4.toByte(), 0xFF.toByte(), 0x92.toByte(), 0x11.toByte(), 0x00.toByte(),
            0x40.toByte(), 0x20.toByte(), 0x00.toByte(), 0xC5.toByte(), 0x23.toByte(), 0x00.toByte(),
            0x8E.toByte(), 0x0F.toByte(), 0x00.toByte(), 0x01.toByte(), 0x06.toByte(), 0x00.toByte(),
            0x6F.toByte(), 0x05.toByte(), 0x00.toByte(), 0xD4.toByte(), 0x02.toByte(), 0x19.toByte(),
            0xB8.toByte(), 0x2D.toByte(), 0x38.toByte(), 0xBF.toByte(), 0x2D.toByte(), 0x00.toByte(),
            0x2C.toByte(), 0x78.toByte(), 0x00.toByte(), 0x23.toByte(), 0x2E.toByte(), 0x00.toByte(),
            0xA3.toByte(), 0x77.toByte(), 0x00.toByte(), 0x6C.toByte(), 0xCA.toByte(), 0xB5.toByte(),
            0x32.toByte(), 0x8A.toByte(), 0xFD.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xDE.toByte(), 0xF1.toByte(), 0xFF.toByte(), 0x24.toByte(), 0x06.toByte(), 0x00.toByte(),
            0x2B.toByte(), 0x12.toByte(), 0x00.toByte(), 0x78.toByte(), 0xFE.toByte(), 0xFF.toByte(),
            0x89.toByte(), 0xE9.toByte(), 0xFF.toByte(), 0x08.toByte(), 0xFD.toByte(), 0xFF.toByte(),
            0x31.toByte(), 0x0B.toByte(), 0x00.toByte(), 0x49.toByte(), 0x0F.toByte(), 0x00.toByte(),
            0xE2.toByte(), 0x06.toByte(), 0x00.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x00.toByte(),
            0x50.toByte(), 0x02.toByte(), 0x00.toByte(), 0x53.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x2C.toByte(), 0xFD.toByte(), 0xE6.toByte(), 0x48.toByte(), 0xD2.toByte(), 0xC7.toByte(),
            0x2E.toByte(), 0x0C.toByte(), 0x00.toByte(), 0x11.toByte(), 0x1C.toByte(), 0x00.toByte(),
            0x0F.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x2E.toByte(), 0x1C.toByte(), 0x00.toByte(),
            0x94.toByte(), 0x35.toByte(), 0x4A.toByte(), 0xCE.toByte(), 0x75.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x9C.toByte(), 0x06.toByte(), 0x00.toByte(),
            0x33.toByte(), 0x04.toByte(), 0x00.toByte(), 0x5F.toByte(), 0x06.toByte(), 0x00.toByte(),
            0xE1.toByte(), 0xFB.toByte(), 0xFF.toByte(), 0x15.toByte(), 0x06.toByte(), 0x00.toByte(),
            0xB9.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1C.toByte(), 0x06.toByte(), 0x00.toByte(),
            0xCE.toByte(), 0x01.toByte(), 0x00.toByte(), 0x6F.toByte(), 0xFC.toByte(), 0xFF.toByte(),
            0xCD.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xD1.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x6A.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xD4.toByte(), 0x02.toByte(), 0x19.toByte(),
            0xB8.toByte(), 0x2D.toByte(), 0x38.toByte(), 0x68.toByte(), 0xFE.toByte(), 0xFF.toByte(),
            0xB5.toByte(), 0xF6.toByte(), 0xFF.toByte(), 0xD8.toByte(), 0xFD.toByte(), 0xFF.toByte(),
            0xA6.toByte(), 0xF6.toByte(), 0xFF.toByte(), 0x6C.toByte(), 0xCA.toByte(), 0xB5.toByte(),
            0x32.toByte(), 0x8A.toByte(), 0xFD.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x6E.toByte(), 0xDC.toByte(), 0xFF.toByte(), 0xC7.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x0F.toByte(), 0x06.toByte(), 0x00.toByte(), 0xC5.toByte(), 0xF8.toByte(), 0xFF.toByte(),
            0x9F.toByte(), 0xDE.toByte(), 0xFF.toByte(), 0xE7.toByte(), 0xFB.toByte(), 0xFF.toByte(),
            0x0E.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0x61.toByte(), 0xF9.toByte(), 0xFF.toByte(),
            0x9D.toByte(), 0x04.toByte(), 0x00.toByte(), 0xB8.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x7E.toByte(), 0x02.toByte(), 0x00.toByte(), 0x67.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x2C.toByte(), 0xFD.toByte(), 0xE6.toByte(), 0x48.toByte(), 0xD2.toByte(), 0xC7.toByte(),
            0x7E.toByte(), 0x07.toByte(), 0x00.toByte(), 0xF5.toByte(), 0x0E.toByte(), 0x00.toByte(),
            0x14.toByte(), 0x07.toByte(), 0x00.toByte(), 0xF1.toByte(), 0x0E.toByte(), 0x00.toByte(),
            0x94.toByte(), 0x35.toByte(), 0x4A.toByte(), 0xCE.toByte(), 0x75.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte())

        val dataFrame = PmdDataFrame(
            data = ppgDataFrameContent,
            getPreviousTimeStamp = {  pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> 1000uL },
            getFactor = { 1.0f }
        ) { 13 }

        val ppgData = PpgData.parseDataFromDataFrame(dataFrame)

        assertNotNull(ppgData)
        assertEquals(expectedStatus, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType10).status)
        assertEquals(7, ppgData.ppgSamples.size)
        assertEquals(expectedTimeStamp, (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType10).timeStamp)

        var index = 0
        for (value in (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType10).redSamples) {
            val expected = expectedRedSamples[index++]
            assertEquals(expected, value)
        }
        index = 0
        for (value in (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType10).greenSamples) {
            val expected = expectedGreenSamples[index++]
            assertEquals(expected, value)
        }
        index = 0
        for (value in (ppgData.ppgSamples[0] as PpgData.PpgDataFrameType10).irSamples) {
            val expected = expectedIrSamples[index++]
            assertEquals(expected, value)
        }
    }

    @Test
    fun `test uncompressed PPG frame type 10 throws error`() {

        // 10th value (0x0A) (decimal 10) results false for check compressed mask (0x80, decimal 128 )
        val ppgDataFrameContent = byteArrayOf(
            0x01.toByte(), 0xC0.toByte(), 0x6E.toByte(), 0x6A.toByte(), 0x43.toByte(), 0xE1.toByte(),
            0x61.toByte(), 0xEE.toByte(), 0x0A.toByte(), 0x0A.toByte(), 0x35.toByte(), 0x0B.toByte(),
            0x18.toByte(), 0x6B.toByte(), 0xDF.toByte(), 0x1D.toByte(), 0xC5.toByte(), 0x8D.toByte(),
            0x1A.toByte(), 0x6C.toByte(), 0xE1.toByte(), 0x1A.toByte(), 0x4D.toByte(), 0x93.toByte()
        )
        val dataFrame = PmdDataFrame(
            data = ppgDataFrameContent,
            getPreviousTimeStamp = {  pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> 1000uL },
            getFactor = { 1.0f }
        ) { 13 }

        var throwingRunnable = ThrowingRunnable { PpgData.parseDataFromDataFrame(dataFrame) }
        val exception = assertThrows(java.lang.Exception::class.java, throwingRunnable)
        assertThat(exception.message, equalTo("Raw FrameType: TYPE_10 is not supported by PPG data parser"))
    }
}