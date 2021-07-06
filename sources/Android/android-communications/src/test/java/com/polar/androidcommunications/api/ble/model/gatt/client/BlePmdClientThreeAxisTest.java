package com.polar.androidcommunications.api.ble.model.gatt.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlePmdClientThreeAxisTest {

    @Test
    public void test_parseThreeAxisData_withResolution16() {
        // Arrange
        // HEX: 71 07 F0 6A 9E 8D 0A 38 BE 5C BE BA 2F 96 B3 EE 4B E5 AD FB 42 B9 EB BE 4C FE BA 2F 92 BF EE 4B E4 B1 FB 12 B9 EC BD 3C 3E BB 2F 8F D3 DE 4B E3 B5 F7 D2 B8 ED BD 30 7E 7B 2F 8B E3 CE 8B E2 BA F7 A2 B8 EE BC 20 BE 7B 2F 88 F3 CE CB E1 BD EF 52 F8 EF BC 18 FE 3B 2F 84 03 BF CB E0 C2 EF 32 B8 F0 BB 04 4E BC 2E 81 13 AF 0B E0 C6 EF F2 F7 F1 B9 FC 7D BC 2E 7D 27 9F 4B DF CA EB C2 F7 F2 B8 EC CD 7C 2E 7B 37 8F 4B DE CE E3 92 F7 F3 B8 E0 0D FD 2D 77 4B 7F CB DD D2 DF 62 37 F5 B7 D4 4D BD 2D 74 5B 6F CB DC D7 D7 32 37 F6 B5 C8 8D 7D 2D 71 6B 4F 4B DC DC D3 F2 36 F7 B4 BC DD FD 2C 6F 7B 3F 4B DB E0 CF D2 36 F8 B2 B0 2D BE 2C 6C 8F 1F CB DA E3 C7 A2 76 F9
        // index    type                                            data:
        // 0-5:   Reference sample                     size 6:      71 07 F0 6A 9E 8D
        //      Sample 0 (aka. reference sample):
        //      channel 0: 71 07 =>0x0771 => 1905
        int refSample0Channel0 = 1905;
        //      channel 1: F0 6A => 0x6AF0 => 27376
        int refSample0Channel1 = 27376;
        //      channel 2: 9E 8D => 0x8D9E => -29282
        int refSample0Channel2 = -29282;
        // Delta dump: 0A 38 BE 5C BE BA 2F 96 B3 EE 4B E5 AD FB
        // 6:      Delta size                           size 1:    0x0A (10 bits)
        // 7:      Sample amount                        size 1:    0x38 (Delta block contains 56 samples)
        // 8:                                                      0xBE (binary: 1011 1110)
        // 9:                                                      0x5C (binary: 0101 11| 00)
        //      Sample 1 - channel 0, size 10 bits: 00 1011 1110 => 190
        // 10:                                                     0xBE (binary: 1011 | 1110)
        //      Sample 1 - channel 1, size 10 bits: 11 1001 0111 => -105
        // 11:                                                      0xBA (binary: 10 |11 1010)
        //      Sample 1 - channel 2, size 10 bits: 11 1010 1011 => -85
        int refSample1Channel0 = 190;
        int refSample1Channel1 = -105;
        int refSample1Channel2 = -85;
        int amountOfSamples = 1 + 56; // reference sample + delta samples
        byte[] measurementFrame = {(byte) 0x71, (byte) 0x07, (byte) 0xF0, (byte) 0x6A, (byte) 0x9E, (byte) 0x8D, (byte) 0x0A, (byte) 0x38, (byte) 0xBE, (byte) 0x5C, (byte) 0xBE, (byte) 0xBA, (byte) 0x2F, (byte) 0x96, (byte) 0xB3, (byte) 0xEE, (byte) 0x4B, (byte) 0xE5, (byte) 0xAD, (byte) 0xFB, (byte) 0x42, (byte) 0xB9, (byte) 0xEB, (byte) 0xBE, (byte) 0x4C, (byte) 0xFE, (byte) 0xBA, (byte) 0x2F, (byte) 0x92, (byte) 0xBF, (byte) 0xEE, (byte) 0x4B, (byte) 0xE4, (byte) 0xB1, (byte) 0xFB, (byte) 0x12, (byte) 0xB9, (byte) 0xEC, (byte) 0xBD, (byte) 0x3C, (byte) 0x3E, (byte) 0xBB, (byte) 0x2F, (byte) 0x8F, (byte) 0xD3, (byte) 0xDE, (byte) 0x4B, (byte) 0xE3, (byte) 0xB5, (byte) 0xF7, (byte) 0xD2, (byte) 0xB8, (byte) 0xED, (byte) 0xBD, (byte) 0x30, (byte) 0x7E, (byte) 0x7B, (byte) 0x2F, (byte) 0x8B, (byte) 0xE3, (byte) 0xCE, (byte) 0x8B, (byte) 0xE2, (byte) 0xBA, (byte) 0xF7, (byte) 0xA2, (byte) 0xB8, (byte) 0xEE, (byte) 0xBC, (byte) 0x20, (byte) 0xBE, (byte) 0x7B, (byte) 0x2F, (byte) 0x88, (byte) 0xF3, (byte) 0xCE, (byte) 0xCB, (byte) 0xE1, (byte) 0xBD, (byte) 0xEF, (byte) 0x52, (byte) 0xF8, (byte) 0xEF, (byte) 0xBC, (byte) 0x18, (byte) 0xFE, (byte) 0x3B, (byte) 0x2F, (byte) 0x84, (byte) 0x03, (byte) 0xBF, (byte) 0xCB, (byte) 0xE0, (byte) 0xC2, (byte) 0xEF, (byte) 0x32, (byte) 0xB8, (byte) 0xF0, (byte) 0xBB, (byte) 0x04, (byte) 0x4E, (byte) 0xBC, (byte) 0x2E, (byte) 0x81, (byte) 0x13, (byte) 0xAF, (byte) 0x0B, (byte) 0xE0, (byte) 0xC6, (byte) 0xEF, (byte) 0xF2, (byte) 0xF7, (byte) 0xF1, (byte) 0xB9, (byte) 0xFC, (byte) 0x7D, (byte) 0xBC, (byte) 0x2E, (byte) 0x7D, (byte) 0x27, (byte) 0x9F, (byte) 0x4B, (byte) 0xDF, (byte) 0xCA, (byte) 0xEB, (byte) 0xC2, (byte) 0xF7, (byte) 0xF2, (byte) 0xB8, (byte) 0xEC, (byte) 0xCD, (byte) 0x7C, (byte) 0x2E, (byte) 0x7B, (byte) 0x37, (byte) 0x8F, (byte) 0x4B, (byte) 0xDE, (byte) 0xCE, (byte) 0xE3, (byte) 0x92, (byte) 0xF7, (byte) 0xF3, (byte) 0xB8, (byte) 0xE0, (byte) 0x0D, (byte) 0xFD, (byte) 0x2D, (byte) 0x77, (byte) 0x4B, (byte) 0x7F, (byte) 0xCB, (byte) 0xDD, (byte) 0xD2, (byte) 0xDF, (byte) 0x62, (byte) 0x37, (byte) 0xF5, (byte) 0xB7, (byte) 0xD4, (byte) 0x4D, (byte) 0xBD, (byte) 0x2D, (byte) 0x74, (byte) 0x5B, (byte) 0x6F, (byte) 0xCB, (byte) 0xDC, (byte) 0xD7, (byte) 0xD7, (byte) 0x32, (byte) 0x37, (byte) 0xF6, (byte) 0xB5, (byte) 0xC8, (byte) 0x8D, (byte) 0x7D, (byte) 0x2D, (byte) 0x71, (byte) 0x6B, (byte) 0x4F, (byte) 0x4B, (byte) 0xDC, (byte) 0xDC, (byte) 0xD3, (byte) 0xF2, (byte) 0x36, (byte) 0xF7, (byte) 0xB4, (byte) 0xBC, (byte) 0xDD, (byte) 0xFD, (byte) 0x2C, (byte) 0x6F, (byte) 0x7B, (byte) 0x3F, (byte) 0x4B, (byte) 0xDB, (byte) 0xE0, (byte) 0xCF, (byte) 0xD2, (byte) 0x36, (byte) 0xF8, (byte) 0xB2, (byte) 0xB0, (byte) 0x2D, (byte) 0xBE, (byte) 0x2C, (byte) 0x6C, (byte) 0x8F, (byte) 0x1F, (byte) 0xCB, (byte) 0xDA, (byte) 0xE3, (byte) 0xC7, (byte) 0xA2, (byte) 0x76, (byte) 0xF9};
        int resolution = 16;
        int range = 8;
        float factor = 2.44E-4f;
        long timeStamp = 0;

        // Act
        BlePMDClient.ThreeAxisDeltaFramedData threeAxisDeltaFramedData = new BlePMDClient.ThreeAxisDeltaFramedData(measurementFrame, factor, resolution, timeStamp);

        // Assert
        assertEquals((factor * refSample0Channel0), threeAxisDeltaFramedData.axisSamples.get(0).x, 0.001);
        assertEquals((factor * refSample0Channel1), threeAxisDeltaFramedData.axisSamples.get(0).y, 0.001);
        assertEquals((factor * refSample0Channel2), threeAxisDeltaFramedData.axisSamples.get(0).z, 0.001);

        assertEquals((factor * (refSample0Channel0 + refSample1Channel0)), threeAxisDeltaFramedData.axisSamples.get(1).x, 0.000000001);
        assertEquals((factor * (refSample0Channel1 + refSample1Channel1)), threeAxisDeltaFramedData.axisSamples.get(1).y, 0.000000001);
        assertEquals((factor * (refSample0Channel2 + refSample1Channel2)), threeAxisDeltaFramedData.axisSamples.get(1).z, 0.000000001);

        // validate data in range
        for (BlePMDClient.ThreeAxisDeltaFramedData.ThreeAxisSample sample : threeAxisDeltaFramedData.axisSamples) {
            assertTrue(Math.abs(sample.x) <= range);
            assertTrue(Math.abs(sample.y) <= range);
            assertTrue(Math.abs(sample.z) <= range);
        }

        // validate data size
        assertEquals(amountOfSamples, threeAxisDeltaFramedData.axisSamples.size());
    }
}
