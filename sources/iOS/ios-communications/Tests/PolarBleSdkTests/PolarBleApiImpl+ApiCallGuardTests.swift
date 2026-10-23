/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

final class PolarBleApiImplApiCallGuardTests: XCTestCase {

    private final class Gate: @unchecked Sendable {
        private let lock = NSLock()
        private var started = false
        private var released = false

        func markStarted() { lock.lock(); started = true; lock.unlock() }
        func release() { lock.lock(); released = true; lock.unlock() }
        private func isStarted() -> Bool { lock.lock(); defer { lock.unlock() }; return started }
        private func isReleased() -> Bool { lock.lock(); defer { lock.unlock() }; return released }

        func waitUntilStarted() async throws {
            while !isStarted() { try await Task.sleep(nanoseconds: 5_000_000) }
        }

        func waitUntilReleased() async throws {
            while !isReleased() { try await Task.sleep(nanoseconds: 5_000_000) }
        }
    }

    private let identifier = "TEST_DEVICE"
    private var mockClient: MockBlePsFtpClient!
    private var api: PolarBleApiImplWithMockSession!

    override func setUpWithError() throws {
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: [
                "360": .polarFileSystemV2,
                "h10": .h10FileSystem
            ]
        )
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        let session = MockBleDeviceSession(mockFtpClient: mockClient)
        api = PolarBleApiImplWithMockSession(mockDeviceSession: session)
    }

    override func tearDownWithError() throws {
        api = nil
        mockClient = nil
    }

    func testConcurrentGuardedCall_throwsConcurrentApiCallWhileFirstIsInProgress() async throws {
        let gate = Gate()
        mockClient.requestReturnValueClosure = { _ in
            gate.markStarted()
            try await gate.waitUntilReleased()
            return Data()
        }

        let first = Task { [api, identifier] in
            try await api!.getLogConfig(identifier)
        }
        try await gate.waitUntilStarted()

        do {
            _ = try await api.getSleep(identifier: identifier, fromDate: Date(), toDate: Date())
            XCTFail("Expected concurrentApiCall")
        } catch let error as PolarErrors {
            guard case let .concurrentApiCall(description) = error else {
                return XCTFail("Expected concurrentApiCall, got \(error)")
            }
            XCTAssertNotNil(description.range(of: "getSleep"))
            XCTAssertNotNil(description.range(of: "getLogConfig(\(identifier))"))
        }

        gate.release()
        _ = try? await first.value
    }

    func testGuardIsReleasedAfterFailure_nextCallIsNotRejected() async throws {
        mockClient.requestReturnValueClosure = { _ in
            throw BlePsFtpException.responseError(errorCode: 103)
        }

        _ = try? await api.getLogConfig(identifier)

        do {
            _ = try await api.getLogConfig(identifier)
        } catch PolarErrors.concurrentApiCall {
            XCTFail("Guard was not released after the first call failed")
        } catch {
            // The device error itself is expected; only the guard rejection is a failure.
        }
    }

    func testTelemetryApis_areNotRejectedWhileGuardedCallIsInProgress() async throws {
        let gate = Gate()
        mockClient.requestReturnValueClosure = { _ in
            gate.markStarted()
            try await gate.waitUntilReleased()
            return Data()
        }
        let first = Task { [api, identifier] in
            try await api!.getLogConfig(identifier)
        }
        try await gate.waitUntilStarted()

        do {
            _ = try await api.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, identifier)
        } catch PolarErrors.concurrentApiCall {
            XCTFail("Telemetry must not use the API call guard")
        } catch {}
        do {
            _ = try await api.startTelemetry(telemetryType: .memfault_mds, identifier)
        } catch PolarErrors.concurrentApiCall {
            XCTFail("Telemetry must not use the API call guard")
        } catch {}

        gate.release()
        _ = try? await first.value
    }

    func testGetSleepData_aliasDoesNotRejectItself() async throws {
        mockClient.requestReturnValueClosure = { _ in
            throw BlePsFtpException.responseError(errorCode: 103)
        }

        do {
            _ = try await api.getSleepData(identifier: identifier, fromDate: Date(), toDate: Date())
        } catch PolarErrors.concurrentApiCall {
            XCTFail("Deprecated alias must not take the guard twice")
        } catch {
            // Device errors are irrelevant here.
        }
    }
}
