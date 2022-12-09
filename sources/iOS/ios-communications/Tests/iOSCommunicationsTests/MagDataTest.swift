//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class MagDataTest: XCTestCase {
    
    func testProcessMagnetometerCompressedDataType0() throws {
        // Arrange
        // HEX: 06 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           06 (MAG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp:UInt64 = 2000000000
        // 10       frame type                                     80 (compressed, type 0)
        
        let magDataFrameHeader = Data([
            0x06,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x80,
        ])
        let previousTimeStamp:UInt64 = 100
        
        // HEX: E2 E6 FA 15 49 0A 06 01 7F 20 FC
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  E2 E6 (0xE6E2 = -6430)
        // 1..2     Sample 0 - channel 1 (ref. sample)  FA 15 (0x15FA = 5626)
        // 3..4     Sample 0 - channel 2 (ref. sample)  49 0A (0x0A49 = 2633)
        // 5        Delta size                          06 (6 bit)
        // 6        Sample amount                       01 (1 samples)
        // 7..      Delta data                          7F (binary: 01 111111) 20 (binary: 0010 0000) FC (binary: 111111 00)
        // Delta channel 0                              111111b
        // Delta channel 1                              000001b
        // Delta channel 2                              000010b
        let expectedSamplesSize = 1 + 1 // reference sample + delta samples
        let magDataFrameContent = Data([
            0xE2, 0xE6, 0xFA, 0x15, 0x49, 0x0A,
            0x06, 0x01, 0x7F, 0x20, 0xFC
        ])
        
        let sample0channel0:Float = -6430.0
        let sample0channel1:Float = 5626.0
        let sample0channel2:Float = 2633.0
        
        let sample1channel0 = sample0channel0 - 0x01
        let sample1channel1 = sample0channel1 + 0x01
        let sample1channel2 = sample0channel2 + 0x02
        
        let factor:Float = 1.0
        
        
        let dataFrame = try PmdDataFrame(
            data: magDataFrameHeader + magDataFrameContent,
            { _ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })
        
        // Act
        let magData = try MagData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSamplesSize, magData.samples.count)
        
        XCTAssertEqual(sample0channel0, magData.samples[0].x)
        XCTAssertEqual(sample0channel1, magData.samples[0].y)
        XCTAssertEqual(sample0channel2, magData.samples[0].z)
        
        XCTAssertEqual(sample1channel0, magData.samples[1].x)
        XCTAssertEqual(sample1channel1, magData.samples[1].y)
        XCTAssertEqual(sample1channel2, magData.samples[1].z)
        
        XCTAssertEqual(timeStamp, magData.timeStamp)
        XCTAssertEqual(timeStamp, magData.samples[1].timeStamp)
    }
}
