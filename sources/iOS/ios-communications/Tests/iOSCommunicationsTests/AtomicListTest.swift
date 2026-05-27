//  Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class AtomicListTests: XCTestCase {

    // MARK: - append / push / pushItems / size

    func testAppendIncreasesSize() {
        let list = AtomicList<Int>()
        list.append(1)
        list.append(2)
        XCTAssertEqual(list.size(), 2)
    }

    func testPushIncreasesSize() {
        let list = AtomicList<Int>()
        list.push(10)
        XCTAssertEqual(list.size(), 1)
    }

    func testPushItemsAddsAllItems() {
        let list = AtomicList<Int>()
        list.pushItems([1, 2, 3])
        XCTAssertEqual(list.size(), 3)
    }

    func testPushItemsAppendsToExistingItems() {
        let list = AtomicList<Int>()
        list.append(0)
        list.pushItems([1, 2])
        XCTAssertEqual(list.size(), 3)
    }

    func testEmptyListHasSizeZero() {
        let list = AtomicList<String>()
        XCTAssertEqual(list.size(), 0)
    }

    // MARK: - list

    func testListReturnsSnapshotOfItems() {
        let list = AtomicList<Int>()
        list.pushItems([10, 20, 30])
        XCTAssertEqual(list.list(), [10, 20, 30])
    }

    func testListReturnsCopyNotReference() {
        let list = AtomicList<Int>()
        list.append(1)
        var snapshot = list.list()
        snapshot.append(99)
        XCTAssertEqual(list.size(), 1, "Mutating the snapshot must not affect the list")
    }

    // MARK: - removeAll

    func testRemoveAllClearsAllItems() {
        let list = AtomicList<Int>()
        list.pushItems([1, 2, 3])
        list.removeAll()
        XCTAssertEqual(list.size(), 0)
    }

    func testRemoveAllOnEmptyListDoesNotThrow() {
        let list = AtomicList<Int>()
        list.removeAll()
        XCTAssertEqual(list.size(), 0)
    }

    // MARK: - pop

    func testPopRemovesAndReturnsFirstItem() throws {
        let list = AtomicList<Int>()
        list.pushItems([1, 2, 3])
        let item = try list.pop()
        XCTAssertEqual(item, 1)
        XCTAssertEqual(list.size(), 2)
    }

    func testPopOnEmptyListThrowsEmptyQueueSignal() {
        let list = AtomicList<Int>()
        XCTAssertThrowsError(try list.pop()) { error in
            guard case AtomicListException.emptyQueueSignal = error else {
                XCTFail("Expected emptyQueueSignal, got \(error)")
                return
            }
        }
    }

    func testPopIsFirstInFirstOut() throws {
        let list = AtomicList<Int>()
        list.pushItems([10, 20, 30])
        XCTAssertEqual(try list.pop(), 10)
        XCTAssertEqual(try list.pop(), 20)
        XCTAssertEqual(try list.pop(), 30)
    }

    // MARK: - remove

    func testRemoveDeletesFirstMatchingItem() {
        let list = AtomicList<Int>()
        list.pushItems([1, 2, 3, 2])
        list.remove { $0 == 2 }
        XCTAssertEqual(list.list(), [1, 3, 2])
    }

    func testRemoveDoesNothingWhenNoMatchFound() {
        let list = AtomicList<Int>()
        list.pushItems([1, 2, 3])
        list.remove { $0 == 99 }
        XCTAssertEqual(list.size(), 3)
    }

    // MARK: - removeIf

    func testRemoveIfDeletesAllMatchingItems() {
        let list = AtomicList<Int>()
        list.pushItems([1, 2, 3, 4, 5])
        let removed = list.removeIf { $0 % 2 == 0 }
        XCTAssertEqual(removed, 2)
        XCTAssertEqual(list.list(), [1, 3, 5])
    }

    func testRemoveIfReturnsZeroWhenNothingMatches() {
        let list = AtomicList<Int>()
        list.pushItems([1, 3, 5])
        let removed = list.removeIf { $0 % 2 == 0 }
        XCTAssertEqual(removed, 0)
        XCTAssertEqual(list.size(), 3)
    }

    func testRemoveIfReturnsCorrectCountWhenAllRemoved() {
        let list = AtomicList<Int>()
        list.pushItems([2, 4, 6])
        let removed = list.removeIf { _ in true }
        XCTAssertEqual(removed, 3)
        XCTAssertEqual(list.size(), 0)
    }

    // MARK: - fetch

    func testFetchReturnsFirstMatchingItem() {
        let list = AtomicList<Int>()
        list.pushItems([1, 2, 3])
        let result = list.fetch { $0 == 2 }
        XCTAssertEqual(result, 2)
    }

    func testFetchReturnsNilWhenNoMatch() {
        let list = AtomicList<Int>()
        list.pushItems([1, 2, 3])
        let result = list.fetch { $0 == 99 }
        XCTAssertNil(result)
    }

    func testFetchDoesNotRemoveItem() {
        let list = AtomicList<Int>()
        list.pushItems([1, 2, 3])
        _ = list.fetch { $0 == 2 }
        XCTAssertEqual(list.size(), 3)
    }

    // MARK: - poll() (no timeout)

    func testPollReturnsFirstItemWhenAvailable() throws {
        let list = AtomicList<Int>()
        list.push(42)
        let item = try list.poll()
        XCTAssertEqual(item, 42)
    }

    func testPollThrowsEmptyQueueSignalWhenEmpty() {
        let list = AtomicList<Int>()
        XCTAssertThrowsError(try list.poll()) { error in
            guard case AtomicListException.emptyQueueSignal = error else {
                XCTFail("Expected emptyQueueSignal, got \(error)")
                return
            }
        }
    }

    // MARK: - poll(secs:) (with timeout)

    func testPollWithTimeoutReturnsItemWhenAvailable() throws {
        let list = AtomicList<Int>()
        list.push(7)
        let item = try list.poll(1.0)
        XCTAssertEqual(item, 7)
    }

    func testPollWithTimeoutReturnsItemPushedAfterWait() throws {
        let list = AtomicList<Int>()
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            list.push(99)
        }
        let item = try list.poll(2.0)
        XCTAssertEqual(item, 99)
    }

    func testPollWithTimeoutThrowsWaitTimeoutWhenEmpty() {
        let list = AtomicList<Int>()
        XCTAssertThrowsError(try list.poll(0.1)) { error in
            guard case AtomicListException.waitTimeout = error else {
                XCTFail("Expected waitTimeout, got \(error)")
                return
            }
        }
    }

    // MARK: - poll(secs:canceled:cancelError:)

    func testPollWithCancelThrowsCancelErrorWhenPreCancelled() {
        let list = AtomicList<Int>()
        let op = BlockOperation()
        op.cancel()
        let cancelError = NSError(domain: "test", code: 1)
        XCTAssertThrowsError(try list.poll(2.0, canceled: op, cancelError: cancelError)) { error in
            XCTAssertEqual((error as NSError).domain, "test")
        }
    }

    func testPollWithCancelReturnsItemWhenAvailableAndNotCancelled() throws {
        let list = AtomicList<Int>()
        list.push(5)
        let op = BlockOperation()
        let cancelError = NSError(domain: "test", code: 1)
        let item = try list.poll(1.0, canceled: op, cancelError: cancelError)
        XCTAssertEqual(item, 5)
    }

    func testPollWithCancelThrowsTimeoutWhenNoItemAndNotCancelled() {
        let list = AtomicList<Int>()
        let op = BlockOperation()
        let cancelError = NSError(domain: "test", code: 1)
        XCTAssertThrowsError(try list.poll(0.1, canceled: op, cancelError: cancelError)) { error in
            guard case AtomicListException.waitTimeout = error else {
                XCTFail("Expected waitTimeout, got \(error)")
                return
            }
        }
    }

    // MARK: - pollUntilSignaled()

    func testPollUntilSignaledReturnsItemAlreadyPresent() throws {
        let list = AtomicList<Int>()
        list.push(11)
        let item = try list.pollUntilSignaled()
        XCTAssertEqual(item, 11)
    }

    func testPollUntilSignaledBlocksUntilPush() {
        let list = AtomicList<Int>()
        var received: Int?
        let done = XCTestExpectation(description: "pollUntilSignaled unblocked")

        DispatchQueue.global().async {
            received = try? list.pollUntilSignaled()
            done.fulfill()
        }
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            list.push(55)
        }

        wait(for: [done], timeout: 2.0)
        XCTAssertEqual(received, 55)
    }

    // MARK: - pollUntilSignaled(canceled:cancelError:)

    func testPollUntilSignaledCancelledThrowsCancelError() {
        let list = AtomicList<Int>()
        let op = BlockOperation()
        op.cancel()
        let cancelError = NSError(domain: "test", code: 2)
        XCTAssertThrowsError(
            try list.pollUntilSignaled(canceled: op, cancelError: cancelError)
        ) { error in
            XCTAssertEqual((error as NSError).domain, "test")
        }
    }

    func testPollUntilSignaledWithCancelReturnsItemWhenAvailable() throws {
        let list = AtomicList<Int>()
        list.push(77)
        let op = BlockOperation()
        let cancelError = NSError(domain: "test", code: 2)
        let item = try list.pollUntilSignaled(canceled: op, cancelError: cancelError)
        XCTAssertEqual(item, 77)
    }

    // MARK: - Thread safety

    func testConcurrentAppendReachesExpectedSize() {
        let list = AtomicList<Int>()
        let iterations = 1000
        let allDone = XCTestExpectation(description: "all appends done")
        allDone.expectedFulfillmentCount = iterations

        for i in 0..<iterations {
            DispatchQueue.global().async {
                list.append(i)
                allDone.fulfill()
            }
        }

        wait(for: [allDone], timeout: 5.0)
        XCTAssertEqual(list.size(), iterations)
    }

    func testConcurrentPushAndPollDoNotDeadlock() {
        let list = AtomicList<Int>()
        let pushCount = 500
        let allDone = XCTestExpectation(description: "all done")
        allDone.expectedFulfillmentCount = pushCount

        for i in 0..<pushCount {
            DispatchQueue.global().async {
                list.push(i)
                _ = try? list.poll()
                allDone.fulfill()
            }
        }

        wait(for: [allDone], timeout: 5.0)
    }
}
