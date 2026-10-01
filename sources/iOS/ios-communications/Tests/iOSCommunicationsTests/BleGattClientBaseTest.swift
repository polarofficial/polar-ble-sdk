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

    // GIVEN waitDiscovered() is subscribed to before the service has been discovered
    // WHEN setServiceDiscovered(true) is called afterwards
    // THEN the already-registered subscriber completes successfully instead of hanging forever
    func testWaitDiscovered_completesForSubscriberRegisteredBeforeDiscovery() {
        let expectation = expectation(description: "waitDiscovered completes")
        var cancellable: AnyCancellable?
        var completed = false

        cancellable = bleGattClientBase.waitDiscovered(checkConnection: false)
            .sink(
                receiveCompletion: { completion in
                    if case .finished = completion { completed = true }
                    expectation.fulfill()
                    _ = cancellable
                },
                receiveValue: { _ in }
            )

        XCTAssertFalse(completed)
        bleGattClientBase.setServiceDiscovered(true)

        wait(for: [expectation], timeout: 1.0)
        XCTAssertTrue(completed)
    }

    // GIVEN waitDiscovered() is subscribed to before the service has been discovered
    // WHEN disconnected() happens before discovery completes
    // THEN the subscriber fails with gattDisconnected instead of hanging forever
    func testWaitDiscovered_failsOnDisconnectWhilePending() {
        let expectation = expectation(description: "waitDiscovered fails")
        var cancellable: AnyCancellable?
        var caughtError: Error?

        cancellable = bleGattClientBase.waitDiscovered(checkConnection: false)
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion { caughtError = error }
                    expectation.fulfill()
                    _ = cancellable
                },
                receiveValue: { _ in }
            )

        bleGattClientBase.disconnected()

        wait(for: [expectation], timeout: 1.0)
        guard case BleGattException.gattDisconnected = caughtError ?? BleGattException.gattCharacteristicNotFound else {
            return XCTFail("Expected gattDisconnected")
        }
    }

    // GIVEN waitCharacteristicsDiscovered() is subscribed to before characteristics have been discovered
    // WHEN setCharacteristicsDiscovered(true) is called afterwards
    // THEN the already-registered subscriber completes successfully instead of hanging forever
    func testWaitCharacteristicsDiscovered_completesForSubscriberRegisteredBeforeDiscovery() {
        let expectation = expectation(description: "waitCharacteristicsDiscovered completes")
        var cancellable: AnyCancellable?
        var completed = false

        cancellable = bleGattClientBase.waitCharacteristicsDiscovered(checkConnection: false)
            .sink(
                receiveCompletion: { completion in
                    if case .finished = completion { completed = true }
                    expectation.fulfill()
                    _ = cancellable
                },
                receiveValue: { _ in }
            )

        XCTAssertFalse(completed)
        bleGattClientBase.setCharacteristicsDiscovered(true)

        wait(for: [expectation], timeout: 1.0)
        XCTAssertTrue(completed)
    }

    // GIVEN the service has been discovered but its characteristics have not
    // THEN waitCharacteristicsDiscovered() must still be pending (service-discovered alone is
    // not sufficient readiness).
    func testWaitCharacteristicsDiscovered_doesNotCompleteWhenOnlyServiceDiscovered() {
        var completed = false
        var cancellable: AnyCancellable?

        bleGattClientBase.setServiceDiscovered(true)
        cancellable = bleGattClientBase.waitCharacteristicsDiscovered(checkConnection: false)
            .sink(
                receiveCompletion: { completion in
                    if case .finished = completion { completed = true }
                    _ = cancellable
                },
                receiveValue: { _ in }
            )

        XCTAssertFalse(completed)
    }

    // GIVEN waitCharacteristicsDiscovered() is subscribed to before discovery completes
    // WHEN disconnected() happens before discovery completes
    // THEN the subscriber fails with gattDisconnected instead of hanging forever
    func testWaitCharacteristicsDiscovered_failsOnDisconnectWhilePending() {
        let expectation = expectation(description: "waitCharacteristicsDiscovered fails")
        var cancellable: AnyCancellable?
        var caughtError: Error?

        cancellable = bleGattClientBase.waitCharacteristicsDiscovered(checkConnection: false)
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion { caughtError = error }
                    expectation.fulfill()
                    _ = cancellable
                },
                receiveValue: { _ in }
            )

        bleGattClientBase.disconnected()

        wait(for: [expectation], timeout: 1.0)
        guard case BleGattException.gattDisconnected = caughtError ?? BleGattException.gattCharacteristicNotFound else {
            return XCTFail("Expected gattDisconnected")
        }
    }
}

