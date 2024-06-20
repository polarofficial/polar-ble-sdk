///  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class TemperatureDataTest: XCTestCase {

    func testOfflineTemperatureData() throws {
        let previousTimeStamp: UInt64 = 120
        
        let offlineTemperatureDataFrameHeader = Data([
            0x0C, 0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00, 0x00
        ])

        let offlineTemperatureDataFrameContent = Data([
            0xF6, 0x28, 0xC0, 0x41
        ])

        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: offlineTemperatureDataFrameHeader + offlineTemperatureDataFrameContent,
            { _ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })

        let offlineTemperatureData = try TemperatureData.parseDataFromDataFrame(frame: dataFrame)
        
        XCTAssertEqual(1, offlineTemperatureData.samples.count)
        XCTAssertEqual(24.0200005, offlineTemperatureData.samples[0].temperature)
        XCTAssertEqual(2000000000, offlineTemperatureData.samples[0].timeStamp)
    }
}
