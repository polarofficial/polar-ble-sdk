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
    private var pfcMockClient: MockBlePfcClient!
    private var pfcMockSession: MockPfcBleDeviceSession!
    private var pfcApi: MockPfcBleApiImpl!
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
        let pfcGatt = MockPolarGattServiceTransmitter()
        pfcMockClient = MockBlePfcClient(gattServiceTransmitter: pfcGatt)
        pfcMockSession = MockPfcBleDeviceSession(mockPfcClient: pfcMockClient)
        pfcApi = MockPfcBleApiImpl(mockPfcSession: pfcMockSession)
    }

    override func tearDownWithError() throws {
        v2MockClient = nil; v2MockSession = nil; v2Api = nil
        h10MockClient = nil; h10MockSession = nil; h10Api = nil
        pfcMockClient = nil; pfcMockSession = nil; pfcApi = nil
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
    
    private func awaitFirstValue<T>(_ stream: AsyncThrowingStream<T, Error>, timeout: TimeInterval = 2) throws -> T {
        try awaitSingleAsync({
            var iterator = stream.makeAsyncIterator()
            guard let value = try await iterator.next() else {
                throw NSError(domain: "PolarBleApiImplTests.awaitFirstValue", code: 0)
            }
            return value
        }, timeout: timeout)
    }

    private func awaitStreamError<T>(_ stream: AsyncThrowingStream<T, Error>, timeout: TimeInterval = 2) -> Error? {
        awaitErrorAsync({
            for try await _ in stream {}
        }, timeout: timeout)
    }

    private func readAll(from stream: InputStream) -> Data {
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 256)
        while stream.hasBytesAvailable {
            let read = stream.read(&buffer, maxLength: buffer.count)
            if read <= 0 { break }
            data.append(buffer, count: read)
        }
        return data
    }

    private func makeUserDeviceSettingsProto(
        deviceLocation: PbDeviceLocation = .deviceLocationWristLeft,
        usbMode: Data_PbUsbConnectionSettings.PbUsbConnectionMode = .off,
        telemetryEnabled: Bool = false,
        autosEnabled: Bool = false,
        atdState: Data_PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState = .off,
        atdSensitivity: UInt32 = 10,
        minimumDuration: UInt32 = 300
    ) throws -> Data {
        var proto = Data_PbUserDeviceSettings()
        proto.generalSettings.deviceLocation = deviceLocation
        proto.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date())
        proto.usbConnectionSettings.mode = usbMode
        proto.telemetrySettings.telemetryEnabled = telemetryEnabled
        proto.automaticMeasurementSettings.automaticOhrMeasurement.state = autosEnabled ? .alwaysOn : .off
        proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.state = atdState
        proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity = atdSensitivity
        proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds = minimumDuration
        return try proto.serializedData()
    }

    private func makeDynamicApi() -> (MockDynamicBleApiImpl, MockDynamicServiceClientUtils) {
        let utils = MockDynamicServiceClientUtils(listener: MockCBDeviceListenerImpl())
        return (MockDynamicBleApiImpl(serviceUtils: utils), utils)
    }

    private func makeDate(_ year: Int, _ month: Int, _ day: Int) -> Date {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = day
        comps.hour = 0
        comps.minute = 0
        comps.second = 0
        comps.timeZone = TimeZone(secondsFromGMT: 0)
        return Calendar(identifier: .gregorian).date(from: comps)!
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

    func test_startAutoConnectToDevice_sessionInOpenParkState_isOpened() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        let session = makeAutoSession(rssi: -70)
        session.state = .sessionOpenPark
        autoConnectApi.searchSubject.send(session)
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(autoConnectApi.openedSessions.count, 1,
                       "A .sessionOpenPark session must be opened by startAutoConnectToDevice")
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

    // MARK: - setSensorInitiatedSecurityMode

    func test_setSensorInitiatedSecurityMode_enable_sendsCommandWithValue1() throws {
        // Arrange
        pfcMockClient.commandReturnValue = .success(Pfc.PfcResponse())

        // Act
        try awaitVoidAsync { [self] in
            try await pfcApi.setSensorInitiatedSecurityMode(identifier: deviceId, enable: true)
        }

        // Assert – exactly one command was sent with opcode 14 (pfcConfigureSensorInitiatedSecurityMode) and enable value 1
        XCTAssertEqual(pfcMockClient.commandCalls.count, 1)
        XCTAssertEqual(pfcMockClient.commandCalls.first?.command, .pfcConfigureSensorInitiatedSecurityMode)
        XCTAssertEqual(pfcMockClient.commandCalls.first?.value, [1])
    }

    func test_setSensorInitiatedSecurityMode_disable_sendsCommandWithValue0() throws {
        // Arrange
        pfcMockClient.commandReturnValue = .success(Pfc.PfcResponse())

        // Act
        try awaitVoidAsync { [self] in
            try await pfcApi.setSensorInitiatedSecurityMode(identifier: deviceId, enable: false)
        }

        // Assert – command sent with disable value 0
        XCTAssertEqual(pfcMockClient.commandCalls.count, 1)
        XCTAssertEqual(pfcMockClient.commandCalls.first?.command, .pfcConfigureSensorInitiatedSecurityMode)
        XCTAssertEqual(pfcMockClient.commandCalls.first?.value, [0])
    }

    func test_setSensorInitiatedSecurityMode_nonSuccessResponse_throwsOperationNotSupported() {
        // Arrange – device returns an error status (errorNotSupported = 2)
        let errorResponse = Pfc.PfcResponse(data: Data([0xF0, 0x0E, UInt8(Pfc.PfcResponse.PfcResponseCodes.errorNotSupported.rawValue)]))
        pfcMockClient.commandReturnValue = .success(errorResponse)

        // Act
        let error = awaitErrorAsync { [self] in
            try await pfcApi.setSensorInitiatedSecurityMode(identifier: deviceId, enable: true)
        }

        // Assert
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else {
            XCTFail("Expected PolarErrors.operationNotSupported, got \(String(describing: error))")
        }
    }

    func test_setSensorInitiatedSecurityMode_sessionNotReady_propagatesError() {
        // Arrange – make sessionPfcClientReady throw a device-not-found error
        pfcApi.pfcServiceUtils.stubError = PolarErrors.deviceNotFound

        // Act
        let error = awaitErrorAsync { [self] in
            try await pfcApi.setSensorInitiatedSecurityMode(identifier: deviceId, enable: true)
        }

        // Assert – the error is forwarded and no command was dispatched
        XCTAssertNotNil(error)
        XCTAssertTrue(pfcMockClient.commandCalls.isEmpty)
    }

    func test_setSensorInitiatedSecurityMode_pfcServiceNotFound_throwsServiceNotFound() {
        // Arrange – replace the session with one that has no PFC GATT client
        let noServiceSession = MockBleDeviceSession(mockFtpClient: v2MockClient)
        pfcApi.pfcServiceUtils.stubSession = noServiceSession

        // Act
        let error = awaitErrorAsync { [self] in
            try await pfcApi.setSensorInitiatedSecurityMode(identifier: deviceId, enable: true)
        }

        // Assert
        XCTAssertNotNil(error)
        if case PolarErrors.serviceNotFound = error! { } else {
            XCTFail("Expected PolarErrors.serviceNotFound, got \(String(describing: error))")
        }
    }

    // MARK: - getSensorInitiatedSecurityMode

    func test_getSensorInitiatedSecurityMode_payloadEnabled_returnsTrue() throws {
        // Arrange – device returns payload byte 0x01 (enabled)
        // Data layout: [responseCode, opCode(0x0F), status(success=1), payload(0x01)]
        let enabledResponse = Pfc.PfcResponse(data: Data([0xF0, 0x0F, 0x01, 0x01]))
        pfcMockClient.commandReturnValue = .success(enabledResponse)

        // Act
        let result = try awaitSingleAsync { [self] in
            try await pfcApi.getSensorInitiatedSecurityMode(identifier: deviceId)
        }

        // Assert
        XCTAssertTrue(result)
        XCTAssertEqual(pfcMockClient.commandCalls.count, 1)
        XCTAssertEqual(pfcMockClient.commandCalls.first?.command, .pfcRequestSensorInitiatedSecurityMode)
        XCTAssertEqual(pfcMockClient.commandCalls.first?.value, [0])
    }

    func test_getSensorInitiatedSecurityMode_payloadDisabled_returnsFalse() throws {
        // Arrange – device returns payload byte 0x00 (disabled)
        let disabledResponse = Pfc.PfcResponse(data: Data([0xF0, 0x0F, 0x01, 0x00]))
        pfcMockClient.commandReturnValue = .success(disabledResponse)

        // Act
        let result = try awaitSingleAsync { [self] in
            try await pfcApi.getSensorInitiatedSecurityMode(identifier: deviceId)
        }

        // Assert
        XCTAssertFalse(result)
    }

    func test_getSensorInitiatedSecurityMode_emptyPayload_returnsFalse() throws {
        // Arrange – device returns no extra payload bytes (only responseCode, opCode, status)
        let emptyPayloadResponse = Pfc.PfcResponse(data: Data([0xF0, 0x0F, 0x01]))
        pfcMockClient.commandReturnValue = .success(emptyPayloadResponse)

        // Act
        let result = try awaitSingleAsync { [self] in
            try await pfcApi.getSensorInitiatedSecurityMode(identifier: deviceId)
        }

        // Assert – empty payload is treated as disabled
        XCTAssertFalse(result)
    }

    func test_getSensorInitiatedSecurityMode_sendsCorrectCommand() throws {
        // Arrange
        pfcMockClient.commandReturnValue = .success(Pfc.PfcResponse(data: Data([0xF0, 0x0F, 0x01])))

        // Act
        _ = try awaitSingleAsync { [self] in
            try await pfcApi.getSensorInitiatedSecurityMode(identifier: deviceId)
        }

        // Assert – exactly one pfcRequestSensorInitiatedSecurityMode command with value 0
        XCTAssertEqual(pfcMockClient.commandCalls.count, 1)
        XCTAssertEqual(pfcMockClient.commandCalls.first?.command, .pfcRequestSensorInitiatedSecurityMode)
        XCTAssertEqual(pfcMockClient.commandCalls.first?.value, [0])
    }

    func test_getSensorInitiatedSecurityMode_sessionNotReady_propagatesError() {
        // Arrange – make waitPfcClientReady throw a device-not-found error
        pfcApi.pfcServiceUtils.stubError = PolarErrors.deviceNotFound

        // Act
        let error = awaitErrorAsync { [self] in
            try await pfcApi.getSensorInitiatedSecurityMode(identifier: deviceId)
        }

        // Assert – the error is forwarded and no command was dispatched
        XCTAssertNotNil(error)
        XCTAssertTrue(pfcMockClient.commandCalls.isEmpty)
    }

    func test_getSensorInitiatedSecurityMode_pfcServiceNotFound_throwsServiceNotFound() {
        // Arrange – replace the session with one that has no PFC GATT client
        let noServiceSession = MockBleDeviceSession(mockFtpClient: v2MockClient)
        pfcApi.pfcServiceUtils.stubSession = noServiceSession

        // Act
        let error = awaitErrorAsync { [self] in
            try await pfcApi.getSensorInitiatedSecurityMode(identifier: deviceId)
        }

        // Assert
        XCTAssertNotNil(error)
        if case PolarErrors.serviceNotFound = error! { } else {
            XCTFail("Expected PolarErrors.serviceNotFound, got \(String(describing: error))")
        }
    }

    func test_getSensorInitiatedSecurityMode_commandThrows_propagatesError() {
        // Arrange – command returns a transport error
        let transportError = BleGattException.gattTransportNotAvailable
        pfcMockClient.commandReturnValue = .failure(transportError)

        // Act
        let error = awaitErrorAsync { [self] in
            try await pfcApi.getSensorInitiatedSecurityMode(identifier: deviceId)
        }

        // Assert – the BLE error is propagated
        XCTAssertNotNil(error)
        if case BleGattException.gattTransportNotAvailable = error! { } else {
            XCTFail("Expected gattTransportNotAvailable, got \(String(describing: error))")
        }
    }

    // MARK: - setHibernateMode

    func test_setHibernateMode_sendsResetNotificationWithHibernateTrueSleepTrueDoFactoryDefaultsFalse() throws {
        // Arrange
        // Act
        try awaitVoidAsync { [self] in try await v2Api.setHibernateMode(deviceId) }

        // Assert – exactly one RESET notification was sent
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(
            v2MockClient.sendNotificationCalls.first?.notification,
            Protocol_PbPFtpHostToDevNotification.reset.rawValue,
            "Expected RESET notification"
        )

        let paramsData = try XCTUnwrap(v2MockClient.sendNotificationCalls.first?.parameters) as Data
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: paramsData)
        XCTAssertTrue(params.hibernate, "hibernate should be true")
        XCTAssertTrue(params.sleep, "sleep should be true to initiate low-power mode")
        XCTAssertFalse(params.doFactoryDefaults, "doFactoryDefaults should be false")
    }

    func test_setHibernateMode_doesNotTriggerFactoryDefaultsAndSetsHibernateFlag() throws {
        // Arrange
        // Act
        try awaitVoidAsync { [self] in try await v2Api.setHibernateMode(deviceId) }

        // Assert – hibernate is true and factory defaults are not triggered
        let paramsData = try XCTUnwrap(v2MockClient.sendNotificationCalls.first?.parameters) as Data
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: paramsData)
        XCTAssertTrue(params.hibernate, "Hibernate flag must be set to true")
        XCTAssertFalse(params.doFactoryDefaults, "Hibernate mode must not trigger factory defaults")
    }

    func test_setHibernateMode_sendsExactlyOneNotification() throws {
        // Arrange
        // Act
        try awaitVoidAsync { [self] in try await v2Api.setHibernateMode(deviceId) }

        // Assert
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1, "Expected exactly one notification to be sent")
    }

    func test_setHibernateMode_notificationError_propagatesError() {
        // Arrange – make sendNotification throw
        v2MockClient.sendNotificationError = NSError(domain: "ble", code: 42)

        // Act
        let error = awaitErrorAsync { [self] in try await v2Api.setHibernateMode(deviceId) }

        // Assert
        XCTAssertNotNil(error)
    }

    // MARK: - Low-level API (PolarBleLowLevelApi)

    // MARK: readFile

    func test_readFile_success_returnsData() throws {
        // Arrange
        v2MockClient.requestReturnValue = .success(Data([0x01, 0x02, 0x03]))

        // Act
        let result = try awaitSingleAsync { [self] in try await v2Api.readFile(identifier: deviceId, filePath: "/U/0/TEST.BPB") }

        // Assert
        XCTAssertEqual(result, Data([0x01, 0x02, 0x03]))
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let op = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(op.command, .get)
        XCTAssertEqual(op.path, "/U/0/TEST.BPB")
    }

    func test_readFile_failure_propagatesError() {
        // Arrange
        v2MockClient.requestReturnValue = .failure(PolarErrors.deviceNotConnected)

        // Act & Assert
        XCTAssertNotNil(awaitErrorAsync { [self] in try await v2Api.readFile(identifier: deviceId, filePath: "/U/0/TEST.BPB") })
    }

    // MARK: writeFile

    func test_writeFile_success_sendsCorrectPutCommand() throws {
        // Arrange
        let payload = Data([0xAA, 0xBB])
        v2MockClient.writeReturnValue = AsyncThrowingStream { $0.yield(0); $0.finish() }

        // Act
        try awaitVoidAsync { [self] in try await v2Api.writeFile(identifier: deviceId, filePath: "/U/0/OUT.BPB", fileData: payload) }

        // Assert
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let op = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(op.command, .put)
        XCTAssertEqual(op.path, "/U/0/OUT.BPB")
    }

    func test_writeFile_failure_propagatesError() {
        // Arrange
        v2MockClient.writeReturnValue = AsyncThrowingStream { $0.finish(throwing: PolarErrors.deviceNotConnected) }

        // Act & Assert
        XCTAssertNotNil(awaitErrorAsync { [self] in
            try await v2Api.writeFile(identifier: deviceId, filePath: "/U/0/OUT.BPB", fileData: Data([0x01]))
        })
    }

    // MARK: deleteFileOrDirectory

    func test_deleteFileOrDirectory_success_sendsCorrectRemoveCommand() throws {
        // Arrange
        v2MockClient.requestReturnValue = .success(Data())

        // Act
        try awaitVoidAsync { [self] in
            try await v2Api.deleteFileOrDirectory(identifier: deviceId, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
        }

        // Assert
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let op = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(op.command, .remove)
        XCTAssertEqual(op.path, "/U/0/20260101/DSUM/DSUM.BPB")
    }

    func test_deleteFileOrDirectory_failure_propagatesError() {
        // Arrange
        v2MockClient.requestReturnValue = .failure(PolarErrors.deviceNotConnected)

        // Act & Assert
        XCTAssertNotNil(awaitErrorAsync { [self] in
            try await v2Api.deleteFileOrDirectory(identifier: deviceId, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
        })
    }

    private func makePmdFilesTxtData(entries: [(size: Int, path: String)]) -> Data {
        (entries.map { "\($0.size) \($0.path)" }.joined(separator: "\n")).data(using: .utf8)!
    }

    private func makeDirectoryProtoData(entries: [(name: String, size: UInt64)]) throws -> Data {
        var dir = Protocol_PbPFtpDirectory()
        dir.entries = entries.map { e in
            var entry = Protocol_PbPFtpEntry()
            entry.name = e.name
            entry.size = e.size
            return entry
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
            throw NSError(domain: "test.unrouted", code: 0, userInfo: [NSLocalizedDescriptionKey: "Unrouted: \(op.path)"])
        }
    }

    private func makeCommandAwareRequestClosure(_ responses: [String: [() throws -> Data]]) -> (Data) async throws -> Data {
        var remaining = responses
        return { headerData in
            guard let op = try? Protocol_PbPFtpOperation(serializedBytes: headerData) else {
                throw NSError(domain: "test.proto", code: 0)
            }
            let key = "\(op.command.rawValue)|\(op.path)"
            if var builders = remaining[key], !builders.isEmpty {
                let builder = builders.removeFirst()
                remaining[key] = builders
                return try builder()
            }
            throw NSError(domain: "test.unrouted", code: 0, userInfo: [NSLocalizedDescriptionKey: "Unrouted: \(key)"])
        }
    }

    // MARK: getFileList

    func test_getFileList_recurseDeepTrue_returnsFiles() throws {
        // Arrange
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/U/0/":                    { try self.makeDirectoryProtoData(entries: [("20260101/", 0)]) },
            "/U/0/20260101/":           { try self.makeDirectoryProtoData(entries: [("DSUM/", 0)]) },
            "/U/0/20260101/DSUM/":      { try self.makeDirectoryProtoData(entries: [("DSUM.BPB", 1024)]) }
        ])

        // Act
        let result = try awaitSingleAsync { [self] in
            try await v2Api.getFileList(identifier: deviceId, directoryPath: "/U/0/", recurseDeep: true)
        }

        // Assert
        XCTAssertEqual(result, ["/U/0/20260101/DSUM/DSUM.BPB"])
    }

    func test_getFileList_recurseDeepFalse_returnsOnlyMatchingEntries() throws {
        // Arrange – only a file with an extension passes the internal condition
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/U/0/": { try self.makeDirectoryProtoData(entries: [("AUTOS000.BPB", 512)]) }
        ])

        // Act
        let result = try awaitSingleAsync { [self] in
            try await v2Api.getFileList(identifier: deviceId, directoryPath: "/U/0/", recurseDeep: false)
        }

        // Assert
        XCTAssertEqual(result, ["/U/0/AUTOS000.BPB"])
    }

    func test_getFileList_failure_propagatesError() {
        // Arrange
        v2MockClient.requestReturnValue = .failure(PolarErrors.deviceNotConnected)

        // Act & Assert
        XCTAssertNotNil(awaitErrorAsync { [self] in
            try await v2Api.getFileList(identifier: deviceId, directoryPath: "/U/0/", recurseDeep: false)
        })
    }

    // MARK: createFolder

    func test_createFolder_success_sendsCorrectPutCommand() throws {
        // Arrange
        v2MockClient.writeReturnValue = AsyncThrowingStream { $0.yield(0); $0.finish() }

        // Act
        try awaitVoidAsync { [self] in
            try await v2Api.createFolder(identifier: deviceId, folderPath: "/U/0/20240622/ACT/")
        }

        // Assert
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let op = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(op.command, .put)
        XCTAssertEqual(op.path, "/U/0/20240622/ACT/")
    }

    func test_createFolder_normalizesPathWhenMissingTrailingSlash() throws {
        // Arrange
        v2MockClient.writeReturnValue = AsyncThrowingStream { $0.yield(0); $0.finish() }

        // Act
        try awaitVoidAsync { [self] in
            try await v2Api.createFolder(identifier: deviceId, folderPath: "/U/0/20240622/ACT")
        }

        // Assert – path must be normalized to end with '/'
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let op = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(op.path, "/U/0/20240622/ACT/")
    }

    func test_createFolder_sendsEmptyPayload() throws {
        // Arrange
        v2MockClient.writeReturnValue = AsyncThrowingStream { $0.yield(0); $0.finish() }

        // Act
        try awaitVoidAsync { [self] in
            try await v2Api.createFolder(identifier: deviceId, folderPath: "/U/0/20240622/SLP/")
        }

        // Assert – empty payload signals folder creation to the device
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let stream = v2MockClient.writeCalls[0].data
        stream.open()
        var buffer = [UInt8](repeating: 0, count: 64)
        let bytesRead = stream.read(&buffer, maxLength: 64)
        stream.close()
        XCTAssertEqual(bytesRead, 0, "Payload must be empty for folder creation")
    }

    func test_createFolder_failure_writeFails_propagatesError() {
        // Arrange
        v2MockClient.writeReturnValue = AsyncThrowingStream { $0.finish(throwing: PolarErrors.deviceNotConnected) }

        // Act & Assert
        XCTAssertNotNil(awaitErrorAsync { [self] in
            try await v2Api.createFolder(identifier: deviceId, folderPath: "/U/0/20240622/ACT/")
        })
    }

    func test_createFolder_failure_pftpResponseError_propagatesError() {
        // Arrange
        v2MockClient.writeReturnValue = AsyncThrowingStream {
            $0.finish(throwing: BlePsFtpException.responseError(errorCode: 201))
        }

        // Act & Assert
        XCTAssertNotNil(awaitErrorAsync { [self] in
            try await v2Api.createFolder(identifier: deviceId, folderPath: "/U/0/20240622/ACT/")
        })
    }

       func test_cleanup_polarFilter_isFeatureReady_and_connectToDeviceWithoutSession_behaveAsExpected() throws {
           v2Api.cleanup()
           XCTAssertTrue(v2Api.listener.allSessions().isEmpty)
           XCTAssertFalse(v2Api.isFeatureReady(deviceId, feature: .feature_hr))

           v2Api.polarFilter(true)
           XCTAssertNotNil(v2Api.listener.scanPreFilter)
           v2Api.polarFilter(false)
           XCTAssertNil(v2Api.listener.scanPreFilter)

           let (api, utils) = makeDynamicApi()
           utils.fetchSessionHandler = { _ in nil }
           try api.connectToDevice(self.deviceId)
           XCTAssertNotNil(api.connectSubscriptions[self.deviceId])
           api.connectSubscriptions[self.deviceId]?.cancel()
       }

       func test_checkFirmwareUpdate_and_updateFirmware_emitFailureStatuses_whenNoFtpClientExists() throws {
           let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: MockNoFtpClientBleDeviceSession())

           let checkStatuses = try collectAllAsync(noFtpApi.checkFirmwareUpdate(deviceId))
           if case .checkFwUpdateFailed(let details) = try XCTUnwrap(checkStatuses.first) {
               XCTAssertTrue(details.contains("No BlePsFtpClient available"))
           } else {
               XCTFail("Expected checkFwUpdateFailed")
           }

           let updateStatuses = try collectAllAsync(noFtpApi.updateFirmware(deviceId))
           if case .fwUpdateFailed(let details) = try XCTUnwrap(updateStatuses.first) {
               XCTAssertTrue(details.contains("No BlePsFtpClient available"))
           } else {
               XCTFail("Expected fwUpdateFailed")
           }

           let urlStatuses = try collectAllAsync(noFtpApi.updateFirmware(deviceId, fromFirmwareURL: URL(fileURLWithPath: "/tmp/fw.zip")))
           if case .fwUpdateFailed(let details) = try XCTUnwrap(urlStatuses.first) {
               XCTAssertTrue(details.contains("No BlePsFtpClient available"))
           } else {
               XCTFail("Expected fwUpdateFailed")
           }
       }

       func test_setLedConfig_writesExpectedBytes() throws {
           try awaitVoidAsync { [self] in
               try await v2Api.setLedConfig(deviceId, ledConfig: LedConfig(sdkModeLedEnabled: true, ppiModeLedEnabled: false))
           }

           let writeCall = try XCTUnwrap(v2MockClient.writeCalls.first)
           let header = try Protocol_PbPFtpOperation(serializedBytes: writeCall.header as Data)
           XCTAssertEqual(header.command, .put)
           XCTAssertEqual(header.path, LedConfig.LED_CONFIG_FILENAME)
           XCTAssertEqual(readAll(from: writeCall.data), Data([LedConfig.LED_ANIMATION_ENABLE_BYTE, LedConfig.LED_ANIMATION_DISABLE_BYTE]))
       }

       func test_resetAndSyncHelpers_sendExpectedNotifications() throws {
           try awaitVoidAsync { [self] in try await v2Api.doFactoryReset(deviceId, preservePairingInformation: true) }
           var params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertTrue(params.otaFwupdate)

           try awaitVoidAsync { [self] in try await v2Api.doRestart(deviceId) }
           params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertFalse(params.doFactoryDefaults)
           XCTAssertFalse(params.otaFwupdate)

           try awaitVoidAsync { [self] in try await v2Api.setWarehouseSleep(deviceId) }
           params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertTrue(params.sleep)

           try awaitVoidAsync { [self] in try await v2Api.turnDeviceOff(deviceId) }
           params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertTrue(params.sleep)
           XCTAssertFalse(params.doFactoryDefaults)

           try awaitVoidAsync { [self] in try await v2Api.sendInitializationAndStartSyncNotifications(identifier: deviceId) }
           XCTAssertEqual(v2MockClient.queryCalls.last?.id, Protocol_PbPFtpQuery.requestSynchronization.rawValue)
           XCTAssertEqual(v2MockClient.sendNotificationCalls.suffix(2).map(\.notification), [
               Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue,
               Protocol_PbPFtpHostToDevNotification.startSync.rawValue,
           ])
       }

       func test_exerciseControlQueries_and_statusObservation_work() throws {
           var status = Protocol_PbPftpGetExerciseStatusResult()
           status.exerciseState = .exerciseStateRunning
           status.sportIdentifier.value = UInt64(PolarExerciseSession.SportProfile.running.rawValue)
           let statusData = try status.serializedData()
           v2MockClient.queryReturnValues = [.success(Data()), .success(Data()), .success(Data()), .success(Data()), .success(statusData)]

           try awaitVoidAsync { [self] in try await v2Api.startExercise(identifier: deviceId, profile: .running) }
           try awaitVoidAsync { [self] in try await v2Api.pauseExercise(identifier: deviceId) }
           try awaitVoidAsync { [self] in try await v2Api.resumeExercise(identifier: deviceId) }
           try awaitVoidAsync { [self] in try await v2Api.stopExercise(identifier: deviceId) }
           let result = try awaitSingleAsync { [self] in try await v2Api.getExerciseStatus(identifier: deviceId) }

           XCTAssertEqual(v2MockClient.queryCalls.map(\.id), [
               Protocol_PbPFtpQuery.startExercise.rawValue,
               Protocol_PbPFtpQuery.pauseExercise.rawValue,
               Protocol_PbPFtpQuery.resumeExercise.rawValue,
               Protocol_PbPFtpQuery.stopExercise.rawValue,
               Protocol_PbPFtpQuery.getExerciseStatus.rawValue,
           ])
           XCTAssertEqual(result.status, .inProgress)
           XCTAssertEqual(result.sportProfile, .running)

           v2MockClient.receiveNotificationCalls = [
               (notification: Protocol_PbPFtpDevToHostNotification.exerciseStatus.rawValue, parameters: [statusData], compressed: false)
           ]
           let notifications = try collectAllAsync(v2Api.observeExerciseStatus(identifier: deviceId))
           XCTAssertEqual(notifications.first?.status, .inProgress)
       }

    private func makeActivityAndTrainingErrorContext() -> (
        api: MockDynamicBleApiImpl,
        fromDate: Date,
        toDate: Date,
        reference: PolarTrainingSessionReference,
        exerciseEntry: PolarExerciseEntry
    ) {
        let (api, utils) = makeDynamicApi()
        utils.ftpError = PolarErrors.deviceNotConnected
        let fromDate = makeDate(2024, 6, 1)
        let toDate = makeDate(2024, 6, 2)
        let reference = PolarTrainingSessionReference(date: fromDate, path: "/U/0/20240601/TSESS.BPB", trainingDataTypes: [.trainingSessionSummary], exercises: [])
        let exerciseEntry = PolarExerciseEntry(path: "/U/0/EX/SAMPLES.BPB", date: Date(), entryId: "x")
        return (api, fromDate, toDate, reference, exerciseEntry)
    }

    func test_activityAndTrainingApis_fetchExercise_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.fetchExercise(self.deviceId, entry: context.exerciseEntry) })
    }

    func test_activityAndTrainingApis_listExercisesStream_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitStreamError(context.api.listExercises(self.deviceId)))
    }

    func test_activityAndTrainingApis_getSteps_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.getSteps(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate) })
    }

    func test_activityAndTrainingApis_getDistance_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.getDistance(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate) })
    }

    func test_activityAndTrainingApis_get247HrSamples_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.get247HrSamples(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate) })
    }

    func test_activityAndTrainingApis_get247PPiSamples_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.get247PPiSamples(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate) })
    }

    func test_activityAndTrainingApis_getNightlyRecharge_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.getNightlyRecharge(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate) })
    }

    func test_activityAndTrainingApis_getCalories_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.getCalories(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate, caloriesType: .activity) })
    }

    func test_activityAndTrainingApis_getActivitySampleData_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.getActivitySampleData(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate) })
    }

    func test_activityAndTrainingApis_getDailySummaryData_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.getDailySummaryData(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate) })
    }

    func test_activityAndTrainingApis_getActiveTime_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.getActiveTime(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate) })
    }

    func test_activityAndTrainingApis_getTrainingSessionReferences_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.getTrainingSessionReferences(identifier: self.deviceId, fromDate: context.fromDate, toDate: context.toDate) })
    }

    func test_activityAndTrainingApis_getTrainingSession_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.getTrainingSession(identifier: self.deviceId, trainingSessionReference: context.reference) })
    }

    func test_activityAndTrainingApis_getTrainingSessionWithProgress_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync {
            try await context.api.getTrainingSessionWithProgress(identifier: self.deviceId, trainingSessionReference: context.reference, progressHandler: { _ in })
        })
    }
    
    func test_activityAndTrainingApis_deleteTrainingSession_returnsError_whenFtpLookupFails() {
        let context = makeActivityAndTrainingErrorContext()
        XCTAssertNotNil(awaitErrorAsync { try await context.api.deleteTrainingSession(identifier: self.deviceId, reference: context.reference) as Void })
        
        func test_userSettingsReadAndMutationApis_useExpectedPathsAndValues() throws {
            v2MockClient.requestReturnValue = .success(try makeUserDeviceSettingsProto(deviceLocation: .deviceLocationWristRight, usbMode: .on, telemetryEnabled: true, autosEnabled: true, atdState: .on, atdSensitivity: 44, minimumDuration: 600))
            let result = try awaitSingleAsync { [self] in try await v2Api.getPolarUserDeviceSettings(identifier: deviceId) }
            XCTAssertEqual(result.deviceLocation, .WRIST_RIGHT)
            XCTAssertEqual(result.usbConnectionMode, .ON)
            XCTAssertEqual(result.telemetryEnabled, true)
            XCTAssertEqual(result.autosFilesEnabled, true)
            
            let settings = PolarUserDeviceSettings()
            settings.deviceLocation = .CHEST
            settings.usbConnectionMode = .OFF
            settings.telemetryEnabled = false
            settings.autosFilesEnabled = false
            try awaitVoidAsync { [self] in try await v2Api.setPolarUserDeviceSettings(deviceId, polarUserDeviceSettings: settings) }
            
            var written = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: try XCTUnwrap(v2MockClient.writeCalls.last?.data)))
            XCTAssertEqual(written.generalSettings.deviceLocation, .deviceLocationChest)
            XCTAssertEqual(written.telemetrySettings.telemetryEnabled, false)
            XCTAssertEqual(written.automaticMeasurementSettings.automaticOhrMeasurement.state, .off)
            
            v2MockClient.writeCalls.removeAll()
            v2MockClient.requestReturnValue = .success(try makeUserDeviceSettingsProto())
            try awaitVoidAsync { [self] in try await v2Api.setUsbConnectionMode(deviceId, enabled: true) }
            written = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: try XCTUnwrap(v2MockClient.writeCalls.last?.data)))
            XCTAssertEqual(written.usbConnectionSettings.mode, .on)
            
            v2MockClient.writeCalls.removeAll()
            v2MockClient.requestReturnValue = .success(try makeUserDeviceSettingsProto())
            try awaitVoidAsync { [self] in try await v2Api.setAutomaticTrainingDetectionSettings(deviceId, mode: true, sensitivity: 77, minimumDuration: 900) }
            written = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: try XCTUnwrap(v2MockClient.writeCalls.last?.data)))
            XCTAssertEqual(written.automaticMeasurementSettings.automaticTrainingDetectionSettings.state, .on)
            XCTAssertEqual(written.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity, 77)
            XCTAssertEqual(written.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds, 900)
            
            v2MockClient.writeCalls.removeAll()
            v2MockClient.requestReturnValue = .success(try makeUserDeviceSettingsProto())
            try awaitVoidAsync { [self] in try await v2Api.setTelemetryEnabled(deviceId, enabled: true) }
            written = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: try XCTUnwrap(v2MockClient.writeCalls.last?.data)))
            XCTAssertTrue(written.telemetrySettings.telemetryEnabled)
            
            v2MockClient.writeCalls.removeAll()
            v2MockClient.requestReturnValue = .success(try makeUserDeviceSettingsProto(autosEnabled: true))
            try awaitVoidAsync { [self] in try await v2Api.setAutomaticOHRMeasurementEnabled(deviceId, enabled: false) }
            written = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: try XCTUnwrap(v2MockClient.writeCalls.last?.data)))
            XCTAssertEqual(written.automaticMeasurementSettings.automaticOhrMeasurement.state, .off)
        }
        
        func test_setUserDeviceLocation_setDaylightSavingTime_doFirstTimeUse_isFtuDone_and_getUserPhysicalConfiguration_coverErrorAndNilPaths() throws {
            XCTAssertNotNil(awaitErrorAsync { [self] in try await v2Api.setUserDeviceLocation(deviceId, location: 999) })
            
            let (api, utils) = makeDynamicApi()
            utils.ftpError = PolarErrors.deviceNotConnected
            XCTAssertNotNil(awaitErrorAsync { try await api.setDaylightSavingTime(self.deviceId) })
            
            let config = PolarFirstTimeUseConfig(
                gender: .male,
                birthDate: makeDate(1990, 1, 1),
                height: 180,
                weight: 80,
                maxHeartRate: 190,
                vo2Max: 50,
                restingHeartRate: 55,
                trainingBackground: .regular,
                deviceTime: "not-an-iso-date",
                typicalDay: .mostlyMoving,
                sleepGoalMinutes: 480
            )
            XCTAssertNotNil(awaitErrorAsync { [self] in try await v2Api.doFirstTimeUse(deviceId, ftuConfig: config) })
            
            var userId = Data_PbUserIdentifier()
            userId.masterIdentifier = 12345
            v2MockClient.requestReturnValue = .success(try userId.serializedData())
            XCTAssertTrue(try awaitSingleAsync { [self] in try await v2Api.isFtuDone(deviceId) })
            
            v2MockClient.requestReturnValue = .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))
            let physicalConfig = try awaitSingleAsync { [self] in try await v2Api.getUserPhysicalConfiguration(deviceId) }
            XCTAssertNil(physicalConfig)
        }
        
        func test_deleteDataApis_useFileUtils_results() throws {
            let (api, utils) = makeDynamicApi()
            let fileUtils = MockPolarFileUtils(listener: utils.listener!, serviceClientUtils: utils)
            api.fileUtils = fileUtils
            fileUtils.listedFiles = ["/SDLOGS/ABC1.SLG", "/U/0/20240601/", "ABC123TRC.BIN"]
            
            try awaitVoidAsync { [self] in try await api.deleteStoredDeviceData(deviceId, dataType: .SDLOGS, until: makeDate(2024, 6, 1)) }
            XCTAssertTrue(fileUtils.removeSingleFileCalls.contains(where: { $0.filePath == "/SDLOGS/ABC1.SLG" }))
            
            fileUtils.removeSingleFileCalls.removeAll()
            try awaitVoidAsync { [self] in try await api.deleteDeviceDateFolders(deviceId, fromDate: makeDate(2024, 6, 1), toDate: makeDate(2024, 6, 1)) }
            XCTAssertTrue(fileUtils.removeSingleFileCalls.contains(where: { $0.filePath == "/U/0/20240601/" }))
            
            fileUtils.removeSingleFileCalls.removeAll()
            try awaitVoidAsync { [self] in try await api.deleteTelemetryData(deviceId) }
            XCTAssertTrue(fileUtils.removeSingleFileCalls.contains(where: { $0.filePath == "ABC123TRC.BIN" }))
        }
        
        func test_offlineRecordingControl_onlineAvailability_streaming_sdk_battery_and_waitHelpers_coverAdditionalApis() throws {
            let pmdTransport = MockPolarGattServiceTransmitter()
            let pmdClient = MockBlePmdClient(gattServiceTransmitter: pmdTransport)
            let pmdSession = MockPmdBleDeviceSession(mockPmdClient: pmdClient)
            let (api, utils) = makeDynamicApi()
            utils.pmdSession = pmdSession
            pmdClient.readMeasurementStatusReturnValue = .success([(.acc, .no_measurement_active)])
            pmdClient.getOfflineRecordingTriggerStatusReturnValue = .success(PmdOfflineTrigger(triggerMode: .systemStart, triggers: [.acc: (.enabled, nil)]))
            pmdClient.sdkModeEnabledReturnValue = .success(.enabled)
            try awaitVoidAsync { [self] in try await api.startOfflineRecording(deviceId, feature: .acc, settings: nil, secret: nil) }
            try awaitVoidAsync { [self] in try await api.stopOfflineRecording(deviceId, feature: .acc) }
            try awaitVoidAsync { [self] in try await api.setOfflineRecordingTrigger(deviceId, trigger: PolarOfflineRecordingTrigger(triggerMode: .triggerSystemStart, triggerFeatures: [.acc: nil]), secret: nil) }
            let trigger = try awaitSingleAsync { [self] in try await api.getOfflineRecordingTriggerSetup(deviceId) }
            try awaitVoidAsync { [self] in try await api.enableSDKMode(deviceId) }
            try awaitVoidAsync { [self] in try await api.disableSDKMode(deviceId) }
            XCTAssertTrue(try awaitSingleAsync { [self] in try await api.isSDKModeEnabled(deviceId) })
            XCTAssertEqual(pmdClient.startMeasurementCalls.first?.recordingType, .offline)
            XCTAssertEqual(trigger.triggerMode, .triggerSystemStart)
            
            let hrTransport = MockPolarGattServiceTransmitter()
            let hrClient = BleHrClient(gattServiceTransmitter: hrTransport)
            hrClient.setServiceDiscovered(true)
            hrClient.notifyDescriptorWritten(BleHrClient.HR_MEASUREMENT, enabled: true, err: 0)
            let hrSession = MockMultiClientBleDeviceSession(clients: [hrClient])
            utils.hrSession = hrSession
            utils.serviceSessionByUuid[BleHrClient.HR_SERVICE.uuidString] = hrSession
            pmdClient.readFeatureReturnValue = .success([.acc, .mgn, .temperature])
            let onlineTypes = try awaitSingleAsync { [self] in try await api.getAvailableOnlineStreamDataTypes(deviceId) }
            let hrTypes = try awaitSingleAsync { [self] in try await api.getAvailableHRServiceDataTypes(identifier: deviceId) }
            XCTAssertEqual(onlineTypes, Set([.hr, .acc, .magnetometer, .temperature]))
            XCTAssertEqual(hrTypes, Set([.hr]))
            
            let hrStream = api.startHrStreaming(deviceId)
            Task {
                try? await Task.sleep(nanoseconds: 50_000_000)
                hrClient.processServiceData(BleHrClient.HR_MEASUREMENT, data: Data([0x00, 60]), err: 0)
            }
            let hrData = try awaitFirstValue(hrStream)
            XCTAssertEqual(hrData.first?.hr, 60)
            
            let basTransport = MockPolarGattServiceTransmitter()
            let basClient = BleBasClient(gattServiceTransmitter: basTransport)
            basClient.setServiceDiscovered(true)
            basClient.processServiceData(CBUUID(string: "2A19"), data: Data([88]), err: 0)
            basClient.processServiceData(BleBasClient.BATTERY_STATUS_CHARACTERISTIC, data: Data([0x00, 0x21]), err: 0)
            let basSession = MockMultiClientBleDeviceSession(clients: [basClient])
            utils.serviceSessionByUuid[BleBasClient.BATTERY_SERVICE.uuidString] = basSession
            utils.rssiHandler = { _ in -42 }
            utils.pairingHandler = { _ in true }
            XCTAssertEqual(try api.getBatteryLevel(identifier: deviceId), 88)
            XCTAssertEqual(try api.getChargerState(identifier: deviceId), .charging)
            XCTAssertEqual(try api.getRSSIValue(deviceId), -42)
            XCTAssertTrue(try api.checkIfDeviceDisconnectedDueRemovedPairing(deviceId))
            
            let waitSession = MockBleDeviceSession(mockFtpClient: v2MockClient)
            waitSession.state = .sessionClosed
            var fetchCallCount = 0
            utils.fetchSessionHandler = { _ in
                fetchCallCount += 1
                if fetchCallCount > 1 { waitSession.state = .sessionOpen }
                return waitSession
            }
            try awaitSingleAsync({ [self] in try await api.waitForConnection(deviceId); return () }, timeout: 3)
            XCTAssertGreaterThanOrEqual(fetchCallCount, 2)
        }
    }
        
        private func makePmdApiWithSessionNotReadyError() throws -> (MockPmdBleApiImpl, PolarSensorSetting) {
            let transport = MockPolarGattServiceTransmitter()
            let pmdClient = MockBlePmdClient(gattServiceTransmitter: transport)
            let pmdSession = MockPmdBleDeviceSession(mockPmdClient: pmdClient)
            let api = MockPmdBleApiImpl(mockPmdSession: pmdSession)
            api.pmdServiceUtils.stubError = PolarErrors.deviceNotConnected
            return (api, try PolarSensorSetting([.sampleRate: 52]))
        }
        
        func test_startEcgStreaming_sessionNotReady_emitsError() throws {
            let (api, settings) = try makePmdApiWithSessionNotReadyError()
            XCTAssertNotNil(awaitStreamError(api.startEcgStreaming(deviceId, settings: settings)))
        }
        
        func test_startAccStreaming_sessionNotReady_emitsError() throws {
            let (api, settings) = try makePmdApiWithSessionNotReadyError()
            XCTAssertNotNil(awaitStreamError(api.startAccStreaming(deviceId, settings: settings)))
        }
        
        func test_startGyroStreaming_sessionNotReady_emitsError() throws {
            let (api, settings) = try makePmdApiWithSessionNotReadyError()
            XCTAssertNotNil(awaitStreamError(api.startGyroStreaming(deviceId, settings: settings)))
        }
        
        func test_startMagnetometerStreaming_sessionNotReady_emitsError() throws {
            let (api, settings) = try makePmdApiWithSessionNotReadyError()
            XCTAssertNotNil(awaitStreamError(api.startMagnetometerStreaming(deviceId, settings: settings)))
        }
        
        func test_startPpgStreaming_sessionNotReady_emitsError() throws {
            let (api, settings) = try makePmdApiWithSessionNotReadyError()
            XCTAssertNotNil(awaitStreamError(api.startPpgStreaming(deviceId, settings: settings)))
        }
        
        func test_startPpiStreaming_sessionNotReady_emitsError() throws {
            let (api, _) = try makePmdApiWithSessionNotReadyError()
            XCTAssertNotNil(awaitStreamError(api.startPpiStreaming(deviceId)))
        }
        
        func test_startTemperatureStreaming_sessionNotReady_emitsError() throws {
            let (api, settings) = try makePmdApiWithSessionNotReadyError()
            XCTAssertNotNil(awaitStreamError(api.startTemperatureStreaming(deviceId, settings: settings)))
        }
        
        func test_startPressureStreaming_sessionNotReady_emitsError() throws {
            let (api, settings) = try makePmdApiWithSessionNotReadyError()
            XCTAssertNotNil(awaitStreamError(api.startPressureStreaming(deviceId, settings: settings)))
        }
        
    func test_startSkinTemperatureStreaming_sessionNotReady_emitsError() throws {
        let (api, settings) = try makePmdApiWithSessionNotReadyError()
        XCTAssertNotNil(awaitStreamError(api.startSkinTemperatureStreaming(deviceId, settings: settings)))
    }

    // MARK: - stopStreaming tests

    func test_stopStreaming_ecg_callsStopMeasurementWithEcgType() throws {
        setUpPmdApi()
        try awaitVoidAsync { [self] in try await pmdApi.stopStreaming(deviceId, type: .ecg) }
        XCTAssertEqual(mockPmdClient.stopMeasurementCalls.count, 1)
        XCTAssertEqual(mockPmdClient.stopMeasurementCalls.first, .ecg)
    }

    func test_stopStreaming_acc_callsStopMeasurementWithAccType() throws {
        setUpPmdApi()
        try awaitVoidAsync { [self] in try await pmdApi.stopStreaming(deviceId, type: .acc) }
        XCTAssertEqual(mockPmdClient.stopMeasurementCalls.first, .acc)
    }

    func test_stopStreaming_ppg_callsStopMeasurementWithPpgType() throws {
        setUpPmdApi()
        try awaitVoidAsync { [self] in try await pmdApi.stopStreaming(deviceId, type: .ppg) }
        XCTAssertEqual(mockPmdClient.stopMeasurementCalls.first, .ppg)
    }

    func test_stopStreaming_gyro_callsStopMeasurementWithGyroType() throws {
        setUpPmdApi()
        try awaitVoidAsync { [self] in try await pmdApi.stopStreaming(deviceId, type: .gyro) }
        XCTAssertEqual(mockPmdClient.stopMeasurementCalls.first, .gyro)
    }

    func test_stopStreaming_magnetometer_callsStopMeasurementWithMgnType() throws {
        setUpPmdApi()
        try awaitVoidAsync { [self] in try await pmdApi.stopStreaming(deviceId, type: .mgn) }
        XCTAssertEqual(mockPmdClient.stopMeasurementCalls.first, .mgn)
    }

    func test_stopStreaming_stopMeasurementError_wrapsAsDeviceError() {
        setUpPmdApi()
        mockPmdClient.stopMeasurementError = NSError(domain: "pmd.stop", code: 42)
        let e = awaitErrorAsync { [self] in try await pmdApi.stopStreaming(deviceId, type: .ecg) }
        XCTAssertNotNil(e)
        if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError, got \(String(describing: e))") }
    }

    func test_stopStreaming_sessionNotReady_wrapsAsDeviceError() {
        setUpPmdApi()
        pmdApi.pmdServiceUtils.stubError = PolarErrors.deviceNotConnected
        let e = awaitErrorAsync { [self] in try await pmdApi.stopStreaming(deviceId, type: .ecg) }
        XCTAssertNotNil(e)
        if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError, got \(String(describing: e))") }
    }

    // MARK: - stopHrStreaming tests

    private func makeHrApiWithSession() -> (MockDynamicBleApiImpl, MockDynamicServiceClientUtils, BleHrClient) {
        let hrTransport = MockPolarGattServiceTransmitter()
        let hrClient = BleHrClient(gattServiceTransmitter: hrTransport)
        let hrSession = MockMultiClientBleDeviceSession(clients: [hrClient])
        let (api, utils) = makeDynamicApi()
        utils.pmdSession = hrSession
        return (api, utils, hrClient)
    }

    func test_stopHrStreaming_success_completesWithoutError() throws {
        let (api, _, _) = makeHrApiWithSession()
        try awaitVoidAsync { [self] in try await api.stopHrStreaming(deviceId) }
    }

    func test_stopHrStreaming_finishesActiveHrStream() throws {
        let (api, _, hrClient) = makeHrApiWithSession()
        hrClient.setServiceDiscovered(true)
        hrClient.notifyDescriptorWritten(BleHrClient.HR_MEASUREMENT, enabled: true, err: 0)
        let hrStream = hrClient.observeHrNotifications(false)
        var streamFinished = false
        let exp = XCTestExpectation(description: "stream finished")
        Task {
            do {
                for try await _ in hrStream { }
            } catch {
                // stream may finish with or without error
            }
            streamFinished = true
            exp.fulfill()
        }
        try awaitVoidAsync { [self] in try await api.stopHrStreaming(deviceId) }
        wait(for: [exp], timeout: 2)
        XCTAssertTrue(streamFinished)
    }

    func test_stopHrStreaming_sessionNotReady_wrapsAsDeviceError() {
        let (api, utils) = makeDynamicApi()
        utils.pmdError = PolarErrors.deviceNotConnected
        let e = awaitErrorAsync { [self] in try await api.stopHrStreaming(deviceId) }
        XCTAssertNotNil(e)
        if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError, got \(String(describing: e))") }
    }

   func test_getSubRecordings_removeOfflineRecords_and_multiBleMode_coverRemainingHelpers() throws {
           let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
           let get = Protocol_PbPFtpOperation.Command.get.rawValue
           let remove = Protocol_PbPFtpOperation.Command.remove.rawValue
           v2MockClient.requestReturnValueClosure = makeCommandAwareRequestClosure([
               "\(get)|/U/0/20240615/R/103000/": [
                   { try self.makeDirectoryProtoData(entries: [("ACC0.REC", 50), ("ACC1.REC", 50), ("GYRO0.REC", 50)]) },
                   { try self.makeDirectoryProtoData(entries: [("ACC0.REC", 50), ("ACC1.REC", 50), ("GYRO0.REC", 50)]) },
                   { try self.makeDirectoryProtoData(entries: []) },
               ],
               "\(remove)|/U/0/20240615/R/103000/ACC0.REC": [{ Data() }],
               "\(remove)|/U/0/20240615/R/103000/ACC1.REC": [{ Data() }],
               "\(remove)|/U/0/20240615/R/103000/": [{ Data() }],
               "\(get)|/U/0/20240615/R/": [{ try self.makeDirectoryProtoData(entries: []) }],
               "\(remove)|/U/0/20240615/R/": [{ Data() }],
               "\(get)|/U/0/20240615/": [{ try self.makeDirectoryProtoData(entries: []) }],
               "\(remove)|/U/0/20240615/": [{ Data() }],
           ])

           let subRecordings = try awaitSingleAsync { [self] in try await v2Api.getSubRecordings(identifier: deviceId, entry: entry) }
           XCTAssertEqual(subRecordings, [
               "/U/0/20240615/R/103000/ACC0.REC",
               "/U/0/20240615/R/103000/ACC1.REC",
           ])
           XCTAssertTrue(try awaitSingleAsync { [self] in try await v2Api.removeOfflineRecords(deviceId, entry: entry) })

           let pfcTransport = MockPolarGattServiceTransmitter()
           let pfcClient = MockBlePfcClient(gattServiceTransmitter: pfcTransport)
           let pfcSession = MockPfcBleDeviceSession(mockPfcClient: pfcClient)
           let pfcApi = MockPfcBleApiImpl(mockPfcSession: pfcSession)
           pfcClient.commandReturnValue = .success(Pfc.PfcResponse())
           try awaitVoidAsync { [self] in try await pfcApi.setMultiBLEConnectionMode(identifier: deviceId, enable: true) }
           pfcClient.commandReturnValue = .success(Pfc.PfcResponse(data: Data([0x00, 0x00, 0x01, 0x01])))
           XCTAssertTrue(try awaitSingleAsync { [self] in try await pfcApi.getMultiBLEConnectionMode(identifier: deviceId) })
           XCTAssertEqual(pfcClient.commandCalls.first?.command, .pfcConfigureMultiConnection)
           XCTAssertEqual(pfcClient.commandCalls.last?.command, .pfcRequestMultiConnectionSetting)
       }

       @available(*, deprecated)
       func test_resetAndPowerControlHelpers_sendExpectedResetNotifications() throws {
           try awaitSingleAsync { [self] in
               try await v2Api.doFactoryReset(deviceId, preservePairingInformation: true)
               return ()
           }
           var params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertTrue(params.otaFwupdate)
           XCTAssertFalse(params.sleep)

           try awaitSingleAsync { [self] in
               try await v2Api.doFactoryReset(deviceId)
               return ()
           }
           params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertFalse(params.otaFwupdate)
           XCTAssertFalse(params.sleep)

           try awaitSingleAsync { [self] in
               try await v2Api.doRestart(deviceId, preservePairingInformation: true)
               return ()
           }
           params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertFalse(params.doFactoryDefaults)
           XCTAssertTrue(params.otaFwupdate)

           try awaitSingleAsync { [self] in
               try await v2Api.doRestart(deviceId)
               return ()
           }
           params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertFalse(params.doFactoryDefaults)
           XCTAssertFalse(params.otaFwupdate)

           try awaitSingleAsync { [self] in
               try await v2Api.setWarehouseSleep(deviceId, enableWarehouseSleep: false)
               return ()
           }
           params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertFalse(params.sleep)
           XCTAssertTrue(params.otaFwupdate)

           try awaitSingleAsync { [self] in
               try await v2Api.setWarehouseSleep(deviceId)
               return ()
           }
           params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertTrue(params.sleep)
           XCTAssertTrue(params.doFactoryDefaults)

           try awaitSingleAsync { [self] in
               try await v2Api.turnDeviceOff(deviceId)
               return ()
           }
           params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.last?.parameters as Data?))
           XCTAssertTrue(params.sleep)
           XCTAssertFalse(params.doFactoryDefaults)
       }

       func test_syncNotificationHelpers_sendExpectedCommands() throws {
           try awaitSingleAsync { [self] in
               try await v2Api.sendInitializationAndStartSyncNotifications(identifier: deviceId)
               return ()
           }
           XCTAssertEqual(v2MockClient.queryCalls.last?.id, Protocol_PbPFtpQuery.requestSynchronization.rawValue)
           XCTAssertEqual(v2MockClient.sendNotificationCalls.suffix(2).map(\.notification), [
               Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue,
               Protocol_PbPFtpHostToDevNotification.startSync.rawValue,
           ])

           v2MockClient.sendNotificationCalls.removeAll()
           try awaitSingleAsync { [self] in
               try await v2Api.sendTerminateAndStopSyncNotifications(identifier: deviceId)
               return ()
           }
           XCTAssertEqual(v2MockClient.sendNotificationCalls.map(\.notification), [
               Protocol_PbPFtpHostToDevNotification.stopSync.rawValue,
               Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue,
           ])
           let stopParams = try Protocol_PbPFtpStopSyncParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls.first?.parameters as Data?))
           XCTAssertTrue(stopParams.completed)

           v2MockClient.sendNotificationCalls.removeAll()
           try awaitSingleAsync { [self] in
               try await v2Api.sendTerminateSessionNotification(identifier: deviceId)
               return ()
           }
           XCTAssertEqual(v2MockClient.sendNotificationCalls.map(\.notification), [Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue])

           v2MockClient.sendNotificationCalls.removeAll()
           try awaitSingleAsync { [self] in
               try await v2Api.sendStopSyncNotification(identifier: deviceId)
               return ()
           }
           XCTAssertEqual(v2MockClient.sendNotificationCalls.map(\.notification), [Protocol_PbPFtpHostToDevNotification.stopSync.rawValue])
       }

       func test_exerciseControlQueries_sendExpectedCommands_and_getStatusParsesResponse() throws {
           var status = Protocol_PbPftpGetExerciseStatusResult()
           status.exerciseState = .exerciseStateRunning
           status.sportIdentifier.value = UInt64(PolarExerciseSession.SportProfile.running.rawValue)

           v2MockClient.queryReturnValues = [
               .success(Data()),
               .success(Data()),
               .success(Data()),
               .success(Data()),
               .success(try status.serializedData()),
           ]

           try awaitSingleAsync { [self] in
               try await v2Api.startExercise(identifier: deviceId, profile: .running)
               return ()
           }
           try awaitSingleAsync { [self] in
               try await v2Api.pauseExercise(identifier: deviceId)
               return ()
           }
           try awaitSingleAsync { [self] in
               try await v2Api.resumeExercise(identifier: deviceId)
               return ()
           }
           try awaitSingleAsync { [self] in
               try await v2Api.stopExercise(identifier: deviceId)
               return ()
           }
           let result = try awaitSingleAsync { [self] in
               try await v2Api.getExerciseStatus(identifier: deviceId)
           }

           XCTAssertEqual(v2MockClient.queryCalls.map(\.id), [
               Protocol_PbPFtpQuery.startExercise.rawValue,
               Protocol_PbPFtpQuery.pauseExercise.rawValue,
               Protocol_PbPFtpQuery.resumeExercise.rawValue,
               Protocol_PbPFtpQuery.stopExercise.rawValue,
               Protocol_PbPFtpQuery.getExerciseStatus.rawValue,
           ])
           XCTAssertEqual(result.status, .inProgress)
           XCTAssertEqual(result.sportProfile, .running)
       }

       func test_observeExerciseStatus_yieldsParsedNotifications() throws {
           var status = Protocol_PbPftpGetExerciseStatusResult()
           status.exerciseState = .exerciseStatePaused
           status.sportIdentifier.value = UInt64(PolarExerciseSession.SportProfile.cycling.rawValue)
           v2MockClient.receiveNotificationCalls = [
               (notification: Protocol_PbPFtpDevToHostNotification.exerciseStatus.rawValue,
                parameters: [try status.serializedData()],
                compressed: false),
           ]

           let values = try collectAllAsync(v2Api.observeExerciseStatus(identifier: deviceId))
           XCTAssertEqual(values.count, 1)
           XCTAssertEqual(values.first?.status, .paused)
           XCTAssertEqual(values.first?.sportProfile, .cycling)
       }

       @available(*, deprecated)
       func test_offlineExerciseListingApis_returnErrors_whenFtpLookupFails() {
           let (api, utils) = makeDynamicApi()
           utils.ftpError = PolarErrors.deviceNotConnected

           let exerciseEntry = PolarExerciseEntry(path: "/U/0/EX/SAMPLES.BPB", date: Date(), entryId: "x")
           XCTAssertNotNil(awaitErrorAsync { try await api.fetchExercise(self.deviceId, entry: exerciseEntry) })
           XCTAssertNotNil(awaitStreamError(api.listExercises(self.deviceId)))
           XCTAssertNotNil(awaitStreamError(api.fetchStoredExerciseList(self.deviceId)))
       }

       func test_userSettingsReadAndWriteApis_useExpectedPathsAndValues() throws {
           let getProtoData = try makeUserDeviceSettingsProto(deviceLocation: .deviceLocationWristRight, usbMode: .on, telemetryEnabled: true, autosEnabled: true, atdState: .on, atdSensitivity: 44, minimumDuration: 600)
           v2MockClient.requestReturnValue = .success(getProtoData)

           let result = try awaitSingleAsync { [self] in
               try await v2Api.getPolarUserDeviceSettings(identifier: deviceId)
           }
           XCTAssertEqual(result.deviceLocation, .WRIST_RIGHT)
           XCTAssertEqual(result.usbConnectionMode, .ON)
           XCTAssertEqual(result.telemetryEnabled, true)
           XCTAssertEqual(result.autosFilesEnabled, true)

           let settings = PolarUserDeviceSettings()
           settings.deviceLocation = .CHEST
           settings.usbConnectionMode = .OFF
           settings.telemetryEnabled = false
           settings.autosFilesEnabled = false
           try awaitSingleAsync { [self] in
               try await v2Api.setPolarUserDeviceSettings(deviceId, polarUserDeviceSettings: settings)
               return ()
           }

           let writeCall = try XCTUnwrap(v2MockClient.writeCalls.last)
           let header = try Protocol_PbPFtpOperation(serializedBytes: writeCall.header as Data)
           XCTAssertEqual(header.path, DEVICE_SETTINGS_FILE_PATH)
           let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: writeCall.data))
           XCTAssertEqual(writtenSettings.generalSettings.deviceLocation, .deviceLocationChest)
           XCTAssertEqual(writtenSettings.telemetrySettings.telemetryEnabled, false)
           XCTAssertEqual(writtenSettings.automaticMeasurementSettings.automaticOhrMeasurement.state, .off)
       }

       func test_settingsMutationApis_updateExpectedFields() throws {
           v2MockClient.requestReturnValue = .success(try makeUserDeviceSettingsProto())
           try awaitSingleAsync { [self] in
               try await v2Api.setUsbConnectionMode(deviceId, enabled: true)
               return ()
           }
           var written = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: try XCTUnwrap(v2MockClient.writeCalls.last?.data)))
           XCTAssertEqual(written.usbConnectionSettings.mode, .on)

           v2MockClient.writeCalls.removeAll()
           v2MockClient.requestReturnValue = .success(try makeUserDeviceSettingsProto())
           try awaitSingleAsync { [self] in
               try await v2Api.setAutomaticTrainingDetectionSettings(deviceId, mode: true, sensitivity: 77, minimumDuration: 900)
               return ()
           }
           written = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: try XCTUnwrap(v2MockClient.writeCalls.last?.data)))
           XCTAssertEqual(written.automaticMeasurementSettings.automaticTrainingDetectionSettings.state, .on)
           XCTAssertEqual(written.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity, 77)
           XCTAssertEqual(written.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds, 900)

           v2MockClient.writeCalls.removeAll()
           v2MockClient.requestReturnValue = .success(try makeUserDeviceSettingsProto())
           try awaitSingleAsync { [self] in
               try await v2Api.setTelemetryEnabled(deviceId, enabled: true)
               return ()
           }
           written = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: try XCTUnwrap(v2MockClient.writeCalls.last?.data)))
           XCTAssertTrue(written.telemetrySettings.telemetryEnabled)

           v2MockClient.writeCalls.removeAll()
           v2MockClient.requestReturnValue = .success(try makeUserDeviceSettingsProto(autosEnabled: true))
           try awaitSingleAsync { [self] in
               try await v2Api.setAutomaticOHRMeasurementEnabled(deviceId, enabled: false)
               return ()
           }
           written = try Data_PbUserDeviceSettings(serializedBytes: readAll(from: try XCTUnwrap(v2MockClient.writeCalls.last?.data)))
           XCTAssertEqual(written.automaticMeasurementSettings.automaticOhrMeasurement.state, .off)
       }

       func test_setUserDeviceLocation_invalidLocation_and_setDaylightSavingTime_sessionError_areReported() {
           let invalidLocationError = awaitErrorAsync { [self] in
               try await v2Api.setUserDeviceLocation(deviceId, location: 999)
           }
           XCTAssertNotNil(invalidLocationError)

           let (api, utils) = makeDynamicApi()
           utils.ftpError = PolarErrors.deviceNotConnected
           let daylightSavingError = awaitErrorAsync { try await api.setDaylightSavingTime(self.deviceId) }
           XCTAssertNotNil(daylightSavingError)
       }

       func test_doFirstTimeUse_invalidDeviceTime_throws() {
           let config = PolarFirstTimeUseConfig(
               gender: .male,
               birthDate: makeDate(1990, 1, 1),
               height: 180,
               weight: 80,
               maxHeartRate: 190,
               vo2Max: 50,
               restingHeartRate: 55,
               trainingBackground: .regular,
               deviceTime: "not-an-iso-date",
               typicalDay: .mostlyMoving,
               sleepGoalMinutes: 480
           )

           XCTAssertNotNil(awaitErrorAsync { [self] in
               try await v2Api.doFirstTimeUse(deviceId, ftuConfig: config)
           })
       }

       func test_isFtuDone_and_getUserPhysicalConfiguration_handleExistingAndMissingFiles() throws {
           var userId = Data_PbUserIdentifier()
           userId.masterIdentifier = 12345
           v2MockClient.requestReturnValue = .success(try userId.serializedData())
           let isDone = try awaitSingleAsync { [self] in try await v2Api.isFtuDone(deviceId) }
           XCTAssertTrue(isDone)

           v2MockClient.requestReturnValue = .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))
           let config = try awaitSingleAsync { [self] in try await v2Api.getUserPhysicalConfiguration(deviceId) }
           XCTAssertNil(config)
       }

       func test_offlineRecordingControl_and_sdkMode_delegateToPmdClient() throws {
           let transport = MockPolarGattServiceTransmitter()
           let pmdClient = MockBlePmdClient(gattServiceTransmitter: transport)
           let pmdSession = MockPmdBleDeviceSession(mockPmdClient: pmdClient)
           let (api, utils) = makeDynamicApi()
           utils.pmdSession = pmdSession
           pmdClient.readMeasurementStatusReturnValue = .success([(.acc, .no_measurement_active)])
           pmdClient.getOfflineRecordingTriggerStatusReturnValue = .success(PmdOfflineTrigger(triggerMode: .systemStart, triggers: [.acc: (.enabled, nil)]))
           pmdClient.sdkModeEnabledReturnValue = .success(.enabled)

           try awaitSingleAsync { [self] in
               try await api.startOfflineRecording(deviceId, feature: .acc, settings: nil, secret: nil)
               return ()
           }
           try awaitSingleAsync { [self] in
               try await api.stopOfflineRecording(deviceId, feature: .acc)
               return ()
           }
           try awaitSingleAsync { [self] in
               try await api.setOfflineRecordingTrigger(deviceId, trigger: PolarOfflineRecordingTrigger(triggerMode: .triggerSystemStart, triggerFeatures: [.acc: nil]), secret: nil)
               return ()
           }
           let trigger = try awaitSingleAsync { [self] in
               try await api.getOfflineRecordingTriggerSetup(deviceId)
           }
           try awaitSingleAsync { [self] in
               try await api.enableSDKMode(deviceId)
               return ()
           }
           try awaitSingleAsync { [self] in
               try await api.disableSDKMode(deviceId)
               return ()
           }
           let sdkEnabled = try awaitSingleAsync { [self] in
               try await api.isSDKModeEnabled(deviceId)
           }

           XCTAssertEqual(pmdClient.startMeasurementCalls.first?.type, .acc)
           XCTAssertEqual(pmdClient.startMeasurementCalls.first?.recordingType, .offline)
           XCTAssertEqual(pmdClient.stopMeasurementCalls, [.acc])
           XCTAssertEqual(pmdClient.setOfflineRecordingTriggerCalls.count, 1)
           XCTAssertEqual(trigger.triggerMode, .triggerSystemStart)
           XCTAssertTrue(trigger.triggerFeatures.keys.contains(.acc))
           XCTAssertEqual(pmdClient.sdkModeStartCalls, 1)
           XCTAssertEqual(pmdClient.sdkModeStopCalls, 1)
           XCTAssertTrue(sdkEnabled)
       }

       func test_getAvailableOnlineAndHrServiceDataTypes_returnExpectedSets() throws {
           let hrTransport = MockPolarGattServiceTransmitter()
           let hrClient = BleHrClient(gattServiceTransmitter: hrTransport)
           hrClient.setServiceDiscovered(true)
           hrClient.notifyDescriptorWritten(BleHrClient.HR_MEASUREMENT, enabled: true, err: 0)
           let hrSession = MockMultiClientBleDeviceSession(clients: [hrClient])

           let pmdTransport = MockPolarGattServiceTransmitter()
           let pmdClient = MockBlePmdClient(gattServiceTransmitter: pmdTransport)
           pmdClient.readFeatureReturnValue = .success([.acc, .mgn, .temperature])
           let pmdSession = MockPmdBleDeviceSession(mockPmdClient: pmdClient)

           let (api, utils) = makeDynamicApi()
           utils.hrSession = hrSession
           utils.pmdSession = pmdSession
           utils.serviceSessionByUuid[BleHrClient.HR_SERVICE.uuidString] = hrSession

           let online = try awaitSingleAsync { [self] in
               try await api.getAvailableOnlineStreamDataTypes(deviceId)
           }
           let hrOnly = try awaitSingleAsync { [self] in
               try await api.getAvailableHRServiceDataTypes(identifier: deviceId)
           }

           XCTAssertEqual(online, Set([.hr, .acc, .magnetometer, .temperature]))
           XCTAssertEqual(hrOnly, Set([.hr]))
       }

       func test_onlineStreamingApis_wrapPmdErrors() {
           let transport = MockPolarGattServiceTransmitter()
           let pmdClient = MockBlePmdClient(gattServiceTransmitter: transport)
           let pmdSession = MockPmdBleDeviceSession(mockPmdClient: pmdClient)
           let api = MockPmdBleApiImpl(mockPmdSession: pmdSession)
           api.pmdServiceUtils.stubError = PolarErrors.deviceNotConnected
           let settings = try! PolarSensorSetting([.sampleRate: 52])

           XCTAssertNotNil(awaitStreamError(api.startEcgStreaming(deviceId, settings: settings)))
           XCTAssertNotNil(awaitStreamError(api.startAccStreaming(deviceId, settings: settings)))
           XCTAssertNotNil(awaitStreamError(api.startGyroStreaming(deviceId, settings: settings)))
           XCTAssertNotNil(awaitStreamError(api.startMagnetometerStreaming(deviceId, settings: settings)))
           XCTAssertNotNil(awaitStreamError(api.startPpgStreaming(deviceId, settings: settings)))
           XCTAssertNotNil(awaitStreamError(api.startPpiStreaming(deviceId)))
           XCTAssertNotNil(awaitStreamError(api.startTemperatureStreaming(deviceId, settings: settings)))
           XCTAssertNotNil(awaitStreamError(api.startPressureStreaming(deviceId, settings: settings)))
           XCTAssertNotNil(awaitStreamError(api.startSkinTemperatureStreaming(deviceId, settings: settings)))
       }

       func test_startHrStreaming_yieldsMappedHrSample() throws {
           let transport = MockPolarGattServiceTransmitter()
           let hrClient = BleHrClient(gattServiceTransmitter: transport)
           hrClient.setServiceDiscovered(true)
           hrClient.notifyDescriptorWritten(BleHrClient.HR_MEASUREMENT, enabled: true, err: 0)
           let hrSession = MockMultiClientBleDeviceSession(clients: [hrClient])
           let (api, utils) = makeDynamicApi()
           utils.hrSession = hrSession
           utils.serviceSessionByUuid[BleHrClient.HR_SERVICE.uuidString] = hrSession

           let stream = api.startHrStreaming(deviceId)
           Task {
               try? await Task.sleep(nanoseconds: 50_000_000)
               hrClient.processServiceData(BleHrClient.HR_MEASUREMENT, data: Data([0x00, 60]), err: 0)
           }

           let hrData = try awaitFirstValue(stream)
           XCTAssertEqual(hrData.first?.hr, 60)
           XCTAssertEqual(hrData.first?.rrAvailable, false)
       }

       func test_multiBleConnectionMode_queriesAndSetsPfc() throws {
           let transport = MockPolarGattServiceTransmitter()
           let pfcClient = MockBlePfcClient(gattServiceTransmitter: transport)
           let pfcSession = MockPfcBleDeviceSession(mockPfcClient: pfcClient)
           let api = MockPfcBleApiImpl(mockPfcSession: pfcSession)

           pfcClient.commandReturnValue = .success(Pfc.PfcResponse())
           try awaitSingleAsync { [self] in
               try await api.setMultiBLEConnectionMode(identifier: deviceId, enable: true)
               return ()
           }

           pfcClient.commandReturnValue = .success(Pfc.PfcResponse(data: Data([0x00, 0x00, 0x01, 0x01])))
           let enabled = try awaitSingleAsync { [self] in
               try await api.getMultiBLEConnectionMode(identifier: deviceId)
           }

           XCTAssertEqual(pfcClient.commandCalls.count, 2)
           XCTAssertEqual(pfcClient.commandCalls.first?.command, .pfcConfigureMultiConnection)
           XCTAssertEqual(pfcClient.commandCalls.first?.value, [1])
           XCTAssertEqual(pfcClient.commandCalls.last?.command, .pfcRequestMultiConnectionSetting)
           XCTAssertTrue(enabled)
       }

       func test_batteryRssiAndPairingHelpers_returnUtilityValues() throws {
           let transport = MockPolarGattServiceTransmitter()
           let basClient = BleBasClient(gattServiceTransmitter: transport)
           basClient.setServiceDiscovered(true)
           basClient.processServiceData(CBUUID(string: "2A19"), data: Data([88]), err: 0)
           basClient.processServiceData(BleBasClient.BATTERY_STATUS_CHARACTERISTIC, data: Data([0x00, 0x21]), err: 0)
           let basSession = MockMultiClientBleDeviceSession(clients: [basClient])

           let (api, utils) = makeDynamicApi()
           utils.serviceSessionByUuid[BleBasClient.BATTERY_SERVICE.uuidString] = basSession
           utils.rssiHandler = { _ in -42 }
           utils.pairingHandler = { _ in true }

           XCTAssertEqual(try api.getBatteryLevel(identifier: deviceId), 88)
           XCTAssertEqual(try api.getChargerState(identifier: deviceId), .charging)
           XCTAssertEqual(try api.getRSSIValue(deviceId), -42)
           XCTAssertTrue(try api.checkIfDeviceDisconnectedDueRemovedPairing(deviceId))
       }

       func test_waitForConnection_returnsAfterSessionBecomesOpen() throws {
           let (api, utils) = makeDynamicApi()
           let session = MockBleDeviceSession(mockFtpClient: v2MockClient)
           session.state = .sessionClosed
           var callCount = 0
           utils.fetchSessionHandler = { _ in
               callCount += 1
               if callCount > 1 { session.state = .sessionOpen }
               return session
           }

           try awaitSingleAsync({ [self] in
               try await api.waitForConnection(deviceId)
               return ()
           }, timeout: 3)
           XCTAssertGreaterThanOrEqual(callCount, 2)
       }

       func test_getSubRecordings_and_removeOfflineRecords_coverOfflineRecordingHelpers() throws {
           let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
           v2MockClient.requestReturnValueClosure = makeCommandAwareRequestClosure([
               "\(Protocol_PbPFtpOperation.Command.get.rawValue)|/U/0/20240615/R/103000/": [
                   { try self.makeDirectoryProtoData(entries: [("ACC0.REC", 50), ("ACC1.REC", 50), ("GYRO0.REC", 50)]) },
                   { try self.makeDirectoryProtoData(entries: [("ACC0.REC", 50), ("ACC1.REC", 50), ("GYRO0.REC", 50)]) },
                   { try self.makeDirectoryProtoData(entries: []) },
               ],
               "\(Protocol_PbPFtpOperation.Command.remove.rawValue)|/U/0/20240615/R/103000/ACC0.REC": [{ Data() }],
               "\(Protocol_PbPFtpOperation.Command.remove.rawValue)|/U/0/20240615/R/103000/ACC1.REC": [{ Data() }],
               "\(Protocol_PbPFtpOperation.Command.remove.rawValue)|/U/0/20240615/R/103000/": [{ Data() }],
               "\(Protocol_PbPFtpOperation.Command.get.rawValue)|/U/0/20240615/R/": [{ try self.makeDirectoryProtoData(entries: []) }],
               "\(Protocol_PbPFtpOperation.Command.remove.rawValue)|/U/0/20240615/R/": [{ Data() }],
               "\(Protocol_PbPFtpOperation.Command.get.rawValue)|/U/0/20240615/": [{ try self.makeDirectoryProtoData(entries: []) }],
               "\(Protocol_PbPFtpOperation.Command.remove.rawValue)|/U/0/20240615/": [{ Data() }],
           ])

           let subRecordings = try awaitSingleAsync { [self] in
               try await v2Api.getSubRecordings(identifier: deviceId, entry: entry)
           }
           XCTAssertEqual(subRecordings, [
               "/U/0/20240615/R/103000/ACC0.REC",
               "/U/0/20240615/R/103000/ACC1.REC",
           ])

           let removed = try awaitSingleAsync { [self] in
               try await v2Api.removeOfflineRecords(deviceId, entry: entry)
           }
           XCTAssertTrue(removed)
       }
}
