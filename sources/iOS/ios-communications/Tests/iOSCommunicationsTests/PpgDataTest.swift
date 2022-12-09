//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class PpgDataTest: XCTestCase {
    
    func testRawPpgFrameType0() throws {
        // Arrange
        // HEX: 01 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           01 (PPG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp:UInt64 = 2000000000
        // 10       frame type                                     00 (raw, type 0)
        let ppgDataFrameHeader = Data([
            0x01,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x00,
        ])
        let previousTimeStamp:UInt64 = 100
        let ppgDataFrameContent = Data([
            0x01, 0x02, 0x03,  //PPG0 (197121)
            0x04, 0x05, 0x06,  //PPG1 (394500)
            0xFF, 0xFF, 0x7F,  //PPG2 (8388607)
            0x00, 0x00, 0x00,  //ambient (0)
            0xFF, 0xFF, 0xFF,  //PPG0 (-1)
            0x0F, 0xEF, 0xEF,  //PPG1 (-1052913)
            0x00, 0x00, 0x80,  //PPG2 (-8388608)
            0x0F, 0xEF, 0xEF   //ambient (-1052913)
        ])
        let ppg0Sample0:Int32 = 197121
        let ppg1Sample0:Int32 = 394500
        let ppg2Sample0:Int32 = 8388607
        let ambientSample0:Int32 = 0
        let ppg0Sample1:Int32 = -1
        let ppg1Sample1:Int32 = -1052913
        let ppg2Sample1:Int32 = -8388608
        let ambientSample1:Int32 = -1052913
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: ppgDataFrameHeader + ppgDataFrameContent,
            { _ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })
        
        
        // Act
        let ppgData = try PpgData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(2, ppgData.samples.count)
        XCTAssertEqual(3, ppgData.samples[0].ppgDataSamples.count)
        XCTAssertEqual(ppg0Sample0, ppgData.samples[0].ppgDataSamples[0])
        XCTAssertEqual(ppg1Sample0, ppgData.samples[0].ppgDataSamples[1])
        XCTAssertEqual(ppg2Sample0, ppgData.samples[0].ppgDataSamples[2])
        XCTAssertEqual(ambientSample0, ppgData.samples[0].ambientSample)
        
        XCTAssertEqual(3, ppgData.samples[1].ppgDataSamples.count)
        XCTAssertEqual(ppg0Sample1, ppgData.samples[1].ppgDataSamples[0])
        XCTAssertEqual(ppg1Sample1, ppgData.samples[1].ppgDataSamples[1])
        XCTAssertEqual(ppg2Sample1, ppgData.samples[1].ppgDataSamples[2])
        XCTAssertEqual(ambientSample1, ppgData.samples[1].ambientSample)
        
        XCTAssertEqual(timeStamp, ppgData.timeStamp)
        XCTAssertEqual(timeStamp, ppgData.samples.last?.timeStamp)
    }
    func testCompressePpgFrameType0() throws {
        // Arrange
        // HEX: 01 00 94 35 77 00 00 00 00 80
        // index                                                   data:
        // 0        type                                           01 (PPG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp:UInt64 = 2000000000
        // 10       frame type                                     80 (compressed, type 0)
        let ppgDataFrameHeader = Data([
            0x01,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x80,
        ])
        let previousTimeStamp:UInt64 = 100
        
        // HEX: 2C 2D 00 C2 77 00 D3 D2 FF 3D 88 FF 0A 29 B2 F0 EE 34 11 B2 EC EE 74 11 B1 E8 FE B4 11 B1 E8 FE B4 11 B1 E0 FE 34 12 B0 DC 0E 75 12 B0 D8 0E B5 12 AF D4 1E F5 12 AF D0 1E 35 13 AE CC 2E 75 13 AE C8 2E B5 13 AD C4 3E F5 13 AD BC 3E 75 14 AD BC 3E 75 14 AC B8 4E B5 14 AC B4 4E F5 14 AB B0 5E 35 15 AA AC 6E 75 15 AA A8 6E B5 15 AA A4 6E F5 15 A9 A0 7E 35 16 A9 9C 7E 75 16 A8 98 8E B5 16 A7 94 9E F5 16 A7 90 9E 35 17 A7 8C 9E 75 17 A6 88 AE B5 17 A5 88 BE B5 17 A5 80 BE 35 18 A4 7C CE 75 18 A4 78 CE B5 18 A3 78 DE B5 18 A2 70 EE 35 19 A2 6C EE 75 19 A2 6C EE 75 19 A1 68 FE B5 19 A0 60 0E 36 1A 9F 60 1E 36 1A 9F 5C 1E 76 1A 9F 58 1E B6 1A 9D 54 3E F6 1A
        // index    type                                    data:
        // 0..11:    Reference sample                       0x2C 0x2D 0x00 0xC2 0x77 0x00 0xD3 0xD2 0xFF 0x3D 0x88 0xFF
        //      Sample 0 (aka. reference sample):
        //      channel 0: 2C 2D 00 => 0x002D2C => 11564
        let refSample0Channel0:Int32 = 11564
        //      channel 1: C2 77 00 => 0x0077C2 => 30658
        let refSample0Channel1:Int32 = 30658
        //      channel 2: D3 D2 FF => 0xFFD2D3 => -11565
        let refSample0Channel2:Int32 = -11565
        //      channel 3: 3D 88 FF => 0xFF883D => -30659
        let refSample0Channel3:Int32 = -30659
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
        let refSample1Channel0:Int32 = 178
        let refSample1Channel1:Int32 = -68
        let refSample1Channel2:Int32 = -178
        let refSample1Channel3:Int32 = 68
        let amountOfSamples = 1 + 41 // reference sample + delta samples
        let ppgDataFrameContent = Data([
            0x2C, 0x2D, 0x00, 0xC2, 0x77, 0x00, 0xD3, 0xD2, 0xFF,
            0x3D, 0x88, 0xFF, 0x0A, 0x29, 0xB2, 0xF0, 0xEE, 0x34,
            0x11, 0xB2, 0xEC, 0xEE, 0x74, 0x11, 0xB1, 0xE8, 0xFE,
            0xB4, 0x11, 0xB1, 0xE8, 0xFE, 0xB4, 0x11, 0xB1, 0xE0,
            0xFE, 0x34, 0x12, 0xB0, 0xDC, 0x0E, 0x75, 0x12, 0xB0,
            0xD8, 0x0E, 0xB5, 0x12, 0xAF, 0xD4, 0x1E, 0xF5, 0x12,
            0xAF, 0xD0, 0x1E, 0x35, 0x13, 0xAE, 0xCC, 0x2E, 0x75,
            0x13, 0xAE, 0xC8, 0x2E, 0xB5, 0x13, 0xAD, 0xC4, 0x3E,
            0xF5, 0x13, 0xAD, 0xBC, 0x3E, 0x75, 0x14, 0xAD, 0xBC,
            0x3E, 0x75, 0x14, 0xAC, 0xB8, 0x4E, 0xB5, 0x14, 0xAC,
            0xB4, 0x4E, 0xF5, 0x14, 0xAB, 0xB0, 0x5E, 0x35, 0x15,
            0xAA, 0xAC, 0x6E, 0x75, 0x15, 0xAA, 0xA8, 0x6E, 0xB5,
            0x15, 0xAA, 0xA4, 0x6E, 0xF5, 0x15, 0xA9, 0xA0, 0x7E,
            0x35, 0x16, 0xA9, 0x9C, 0x7E, 0x75, 0x16, 0xA8, 0x98,
            0x8E, 0xB5, 0x16, 0xA7, 0x94, 0x9E, 0xF5, 0x16, 0xA7,
            0x90, 0x9E, 0x35, 0x17, 0xA7, 0x8C, 0x9E, 0x75, 0x17,
            0xA6, 0x88, 0xAE, 0xB5, 0x17, 0xA5, 0x88, 0xBE, 0xB5,
            0x17, 0xA5, 0x80, 0xBE, 0x35, 0x18, 0xA4, 0x7C, 0xCE,
            0x75, 0x18, 0xA4, 0x78, 0xCE, 0xB5, 0x18, 0xA3, 0x78,
            0xDE, 0xB5, 0x18, 0xA2, 0x70, 0xEE, 0x35, 0x19, 0xA2,
            0x6C, 0xEE, 0x75, 0x19, 0xA2, 0x6C, 0xEE, 0x75, 0x19,
            0xA1, 0x68, 0xFE, 0xB5, 0x19, 0xA0, 0x60, 0x0E, 0x36,
            0x1A, 0x9F, 0x60, 0x1E, 0x36, 0x1A, 0x9F, 0x5C, 0x1E,
            0x76, 0x1A, 0x9F, 0x58, 0x1E, 0xB6, 0x1A, 0x9D, 0x54,
            0x3E, 0xF6, 0x1A
        ])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: ppgDataFrameHeader + ppgDataFrameContent,
            { _ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })
        
        // Act
        let ppgData = try PpgData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(amountOfSamples, ppgData.samples.count)
        XCTAssertEqual(3, ppgData.samples[0] .ppgDataSamples.count)
        XCTAssertEqual(refSample0Channel0, ppgData.samples[0] .ppgDataSamples[0])
        XCTAssertEqual(refSample0Channel1, ppgData.samples[0].ppgDataSamples[1])
        XCTAssertEqual(refSample0Channel2, ppgData.samples[0].ppgDataSamples[2])
        XCTAssertEqual(refSample0Channel3, ppgData.samples[0].ambientSample)
        
        XCTAssertEqual(3, ppgData.samples[1].ppgDataSamples.count)
        XCTAssertEqual(refSample0Channel0 + refSample1Channel0, ppgData.samples[1].ppgDataSamples[0])
        XCTAssertEqual(refSample0Channel1 + refSample1Channel1, ppgData.samples[1].ppgDataSamples[1])
        XCTAssertEqual(refSample0Channel2 + refSample1Channel2, ppgData.samples[1].ppgDataSamples[2])
        XCTAssertEqual(refSample0Channel3 + refSample1Channel3, ppgData.samples[1].ambientSample)
        
        XCTAssertEqual(timeStamp, ppgData.timeStamp)
        XCTAssertEqual(timeStamp, ppgData.samples.last?.timeStamp)
    
    }
}
