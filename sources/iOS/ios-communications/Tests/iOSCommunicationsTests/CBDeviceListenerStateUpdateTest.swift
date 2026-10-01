// Copyright © 2026 Polar. All rights reserved.

import XCTest
import Combine
import CoreBluetooth
@testable import iOSCommunications

final class CBDeviceListenerStateUpdateTest: XCTestCase {

    /// Returns `stub` on the first read and `secondaryState` afterwards, which is what the real
    /// central does when it finishes resetting while our block is still queued.
    private final class StateSpyCentralManager: CBCentralManager {
        var stub: CBManagerState = .resetting
        var secondaryState: CBManagerState = .poweredOn
        var readCount = 0

        override var state: CBManagerState {
            readCount += 1
            return readCount == 1 ? stub : secondaryState
        }
    }

    private final class PowerStateSpy: BlePowerStateObserver {
        var states: [BleState] = []
        func powerStateChanged(_ state: BleState) {
            states.append(state)
        }
    }

    private var queue: DispatchQueue!
    private var listener: CBDeviceListenerImpl!
    private var observer: PowerStateSpy!
    private var cancellables: Set<AnyCancellable>!

    override func setUp() {
        super.setUp()
        queue = DispatchQueue(label: "flow75453.test.queue")
        listener = CBDeviceListenerImpl(queue, clients: [], identifier: 75453)
        observer = PowerStateSpy()
        cancellables = Set<AnyCancellable>()
        listener.powerStateObserver = observer
        settle()
    }

    override func tearDown() {
        cancellables.removeAll()
        observer = nil
        listener = nil
        queue = nil
        super.tearDown()
    }

    /// Forces the lazy `CBCentralManager` to be created and lets its initial state callback land,
    /// so it cannot bleed into the assertions below.
    private func settle() {
        _ = listener.blePowered()
        drain()
        observer.states.removeAll()
    }

    private func drain() {
        for _ in 0 ..< 8 {
            queue.sync { }
            usleep(30_000)
        }
    }

    // MARK: - The defect

    func testCentralStateIsReadExactlyOnce() {
        let spy = StateSpyCentralManager(delegate: nil, queue: queue)
        drain()
        spy.readCount = 0

        listener.centralManagerDidUpdateState(spy)
        drain()

        XCTAssertEqual(spy.readCount, 1,
                       "the state must be captured once, synchronously; re-reading it lets CoreBluetooth change it mid-handler")
    }

    func testResettingIsReportedEvenWhenCentralHasAlreadyMovedOn() {
        let spy = StateSpyCentralManager(delegate: nil, queue: queue)
        spy.stub = .resetting
        spy.secondaryState = .poweredOn
        drain()
        spy.readCount = 0
        observer.states.removeAll()

        listener.centralManagerDidUpdateState(spy)
        drain()

        XCTAssertEqual(observer.states, [.resetting],
                       "the transition that triggered the callback must be reported, not the state the central has since reached")
    }

    // MARK: - Emission mapping

    func testEveryStateIsForwardedToThePowerStateObserver() {
        let cases: [(CBManagerState, BleState)] = [
            (.unknown, .unknown),
            (.resetting, .resetting),
            (.unsupported, .unsupported),
            (.unauthorized, .unauthorized),
            (.poweredOff, .poweredOff),
            (.poweredOn, .poweredOn)
        ]

        for (input, expected) in cases {
            observer.states.removeAll()

            listener.handleCentralStateUpdate(input)
            drain()

            XCTAssertEqual(observer.states, [expected], "state \(input.rawValue) must map to \(expected)")
        }
    }

    func testMonitorBleStateSubscribersReceiveResetting() {
        var received: [BleState] = []
        let expectation = XCTestExpectation(description: "resetting observed via monitorBleState")

        listener.monitorBleState()
            .dropFirst() // drop the current-value replay from the CurrentValueSubject
            .sink(
                receiveCompletion: { _ in },
                receiveValue: { state in
                    received.append(state)
                    if state == .resetting { expectation.fulfill() }
                }
            )
            .store(in: &cancellables)

        listener.handleCentralStateUpdate(.resetting)
        drain()

        wait(for: [expectation], timeout: 1.0)
        XCTAssertEqual(received, [.resetting])
    }

    // MARK: - Purge

    func testResettingPurgesTheSessionList() {
        listener.handleCentralStateUpdate(.resetting)
        drain()

        XCTAssertTrue(listener.allSessions().isEmpty)
    }

    func testPoweredOffStillReportsState() {
        listener.handleCentralStateUpdate(.poweredOff)
        drain()

        XCTAssertEqual(observer.states, [.poweredOff])
    }
}

