//  Copyright © 2021 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
import CoreBluetooth
import RxTest
import RxSwift

class BleGattClientBaseTest: XCTestCase {
    var scheduler: TestScheduler!
    var disposeBag: DisposeBag!
    var mockGattServiceTransmitterImpl:MockGattServiceTransmitterImpl!
    static let SOME_BLE_SERVICE_UUID = CBUUID(string: "1234")
    
    var bleGattClientBase:BleGattClientBase!
    
    override func setUpWithError() throws {
        scheduler = TestScheduler(initialClock: 0)
        disposeBag = DisposeBag()
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        bleGattClientBase = BleGattClientBase(serviceUuid: BleGattClientBaseTest.SOME_BLE_SERVICE_UUID, gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }
    override func tearDownWithError() throws {
        scheduler = nil
        disposeBag = nil
        mockGattServiceTransmitterImpl = nil
        bleGattClientBase = nil
    }
    
    func testAddNotificationCharacteristic() throws {
        //Arrange
        let someBleCharacteristicsUUID: CBUUID = CBUUID(string: "ffff")
        
        // Act
        bleGattClientBase.automaticEnableNotificationsOnConnect(chr: someBleCharacteristicsUUID)
        
        //Assert
        XCTAssertTrue(bleGattClientBase.containsNotifyCharacteristic(someBleCharacteristicsUUID))
        XCTAssertEqual(-1, bleGattClientBase.getNotificationCharacteristicState(someBleCharacteristicsUUID)!.get())
        
        XCTAssertTrue(bleGattClientBase.containsCharacteristic(someBleCharacteristicsUUID))
        XCTAssertFalse(bleGattClientBase.containsReadCharacteristic(someBleCharacteristicsUUID))
    }
    
    func testRemoveNotificationCharacteristic() throws {
        //Arrange
        let someBleCharacteristicToRemoveUUID: CBUUID = CBUUID(string: "ffff")
        let someBleCharacteristicToKeepUUID = CBUUID(string: "12ff")
        let someBleCharacteristicToRemoveWhichNotFoundUUID = CBUUID(string: "2112")
        
        // Act
        bleGattClientBase.automaticEnableNotificationsOnConnect(chr: someBleCharacteristicToKeepUUID)
        bleGattClientBase.automaticEnableNotificationsOnConnect(chr: someBleCharacteristicToRemoveUUID)
        bleGattClientBase.removeCharacteristicNotification(someBleCharacteristicToRemoveUUID)
        bleGattClientBase.removeCharacteristicNotification(someBleCharacteristicToRemoveWhichNotFoundUUID)
        
        //Assert
        XCTAssertFalse(bleGattClientBase.containsNotifyCharacteristic(someBleCharacteristicToRemoveUUID))
        XCTAssertFalse(bleGattClientBase.containsCharacteristic(someBleCharacteristicToRemoveUUID))
        XCTAssertFalse(bleGattClientBase.containsReadCharacteristic(someBleCharacteristicToRemoveUUID))
        
        XCTAssertTrue(bleGattClientBase.containsNotifyCharacteristic(someBleCharacteristicToKeepUUID))
        XCTAssertTrue(bleGattClientBase.containsCharacteristic(someBleCharacteristicToKeepUUID))
    }
    
    func testNotificationEnableResponse_missingCharacteristic() throws {
        //Arrange
        let someBleCharacteristicUUID = CBUUID(string: "12ff")
        
        // Act
        let result = bleGattClientBase.waitNotificationEnabled(someBleCharacteristicUUID, checkConnection: false).toBlocking().materialize()
        
        // Assert
        switch result {
        case .completed(_):
            XCTFail("Observable should fail instead of complete")
            
        case .failed(_, let error):
            guard case BleGattException.gattCharacteristicNotFound = error else {
                return XCTFail()
            }
        }
    }
    
    // GIVEN BLE GATT client has sent enable notification event on BLE GATT server and response is already received
    // WHEN BLE GATT client waitNotificationEnabled() is called
    // THEN BLE GATT client shall respond with already received status
    func testNotificationEnableResponse_receivedAlready() throws {
        //Arrange
        let someBleCharacteristicUUID = CBUUID(string: "12ff")
        bleGattClientBase.automaticEnableNotificationsOnConnect(chr: someBleCharacteristicUUID)
        bleGattClientBase.notifyDescriptorWritten(someBleCharacteristicUUID, enabled: true, err: 0)
        
        // Act
        let result = bleGattClientBase.waitNotificationEnabled(someBleCharacteristicUUID, checkConnection: false).toBlocking().materialize()
        
        // Assert
        switch result {
        case .completed(_):
            //Pass
            return
        case .failed(_, _):
            return XCTFail()
        }
    }
}
