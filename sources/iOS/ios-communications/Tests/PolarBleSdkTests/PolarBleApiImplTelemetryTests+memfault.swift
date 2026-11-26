// Copyright 2026 Polar Electro Oy. All rights reserved.

import XCTest
import Combine
import CoreBluetooth
@testable import PolarBleSdk

final class PolarBleApiImplMemfaultTests: XCTestCase {

    private var mockMdsClient: MockBleMdsClient!
    private var mockMdsSession: MockMdsBleDeviceSession!
    private var sut: MockMdsBleApiImpl!
    private var cancellables = Set<AnyCancellable>()

    override func setUpWithError() throws {
        mockMdsClient = MockBleMdsClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockMdsClient.setServiceDiscovered(true)
        mockMdsSession = MockMdsBleDeviceSession(mockMdsClient: mockMdsClient)
        sut = MockMdsBleApiImpl(mockMdsSession: mockMdsSession)
    }

    override func tearDownWithError() throws {
        sut = nil
        mockMdsSession = nil
        mockMdsClient = nil
        cancellables.removeAll()
    }

    // MARK: - Helpers

    @discardableResult
    private func awaitSingleAsync<T>(_ operation: @escaping () async throws -> T,
                                     timeout: TimeInterval = 2) throws -> T {
        var result: Result<T, Error>?
        let exp = XCTestExpectation(description: "awaitSingleAsync")
        Task {
            do { result = .success(try await operation()) }
            catch { result = .failure(error) }
            exp.fulfill()
        }
        wait(for: [exp], timeout: timeout)
        return try XCTUnwrap(result).get()
    }

    private func awaitVoidAsync(_ operation: @escaping () async throws -> Void,
                                timeout: TimeInterval = 2) throws {
        try awaitSingleAsync(operation, timeout: timeout)
    }

    private func awaitErrorAsync<T>(_ operation: @escaping () async throws -> T,
                                    timeout: TimeInterval = 2) -> Error? {
        var receivedError: Error?
        let exp = XCTestExpectation(description: "awaitErrorAsync")
        Task {
            do { _ = try await operation() }
            catch { receivedError = error }
            exp.fulfill()
        }
        wait(for: [exp], timeout: timeout)
        return receivedError
    }

    private func collectAllAsync<T>(_ stream: AsyncThrowingStream<T, Error>,
                                    timeout: TimeInterval = 2) throws -> [T] {
        var results: [T] = []
        let exp = XCTestExpectation(description: "collectAllAsync")
        Task {
            do {
                for try await v in stream { results.append(v) }
            } catch { }
            exp.fulfill()
        }
        wait(for: [exp], timeout: timeout)
        return results
    }

    // MARK: - getDeviceTelemetryConfiguration

    func test_getTelemetryConfiguration_returnsConnectionInfo() throws {
        // Arrange
        mockMdsClient.readSupportedFeaturesReturnValue = .success(0x03) // streaming + export
        mockMdsClient.readDeviceIdentifierReturnValue  = .success("POLAR-ABCDEF")
        mockMdsClient.readDataUriReturnValue           = .success("https://nrf-chunks.memfault.com/api/v0/chunks/")
        mockMdsClient.readAuthorizationReturnValue     = .success("my-project-key")

        // Act
        let config = try awaitSingleAsync { [self] in
            try await self.sut.getDeviceTelemetryConfiguration(telemetryType: PolarDeviceTelemetryType.memfault_mds, "ABC123")
        }

        // Assert
        XCTAssertNotNil(config)
        XCTAssertEqual(config?.deviceIdentifier, "POLAR-ABCDEF")
        
        // Pattern match to extract Memfault configuration
        guard case .memfault(let memfaultConfig) = config else {
            return XCTFail("Expected memfault configuration")
        }
        XCTAssertEqual(memfaultConfig.deviceIdentifier, "POLAR-ABCDEF")
        XCTAssertEqual(memfaultConfig.dataUri, "https://nrf-chunks.memfault.com/api/v0/chunks/")
        XCTAssertEqual(memfaultConfig.authorization, "my-project-key")
        XCTAssertTrue((memfaultConfig.supportedFeatures[0] & 0x01) != 0)
    }

    func test_getTelemetryConfiguration_streamingNotSupported_returnsInfoWithFalse() throws {
        mockMdsClient.readSupportedFeaturesReturnValue = .success(0x02) // only export, not streaming
        mockMdsClient.readDeviceIdentifierReturnValue  = .success("ID")
        mockMdsClient.readDataUriReturnValue           = .success("uri")
        mockMdsClient.readAuthorizationReturnValue     = .success("")

        let config = try awaitSingleAsync { [self] in
            try await self.sut.getDeviceTelemetryConfiguration(telemetryType: PolarDeviceTelemetryType.memfault_mds, "ABC123")
        }

        XCTAssertNotNil(config)
        guard case .memfault(let memfaultConfig) = config else {
            return XCTFail("Expected memfault configuration")
        }
        XCTAssertFalse((memfaultConfig.supportedFeatures[0] & 0x01) != 0)
    }

    func test_getTelemetryConfiguration_emptyAuth_returnsEmptyString() throws {
        mockMdsClient.readAuthorizationReturnValue = .success("")

        let config = try awaitSingleAsync { [self] in
            try await self.sut.getDeviceTelemetryConfiguration(telemetryType: PolarDeviceTelemetryType.memfault_mds, "ABC123")
        }

        guard case .memfault(let memfaultConfig) = config else {
            return XCTFail("Expected memfault configuration")
        }
        XCTAssertEqual(memfaultConfig.authorization, "")
    }

    // MARK: - getDeviceTelemetryConfiguration MDS API call guard

    private final class Gate: @unchecked Sendable {
        let entered: XCTestExpectation
        private let lock = NSLock()
        private var released = false
        private var waiters: [UUID: CheckedContinuation<Void, Error>] = [:]

        init(expectedEntries: Int = 1) {
            entered = XCTestExpectation(description: "gate entered")
            entered.expectedFulfillmentCount = expectedEntries
        }

        func release() {
            lock.lock()
            released = true
            let pending = Array(waiters.values)
            waiters.removeAll()
            lock.unlock()
            pending.forEach { $0.resume() }
        }

        /// Signals that the guarded operation was entered, then suspends until release() or task cancellation.
        func enterAndWait() async throws {
            entered.fulfill()
            let id = UUID()
            try await withTaskCancellationHandler {
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    lock.lock()
                    if released {
                        lock.unlock()
                        continuation.resume()
                    } else if Task.isCancelled {
                        lock.unlock()
                        continuation.resume(throwing: CancellationError())
                    } else {
                        waiters[id] = continuation
                        lock.unlock()
                    }
                }
            } onCancel: {
                lock.lock()
                let continuation = waiters.removeValue(forKey: id)
                lock.unlock()
                continuation?.resume(throwing: CancellationError())
            }
        }
    }

    func test_getTelemetryConfiguration_concurrentCallForSameDevice_isRejectedWhileFirstIsInProgress() async throws {
        let gate = Gate()
        mockMdsClient.readSupportedFeaturesGate = { try await gate.enterAndWait() }
        defer { gate.release() }
        let first = Task { [sut] in
            try await sut!.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, "ABC123")
        }
        await fulfillment(of: [gate.entered], timeout: 2)

        do {
            _ = try await sut.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, "ABC123")
            XCTFail("Expected concurrentApiCall")
        } catch let error as PolarErrors {
            guard case let .concurrentApiCall(description) = error else {
                gate.release()
                return XCTFail("Expected concurrentApiCall, got \(error)")
            }
            XCTAssertNotNil(description.range(of: "getDeviceTelemetryConfiguration"))
        }

        gate.release()
        let config = try await first.value
        XCTAssertNotNil(config, "the first call must not be disturbed by the rejected one")
    }

    func test_getTelemetryConfiguration_mdsReadSlotStillReserved_throwsPublicConcurrentApiCall() async throws {
        // Arrange
        mockMdsClient.readSupportedFeaturesReturnValue = .failure(BleMdsClientError.operationInProgress)

        // Act
        do {
            _ = try await sut.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, "ABC123")
            XCTFail("Expected concurrentApiCall")
        } catch let error as PolarErrors {
            // Assert
            guard case let .concurrentApiCall(description) = error else {
                return XCTFail("Expected concurrentApiCall, got \(error)")
            }
            XCTAssertNotNil(description.range(of: "getDeviceTelemetryConfiguration"))
        }
    }

    func test_getTelemetryConfiguration_guardIsReleasedAfterFailure_nextCallSucceeds() async throws {
        mockMdsClient.readSupportedFeaturesReturnValue = .failure(PolarErrors.deviceError(description: "boom"))
        _ = try? await sut.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, "ABC123")

        mockMdsClient.readSupportedFeaturesReturnValue = .success(0x01)
        let config = try await sut.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, "ABC123")
        XCTAssertNotNil(config)
    }

    func test_getTelemetryConfiguration_callForOtherDevice_isNotRejected() async throws {
        let gate = Gate(expectedEntries: 2)
        mockMdsClient.readSupportedFeaturesGate = { try await gate.enterAndWait() }
        defer { gate.release() }
        let first = Task { [sut] in
            try await sut!.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, "ABC123")
        }
        let other = Task { [sut] in
            try await sut!.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, "DEF456")
        }
        // Both calls must reach the mock gate, which proves that the calls overlap.
        await fulfillment(of: [gate.entered], timeout: 2)
        gate.release()

        do {
            _ = try await other.value
        } catch PolarErrors.concurrentApiCall {
            XCTFail("A different device must not share the guard")
        }
        _ = try await first.value
    }

    func test_getTelemetryConfiguration_cancelledCall_releasesGuard() async throws {
        let gate = Gate()
        mockMdsClient.readSupportedFeaturesGate = { try await gate.enterAndWait() }
        defer { gate.release() }
        let first = Task { [sut] in
            try await sut!.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, "ABC123")
        }
        await fulfillment(of: [gate.entered], timeout: 2)
        first.cancel()
        gate.release()
        _ = try? await first.value

        mockMdsClient.readSupportedFeaturesGate = nil
        let config = try await sut.getDeviceTelemetryConfiguration(telemetryType: .memfault_mds, "ABC123")
        XCTAssertNotNil(config)
    }

    func test_guardKeyResolver_connectedSession_mapsDeviceIdAndAddressToSameKey() {
        // Arrange
        let utils = MockMdsServiceClientUtils(listener: MockCBDeviceListenerImpl())
        utils.stubFetchedSession = mockMdsSession
        let resolve = PolarBleApiImpl.makeGuardKeyResolver(utils)
        let address = mockMdsSession.address.uuidString

        // Act
        let keyFromDeviceId = resolve("ABC123")
        let keyFromAddress = resolve(address)

        // Assert
        XCTAssertEqual(keyFromDeviceId, address)
        XCTAssertEqual(keyFromAddress, address)
    }

    func test_guardKeyResolver_noSession_usesUpperCasedIdentifier() {
        // Arrange
        let utils = MockMdsServiceClientUtils(listener: MockCBDeviceListenerImpl())
        let resolve = PolarBleApiImpl.makeGuardKeyResolver(utils)

        // Act
        let key = resolve("abc123")

        // Assert
        XCTAssertEqual(key, "ABC123")
    }

    func test_getTelemetryConfiguration_deviceNotFound_throws() {
        let utils = sut.serviceClientUtils as! MockMdsServiceClientUtils
        utils.stubError = PolarErrors.deviceNotFound

        let error = awaitErrorAsync { [self] in
            _ = try await self.sut.getDeviceTelemetryConfiguration(telemetryType: PolarDeviceTelemetryType.memfault_mds, "ABC123")
        }

        guard let e = error, case PolarErrors.deviceNotFound = e else {
            return XCTFail("Expected deviceNotFound, got \(String(describing: error))")
        }
    }

    func test_getTelemetryConfiguration_serviceNotFound_throws() {
        let utils = sut.serviceClientUtils as! MockMdsServiceClientUtils
        utils.stubError = PolarErrors.serviceNotFound

        let error = awaitErrorAsync { [self] in
            _ = try await self.sut.getDeviceTelemetryConfiguration(telemetryType: PolarDeviceTelemetryType.memfault_mds, "ABC123")
        }

        guard let e = error, case PolarErrors.serviceNotFound = e else {
            return XCTFail("Expected serviceNotFound, got \(String(describing: error))")
        }
    }

    func test_getTelemetryConfiguration_readFeaturesFails_throws() {
        mockMdsClient.readSupportedFeaturesReturnValue = .failure(NSError(domain: "test", code: 42))

        let error = awaitErrorAsync { [self] in
            _ = try await self.sut.getDeviceTelemetryConfiguration(telemetryType: PolarDeviceTelemetryType.memfault_mds, "ABC123")
        }

        XCTAssertNotNil(error)
    }

    // MARK: - startMemfaultStream

    func test_startMemfaultStream_yieldsChunks() async throws {
        let expectedChunks = [Data([0x01, 0x02]), Data([0x03, 0x04]), Data([0x05, 0x06])]
        mockMdsClient.stubbedChunks = expectedChunks

        let stream = try await sut.startTelemetry(telemetryType: .memfault_mds, "ABC123")
        var received: [Data] = []
        for try await event in stream {
            received.append(event.payload)
        }

        XCTAssertEqual(received, expectedChunks)
        XCTAssertTrue(mockMdsClient.enableNotificationCalled)
    }

    func test_startMemfaultStream_deviceNotFound_throws() async {
        let utils = sut.serviceClientUtils as! MockMdsServiceClientUtils
        utils.stubError = PolarErrors.deviceNotFound

        do {
            _ = try await sut.startTelemetry(telemetryType: .memfault_mds, "ABC123")
            XCTFail("Expected deviceNotFound to be thrown")
        } catch let e as PolarErrors {
            if case .deviceNotFound = e { /* expected */ }
            else { XCTFail("Expected deviceNotFound, got \(e)") }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func test_startMemfaultStream_enablesDataExportNotification() async throws {
        mockMdsClient.stubbedChunks = []

        let stream = try await sut.startTelemetry(telemetryType: .memfault_mds, "ABC123")
        for try await _ in stream { }

        XCTAssertTrue(mockMdsClient.enableNotificationCalled)
    }

    func test_startMemfaultStream_chunkStreamError_finishesWithError() async throws {
        mockMdsClient.stubbedChunks = [Data([0x01])]
        mockMdsClient.stubbedChunkError = NSError(domain: "BLE", code: 133)

        do {
            let stream = try await sut.startTelemetry(telemetryType: .memfault_mds, "ABC123")
            for try await _ in stream { }
            XCTFail("Expected stream to throw an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }

    // MARK: - stopMemfaultStream

    func test_stopMemfaultStream_disablesNotification() throws {
        try awaitVoidAsync { [self] in
            try await self.sut.stopTelemetry(telemetryType: PolarDeviceTelemetryType.memfault_mds, "ABC123")
        }

        XCTAssertTrue(mockMdsClient.disableNotificationCalled)
    }

    func test_stopMemfaultStream_deviceNotAvailable_throwsError() {
        let utils = sut.serviceClientUtils as! MockMdsServiceClientUtils
        utils.stubError = PolarErrors.deviceNotFound

        let error = awaitErrorAsync { [self] in
            try await self.sut.stopTelemetry(telemetryType: .memfault_mds, "ABC123")
        }

        XCTAssertNotNil(error, "stopMemfaultStream should throw when device is not available")
    }

    // MARK: - TelemetryConfiguration equatable

    func test_telemetryConfiguration_equality() {
        let memfaultConfigA = BleMdsClient.MemfaultTelemetryConfiguration(
            deviceIdentifier: "ID",
            dataUri: "uri",
            authorization: "auth",
            supportedFeatures: [0x01]
        )
        let memfaultConfigB = BleMdsClient.MemfaultTelemetryConfiguration(
            deviceIdentifier: "ID",
            dataUri: "uri",
            authorization: "auth",
            supportedFeatures: [0x01]
        )
        let memfaultConfigC = BleMdsClient.MemfaultTelemetryConfiguration(
            deviceIdentifier: "OTHER",
            dataUri: "uri",
            authorization: "auth",
            supportedFeatures: [0x01]
        )
        
        let a = DeviceTelemetryConfiguration.memfault(memfaultConfigA)
        let b = DeviceTelemetryConfiguration.memfault(memfaultConfigB)
        let c = DeviceTelemetryConfiguration.memfault(memfaultConfigC)

        XCTAssertEqual(a, b)
        XCTAssertNotEqual(a, c)
    }
}
