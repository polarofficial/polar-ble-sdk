//  Copyright © 2021 Polar. All rights reserved.

import XCTest
import iOSCommunications

class PolarAdvDataUtilityTest: XCTestCase {
    
    func testGetPolarModelNameFromAdvLocalNameOneWord() throws {
        // Arrange
        let advName = "Polar OneWord1234 12345678"
        let modelNameExpected = "OneWord1234"
        
        // Act
        let modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advLocalName: advName)
        
        // Assert
        XCTAssertEqual(modelNameExpected, modelName)
    }
    
    func testGetPolarModelNameFromAdvLocalNameTwoWords() throws {
        // Arrange
        let advName = " Polar two woRds 12345678 "
        let modelNameExpected = "two woRds"
        
        // Act
        let modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advLocalName: advName)
        
        // Assert
        XCTAssertEqual(modelNameExpected, modelName)
    }
    
    func testGetPolarModelNameFromAdvLocalNameMultiWords() throws {
        // Arrange
        let advName = "Polar Some m x name 12345678"
        let modelNameExpected = "Some m x name"
        
        // Act
        let modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advLocalName: advName)
        
        // Assert
        XCTAssertEqual(modelNameExpected, modelName)
    }
    
    func testGetPolarModelNameFromAdvLocalNameNoPolarPrefix() throws {
        // Arrange
        let advNameMultiWords = "Some m x name Polar 12345678"
        let modelNameExpected = ""
        
        // Act
        let modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advLocalName: advNameMultiWords)
        
        // Assert
        XCTAssertEqual(modelNameExpected, modelName)
    }

    func testGetPolarModelNameFromAdvLocalNameDeviceIdMissing() throws {
        // Arrange
        let advNameMultiWords = "Polar noId "
        let modelNameExpected = ""

        // Act
        let modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advLocalName: advNameMultiWords)

        // Assert
        XCTAssertEqual(modelNameExpected, modelName)
    }
    
    func testIsPolarDeviceWithPolarPrefix() throws {
        // Arrange
        let advName = " Polar Some m x name 12345678"
        
        // Act
        let isPolarDevice = PolarAdvDataUtility.isPolarDevice(advLocalName: advName)
        
        // Assert
        XCTAssertTrue(isPolarDevice)
    }
    
    func testIsPolarDeviceNoPolarPrefix() throws {
        // Arrange
        let advName = "Some m x name Polar 12345678"
        
        // Act
        let isPolarDevice = PolarAdvDataUtility.isPolarDevice(advLocalName: advName)
        
        // Assert
        XCTAssertFalse(isPolarDevice)
    }
    
    func testIsPolarDeviceOnlyPrefix() throws {
        // Arrange
        let advName = "Polar"
        
        // Act
        let isPolarDevice = PolarAdvDataUtility.isPolarDevice(advLocalName: advName)
        
        // Assert
        XCTAssertFalse(isPolarDevice)
    }
}
