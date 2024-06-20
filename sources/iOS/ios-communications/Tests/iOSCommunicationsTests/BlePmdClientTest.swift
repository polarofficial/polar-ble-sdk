//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
import RxTest
import RxSwift

class BlePmdClientTest: XCTestCase {
    var scheduler: TestScheduler!
    var mockGattServiceTransmitterImpl: MockGattServiceTransmitterImpl!
    var blePmdClient: BlePmdClient!
    var disposeBag: DisposeBag!
    
    override func setUpWithError() throws {
        scheduler = TestScheduler(initialClock: 0)
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        disposeBag = DisposeBag()
        blePmdClient = BlePmdClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }
    
    override func tearDownWithError() throws {
        scheduler = nil
        mockGattServiceTransmitterImpl = nil
        blePmdClient = nil
        disposeBag = nil
    }
    
    func testProcessControlPointResponseWhenStatusIsSuccess() throws {
        // Arrange
        // HEX: F0 01 00 00 00 00 00 00 70 FF
        // index    type                                data
        // 0:      Response code                        F0
        // 1...:   Data                                 01 00 00 00 00 00 00 70 FF
        let controlPointResponse = Data([
            0xF0,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x70, 0xFF
        ])
        let successErrCode = 0x00
        
        // Act
        blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: controlPointResponse, err: successErrCode)
        
        // Assert
        let data = try blePmdClient.pmdCpResponseQueue.pop()
        XCTAssertEqual(controlPointResponse, data)
    }
    
    func testProcessMeasurementStopControlPointCommand() throws {
        // Arrange
        // HEX: 01 01 02
        // index    type                                data
        // 0:      Online Measurement Stopped           01
        // 1...:   Measurement types                    01, 02
        let controlPointResponse = Data([
            0x01, 0x01, 0x02
        ])
        let successErrCode = 0x00
        
        let observerPpg = scheduler.createObserver(PpgData.self)
        let observerAcc = scheduler.createObserver(AccData.self)
        let observerPpi = scheduler.createObserver(PpiData.self)
        
        blePmdClient.observePpg().subscribe(observerPpg).disposed(by: disposeBag)
        blePmdClient.observeAcc().subscribe(observerAcc).disposed(by: disposeBag)
        blePmdClient.observePpi().subscribe(observerPpi).disposed(by: disposeBag)
        scheduler.start()
        
        // Act
        blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: controlPointResponse, err: successErrCode)
        
        // Assert
        XCTAssertEqual(1, observerPpg.events.count)
        for event in observerPpg.events {
            switch event.value {
            case .next((let _)):
                XCTFail()
            case .error(let error):
                guard case BlePmdError.bleOnlineStreamClosed = error else {
                    return XCTFail()
                }
            case .completed:
                XCTFail()
            }
        }
        
        XCTAssertEqual(1, observerAcc.events.count)
        for event in observerAcc.events {
            switch event.value {
            case .next((let _)):
                XCTFail()
            case .error(let error):
                guard case BlePmdError.bleOnlineStreamClosed = error else {
                    return XCTFail()
                }
            case .completed:
                XCTFail()
            }
        }
        
        XCTAssertEqual(0, observerPpi.events.count)
    }
}
