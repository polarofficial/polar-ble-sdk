//  Copyright © 2022 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import iOSCommunications

class BleAdvertisementContentTest: XCTestCase {
    var bleAdvertisementContent:BleAdvertisementContent!
    
    override func setUpWithError() throws {
        bleAdvertisementContent = BleAdvertisementContent()
    }
    
    override func tearDownWithError() throws {
        bleAdvertisementContent = nil
    }
    
    func testParseNameFromCompleteLocalName() throws {
        // Arrange
        let testInputString = "ABC EDE aa123459"
        let name: [String : String] = [CBAdvertisementDataLocalNameKey : testInputString]
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: name)
        // Assert
        XCTAssertEqual(testInputString, bleAdvertisementContent.name)
        XCTAssertTrue(bleAdvertisementContent.polarDeviceType.isEmpty)
        XCTAssertTrue(bleAdvertisementContent.polarDeviceId.isEmpty)
    }
    
    func testParseNameFromCompleteLocalNameWhenPolarDevice() throws {
        // Arrange
        let testInputString = "Polar GritX Pro AA123459"
        let name: [String : String] = [CBAdvertisementDataLocalNameKey : testInputString]
        
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: name)
        
        // Assert
        XCTAssertEqual(testInputString, bleAdvertisementContent.name)
        XCTAssertEqual("GritX Pro", bleAdvertisementContent.polarDeviceType)
        XCTAssertEqual("AA123459", bleAdvertisementContent.polarDeviceId)
    }
    
    func testParseHrFromManufacturerDataWithoutHr() throws {
        // Arrange
        let onlyGpbManufacturerData = Data([0x6b, 0x00,
                                            0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00])
        
        let onlyGpb:[String : Data] = [CBAdvertisementDataManufacturerDataKey : onlyGpbManufacturerData]
        
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: onlyGpb)
        
        // Assert
        XCTAssertNotNil(bleAdvertisementContent.polarHrAdvertisementData)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
    }
    
    func testParseHrFromManufacturerDataSAGRFC23format() {
        // Arrange
        let gpbAndHrManufacturerData = Data([0x6b, 0x00,
                                             0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00,
                                             0x7a, 0x01, 0x03, 0x33, 0x00, 0x00])
        
        let gbpAndHr: [String : Data] = [CBAdvertisementDataManufacturerDataKey : gpbAndHrManufacturerData]
        
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: gbpAndHr)
        
        // Assert
        XCTAssertNotNil(bleAdvertisementContent.polarHrAdvertisementData)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
    }
    
    func testParseHrFromManufacturerDataSAGRFC31format() throws {
        // Arrange
        let onlyHrManufacturerData = Data([0x6b, 0x00, 0x2b, 0x0b, 0xb6, 0xac])
        let onlyHr: [String : Data] = [CBAdvertisementDataManufacturerDataKey : onlyHrManufacturerData]
        
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: onlyHr)
        
        // Assert
        XCTAssertNotNil(bleAdvertisementContent.polarHrAdvertisementData)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
    }
    
    func testParseHrFromManufacturerDataNotPolarAdv() throws {
        // Arrange
        let nonPolarManufacturerData = Data([0x6b, 0x01, // 0x006B is Polar manufacturer Id, 0x016B is not Polar
                                             0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00,
                                             0x7a, 0x01, 0x03, 0x33, 0x00, 0x00])
        
        let nonPolar: [String : Data] = [CBAdvertisementDataManufacturerDataKey : nonPolarManufacturerData]

        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: nonPolar)

        // Assert
        XCTAssertNotNil(bleAdvertisementContent.polarHrAdvertisementData)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
    }
    
    func testProcessOfRssiWhenLessThan7RssiValuesArrived() throws {
        // Arrange
        let rssiValues:[Int32] = [-0, -1, -2, -3, -4, -99]

        // Act
        for rssi in rssiValues {
            bleAdvertisementContent.processAdvertisementData(rssi, advertisementData: [CBAdvertisementDataManufacturerDataKey : Data()])
        }

        // Assert
        XCTAssertEqual(rssiValues.last, bleAdvertisementContent.rssiFilter.rssi)
        XCTAssertEqual(rssiValues.last, bleAdvertisementContent.medianRssi)
    }
    
    func testProcessOfRssiWhenMoreThan7RssiValuesArrived() throws {
        // Arrange
        let rssiValues:[Int32] = [-10, -21, -2, -5, -9, -0, -8, -1, -8, -2]
        let median:Int32 = -5 // -0, -1, -2, !-5!, -8, -8, -9,
        
        // Act
        for rssi in rssiValues {
            bleAdvertisementContent.processAdvertisementData(rssi, advertisementData: [CBAdvertisementDataManufacturerDataKey : Data()])
        }

        // Assert
        XCTAssertEqual(rssiValues.last, bleAdvertisementContent.rssiFilter.rssi)
        XCTAssertEqual(median, bleAdvertisementContent.medianRssi)
    }
    
    func testAdvContainsService() throws {
        // Arrange
        let services:[CBUUID] = [BleHrClient.HR_SERVICE, BlePsFtpClient.PSFTP_SERVICE]
        let servicesAdvData:[String : [CBUUID]] = [CBAdvertisementDataServiceUUIDsKey : services]
        let singleServiceAdvData: [String : [CBUUID]] = [CBAdvertisementDataServiceUUIDsKey : [BleHrClient.HR_SERVICE]]
        let emptyServicesAdvData: [String : Data] = [CBAdvertisementDataServiceUUIDsKey : Data()]
                
        // Act & Assert
        bleAdvertisementContent.processAdvertisementData(0, advertisementData:servicesAdvData)
        XCTAssertTrue(bleAdvertisementContent.containsService(BlePsFtpClient.PSFTP_SERVICE))
        XCTAssertTrue(bleAdvertisementContent.containsService(BleHrClient.HR_SERVICE))
        bleAdvertisementContent.processAdvertisementData(0, advertisementData:emptyServicesAdvData)
        XCTAssertFalse(bleAdvertisementContent.containsService(BlePsFtpClient.PSFTP_SERVICE))
        XCTAssertFalse(bleAdvertisementContent.containsService(BleHrClient.HR_SERVICE))
        bleAdvertisementContent.processAdvertisementData(0, advertisementData:singleServiceAdvData)
        XCTAssertFalse(bleAdvertisementContent.containsService(BlePsFtpClient.PSFTP_SERVICE))
        XCTAssertTrue(bleAdvertisementContent.containsService(BleHrClient.HR_SERVICE))
    }
    
    func testProcessingOfConsecutiveAdvPackets() throws {
        // Arrange
        let gpbAndHrManufacturerData = Data([0x6b, 0x00,
                                             0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00,
                                             0x7a, 0x01, 0x03, 0x33, 0x00, 0x00])
        let onlyGpbManufacturerData = Data([0x6b, 0x00,
                                            0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00])
        let emptyManufacturerData = Data()
        
        let gbpAndHr: [String : Data] = [CBAdvertisementDataManufacturerDataKey : gpbAndHrManufacturerData]
        let onlyGpb:[String : Data] = [CBAdvertisementDataManufacturerDataKey : onlyGpbManufacturerData]
        let emptyManufacturer: [String : Data] = [CBAdvertisementDataManufacturerDataKey : emptyManufacturerData]
        let nilManufacturer: [String : Data?] = [CBAdvertisementDataManufacturerDataKey : nil]
        
        // Act & Assert
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: gbpAndHr)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: onlyGpb)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: gbpAndHr)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: emptyManufacturer)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: gbpAndHr)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: nilManufacturer)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
    }
}
