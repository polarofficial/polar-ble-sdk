/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

final class PolarApiCallGuardTests: XCTestCase {

    private final class Gate: @unchecked Sendable {
        private let lock = NSLock()
        private var started = false
        private var released = false

        func markStarted() { lock.lock(); started = true; lock.unlock() }
        func release() { lock.lock(); released = true; lock.unlock() }
        var hasStarted: Bool { isStarted() }
        private func isStarted() -> Bool { lock.lock(); defer { lock.unlock() }; return started }
        private func isReleased() -> Bool { lock.lock(); defer { lock.unlock() }; return released }

        func waitUntilStarted() async throws {
            while !isStarted() { try await Task.sleep(nanoseconds: 5_000_000) }
        }

        func waitUntilReleased() async throws {
            while !isReleased() { try await Task.sleep(nanoseconds: 5_000_000) }
        }
    }

    private func assertConcurrentApiCall(_ error: Error, containing fragments: [String], file: StaticString = #filePath, line: UInt = #line) {
        guard case let PolarErrors.concurrentApiCall(description) = error else {
            return XCTFail("Expected concurrentApiCall, got \(error)", file: file, line: line)
        }
        for fragment in fragments {
            XCTAssertNotNil(description.range(of: fragment), "'\(description)' should contain '\(fragment)'", file: file, line: line)
        }
    }

    func testGuardAsync_releasesLockAfterSuccess() async throws {
        let guardUnderTest = PolarApiCallGuard()

        let first = try await guardUnderTest.guardAsync("read", "A1") { "first" }
        let second = try await guardUnderTest.guardAsync("read", "A1") { "second" }

        XCTAssertEqual(first, "first")
        XCTAssertEqual(second, "second")
    }

    func testGuardAsync_releasesLockAfterFailure() async throws {
        let guardUnderTest = PolarApiCallGuard()

        do {
            try await guardUnderTest.guardAsync("read", "A1") { throw NSError(domain: "test", code: 1) }
            XCTFail("Expected failure")
        } catch let error as NSError {
            XCTAssertEqual(error.domain, "test")
        }

        let recovered = try await guardUnderTest.guardAsync("read", "A1") { "recovered" }
        XCTAssertEqual(recovered, "recovered")
    }

    func testGuardAsync_rejectsOverlappingCallsOnSameDevice() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let gate = Gate()

        let first = Task {
            try await guardUnderTest.guardAsync("first", "device-1") {
                gate.markStarted()
                try await gate.waitUntilReleased()
                return "first-result"
            }
        }
        try await gate.waitUntilStarted()

        do {
            _ = try await guardUnderTest.guardAsync("second", "device-1") { "second-result" }
            XCTFail("Expected concurrentApiCall")
        } catch {
            assertConcurrentApiCall(error, containing: ["Cannot start second", "first(device-1)"])
        }

        gate.release()
        let result = try await first.value
        XCTAssertEqual(result, "first-result")
    }

    func testGuardAsync_allowsOverlappingCallsOnDifferentDevices() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let gate = Gate()

        let first = Task {
            try await guardUnderTest.guardAsync("first", "device-1") {
                gate.markStarted()
                try await gate.waitUntilReleased()
            }
        }
        try await gate.waitUntilStarted()

        let other = try await guardUnderTest.guardAsync("second", "device-2") { "other-device" }
        XCTAssertEqual(other, "other-device")

        gate.release()
        try await first.value
    }

    func testGuardAsync_allowConcurrentBypassesLockAndDoesNotHoldIt() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let gate = Gate()

        let first = Task {
            try await guardUnderTest.guardAsync("first", "device-1") {
                gate.markStarted()
                try await gate.waitUntilReleased()
            }
        }
        try await gate.waitUntilStarted()

        let bypassed = try await guardUnderTest.guardAsync("status", "device-1", allowConcurrent: true) { "ok" }
        XCTAssertEqual(bypassed, "ok")

        gate.release()
        try await first.value

        let bypassGate = Gate()
        let concurrentFirst = Task {
            try await guardUnderTest.guardAsync("status", "device-1", allowConcurrent: true) {
                bypassGate.markStarted()
                try await bypassGate.waitUntilReleased()
            }
        }
        try await bypassGate.waitUntilStarted()
        let guarded = try await guardUnderTest.guardAsync("read", "device-1") { "guarded" }
        XCTAssertEqual(guarded, "guarded")
        bypassGate.release()
        try await concurrentFirst.value
    }

    func testGuardAsync_releasesLockWhenTaskIsCancelled() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let gate = Gate()

        let first = Task {
            try await guardUnderTest.guardAsync("first", "device-1") {
                gate.markStarted()
                try await gate.waitUntilReleased()
            }
        }
        try await gate.waitUntilStarted()
        first.cancel()
        _ = try? await first.value

        let next = try await guardUnderTest.guardAsync("next", "device-1") { "next" }
        XCTAssertEqual(next, "next")
    }

    func testGuardSync_locksAndReleases() throws {
        let guardUnderTest = PolarApiCallGuard()

        let value = try guardUnderTest.guardSync("sync", "A1") {
            do {
                _ = try guardUnderTest.guardSync("nested", "A1") { 0 }
                XCTFail("Expected concurrentApiCall")
            } catch {
                assertConcurrentApiCall(error, containing: ["Cannot start nested", "sync(A1)"])
            }
            return 1
        }

        XCTAssertEqual(value, 1)
        XCTAssertEqual(try guardUnderTest.guardSync("sync", "A1") { 2 }, 2)
    }

    func testGuardStream_holdsLockUntilStreamFinishes() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let gate = Gate()

        let stream = AsyncThrowingStream<Int, Error> { continuation in
            guardUnderTest.streamTask("observe", "device-1", continuation) {
                continuation.yield(1)
                gate.markStarted()
                try? await gate.waitUntilReleased()
                continuation.finish()
            }
        }

        let consumer = Task { () -> [Int] in
            var received: [Int] = []
            for try await value in stream { received.append(value) }
            return received
        }
        try await gate.waitUntilStarted()

        do {
            _ = try await guardUnderTest.guardAsync("read", "device-1") { 0 }
            XCTFail("Expected concurrentApiCall")
        } catch {
            assertConcurrentApiCall(error, containing: ["Cannot start read", "observe(device-1)"])
        }

        gate.release()
        let received = try await consumer.value
        XCTAssertEqual(received, [1])

        let afterStream = try await eventually { try await guardUnderTest.guardAsync("read", "device-1") { "free" } }
        XCTAssertEqual(afterStream, "free")
    }

    func testGuardStream_failsImmediatelyWhenLockIsBusy() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let token = try guardUnderTest.acquire("busy", "device-1")
        defer { guardUnderTest.release(token) }

        let bodyRan = Gate()
        let stream = AsyncThrowingStream<Int, Error> { continuation in
            guardUnderTest.streamTask("observe", "device-1", continuation) {
                bodyRan.markStarted()
                continuation.finish()
            }
        }

        do {
            for try await _ in stream { }
            XCTFail("Expected concurrentApiCall")
        } catch {
            assertConcurrentApiCall(error, containing: ["Cannot start observe", "busy(device-1)"])
        }
        XCTAssertFalse(bodyRan.hasStarted, "Body must not run when the lock cannot be acquired")
    }

    func testGuardStream_releasesLockWhenConsumerCancels() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let gate = Gate()

        let stream = AsyncThrowingStream<Int, Error> { continuation in
            guardUnderTest.streamTask("observe", "device-1", continuation) {
                gate.markStarted()
                while !Task.isCancelled { try? await Task.sleep(nanoseconds: 5_000_000) }
                continuation.finish()
            }
        }

        let consumer = Task { for try await _ in stream { } }
        try await gate.waitUntilStarted()
        consumer.cancel()
        _ = try? await consumer.value

        let afterCancel = try await eventually { try await guardUnderTest.guardAsync("read", "device-1") { "free" } }
        XCTAssertEqual(afterCancel, "free")
    }

    func testStreamTask_keepsLockUntilBodyStopsAfterConsumerCancels() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let started = Gate()
        let bodyMayExit = Gate()

        let stream = AsyncThrowingStream<Int, Error> { continuation in
            guardUnderTest.streamTask("observe", "device-1", continuation) {
                started.markStarted()
                try? await bodyMayExit.waitUntilReleased()
            }
        }

        let consumer = Task { for try await _ in stream { } }
        try await started.waitUntilStarted()
        consumer.cancel()
        _ = try? await consumer.value

        do {
            _ = try await guardUnderTest.guardAsync("read", "device-1") { 0 }
            XCTFail("Lock must be held while the stream body is still running")
        } catch {
            assertConcurrentApiCall(error, containing: ["observe(device-1)"])
        }

        bodyMayExit.release()
        let afterBody = try await eventually { try await guardUnderTest.guardAsync("read", "device-1") { "free" } }
        XCTAssertEqual(afterBody, "free")
    }

    func testStreamTask_lockIsFreeWhenConsumerSeesStreamEnd() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let stream = AsyncThrowingStream<Int, Error> { continuation in
            guardUnderTest.streamTask("observe", "device-1", continuation) {
                continuation.yield(1)
                continuation.finish()
            }
        }

        for try await _ in stream { }

        let free = try await guardUnderTest.guardAsync("read", "device-1") { "free" }
        XCTAssertEqual(free, "free")
    }

    func testStreamTask_differentDevicesDoNotBlockEachOther() async throws {
        let guardUnderTest = PolarApiCallGuard()
        let started = Gate()
        let stop = Gate()
        let stream = AsyncThrowingStream<Int, Error> { continuation in
            guardUnderTest.streamTask("observe", "device-1", continuation) {
                started.markStarted()
                try? await stop.waitUntilReleased()
            }
        }
        let consumer = Task { for try await _ in stream { } }
        try await started.waitUntilStarted()

        let other = try await guardUnderTest.guardAsync("read", "device-2") { "ok" }
        XCTAssertEqual(other, "ok")

        stop.release()
        _ = try? await consumer.value
    }

    func testParallelAcquireRelease_acrossDevicesIsConsistent() async throws {
        let guardUnderTest = PolarApiCallGuard()
        await withTaskGroup(of: Void.self) { group in
            for device in 0..<8 {
                for _ in 0..<50 {
                    group.addTask {
                        if let token = try? guardUnderTest.acquire("op", "device-\(device)") {
                            try? await Task.sleep(nanoseconds: 1_000_000)
                            guardUnderTest.release(token)
                        }
                    }
                }
            }
        }
        for device in 0..<8 {
            let token = try guardUnderTest.acquire("final", "device-\(device)")
            guardUnderTest.release(token)
        }
    }

    func testRelease_isIdempotentAndIgnoresStaleTokens() throws {
        let guardUnderTest = PolarApiCallGuard()
        let stale = try guardUnderTest.acquire("first", "device-1")
        guardUnderTest.release(stale)
        let current = try guardUnderTest.acquire("second", "device-1")

        guardUnderTest.release(stale)

        XCTAssertThrowsError(try guardUnderTest.acquire("third", "device-1"))
        guardUnderTest.release(current)
        XCTAssertNoThrow(try guardUnderTest.acquire("third", "device-1"))
    }

    private func eventually<T>(attempts: Int = 100, _ operation: () async throws -> T) async throws -> T {
        var lastError: Error?
        for _ in 0..<attempts {
            do { return try await operation() } catch PolarErrors.concurrentApiCall(let description) {
                lastError = PolarErrors.concurrentApiCall(description: description)
                try await Task.sleep(nanoseconds: 10_000_000)
            }
        }
        throw lastError ?? PolarErrors.timeout(description: "eventually")
    }
}
