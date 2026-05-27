//  Copyright © 2021 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
import CoreBluetooth
import Combine

class BleGattClientBaseTest: XCTestCase {

    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    var bleGattClientBase: BleGattClientBase!
    static let SOME_BLE_SERVICE_UUID = CBUUID(string: "1234")

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        bleGattClientBase = BleGattClientBase(
            serviceUuid: BleGattClientBaseTest.SOME_BLE_SERVICE_UUID,
            gattServiceTransmitter: mockGattServiceTransmitterImpl
        )
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        bleGattClientBase = nil
    }

    // MARK: - Helper

    func testAddNotificationCharacteristic() throws {
        // Arrange
        let someBleCharacteristicsUUID = CBUUID(string: "ffff")

        // Act
        bleGattClientBase.automaticEnableNotificationsOnConnect(chr: someBleCharacteristicsUUID)

        // Assert
        XCTAssertTrue(bleGattClientBase.containsNotifyCharacteristic(someBleCharacteristicsUUID))
        XCTAssertEqual(-1, bleGattClientBase.getNotificationCharacteristicState(someBleCharacteristicsUUID)!.get())
        XCTAssertTrue(bleGattClientBase.containsCharacteristic(someBleCharacteristicsUUID))
        XCTAssertFalse(bleGattClientBase.containsReadCharacteristic(someBleCharacteristicsUUID))
    }

    func testRemoveNotificationCharacteristic() throws {
        // Arrange
        let someBleCharacteristicToRemoveUUID = CBUUID(string: "ffff")
        let someBleCharacteristicToKeepUUID = CBUUID(string: "12ff")
        let someBleCharacteristicToRemoveWhichNotFoundUUID = CBUUID(string: "2112")

        // Act
        bleGattClientBase.automaticEnableNotificationsOnConnect(chr: someBleCharacteristicToKeepUUID)
        bleGattClientBase.automaticEnableNotificationsOnConnect(chr: someBleCharacteristicToRemoveUUID)
        bleGattClientBase.removeCharacteristicNotification(someBleCharacteristicToRemoveUUID)
        bleGattClientBase.removeCharacteristicNotification(someBleCharacteristicToRemoveWhichNotFoundUUID)

        // Assert
        XCTAssertFalse(bleGattClientBase.containsNotifyCharacteristic(someBleCharacteristicToRemoveUUID))
        XCTAssertFalse(bleGattClientBase.containsCharacteristic(someBleCharacteristicToRemoveUUID))
        XCTAssertFalse(bleGattClientBase.containsReadCharacteristic(someBleCharacteristicToRemoveUUID))
        XCTAssertTrue(bleGattClientBase.containsNotifyCharacteristic(someBleCharacteristicToKeepUUID))
        XCTAssertTrue(bleGattClientBase.containsCharacteristic(someBleCharacteristicToKeepUUID))
    }

    func testNotificationEnableResponse_missingCharacteristic() {
        // Arrange
        let someBleCharacteristicUUID = CBUUID(string: "12ff")
        let expectation = expectation(description: "publisher fails with gattCharacteristicNotFound")
        var cancellable: AnyCancellable?

        // Act
        cancellable = bleGattClientBase
            .waitNotificationEnabled(someBleCharacteristicUUID, checkConnection: false)
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        guard case BleGattException.gattCharacteristicNotFound = error else {
                            XCTFail("Expected gattCharacteristicNotFound, got \(error)")
                            expectation.fulfill()
                            return
                        }
                        expectation.fulfill()
                    } else {
                        XCTFail("Expected gattCharacteristicNotFound error to be thrown")
                        expectation.fulfill()
                    }
                    _ = cancellable
                },
                receiveValue: { _ in }
            )

        wait(for: [expectation], timeout: 1.0)
    }

    // GIVEN BLE GATT client has sent enable notification event on BLE GATT server and response is already received
    // WHEN BLE GATT client waitNotificationEnabled() is called
    // THEN BLE GATT client shall respond with already received status
    func testNotificationEnableResponse_receivedAlready() {
        // Arrange
        let someBleCharacteristicUUID = CBUUID(string: "12ff")
        bleGattClientBase.automaticEnableNotificationsOnConnect(chr: someBleCharacteristicUUID)
        bleGattClientBase.notifyDescriptorWritten(someBleCharacteristicUUID, enabled: true, err: 0)
        let expectation = expectation(description: "publisher completes without error")
        var cancellable: AnyCancellable?

        // Act
        cancellable = bleGattClientBase
            .waitNotificationEnabled(someBleCharacteristicUUID, checkConnection: false)
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        XCTFail("Expected success, got \(error)")
                    }
                    expectation.fulfill()
                    _ = cancellable
                },
                receiveValue: { _ in }
            )

        wait(for: [expectation], timeout: 1.0)
    }
}
