// Copyright © 2026 Polar. All rights reserved.

import XCTest
import Combine
import CoreBluetooth
@testable import iOSCommunications

final class CBDeviceListenerImplTest: XCTestCase {

    var sut: CBDeviceListenerImpl!
    var cancellables = Set<AnyCancellable>()

    override func setUpWithError() throws {
        sut = CBDeviceListenerImpl(
            DispatchQueue(label: "com.polar.test.ble"),
            clients: [],
            identifier: 0
        )
    }

    override func tearDownWithError() throws {
        cancellables.removeAll()
        sut = nil
    }

    // MARK: - Default property values

    func testAutomaticH10MappingDefaultIsFalse() {
        XCTAssertFalse(sut.automaticH10Mapping)
    }

    func testAutomaticReconnectionDefaultIsTrue() {
        XCTAssertTrue(sut.automaticReconnection)
    }

    func testScanPreFilterDefaultIsNil() {
        XCTAssertNil(sut.scanPreFilter)
    }

    func testServicesToScanForDefaultIsNil() {
        XCTAssertNil(sut.servicesToScanFor)
    }

    // MARK: - allSessions

    func testAllSessionsIsEmptyOnInit() {
        XCTAssertTrue(sut.allSessions().isEmpty)
    }

    // MARK: - removeAllSessions

    func testRemoveAllSessionsReturnsZeroWhenNoSessionsExist() {
        let removed = sut.removeAllSessions()
        XCTAssertEqual(0, removed)
    }

    func testRemoveAllSessionsInStateReturnsZeroWhenNoSessionsExist() {
        let removed = sut.removeAllSessions(Set([
            BleDeviceSession.DeviceSessionState.sessionClosed,
            BleDeviceSession.DeviceSessionState.sessionOpenPark
        ]))
        XCTAssertEqual(0, removed)
    }

    func testRemoveAllSessionsWithAllStatesReturnsZeroWhenNoSessionsExist() {
        let allStates: Set<BleDeviceSession.DeviceSessionState> = [
            .sessionClosed, .sessionOpening, .sessionOpen, .sessionOpenPark, .sessionClosing
        ]
        let removed = sut.removeAllSessions(allStates)
        XCTAssertEqual(0, removed)
    }

    // MARK: - monitorDeviceSessionState

    func testMonitorDeviceSessionStateDoesNotImmediatelyComplete() {
        var completed = false
        var failed = false

        sut.monitorDeviceSessionState()
            .sink(
                receiveCompletion: { completion in
                    switch completion {
                    case .finished: completed = true
                    case .failure: failed = true
                    }
                },
                receiveValue: { _ in }
            )
            .store(in: &cancellables)

        XCTAssertFalse(completed, "monitorDeviceSessionState should not complete immediately")
        XCTAssertFalse(failed, "monitorDeviceSessionState should not fail immediately")
    }

    func testMonitorDeviceSessionStateCancelRemovesObserver() {
        // Subscribe then immediately cancel — verifies no crash or retain cycle
        let cancellable = sut.monitorDeviceSessionState()
            .sink(receiveCompletion: { _ in }, receiveValue: { _ in })

        cancellable.cancel()
        // No assertion needed; the test passes if there is no crash
    }

    func testMultipleMonitorDeviceSessionStateSubscribersAreSupported() {
        var completedCount = 0

        let sub1 = sut.monitorDeviceSessionState()
            .sink(receiveCompletion: { _ in completedCount += 1 }, receiveValue: { _ in })
        let sub2 = sut.monitorDeviceSessionState()
            .sink(receiveCompletion: { _ in completedCount += 1 }, receiveValue: { _ in })

        sub1.cancel()
        sub2.cancel()

        XCTAssertEqual(0, completedCount)
    }

    // MARK: - monitorBleState

    func testMonitorBleStateEmitsInitialStateImmediately() {
        var receivedStates: [BleState] = []
        let expectation = XCTestExpectation(description: "initial BLE state emitted")

        sut.monitorBleState()
            .prefix(1)
            .sink(
                receiveCompletion: { _ in expectation.fulfill() },
                receiveValue: { receivedStates.append($0) }
            )
            .store(in: &cancellables)

        wait(for: [expectation], timeout: 1.0)

        XCTAssertEqual(1, receivedStates.count, "Should receive exactly one initial state")
    }

    func testMonitorBleStateCancelRemovesObserver() {
        let cancellable = sut.monitorBleState()
            .sink(receiveCompletion: { _ in }, receiveValue: { _ in })

        cancellable.cancel()
        // No assertion needed; the test passes if there is no crash
    }

    func testMonitorBleStateDoesNotCompleteAfterInitialValue() {
        var completionCount = 0

        sut.monitorBleState()
            .dropFirst() // drop the prepended initial value
            .sink(
                receiveCompletion: { _ in completionCount += 1 },
                receiveValue: { _ in }
            )
            .store(in: &cancellables)

        XCTAssertEqual(0, completionCount, "monitorBleState should not complete after initial value")
    }

    // MARK: - powerStateObserver

    func testPowerStateObserverReceivesCurrentStateOnSet() {
        let mockObserver = MockBlePowerStateObserver()
        sut.powerStateObserver = mockObserver

        XCTAssertEqual(1, mockObserver.receivedStates.count,
                       "powerStateObserver should be called once with current BLE state on assignment")
    }

    // MARK: - blePowered

    func testBlePoweredReturnsFalseWithoutRealBluetooth() {
        // In a unit-test environment the CBCentralManager state is .unknown or
        // .poweredOff — never .poweredOn — so blePowered() must return false.
        XCTAssertFalse(sut.blePowered())
    }

    // MARK: - servicesToScanFor

    func testServicesToScanForCanBeSet() {
        let uuid = CBUUID(string: "180D")
        sut.servicesToScanFor = [uuid]
        XCTAssertEqual([uuid], sut.servicesToScanFor)
    }

    func testServicesToScanForCanBeCleared() {
        sut.servicesToScanFor = [CBUUID(string: "180D")]
        sut.servicesToScanFor = nil
        XCTAssertNil(sut.servicesToScanFor)
    }
}

// MARK: - Test helpers

private class MockBlePowerStateObserver: BlePowerStateObserver {
    private(set) var receivedStates: [BleState] = []

    func powerStateChanged(_ state: BleState) {
        receivedStates.append(state)
    }
}
