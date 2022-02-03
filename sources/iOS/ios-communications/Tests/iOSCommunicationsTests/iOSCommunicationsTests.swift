//  Copyright © 2021 Polar. All rights reserved.

import XCTest
import iOSCommunications
import RxSwift
import CoreBluetooth

class iOSCommunicationsTests: XCTestCase {
    
    override func setUp() {
        super.setUp()
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }
    
    override func tearDown() {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
        super.tearDown()
    }
    
    fileprivate func assertEquals(_ lhs: Float, rhs: Float, delta: Float)
    {
        let diff = lhs - rhs
        XCTAssert(diff <= delta && diff >= (delta * -1.0))
    }
    
    func testPmdDeltaFrameDecoder() {
        let data = Data([0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0x03,0x03,0xAA,0xAA,0xCC,0xCF,0x03,0x02,0xAA,0xAA,0xCC])
        let samples = Pmd.buildFromDeltaFrame3Axis(data, resolution: 14, timeStamp: 0)
        XCTAssert(samples.samples.count == 6)
        XCTAssert(samples.samples[0].x == -1)
        XCTAssert(samples.samples[0].y == -1)
        XCTAssert(samples.samples[0].z == -1)
        XCTAssert(samples.samples[1].x == 1)
        XCTAssert(samples.samples[1].y == -4)
        XCTAssert(samples.samples[1].z == 1)
        XCTAssert(samples.samples[2].x == -2)
        XCTAssert(samples.samples[2].y == -2)
        XCTAssert(samples.samples[2].z == 2)
        XCTAssert(samples.samples[3].x == 1)
        XCTAssert(samples.samples[3].y == -4)
        XCTAssert(samples.samples[3].z == 1)
        XCTAssert(samples.samples[4].x == 3)
        XCTAssert(samples.samples[4].y == -7)
        XCTAssert(samples.samples[4].z == 3)
        XCTAssert(samples.samples[5].x == 0)
        XCTAssert(samples.samples[5].y == -5)
        XCTAssert(samples.samples[5].z == 4)
    }
    
    func testPmdParseThreeAxisDataWithResolution16() {
        // Arrange
        // HEX: 71 07 F0 6A 9E 8D 0A 38 BE 5C BE BA 2F 96 B3 EE 4B E5 AD FB 42 B9 EB BE 4C FE BA 2F 92 BF EE 4B E4 B1 FB 12 B9 EC BD 3C 3E BB 2F 8F D3 DE 4B E3 B5 F7 D2 B8 ED BD 30 7E 7B 2F 8B E3 CE 8B E2 BA F7 A2 B8 EE BC 20 BE 7B 2F 88 F3 CE CB E1 BD EF 52 F8 EF BC 18 FE 3B 2F 84 03 BF CB E0 C2 EF 32 B8 F0 BB 04 4E BC 2E 81 13 AF 0B E0 C6 EF F2 F7 F1 B9 FC 7D BC 2E 7D 27 9F 4B DF CA EB C2 F7 F2 B8 EC CD 7C 2E 7B 37 8F 4B DE CE E3 92 F7 F3 B8 E0 0D FD 2D 77 4B 7F CB DD D2 DF 62 37 F5 B7 D4 4D BD 2D 74 5B 6F CB DC D7 D7 32 37 F6 B5 C8 8D 7D 2D 71 6B 4F 4B DC DC D3 F2 36 F7 B4 BC DD FD 2C 6F 7B 3F 4B DB E0 CF D2 36 F8 B2 B0 2D BE 2C 6C 8F 1F CB DA E3 C7 A2 76 F9
        // index    type                                            data:
        // 0-5:   Reference sample                     size 6:      71 07 F0 6A 9E 8D
        //      Sample 0 (aka. reference sample):
        //      channel 0: 71 07 =>0x0771 => 1905
        let refSample0Channel0 = Float(1905.0)
        //      channel 1: F0 6A => 0x6AF0 => 27376
        let refSample0Channel1 = Float(27376.0)
        //      channel 2: 9E 8D => 0x8D9E => -29282
        let refSample0Channel2 = Float(-29282.0)
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
        let refSample1Channel0 = Float(190)
        let refSample1Channel1 = Float(-105)
        let refSample1Channel2 = Float(-85)
        let amountOfSamples = 1 + 56 // reference sample + delta samples
        let measurementFrame = Data([0x71,  0x07, 0xF0, 0x6A, 0x9E, 0x8D, 0x0A, 0x38, 0xBE, 0x5C, 0xBE, 0xBA, 0x2F, 0x96, 0xB3, 0xEE, 0x4B, 0xE5, 0xAD, 0xFB, 0x42, 0xB9, 0xEB, 0xBE, 0x4C, 0xFE, 0xBA, 0x2F, 0x92, 0xBF, 0xEE, 0x4B, 0xE4, 0xB1, 0xFB, 0x12, 0xB9, 0xEC, 0xBD, 0x3C, 0x3E, 0xBB, 0x2F, 0x8F, 0xD3, 0xDE, 0x4B, 0xE3, 0xB5, 0xF7, 0xD2, 0xB8, 0xED, 0xBD, 0x30, 0x7E, 0x7B, 0x2F, 0x8B, 0xE3, 0xCE, 0x8B, 0xE2, 0xBA, 0xF7, 0xA2, 0xB8, 0xEE, 0xBC, 0x20, 0xBE, 0x7B, 0x2F, 0x88, 0xF3, 0xCE, 0xCB, 0xE1, 0xBD, 0xEF, 0x52, 0xF8, 0xEF, 0xBC, 0x18, 0xFE, 0x3B, 0x2F, 0x84, 0x03, 0xBF, 0xCB, 0xE0, 0xC2, 0xEF, 0x32, 0xB8, 0xF0, 0xBB, 0x04, 0x4E, 0xBC, 0x2E, 0x81, 0x13, 0xAF, 0x0B, 0xE0, 0xC6, 0xEF, 0xF2, 0xF7, 0xF1, 0xB9, 0xFC, 0x7D, 0xBC, 0x2E, 0x7D, 0x27, 0x9F, 0x4B, 0xDF, 0xCA, 0xEB, 0xC2, 0xF7, 0xF2, 0xB8, 0xEC, 0xCD, 0x7C, 0x2E, 0x7B, 0x37, 0x8F, 0x4B, 0xDE, 0xCE, 0xE3, 0x92, 0xF7, 0xF3, 0xB8, 0xE0, 0x0D, 0xFD, 0x2D, 0x77, 0x4B, 0x7F, 0xCB, 0xDD, 0xD2, 0xDF, 0x62, 0x37, 0xF5, 0xB7, 0xD4, 0x4D, 0xBD, 0x2D, 0x74, 0x5B, 0x6F, 0xCB, 0xDC, 0xD7, 0xD7, 0x32, 0x37, 0xF6, 0xB5, 0xC8, 0x8D, 0x7D, 0x2D, 0x71, 0x6B, 0x4F, 0x4B, 0xDC, 0xDC, 0xD3, 0xF2, 0x36, 0xF7, 0xB4, 0xBC, 0xDD, 0xFD, 0x2C, 0x6F, 0x7B, 0x3F, 0x4B, 0xDB, 0xE0, 0xCF, 0xD2, 0x36, 0xF8, 0xB2, 0xB0, 0x2D, 0xBE, 0x2C, 0x6C, 0x8F, 0x1F, 0xCB, 0xDA, 0xE3, 0xC7, 0xA2, 0x76, 0xF9]);
        let resolution = 16
        let range = 8
        let factor = Float(0.000244)//E-4f;
        let timeStamp = 0

        // Act
        let threeAxisDeltaFramedData = Pmd.buildFromDeltaFrame3Axis(measurementFrame, resolution: UInt8(resolution), factor: Float(factor), timeStamp: UInt64(timeStamp))
        
        // Assert
        assertEquals((factor * refSample0Channel0), rhs: threeAxisDeltaFramedData.samples[0].x, delta: 0.001)
        assertEquals((factor * refSample0Channel1), rhs: threeAxisDeltaFramedData.samples[0].y, delta: 0.001)
        assertEquals((factor * refSample0Channel2), rhs: threeAxisDeltaFramedData.samples[0].z, delta: 0.001)

        assertEquals((factor * (refSample0Channel0 + refSample1Channel0)), rhs: threeAxisDeltaFramedData.samples[1].x, delta: 0.000000001)
        assertEquals((factor * (refSample0Channel1 + refSample1Channel1)), rhs: threeAxisDeltaFramedData.samples[1].y, delta: 0.000000001)
        assertEquals((factor * (refSample0Channel2 + refSample1Channel2)), rhs: threeAxisDeltaFramedData.samples[1].z, delta: 0.000000001)

        // validate data in range
        threeAxisDeltaFramedData.samples.forEach { (arg0) in
            let (x, y, z) = arg0
            XCTAssertTrue(abs(Int(x)) <= range)
            XCTAssertTrue(abs(Int(y)) <= range)
            XCTAssertTrue(abs(Int(z)) <= range)
        }

        // validate data size
        XCTAssertEqual(amountOfSamples, threeAxisDeltaFramedData.samples.count)
    }
    
    func testPPIDataSample()
    {
        // Arrange
        // HEX:  80 80 80 80 80 FF 00 01 00 01 00 00
        // index    type                                            data:
        // 0        HR                                              0x80 (128)
        let heartRate = 128
        // 1..2     PP                                              0x80 0x80 (32896)
        let intervalInMs = 32896
        // 3..4     PP Error Estimate                               0x80 0x80 (32896)
        let errorEstimate = 32896
        // 5        PP flags                                        0xFF
        let ppFlags = 0xFF
        let blockerBit = (ppFlags & 0x01 != 0) ? 0x01 : 0x00
        let skinContactStatus = (ppFlags & 0x02 != 0) ? 0x01 : 0x00
        let skinContactSupported = (ppFlags & 0x04 != 0) ? 0x01 : 0x00

        // 6        HR                                              0x00 (0)
        let heartRate2 = 0
        // 7..8     PP                                              0x01 0x00 (1)
        let intervalInMs2 = 1
        // 9..10     PP Error Estimate                              0x01 0x00 (1)
        let errorEstimate2 = 1
        // 11        PP flags                                       0x00
        let ppFlags2 = 0x00
        let blockerBit2 = (ppFlags2 & 0x01 != 0) ? 0x01 : 0x00
        let skinContactStatus2 = (ppFlags2 & 0x02 != 0) ? 0x01 : 0x00
        let skinContactSupported2 = (ppFlags2 & 0x04 != 0) ? 0x01 : 0x00

        let measurementFrame = Data([0x80, 0x80, 0x80, 0x80, 0x80, 0xFF, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00])
        let timeStamp = UInt64.max

        // Act
        let ppiData = Pmd.buildPpiSamples(measurementFrame, timeStamp: timeStamp)

        // Assert
        XCTAssertEqual(heartRate, ppiData.samples[0].hr)
        XCTAssertEqual(intervalInMs, Int(ppiData.samples[0].ppInMs))
        XCTAssertEqual(errorEstimate, Int(ppiData.samples[0].ppErrorEstimate))
        XCTAssertEqual(blockerBit, ppiData.samples[0].blockerBit)
        XCTAssertEqual(skinContactStatus, ppiData.samples[0].skinContactStatus)
        XCTAssertEqual(skinContactSupported, ppiData.samples[0].skinContactSupported)

        XCTAssertEqual(heartRate2, ppiData.samples[1].hr)
        XCTAssertEqual(intervalInMs2, Int(ppiData.samples[1].ppInMs))
        XCTAssertEqual(errorEstimate2, Int(ppiData.samples[1].ppErrorEstimate))
        XCTAssertEqual(blockerBit2, ppiData.samples[1].blockerBit)
        XCTAssertEqual(skinContactStatus2, ppiData.samples[1].skinContactStatus)
        XCTAssertEqual(skinContactSupported2, ppiData.samples[1].skinContactSupported)

        XCTAssertEqual(2, ppiData.samples.count)
    }
    
    func testPPGDataSampleType0() {
        // Arrange
        let timeStamp: UInt64 = 0
        let measurementFrame = Data([
                0x01, 0x02, 0x03,  //PPG0 (197121)
                0x04, 0x05, 0x06,  //PPG1 (394500)
                0xFF, 0xFF, 0x7F,  //PPG2 (8388607)
                0x00, 0x00, 0x00,  //ambient (0)
                0xFF, 0xFF, 0xFF,  //PPG0 (-1)
                0x0F, 0xEF, 0xEF,  //PPG1 (-1052913)
                0x00, 0x00, 0x80,  //PPG2 (-8388608)
                0x0F, 0xEF, 0xEF])  //ambient (-1052913)
        let ppg0_0: Int32 = 197121
        let ppg1_0: Int32  = 394500
        let ppg2_0: Int32  = 8388607
        let ambient_0: Int32  = 0
        let ppg0_1: Int32  = -1
        let ppg1_1: Int32  = -1052913
        let ppg2_1: Int32  = -8388608
        let ambient_1: Int32  = -1052913

        // Act
        let ppgData = Pmd.buildPpgSamples(measurementFrame, timeStamp: timeStamp)

        // Assert
        XCTAssertEqual(2, ppgData.samples.count)
        XCTAssertEqual(4, ppgData.channels)

        XCTAssertEqual(4, ppgData.samples[0].count)
        XCTAssertEqual(ppg0_0, ppgData.samples[0][0])
        XCTAssertEqual(ppg1_0, ppgData.samples[0][1])
        XCTAssertEqual(ppg2_0, ppgData.samples[0][2])
        XCTAssertEqual(ambient_0, ppgData.samples[0][3])

        XCTAssertEqual(4, ppgData.samples[1].count)
        XCTAssertEqual(ppg0_1, ppgData.samples[1][0])
        XCTAssertEqual(ppg1_1, ppgData.samples[1][1])
        XCTAssertEqual(ppg2_1, ppgData.samples[1][2])
        XCTAssertEqual(ambient_1, ppgData.samples[1][3])
    }

    func testPPGDataSampleDelta() {
        // Arrange
        // HEX: 2C 2D 00 C2 77 00 D3 D2 FF 3D 88 FF 0A 29 B2 F0 EE 34 11 B2 EC EE 74 11 B1 E8 FE B4 11 B1 E8 FE B4 11 B1 E0 FE 34 12 B0 DC 0E 75 12 B0 D8 0E B5 12 AF D4 1E F5 12 AF D0 1E 35 13 AE CC 2E 75 13 AE C8 2E B5 13 AD C4 3E F5 13 AD BC 3E 75 14 AD BC 3E 75 14 AC B8 4E B5 14 AC B4 4E F5 14 AB B0 5E 35 15 AA AC 6E 75 15 AA A8 6E B5 15 AA A4 6E F5 15 A9 A0 7E 35 16 A9 9C 7E 75 16 A8 98 8E B5 16 A7 94 9E F5 16 A7 90 9E 35 17 A7 8C 9E 75 17 A6 88 AE B5 17 A5 88 BE B5 17 A5 80 BE 35 18 A4 7C CE 75 18 A4 78 CE B5 18 A3 78 DE B5 18 A2 70 EE 35 19 A2 6C EE 75 19 A2 6C EE 75 19 A1 68 FE B5 19 A0 60 0E 36 1A 9F 60 1E 36 1A 9F 5C 1E 76 1A 9F 58 1E B6 1A 9D 54 3E F6 1A
        // index    type                                            data:
        // 0-5:    Reference sample                     size 6:    0x2C 0x2D 0x00 0xC2 0x77 0x00 0xD3 0xD2 0xFF 0x3D 0x88 0xFF
        //      Sample 0 (aka. reference sample):
        //      channel 0: 2C 2D 00 => 0x002D2C => 11564
        let refSample0Channel0: Float = 11564
        //      channel 1: C2 77 00 => 0x0077C2 => 30658
        let refSample0Channel1: Float = 30658
        //      channel 2: D3 D2 FF => 0xFFD2D3 => -11565
        let refSample0Channel2: Float = -11565
        //      channel 3: 3D 88 FF => 0xFF883D => -30659
        let refSample0Channel3: Float = -30659
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
        let refSample1Channel0: Float = 178
        let refSample1Channel1: Float = -68
        let refSample1Channel2: Float = -178
        let refSample1Channel3: Float = 68
        let amountOfSamples = 1 + 41 // reference sample + delta samples
        let measurementFrame = Data([0x2C, 0x2D, 0x00, 0xC2, 0x77, 0x00, 0xD3, 0xD2, 0xFF, 0x3D, 0x88, 0xFF, 0x0A, 0x29, 0xB2, 0xF0, 0xEE, 0x34, 0x11, 0xB2, 0xEC, 0xEE, 0x74, 0x11, 0xB1, 0xE8, 0xFE, 0xB4, 0x11, 0xB1, 0xE8, 0xFE, 0xB4, 0x11, 0xB1, 0xE0, 0xFE, 0x34, 0x12, 0xB0, 0xDC, 0x0E, 0x75, 0x12, 0xB0, 0xD8, 0x0E, 0xB5, 0x12, 0xAF, 0xD4, 0x1E, 0xF5, 0x12, 0xAF, 0xD0, 0x1E, 0x35, 0x13, 0xAE, 0xCC, 0x2E, 0x75, 0x13, 0xAE, 0xC8, 0x2E, 0xB5, 0x13, 0xAD, 0xC4, 0x3E, 0xF5, 0x13, 0xAD, 0xBC, 0x3E, 0x75, 0x14, 0xAD, 0xBC, 0x3E, 0x75, 0x14, 0xAC, 0xB8, 0x4E, 0xB5, 0x14, 0xAC, 0xB4, 0x4E, 0xF5, 0x14, 0xAB, 0xB0, 0x5E, 0x35, 0x15, 0xAA, 0xAC, 0x6E, 0x75, 0x15, 0xAA, 0xA8, 0x6E, 0xB5, 0x15, 0xAA, 0xA4, 0x6E, 0xF5, 0x15, 0xA9, 0xA0, 0x7E, 0x35, 0x16, 0xA9, 0x9C, 0x7E, 0x75, 0x16, 0xA8, 0x98, 0x8E, 0xB5, 0x16, 0xA7, 0x94, 0x9E, 0xF5, 0x16, 0xA7, 0x90, 0x9E, 0x35, 0x17, 0xA7, 0x8C, 0x9E, 0x75, 0x17, 0xA6, 0x88, 0xAE, 0xB5, 0x17, 0xA5, 0x88, 0xBE, 0xB5, 0x17, 0xA5, 0x80, 0xBE, 0x35, 0x18, 0xA4, 0x7C, 0xCE, 0x75, 0x18, 0xA4, 0x78, 0xCE, 0xB5, 0x18, 0xA3, 0x78, 0xDE, 0xB5, 0x18, 0xA2, 0x70, 0xEE, 0x35, 0x19, 0xA2, 0x6C, 0xEE, 0x75, 0x19, 0xA2, 0x6C, 0xEE, 0x75, 0x19, 0xA1, 0x68, 0xFE, 0xB5, 0x19, 0xA0, 0x60, 0x0E, 0x36, 0x1A, 0x9F, 0x60, 0x1E, 0x36, 0x1A, 0x9F, 0x5C, 0x1E, 0x76, 0x1A, 0x9F, 0x58, 0x1E, 0xB6, 0x1A, 0x9D, 0x54, 0x3E, 0xF6, 0x1A])
        let factor: Float = 1.0
        let channels = 4
        let resolution = 22
        let timeStamp: UInt64 = 0

        // Act
        let ppgData = Pmd.buildFromDeltaFramePPG(measurementFrame, resolution: UInt8(resolution), channels: UInt8(channels), timeStamp: timeStamp)

        // Assert
        XCTAssertEqual(amountOfSamples, ppgData.samples.count)
        XCTAssertEqual(4, ppgData.channels)
        XCTAssertEqual(4, ppgData.samples[0].count)
        XCTAssertEqual(Int32(factor * refSample0Channel0), ppgData.samples[0][0])
        XCTAssertEqual(Int32(factor * refSample0Channel1), ppgData.samples[0][1])
        XCTAssertEqual(Int32(factor * refSample0Channel2), ppgData.samples[0][2])
        XCTAssertEqual(Int32(factor * refSample0Channel3), ppgData.samples[0][3])
        XCTAssertEqual(Int32(factor * (refSample0Channel0 + refSample1Channel0)), ppgData.samples[1][0])
        XCTAssertEqual(Int32(factor * (refSample0Channel1 + refSample1Channel1)), ppgData.samples[1][1])
        XCTAssertEqual(Int32(factor * (refSample0Channel2 + refSample1Channel2)), ppgData.samples[1][2])
        XCTAssertEqual(Int32(factor * (refSample0Channel3 + refSample1Channel3)), ppgData.samples[1][3])
        XCTAssertEqual(4, ppgData.samples[1].count)
    }
    
    func testEcgDataSampletype0() {
        // Arrange
        // HEX: 02 08 FF 02 80 00
        // index    type                                            data:
        // 0
        let type: UInt8 = 0
        // 1..3     uVolts                                          02 80 FF (-32766)
        let ecgValue1: Int32 = -32766
        // 4..6     uVolts                                          02 80 00 (32770)
        let ecgValue2: Int32 = 32770
        let measurementFrame = Data([0x02, 0x80, 0xFF, 0x02, 0x80, 0x00])
        let timeStamp = UInt64.max

        // Act
        let ecgData = Pmd.buildEcgSamples(type, data: measurementFrame, timeStamp: timeStamp)

        // Assert
        XCTAssertEqual(timeStamp, ecgData.timeStamp)
        XCTAssertEqual(ecgValue1, ecgData.samples[0])
        XCTAssertEqual(ecgValue2, ecgData.samples[1])
        XCTAssertEqual(2, ecgData.samples.count)
    }
    
    func testparseAccDataWithResolution16() {
      // Arrange
      // HEX: 71 07 F0 6A 9E 8D 0A 38 BE 5C BE BA 2F 96 B3 EE 4B E5 AD FB 42 B9 EB BE 4C FE BA 2F 92 BF EE 4B E4 B1 FB 12 B9 EC BD 3C 3E BB 2F 8F D3 DE 4B E3 B5 F7 D2 B8 ED BD 30 7E 7B 2F 8B E3 CE 8B E2 BA F7 A2 B8 EE BC 20 BE 7B 2F 88 F3 CE CB E1 BD EF 52 F8 EF BC 18 FE 3B 2F 84 03 BF CB E0 C2 EF 32 B8 F0 BB 04 4E BC 2E 81 13 AF 0B E0 C6 EF F2 F7 F1 B9 FC 7D BC 2E 7D 27 9F 4B DF CA EB C2 F7 F2 B8 EC CD 7C 2E 7B 37 8F 4B DE CE E3 92 F7 F3 B8 E0 0D FD 2D 77 4B 7F CB DD D2 DF 62 37 F5 B7 D4 4D BD 2D 74 5B 6F CB DC D7 D7 32 37 F6 B5 C8 8D 7D 2D 71 6B 4F 4B DC DC D3 F2 36 F7 B4 BC DD FD 2C 6F 7B 3F 4B DB E0 CF D2 36 F8 B2 B0 2D BE 2C 6C 8F 1F CB DA E3 C7 A2 76 F9
      // index    type                                            data:
      // 0-5:    Reference sample                     size 6:    0xC9 0xFF 0x12 0x00 0x11 0x00
      //      Sample 0 (aka. reference sample):
      //      channel 0: 71 07 => 0x0771 => 1905
      let refSample0Channel0: Float = 1905
      //      channel 1: F0 6A => 0x6AF0 => 27376
      let refSample0Channel1: Float = 27376
      //      channel 2: 9E 8D => 0x8D9E => -29282
      let refSample0Channel2: Float = -29282
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
      let refSample1Channel0: Float = 190
      let refSample1Channel1: Float = -105
      let refSample1Channel2: Float = -85
      let amountOfSamples = 1 + 56 // reference sample + delta samples

      let measurementFrame = Data([0x71, 0x07, 0xF0, 0x6A, 0x9E, 0x8D, 0x0A, 0x38, 0xBE, 0x5C, 0xBE, 0xBA, 0x2F, 0x96, 0xB3, 0xEE, 0x4B, 0xE5, 0xAD, 0xFB, 0x42, 0xB9, 0xEB, 0xBE, 0x4C, 0xFE, 0xBA, 0x2F, 0x92, 0xBF, 0xEE, 0x4B, 0xE4, 0xB1, 0xFB, 0x12, 0xB9, 0xEC, 0xBD, 0x3C, 0x3E, 0xBB, 0x2F, 0x8F, 0xD3, 0xDE, 0x4B, 0xE3, 0xB5, 0xF7, 0xD2, 0xB8, 0xED, 0xBD, 0x30, 0x7E, 0x7B, 0x2F, 0x8B, 0xE3, 0xCE, 0x8B, 0xE2, 0xBA, 0xF7, 0xA2, 0xB8, 0xEE, 0xBC, 0x20, 0xBE, 0x7B, 0x2F, 0x88, 0xF3, 0xCE, 0xCB, 0xE1, 0xBD, 0xEF, 0x52, 0xF8, 0xEF, 0xBC, 0x18, 0xFE, 0x3B, 0x2F, 0x84, 0x03, 0xBF, 0xCB, 0xE0, 0xC2, 0xEF, 0x32, 0xB8, 0xF0, 0xBB, 0x04, 0x4E, 0xBC, 0x2E, 0x81, 0x13, 0xAF, 0x0B, 0xE0, 0xC6, 0xEF, 0xF2, 0xF7, 0xF1, 0xB9, 0xFC, 0x7D, 0xBC, 0x2E, 0x7D, 0x27, 0x9F, 0x4B, 0xDF, 0xCA, 0xEB, 0xC2, 0xF7, 0xF2, 0xB8, 0xEC, 0xCD, 0x7C, 0x2E, 0x7B, 0x37, 0x8F, 0x4B, 0xDE, 0xCE, 0xE3, 0x92, 0xF7, 0xF3, 0xB8, 0xE0, 0x0D, 0xFD, 0x2D, 0x77, 0x4B, 0x7F, 0xCB, 0xDD, 0xD2, 0xDF, 0x62, 0x37, 0xF5, 0xB7, 0xD4, 0x4D, 0xBD, 0x2D, 0x74, 0x5B, 0x6F, 0xCB, 0xDC, 0xD7, 0xD7, 0x32, 0x37, 0xF6, 0xB5, 0xC8, 0x8D, 0x7D, 0x2D, 0x71, 0x6B, 0x4F, 0x4B, 0xDC, 0xDC, 0xD3, 0xF2, 0x36, 0xF7, 0xB4, 0xBC, 0xDD, 0xFD, 0x2C, 0x6F, 0x7B, 0x3F, 0x4B, 0xDB, 0xE0, 0xCF, 0xD2, 0x36, 0xF8, 0xB2, 0xB0, 0x2D, 0xBE, 0x2C, 0x6C, 0x8F, 0x1F, 0xCB, 0xDA, 0xE3, 0xC7, 0xA2, 0x76, 0xF9])
      let resolution = 16
      let range = 8
      let factor: Float = 0.244
      let timeStamp: UInt64 = 0

      // Act
      let accData1 = Pmd.buildFromDeltaFrame3Axis(measurementFrame, resolution: UInt8(resolution), factor: factor, timeStamp: timeStamp)
        let accData = (accData1.timeStamp, samples: accData1.samples.map({ (arg0) -> (x:Int32, y: Int32, z: Int32)  in
          let (x, y, z) = arg0
          return (Int32(x),Int32(y),Int32(z))
      }))

      // Assert
      XCTAssertEqual(Int32(factor * refSample0Channel0), accData.samples[0].x)
      XCTAssertEqual(Int32(factor * refSample0Channel1), accData.samples[0].y)
      XCTAssertEqual(Int32(factor * refSample0Channel2), accData.samples[0].z)

      XCTAssertEqual(Int32((factor * (refSample0Channel0 + refSample1Channel0))), accData.samples[1].x)
      XCTAssertEqual(Int32((factor * (refSample0Channel1 + refSample1Channel1))), accData.samples[1].y)
      XCTAssertEqual(Int32((factor * (refSample0Channel2 + refSample1Channel2))), accData.samples[1].z)

      accData.samples.forEach { (arg0) in
          let (x, y, z) = arg0
          XCTAssertTrue(abs(x) <= (range * 1000))
          XCTAssertTrue(abs(y) <= (range * 1000))
          XCTAssertTrue(abs(z) <= (range * 1000))
      }
        
      // validate data size
      XCTAssertEqual(amountOfSamples, accData.samples.count)
   }

   func testPmdSettingsWithRange() {
        //Arrange
        let bytes = Data([0x00, 0x01, 0x34, 0x00, 0x01, 0x01, 0x10, 0x00, 0x02, 0x04, 0xF5, 0x00, 0xF4, 0x01, 0xE8, 0x03, 0xD0, 0x07, 0x04, 0x01, 0x03])
        // Parameters
        // Setting Type : 00 (Sample Rate)
        // array_length : 01
        // array of settings values: 34 00 (52Hz)
        let sampleRate: UInt32 = 52
        //Setting Type : 01 (Resolution)
        // array_length : 01
        // array of settings values: 10 00 (16)
        let resolution: UInt32 = 16
        // Setting Type : 02 (Range)
        // array_length : 04
        // array of settings values: F5 00 (245)
        let range1: UInt32 = 245
        // array of settings values: F4 01 (500)
        let range2: UInt32 = 500
        // array of settings values: E8 03 (1000)
        let range3: UInt32 = 1000
        // array of settings values: D0 07 (2000)
        let range4: UInt32 = 2000
        // Setting Type : 04 (Channels)
        // array_length : 01
        // array of settings values: 03 (3 Channels)
        let channels: UInt32 = 3
        let numberOfSettings = 4

        //Act
        let pmdSetting = Pmd.PmdSetting(bytes)

        // Assert
        XCTAssertEqual(numberOfSettings, pmdSetting.settings.count)

        XCTAssertEqual(sampleRate, pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.sampleRate]!.first!)
    
        XCTAssertEqual(1, pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.sampleRate]!.count)
    
        XCTAssertEqual(resolution, pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.resolution]!.first!)
    
        XCTAssertEqual(1, pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.resolution]!.count)
    
        XCTAssertTrue(pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.range]!.contains(range1))
        XCTAssertTrue(pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.range]!.contains(range2))
        XCTAssertTrue(pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.range]!.contains(range3))
        XCTAssertTrue(pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.range]!.contains(range4))
        XCTAssertEqual(4, pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.range]!.count)

        XCTAssertEqual(channels, pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.channels]!.first!)
    
        XCTAssertEqual(1,  pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.channels]!.count)
    
        XCTAssertNil(pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.rangeMilliUnit])
        XCTAssertNil(pmdSetting.settings[Pmd.PmdSetting.PmdSettingType.factor])
    }
    
    func testPmdSelectedSerialization() {
       //Arrange
       var selected = [Pmd.PmdSetting.PmdSettingType : UInt32]()
       let sampleRate: UInt32 = 0xFFFF
       selected[Pmd.PmdSetting.PmdSettingType.sampleRate] = sampleRate
       let resolution: UInt32 = 0
       selected[Pmd.PmdSetting.PmdSettingType.resolution] = resolution
       let range: UInt32 = 15
       selected[Pmd.PmdSetting.PmdSettingType.range] = range
       let rangeMilliUnit = UInt32.max
       selected[Pmd.PmdSetting.PmdSettingType.rangeMilliUnit] = rangeMilliUnit
       let channels: UInt32 = 4
       selected[Pmd.PmdSetting.PmdSettingType.channels] = channels
       let factor: UInt32 = 15
       selected[Pmd.PmdSetting.PmdSettingType.factor] = factor
       let numberOfSettings = 5

       //Act
       let settingsFromSelected = Pmd.PmdSetting.init(selected)
       let serializedSelected = settingsFromSelected.serialize()
       let settings = Pmd.PmdSetting(serializedSelected)

       //Assert
       XCTAssertEqual(numberOfSettings, settings.settings.count)
       XCTAssertTrue(settings.settings[Pmd.PmdSetting.PmdSettingType.sampleRate]!.contains(sampleRate))
       XCTAssertEqual(1, settings.settings[Pmd.PmdSetting.PmdSettingType.sampleRate]!.count)
       XCTAssertTrue(settings.settings[Pmd.PmdSetting.PmdSettingType.resolution]!.contains(resolution))
       XCTAssertTrue(settings.settings[Pmd.PmdSetting.PmdSettingType.range]!.contains(range));
       XCTAssertTrue(settings.settings[Pmd.PmdSetting.PmdSettingType.rangeMilliUnit]!.contains(rangeMilliUnit))
       XCTAssertTrue(settings.settings[Pmd.PmdSetting.PmdSettingType.channels]!.contains(channels))
       XCTAssertNil(settings.settings[Pmd.PmdSetting.PmdSettingType.factor])
   }
    
   func testPmdSetting() {
        
        let data = Data([Pmd.PmdSetting.PmdSettingType.rangeMilliUnit.rawValue,
                         0x02,0xFF,0xFF,0xFF,0xFF,0xFF,0x00,0x00,0x00,
                         Pmd.PmdSetting.PmdSettingType.resolution.rawValue,
                         0x01,0x0E,0x00])
        let setting = Pmd.PmdSetting(data)
        XCTAssertEqual(setting.settings.count, 2)
        XCTAssertNotNil(setting.settings[Pmd.PmdSetting.PmdSettingType.rangeMilliUnit])
        XCTAssertNotNil(setting.settings[Pmd.PmdSetting.PmdSettingType.resolution])
        XCTAssertEqual(setting.settings[Pmd.PmdSetting.PmdSettingType.rangeMilliUnit]!.count, 2)
        XCTAssertEqual(setting.settings[Pmd.PmdSetting.PmdSettingType.resolution]!.count, 1)
        XCTAssertTrue(setting.settings[Pmd.PmdSetting.PmdSettingType.rangeMilliUnit]!.contains(0xffffffff))
        XCTAssertTrue(setting.settings[Pmd.PmdSetting.PmdSettingType.rangeMilliUnit]!.contains(0xff))
        XCTAssertTrue(setting.settings[Pmd.PmdSetting.PmdSettingType.resolution]!.contains(0x0E))
        
        let serialized = setting.serialize()
        XCTAssertEqual(serialized.count, 10)
    }
}
