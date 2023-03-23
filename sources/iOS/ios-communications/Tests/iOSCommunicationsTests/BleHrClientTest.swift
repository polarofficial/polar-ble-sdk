//  Copyright © 2021 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
import CoreBluetooth
import RxTest
import RxSwift

class BleHrClientTest: XCTestCase {
    var scheduler: TestScheduler!
    var disposeBag: DisposeBag!
    var bleHrClient:BleHrClient!
    var mockGattServiceTransmitterImpl:MockGattServiceTransmitterImpl!
    
    override func setUpWithError() throws {
        scheduler = TestScheduler(initialClock: 0)
        disposeBag = DisposeBag()
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        bleHrClient = BleHrClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }
    
    override func tearDownWithError() throws {
        scheduler = nil
        disposeBag = nil
        mockGattServiceTransmitterImpl = nil
        bleHrClient = nil
    }
    
    // GIVEN that BLE HR Service client observable is subscriped
    // WHEN the connection to device is missing
    // THEN observable should dispose immediately
    func testConnectionToDeviceIsLost() throws {
        // Arrange
        mockGattServiceTransmitterImpl.mockConnectionStatus = false
        let observer = scheduler.createObserver(BleHrClient.BleHrNotification.self)
        
        // Act
        let observable = bleHrClient.observeHrNotifications(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        // Assert
        XCTAssertEqual(1, observer.events.count)
        for event in observer.events {
            switch event.value {
            case .next((_, _, _, _, _, _, _)):
                XCTFail()
            case .error(let error):
                guard case BleGattException.gattDisconnected = error else {
                    return XCTFail()
                }
            case .completed:
                XCTFail()
            }
        }
    }
    
    // GIVEN that BLE HR Service client receives heart rate data updates
    // WHEN hr data observable is subscribed
    // THEN at some point device connection is lost
    func testDeviceDisconnectsWhileStreaming() throws {
        // Arrange
        let characteristic: CBUUID = CBUUID(string: "2a37")
        let deviceNotifyingHrData1 = Data([0x00, 1])
        let deviceNotifyingHrData2 = Data([0x00, 2])
        let deviceNotifyingHrData3 = Data([0x00, 3])
        let error = 0
        let observer = scheduler.createObserver(BleHrClient.BleHrNotification.self)
        
        // Act
        let observable = bleHrClient.observeHrNotifications(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData1, err: error)
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData2, err: error)
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData3, err: error)
        bleHrClient.disconnected()
        
        // Assert
        XCTAssertEqual(4, observer.events.count)
        for event in observer.events {
            switch event.value {
            case .next((let hr, _, _, _, _, _, _)):
                XCTAssertTrue((1...3).contains(hr))
            case .error(let error):
                guard case BleGattException.gattDisconnected = error else {
                    return XCTFail()
                }
            case .completed:
                XCTFail()
            }
        }
    }
    
    // GIVEN that BLE HR Service client observable is receiving hr values
    // WHEN observable is subscriped
    // THEN no values in cache
    func testNoCachedValues() throws {
        // Arrange
        let deviceNotifyingHrData1 = Data([0x00, 101])
        let deviceNotifyingHrData2 = Data([0x00, 102])
        
        let characteristic: CBUUID = CBUUID(string: "2a37")
        let error = 0
        let observer = scheduler.createObserver(BleHrClient.BleHrNotification.self)
        // Act
        let observable = bleHrClient.observeHrNotifications(true)
        
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData1, err: error)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData2, err: error)
        
        // Assert
        XCTAssertEqual(1, observer.events.count)
        let event1 = observer.events[0]
        
        XCTAssertEqual(102, event1.value.element!.hr)
    }
    
    // GIVEN that BLE HR Service client receives heart rate value in uint8 data format
    // WHEN heart rate observable is subscribed
    // THEN the value is emitted
    func testHrFormatUint8() throws {
        
        // Arrange
        // HEX: 00 FF
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x00
        // Heart rate value format bit:     0 (uint8)
        // 1:       Heart Rate Measurement Value field      size 1:     0xFF
        let expectedHeartRate1:UInt8 = 255
        let deviceNotifyingHrData1 = Data([0x00, expectedHeartRate1])
        // HEX: 00 7F
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x00
        // Heart rate value format bit:     0 (uint8)
        // 1:       Heart Rate Measurement Value field      size 1:     0x7F
        let expectedHeartRate2:UInt8 = 127
        let deviceNotifyingHrData2 = Data([0x00, expectedHeartRate2])
        
        let characteristic: CBUUID = CBUUID(string: "2a37")
        let error = 0
        let observer = scheduler.createObserver(BleHrClient.BleHrNotification.self)
        
        // Act
        let observable = bleHrClient.observeHrNotifications(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData1, err: error)
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData2, err: error)
        
        // Assert
        XCTAssertEqual(2, observer.events.count)
        let event1 = observer.events[0]
        let event2 = observer.events[1]
        
        XCTAssertEqual(Int(expectedHeartRate1), event1.value.element!.hr)
        XCTAssertFalse(event1.value.element!.sensorContact)
        XCTAssertFalse(event1.value.element!.sensorContactSupported)
        XCTAssertEqual(0, event1.value.element!.energy)
        XCTAssertEqual(0, event1.value.element!.rrs.count)
        
        XCTAssertEqual(Int(expectedHeartRate2), event2.value.element!.hr)
        XCTAssertFalse(event2.value.element!.sensorContact)
        XCTAssertFalse(event2.value.element!.sensorContactSupported)
        XCTAssertEqual(0, event2.value.element!.energy)
        XCTAssertEqual(0, event2.value.element!.rrs.count)
        
    }
    
    // GIVEN that BLE HR Service client receives heart rate value in uint16 data format
    // WHEN heart rate observable is subscribed
    // THEN the value is emitted
    func testHrFormatUint16() throws {
        
        // Arrange
        // HEX: 01 80 80
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x01
        // Heart rate value format bit      1 (uint16)
        // 1..2:    Heart Rate Measurement Value field      size 2:     0x80 0x80 (32896)
        let expectedHeartRate1 = 32896
        let deviceNotifyingHrData1 = Data([0x01, 0x80, 0x80])
        // HEX: 01 7F 7F
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x01
        // Heart rate value format bit      1 (uint16)
        // 1..2:    Heart Rate Measurement Value field      size 2:     0x7F 0x7F
        let expectedHeartRate2 = 32639
        let deviceNotifyingHrData2 = Data([0x01, 0x7F, 0x7F])
        
        let characteristic: CBUUID = CBUUID(string: "2a37")
        let error = 0
        let observer = scheduler.createObserver(BleHrClient.BleHrNotification.self)
        
        // Act
        let observable = bleHrClient.observeHrNotifications(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData1, err: error)
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData2, err: error)
        
        // Assert
        XCTAssertEqual(2, observer.events.count)
        let event1 = observer.events[0]
        let event2 = observer.events[1]
        
        XCTAssertEqual(expectedHeartRate1, event1.value.element!.hr)
        XCTAssertFalse(event1.value.element!.sensorContact)
        XCTAssertFalse(event1.value.element!.sensorContactSupported)
        XCTAssertEqual(0, event1.value.element!.energy)
        XCTAssertEqual(0, event1.value.element!.rrs.count)
        
        XCTAssertEqual(expectedHeartRate2, event2.value.element!.hr)
        XCTAssertFalse(event2.value.element!.sensorContact)
        XCTAssertFalse(event2.value.element!.sensorContactSupported)
        XCTAssertEqual(0, event2.value.element!.energy)
        XCTAssertEqual(0, event2.value.element!.rrs.count)
    }
    
    // GIVEN that BLE HR Service client receives data
    // WHEN sensor contact is supported
    // THEN sensor contact correspond the support flag
    func testSensorContactSupported() throws {
        
        //Arrange
        // HEX: 06 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x06
        // Sensor Contact Status bit        1
        // Sensor Contact Support bit       1 (Supported)
        let deviceNotifyingHrData1 = Data([0x06, 0xFF])
        
        // HEX: 04 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x04
        // Sensor Contact Status bit        0
        // Sensor Contact Support bit       1 (Supported)
        let deviceNotifyingHrData2 = Data([0x04, 0xFF])
        
        let characteristic: CBUUID = CBUUID(string: "2a37")
        let error = 0
        let observer = scheduler.createObserver(BleHrClient.BleHrNotification.self)
        
        // Act
        let observable = bleHrClient.observeHrNotifications(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData1, err: error)
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData2, err: error)
        
        //Assert
        let event1 = observer.events[0]
        let event2 = observer.events[1]
        XCTAssertTrue(event1.value.element!.sensorContact)
        XCTAssertTrue(event1.value.element!.sensorContactSupported)
        XCTAssertFalse(event2.value.element!.sensorContact)
        XCTAssertTrue(event2.value.element!.sensorContactSupported)
    }
    
    // GIVEN that BLE HR Service client receives data
    // WHEN sensor contact is not supported
    // THEN sensor contact shall be always false
    func testSensorContactNotSupported() throws {
        // Arrange
        // HEX: 02 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x02
        // Sensor Contact Status bit        1
        // Sensor Contact Support bit       0 (Not supported)
        let deviceNotifyingHrData1 = Data([0x02, 0x00])
        
        // HEX: 00 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x00
        // Sensor Contact Status bit        0
        // Sensor Contact Support bit       0 (Not supported)
        let deviceNotifyingHrData2 = Data([0x00, 0x00])
        
        let characteristic: CBUUID = CBUUID(string: "2a37")
        let error = 0
        let observer = scheduler.createObserver(BleHrClient.BleHrNotification.self)
        
        // Act
        let observable = bleHrClient.observeHrNotifications(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData1, err: error)
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData2, err: error)
        
        //Assert
        let event1 = observer.events[0]
        let event2 = observer.events[1]
        XCTAssertFalse(event1.value.element!.sensorContact)
        XCTAssertFalse(event1.value.element!.sensorContactSupported)
        XCTAssertFalse(event2.value.element!.sensorContact)
        XCTAssertFalse(event2.value.element!.sensorContactSupported)
    }
    
    // GIVEN that BLE HR Service client receives data
    // WHEN Energy Expended field is present
    // THEN correct energy value is emitted
    func testEnergyExpended() throws {
        //Arrange
        // HEX: 09 00 00 FF FF
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x09
        // Heart rate value format bit      1 (uint16)
        // Energy Expended Status bit       1 (Energy Expended field is present)
        // 1..2:    Heart Rate Measurement Value field      size 2:     0x00 0x00
        // 3..4:    Energy Expended field                   size 2:     0xFF 0xFF
        let energyExpended1 = 65535
        let deviceNotifyingHrData1 = Data([0x09, 0x00, 0x00, 0xFF, 0xFF])
        
        // HEX: 08 00 7F 80
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x08
        // Heart rate value format bit      0 (uint8)
        // Energy Expended Status bit       1 (Energy Expended field is present)
        // 1:       Heart Rate Measurement Value field      size 1:     0x00
        // 2..3:    Energy Expended field                   size 2:     0x7F 0x80
        let energyExpended2 = 32895
        let deviceNotifyingHrData2 = Data([0x08, 0x00, 0x7F, 0x80])
        
        let characteristic: CBUUID = CBUUID(string: "2a37")
        let error = 0
        let observer = scheduler.createObserver(BleHrClient.BleHrNotification.self)
        
        // Act
        let observable = bleHrClient.observeHrNotifications(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData1, err: error)
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData2, err: error)
        
        //Assert
        let event1 = observer.events[0]
        let event2 = observer.events[1]
        XCTAssertEqual(energyExpended1, event1.value.element!.energy)
        XCTAssertEqual(energyExpended2, event2.value.element!.energy)
    }
    
    
    // GIVEN that BLE HR Service client receives data
    // WHEN Energy Expended field is present
    // THEN correct rr value is emitted
    func testRRinterval() throws {
        //Arrange
        // HEX: 10 00 FF FF
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x10
        // Heart rate value format bit      0 (uint8)
        // RR-interval bit                  1 (RR-Interval values are present)
        // 1:       Heart Rate Measurement Value field      size 1:     0x00
        // 2..3:    RR-Interval Field                       size 2:     0xFF 0xFF
        let expectedHeartRate1 = 0
        let sample1ExpectedRRin1024Unit = 65535
        let sample1ExpectedRRinMsUnit = 63999
        let deviceNotifyingHrData1 = Data([0x10, 0x00, 0xFF, 0xFF])
        // HEX: 11 00 00 FF FF 7F 80
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x11
        // Heart rate value format bit      1 (uint16)
        // RR-interval bit                  1 (RR-Interval values are present)
        // 1..2:    Heart Rate Measurement Value field      size 1:     0x00 0x00
        // 3..4:    RR-Interval Field                       size 2:     0xFF 0xFF
        // 5..6:    RR-Interval Field                       size 2:     0x7F 0x80
        let expectedHeartRate2 = 0
        let sample2ExpectedRRin1024Unit = 65535
        let sample2ExpectedRRinMsUnit = 63999
        let sample3ExpectedRRin1024Unit = 32895
        let sample3ExpectedRRinMsUnit = 32124
        let deviceNotifyingHrData2 = Data([0x11, 0x00, 0x00, 0xFF, 0xFF, 0x7F, 0x80])
        // HEX: 10 00 00 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x10
        // Heart rate value format bit      0 (uint8)
        // RR-interval bit                  1 (RR-Interval values are present)
        // 1:       Heart Rate Measurement Value field      size 1:     0x00
        // 2..3:    RR-Interval Field                       size 2:     0x00 0x00
        let expectedHeartRate3 = 0
        let sample4ExpectedRRin1024Unit = 0
        let sample4ExpectedRRinMsUnit = 0
        let deviceNotifyingHrData3 = Data([0x10, 0x00, 0x00, 0x00])
        
        let characteristic: CBUUID = CBUUID(string: "2a37")
        let error = 0
        let observer = scheduler.createObserver(BleHrClient.BleHrNotification.self)
        
        // Act
        let observable = bleHrClient.observeHrNotifications(true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData1, err: error)
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData2, err: error)
        bleHrClient.processServiceData(characteristic, data: deviceNotifyingHrData3, err: error)
        
        //Assert
        XCTAssertEqual(3, observer.events.count)
        
        let hrData1 = observer.events[0]
        let hrData2 = observer.events[1]
        let hrData3 = observer.events[2]
        
        XCTAssertEqual(expectedHeartRate1, hrData1.value.element?.hr)
        XCTAssertEqual(1, hrData1.value.element?.rrs.count)
        XCTAssertEqual(1, hrData1.value.element?.rrsMs.count)
        XCTAssertTrue(hrData1.value.element!.rrPresent)
        XCTAssertEqual(sample1ExpectedRRin1024Unit, hrData1.value.element?.rrs[0])
        XCTAssertEqual(sample1ExpectedRRinMsUnit, hrData1.value.element?.rrsMs[0])
        
        XCTAssertEqual(expectedHeartRate2, hrData2.value.element?.hr)
        XCTAssertEqual(2, hrData2.value.element?.rrs.count)
        XCTAssertEqual(2, hrData2.value.element?.rrsMs.count)
        XCTAssertTrue(hrData2.value.element!.rrPresent)
        XCTAssertEqual(sample2ExpectedRRin1024Unit, hrData2.value.element?.rrs[0])
        XCTAssertEqual(sample2ExpectedRRinMsUnit, hrData2.value.element?.rrsMs[0])
        XCTAssertEqual(sample3ExpectedRRin1024Unit, hrData2.value.element?.rrs[1])
        XCTAssertEqual(sample3ExpectedRRinMsUnit, hrData2.value.element?.rrsMs[1])
        
        XCTAssertEqual(expectedHeartRate3, hrData3.value.element?.hr)
        XCTAssertEqual(1, hrData3.value.element?.rrs.count)
        XCTAssertEqual(1, hrData3.value.element?.rrsMs.count)
        XCTAssertTrue(hrData3.value.element!.rrPresent)
        XCTAssertEqual(sample4ExpectedRRin1024Unit, hrData3.value.element?.rrs[0])
        XCTAssertEqual(sample4ExpectedRRinMsUnit, hrData3.value.element?.rrsMs[0])
    }
}
