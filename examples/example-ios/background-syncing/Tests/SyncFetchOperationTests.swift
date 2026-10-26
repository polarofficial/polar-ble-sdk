// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest

final class SyncFetchOperationTests: XCTestCase {
    func testBackgroundExpirationClosesCycleAndPreservesPostponedTypes() {
        var plan = SyncFetchPlan(maxRetries: 3)
        let types: [SyncableDataType] = [.steps, .hrSamples247, .ppiSamples247]
        plan.beginCycle(dataTypes: types)
        plan.complete(.steps)
        plan.recordFailure(.hrSamples247)
        XCTAssertEqual(plan.completedTypeCount, 1, "Failures must not count as completed types")
        XCTAssertEqual(plan.pendingTypes, [.ppiSamples247, .hrSamples247])
        plan.postponeRemaining()
        XCTAssertFalse(plan.isActive)
        XCTAssertNil(plan.next, "No immediate continuation after background expiration")
        XCTAssertEqual(plan.postponedTypes, [.ppiSamples247, .hrSamples247])
        XCTAssertTrue(plan.exhaustedTypes.isEmpty, "Budget exhaustion is not a data-type failure")
        XCTAssertTrue(plan.beginCycle(dataTypes: types))
        XCTAssertEqual(plan.completedTypeCount, 0)
        XCTAssertTrue(plan.postponedTypes.isEmpty)
        XCTAssertEqual(plan.next, .steps)
    }

    func testDisconnectFailuresRotateAndExhaustWithoutStarvingRemainingTypes() {
        var plan = SyncFetchPlan(maxRetries: 1)
        let types: [SyncableDataType] = [.hrSamples247, .steps, .ppiSamples247]
        plan.beginCycle(dataTypes: types)
        XCTAssertTrue(plan.recordFailure(.hrSamples247))
        XCTAssertFalse(plan.beginCycle(dataTypes: types))
        XCTAssertEqual(plan.next, .steps)
        plan.complete(.steps)
        plan.complete(.ppiSamples247)
        XCTAssertFalse(plan.recordFailure(.hrSamples247))
        XCTAssertNil(plan.next)
        XCTAssertEqual(plan.exhaustedTypes, [.hrSamples247])
    }

    func testRepeatedFailuresDoNotBlockHealthyTypesOrResetOnReconnect() {
        let types: [SyncableDataType] = [.hrSamples247, .ppiSamples247, .steps]
        var plan = SyncFetchPlan(maxRetries: 1)
        XCTAssertTrue(plan.beginCycle(dataTypes: types))

        XCTAssertTrue(plan.recordFailure(.hrSamples247))
        XCTAssertEqual(plan.next, .ppiSamples247)
        XCTAssertTrue(plan.recordFailure(.ppiSamples247))
        XCTAssertEqual(plan.next, .steps)
        XCTAssertFalse(plan.nextIsRetry)
        plan.complete(.steps)

        XCTAssertFalse(plan.beginCycle(dataTypes: types), "Reconnect must continue, not restart the cycle")
        XCTAssertEqual(plan.pendingTypes, [.hrSamples247, .ppiSamples247])
        XCTAssertTrue(plan.nextIsRetry)
        XCTAssertFalse(plan.recordFailure(.hrSamples247))
        XCTAssertEqual(plan.next, .ppiSamples247)
        plan.complete(.ppiSamples247)
        XCTAssertNil(plan.next)
        XCTAssertEqual(plan.exhaustedTypes, [.hrSamples247])
    }

    func testRetryBudgetIsPerTypeNotSharedAcrossFailures() {
        var plan = SyncFetchPlan(maxRetries: 3)
        plan.beginCycle(dataTypes: [.hrSamples247, .ppiSamples247])
        for _ in 0..<3 {
            XCTAssertEqual(plan.next, .hrSamples247)
            XCTAssertTrue(plan.recordFailure(.hrSamples247))
            XCTAssertEqual(plan.next, .ppiSamples247)
            XCTAssertTrue(plan.recordFailure(.ppiSamples247))
        }
        XCTAssertFalse(plan.recordFailure(.hrSamples247))
        XCTAssertEqual(plan.next, .ppiSamples247)
        XCTAssertFalse(plan.recordFailure(.ppiSamples247))
        XCTAssertNil(plan.next)
        XCTAssertEqual(plan.exhaustedTypes, [.hrSamples247, .ppiSamples247])
    }

    func testAllFailedTypesWaitForNewCycleAndReceiveFreshRetryBudgets() {
        let types: [SyncableDataType] = [.hrSamples247, .steps]
        var plan = SyncFetchPlan(maxRetries: 1)
        plan.beginCycle(dataTypes: types)
        var attempts: [SyncableDataType] = []
        for _ in 0..<10 {
            guard let type = plan.next else { break }
            attempts.append(type)
            plan.recordFailure(type)
        }
        XCTAssertEqual(attempts, [.hrSamples247, .steps, .hrSamples247, .steps])
        XCTAssertNil(plan.next)
        plan.finishCycle()
        XCTAssertNil(plan.next, "Finishing must not automatically requeue exhausted work")

        XCTAssertTrue(plan.beginCycle(dataTypes: types))
        XCTAssertTrue(plan.exhaustedTypes.isEmpty)
        XCTAssertEqual(plan.next, .hrSamples247)
        XCTAssertFalse(plan.nextIsRetry)
        XCTAssertTrue(plan.recordFailure(.hrSamples247), "A new trigger must restore the retry budget")
        XCTAssertEqual(plan.next, .steps)
    }

    @MainActor
    func testFailedTypeDoesNotAdvanceToOtherSDKWorkUntilDisconnectAndDrain() async {
        let started = expectation(description: "SDK request started")
        let timedOut = expectation(description: "Request timed out")
        let drained = expectation(description: "Request drained")
        var plan = SyncFetchPlan(maxRetries: 0)
        plan.beginCycle(dataTypes: [.hrSamples247, .steps])
        let registry = SyncFetchRegistry<Int>()
        let fetch = SyncFetchOperation<Int>()
        registry["sensor"] = fetch
        var request: CheckedContinuation<Int, Error>?
        let caller = Task {
            do {
                _ = try await fetch.run(seconds: 0.05, operation: {
                    try await withCheckedThrowingContinuation { continuation in
                        request = continuation
                        started.fulfill()
                    }
                }, onTimeout: {
                    XCTAssertFalse(plan.recordFailure(.hrSamples247))
                    registry.requestDisconnect("sensor")
                }, onDrained: {
                    registry["sensor"] = nil
                    drained.fulfill()
                })
                XCTFail("Expected timeout")
            } catch is FetchTimeoutError {
                timedOut.fulfill()
            } catch {
                XCTFail("Unexpected error: \(error)")
            }
        }
        await fulfillment(of: [started, timedOut], timeout: 2)
        XCTAssertEqual(plan.next, .steps)
        XCTAssertTrue(registry.isBlocked("sensor"))
        registry.didDisconnect("sensor")
        XCTAssertTrue(registry.isBlocked("sensor"), "Exhausted retries must not bypass request draining")
        request?.resume(returning: 42)
        await fulfillment(of: [drained], timeout: 2)
        await caller.value
        XCTAssertFalse(registry.isBlocked("sensor"))
        XCTAssertFalse(plan.beginCycle(dataTypes: [.hrSamples247, .steps]))
        XCTAssertEqual(plan.next, .steps)
    }

    @MainActor
    func testDeadlineReturnsBeforeNonCancellableRequestDrains() async {
        let started = expectation(description: "SDK request started")
        let timedOut = expectation(description: "Deadline reported")
        let drained = expectation(description: "SDK request drained")
        var request: CheckedContinuation<Int, Error>?
        var didDrain = false
        var disconnects = 0
        let fetch = SyncFetchOperation<Int>()
        let caller = Task {
            do {
                _ = try await fetch.run(seconds: 0.05, operation: {
                    try await withCheckedThrowingContinuation { continuation in
                        request = continuation
                        started.fulfill()
                    }
                }, onTimeout: {
                    disconnects += 1
                }, onDrained: {
                    didDrain = true
                    drained.fulfill()
                })
                XCTFail("Expected timeout")
            } catch is FetchTimeoutError {
                timedOut.fulfill()
            } catch {
                XCTFail("Unexpected error: \(error)")
            }
        }
        await fulfillment(of: [started, timedOut], timeout: 2)
        XCTAssertFalse(didDrain, "Recovery must keep the old request quarantined")
        XCTAssertEqual(disconnects, 1)
        request?.resume(returning: 42)
        await fulfillment(of: [drained], timeout: 2)
        await caller.value
        XCTAssertEqual(disconnects, 1)
    }

    @MainActor
    func testCancellationReleasesCallerButStillTracksPendingRequest() async {
        let started = expectation(description: "SDK request started")
        let cancelled = expectation(description: "Caller cancelled")
        let drained = expectation(description: "SDK request drained")
        var request: CheckedContinuation<Int, Error>?
        let fetch = SyncFetchOperation<Int>()
        let caller = Task {
            do {
                _ = try await fetch.run(seconds: 60, operation: {
                    try await withCheckedThrowingContinuation { continuation in
                        request = continuation
                        started.fulfill()
                    }
                }, onTimeout: {
                    XCTFail("Cancellation must not report a timeout")
                }, onDrained: {
                    drained.fulfill()
                })
                XCTFail("Expected cancellation")
            } catch is CancellationError {
                cancelled.fulfill()
            } catch {
                XCTFail("Unexpected error: \(error)")
            }
        }
        await fulfillment(of: [started], timeout: 2)
        caller.cancel()
        await fulfillment(of: [cancelled], timeout: 2)
        fetch.cancel()
        request?.resume(returning: 42)
        await fulfillment(of: [drained], timeout: 2)
        await caller.value
    }

    @MainActor
    func testSuccessfulFetchDoesNotDisconnect() async throws {
        let fetch = SyncFetchOperation<Int>()
        var didDrain = false
        let result = try await fetch.run(seconds: 60, operation: { 42 }, onTimeout: {
            XCTFail("Successful fetch must not disconnect")
        }, onDrained: {
            didDrain = true
        })
        XCTAssertEqual(result, 42)
        XCTAssertTrue(didDrain)
    }

    @MainActor
    func testAlreadyCancelledFetchReleasesRegistryWithoutStartingRequest() async {
        var didDrain = false
        let fetch = SyncFetchOperation<Int>()
        let caller = Task {
            do {
                _ = try await fetch.run(seconds: 60, operation: {
                    XCTFail("Already cancelled operation must not issue an SDK request")
                    return 42
                }, onTimeout: {
                    XCTFail("Already cancelled operation must not disconnect")
                }, onDrained: {
                    didDrain = true
                })
                XCTFail("Expected cancellation")
            } catch is CancellationError {
            } catch {
                XCTFail("Unexpected error: \(error)")
            }
        }
        caller.cancel()
        await caller.value
        XCTAssertTrue(didDrain)
    }

    @MainActor
    func testRecoveryWaitsForBothDisconnectAndDrainInEitherOrder() {
        let registry = SyncFetchRegistry<Int>()
        for disconnectFirst in [true, false] {
            registry["sensor"] = SyncFetchOperation<Int>()
            registry.requestDisconnect("sensor")
            XCTAssertFalse(registry.isBlocked("other-sensor"))
            if disconnectFirst {
                registry.didDisconnect("sensor")
            } else {
                registry["sensor"] = nil
            }
            XCTAssertTrue(registry.isBlocked("sensor"))
            if disconnectFirst {
                registry["sensor"] = nil
            } else {
                registry.didDisconnect("sensor")
            }
            XCTAssertFalse(registry.isBlocked("sensor"))
        }
    }

    func testMissingSleepDaysReturnEmptyAndContinueAcrossDaylightSavingChange() async throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "Europe/Helsinki"))
        let from = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 24, hour: 14)))
        let to = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 26)))
        var requestedDays: [Int] = []
        let values: [Int] = try await CalendarDayFetcher.fetch(from: from, to: to, calendar: calendar) { day in
            XCTAssertEqual(calendar.component(.hour, from: day), 0)
            requestedDays.append(calendar.component(.day, from: day))
            return []
        }
        XCTAssertEqual(requestedDays, [24, 25, 26])
        XCTAssertTrue(values.isEmpty)
    }

    @MainActor
    func testCancelledSleepReadDoesNotFetchNextDayOrPublishLateData() async throws {
        let started = expectation(description: "Sleep request started")
        var request: CheckedContinuation<[Int], Error>?
        var requests = 0
        var completedDays = 0
        let from = Date()
        let to = try XCTUnwrap(Calendar.current.date(byAdding: .day, value: 2, to: from))
        let caller = Task {
            try await CalendarDayFetcher.fetch(
                from: from, to: to, onDayCompleted: { _ in completedDays += 1 }
            ) { _ in
                requests += 1
                return try await withCheckedThrowingContinuation { continuation in
                    request = continuation
                    started.fulfill()
                }
            }
        }
        await fulfillment(of: [started], timeout: 2)
        caller.cancel()
        request?.resume(returning: [42])
        do {
            _ = try await caller.value
            XCTFail("Cancelled sleep data must not be published or saved")
        } catch is CancellationError {
            XCTAssertEqual(requests, 1)
            XCTAssertEqual(completedDays, 0)
        }
    }

    @MainActor
    func testRecoveryResumesAtUnfinishedDayIncludingPreviouslyEmptyDays() async throws {
        struct ReadError: Error {}
        let calendar = Calendar.current
        let first = calendar.startOfDay(for: Date())
        let second = try XCTUnwrap(calendar.date(byAdding: .day, value: 1, to: first))
        let third = try XCTUnwrap(calendar.date(byAdding: .day, value: 2, to: first))
        var cursor = first
        var requests: [Date] = []
        let checkpoint: (Date) -> Void = { day in
            cursor = calendar.date(byAdding: .day, value: 1, to: day)!
        }
        do {
            let _: [Int] = try await CalendarDayFetcher.fetch(
                from: cursor, to: third, onDayCompleted: checkpoint
            ) { day in
                requests.append(day)
                if day == second { throw ReadError() }
                return []
            }
            XCTFail("Expected interrupted scan")
        } catch is ReadError {
            XCTAssertEqual(cursor, second)
        }
        let values: [Int] = try await CalendarDayFetcher.fetch(
            from: cursor, to: third, onDayCompleted: checkpoint
        ) { day in
            requests.append(day)
            return [calendar.component(.day, from: day)]
        }
        XCTAssertEqual(requests, [first, second, second, third])
        XCTAssertEqual(values.count, 2)
    }

    func testSleepReadErrorIsNotReportedAsEmptySuccess() async {
        struct ReadError: Error {}
        do {
            let _: [Int] = try await CalendarDayFetcher.fetch(from: Date(), to: Date()) { _ in
                throw ReadError()
            }
            XCTFail("Expected the fetch failure to reach sync error handling")
        } catch is ReadError {
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testUTCDateChunksPreserveSDKRangeInterpretation() async throws {
        var local = Calendar(identifier: .gregorian)
        local.timeZone = try XCTUnwrap(TimeZone(identifier: "Europe/Helsinki"))
        let from = try XCTUnwrap(local.date(from: DateComponents(year: 2026, month: 10, day: 25)))
        let to = try XCTUnwrap(local.date(from: DateComponents(year: 2026, month: 10, day: 26)))
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: 0))
        let days: [Int] = try await CalendarDayFetcher.fetch(from: from, to: to, calendar: utc) { day in
            XCTAssertEqual(utc.component(.hour, from: day), 0)
            return [utc.component(.day, from: day)]
        }
        XCTAssertEqual(days, [24, 25])
    }

    func testStorageFailureIsThrownInsteadOfReturningSuccessfulPath() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try Data([0]).write(to: root)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = LocalDataStore(rootDirectory: root)
        XCTAssertThrowsError(try store.saveData(
            deviceId: "test", dataType: .steps, date: Date(), jsonContent: "{}"))
        XCTAssertThrowsError(try store.saveRawData(
            deviceId: "test", dataType: .offlineHr, dateTime: Date(), data: Data([1])))
    }

    @MainActor
    func testCancellationPreventsLocalWrites() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = LocalDataStore(rootDirectory: root)
        defer { try? FileManager.default.removeItem(at: root) }
        let caller = Task {
            XCTAssertThrowsError(try store.saveData(
                deviceId: "test", dataType: .steps, date: Date(), jsonContent: "{}")) {
                XCTAssertTrue($0 is CancellationError)
            }
            XCTAssertThrowsError(try store.saveRawData(
                deviceId: "test", dataType: .offlineHr, dateTime: Date(), data: Data([1]))) {
                XCTAssertTrue($0 is CancellationError)
            }
        }
        caller.cancel()
        try await caller.value
        XCTAssertTrue(store.listEntries(
            deviceId: "test", dataType: .steps, fromDate: nil, toDate: nil).isEmpty)
        XCTAssertTrue(store.listEntries(
            deviceId: "test", dataType: .offlineHr, fromDate: nil, toDate: nil).isEmpty)
    }
}
