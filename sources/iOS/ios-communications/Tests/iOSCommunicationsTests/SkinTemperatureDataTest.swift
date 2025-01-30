///  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class SkinTemperatureDataTest: XCTestCase {

    func testUncompressedSkinTemperatureData() throws {
        let previousTimeStamp: UInt64 = 120

        let temperatureDataFrameHeader = Data([
            0x07, 0x40, 0xAE, 0x21, 0xAE, 0x31, 0xB2, 0xEE, 0x0A, 0x00
        ])

        let temperatureDataFrameContent = Data([
            0xF6, 0x28, 0xC0, 0x41
        ])

        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: temperatureDataFrameHeader + temperatureDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        let temperatureData = try SkinTemperatureData.parseDataFromDataFrame(frame: dataFrame)
        
        XCTAssertEqual(1,temperatureData.samples.count)
        XCTAssertEqual(24.0200005, temperatureData.samples[0].skinTemperature)
        XCTAssertEqual(787762911281000000, temperatureData.samples[0].timeStamp)
    }
    
    func testCompressedSkinTemperatureData() throws {
        let previousTimeStamp: UInt64 = 120
        
        
        let skinTemperatureDataFrameHeader = Data([
            0x07, 0x40, 0xAE, 0x21, 0xAE, 0x31, 0xB2, 0xEE, 0x0A, 0x80
        ])
        
        let skinTemperatureDataFrameContent = Data([
            0xEC, 0x51, 0xDC, 0x41, 0x03, 0x02, 0x00
        ])

        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: skinTemperatureDataFrameHeader + skinTemperatureDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        let temperatureData = try SkinTemperatureData.parseDataFromDataFrame(frame: dataFrame)
        
        XCTAssertEqual(3,temperatureData.samples.count)
        XCTAssertEqual(27.54, temperatureData.samples[0].skinTemperature)
        XCTAssertEqual(27.54, temperatureData.samples[1].skinTemperature)
        XCTAssertEqual(27.54, temperatureData.samples[2].skinTemperature)
    }
}
