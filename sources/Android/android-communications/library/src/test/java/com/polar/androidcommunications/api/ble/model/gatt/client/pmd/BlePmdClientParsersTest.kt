package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.PmdDataFieldEncoding
import org.junit.Assert
import org.junit.Test

class BlePmdClientParsersTest {

    @Test
    fun `parse reference sample when resolution16 and type is unsigned int`() {
        // Arrange
        // HEX: FF FF 00 00 FF 7F 00 80
        // index    type                                            data:
        //      channel 0: FF FF => 0xFFFF => -1
        //      channel 1: 00 00 => 0x0000 => 0
        //      channel 2: FF 7F => 0x7FFF => 32767
        //      channel 3: 00 80 => 0x8000 => -32768
        val dataBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0x7F.toByte(), 0x00.toByte(), 0x80.toByte())
        val expectedRefSample0 = -1
        val expectedRefSample1 = 0
        val expectedRefSample2 = 32767
        val expectedRefSample3 = -32768
        val resolution = 16
        val channels = 4

        // Act
        val refSamples = BlePMDClient.parseDeltaFrameRefSamples(dataBytes, channels, resolution, PmdDataFieldEncoding.SIGNED_INT)

        // Assert
        Assert.assertEquals(expectedRefSample0, refSamples[0])
        Assert.assertEquals(expectedRefSample1, refSamples[1])
        Assert.assertEquals(expectedRefSample2, refSamples[2])
        Assert.assertEquals(expectedRefSample3, refSamples[3])
    }

    @Test
    fun `parse reference sample when resolution32 and type is decimal IEEE 754`() {
        // Arrange
        // HEX: 00 00 00 00 FF FF FF FF 00 00 00 80
        val dataBytes = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x80.toByte()
        )

        //  Reference sample            size 8:      00 00 00 00 FF FF FF FF 00 00 00 80
        //      Sample 0 - channel 0: 02 00 00 00 => 0x00000000
        //      Sample 0 - channel 1: FF FF FF FF => 0xFFFFFFFF
        //      Sample 0 - channel 2: 00 00 FF FF => 0x80000000
        val expectedRefSample0 = 0x00000000
        val expectedRefSample1 = 0xFFFFFFFF.toInt()
        val expectedRefSample2 = 0x80000000.toInt()
        val resolution = 32
        val channels = 3

        // Act
        val refSamples = BlePMDClient.parseDeltaFrameRefSamples(dataBytes, channels, resolution, PmdDataFieldEncoding.FLOAT_IEEE754)

        // Assert
        Assert.assertEquals(0x0, expectedRefSample0.xor(refSamples[0]))
        Assert.assertEquals(0x0, expectedRefSample1.xor(refSamples[1]))
        Assert.assertEquals(0x0, expectedRefSample2.xor(refSamples[2]))
    }

    @Test
    fun test_parseDeltaFrameAllSamples_withResolution32_realVector1() {
        // Arrange
        // HEX: 00 00 80 3F 00 00 20 41 00 00 A0 41
        // 1C (28 bits)
        // 06 (6 samples => (6x3x28)/8 = 63 bytes)
        // 00 00 A0 01 00 00 08 CD CC EC
        // 0D 00 00 08 CD CC EC 3D 33 33
        // 1A CD CC EC 3D 33 33 1A 00 00
        // C0 30 33 33 1A 00 00 C0 A0 99
        // 99 DA 00 00 C0 A0 99 99 DA 66
        // 66 A6 A1 99 99 DA 66 66 A6 01
        // 00 00 0E
        // 20 (32 bits)
        // 03 (3 samples => (3x3x32)/8 = 36 bytes)
        // 66 66 A6 01
        // 00 00 E0 00
        // 42 6C 45 0E
        // 00 00 E0 00
        // 42 6C 45 0E
        // 2B F8 ED 21
        // 00 00 60 FD
        // BE 93 BA F0
        // 93 9B 4C CF
        // 1C (28 bits)
        // 06 (6 samples => (6x3x28)/8 = 63 bytes)
        // 00 00 A0 01 00 00 08 CD CC EC
        // 0D 00 00 08 CD CC EC 3D 33 33
        // 1A CD CC EC 3D 33 33 1A 00 00
        // C0 30 33 33 1A 00 00 C0 A0 99
        // 99 DA 00 00 C0 A0 99 99 DA 66
        // 66 A6 A1 99 99 DA 66 66 A6 01
        // 00 00 0E
        // 20 (32 bits)
        // 03 (3 samples => (3x3x32)/8 = 36 bytes)
        // 66 66 A6 01
        // 00 00 E0 00
        // 42 6C 45 0E
        // 00 00 E0 00
        // 42 6C 45 0E
        // 2B F8 ED 21
        // 00 00 60 FD
        // BE 93 BA F0
        // 93 9B 4C CF

        // index    type                                data
        // 0..3    Sample 0 - channel 0 (ref. sample)   00 00 80 3F (0x3F800000)
        // 4..7    Sample 0 - channel 1 (ref. sample)   00 00 20 41 (0x41200000)
        // 8..11   Sample 0 - channel 2 (ref. sample)   00 00 A0 41 (0x41A00000)
        // 12      Delta size                           1C (28 bit)
        // 13      Sample amount                        06 (6 samples)
        // 14..25 delta data: 00 00 A0 01 00 00 08 CD CC EC 0D ...
        // 14..17  Sample 1 - channel 0: (0x1A00000)
        // 18..21  Sample 1 - channel 1: (0x0800000)
        // 22..25  Sample 1 - channel 2  (0xDECCCCD)

        val expectedSamplesSize = 1 + 6 + 3 + 6 + 3 // reference sample + delta samples
        val sample0Channel0 = 0x3F800000
        val sample0Channel1 = 0x41200000
        val sample0Channel2 = 0x41A00000

        val sample1Channel0 = 0x3F800000 + 0x1A00000
        val sample1Channel1 = 0x41200000 + 0x0800000
        val sample1Channel2 = (0x41A00000 + 0xFDECCCCD).toInt()

        val deltaCodedDataFrame = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x80.toByte(), 0x3F.toByte(), 0x00.toByte(), 0x00.toByte(), 0x20.toByte(), 0x41.toByte(), 0x00.toByte(),
            0x00.toByte(), 0xA0.toByte(), 0x41.toByte(), 0x1C.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(), 0xA0.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x08.toByte(), 0xCD.toByte(), 0xCC.toByte(), 0xEC.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x08.toByte(), 0xCD.toByte(), 0xCC.toByte(), 0xEC.toByte(), 0x3D.toByte(), 0x33.toByte(), 0x33.toByte(), 0x1A.toByte(), 0xCD.toByte(),
            0xCC.toByte(), 0xEC.toByte(), 0x3D.toByte(), 0x33.toByte(), 0x33.toByte(), 0x1A.toByte(), 0x00.toByte(), 0x00.toByte(), 0xC0.toByte(),
            0x30.toByte(), 0x33.toByte(), 0x33.toByte(), 0x1A.toByte(), 0x00.toByte(), 0x00.toByte(), 0xC0.toByte(), 0xA0.toByte(), 0x99.toByte(),
            0x99.toByte(), 0xDA.toByte(), 0x00.toByte(), 0x00.toByte(), 0xC0.toByte(), 0xA0.toByte(), 0x99.toByte(), 0x99.toByte(), 0xDA.toByte(),
            0x66.toByte(), 0x66.toByte(), 0xA6.toByte(), 0xA1.toByte(), 0x99.toByte(), 0x99.toByte(), 0xDA.toByte(), 0x66.toByte(), 0x66.toByte(),
            0xA6.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0E.toByte(), 0x20.toByte(), 0x03.toByte(), 0x66.toByte(), 0x66.toByte(),
            0xA6.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x42.toByte(), 0x6C.toByte(), 0x45.toByte(),
            0x0E.toByte(), 0x00.toByte(), 0x00.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x42.toByte(), 0x6C.toByte(), 0x45.toByte(), 0x0E.toByte(),
            0x2B.toByte(), 0xF8.toByte(), 0xED.toByte(), 0x21.toByte(), 0x00.toByte(), 0x00.toByte(), 0x60.toByte(), 0xFD.toByte(), 0xBE.toByte(),
            0x93.toByte(), 0xBA.toByte(), 0xF0.toByte(), 0x93.toByte(), 0x9B.toByte(), 0x4C.toByte(), 0xCF.toByte(), 0x1C.toByte(), 0x06.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xA0.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x08.toByte(), 0xCD.toByte(), 0xCC.toByte(),
            0xEC.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x00.toByte(), 0x08.toByte(), 0xCD.toByte(), 0xCC.toByte(), 0xEC.toByte(), 0x3D.toByte(),
            0x33.toByte(), 0x33.toByte(), 0x1A.toByte(), 0xCD.toByte(), 0xCC.toByte(), 0xEC.toByte(), 0x3D.toByte(), 0x33.toByte(), 0x33.toByte(),
            0x1A.toByte(), 0x00.toByte(), 0x00.toByte(), 0xC0.toByte(), 0x30.toByte(), 0x33.toByte(), 0x33.toByte(), 0x1A.toByte(), 0x00.toByte(),
            0x00.toByte(), 0xC0.toByte(), 0xA0.toByte(), 0x99.toByte(), 0x99.toByte(), 0xDA.toByte(), 0x00.toByte(), 0x00.toByte(), 0xC0.toByte(),
            0xA0.toByte(), 0x99.toByte(), 0x99.toByte(), 0xDA.toByte(), 0x66.toByte(), 0x66.toByte(), 0xA6.toByte(), 0xA1.toByte(), 0x99.toByte(),
            0x99.toByte(), 0xDA.toByte(), 0x66.toByte(), 0x66.toByte(), 0xA6.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0E.toByte(),
            0x20.toByte(), 0x03.toByte(), 0x66.toByte(), 0x66.toByte(), 0xA6.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0xE0.toByte(),
            0x00.toByte(), 0x42.toByte(), 0x6C.toByte(), 0x45.toByte(), 0x0E.toByte(), 0x00.toByte(), 0x00.toByte(), 0xE0.toByte(), 0x00.toByte(),
            0x42.toByte(), 0x6C.toByte(), 0x45.toByte(), 0x0E.toByte(), 0x2B.toByte(), 0xF8.toByte(), 0xED.toByte(), 0x21.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x60.toByte(), 0xFD.toByte(), 0xBE.toByte(), 0x93.toByte(), 0xBA.toByte(), 0xF0.toByte(), 0x93.toByte(), 0x9B.toByte(),
            0x4C.toByte(), 0xCF.toByte()
        )
        val resolution = 32
        val channels = 3

        // Act
        val allSamples = BlePMDClient.parseDeltaFramesAll(deltaCodedDataFrame, channels, resolution, PmdDataFieldEncoding.FLOAT_IEEE754)

        // Assert
        Assert.assertEquals(expectedSamplesSize, allSamples.size)
        Assert.assertEquals(3, allSamples[0].size)

        Assert.assertEquals(0x0, sample0Channel0.xor(allSamples[0][0]))
        Assert.assertEquals(0x0, sample0Channel1.xor(allSamples[0][1]))
        Assert.assertEquals(0x0, sample0Channel2.xor(allSamples[0][2]))

        Assert.assertEquals(0x0, sample1Channel0.xor(allSamples[1][0]))
        Assert.assertEquals(0x0, sample1Channel1.xor(allSamples[1][1]))
        Assert.assertEquals(0x0, sample1Channel2.xor(allSamples[1][2]))
    }

    @Test
    fun test_parseDeltaFrameAllSamples_withResolution32() {
        // Arrange
        // HEX: 02 00 00 00 19 32 99 E9 00 DA FE FF 20 11 17 32 99 E9 E7 A7 65 16 38 DB 06 46 E7 A7 65 16 38 DB 06 46 73 41 3B B8 7A 26 04 01 C9 0A FF 59 77 07 D1 32 87 BF 01 9F 21 3E 0D 91 E5 01 76 AD 21 3E 0D 91 E5 01 76 AD 92 01 38 CD E5 01 76 AD 92 01 38 CD 00 54 84 87 92 01 38 CD 00 54 84 87 78 CB EE 40 00 54 84 87 78 CB EE 40 F1 DE CC 8B 78 CB EE 40 F1 DE CC 8B 17 32 99 E9 F1 DE CC 8B 17 32 99 E9 E7 A7 65 16 17 32 99 E9 E7 A7 65 16 38 DB 06 46 E7 A7 65 16 38 DB 06 46 73 41 3B B8 7A 26 04 01 C9 0A FF 59 77 07 D1 32 87 BF 01 9F 21 3E 0D 91 E5 01 76 AD 21 3E 0D 91 E5 01 76 AD 92 01 38 CD E5 01 76 AD 92 01 38 CD 00 54 84 87 92 01 38 CD 00 54 84 87 78 CB EE 40
        // index  type                                              data:
        // 0-5:   Reference sample                     size 6:      02 00 00 00 19 32 99 E9 00 DA FE FF
        //      Sample 0 (aka. reference sample):
        //      Sample 0 - channel 0: 02 00 00 00 => 0x00000002
        //      Sample 0 - channel 1: 19 32 99 E9 => 0xE9993219
        //      Sample 0 - channel 2: 00 DA FE FF => 0xFFFEDA00
        // 6:      Delta size                           size 1:    0x20 (32 bits)
        // 7:      Sample amount                        size 1:    0x11 (Delta block contains 17 samples)
        // 8-11:   Sample 1 - channel 0                 17 32 99 E9 => 0xE9993217
        // 12-15:  Sample 1 - channel 1                 E7 A7 65 16 => 0x1665A7E7
        // 16-19:  Sample 1 - channel 2                 38 DB 06 46 => 0x4606DB38

        val sample0Channel0 = 0x00000002
        val sample0Channel1 = 0xE9993219.toInt()
        val sample0Channel2 = 0xFFFEDA00.toInt()
        val sample1Channel0 = sample0Channel0 + 0xE9993217.toInt()
        val sample1Channel1 = sample0Channel1 + 0x1665A7E7
        val sample1Channel2 = sample0Channel2 + 0x4606DB38

        val measurementFrame = byteArrayOf(
            0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x19.toByte(), 0x32.toByte(), 0x99.toByte(), 0xE9.toByte(),
            0x00.toByte(), 0xDA.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0x20.toByte(), 0x11.toByte(), 0x17.toByte(), 0x32.toByte(),
            0x99.toByte(), 0xE9.toByte(), 0xE7.toByte(), 0xA7.toByte(), 0x65.toByte(), 0x16.toByte(), 0x38.toByte(), 0xDB.toByte(),
            0x06.toByte(), 0x46.toByte(), 0xE7.toByte(), 0xA7.toByte(), 0x65.toByte(), 0x16.toByte(), 0x38.toByte(), 0xDB.toByte(),
            0x06.toByte(), 0x46.toByte(), 0x73.toByte(), 0x41.toByte(), 0x3B.toByte(), 0xB8.toByte(), 0x7A.toByte(), 0x26.toByte(),
            0x04.toByte(), 0x01.toByte(), 0xC9.toByte(), 0x0A.toByte(), 0xFF.toByte(), 0x59.toByte(), 0x77.toByte(), 0x07.toByte(),
            0xD1.toByte(), 0x32.toByte(), 0x87.toByte(), 0xBF.toByte(), 0x01.toByte(), 0x9F.toByte(), 0x21.toByte(), 0x3E.toByte(),
            0x0D.toByte(), 0x91.toByte(), 0xE5.toByte(), 0x01.toByte(), 0x76.toByte(), 0xAD.toByte(), 0x21.toByte(), 0x3E.toByte(),
            0x0D.toByte(), 0x91.toByte(), 0xE5.toByte(), 0x01.toByte(), 0x76.toByte(), 0xAD.toByte(), 0x92.toByte(), 0x01.toByte(),
            0x38.toByte(), 0xCD.toByte(), 0xE5.toByte(), 0x01.toByte(), 0x76.toByte(), 0xAD.toByte(), 0x92.toByte(), 0x01.toByte(),
            0x38.toByte(), 0xCD.toByte(), 0x00.toByte(), 0x54.toByte(), 0x84.toByte(), 0x87.toByte(), 0x92.toByte(), 0x01.toByte(),
            0x38.toByte(), 0xCD.toByte(), 0x00.toByte(), 0x54.toByte(), 0x84.toByte(), 0x87.toByte(), 0x78.toByte(), 0xCB.toByte(),
            0xEE.toByte(), 0x40.toByte(), 0x00.toByte(), 0x54.toByte(), 0x84.toByte(), 0x87.toByte(), 0x78.toByte(), 0xCB.toByte(),
            0xEE.toByte(), 0x40.toByte(), 0xF1.toByte(), 0xDE.toByte(), 0xCC.toByte(), 0x8B.toByte(), 0x78.toByte(), 0xCB.toByte(),
            0xEE.toByte(), 0x40.toByte(), 0xF1.toByte(), 0xDE.toByte(), 0xCC.toByte(), 0x8B.toByte(), 0x17.toByte(), 0x32.toByte(),
            0x99.toByte(), 0xE9.toByte(), 0xF1.toByte(), 0xDE.toByte(), 0xCC.toByte(), 0x8B.toByte(), 0x17.toByte(), 0x32.toByte(),
            0x99.toByte(), 0xE9.toByte(), 0xE7.toByte(), 0xA7.toByte(), 0x65.toByte(), 0x16.toByte(), 0x17.toByte(), 0x32.toByte(),
            0x99.toByte(), 0xE9.toByte(), 0xE7.toByte(), 0xA7.toByte(), 0x65.toByte(), 0x16.toByte(), 0x38.toByte(), 0xDB.toByte(),
            0x06.toByte(), 0x46.toByte(), 0xE7.toByte(), 0xA7.toByte(), 0x65.toByte(), 0x16.toByte(), 0x38.toByte(), 0xDB.toByte(),
            0x06.toByte(), 0x46.toByte(), 0x73.toByte(), 0x41.toByte(), 0x3B.toByte(), 0xB8.toByte(), 0x7A.toByte(), 0x26.toByte(),
            0x04.toByte(), 0x01.toByte(), 0xC9.toByte(), 0x0A.toByte(), 0xFF.toByte(), 0x59.toByte(), 0x77.toByte(), 0x07.toByte(),
            0xD1.toByte(), 0x32.toByte(), 0x87.toByte(), 0xBF.toByte(), 0x01.toByte(), 0x9F.toByte(), 0x21.toByte(), 0x3E.toByte(),
            0x0D.toByte(), 0x91.toByte(), 0xE5.toByte(), 0x01.toByte(), 0x76.toByte(), 0xAD.toByte(), 0x21.toByte(), 0x3E.toByte(),
            0x0D.toByte(), 0x91.toByte(), 0xE5.toByte(), 0x01.toByte(), 0x76.toByte(), 0xAD.toByte(), 0x92.toByte(), 0x01.toByte(),
            0x38.toByte(), 0xCD.toByte(), 0xE5.toByte(), 0x01.toByte(), 0x76.toByte(), 0xAD.toByte(), 0x92.toByte(), 0x01.toByte(),
            0x38.toByte(), 0xCD.toByte(), 0x00.toByte(), 0x54.toByte(), 0x84.toByte(), 0x87.toByte(), 0x92.toByte(), 0x01.toByte(),
            0x38.toByte(), 0xCD.toByte(), 0x00.toByte(), 0x54.toByte(), 0x84.toByte(), 0x87.toByte(), 0x78.toByte(), 0xCB.toByte(),
            0xEE.toByte(), 0x40.toByte()
        )

        val amountOfSamples = 18 // reference sample + delta samples
        val resolution = 32
        val channels = 3

        // Act
        val allSamples = BlePMDClient.parseDeltaFramesAll(measurementFrame, channels, resolution, PmdDataFieldEncoding.FLOAT_IEEE754)

        // Assert
        Assert.assertEquals(amountOfSamples, allSamples.size)
        Assert.assertEquals(3, allSamples[0].size)

        Assert.assertEquals(0x0, sample0Channel0.xor(allSamples[0][0]))
        Assert.assertEquals(0x0, sample0Channel1.xor(allSamples[0][1]))
        Assert.assertEquals(0x0, sample0Channel2.xor(allSamples[0][2]))

        Assert.assertEquals(0x0, sample1Channel0.xor(allSamples[1][0]))
        Assert.assertEquals(0x0, sample1Channel1.xor(allSamples[1][1]))
        Assert.assertEquals(0x0, sample1Channel2.xor(allSamples[1][2]))
    }

    @Test
    fun test_parseDeltaFrameAllSamples_multipleDeltas() {
        // Arrange
        // Delta data dump: C9 FF 12 00 11 00 03 09 41 FE 2B 0F 9C 0B BF 15 00 4F 00 04 1E F1 EF 00 F0 C1 23 E4 ED F4 D1 F1 F1 F5 FF 22 DE 31 00 F1 FE 21 02 1F 0E 2B 1F 00 E2 20 00 0E 02 E1 1E 20 FF F1 F1 02 C5 D0 02 E0 E1 02 03 0A 31 2E FB BA 90 2B AA 0E 23 40 9E 03 04 14 E3 EF F3 0F 02 1F 01 E0 0F 04 9E 13 E2 D0 04 E2 22 E2 C2 0E 20 0F 20 02 FE 00 0F 1C 32 EE 03 0A 89 00 07 08 7C 00 CE 2F E8 3A 9E 03 04 1E 01 00 11 19 4F 00 2F 12 FD 13 FF 0E 10 00 00 F1 C0 12 E4 EF 21 00 00 01 F1 FF FF 02 10 10 2B 51 0B 4E 31 FC 2E BF 31 14 EC 0E 2F 52 EF 03 0A 06 9E 04 0E 02 A8 88 EE E0 07 9A 00 04 0A 1F 21 1E 4E 2E FE C6 C0 02 EF 03 01 02 EE 11 03 0A F8 13 00 00 F0 40 BF A5 E7 00 76 00
        // index    type                                            data:
        // 0-5:   Reference sample                     size 6:    0xC9 0xFF 0x12 0x00 0x11 0x00
        //      Sample 0 (aka. reference sample):
        //      channel 0: C9 FF => 0xFFC9 => -55
        val refSample0Channel0 = -55
        //      channel 1: 12 00 => 0x0012 => 18
        val refSample0Channel1 = 18
        //      channel 2: 11 00 => 0x0011 => 17
        val refSample0Channel2 = 17
        // Delta dump: 03 09 | 41 FE 2B 0F 9C 0B BF 15 00 4F 00
        // 6:      Delta size                           size 1:    0x03 (3 bits)
        // 7:      Sample amount                        size 1:    0x09 (Delta block contains 9 samples)
        // 8:                                                      0x41 (binary: 01 | 000 | 001)
        //      Sample 1 - channel 0, size 3 bits: 001
        //      Sample 1 - channel 1, size 3 bits: 000
        // 9:                                                      0xFE (binary: 1 | 111 | 111 | 0)
        //      Sample 1 - channel 2, size 3 bits: 001
        //      Sample 2 - channel 0, size 3 bits: 111
        //      Sample 2 - channel 1, size 3 bits: 111
        // 10:                                                      0x2B (binary: 001 | 010 | 11)
        //      Sample 2 - channel 2, size 3 bits: 111
        val refSample1Channel0 = -54
        val refSample1Channel1 = 18
        val refSample1Channel2 = 18

        // ...
        // Delta dump: 04 1E | F1 EF 00 F0 C1 23 E4 ED F4 D1  F1 F1 F5 FF 22 DE 31 00 F1 FE  21 02 1F 0E 2B 1F 00 E2 20 00 0E 02 E1 1E 20 FF F1 F1 02 C5  D0 02 E0 E1 02
        // 19:      Delta size                           size 1:    0x04 (4 bits)
        // 20:      Sample amount                        size 1:    0x1E (Rest of the data contains 30 samples)
        // ...
        // Delta dump: 03 0A | 31 2E FB BA 90 2B AA 0E 23 40 9E 03
        // 66:      Delta size                           size 1:    0x03 (3 bits)
        // 67:      Sample amount                        size 1:    0x0A (Rest of the data contains 10 samples)
        // ...
        // Delta dump: 04 14 | E3 EF F3 0F 02 1F 01 E0 0F 04 9E 13 E2 D0 04 E2 22 E2 C2 0E 20 0F 20 02 FE 00 0F 1C 32 EE
        // 80:      Delta size                           size 1:    0x04 (4 bits)
        // 81:      Sample amount                        size 1:    0x14 (Rest of the data contains 20 samples)
        // ...
        // Delta dump: 03 0A | 89 00 07 08 7C 00 CE 2F E8 3A 9E 03
        // 112:      Delta size                          size 1:    0x03 (3 bits)
        // 113:      Sample amount                       size 1:    0x0A (Rest of the data contains 10 samples)
        // ...
        // Delta dump: 04 1E | 01 00 11 19 4F 00 2F 12 FD 13 FF 0E 10 00 00 F1 C0 12 E4 EF 21 00 00 01 F1 FF FF 02 10 10 2B 51 0B 4E 31 FC 2E BF 31 14 EC 0E 2F 52 EF
        // 126:      Delta size                           size 1:    0x04 (4 bits)
        // 127:      Sample amount                        size 1:    0x1E (Rest of the data contains 30 samples)
        // ...
        // Delta dump: 03 0A |06 9E 04 0E 02 A8 88 EE E0 07 9A 00
        // 173:      Delta size                          size 1:    0x03 (3 bits)
        // 174:      Sample amount                        size 1:    0x0A (Rest of the data contains 10 samples)
        // ...
        // Delta dump: 04 0A | 1F 21 1E 4E 2E FE C6 C0 02 EF 03 01 02 EE 11
        // 187:      Delta size                          size 1:    0x04 (4 bits)
        // 188:      Sample amount                        size 1:    0x0A (Rest of the data contains 10 samples)
        // ...
        // Delta dump: 03 0A | F8 13 00 00 F0 40 BF A5 E7 00 76 00
        // 204:      Delta size                           size 1:    0x03 (3 bits)
        // 205:      Sample amount                        size 1:    0x0A (Rest of the data contains 10 samples)
        val expectedSampleSize = 1 + 9 + 30 + 10 + 20 + 10 + 30 + 10 + 10 + 10
        val resolution = 16
        val channels = 3
        val dataFrame = byteArrayOf(
            0xC9.toByte(), 0xFF.toByte(), 0x12.toByte(), 0x00.toByte(), 0x11.toByte(), 0x00.toByte(), 0x03.toByte(), 0x09.toByte(),
            0x41.toByte(), 0xFE.toByte(), 0x2B.toByte(), 0x0F.toByte(), 0x9C.toByte(), 0x0B.toByte(), 0xBF.toByte(), 0x15.toByte(),
            0x00.toByte(), 0x4F.toByte(), 0x00.toByte(), 0x04.toByte(), 0x1E.toByte(), 0xF1.toByte(), 0xEF.toByte(), 0x00.toByte(),
            0xF0.toByte(), 0xC1.toByte(), 0x23.toByte(), 0xE4.toByte(), 0xED.toByte(), 0xF4.toByte(), 0xD1.toByte(), 0xF1.toByte(),
            0xF1.toByte(), 0xF5.toByte(), 0xFF.toByte(), 0x22.toByte(), 0xDE.toByte(), 0x31.toByte(), 0x00.toByte(), 0xF1.toByte(),
            0xFE.toByte(), 0x21.toByte(), 0x02.toByte(), 0x1F.toByte(), 0x0E.toByte(), 0x2B.toByte(), 0x1F.toByte(), 0x00.toByte(),
            0xE2.toByte(), 0x20.toByte(), 0x00.toByte(), 0x0E.toByte(), 0x02.toByte(), 0xE1.toByte(), 0x1E.toByte(), 0x20.toByte(),
            0xFF.toByte(), 0xF1.toByte(), 0xF1.toByte(), 0x02.toByte(), 0xC5.toByte(), 0xD0.toByte(), 0x02.toByte(), 0xE0.toByte(),
            0xE1.toByte(), 0x02.toByte(), 0x03.toByte(), 0x0A.toByte(), 0x31.toByte(), 0x2E.toByte(), 0xFB.toByte(), 0xBA.toByte(),
            0x90.toByte(), 0x2B.toByte(), 0xAA.toByte(), 0x0E.toByte(), 0x23.toByte(), 0x40.toByte(), 0x9E.toByte(), 0x03.toByte(),
            0x04.toByte(), 0x14.toByte(), 0xE3.toByte(), 0xEF.toByte(), 0xF3.toByte(), 0x0F.toByte(), 0x02.toByte(), 0x1F.toByte(),
            0x01.toByte(), 0xE0.toByte(), 0x0F.toByte(), 0x04.toByte(), 0x9E.toByte(), 0x13.toByte(), 0xE2.toByte(), 0xD0.toByte(),
            0x04.toByte(), 0xE2.toByte(), 0x22.toByte(), 0xE2.toByte(), 0xC2.toByte(), 0x0E.toByte(), 0x20.toByte(), 0x0F.toByte(),
            0x20.toByte(), 0x02.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x0F.toByte(), 0x1C.toByte(), 0x32.toByte(), 0xEE.toByte(),
            0x03.toByte(), 0x0A.toByte(), 0x89.toByte(), 0x00.toByte(), 0x07.toByte(), 0x08.toByte(), 0x7C.toByte(), 0x00.toByte(),
            0xCE.toByte(), 0x2F.toByte(), 0xE8.toByte(), 0x3A.toByte(), 0x9E.toByte(), 0x03.toByte(), 0x04.toByte(), 0x1E.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x11.toByte(), 0x19.toByte(), 0x4F.toByte(), 0x00.toByte(), 0x2F.toByte(), 0x12.toByte(),
            0xFD.toByte(), 0x13.toByte(), 0xFF.toByte(), 0x0E.toByte(), 0x10.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(),
            0xC0.toByte(), 0x12.toByte(), 0xE4.toByte(), 0xEF.toByte(), 0x21.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0xF1.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x02.toByte(), 0x10.toByte(), 0x10.toByte(), 0x2B.toByte(), 0x51.toByte(),
            0x0B.toByte(), 0x4E.toByte(), 0x31.toByte(), 0xFC.toByte(), 0x2E.toByte(), 0xBF.toByte(), 0x31.toByte(), 0x14.toByte(),
            0xEC.toByte(), 0x0E.toByte(), 0x2F.toByte(), 0x52.toByte(), 0xEF.toByte(), 0x03.toByte(), 0x0A.toByte(), 0x06.toByte(),
            0x9E.toByte(), 0x04.toByte(), 0x0E.toByte(), 0x02.toByte(), 0xA8.toByte(), 0x88.toByte(), 0xEE.toByte(), 0xE0.toByte(),
            0x07.toByte(), 0x9A.toByte(), 0x00.toByte(), 0x04.toByte(), 0x0A.toByte(), 0x1F.toByte(), 0x21.toByte(), 0x1E.toByte(),
            0x4E.toByte(), 0x2E.toByte(), 0xFE.toByte(), 0xC6.toByte(), 0xC0.toByte(), 0x02.toByte(), 0xEF.toByte(), 0x03.toByte(),
            0x01.toByte(), 0x02.toByte(), 0xEE.toByte(), 0x11.toByte(), 0x03.toByte(), 0x0A.toByte(), 0xF8.toByte(), 0x13.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xF0.toByte(), 0x40.toByte(), 0xBF.toByte(), 0xA5.toByte(), 0xE7.toByte(), 0x00.toByte(),
            0x76.toByte(), 0x00.toByte()
        )

        // Act
        val allSamples = BlePMDClient.parseDeltaFramesAll(dataFrame, channels, resolution, PmdDataFieldEncoding.SIGNED_INT)

        // Assert
        Assert.assertEquals(expectedSampleSize, allSamples.size)
        Assert.assertEquals(3, allSamples[0].size)
        Assert.assertEquals(refSample0Channel0, allSamples[0][0])
        Assert.assertEquals(refSample0Channel1, allSamples[0][1])
        Assert.assertEquals(refSample0Channel2, allSamples[0][2])
        Assert.assertEquals(refSample1Channel0, allSamples[1][0])
        Assert.assertEquals(refSample1Channel1, allSamples[1][1])
        Assert.assertEquals(refSample1Channel2, allSamples[1][2])
    }
}