//  Copyright © 2023 Polar. All rights reserved.

import XCTest
import iOSCommunications
import RxSwift
import CoreBluetooth
import RxTest


class BleHtsClientTest: XCTestCase {

    var scheduler: TestScheduler!
    var disposeBag: DisposeBag!
    var bleHtsClient: BleHtsClient!
    var mockGattServiceTransmitterImpl: MockGattServiceTransmitterImpl!

    override func setUpWithError() throws {
        scheduler = TestScheduler(initialClock: 0)
        disposeBag = DisposeBag()
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        bleHtsClient = BleHtsClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }
    
    override func tearDownWithError() throws {
        scheduler = nil
        disposeBag = nil
        mockGattServiceTransmitterImpl = nil
        bleHtsClient = nil
    }


    func testTemperatureMeasurement() {
        // Arrange
        let characteristic: CBUUID = HealthThermometer.TEMPERATURE_MEASUREMENT
        let status = 0

        let expectedCelsius1 = 27.20
        let measurementFrame1: [UInt8] = [0x00, 0xa0, 0x0a, 0x00, 0xfe]

        let expectedCelsius2 = 27.21
        let measurementFrame2: [UInt8] = [0x00, 0xa1, 0x0a, 0x00, 0xfe]
        
        let observer = scheduler.createObserver(BleHtsClient.TemperatureMeasurement.self)

        // Act
        let observable = bleHtsClient.observeHtsNotifications(checkConnection: true)
        observable.subscribe(observer).disposed(by: disposeBag)
        scheduler.start()
        
        bleHtsClient.processServiceData(characteristic, data: Data(measurementFrame1), err: status)
        bleHtsClient.processServiceData(characteristic, data: Data(measurementFrame2), err: status)
        
        // Assert
        let recordedEvents = observer.events
        XCTAssertEqual(recordedEvents.count, 2)
        
        let tempCelsius1 = recordedEvents[0].value.element
        XCTAssertEqual(tempCelsius1?.temperatureCelsius, Float(expectedCelsius1))
        
        let tempCelsius2 = recordedEvents[1].value.element
        XCTAssertEqual(tempCelsius2?.temperatureCelsius, Float(expectedCelsius2))
    }
}
