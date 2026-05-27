// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest
import Combine
import CoreBluetooth

@testable import PolarBleSdk

/// Unit tests for `PolarBleApiImpl`.
final class PolarBleApiImplTests: XCTestCase {

    // MARK: - Properties

    private let deviceId = "ABCDEF01"
    private var v2MockClient: MockBlePsFtpClient!
    private var v2MockSession: MockBleDeviceSession!
    private var v2Api: PolarBleApiImplWithMockSession!
    private var h10MockClient: MockBlePsFtpClient!
    private var h10MockSession: MockH10BleDeviceSession!
    private var h10Api: PolarBleApiImplWithMockH10Session!
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Set-up / Tear-down

    override func setUpWithError() throws {
        // Ensure device-capability JSON is loaded so fileSystemType()/isRecordingSupported()
        // return correct values for all device types used in these tests ("360", "h10", …).
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: ["h10": .h10FileSystem],
            defaultFileSystemType: .polarFileSystemV2,
            defaultRecordingSupported: false
        )
        let gatt = MockPolarGattServiceTransmitter()
        v2MockClient = MockBlePsFtpClient(gattServiceTransmitter: gatt)
        v2MockSession = MockBleDeviceSession(mockFtpClient: v2MockClient)
        v2Api = PolarBleApiImplWithMockSession(mockDeviceSession: v2MockSession)
        let h10Gatt = MockPolarGattServiceTransmitter()
        h10MockClient = MockBlePsFtpClient(gattServiceTransmitter: h10Gatt)
        h10MockSession = MockH10BleDeviceSession(mockFtpClient: h10MockClient)
        h10Api = PolarBleApiImplWithMockH10Session(mockDeviceSession: h10MockSession)
    }

    override func tearDownWithError() throws {
        v2MockClient = nil; v2MockSession = nil; v2Api = nil
        h10MockClient = nil; h10MockSession = nil; h10Api = nil
        cancellables.removeAll()
    }

    // MARK: - Helpers

    // MARK: - Combine-based helpers (for AnyPublisher-returning APIs)

    @discardableResult
    private func awaitSingle<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 2) throws -> T {
        var result: T?; var receivedError: Error?
        let exp = XCTestExpectation(description: "awaitSingle")
        publisher.first()
            .sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                  receiveValue: { result = $0 })
            .store(in: &cancellables)
        wait(for: [exp], timeout: timeout)
        if let e = receivedError { throw e }
        return try XCTUnwrap(result)
    }

    private func awaitCompletion(_ publisher: AnyPublisher<Never, Error>, timeout: TimeInterval = 2) throws {
        var receivedError: Error?
        let exp = XCTestExpectation(description: "awaitCompletion")
        publisher.sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                       receiveValue: { _ in })
            .store(in: &cancellables)
        wait(for: [exp], timeout: timeout)
        if let e = receivedError { throw e }
    }

    private func awaitError<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 2) -> Error? {
        var receivedError: Error?
        let exp = XCTestExpectation(description: "awaitError")
        publisher.sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                       receiveValue: { _ in })
            .store(in: &cancellables)
        wait(for: [exp], timeout: timeout)
        return receivedError
    }

    /// Collects all emitted values until the publisher completes or fails.
    private func collectAll<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 5) throws -> [T] {
        var results: [T] = []; var receivedError: Error?
        let exp = XCTestExpectation(description: "collectAll")
        publisher.collect()
            .sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                  receiveValue: { results = $0 })
            .store(in: &cancellables)
        wait(for: [exp], timeout: timeout)
        if let e = receivedError { throw e }
        return results
    }

    // MARK: - Async/await helpers (for async throws APIs)

    @discardableResult
    private func awaitSingleAsync<T>(_ operation: @escaping () async throws -> T, timeout: TimeInterval = 2) throws -> T {
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

    private func awaitVoidAsync(_ operation: @escaping () async throws -> Void, timeout: TimeInterval = 2) throws {
        try awaitSingleAsync(operation, timeout: timeout)
    }

    private func awaitErrorAsync<T>(_ operation: @escaping () async throws -> T, timeout: TimeInterval = 2) -> Error? {
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

    /// Collects all values from an AsyncThrowingStream until it finishes or throws.
    private func collectAllAsync<T>(_ stream: AsyncThrowingStream<T, Error>, timeout: TimeInterval = 5) throws -> [T] {
        var results: [T] = []
        try awaitSingleAsync({
            var r: [T] = []
            for try await value in stream { r.append(value) }
            return r
        }, timeout: timeout).forEach { results.append($0) }
        return results
    }

    // MARK: - getLocalTime

    func test_getLocalTime_polarFileSystemV2_success() throws {
        var proto = Protocol_PbPFtpSetLocalTimeParams()
        proto.date.year = 2024; proto.date.month = 6; proto.date.day = 15
        proto.time.hour = 10; proto.time.minute = 30; proto.time.seconds = 0; proto.tzOffset = 120
        v2MockClient.queryReturnValue = .success(try proto.serializedData())
        let date = try awaitSingleAsync { [self] in try await v2Api.getLocalTime(deviceId) }
        XCTAssertEqual(v2MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.getLocalTime.rawValue)
        let c = Calendar(identifier: .gregorian).dateComponents([.year,.month,.day], from: date)
        XCTAssertEqual(c.year, 2024); XCTAssertEqual(c.month, 6); XCTAssertEqual(c.day, 15)
    }

    func test_getLocalTime_h10FileSystem_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await h10Api.getLocalTime(deviceId) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    // MARK: - getLocalTimeWithZone

    func test_getLocalTimeWithZone_polarFileSystemV2_success() throws {
        var proto = Protocol_PbPFtpSetLocalTimeParams()
        proto.date.year = 2025; proto.date.month = 1; proto.date.day = 1
        proto.time.hour = 12; proto.time.minute = 0; proto.time.seconds = 0; proto.tzOffset = 60
        v2MockClient.queryReturnValue = .success(try proto.serializedData())
        let (_, tz) = try awaitSingleAsync { [self] in try await v2Api.getLocalTimeWithZone(deviceId) }
        XCTAssertEqual(tz.secondsFromGMT(), 3600)
    }

    func test_getLocalTimeWithZone_h10_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await h10Api.getLocalTimeWithZone(deviceId) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    // MARK: - setLocalTime

    func test_setLocalTime_h10FileSystem_sendsOneQuery() throws {
        h10MockClient.queryReturnValue = .success(Data())
        try awaitVoidAsync { [self] in try await h10Api.setLocalTime(deviceId, time: Date(), zone: TimeZone(secondsFromGMT: 0)!) }
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.setLocalTime.rawValue)
    }

    func test_setLocalTime_polarFileSystemV2_sendsTwoQueries() throws {
        for _ in 0..<2 { v2MockClient.queryReturnValues.append(.success(Data())) }
        try awaitVoidAsync { [self] in try await v2Api.setLocalTime(deviceId, time: Date(), zone: TimeZone(secondsFromGMT: 3600)!) }
        XCTAssertEqual(v2MockClient.queryCalls.count, 2)
        let ids = v2MockClient.queryCalls.map { $0.id }
        XCTAssertTrue(ids.contains(Protocol_PbPFtpQuery.setLocalTime.rawValue))
        XCTAssertTrue(ids.contains(Protocol_PbPFtpQuery.setSystemTime.rawValue))
    }

    // MARK: - getDiskSpace

    func test_getDiskSpace_success() throws {
        var proto = Protocol_PbPFtpDiskSpaceResult()
        proto.fragmentSize = 512; proto.totalFragments = 200; proto.freeFragments = 100
        v2MockClient.queryReturnValue = .success(try proto.serializedData())
        let d = try awaitSingleAsync { [self] in try await v2Api.getDiskSpace(deviceId) }
        XCTAssertEqual(d.totalSpace, 512 * 200); XCTAssertEqual(d.freeSpace, 512 * 100)
        XCTAssertEqual(v2MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.getDiskSpace.rawValue)
    }

    func test_getDiskSpace_queryError_propagatesError() {
        v2MockClient.queryReturnValue = .failure(NSError(domain: "test", code: 42))
        XCTAssertNotNil(awaitErrorAsync { [self] in try await v2Api.getDiskSpace(deviceId) })
    }

    // MARK: - startRecording

    func test_startRecording_emptyExerciseId_returnsInvalidArgument() {
        let error = awaitErrorAsync { [self] in try await h10Api.startRecording(deviceId, exerciseId: "", interval: .interval_1s, sampleType: .hr) }
        XCTAssertNotNil(error)
        if case PolarErrors.invalidArgument = error! { } else { XCTFail("Expected invalidArgument") }
    }

    func test_startRecording_exerciseIdTooLong_returnsInvalidArgument() {
        let error = awaitErrorAsync { [self] in try await h10Api.startRecording(deviceId, exerciseId: String(repeating: "a", count: 65), interval: .interval_1s, sampleType: .hr) }
        XCTAssertNotNil(error)
        if case PolarErrors.invalidArgument = error! { } else { XCTFail("Expected invalidArgument") }
    }

    func test_startRecording_h10Device_sendsRequestStartRecordingQuery() throws {
        h10MockClient.queryReturnValue = .success(Data())
        try awaitVoidAsync { [self] in try await h10Api.startRecording(deviceId, exerciseId: "myExercise", interval: .interval_1s, sampleType: .hr) }
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.requestStartRecording.rawValue)
    }

    func test_startRecording_nonRecordingDevice_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await v2Api.startRecording(deviceId, exerciseId: "myExercise", interval: .interval_1s, sampleType: .hr) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    // MARK: - stopRecording

    func test_stopRecording_h10Device_sendsRequestStopRecordingQuery() throws {
        h10MockClient.queryReturnValue = .success(Data())
        try awaitVoidAsync { [self] in try await h10Api.stopRecording(deviceId) }
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.requestStopRecording.rawValue)
    }

    func test_stopRecording_nonRecordingDevice_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await v2Api.stopRecording(deviceId) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    // MARK: - requestRecordingStatus

    func test_requestRecordingStatus_h10Device_returnsDecodedStatus() throws {
        var proto = Protocol_PbRequestRecordingStatusResult()
        proto.recordingOn = true; proto.sampleDataIdentifier = "exercise123"
        h10MockClient.queryReturnValue = .success(try proto.serializedData())
        let status = try awaitSingleAsync { [self] in try await h10Api.requestRecordingStatus(deviceId) }
        XCTAssertTrue(status.ongoing); XCTAssertEqual(status.entryId, "exercise123")
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.requestRecordingStatus.rawValue)
    }

    func test_requestRecordingStatus_nonRecordingDevice_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await v2Api.requestRecordingStatus(deviceId) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    // MARK: - searchForDevice helpers

    private var searchApi: MockSearchBleApiImpl!

    private func makeSession(name: String, deviceType: String = "360", deviceIdUntouched: String = "12345678",
                             rssi: Int32 = -70, connectable: Bool = true) -> MockSearchBleDeviceSession {
        let adv = MockSearchAdvertisementContent()
        adv.mockName = name; adv.mockPolarDeviceType = deviceType
        adv.mockPolarDeviceIdUntouched = deviceIdUntouched
        adv.mockMedianRssi = rssi; adv.mockIsConnectable = connectable
        return MockSearchBleDeviceSession(advertisementContent: adv)
    }

    // MARK: - searchForDevice tests

    func test_searchForDevice_noPrefix_emitsAllSessions() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "two"); exp.expectedFulfillmentCount = 2
        searchApi.searchForDevice()
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 12345678"))
        searchApi.searchSubject.send(makeSession(name: "Garmin Device ABCD"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 2)
    }

    func test_searchForDevice_defaultPolarPrefix_filtersNonPolarDevices() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "one"); exp.expectedFulfillmentCount = 1
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: "Polar")
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 AAAABBBB"))
        searchApi.searchSubject.send(makeSession(name: "Garmin Device CCCC"))
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(received.count, 1); XCTAssertEqual(received.first?.name, "Polar H10 AAAABBBB")
    }

    func test_searchForDevice_nonMatchingPrefix_emitsNothing() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: "Garmin")
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0) }).store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 12345678"))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(received.count, 0)
    }

    func test_searchForDevice_nilPrefix_emitsAllSessions() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "two"); exp.expectedFulfillmentCount = 2
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 12345678"))
        searchApi.searchSubject.send(makeSession(name: "Garmin Device ABCD"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 2)
    }

    func test_searchForDevice_distinct_deduplicatesSession() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "one")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        let session = makeSession(name: "Polar H10 AAAABBBB")
        searchApi.searchSubject.send(session); searchApi.searchSubject.send(session)
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 1)
    }

    func test_searchForDevice_mapsPolarDeviceInfoCorrectly() throws {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "received")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 AABBCCDD", deviceType: "h10",
                                                  deviceIdUntouched: "AABBCCDD", rssi: -55, connectable: true))
        wait(for: [exp], timeout: 2)
        let info = try XCTUnwrap(received.first)
        XCTAssertEqual(info.deviceId, "AABBCCDD"); XCTAssertEqual(info.rssi, -55)
        XCTAssertEqual(info.name, "Polar H10 AABBCCDD"); XCTAssertTrue(info.connectable)
    }

    func test_searchForDevice_hasSAGRFCFileSystem_trueForPolarFileSystemV2() throws {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "received")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar 360 AABBCCDD", deviceType: "360"))
        wait(for: [exp], timeout: 2)
        XCTAssertTrue(try XCTUnwrap(received.first).hasSAGRFCFileSystem)
    }

    func test_searchForDevice_hasSAGRFCFileSystem_falseForH10FileSystem() throws {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "received")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 AABBCCDD", deviceType: "h10"))
        wait(for: [exp], timeout: 2)
        XCTAssertFalse(try XCTUnwrap(received.first).hasSAGRFCFileSystem)
    }

    func test_searchForDevice_errorFromSource_propagatesError() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var receivedError: Error?
        let exp = XCTestExpectation(description: "error")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                  receiveValue: { _ in }).store(in: &cancellables)
        searchApi.searchSubject.send(completion: .failure(NSError(domain: "test", code: 99)))
        wait(for: [exp], timeout: 2); XCTAssertNotNil(receivedError)
    }

    func test_searchForDevice_completesWhenSubjectCompletes() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        searchApi.searchSubject.send(completion: .finished)
        wait(for: [exp], timeout: 2)
    }

    func test_searchForDevice_multipleMatchingSessions_emitsAll() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "three"); exp.expectedFulfillmentCount = 3
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: "Polar")
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 AAAA0001", deviceIdUntouched: "AAAA0001"))
        searchApi.searchSubject.send(makeSession(name: "Polar H9 AAAA0002",  deviceIdUntouched: "AAAA0002"))
        searchApi.searchSubject.send(makeSession(name: "Polar Sense AAAA0003", deviceIdUntouched: "AAAA0003"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 3)
    }

    // MARK: - startAutoConnectToDevice helpers

    private var autoConnectApi: MockAutoConnectBleApiImpl!

    private func makeAutoSession(rssi: Int32 = -70, connectable: Bool = true,
                                 deviceType: String = "h10", containsService: Bool = true) -> MockSearchBleDeviceSession {
        let adv = MockSearchAdvertisementContent()
        adv.mockMedianRssi = rssi; adv.mockIsConnectable = connectable
        adv.mockPolarDeviceType = deviceType; adv.mockContainsService = containsService
        return MockSearchBleDeviceSession(advertisementContent: adv)
    }

    // MARK: - startAutoConnectToDevice tests

    func test_startAutoConnectToDevice_matchingSession_completesAndOpensSession() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_prefixesAfterFirstMatch() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70))
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -60))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_rssiTooLow_notOpened() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        autoConnectApi.startAutoConnectToDevice(-60, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(autoConnectApi.openedSessions.count, 0)
    }

    func test_startAutoConnectToDevice_rssiExactlyAtThreshold_isMatched() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-70, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_notConnectable_notOpened() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, connectable: false))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(autoConnectApi.openedSessions.count, 0)
    }

    func test_startAutoConnectToDevice_polarDeviceTypeFilter_matchesCorrectType() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: "h10")
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, deviceType: "360"))
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, deviceType: "h10"))
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
        XCTAssertEqual(autoConnectApi.openedSessions.first?.advertisementContent.polarDeviceType, "h10")
    }

    func test_startAutoConnectToDevice_polarDeviceTypeNil_acceptsAnyType() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, deviceType: "360"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_serviceFilter_matchesSessionContainingService() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: CBUUID(string: "180D"), polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, containsService: true))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_serviceFilter_skipsSessionNotContainingService() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        autoConnectApi.startAutoConnectToDevice(-80, service: CBUUID(string: "180D"), polarDeviceType: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, containsService: false))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(autoConnectApi.openedSessions.count, 0)
    }

    func test_startAutoConnectToDevice_serviceNil_acceptsSessionWithAnyService() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, containsService: false))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_errorFromSource_propagatesError() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        var receivedError: Error?; let exp = XCTestExpectation(description: "error")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                  receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(completion: .failure(NSError(domain: "test", code: 7)))
        wait(for: [exp], timeout: 2); XCTAssertNotNil(receivedError)
    }

    func test_startAutoConnectToDevice_allFiltersPass_opensCorrectSession() throws {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: CBUUID(string: "FEEE"), polarDeviceType: "360")
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -60, connectable: true, deviceType: "360", containsService: true))
        wait(for: [exp], timeout: 2)
        let opened = try XCTUnwrap(autoConnectApi.openedSessions.first)
        XCTAssertEqual(opened.advertisementContent.polarDeviceType, "360")
        XCTAssertEqual(opened.advertisementContent.medianRssi, -60)
    }

    // MARK: - disconnectFromDevice helpers

    private var disconnectApi: MockDisconnectBleApiImpl!

    private func makeDisconnectSession(state: BleDeviceSession.DeviceSessionState) -> MockSearchBleDeviceSession {
        let session = MockSearchBleDeviceSession(advertisementContent: MockSearchAdvertisementContent())
        session.state = state; return session
    }

    // MARK: - disconnectFromDevice tests

    func test_disconnectFromDevice_sessionOpen_callsCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        let session = makeDisconnectSession(state: .sessionOpen)
        disconnectApi.disconnectServiceUtils.stubSession = session
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 1)
        XCTAssertTrue(disconnectApi.closeSessionDirectCalls.first === session)
    }

    func test_disconnectFromDevice_sessionOpening_callsCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = makeDisconnectSession(state: .sessionOpening)
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 1)
    }

    func test_disconnectFromDevice_sessionOpenPark_callsCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = makeDisconnectSession(state: .sessionOpenPark)
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 1)
    }

    func test_disconnectFromDevice_sessionClosed_doesNotCallCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = makeDisconnectSession(state: .sessionClosed)
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 0)
    }

    func test_disconnectFromDevice_sessionClosing_doesNotCallCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = makeDisconnectSession(state: .sessionClosing)
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 0)
    }

    func test_disconnectFromDevice_sessionNotFound_noCloseAndNoThrow() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = nil
        XCTAssertNoThrow(try disconnectApi.disconnectFromDevice(deviceId))
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 0)
    }

    func test_disconnectFromDevice_fetchSessionThrows_propagatesError() {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.shouldThrow = true
        XCTAssertThrowsError(try disconnectApi.disconnectFromDevice(deviceId)) { error in
            if case PolarErrors.invalidArgument = error { } else { XCTFail("Expected invalidArgument") }
        }
    }

    func test_disconnectFromDevice_removesConnectSubscription() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = nil
        var cancelled = false
        disconnectApi.connectSubscriptions[deviceId] = AnyCancellable { cancelled = true }
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertNil(disconnectApi.connectSubscriptions[deviceId]); XCTAssertTrue(cancelled)
    }

    func test_disconnectFromDevice_doesNotRemoveOtherSubscriptions() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = nil
        let other = "BBBBBBBB"
        disconnectApi.connectSubscriptions[other] = AnyCancellable { }
        disconnectApi.connectSubscriptions[deviceId] = AnyCancellable { }
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertNil(disconnectApi.connectSubscriptions[deviceId])
        XCTAssertNotNil(disconnectApi.connectSubscriptions[other])
    }

    func test_disconnectFromDevice_noSubscriptionForDevice_doesNotCrash() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = nil
        XCTAssertNoThrow(try disconnectApi.disconnectFromDevice(deviceId))
    }

    // MARK: - startListenForPolarHrBroadcasts helpers

    private var hrBroadcastApi: MockHrBroadcastBleApiImpl!

    private func makeHrSession(deviceIdUntouched: String = "AABBCCDD", deviceType: String = "h10",
                               hr: UInt8 = 72, batteryOk: Bool = false, rssi: Int32 = -65,
                               connectable: Bool = true, advFrameCounter: UInt8 = 1) -> MockSearchBleDeviceSession {
        let adv = MockSearchAdvertisementContent()
        adv.mockPolarDeviceIdUntouched = deviceIdUntouched; adv.mockPolarDeviceType = deviceType
        adv.mockIsConnectable = connectable; adv.mockName = "Polar H10 \(deviceIdUntouched)"
        adv.rssiFilter.processRssiValueUpdated(rssi)
        let byte0: UInt8 = (batteryOk ? 0x01 : 0x00) | ((advFrameCounter & 0x07) << 2)
        adv.polarHrAdvertisementData.processPolarManufacturerData(Data([byte0, 0x00, 0x00, hr]))
        return MockSearchBleDeviceSession(advertisementContent: adv)
    }

    // MARK: - startListenForPolarHrBroadcasts tests

    func test_startListenForPolarHrBroadcasts_nilIdentifiers_acceptsAllSessions() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        let exp = XCTestExpectation(description: "two"); exp.expectedFulfillmentCount = 2
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "AAAA0001"))
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "BBBB0002"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 2)
    }

    func test_startListenForPolarHrBroadcasts_identifierFilter_passesMatchingDevice() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        let exp = XCTestExpectation(description: "one")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(["AAAA0001"]) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "AAAA0001"))
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(received.count, 1); XCTAssertEqual(received.first?.deviceInfo.deviceId, "AAAA0001")
    }

    func test_startListenForPolarHrBroadcasts_identifierFilter_blocksNonMatchingDevice() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(["AAAA0001"]) { received.append(v) } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "BBBB0002"))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(received.count, 0)
    }

    func test_startListenForPolarHrBroadcasts_hrDataNotPresent_sessionFiltered() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v) } }
        let adv = MockSearchAdvertisementContent()
        hrBroadcastApi.searchSubject.send(MockSearchBleDeviceSession(advertisementContent: adv))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(received.count, 0)
    }

    func test_startListenForPolarHrBroadcasts_hrDataNotUpdated_sessionFiltered() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v) } }
        let adv = MockSearchAdvertisementContent()
        let data = Data([0x04, 0x00, 0x00, 72])
        adv.polarHrAdvertisementData.processPolarManufacturerData(data)
        adv.polarHrAdvertisementData.processPolarManufacturerData(data)
        hrBroadcastApi.searchSubject.send(MockSearchBleDeviceSession(advertisementContent: adv))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(received.count, 0)
    }

    func test_startListenForPolarHrBroadcasts_mapsHrValueCorrectly() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(hr: 95))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.first?.hr, 95)
    }

    func test_startListenForPolarHrBroadcasts_mapsBatteryStatusCorrectly() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(batteryOk: true))
        wait(for: [exp], timeout: 2); XCTAssertTrue(try XCTUnwrap(received.first).batteryStatus)
    }

    func test_startListenForPolarHrBroadcasts_mapsDeviceInfoCorrectly() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "AABBCCDD", hr: 70, rssi: -55))
        wait(for: [exp], timeout: 2)
        let info = try XCTUnwrap(received.first)
        XCTAssertEqual(info.deviceInfo.deviceId, "AABBCCDD"); XCTAssertEqual(info.deviceInfo.rssi, -55)
        XCTAssertTrue(info.deviceInfo.connectable); XCTAssertEqual(info.hr, 70)
    }

    func test_startListenForPolarHrBroadcasts_hasSAGRFCFileSystem_trueForPolarFileSystemV2() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceType: "360"))
        wait(for: [exp], timeout: 2); XCTAssertTrue(try XCTUnwrap(received.first).deviceInfo.hasSAGRFCFileSystem)
    }

    func test_startListenForPolarHrBroadcasts_hasSAGRFCFileSystem_falseForH10() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceType: "h10"))
        wait(for: [exp], timeout: 2); XCTAssertFalse(try XCTUnwrap(received.first).deviceInfo.hasSAGRFCFileSystem)
    }

    func test_startListenForPolarHrBroadcasts_multipleIdentifiers_passesAllMatching() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        let exp = XCTestExpectation(description: "two"); exp.expectedFulfillmentCount = 2
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(["AAAA0001", "BBBB0002"]) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "AAAA0001", advFrameCounter: 1))
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "BBBB0002", advFrameCounter: 1))
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "CCCC0003", advFrameCounter: 1))
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(received.count, 2)
        let ids = received.map { $0.deviceInfo.deviceId }
        XCTAssertTrue(ids.contains("AAAA0001")); XCTAssertTrue(ids.contains("BBBB0002"))
    }

    func test_startListenForPolarHrBroadcasts_errorFromSource_propagatesError() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var receivedError: Error?; let exp = XCTestExpectation(description: "error")
        Task {
            do { for try await _ in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) {} }
            catch { receivedError = error; exp.fulfill() }
        }
        hrBroadcastApi.searchSubject.send(completion: .failure(NSError(domain: "test", code: 5)))
        wait(for: [exp], timeout: 2); XCTAssertNotNil(receivedError)
    }

    func test_startListenForPolarHrBroadcasts_completesWhenSourceCompletes() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        Task {
            for try await _ in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) {}
            exp.fulfill()
        }
        hrBroadcastApi.searchSubject.send(completion: .finished)
        wait(for: [exp], timeout: 2)
    }

    // MARK: - PMD helpers (shared by requestStreamSettings / requestFullStreamSettings /
    //         requestOfflineRecordingSettings / requestFullOfflineRecordingSettings /
    //         getAvailableOfflineRecordingDataTypes / getOfflineRecordingStatus)

    private var pmdApi: MockPmdBleApiImpl!
    private var mockPmdSession: MockPmdBleDeviceSession!
    private var mockPmdClient: MockBlePmdClient!

    private func makeSuccessPmdSetting() throws -> PmdSetting {
        let data = Data([0x00, 0x01, 0x82, 0x00, 0x01, 0x01, 0x10, 0x00])
        return try PmdSetting(data)
    }

    private func setUpPmdApi() {
        let gatt = MockPolarGattServiceTransmitter()
        mockPmdClient = MockBlePmdClient(gattServiceTransmitter: gatt)
        mockPmdSession = MockPmdBleDeviceSession(mockPmdClient: mockPmdClient)
        pmdApi = MockPmdBleApiImpl(mockPmdSession: mockPmdSession)
    }

    // MARK: - requestStreamSettings tests

    func test_requestStreamSettings_ppi_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestStreamSettings(deviceId, feature: .ppi) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestStreamSettings_hr_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestStreamSettings(deviceId, feature: .hr) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestStreamSettings_ecg_queriesEcgType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .ecg)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .online)
    }

    func test_requestStreamSettings_acc_queriesAccType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .acc) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .acc)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .online)
    }

    func test_requestStreamSettings_ppg_queriesPpgType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ppg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .ppg)
    }

    func test_requestStreamSettings_magnetometer_queriesMgnType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .magnetometer) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .mgn)
    }

    func test_requestStreamSettings_gyro_queriesGyroType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .gyro) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .gyro)
    }

    func test_requestStreamSettings_temperature_queriesTemperatureType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .temperature) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .temperature)
    }

    func test_requestStreamSettings_skinTemperature_queriesSkinTemperatureType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .skinTemperature) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .skinTemperature)
    }

    func test_requestStreamSettings_pressure_queriesPressureType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .pressure) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .pressure)
    }

    func test_requestStreamSettings_mapsSettingsCorrectly() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        let result = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(result.settings[.sampleRate], [130]); XCTAssertEqual(result.settings[.resolution], [16])
    }

    func test_requestStreamSettings_queryError_wrappedAsDeviceError() {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .failure(NSError(domain: "pmd", code: 42))
        let e = awaitErrorAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ecg) }
        XCTAssertNotNil(e); if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError") }
    }

    func test_requestStreamSettings_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ecg) })
    }

    // MARK: - requestFullStreamSettings tests

    func test_requestFullStreamSettings_ppi_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .ppi) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_hr_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .hr) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_temperature_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .temperature) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_pressure_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .pressure) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_skinTemperature_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .skinTemperature) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_ecg_queriesEcgTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .ecg)
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.recordingType, .online)
    }

    func test_requestFullStreamSettings_acc_queriesAccTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .acc) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .acc)
    }

    func test_requestFullStreamSettings_ppg_queriesPpgTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ppg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .ppg)
    }

    func test_requestFullStreamSettings_magnetometer_queriesMgnTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .magnetometer) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .mgn)
    }

    func test_requestFullStreamSettings_gyro_queriesGyroTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .gyro) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .gyro)
    }

    func test_requestFullStreamSettings_mapsSettingsCorrectly() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        let result = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(result.settings[.sampleRate], [130]); XCTAssertEqual(result.settings[.resolution], [16])
    }

    func test_requestFullStreamSettings_doesNotCallQuerySettings() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.count, 0)
    }

    func test_requestFullStreamSettings_queryError_wrappedAsDeviceError() {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .failure(NSError(domain: "pmd.full", code: 99))
        let e = awaitErrorAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) }
        XCTAssertNotNil(e); if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError") }
    }

    func test_requestFullStreamSettings_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) })
    }

    // MARK: - requestOfflineRecordingSettings tests

    func test_requestOfflineRecordingSettings_ppi_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestOfflineRecordingSettings(deviceId, feature: .ppi) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestOfflineRecordingSettings_hr_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestOfflineRecordingSettings(deviceId, feature: .hr) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestOfflineRecordingSettings_ecg_queriesEcgTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .ecg)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_acc_queriesAccTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .acc) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .acc)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_ppg_queriesPpgTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ppg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .ppg)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_magnetometer_queriesMgnTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .magnetometer) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .mgn)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_gyro_queriesGyroTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .gyro) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .gyro)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_temperature_queriesTemperatureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .temperature) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .temperature)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_skinTemperature_queriesSkinTemperatureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .skinTemperature) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .skinTemperature)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_pressure_queriesPressureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .pressure) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .pressure)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_mapsSettingsCorrectly() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        let result = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(result.settings[.sampleRate], [130]); XCTAssertEqual(result.settings[.resolution], [16])
    }

    func test_requestOfflineRecordingSettings_usesOfflineNotOnlineRecordingType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        for feature: PolarDeviceDataType in [.ecg, .acc, .ppg, .magnetometer, .gyro, .temperature, .skinTemperature, .pressure] {
            mockPmdClient.querySettingsCalls.removeAll()
            _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: feature) }
            XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline, "\(feature) should use .offline")
        }
    }

    func test_requestOfflineRecordingSettings_doesNotCallQueryFullSettings() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.count, 0)
    }

    func test_requestOfflineRecordingSettings_queryError_wrappedAsDeviceError() {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .failure(NSError(domain: "pmd.offline", code: 7))
        let e = awaitErrorAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertNotNil(e); if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError") }
    }

    func test_requestOfflineRecordingSettings_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) })
    }

    // MARK: - requestFullOfflineRecordingSettings tests

    func test_requestFullOfflineRecordingSettings_ppi_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullOfflineRecordingSettings(deviceId, feature: .ppi) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullOfflineRecordingSettings_hr_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullOfflineRecordingSettings(deviceId, feature: .hr) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullOfflineRecordingSettings_pressure_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullOfflineRecordingSettings(deviceId, feature: .pressure) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullOfflineRecordingSettings_ecg_queriesEcgTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .ecg)
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.recordingType, .offline)
    }

    func test_requestFullOfflineRecordingSettings_acc_queriesAccTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .acc) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .acc)
    }

    func test_requestFullOfflineRecordingSettings_ppg_queriesPpgTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ppg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .ppg)
    }

    func test_requestFullOfflineRecordingSettings_magnetometer_queriesMgnTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .magnetometer) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .mgn)
    }

    func test_requestFullOfflineRecordingSettings_gyro_queriesGyroTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .gyro) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .gyro)
    }

    func test_requestFullOfflineRecordingSettings_temperature_queriesTemperatureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .temperature) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .temperature)
    }

    func test_requestFullOfflineRecordingSettings_skinTemperature_queriesSkinTemperatureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .skinTemperature) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .skinTemperature)
    }

    func test_requestFullOfflineRecordingSettings_mapsSettingsCorrectly() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        let result = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(result.settings[.sampleRate], [130]); XCTAssertEqual(result.settings[.resolution], [16])
    }

    func test_requestFullOfflineRecordingSettings_usesOfflineNotOnlineRecordingType() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        for feature: PolarDeviceDataType in [.ecg, .acc, .ppg, .magnetometer, .gyro, .temperature, .skinTemperature] {
            mockPmdClient.queryFullSettingsCalls.removeAll()
            _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: feature) }
            XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.recordingType, .offline, "\(feature) should use .offline")
        }
    }

    func test_requestFullOfflineRecordingSettings_doesNotCallQuerySettings() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.count, 0)
    }

    func test_requestFullOfflineRecordingSettings_queryError_wrappedAsDeviceError() {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .failure(NSError(domain: "pmd.fullOffline", code: 3))
        let e = awaitErrorAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertNotNil(e); if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError") }
    }

    func test_requestFullOfflineRecordingSettings_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) })
    }

    // MARK: - getAvailableOfflineRecordingDataTypes tests

    func test_getAvailableOfflineRecordingDataTypes_emptyFeatureSet_returnsEmptySet() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set<PmdMeasurementType>())
        XCTAssertTrue(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }.isEmpty)
    }

    func test_getAvailableOfflineRecordingDataTypes_ecg_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.ecg]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.ecg])
    }

    func test_getAvailableOfflineRecordingDataTypes_acc_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.acc]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.acc])
    }

    func test_getAvailableOfflineRecordingDataTypes_ppg_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.ppg]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.ppg])
    }

    func test_getAvailableOfflineRecordingDataTypes_ppi_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.ppi]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.ppi])
    }

    func test_getAvailableOfflineRecordingDataTypes_gyro_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.gyro]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.gyro])
    }

    func test_getAvailableOfflineRecordingDataTypes_mgn_mappedToMagnetometer() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.mgn]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.magnetometer])
    }

    func test_getAvailableOfflineRecordingDataTypes_offlineHr_mappedToHr() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.offline_hr]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.hr])
    }

    func test_getAvailableOfflineRecordingDataTypes_temperature_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.temperature]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.temperature])
    }

    func test_getAvailableOfflineRecordingDataTypes_skinTemperature_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.skinTemperature]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.skinTemperature])
    }

    func test_getAvailableOfflineRecordingDataTypes_multipleTypes_allMapped() throws {
        setUpPmdApi()
        let pmdTypes: Set<PmdMeasurementType> = [.ecg, .acc, .ppg, .gyro, .mgn, .offline_hr, .temperature, .skinTemperature]
        mockPmdClient.readFeatureReturnValue = .success(pmdTypes)
        let result = try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }
        XCTAssertEqual(result, [.ecg, .acc, .ppg, .gyro, .magnetometer, .hr, .temperature, .skinTemperature])
    }

    func test_getAvailableOfflineRecordingDataTypes_passesCheckConnectionTrue() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set<PmdMeasurementType>())
        _ = try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }
        XCTAssertEqual(mockPmdClient.readFeatureCalls.first, true)
    }

    func test_getAvailableOfflineRecordingDataTypes_readFeatureError_propagatesError() {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .failure(NSError(domain: "pmd.readFeature", code: 5))
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) })
    }

    func test_getAvailableOfflineRecordingDataTypes_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) })
    }

    // MARK: - getOfflineRecordingStatus tests

    func test_getOfflineRecordingStatus_emptyStatus_returnsEmptyDictionary() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([])
        XCTAssertTrue(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }.isEmpty)
    }

    func test_getOfflineRecordingStatus_offlineMeasurementActive_returnsTrueForFeature() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.ecg, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.ecg], true)
    }

    func test_getOfflineRecordingStatus_onlineOfflineMeasurementActive_returnsTrueForFeature() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.acc, PmdActiveMeasurement.online_offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.acc], true)
    }

    func test_getOfflineRecordingStatus_noMeasurementActive_returnsFalseForFeature() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.ppg, PmdActiveMeasurement.no_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.ppg], false)
    }

    func test_getOfflineRecordingStatus_onlineMeasurementActive_returnsFalseForFeature() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.gyro, PmdActiveMeasurement.online_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.gyro], false)
    }

    func test_getOfflineRecordingStatus_mgn_mappedToMagnetometer() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.mgn, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.magnetometer], true)
    }

    func test_getOfflineRecordingStatus_offlineHr_mappedToHr() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.offline_hr, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.hr], true)
    }

    func test_getOfflineRecordingStatus_temperature_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.temperature, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.temperature], true)
    }

    func test_getOfflineRecordingStatus_pressure_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.pressure, PmdActiveMeasurement.no_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.pressure], false)
    }

    func test_getOfflineRecordingStatus_multipleFeatures_allMappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([
            (PmdMeasurementType.ecg,  PmdActiveMeasurement.offline_measurement_active),
            (PmdMeasurementType.acc,  PmdActiveMeasurement.no_measurement_active),
            (PmdMeasurementType.ppg,  PmdActiveMeasurement.online_offline_measurement_active),
            (PmdMeasurementType.gyro, PmdActiveMeasurement.online_measurement_active)
        ])
        let result = try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }
        XCTAssertEqual(result[.ecg], true); XCTAssertEqual(result[.acc], false)
        XCTAssertEqual(result[.ppg], true); XCTAssertEqual(result[.gyro], false)
    }

    func test_getOfflineRecordingStatus_unmappablePmdType_propagatesError() {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.unknown_type, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) })
    }

    func test_getOfflineRecordingStatus_readMeasurementStatusError_propagatesError() {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .failure(NSError(domain: "pmd.status", code: 8))
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) })
    }

    func test_getOfflineRecordingStatus_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) })
    }

    func test_getOfflineRecordingStatus_callsReadMeasurementStatus() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([])
        _ = try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }
        XCTAssertEqual(mockPmdClient.readMeasurementStatusCalls, 1)
    }

    // MARK: - listOfflineRecordings helpers

    private func makePmdFilesTxtData(entries: [(size: Int, path: String)]) -> Data {
        (entries.map { "\($0.size) \($0.path)" }.joined(separator: "\n")).data(using: .utf8)!
    }

    private func makeDirectoryProtoData(entries: [(name: String, size: UInt64)]) throws -> Data {
        var dir = Protocol_PbPFtpDirectory()
        dir.entries = entries.map { e in
            var entry = Protocol_PbPFtpEntry(); entry.name = e.name; entry.size = e.size; return entry
        }
        return try dir.serializedData()
    }

    private func makeRequestClosure(_ responses: [String: () throws -> Data]) -> (Data) async throws -> Data {
        return { headerData in
            guard let op = try? Protocol_PbPFtpOperation(serializedBytes: headerData) else {
                throw NSError(domain: "test.proto", code: 0)
            }
            if let builder = responses[op.path] {
                return try builder()
            }
            throw NSError(domain: "test.unrouted", code: 0,
                          userInfo: [NSLocalizedDescriptionKey: "Unrouted: \(op.path)"])
        }
    }

    // MARK: - listOfflineRecordings tests

    func test_listOfflineRecordings_sessionNotReady_propagatesError() {
        XCTAssertNotNil(awaitErrorAsync { [self] in
            for try await _ in MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession).listOfflineRecordings(self.deviceId) {}
        })
    }

    func test_listOfflineRecordings_v2Path_singleEntry_emitsEntry() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 1024, path: "/U/0/20240615/R/103000/ACC.REC")]) }
        ])
        let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 1); XCTAssertEqual(entries.first?.type, .acc)
    }

    func test_listOfflineRecordings_v2Path_multipleEntries_allEmitted() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [
                (size: 1024, path: "/U/0/20240615/R/103000/ACC.REC"),
                (size: 2048, path: "/U/0/20240615/R/103000/GYRO.REC")
            ]) }
        ])
        let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 2)
        let types = Set(entries.map { $0.type })
        XCTAssertTrue(types.contains(.acc)); XCTAssertTrue(types.contains(.gyro))
    }

    func test_listOfflineRecordings_v2Path_zeroSizeEntry_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 0, path: "/U/0/20240615/R/103000/ACC.REC")]) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v2Path_invalidPathTooFewComponents_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 1024, path: "/U/0/20240615/ACC.REC")]) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v2Path_unknownFileType_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 1024, path: "/U/0/20240615/R/103000/UNKNOWN.REC")]) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v2Path_entryTypesMappedCorrectly() throws {
        let cases: [(file: String, expected: PolarDeviceDataType)] = [
            ("ACC.REC", .acc), ("GYRO.REC", .gyro), ("MAGNETOMETER.REC", .magnetometer),
            ("PPG.REC", .ppg), ("PPI.REC", .ppi), ("HR.REC", .hr),
            ("TEMP.REC", .temperature), ("SKINTEMP.REC", .skinTemperature)
        ]
        for (fileName, expectedType) in cases {
            v2MockClient.requestReturnValueClosure = nil; cancellables.removeAll()
            v2MockClient.requestReturnValueClosure = makeRequestClosure([
                "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 512, path: "/U/0/20240615/R/103000/\(fileName)")]) }
            ])
            let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
            XCTAssertEqual(entries.count, 1, "\(fileName) should produce one entry")
            XCTAssertEqual(entries.first?.type, expectedType, "\(fileName) → \(expectedType)")
        }
    }

    func test_listOfflineRecordings_v2Path_dateAndSizeParsedCorrectly() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 4096, path: "/U/0/20240615/R/103000/ACC.REC")]) }
        ])
        let entry = try XCTUnwrap(collectAllAsync(v2Api.listOfflineRecordings(deviceId)).first)
        XCTAssertEqual(entry.size, 4096)
        let comps = Calendar(identifier: .gregorian).dateComponents(in: TimeZone(secondsFromGMT: 0)!, from: entry.date)
        XCTAssertEqual(comps.year, 2024); XCTAssertEqual(comps.month, 6); XCTAssertEqual(comps.day, 15)
    }

    func test_listOfflineRecordings_v2Path_emptyPmdFileTxt_fallsBackToV1WithNoEntries() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { Data() },
            "/U/0/":         { try self.makeDirectoryProtoData(entries: []) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v1Path_validDirectoryStructure_emitsEntry() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT":             { Data() },
            "/U/0/":                     { try self.makeDirectoryProtoData(entries: [("20240615/", 0)]) },
            "/U/0/20240615/":            { try self.makeDirectoryProtoData(entries: [("R/", 0)]) },
            "/U/0/20240615/R/":          { try self.makeDirectoryProtoData(entries: [("103000/", 0)]) },
            "/U/0/20240615/R/103000/":   { try self.makeDirectoryProtoData(entries: [("ACC.REC", 2048)]) }
        ])
        let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 1); XCTAssertEqual(entries.first?.type, .acc)
        XCTAssertEqual(entries.first?.size, 2048)
    }

    func test_listOfflineRecordings_v1Path_zeroSizeFile_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT":             { Data() },
            "/U/0/":                     { try self.makeDirectoryProtoData(entries: [("20240615/", 0)]) },
            "/U/0/20240615/":            { try self.makeDirectoryProtoData(entries: [("R/", 0)]) },
            "/U/0/20240615/R/":          { try self.makeDirectoryProtoData(entries: [("103000/", 0)]) },
            "/U/0/20240615/R/103000/":   { try self.makeDirectoryProtoData(entries: [("ACC.REC", 0)]) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    // MARK: - removeExercise

    func test_removeExercise_polarFileSystemV2_returnsPolarBleSdkInternalException() {
        let error = awaitErrorAsync { [self] in try await v2Api.removeExercise(self.deviceId, entry: PolarExerciseEntry(path: "/some/path", date: Date(), entryId: "id1")) }
        XCTAssertNotNil(error)
        if case PolarErrors.polarBleSdkInternalException = error! { } else { XCTFail("Expected polarBleSdkInternalException") }
    }

    func test_removeExercise_h10_sendsRemoveRequest() throws {
        // Arrange: request returns empty data (success)
        h10MockClient.requestReturnValue = .success(Data())

        // Act
        let entry = PolarExerciseEntry(path: "/EXERCISE/E0000001.BPB", date: Date(), entryId: "id1")
        try awaitVoidAsync { [self] in try await h10Api.removeExercise(self.deviceId, entry: entry) }

        // Assert
        XCTAssertEqual(h10MockClient.requestCalls.count, 1)
    }
}
