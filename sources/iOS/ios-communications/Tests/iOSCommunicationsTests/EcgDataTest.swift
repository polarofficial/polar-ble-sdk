//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class EcgDataTest: XCTestCase {
    
    func testProcessRawEcgDataType0() throws {
        // Arrange
        // HEX: 00 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           00 (Ecg)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp:UInt64 = 2000000000
        // 10       frame type                                     00 (raw, type 0)
        let ecgDataFrameHeader = Data([
            0x00,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x00
        ])
        let previousTimeStamp:UInt64 = 100
        
        // HEX: 02 08 FF 02 80 00
        // index    type                                            data:
        // 0..2     uVolts                                          02 80 FF (-32766)
        let ecgValue1:Int32 = -32766
        // 3..4     uVolts                                          02 80 00 (32770)
        let ecgValue2:Int32 = 32770
        let ecgDataFrameContent = Data([0x02, 0x80, 0xFF, 0x02, 0x80, 0x00])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data:  ecgDataFrameHeader + ecgDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })
        
        // Act
        let ecgData = try EcgData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(ecgValue1, ecgData.samples[0].microVolts)
        XCTAssertEqual(ecgValue2, ecgData.samples[1].microVolts)
        
        XCTAssertEqual(2, ecgData.samples.count)
        
        XCTAssertEqual(timeStamp, ecgData.timeStamp)
        XCTAssertEqual(timeStamp, ecgData.samples[1].timeStamp)
    }
}
