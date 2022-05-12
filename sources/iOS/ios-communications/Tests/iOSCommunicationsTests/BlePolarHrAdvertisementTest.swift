//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

class BlePolarHrAdvertisementTest: XCTestCase {
    var blePolarHrAdvertisement:BlePolarHrAdvertisement!
    
    override func setUpWithError() throws {
        blePolarHrAdvertisement = BlePolarHrAdvertisement()
    }
    
    override func tearDownWithError() throws {
        blePolarHrAdvertisement = nil
    }
    
    func testBatteryStatus() throws {
        // Arrange
        let batteryStatusOk = Data([0xFE, 0xFE,0xFE,0xFE])
        let batteryStatusNok = Data([0xFF, 0xFE,0xFE,0xFE])
        
        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(batteryStatusOk)
        XCTAssertEqual(false, blePolarHrAdvertisement.batteryStatus)
        blePolarHrAdvertisement.processPolarManufacturerData(batteryStatusNok)
        XCTAssertEqual(true, blePolarHrAdvertisement.batteryStatus)
    }
    
    func testFrameCounter() throws {
        // Arrange
        
        let counter0 = Data([0xE3, 0xFE, 0xFE, 0xFE])
        let counter1 = Data([0xE7, 0xFE, 0xFE, 0xFE])
        let counter7 = Data([0xFF, 0xFE, 0xFE, 0xFE])
        
        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(counter0)
        XCTAssertEqual(0, blePolarHrAdvertisement.advFrameCounter)
        XCTAssertEqual(true, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter1)
        XCTAssertEqual(1, blePolarHrAdvertisement.advFrameCounter)
        XCTAssertEqual(true, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter1)
        XCTAssertEqual(1, blePolarHrAdvertisement.advFrameCounter)
        XCTAssertEqual(false, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter1)
        XCTAssertEqual(1, blePolarHrAdvertisement.advFrameCounter)
        XCTAssertEqual(false, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter7)
        XCTAssertEqual(7, blePolarHrAdvertisement.advFrameCounter)
        XCTAssertEqual(true, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter0)
        XCTAssertEqual(0, blePolarHrAdvertisement.advFrameCounter)
        XCTAssertEqual(true, blePolarHrAdvertisement.isHrDataUpdated)
    }
    
    func testStatusFlag() throws {
        // Arrange
        let statusFlagUp = Data([0xFF, 0xFE, 0xFE, 0xFE])
        let statusFlagDown = Data([0x7F, 0xFE, 0xFE, 0xFE])
        
        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(statusFlagUp)
        XCTAssertEqual(1, blePolarHrAdvertisement.statusFlags)
        blePolarHrAdvertisement.processPolarManufacturerData(statusFlagDown)
        XCTAssertEqual(0, blePolarHrAdvertisement.statusFlags)
    }
    
    func testKhzCode() throws {
        // Arrange
        let code0 = Data([0xFF, 0x00, 0xFE, 0xFE])
        let code255 = Data([0xFF, 0xFF, 0xFE, 0xFE])
        
        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(code0)
        XCTAssertEqual(0, blePolarHrAdvertisement.khzCode)
        blePolarHrAdvertisement.processPolarManufacturerData(code255)
        XCTAssertEqual(255, blePolarHrAdvertisement.khzCode)
    }
    
    func testFastAndSlowAvgHr() throws {
        // Arrange
        let fastAvgAndLowAvg0 = Data([0xFF, 0xFF, 0x00, 0x00])
        let fastAvgAndSlowAvg255 = Data([0xFF, 0xFF, 0xFF, 0xFF])
        let fastAvg0SlowMissing = Data([0xFF, 0xFF, 0x00])
        let fastAvg255SlowMissing = Data([0xFF, 0xFF, 0xFF])
                
        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(fastAvgAndLowAvg0)
        XCTAssertEqual(0, blePolarHrAdvertisement.fastAverageHr)
        XCTAssertEqual(0, blePolarHrAdvertisement.slowAverageHr)
        XCTAssertEqual(0, blePolarHrAdvertisement.hrValueForDisplay)
        blePolarHrAdvertisement.processPolarManufacturerData(fastAvgAndSlowAvg255)
        XCTAssertEqual(255, blePolarHrAdvertisement.fastAverageHr)
        XCTAssertEqual(255, blePolarHrAdvertisement.slowAverageHr)
        XCTAssertEqual(255, blePolarHrAdvertisement.hrValueForDisplay)
        blePolarHrAdvertisement.processPolarManufacturerData(fastAvg0SlowMissing)
        XCTAssertEqual(0, blePolarHrAdvertisement.fastAverageHr)
        XCTAssertEqual(0, blePolarHrAdvertisement.slowAverageHr)
        XCTAssertEqual(0, blePolarHrAdvertisement.hrValueForDisplay)
        blePolarHrAdvertisement.processPolarManufacturerData(fastAvg255SlowMissing)
        XCTAssertEqual(255, blePolarHrAdvertisement.fastAverageHr)
        XCTAssertEqual(255, blePolarHrAdvertisement.slowAverageHr)
        XCTAssertEqual(255, blePolarHrAdvertisement.hrValueForDisplay)
    }
}
