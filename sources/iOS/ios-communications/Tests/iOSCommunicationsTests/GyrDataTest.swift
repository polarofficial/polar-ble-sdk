//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class GyrDataTest: XCTestCase {
    
    func testProcessCompressedDataType0() throws {
        // Arrange
        // HEX: 05 FF FF FF FF FF FF FF 7F 80
        // index                                                   data:
        // 0        type                                           05 (GYRO)
        // 1..9     timestamp                                      FF FF FF FF FF FF FF 7F
        let timeStamp:UInt64 = 9223372036854775807
        // 10       frame type                                     80 (compressed, type 0)
        
        let gyroDataFrameHeader = Data([
            0x05,
            0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x7F,
            0x80,
        ])
        let previousTimeStamp:UInt64 = 100
        
        // HEX: EA FF 08 00 0D 00 03 01 DF 00
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  EA FF (0xFFEA = -22)
        // 2..3     Sample 0 - channel 1 (ref. sample)  08 00 (0x0008 = 8)
        // 4..5     Sample 0 - channel 2 (ref. sample)  0D 00 (0x000D = 13)
        // 6        Delta size                          03 (3 bit)
        // 7        Sample amount                       01 (1 samples)
        // 8..      Delta data                          DF (binary: 11 011 111) 00 (binary: 0000000 0)
        // Delta channel 0                              111b
        // Delta channel 1                              011b
        // Delta channel 2                              011b
        let expectedSamplesSize = 1 + 1 // reference sample + delta samples
        
        let sample0channel0:Float = -22.0
        let sample0channel1:Float = 8.0
        let sample0channel2:Float = 13.0
        
        let sample1channel0:Float = sample0channel0 - 0x1
        let sample1channel1:Float = sample0channel1 + 0x3
        let sample1channel2:Float = sample0channel2 + 0x3
        
        let gyroDataFrameContent = Data([
            0xEA, 0xFF,
            0x08, 0x00, 0x0D, 0x00,
            0x03, 0x01, 0xDF, 0x00
        ])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: gyroDataFrameHeader + gyroDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })
        
        // Act
        let gyroData = try GyrData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSamplesSize, gyroData.samples.count)
        XCTAssertEqual(sample0channel0, gyroData.samples[0].x)
        XCTAssertEqual(sample0channel1, gyroData.samples[0].y)
        XCTAssertEqual(sample0channel2, gyroData.samples[0].z)
        
        XCTAssertEqual(sample1channel0, gyroData.samples[1].x)
        XCTAssertEqual(sample1channel1, gyroData.samples[1].y)
        XCTAssertEqual(sample1channel2, gyroData.samples[1].z)
        
        XCTAssertEqual(timeStamp, gyroData.timeStamp)
        XCTAssertEqual(timeStamp, gyroData.samples.last?.timeStamp)
    }
}
