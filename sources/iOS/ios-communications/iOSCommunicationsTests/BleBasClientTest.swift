//  Copyright © 2021 Polar. All rights reserved.

import XCTest
import iOSCommunications
import CoreBluetooth
import RxTest
import RxSwift

class BleBasClientTest: XCTestCase {
    var scheduler: TestScheduler!
    var disposeBag: DisposeBag!
    var bleBasClient:BleBasClient!
    var mockGattServiceTransmitterImpl:MockGattServiceTransmitterImpl!
    
    override func setUpWithError() throws {
        scheduler = TestScheduler(initialClock: 0)
        disposeBag = DisposeBag()
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        bleBasClient = BleBasClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }
    
    override func tearDownWithError() throws {
        scheduler = nil
        disposeBag = nil
        mockGattServiceTransmitterImpl = nil
        bleBasClient = nil
    }
    
    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery level observable is subscribed
    // THEN the latest cached battery value is emitted
    func testTheCachedValue() throws {
        
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "2A19")
        let deviceNotifyingBatteryData1 = Data([100])
        let deviceNotifyingBatteryData2 = Data([80])
        let error = 0
        let observer = scheduler.createObserver(Int.self)
        
        // Act
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData1, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData2, err: error)
        
        let observable = bleBasClient.monitorBatteryStatus(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        
        scheduler.start()
        
        // Assert
        XCTAssertEqual(observer.events, [
            .next(0, Int(deviceNotifyingBatteryData2[0]))
        ])
    }
    
    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery level observable is subscribed
    // THEN the correct values are received by observer
    func testBatteryValuesStreamEmitsCorrectValues() throws {
        
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "2A19")
        let deviceNotifyingValidBatteryData1 = Data([100])
        let deviceNotifyingInvalidBatteryData1 = Data([250])
        let deviceNotifyingValidBatteryData2 = Data([80])
        let deviceNotifyingInvalidBatteryData2 = Data([0xFF])
        let deviceNotifyingValidBatteryData3 = Data([00])
        
        let error = 0
        let observer = scheduler.createObserver(Int.self)
        
        // Act
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingValidBatteryData1, err: error)
        let observable = bleBasClient.monitorBatteryStatus(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingInvalidBatteryData1, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingValidBatteryData2, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingInvalidBatteryData2, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingValidBatteryData3, err: error)
        
        // Assert
        XCTAssertEqual(observer.events, [
            .next(0, Int(deviceNotifyingValidBatteryData1[0])),
            .next(0, Int(deviceNotifyingValidBatteryData2[0])),
            .next(0, Int(deviceNotifyingValidBatteryData3[0]))
        ])
    }
    
    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery level observable is subscribed
    // THEN at some point device connection is lost
    func testDeviceDisconnectsWhileStreaming() throws {
        
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "2A19")
        let deviceNotifyingBatteryData1 = Data([100])
        let deviceNotifyingBatteryData2 = Data([90])
        let deviceNotifyingBatteryData3 = Data([80])
        
        let error = 0
        let observer = scheduler.createObserver(Int.self)
        
        // Act
        let observable = bleBasClient.monitorBatteryStatus(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData1, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData2, err: error)
        bleBasClient.processServiceData(characteristic, data: deviceNotifyingBatteryData3, err: error)
        bleBasClient.disconnected()
        
        // Assert
        XCTAssertEqual(observer.events, [
            .next(0, Int(deviceNotifyingBatteryData1[0])),
            .next(0, Int(deviceNotifyingBatteryData2[0])),
            .next(0, Int(deviceNotifyingBatteryData3[0])),
            .error(0, BleGattException.gattDisconnected)
        ])
    }
}
