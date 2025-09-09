//  Copyright © 2025 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

class PolarSensorSettingTests: XCTestCase {
    
    func testInitWithValidValues() throws {
        // Arrange
        let settings: [PolarSensorSetting.SettingType: UInt32] = [
            .sampleRate: 10,
            .resolution: 16
        ]
        
        // Act
        let sensorSetting = try PolarSensorSetting(settings)
        
        // Assert
        XCTAssertEqual(sensorSetting.settings[.sampleRate], [10])
        XCTAssertEqual(sensorSetting.settings[.resolution], [16])
    }
    
    func testInitWithZeroValueThrowsError() throws {
        // Arrange
        let settings: [PolarSensorSetting.SettingType: UInt32] = [
            .sampleRate: 0
        ]
        
        // Act & Assert
        XCTAssertThrowsError(try PolarSensorSetting(settings)) { error in
            guard case PolarErrors.invalidSensorSettingValue(let type, let value) = error else {
                XCTFail("Expected invalidSensorSettingValue error")
                return
            }
            XCTAssertEqual(type, .sampleRate)
            XCTAssertEqual(value, 0)
        }
    }
    
    func testInitWithSetOfValues() throws {
        // Arrange
        let input: [PolarSensorSetting.SettingType: Set<UInt32>] = [
            .range: [1, 2, 3]
        ]
        
        // Act
        let sensorSetting = PolarSensorSetting(input)
        
        // Assert
        XCTAssertEqual(sensorSetting.settings[.range], [1, 2, 3])
    }
}

