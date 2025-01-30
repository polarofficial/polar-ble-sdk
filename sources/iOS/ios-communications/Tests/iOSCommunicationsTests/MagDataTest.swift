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
        
        let sample0channel0: Float = -6430.0
        let sample0channel1: Float = 5626.0
        let sample0channel2: Float = 2633.0
        
        let sample1channel0 = sample0channel0 - 0x01
        let sample1channel1 = sample0channel1 + 0x01
        let sample1channel2 = sample0channel2 + 0x02
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: magDataFrameHeader + magDataFrameContent,
            { _,_ in previousTimeStamp },
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
    
    func testProcessMagnetometerCompressedDataType1() throws {
        // Arrange
        // HEX: 06 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           06 (MAG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp: UInt64 = 2000000000
        // 10       frame type                                     81 (compressed, type 1)
        let magDataFrameHeader = Data([
            0x06,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x81
        ])
        let previousTimeStamp: UInt64 = 100
        
        // HEX: 37 FF 51 FD 6C F6 00 00 03 01 F8 02
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  37 FF (0xFF37 = -201)
        // 2..3     Sample 0 - channel 1 (ref. sample)  51 FD (0xFD51 = -687)
        // 4..5     Sample 0 - channel 2 (ref. sample)  6C F6 (0xF66C = -2452)
        // 6..7     Status (ref. sample)                00 00 (0x0000 = 0)
        // 8        Delta size                          03 (3 bit)
        // 9        Sample amount                       01 (1 samples)
        // 10..     Delta data                          F8 (binary: 11 111 000) 02 (binary: 0000 0010)
        // Delta channel 0                              000b
        // Delta channel 1                              111b
        // Delta channel 2                              011b
        // Delta status                                 001b
        let expectedSamplesSize = 1 + 1 // reference sample + delta samples
        let magDataFrameContent = Data([
            0x37, 0xFF, 0x51, 0xFD, 0x6C, 0xF6,
            0x00, 0x00, 0x03, 0x01, 0xF8, 0x02
        ])
        
        let sample0channel0: Float = -201.0 / 1000
        let sample0channel1: Float = -687.0 / 1000
        let sample0channel2: Float = -2452.0 / 1000
        let sample0status = MagData.CalibrationStatus.getById(id: 0x00)
        
        let sample1channel0: Float = (-201.0 + 0x00) / 1000
        let sample1channel1: Float = (-687.0 - 0x01) / 1000
        let sample1channel2: Float = (-2452.0 + 0x3) / 1000
        let sample1status = MagData.CalibrationStatus.getById(id: 0x00 + 0x01)
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: magDataFrameHeader + magDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })
        
        // Act
        let magData = try MagData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSamplesSize, magData.samples.count)
        
        XCTAssertEqual(sample0channel0, magData.samples[0].x)
        XCTAssertEqual(sample0channel1, magData.samples[0].y)
        XCTAssertEqual(sample0channel2, magData.samples[0].z)
        XCTAssertEqual(sample0status, magData.samples[0].calibrationStatus)
        
        XCTAssertEqual(sample1channel0, magData.samples[1].x)
        XCTAssertEqual(sample1channel1, magData.samples[1].y)
        XCTAssertEqual(sample1channel2, magData.samples[1].z)
        XCTAssertEqual(sample1status, magData.samples[1].calibrationStatus)
        
        XCTAssertEqual(timeStamp, magData.timeStamp)
        XCTAssertEqual(timeStamp, magData.samples[1].timeStamp)
    }
    
    func testProcessMagnetometerCompressedDataType1WithFactor() throws {
        // Arrange
        // HEX: 06 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           06 (MAG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp: UInt64 = 2000000000
        // 10       frame type                                     81 (compressed, type 1)
        let magDataFrameHeader = Data([
            0x06,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x81
        ])
        
        let previousTimeStamp: UInt64 = 100
        // HEX: 37 FF 51 FD 6C F6 00 00 03 01 F8 02
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  37 FF (0xFF37 = -201)
        // 2..3     Sample 0 - channel 1 (ref. sample)  51 FD (0xFD51 = -687)
        // 4..5     Sample 0 - channel 2 (ref. sample)  6C F6 (0xF66C = -2452)
        // 6..7     Status (ref. sample)                00 00 (0x0000 = 0)
        // 8        Delta size                          03 (3 bit)
        // 9        Sample amount                       01 (1 samples)
        // 10..     Delta data                          F8 (binary: 11 111 000) 02 (binary: 0000 0010)
        // Delta channel 0                              000b
        // Delta channel 1                              111b
        // Delta channel 2                              011b
        // Delta status                                 001b
        let expectedSamplesSize = 1 + 1 // reference sample + delta samples
        let magDataFrameContent = Data([
            0x37, 0xFF, 0x51, 0xFD, 0x6C, 0xF6,
            0x00, 0x00, 0x03, 0x01, 0xF8, 0x02])
        
        let sample0channel0: Float = -201.0 / 1000
        let sample0channel1: Float = -687.0 / 1000
        let sample0channel2: Float = -2452.0 / 1000
        let sample0status = MagData.CalibrationStatus.getById(id: 0x00)
        
        let sample1channel0: Float = (-201.0 + 0x00) / 1000
        let sample1channel1: Float = (-687.0 - 0x01) / 1000
        let sample1channel2: Float = (-2452.0 + 0x3) / 1000
        let sample1status = MagData.CalibrationStatus.getById(id: 0x00 + 0x01)
        
        let factor:Float = 1.1
        let dataFrame = try PmdDataFrame(
            data: magDataFrameHeader + magDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })
        
        // Act
        let magData = try MagData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSamplesSize, magData.samples.count)
        
        XCTAssertEqual(factor * sample0channel0, magData.samples[0].x, accuracy: 0.00001)
        XCTAssertEqual(factor * sample0channel1, magData.samples[0].y, accuracy: 0.00001)
        XCTAssertEqual(factor * sample0channel2, magData.samples[0].z, accuracy: 0.00001)
        XCTAssertEqual(sample0status, magData.samples[0].calibrationStatus)
        
        XCTAssertEqual(factor * sample1channel0, magData.samples[1].x, accuracy: 0.00001)
        XCTAssertEqual(factor * sample1channel1, magData.samples[1].y, accuracy: 0.00001)
        XCTAssertEqual(factor * sample1channel2, magData.samples[1].z, accuracy: 0.00001)
        XCTAssertEqual(sample1status, magData.samples[1].calibrationStatus)
        
        XCTAssertEqual(timeStamp, magData.timeStamp)
        XCTAssertEqual(timeStamp, magData.samples[1].timeStamp)
        
    }
}
