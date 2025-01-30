//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class OfflineHrDataTest: XCTestCase {
    
    func testUncompressedOfflineHrDataFrameType0() throws {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        let offlineHrDataFrameHeader = Data([
            0x0E,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x00,
        ])
        let previousTimeStamp: UInt64 = 0
        
        // index                                                   data:
        // 0             sample0                                   00
        let expectedSample0: UInt8 = 0
        // 1             sample0                                   FF
        let expectedSample1: UInt8 = 255
        // last index    sampleN                                   7F
        let expectedSampleLast: UInt8 = 127
        let expectedSampleSize = 9
        let offlineHrDataFrameContent = Data([
            0x00, 0xFF, 0x32, 0x32, 0x33, 0x33, 0x34, 0x35, 0x7F,
        ])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: offlineHrDataFrameHeader + offlineHrDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })
        
        
        // Act
        let offlineHrData = try OfflineHrData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSampleSize, offlineHrData.samples.count)
        XCTAssertEqual(expectedSample0, offlineHrData.samples.first?.hr)
        XCTAssertEqual(expectedSample1, offlineHrData.samples[1].hr)
        XCTAssertEqual(expectedSampleLast, offlineHrData.samples.last?.hr)
    }

    func testCompressedOfflineHrDataFrameType0ThrowsError() throws {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     80 (compressed, type 0)
        let offlineHrDataFrameHeader = Data([
            0x0E, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x80,
        ])
        let previousTimeStamp: UInt64 = 0
        let factor:Float = 1.0
        
        // Act
        let dataFrame = try PmdDataFrame(
            data: offlineHrDataFrameHeader,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        // Assert
        XCTAssertThrowsError(try OfflineHrData.parseDataFromDataFrame(frame: dataFrame), "Raw FrameType: TYPE_0 is not supported by PPG data parser")
        XCTAssertThrowsError(try OfflineHrData.parseDataFromDataFrame(frame: dataFrame)) { error in
            guard case BleGattException.gattDataError = error else {
                return XCTFail()
            }
        }
    }

    func testUncompressedOfflineHrDataFrameType1() throws {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     01 (raw, type 1)
        let offlineHrDataFrameHeader = Data([
            0x0E, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x1,
        ])
        let previousTimeStamp: UInt64 = 0

        // index                                                   data:
        // 0             sample0                                   48
        let expectedHR1: UInt8 = 72
        // 3             sample0                                   51
        let expectedHR2: UInt8 = 81
        // 1             sample0                                   56
        let expectedPPGQuality1: UInt8 = 86
        // 4             sample0                                   40
        let expectedPPGQuality2: UInt8 = 64
        // 2             sample0                                   47
        let expectedCorrectedHR1: UInt8 = 71
        // 5             sample0                                   52
        let expectedCorrectedHR2: UInt8 = 82
        let expectedSampleSize = 2
        
        let offlineHrDataFrameContent = Data([
            0x48, 0x56, 0x47, 0x51, 0x40, 0x52,
        ])

        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: offlineHrDataFrameHeader + offlineHrDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        // Act
        let offlineHrData = try OfflineHrData.parseDataFromDataFrame(frame: dataFrame)

        // Assert
        XCTAssertEqual(expectedSampleSize, offlineHrData.samples.count)
        XCTAssertEqual(expectedHR1, offlineHrData.samples.first?.hr)
        XCTAssertEqual(expectedHR2, offlineHrData.samples.last?.hr)
        XCTAssertEqual(expectedPPGQuality1, offlineHrData.samples.first?.ppgQuality)
        XCTAssertEqual(expectedPPGQuality2, offlineHrData.samples.last?.ppgQuality)
        XCTAssertEqual(expectedCorrectedHR1, offlineHrData.samples.first?.correctedHr)
        XCTAssertEqual(expectedCorrectedHR2, offlineHrData.samples.last?.correctedHr)
    }

    func testCompressedOfflineHrDataFrameType1ThrowsError() throws {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     81 (compressed, type 1)
        let offlineHrDataFrameHeader = Data([
            0x0E, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x81,
        ])
        let previousTimeStamp: UInt64 = 0

        let factor:Float = 1.0
        
        // Act
        let dataFrame = try PmdDataFrame(
            data: offlineHrDataFrameHeader,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        // Assert
        XCTAssertThrowsError(try PpgData.parseDataFromDataFrame(frame: dataFrame), "Raw FrameType: TYPE_1 is not supported by PPG data parser")
        XCTAssertThrowsError(try PpgData.parseDataFromDataFrame(frame: dataFrame)) { error in
            guard case BleGattException.gattDataError = error else {
                return XCTFail()
            }
        }
    }
}
