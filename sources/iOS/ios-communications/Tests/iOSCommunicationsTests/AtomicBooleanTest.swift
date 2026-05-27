//  Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class AtomicBooleanTests: XCTestCase {

    // MARK: - get / set

    func testInitialTrueValueIsReturnedByGet() {
        let atomic = AtomicBoolean(initialValue: true)
        XCTAssertTrue(atomic.get())
    }

    func testInitialFalseValueIsReturnedByGet() {
        let atomic = AtomicBoolean(initialValue: false)
        XCTAssertFalse(atomic.get())
    }

    func testSetTrueUpdatesValue() {
        let atomic = AtomicBoolean(initialValue: false)
        atomic.set(true)
        XCTAssertTrue(atomic.get())
    }

    func testSetFalseUpdatesValue() {
        let atomic = AtomicBoolean(initialValue: true)
        atomic.set(false)
        XCTAssertFalse(atomic.get())
    }

    func testSetOverwritesPreviousValue() {
        let atomic = AtomicBoolean(initialValue: false)
        atomic.set(true)
        atomic.set(false)
        XCTAssertFalse(atomic.get())
    }

    // MARK: - wait / set signalling

    func testWaitIsSignalledBySet() {
        let atomic = AtomicBoolean(initialValue: false)
        let signalled = XCTestExpectation(description: "wait returned after set")

        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            atomic.set(true)
        }

        DispatchQueue.global().async {
            atomic.wait()
            signalled.fulfill()
        }

        wait(for: [signalled], timeout: 2.0)
        XCTAssertTrue(atomic.get())
    }

    // MARK: - checkAndWait

    func testCheckAndWaitReturnsImmediatelyWhenValueAlreadyMatches() {
        let atomic = AtomicBoolean(initialValue: true)
        // Should return without blocking since val == waited
        let done = XCTestExpectation(description: "checkAndWait returns immediately")
        DispatchQueue.global().async {
            atomic.checkAndWait(true)
            done.fulfill()
        }
        wait(for: [done], timeout: 1.0)
    }

    func testCheckAndWaitBlocksUntilValueChanges() {
        let atomic = AtomicBoolean(initialValue: false)
        let done = XCTestExpectation(description: "checkAndWait unblocked after set")

        DispatchQueue.global().async {
            atomic.checkAndWait(true)   // blocks: val (false) != waited (true)
            done.fulfill()
        }

        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            atomic.set(true)            // signals the condition
        }

        wait(for: [done], timeout: 2.0)
        XCTAssertTrue(atomic.get())
    }

    // MARK: - Thread safety

    func testConcurrentSetsProduceValidFinalValue() {
        let atomic = AtomicBoolean(initialValue: false)
        let iterations = 1000
        let allDone = XCTestExpectation(description: "all writes done")
        allDone.expectedFulfillmentCount = iterations

        for i in 0..<iterations {
            DispatchQueue.global().async {
                atomic.set(i % 2 == 0)
                allDone.fulfill()
            }
        }

        wait(for: [allDone], timeout: 5.0)
        // Value must be a valid Bool — no crash or corruption.
        let final = atomic.get()
        XCTAssertTrue(final == true || final == false)
    }

    func testConcurrentMixedReadsAndWrites() {
        let atomic = AtomicBoolean(initialValue: false)
        let ops = 500
        let allDone = XCTestExpectation(description: "all ops done")
        allDone.expectedFulfillmentCount = ops * 2

        for i in 0..<ops {
            DispatchQueue.global().async {
                atomic.set(i % 2 == 0)
                allDone.fulfill()
            }
            DispatchQueue.global().async {
                _ = atomic.get()
                allDone.fulfill()
            }
        }

        wait(for: [allDone], timeout: 5.0)
        // No assertion on exact value — just verifying no crash / deadlock.
    }
}
