//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class PmdOfflineTriggerTest: XCTestCase {
    
    func testTriggersAreDisabled() throws {
        //Arrange
        let pmdGetTriggerStatusResponse = Data([
            // index 0 : trigger mode
            0x00,
            // index 1.. : trigger status, measurement type, settings length (optional), settings (optional)
            0x00, 0x02,
            0x00, 0x05,
            0x00, 0x06,
            0x00, 0x01,
            0x00, 0x03
        ])
        
        //Act
        let pmdOfflineTrigger = try PmdOfflineTrigger.fromResponse(data: pmdGetTriggerStatusResponse)
        
        //Assert
        XCTAssertEqual(PmdOfflineRecTriggerMode.disabled, pmdOfflineTrigger.triggerMode)
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.ppi}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.acc}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.ppg}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.gyro}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.mgn}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.count == 5)
        for trigger in pmdOfflineTrigger.triggers {
            XCTAssertTrue(trigger.value.status == PmdOfflineRecTriggerStatus.disabled)
            XCTAssertTrue(trigger.value.setting == nil)
        }
    }
    
    func testTriggerPpiEnabledAtSystemStart() throws {
        //Arrange
        let pmdGetTriggerStatusResponse = Data([
            // index 0 : trigger mode
            0x01,
            // index 1.. : trigger status, measurement type, settings length (optional), settings (optional)
            0x00, 0x02,
            0x00, 0x05,
            0x00, 0x06,
            0x00, 0x01,
            0x01, 0x03, 0x00
        ])
        
        //Act
        let pmdOfflineTrigger = try PmdOfflineTrigger.fromResponse(data:pmdGetTriggerStatusResponse)
        
        //Assert
        XCTAssertEqual(PmdOfflineRecTriggerMode.systemStart, pmdOfflineTrigger.triggerMode)
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.ppi}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.acc}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.ppg}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.gyro}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.mgn}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.count == 5)
        for trigger in pmdOfflineTrigger.triggers {
            if (trigger.key == PmdMeasurementType.ppi) {
                XCTAssertTrue(trigger.value.status == PmdOfflineRecTriggerStatus.enabled)
                XCTAssertTrue(trigger.value.setting == nil)
            } else {
                XCTAssertTrue(trigger.value.status == PmdOfflineRecTriggerStatus.disabled)
                XCTAssertTrue(trigger.value.setting == nil)
            }
        }
    }
    
    func testTriggerPpiAccGyroMagPpgEnabledAtExerciseStart() throws {
        //Arrange
        let pmdGetTriggerStatusResponse = Data([
            // index 0 : trigger mode
            0x02,
            // index 1.. : trigger status, measurement type, settings length (optional), settings (optional)
            0x01, 0x02, 0x0f, 0x00, 0x01, 0x34, 0x00, 0x01, 0x01, 0x10, 0x00, 0x02, 0x01, 0x08, 0x00, 0x04, 0x01, 0x03,
            0x01, 0x05, 0x0f, 0x00, 0x01, 0x34, 0x00, 0x01, 0x01, 0x10, 0x00, 0x02, 0x01, 0xd0, 0x07, 0x04, 0x01, 0x03,
            0x01, 0x06, 0x0f, 0x00, 0x01, 0x64, 0x00, 0x01, 0x01, 0x10, 0x00, 0x02, 0x01, 0x32, 0x00, 0x04, 0x01, 0x03,
            0x01, 0x01, 0x0f, 0x00, 0x01, 0x87, 0x00, 0x01, 0x01, 0x16, 0x00, 0x02, 0x01, 0x00, 0x00, 0x04, 0x01, 0x04,
            0x01, 0x03, 0x00
        ])
        
        //Act
        let pmdOfflineTrigger = try PmdOfflineTrigger.fromResponse(data:pmdGetTriggerStatusResponse)
        
        //Assert
        XCTAssertEqual(PmdOfflineRecTriggerMode.exerciseStart, pmdOfflineTrigger.triggerMode)
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.ppi}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.acc}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.ppg}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.gyro}))
        XCTAssertTrue(pmdOfflineTrigger.triggers.contains(where: { $0.key == PmdMeasurementType.mgn}))
        
        XCTAssertTrue(pmdOfflineTrigger.triggers.count == 5)
        for trigger in pmdOfflineTrigger.triggers {
            if (trigger.key == PmdMeasurementType.ppi) {
                XCTAssertTrue(trigger.value.status == PmdOfflineRecTriggerStatus.enabled)
                XCTAssertTrue(trigger.value.setting == nil)
            } else {
                XCTAssertTrue(trigger.value.status == PmdOfflineRecTriggerStatus.enabled)
                XCTAssertTrue(trigger.value.setting != nil)
            }
        }
    }
    
    func testTriggerPmdResponseHasWrongStatus() throws {
        //Arrange
        let pmdGetTriggerStatusResponse = Data([
            // index 0 : trigger mode
            0x01,
            // index 1.. : trigger status, measurement type, settings length (optional), settings (optional)
            0x03, 0x01
        ])
        //Act & Assert
        XCTAssertThrowsError(
            try PmdOfflineTrigger.fromResponse(data: pmdGetTriggerStatusResponse))
        { error in
            guard case BleGattException.gattDataError = error else {
                return XCTFail()
            }
        }
    }
}
