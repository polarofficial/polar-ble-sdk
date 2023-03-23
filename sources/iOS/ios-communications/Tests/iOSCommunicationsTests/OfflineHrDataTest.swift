//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class OfflineHrDataTest: XCTestCase {
    
    func testOfflineHrData() throws {
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
            { _ in previousTimeStamp }  ,
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
}
