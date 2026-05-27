//  Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class AtomicIntegerTests: XCTestCase {

    // MARK: - get / set

    func testInitialValueIsReturnedByGet() {
        let atomic = AtomicInteger(initialValue: 42)
        XCTAssertEqual(atomic.get(), 42)
    }

    func testSetUpdatesValue() {
        let atomic = AtomicInteger(initialValue: 0)
        atomic.set(99)
        XCTAssertEqual(atomic.get(), 99)
    }

    func testSetNegativeValue() {
        let atomic = AtomicInteger(initialValue: 5)
        atomic.set(-10)
        XCTAssertEqual(atomic.get(), -10)
    }

    // MARK: - increment / decrement

    func testIncrementAndGetReturnsIncrementedValue() {
        let atomic = AtomicInteger(initialValue: 0)
        let result = atomic.incrementAndGet()
        XCTAssertEqual(result, 1)
        XCTAssertEqual(atomic.get(), 1)
    }

    func testIncrementDoesNotReturnValue() {
        let atomic = AtomicInteger(initialValue: 5)
        atomic.increment()
        XCTAssertEqual(atomic.get(), 6)
    }

    func testDecrementAndGetReturnsDecrementedValue() {
        let atomic = AtomicInteger(initialValue: 3)
        let result = atomic.decrementAndGet()
        XCTAssertEqual(result, 2)
        XCTAssertEqual(atomic.get(), 2)
    }

    func testDecrementBelowZero() {
        let atomic = AtomicInteger(initialValue: 0)
        _ = atomic.decrementAndGet()
        XCTAssertEqual(atomic.get(), -1)
    }

    func testMultipleIncrements() {
        let atomic = AtomicInteger(initialValue: 0)
        for _ in 0..<5 { atomic.increment() }
        XCTAssertEqual(atomic.get(), 5)
    }

    func testMultipleDecrements() {
        let atomic = AtomicInteger(initialValue: 5)
        for _ in 0..<5 { _ = atomic.decrementAndGet() }
        XCTAssertEqual(atomic.get(), 0)
    }

    // MARK: - reset

    func testResetSetsValueToZero() {
        let atomic = AtomicInteger(initialValue: 100)
        atomic.reset()
        XCTAssertEqual(atomic.get(), 0)
    }

    func testResetFromNegativeValue() {
        let atomic = AtomicInteger(initialValue: -50)
        atomic.reset()
        XCTAssertEqual(atomic.get(), 0)
    }

    // MARK: - prefix ++ / -- operators

    func testPrefixIncrementOperator() {
        var atomic = AtomicInteger(initialValue: 10)
        ++atomic
        XCTAssertEqual(atomic.get(), 11)
    }

    func testPrefixDecrementOperator() {
        var atomic = AtomicInteger(initialValue: 10)
        --atomic
        XCTAssertEqual(atomic.get(), 9)
    }

    // MARK: - wait / signal

    func testWaitIsSignalledBySet() {
        let atomic = AtomicInteger(initialValue: 0)
        let signalled = XCTestExpectation(description: "wait returned after set")

        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            atomic.set(1)
        }
        DispatchQueue.global().async {
            atomic.wait()
            signalled.fulfill()
        }

        wait(for: [signalled], timeout: 2.0)
        XCTAssertEqual(atomic.get(), 1)
    }

    func testSignalUnblocksWait() {
        let atomic = AtomicInteger(initialValue: 0)
        let signalled = XCTestExpectation(description: "wait returned after signal")

        DispatchQueue.global().async {
            atomic.wait()
            signalled.fulfill()
        }
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            atomic.signal()
        }

        wait(for: [signalled], timeout: 2.0)
    }

    // MARK: - checkAndWait (no timeout)

    func testCheckAndWaitReturnsImmediatelyWhenValueMatches() {
        let atomic = AtomicInteger(initialValue: 7)
        let done = XCTestExpectation(description: "returns immediately")
        DispatchQueue.global().async {
            atomic.checkAndWait(7)
            done.fulfill()
        }
        wait(for: [done], timeout: 1.0)
    }

    func testCheckAndWaitBlocksUntilValueMatches() {
        let atomic = AtomicInteger(initialValue: 0)
        let done = XCTestExpectation(description: "unblocked after set")

        DispatchQueue.global().async {
            atomic.checkAndWait(1)
            done.fulfill()
        }
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            atomic.set(1)
        }

        wait(for: [done], timeout: 2.0)
        XCTAssertEqual(atomic.get(), 1)
    }

    // MARK: - checkAndWait with timeout

    func testCheckAndWaitWithTimeoutSucceedsWhenValueAlreadyMatches() throws {
        let atomic = AtomicInteger(initialValue: 5)
        XCTAssertNoThrow(try atomic.checkAndWait(5, secs: 1.0))
    }

    func testCheckAndWaitWithTimeoutSucceedsWhenValueChangesInTime() throws {
        let atomic = AtomicInteger(initialValue: 0)
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            atomic.set(3)
        }
        XCTAssertNoThrow(try atomic.checkAndWait(3, secs: 2.0))
    }

    func testCheckAndWaitWithTimeoutThrowsWhenTimeoutExpires() {
        let atomic = AtomicInteger(initialValue: 0)
        XCTAssertThrowsError(try atomic.checkAndWait(99, secs: 0.1)) { error in
            guard case AtomicIntegerException.waitTimeout = error else {
                XCTFail("Expected waitTimeout, got \(error)")
                return
            }
        }
    }

    func testCheckAndWaitWithTimeoutCallIsInvokedOnTimeout() {
        let atomic = AtomicInteger(initialValue: 0)
        var timeoutCallInvoked = false
        XCTAssertThrowsError(
            try atomic.checkAndWait(99, secs: 0.1, timeoutCall: { timeoutCallInvoked = true })
        )
        XCTAssertTrue(timeoutCallInvoked, "timeoutCall should have been invoked on timeout")
    }

    // MARK: - checkAndWait with BlockOperation cancellation

    func testCheckAndWaitWithCanceledOperationThrowsCanceledError() {
        let atomic = AtomicInteger(initialValue: 0)
        let operation = BlockOperation()
        operation.cancel()
        let cancelError = NSError(domain: "test", code: 1)

        XCTAssertThrowsError(
            try atomic.checkAndWait(1, secs: 2.0, canceled: operation, canceledError: cancelError, timeoutCall: {})
        ) { error in
            XCTAssertEqual((error as NSError).domain, "test")
        }
    }

    func testCheckAndWaitWithBlockOperationSucceedsWhenValueMatches() throws {
        let atomic = AtomicInteger(initialValue: 5)
        let operation = BlockOperation()
        let cancelError = NSError(domain: "test", code: 1)
        XCTAssertNoThrow(
            try atomic.checkAndWait(5, secs: 1.0, canceled: operation, canceledError: cancelError, timeoutCall: {})
        )
    }

    // MARK: - Thread safety

    func testConcurrentIncrementsReachExpectedTotal() {
        let atomic = AtomicInteger(initialValue: 0)
        let iterations = 1000
        let allDone = XCTestExpectation(description: "all increments done")
        allDone.expectedFulfillmentCount = iterations

        for _ in 0..<iterations {
            DispatchQueue.global().async {
                atomic.increment()
                allDone.fulfill()
            }
        }

        wait(for: [allDone], timeout: 5.0)
        XCTAssertEqual(atomic.get(), iterations)
    }

    func testConcurrentSetsProduceValidFinalValue() {
        let atomic = AtomicInteger(initialValue: 0)
        let iterations = 1000
        let allDone = XCTestExpectation(description: "all sets done")
        allDone.expectedFulfillmentCount = iterations

        for i in 1...iterations {
            DispatchQueue.global().async {
                atomic.set(i)
                allDone.fulfill()
            }
        }

        wait(for: [allDone], timeout: 5.0)
        let final = atomic.get()
        XCTAssertTrue((1...iterations).contains(final), "Final value \(final) is out of expected range")
    }

    func testConcurrentMixedReadsAndWrites() {
        let atomic = AtomicInteger(initialValue: 0)
        let ops = 500
        let allDone = XCTestExpectation(description: "all ops done")
        allDone.expectedFulfillmentCount = ops * 2

        for i in 0..<ops {
            DispatchQueue.global().async {
                atomic.set(i)
                allDone.fulfill()
            }
            DispatchQueue.global().async {
                _ = atomic.get()
                allDone.fulfill()
            }
        }

        wait(for: [allDone], timeout: 5.0)
    }
}
