//  Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class AtomicTypeTests: XCTestCase {

    // MARK: - get / set

    func testInitialValueIsReturnedByGet() {
        let atomic = AtomicType(initialValue: 42)
        XCTAssertEqual(atomic.get(), 42)
    }

    func testSetUpdatesValue() {
        let atomic = AtomicType(initialValue: 0)
        atomic.set(99)
        XCTAssertEqual(atomic.get(), 99)
    }

    func testSetOverwritesPreviousValue() {
        let atomic = AtomicType(initialValue: "hello")
        atomic.set("world")
        atomic.set("polar")
        XCTAssertEqual(atomic.get(), "polar")
    }

    // MARK: - accessItem

    func testAccessItemMutatesStoredValue() {
        let atomic = AtomicType(initialValue: [Int]())
        atomic.accessItem { $0.append(1) }
        atomic.accessItem { $0.append(2) }
        XCTAssertEqual(atomic.get(), [1, 2])
    }

    func testAccessItemReadsCurrentValue() {
        let atomic = AtomicType(initialValue: 10)
        var captured = 0
        atomic.accessItem { captured = $0 }
        XCTAssertEqual(captured, 10)
    }

    func testAccessItemMutationIsVisibleViaGet() {
        let atomic = AtomicType(initialValue: 0)
        atomic.accessItem { $0 += 5 }
        XCTAssertEqual(atomic.get(), 5)
    }

    // MARK: - Thread safety

    func testConcurrentSetsProduceValidFinalValue() {
        let atomic = AtomicType(initialValue: 0)
        let iterations = 1000
        let expectation = expectation(description: "all writes done")
        expectation.expectedFulfillmentCount = iterations

        for i in 1...iterations {
            DispatchQueue.global().async {
                atomic.set(i)
                expectation.fulfill()
            }
        }

        wait(for: [expectation], timeout: 5.0)
        // The final value must be one of the written values (no corruption).
        let final = atomic.get()
        XCTAssertTrue((1...iterations).contains(final), "Final value \(final) is not in the expected range")
    }

    func testConcurrentAccessItemIncrements() {
        let atomic = AtomicType(initialValue: 0)
        let iterations = 1000
        let expectation = expectation(description: "all increments done")
        expectation.expectedFulfillmentCount = iterations

        for _ in 0..<iterations {
            DispatchQueue.global().async {
                atomic.accessItem { $0 += 1 }
                expectation.fulfill()
            }
        }

        wait(for: [expectation], timeout: 5.0)
        XCTAssertEqual(atomic.get(), iterations)
    }

    func testConcurrentMixedReadsAndWrites() {
        let atomic = AtomicType(initialValue: 0)
        let writes = 500
        let reads = 500
        let allDone = expectation(description: "reads and writes done")
        allDone.expectedFulfillmentCount = writes + reads

        for i in 1...writes {
            DispatchQueue.global().async {
                atomic.set(i)
                allDone.fulfill()
            }
        }
        for _ in 0..<reads {
            DispatchQueue.global().async {
                _ = atomic.get()
                allDone.fulfill()
            }
        }

        wait(for: [allDone], timeout: 5.0)
        // No assertion on exact value — just verifying no crash / deadlock.
    }

    // MARK: - wait / set signalling

    func testWaitIsSignalledBySet() {
        let atomic = AtomicType(initialValue: false)
        let signalled = expectation(description: "wait returned after set")

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

    // MARK: - Generic type support

    func testWorksWithString() {
        let atomic = AtomicType(initialValue: "initial")
        atomic.set("updated")
        XCTAssertEqual(atomic.get(), "updated")
    }

    func testWorksWithOptional() {
        let atomic = AtomicType<Int?>(initialValue: nil)
        XCTAssertNil(atomic.get())
        atomic.set(7)
        XCTAssertEqual(atomic.get(), 7)
    }

    func testWorksWithDictionary() {
        let atomic = AtomicType(initialValue: [String: Int]())
        atomic.accessItem { $0["a"] = 1 }
        atomic.accessItem { $0["b"] = 2 }
        XCTAssertEqual(atomic.get(), ["a": 1, "b": 2])
    }
}
