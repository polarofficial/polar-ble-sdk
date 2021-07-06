package com.polar.androidcommunications.api.ble.model.gatt.client;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class BlePmdClientParsersTest {

    // Delta data dump: C9 FF 12 00 11 00 03 09 41 FE 2B 0F 9C 0B BF 15 00 4F 00 04 1E F1 EF 00 F0 C1 23 E4 ED F4 D1 F1 F1 F5 FF 22 DE 31 00 F1 FE 21 02 1F 0E 2B 1F 00 E2 20 00 0E 02 E1 1E 20 FF F1 F1 02 C5 D0 02 E0 E1 02 03 0A 31 2E FB BA 90 2B AA 0E 23 40 9E 03 04 14 E3 EF F3 0F 02 1F 01 E0 0F 04 9E 13 E2 D0 04 E2 22 E2 C2 0E 20 0F 20 02 FE 00 0F 1C 32 EE 03 0A 89 00 07 08 7C 00 CE 2F E8 3A 9E 03 04 1E 01 00 11 19 4F 00 2F 12 FD 13 FF 0E 10 00 00 F1 C0 12 E4 EF 21 00 00 01 F1 FF FF 02 10 10 2B 51 0B 4E 31 FC 2E BF 31 14 EC 0E 2F 52 EF 03 0A 06 9E 04 0E 02 A8 88 EE E0 07 9A 00 04 0A 1F 21 1E 4E 2E FE C6 C0 02 EF 03 01 02 EE 11 03 0A F8 13 00 00 F0 40 BF A5 E7 00 76 00
    // index    type                                            data:
    // 0-5:   Reference sample                     size 6:    0xC9 0xFF 0x12 0x00 0x11 0x00
    //      Sample 0 (aka. reference sample):
    //      channel 0: C9 FF => 0xFFC9 => -55
    int refSample0Channel0 = -55;
    //      channel 1: 12 00 => 0x0012 => 18
    int refSample0Channel1 = 18;
    //      channel 2: 11 00 => 0x0011 => 17
    int refSample0Channel2 = 17;
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
    int refSample1Channel0 = -54;
    int refSample1Channel1 = 18;
    int refSample1Channel2 = 18;
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
    int totalSamples_size = 1 + 9 + 30 + 10 + 20 + 10 + 30 + 10 + 10 + 10;
    byte[] measurementFrame = {(byte) 0xC9, (byte) 0xFF, (byte) 0x12, (byte) 0x00, (byte) 0x11, (byte) 0x00, (byte) 0x03, (byte) 0x09, (byte) 0x41, (byte) 0xFE, (byte) 0x2B, (byte) 0x0F, (byte) 0x9C, (byte) 0x0B, (byte) 0xBF, (byte) 0x15, (byte) 0x00, (byte) 0x4F, (byte) 0x00, (byte) 0x04, (byte) 0x1E, (byte) 0xF1, (byte) 0xEF, (byte) 0x00, (byte) 0xF0, (byte) 0xC1, (byte) 0x23, (byte) 0xE4, (byte) 0xED, (byte) 0xF4, (byte) 0xD1, (byte) 0xF1, (byte) 0xF1, (byte) 0xF5, (byte) 0xFF, (byte) 0x22, (byte) 0xDE, (byte) 0x31, (byte) 0x00, (byte) 0xF1, (byte) 0xFE, (byte) 0x21, (byte) 0x02, (byte) 0x1F, (byte) 0x0E, (byte) 0x2B, (byte) 0x1F, (byte) 0x00, (byte) 0xE2, (byte) 0x20, (byte) 0x00, (byte) 0x0E, (byte) 0x02, (byte) 0xE1, (byte) 0x1E, (byte) 0x20, (byte) 0xFF, (byte) 0xF1, (byte) 0xF1, (byte) 0x02, (byte) 0xC5, (byte) 0xD0, (byte) 0x02, (byte) 0xE0, (byte) 0xE1, (byte) 0x02, (byte) 0x03, (byte) 0x0A, (byte) 0x31, (byte) 0x2E, (byte) 0xFB, (byte) 0xBA, (byte) 0x90, (byte) 0x2B, (byte) 0xAA, (byte) 0x0E, (byte) 0x23, (byte) 0x40, (byte) 0x9E, (byte) 0x03, (byte) 0x04, (byte) 0x14, (byte) 0xE3, (byte) 0xEF, (byte) 0xF3, (byte) 0x0F, (byte) 0x02, (byte) 0x1F, (byte) 0x01, (byte) 0xE0, (byte) 0x0F, (byte) 0x04, (byte) 0x9E, (byte) 0x13, (byte) 0xE2, (byte) 0xD0, (byte) 0x04, (byte) 0xE2, (byte) 0x22, (byte) 0xE2, (byte) 0xC2, (byte) 0x0E, (byte) 0x20, (byte) 0x0F, (byte) 0x20, (byte) 0x02, (byte) 0xFE, (byte) 0x00, (byte) 0x0F, (byte) 0x1C, (byte) 0x32, (byte) 0xEE, (byte) 0x03, (byte) 0x0A, (byte) 0x89, (byte) 0x00, (byte) 0x07, (byte) 0x08, (byte) 0x7C, (byte) 0x00, (byte) 0xCE, (byte) 0x2F, (byte) 0xE8, (byte) 0x3A, (byte) 0x9E, (byte) 0x03, (byte) 0x04, (byte) 0x1E, (byte) 0x01, (byte) 0x00, (byte) 0x11, (byte) 0x19, (byte) 0x4F, (byte) 0x00, (byte) 0x2F, (byte) 0x12, (byte) 0xFD, (byte) 0x13, (byte) 0xFF, (byte) 0x0E, (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0xF1, (byte) 0xC0, (byte) 0x12, (byte) 0xE4, (byte) 0xEF, (byte) 0x21, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0xF1, (byte) 0xFF, (byte) 0xFF, (byte) 0x02, (byte) 0x10, (byte) 0x10, (byte) 0x2B, (byte) 0x51, (byte) 0x0B, (byte) 0x4E, (byte) 0x31, (byte) 0xFC, (byte) 0x2E, (byte) 0xBF, (byte) 0x31, (byte) 0x14, (byte) 0xEC, (byte) 0x0E, (byte) 0x2F, (byte) 0x52, (byte) 0xEF, (byte) 0x03, (byte) 0x0A, (byte) 0x06, (byte) 0x9E, (byte) 0x04, (byte) 0x0E, (byte) 0x02, (byte) 0xA8, (byte) 0x88, (byte) 0xEE, (byte) 0xE0, (byte) 0x07, (byte) 0x9A, (byte) 0x00, (byte) 0x04, (byte) 0x0A, (byte) 0x1F, (byte) 0x21, (byte) 0x1E, (byte) 0x4E, (byte) 0x2E, (byte) 0xFE, (byte) 0xC6, (byte) 0xC0, (byte) 0x02, (byte) 0xEF, (byte) 0x03, (byte) 0x01, (byte) 0x02, (byte) 0xEE, (byte) 0x11, (byte) 0x03, (byte) 0x0A, (byte) 0xF8, (byte) 0x13, (byte) 0x00, (byte) 0x00, (byte) 0xF0, (byte) 0x40, (byte) 0xBF, (byte) 0xA5, (byte) 0xE7, (byte) 0x00, (byte) 0x76, (byte) 0x00};
    int resolution = 16;
    int channels = 3;

    @Test
    public void test_parseDeltaFrameRefSamples() {
        // Arrange && Act
        List<Integer> refSamples = BlePMDClient.parseDeltaFrameRefSamples(measurementFrame, channels, resolution);
        // Assert
        assertEquals(refSample0Channel0, (int) refSamples.get(0));
        assertEquals(refSample0Channel1, (int) refSamples.get(1));
        assertEquals(refSample0Channel2, (int) refSamples.get(2));
    }

    @Test
    public void test_parseDeltaFrameAllSamples_multipleDeltas() {
        // Arrange && Act
        List<List<Integer>> allSamples = BlePMDClient.parseDeltaFramesAll(measurementFrame, channels, resolution);

        // Assert
        assertEquals(totalSamples_size, allSamples.size());
        assertEquals(3, allSamples.get(0).size());
        assertEquals(refSample0Channel0, (int) allSamples.get(0).get(0));
        assertEquals(refSample0Channel1, (int) allSamples.get(0).get(1));
        assertEquals(refSample0Channel2, (int) allSamples.get(0).get(2));
        assertEquals(refSample1Channel0, (int) allSamples.get(1).get(0));
        assertEquals(refSample1Channel1, (int) allSamples.get(1).get(1));
        assertEquals(refSample1Channel2, (int) allSamples.get(1).get(2));
    }
}
