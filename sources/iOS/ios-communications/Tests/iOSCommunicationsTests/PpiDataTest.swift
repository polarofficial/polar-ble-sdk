//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class PpiDataTest: XCTestCase {
    func testProcessPpiRawDataType0() throws {
        // Arrange
        // HEX: 03 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           03 (PPI)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        let ppiDataFrameHeader = Data([
            0x01,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x00,
        ])
        let previousTimeStamp:UInt64 = 100
        // HEX:  80 80 80 80 80 FF 00 01 00 01 00 00
        // index    type                                            data:
        // 0        HR                                              0x80 (128)
        let heartRate = 128
        // 1..2     PP                                              0x80 0x80 (32896)
        let intervalInMs:UInt16 = 32896
        // 3..4     PP Error Estimate                               0x80 0x80 (32896)
        let errorEstimate:UInt16 = 32896
        // 5        PP flags                                        0xFF
        let blockerBit:Int = 0x01
        let skinContactStatus:Int = 0x01
        let skinContactSupported:Int = 0x01
        
        // 6        HR                                              0x00 (0)
        let heartRate2 = 0
        // 7..8     PP                                              0x01 0x00 (1)
        let intervalInMs2:UInt16 = 1
        // 9..10     PP Error Estimate                              0x01 0x00 (1)
        let errorEstimate2:UInt16 = 1
        // 11        PP flags                                       0x00
        let blockerBit2:Int = 0x00
        let skinContactStatus2:Int = 0x00
        let skinContactSupported2:Int = 0x00
        
        let ppiDataFrameContent = Data([
            0x80, 0x80, 0x80, 0x80,
            0x80, 0xFF, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00
        ])
        //let timeStamp: Long = Long.MAX_VALUE
        
        let dataFrame = try PmdDataFrame(
            data: ppiDataFrameHeader + ppiDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in 1.0 },
            { _ in 0 })
        
        // Act
        let ppiData = try PpiData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(heartRate, ppiData.samples[0].hr)
        XCTAssertEqual(intervalInMs, ppiData.samples[0].ppInMs)
        XCTAssertEqual(errorEstimate, ppiData.samples[0].ppErrorEstimate)
        XCTAssertEqual(blockerBit, ppiData.samples[0].blockerBit)
        XCTAssertEqual(skinContactStatus, ppiData.samples[0].skinContactStatus)
        XCTAssertEqual(skinContactSupported, ppiData.samples[0].skinContactSupported)
        
        XCTAssertEqual(heartRate2, ppiData.samples[1].hr)
        XCTAssertEqual(intervalInMs2, ppiData.samples[1].ppInMs)
        XCTAssertEqual(errorEstimate2, ppiData.samples[1].ppErrorEstimate)
        XCTAssertEqual(blockerBit2, ppiData.samples[1].blockerBit)
        XCTAssertEqual(skinContactStatus2, ppiData.samples[1].skinContactStatus)
        XCTAssertEqual(skinContactSupported2, ppiData.samples[1].skinContactSupported)
        
        XCTAssertEqual(2, ppiData.samples.count)
    }
}
