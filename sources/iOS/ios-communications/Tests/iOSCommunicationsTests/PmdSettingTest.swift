///  Copyright © 2022 Polar. All rights reserved.

import XCTest
import RxTest
import RxSwift
@testable import iOSCommunications
import CoreBluetooth

final class PmdSettingTest: XCTestCase {
    func testPmdSettingsWithRange() {
        //Arrange
        let bytes = Data([0x00, 0x01, 0x34, 0x00, 0x01, 0x01, 0x10, 0x00, 0x02, 0x04, 0xF5, 0x00, 0xF4, 0x01, 0xE8, 0x03, 0xD0, 0x07, 0x04, 0x01, 0x03])
        // Parameters
        // Setting Type : 00 (Sample Rate)
        // array_length : 01
        // array of settings values: 34 00 (52Hz)
        let sampleRate: UInt32 = 52
        //Setting Type : 01 (Resolution)
        // array_length : 01
        // array of settings values: 10 00 (16)
        let resolution: UInt32 = 16
        // Setting Type : 02 (Range)
        // array_length : 04
        // array of settings values: F5 00 (245)
        let range1: UInt32 = 245
        // array of settings values: F4 01 (500)
        let range2: UInt32 = 500
        // array of settings values: E8 03 (1000)
        let range3: UInt32 = 1000
        // array of settings values: D0 07 (2000)
        let range4: UInt32 = 2000
        // Setting Type : 04 (Channels)
        // array_length : 01
        // array of settings values: 03 (3 Channels)
        let channels: UInt32 = 3
        let numberOfSettings = 4
        
        //Act
        let pmdSetting = PmdSetting(bytes)
        
        // Assert
        XCTAssertEqual(numberOfSettings, pmdSetting.settings.count)
        
        XCTAssertEqual(sampleRate, pmdSetting.settings[PmdSetting.PmdSettingType.sampleRate]!.first!)
        
        XCTAssertEqual(1, pmdSetting.settings[PmdSetting.PmdSettingType.sampleRate]!.count)
        
        XCTAssertEqual(resolution, pmdSetting.settings[PmdSetting.PmdSettingType.resolution]!.first!)
        
        XCTAssertEqual(1, pmdSetting.settings[PmdSetting.PmdSettingType.resolution]!.count)
        
        XCTAssertTrue(pmdSetting.settings[PmdSetting.PmdSettingType.range]!.contains(range1))
        XCTAssertTrue(pmdSetting.settings[PmdSetting.PmdSettingType.range]!.contains(range2))
        XCTAssertTrue(pmdSetting.settings[PmdSetting.PmdSettingType.range]!.contains(range3))
        XCTAssertTrue(pmdSetting.settings[PmdSetting.PmdSettingType.range]!.contains(range4))
        XCTAssertEqual(4, pmdSetting.settings[PmdSetting.PmdSettingType.range]!.count)
        
        XCTAssertEqual(channels, pmdSetting.settings[PmdSetting.PmdSettingType.channels]!.first!)
        
        XCTAssertEqual(1,  pmdSetting.settings[PmdSetting.PmdSettingType.channels]!.count)
        
        XCTAssertNil(pmdSetting.settings[PmdSetting.PmdSettingType.rangeMilliUnit])
        XCTAssertNil(pmdSetting.settings[PmdSetting.PmdSettingType.factor])
    }
    
     func testPmdSelectedSerialization() {
        //Arrange
        var selected = [PmdSetting.PmdSettingType : UInt32]()
        let sampleRate: UInt32 = 0xFFFF
        selected[PmdSetting.PmdSettingType.sampleRate] = sampleRate
        let resolution: UInt32 = 0
        selected[PmdSetting.PmdSettingType.resolution] = resolution
        let range: UInt32 = 15
        selected[PmdSetting.PmdSettingType.range] = range
        let rangeMilliUnit = UInt32.max
        selected[PmdSetting.PmdSettingType.rangeMilliUnit] = rangeMilliUnit
        let channels: UInt32 = 4
        selected[PmdSetting.PmdSettingType.channels] = channels
        let factor: UInt32 = 15
        selected[PmdSetting.PmdSettingType.factor] = factor
        let numberOfSettings = 5

        //Act
        let settingsFromSelected = PmdSetting.init(selected)
        let serializedSelected = settingsFromSelected.serialize()
        let settings = PmdSetting(serializedSelected)

        //Assert
        XCTAssertEqual(numberOfSettings, settings.settings.count)
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.sampleRate]!.contains(sampleRate))
        XCTAssertEqual(1, settings.settings[PmdSetting.PmdSettingType.sampleRate]!.count)
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.resolution]!.contains(resolution))
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.range]!.contains(range));
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.rangeMilliUnit]!.contains(rangeMilliUnit))
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.channels]!.contains(channels))
        XCTAssertNil(settings.settings[PmdSetting.PmdSettingType.factor])
    }
     
    func testPmdSetting() {
         
         let data = Data([PmdSetting.PmdSettingType.rangeMilliUnit.rawValue,
                          0x02,0xFF,0xFF,0xFF,0xFF,0xFF,0x00,0x00,0x00,
                          PmdSetting.PmdSettingType.resolution.rawValue,
                          0x01,0x0E,0x00])
         let setting = PmdSetting(data)
         XCTAssertEqual(setting.settings.count, 2)
         XCTAssertNotNil(setting.settings[PmdSetting.PmdSettingType.rangeMilliUnit])
         XCTAssertNotNil(setting.settings[PmdSetting.PmdSettingType.resolution])
         XCTAssertEqual(setting.settings[PmdSetting.PmdSettingType.rangeMilliUnit]!.count, 2)
         XCTAssertEqual(setting.settings[PmdSetting.PmdSettingType.resolution]!.count, 1)
         XCTAssertTrue(setting.settings[PmdSetting.PmdSettingType.rangeMilliUnit]!.contains(0xffffffff))
         XCTAssertTrue(setting.settings[PmdSetting.PmdSettingType.rangeMilliUnit]!.contains(0xff))
         XCTAssertTrue(setting.settings[PmdSetting.PmdSettingType.resolution]!.contains(0x0E))
         
         let serialized = setting.serialize()
         XCTAssertEqual(serialized.count, 10)
     }
}
