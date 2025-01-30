///  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class TemperatureDataTest: XCTestCase {

    func testUncompressedTemperatureData() throws {
        let previousTimeStamp: UInt64 = 120
        
        let temperatureDataFrameHeader = Data([
            0x0C, 0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00, 0x00
        ])

        let temperatureDataFrameContent = Data([
            0xF6, 0x28, 0xC0, 0x41
        ])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: temperatureDataFrameHeader + temperatureDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })

        let temperatureData = try TemperatureData.parseDataFromDataFrame(frame: dataFrame)
        
        XCTAssertEqual(1,temperatureData.samples.count)
        XCTAssertEqual(24.0200005, temperatureData.samples[0].temperature)
        XCTAssertEqual(2000000000, temperatureData.samples[0].timeStamp)
    }
    
    func testCompressedTemperatureData() throws {
        let previousTimeStamp: UInt64 = 120
        
        
        let temperatureDataFrameHeader = Data([
            0x0C, 0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00, 0x80
        ])

        let temperatureDataFrameContent = Data([
            0xEC, 0x51, 0xDC, 0x41, 0x03, 0x02, 0x00
        ])

        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: temperatureDataFrameHeader + temperatureDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        let temperatureData = try TemperatureData.parseDataFromDataFrame(frame: dataFrame)
        
        XCTAssertEqual(3,temperatureData.samples.count)
        XCTAssertEqual(27.54, temperatureData.samples[0].temperature)
        XCTAssertEqual(27.54, temperatureData.samples[1].temperature)
        XCTAssertEqual(27.54, temperatureData.samples[2].temperature)
    }
}
