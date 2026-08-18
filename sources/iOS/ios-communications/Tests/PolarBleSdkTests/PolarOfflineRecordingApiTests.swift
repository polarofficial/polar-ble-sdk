//  Copyright © 2025 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import PolarBleSdk

final class PolarOfflineRecordingApiTests: XCTestCase {
    private let deviceId = "ABCDEF01"
    
    var mockClient: MockBlePsFtpClient!
    var mockSession: MockBleDeviceSession!
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    private var v2MockClient: MockBlePsFtpClient!
    private var v2MockSession: MockBleDeviceSession!
    private var v2Api: PolarBleApiImplWithMockSession!
    private var h10MockClient: MockBlePsFtpClient!
    private var h10MockSession: MockH10BleDeviceSession!
    private var h10Api: PolarBleApiImplWithMockH10Session!
    private var pmdApi: MockPmdBleApiImpl!
    private var mockPmdSession: MockPmdBleDeviceSession!
    private var mockPmdClient: MockBlePmdClient!
    
    override func setUpWithError() throws {
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: ["h10": .h10FileSystem],
            defaultFileSystemType: .polarFileSystemV2,
            defaultRecordingSupported: false
        )
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        v2MockClient = mockClient
        v2MockSession = mockSession
        v2Api = PolarBleApiImplWithMockSession(mockDeviceSession: v2MockSession)

        let h10Gatt = MockPolarGattServiceTransmitter()
        h10MockClient = MockBlePsFtpClient(gattServiceTransmitter: h10Gatt)
        h10MockSession = MockH10BleDeviceSession(mockFtpClient: h10MockClient)
        h10Api = PolarBleApiImplWithMockH10Session(mockDeviceSession: h10MockSession)
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
        mockSession = nil
        v2MockClient = nil
        v2MockSession = nil
        v2Api = nil
        h10MockClient = nil
        h10MockSession = nil
        h10Api = nil
        pmdApi = nil
        mockPmdSession = nil
        mockPmdClient = nil
    }
    
    // MARK: - Helpers
    
    private func makeV1Mocks(dateDir: String, timeDir: String) throws -> [Result<Data, Error>] {
        let userDir = try Protocol_PbPFtpDirectory.with {
            $0.entries = [Protocol_PbPFtpEntry.with { $0.name = "\(dateDir)/"; $0.size = 0 }]
        }.serializedData()
        let dateDirectory = try Protocol_PbPFtpDirectory.with {
            $0.entries = [Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }]
        }.serializedData()
        let recordingsDir = try Protocol_PbPFtpDirectory.with {
            $0.entries = [Protocol_PbPFtpEntry.with { $0.name = "\(timeDir)/"; $0.size = 0 }]
        }.serializedData()
        let recordingDir = try Protocol_PbPFtpDirectory.with {
            $0.entries = [Protocol_PbPFtpEntry.with { $0.name = "HR.REC"; $0.size = 444 }]
        }.serializedData()
        return [
            .failure(PolarErrors.fileError(description: "NO PMDFILES.TXT FILE")), // [0] /PMDFILES.TXT → forces V1 path
            .success(userDir),       // [1] GET /U/0/
            .success(dateDirectory), // [2] GET /U/0/<dateDir>/
            .success(recordingsDir), // [3] GET /U/0/<dateDir>/R/
            .success(recordingDir),  // [4] GET /U/0/<dateDir>/R/<timeDir>/
        ]
    }

    private func collectFirst(_ stream: AsyncThrowingStream<PolarOfflineRecordingEntry, Error>) async throws -> PolarOfflineRecordingEntry? {
        for try await entry in stream { return entry }
        return nil
    }

    private func collectAll(_ stream: AsyncThrowingStream<PolarOfflineRecordingEntry, Error>) async throws -> [PolarOfflineRecordingEntry] {
        var results: [PolarOfflineRecordingEntry] = []
        for try await entry in stream { results.append(entry) }
        return results
    }

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

    private func collectAllAsync<T>(_ stream: AsyncThrowingStream<T, Error>, timeout: TimeInterval = 5) throws -> [T] {
        try awaitSingleAsync({
            var results: [T] = []
            for try await value in stream { results.append(value) }
            return results
        }, timeout: timeout)
    }

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
            throw NSError(domain: "test.unrouted", code: 0,
                          userInfo: [NSLocalizedDescriptionKey: "Unrouted: \(op.path)"])
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
            throw NSError(domain: "test.unrouted", code: 0,
                          userInfo: [NSLocalizedDescriptionKey: "Unrouted: \(key)"])
        }
    }

    private func mockTimeZoneReturningCurrent(_ testTimeZone: TimeZone) -> TimeZone {
        let originalTimeZone = TimeZone.current
        if #available(iOS 17.0, *) {
            setenv("TZ", testTimeZone.identifier, 1)
            CFTimeZoneResetSystem()
        } else {
            NSTimeZone.default = testTimeZone
        }
        return originalTimeZone
    }
    
    private func restoreTimeZone(_ originalTimeZone: TimeZone) {
        if #available(iOS 17.0, *) {
            setenv("TZ", originalTimeZone.identifier, 1)
            CFTimeZoneResetSystem()
        } else {
            NSTimeZone.default = originalTimeZone
        }
    }

    // MARK: - Tests

    func testListOfflineRecordingsFromDaylightSavingsTimeShiftHourForwardInNewYork2025_shouldReturnListing() async throws {
        // Sunday, 9 March 2025, 02:00:00 clocks were turned forward 1 hour to 03:00:00.
        // Use 03:01:30 EDT — a valid, unambiguous time immediately after the spring-forward.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "UTC")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        defer { restoreTimeZone(originalTimeZone) }

        mockClient.requestReturnValues = try makeV1Mocks(dateDir: "20250309", timeDir: "030130")

        // Act
        let entry = try await collectFirst(api.listOfflineRecordings("123456"))

        // Assert
        let unwrappedEntry = try XCTUnwrap(entry)
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Calendar(identifier: .iso8601)
        dateFormatter.timeZone = testTimeZone
        dateFormatter.locale = Locale(identifier: "en_US")
        dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-03-09, 03:01:30 Greenwich Mean Time")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20250309/R/030130/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingV1BeforeDaylightSavingsTimeShiftHourBackwardInNewYork2025_shouldReturnListing() async throws {
        // Sunday, 2 November 2025, 02:00:00 clocks turned backward 1 hour to 01:00:00.
        // Use 00:30:07 EDT — a valid, unambiguous time clearly before the fall-back.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "UTC")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        defer { restoreTimeZone(originalTimeZone) }

        mockClient.requestReturnValues = try makeV1Mocks(dateDir: "20251102", timeDir: "003007")

        let entry = try await collectFirst(api.listOfflineRecordings("123456"))

        let unwrappedEntry = try XCTUnwrap(entry)
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Calendar(identifier: .iso8601)
        dateFormatter.timeZone = testTimeZone
        dateFormatter.locale = Locale(identifier: "en_US")
        dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-11-02, 00:30:07 Greenwich Mean Time")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20251102/R/003007/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV1AfterDaylightSavingsTimeShiftHourBackwardInNewYork2025_shouldReturnListing() async throws {
        // Sunday, 2 November 2025, 02:00:00 clocks turned backward 1 hour to 01:00:00.
        // Use 03:01:30 EST — a valid, unambiguous time clearly after the fall-back.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "UTC")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        defer { restoreTimeZone(originalTimeZone) }

        mockClient.requestReturnValues = try makeV1Mocks(dateDir: "20251102", timeDir: "030130")

        let entry = try await collectFirst(api.listOfflineRecordings("123456"))

        let unwrappedEntry = try XCTUnwrap(entry)
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Calendar(identifier: .iso8601)
        dateFormatter.timeZone = testTimeZone
        dateFormatter.locale = Locale(identifier: "en_US")
        dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-11-02, 03:01:30 Greenwich Mean Time")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20251102/R/030130/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV1AfterDaylightSavingsTimeShiftHourForwardInHelsinki2025_shouldReturnListing() async throws {
        // Sunday, 30 March 2025, 03:00:00 clocks turned forward 1 hour to 04:00:00.
        // Use 04:01:00 EEST — a valid, unambiguous time immediately after the spring-forward.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "UTC")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        defer { restoreTimeZone(originalTimeZone) }

        mockClient.requestReturnValues = try makeV1Mocks(dateDir: "20250330", timeDir: "040100")

        let entry = try await collectFirst(api.listOfflineRecordings("123456"))

        let unwrappedEntry = try XCTUnwrap(entry)
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Calendar(identifier: .iso8601)
        dateFormatter.timeZone = testTimeZone
        dateFormatter.locale = Locale(identifier: "fi_FI")
        dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-03-30, 04:01:00 Greenwichin normaaliaika")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20250330/R/040100/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV1BeforeDaylightSavingsTimeShiftHourBackwardInHelsinki2025_shouldReturnListing() async throws {
        // Sunday, 26 October 2025, 04:00:00 clocks turned backward 1 hour to 03:00:00.
        // Use 02:00:00 EEST — a valid, unambiguous time clearly before the fall-back.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "UTC")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        defer { restoreTimeZone(originalTimeZone) }

        mockClient.requestReturnValues = try makeV1Mocks(dateDir: "20251026", timeDir: "020000")

        let entry = try await collectFirst(api.listOfflineRecordings("123456"))

        let unwrappedEntry = try XCTUnwrap(entry)
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Calendar(identifier: .iso8601)
        dateFormatter.timeZone = testTimeZone
        dateFormatter.locale = Locale(identifier: "fi_FI")
        dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-10-26, 02:00:00 Greenwichin normaaliaika")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20251026/R/020000/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV1AfterDaylightSavingsTimeShiftHourBackwardInHelsinki2025_shouldReturnListing() async throws {
        // Sunday, 26 October 2025, 04:00:00 clocks turned backward 1 hour to 03:00:00.
        // Use 06:00:00 EET — a valid, unambiguous time clearly after the fall-back.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "UTC")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        defer { restoreTimeZone(originalTimeZone) }

        mockClient.requestReturnValues = try makeV1Mocks(dateDir: "20251026", timeDir: "060000")

        let entry = try await collectFirst(api.listOfflineRecordings("123456"))

        let unwrappedEntry = try XCTUnwrap(entry)
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Calendar(identifier: .iso8601)
        dateFormatter.timeZone = testTimeZone
        dateFormatter.locale = Locale(identifier: "fi_FI")
        dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-10-26, 06:00:00 Greenwichin normaaliaika")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20251026/R/060000/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV2_should_return_listing() async throws {
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "UTC")!
        let originalTimeZone = mockTimeZoneReturningCurrent(testTimeZone)
        defer { restoreTimeZone(originalTimeZone) }

        let pmdTxtContent = """
       500120 /U/0/20250730/R/101010/ACC0.REC
       500103 /U/0/20250730/R/101010/ACC1.REC
       102325 /U/0/20250730/R/101010/ACC2.REC
       500000 /U/0/20250730/R/101010/HR0.REC
       500050 /U/0/20250730/R/101010/HR1.REC
       300 /U/0/20250730/R/101010/PPG0.REC
       """.trimmingCharacters(in: .controlCharacters).data(using: .utf8)!

        mockClient.requestReturnValues = [
            .success(pmdTxtContent),
            .success(pmdTxtContent),
            .success(pmdTxtContent)
        ]

        // Act
        let entries = try await collectAll(api.listOfflineRecordings("123456"))

        // Assert
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Calendar(identifier: .iso8601)
        dateFormatter.timeZone = testTimeZone
        dateFormatter.locale = Locale(identifier: "fi_FI")
        dateFormatter.dateFormat = "yyyy-MM-dd, HH:mm:ss zzzz"

        let acc = entries.first { $0.type == .acc }
        XCTAssertEqual(dateFormatter.string(from: acc!.date), "2025-07-30, 10:10:10 Greenwichin normaaliaika")
        XCTAssertEqual(acc?.path, "/U/0/20250730/R/101010/ACC0.REC")
        XCTAssertEqual(acc?.size, 1102548)

        let hr = entries.first { $0.type == .hr }
        XCTAssertEqual(dateFormatter.string(from: hr!.date), "2025-07-30, 10:10:10 Greenwichin normaaliaika")
        XCTAssertEqual(hr?.path, "/U/0/20250730/R/101010/HR0.REC")
        XCTAssertEqual(hr?.size, 1000050)

        let ppg = entries.first { $0.type == .ppg }
        XCTAssertEqual(dateFormatter.string(from: ppg!.date), "2025-07-30, 10:10:10 Greenwichin normaaliaika")
        XCTAssertEqual(ppg?.path, "/U/0/20250730/R/101010/PPG0.REC")
        XCTAssertEqual(ppg?.size, 300)
    }

    // MARK: - moved from PolarBleApiImplTests

    // MARK: requestOfflineRecordingSettings

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
        setUpPmdApi(); pmdApi.pmdServiceUtils.stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) })
    }

    // MARK: requestFullOfflineRecordingSettings

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
        setUpPmdApi(); pmdApi.pmdServiceUtils.stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) })
    }

    // MARK: getAvailableOfflineRecordingDataTypes

    func test_getAvailableOfflineRecordingDataTypes_emptyFeatureSet_returnsEmptySet() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set<PmdMeasurementType>())
        XCTAssertTrue(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }.isEmpty)
    }

    func test_getAvailableOfflineRecordingDataTypes_ecg_mappedCorrectly() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.ecg]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.ecg])
    }

    func test_getAvailableOfflineRecordingDataTypes_acc_mappedCorrectly() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.acc]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.acc])
    }

    func test_getAvailableOfflineRecordingDataTypes_ppg_mappedCorrectly() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.ppg]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.ppg])
    }

    func test_getAvailableOfflineRecordingDataTypes_ppi_mappedCorrectly() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.ppi]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.ppi])
    }

    func test_getAvailableOfflineRecordingDataTypes_gyro_mappedCorrectly() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.gyro]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.gyro])
    }

    func test_getAvailableOfflineRecordingDataTypes_mgn_mappedToMagnetometer() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.mgn]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.magnetometer])
    }

    func test_getAvailableOfflineRecordingDataTypes_offlineHr_mappedToHr() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.offline_hr]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.hr])
    }

    func test_getAvailableOfflineRecordingDataTypes_temperature_mappedCorrectly() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.temperature]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.temperature])
    }

    func test_getAvailableOfflineRecordingDataTypes_skinTemperature_mappedCorrectly() throws {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.skinTemperature]))
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
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .success(Set<PmdMeasurementType>())
        _ = try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }
        XCTAssertEqual(mockPmdClient.readFeatureCalls.first, true)
    }

    func test_getAvailableOfflineRecordingDataTypes_readFeatureError_propagatesError() {
        setUpPmdApi(); mockPmdClient.readFeatureReturnValue = .failure(NSError(domain: "pmd.readFeature", code: 5))
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) })
    }

    func test_getAvailableOfflineRecordingDataTypes_sessionNotReady_propagatesError() {
        setUpPmdApi(); pmdApi.pmdServiceUtils.stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) })
    }

    // MARK: getOfflineRecordingStatus

    func test_getOfflineRecordingStatus_emptyStatus_returnsEmptyDictionary() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([])
        XCTAssertTrue(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }.isEmpty)
    }

    func test_getOfflineRecordingStatus_offlineMeasurementActive_returnsTrueForFeature() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([(.ecg, .offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.ecg], true)
    }

    func test_getOfflineRecordingStatus_onlineOfflineMeasurementActive_returnsTrueForFeature() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([(.acc, .online_offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.acc], true)
    }

    func test_getOfflineRecordingStatus_noMeasurementActive_returnsFalseForFeature() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([(.ppg, .no_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.ppg], false)
    }

    func test_getOfflineRecordingStatus_onlineMeasurementActive_returnsFalseForFeature() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([(.gyro, .online_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.gyro], false)
    }

    func test_getOfflineRecordingStatus_mgn_mappedToMagnetometer() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([(.mgn, .offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.magnetometer], true)
    }

    func test_getOfflineRecordingStatus_offlineHr_mappedToHr() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([(.offline_hr, .offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.hr], true)
    }

    func test_getOfflineRecordingStatus_temperature_mappedCorrectly() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([(.temperature, .offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.temperature], true)
    }

    func test_getOfflineRecordingStatus_pressure_mappedCorrectly() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([(.pressure, .no_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.pressure], false)
    }

    func test_getOfflineRecordingStatus_multipleFeatures_allMappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([
            (.ecg, .offline_measurement_active),
            (.acc, .no_measurement_active),
            (.ppg, .online_offline_measurement_active),
            (.gyro, .online_measurement_active),
        ])
        let result = try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }
        XCTAssertEqual(result[.ecg], true); XCTAssertEqual(result[.acc], false)
        XCTAssertEqual(result[.ppg], true); XCTAssertEqual(result[.gyro], false)
    }

    func test_getOfflineRecordingStatus_unmappablePmdType_propagatesError() {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([(.unknown_type, .offline_measurement_active)])
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) })
    }

    func test_getOfflineRecordingStatus_readMeasurementStatusError_propagatesError() {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .failure(NSError(domain: "pmd.status", code: 8))
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) })
    }

    func test_getOfflineRecordingStatus_sessionNotReady_propagatesError() {
        setUpPmdApi(); pmdApi.pmdServiceUtils.stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) })
    }

    func test_getOfflineRecordingStatus_callsReadMeasurementStatus() throws {
        setUpPmdApi(); mockPmdClient.readMeasurementStatusReturnValue = .success([])
        _ = try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }
        XCTAssertEqual(mockPmdClient.readMeasurementStatusCalls, 1)
    }

    // MARK: getOfflineRecord / split / remove / list

    func test_getOfflineRecord_h10_returnsOperationNotSupported() {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        let error = awaitErrorAsync { [self] in try await h10Api.getOfflineRecord(deviceId, entry: entry, secret: nil) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    func test_getOfflineRecord_missingDataInSubrecording_throwsPolarOfflineRecordingError() {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/U/0/20240615/R/103000/": { throw BlePsFtpException.responseError(errorCode: 103) },
            "/U/0/20240615/R/103000/ACC0.REC": { Data() },
        ])
        let error = awaitErrorAsync { [self] in try await v2Api.getOfflineRecord(deviceId, entry: entry, secret: nil) }
        XCTAssertNotNil(error)
        if case let PolarErrors.polarOfflineRecordingError(description) = error! {
            XCTAssertTrue(description.contains("Invalid data"))
        } else {
            XCTFail("Expected polarOfflineRecordingError, got \(String(describing: error))")
        }
    }

    func test_getOfflineRecordWithProgress_h10_returnsOperationNotSupported() {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        var receivedError: Error?
        let exp = XCTestExpectation(description: "getOfflineRecordWithProgress unsupported")
        Task {
            do {
                for try await _ in h10Api.getOfflineRecordWithProgress(deviceId, entry: entry, secret: nil) { }
            } catch {
                receivedError = error
            }
            exp.fulfill()
        }
        wait(for: [exp], timeout: 2)
        XCTAssertNotNil(receivedError)
        if case PolarErrors.operationNotSupported = receivedError! { } else { XCTFail("Expected operationNotSupported") }
    }

    func test_getOfflineRecordWithProgress_emitsInitialProgress_thenFailsIfParsingFails() {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/U/0/20240615/R/103000/": { throw BlePsFtpException.responseError(errorCode: 103) },
            "/U/0/20240615/R/103000/ACC0.REC": { Data() },
        ])
        var received: [PolarOfflineRecordingResult] = []
        var receivedError: Error?
        let exp = XCTestExpectation(description: "getOfflineRecordWithProgress parse fail")
        Task {
            do {
                for try await item in v2Api.getOfflineRecordWithProgress(deviceId, entry: entry, secret: nil) {
                    received.append(item)
                }
            } catch {
                receivedError = error
            }
            exp.fulfill()
        }
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(received.count, 1)
        if case let .progress(progress) = received.first {
            XCTAssertEqual(progress.bytesDownloaded, 0)
            XCTAssertEqual(progress.totalBytes, 100)
            XCTAssertEqual(progress.progressPercent, 0)
        } else {
            XCTFail("Expected initial .progress event")
        }
        XCTAssertNotNil(receivedError)
    }

    func test_getSubRecordingCount_countsMatchingTypeFiles() throws {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/U/0/20240615/R/103000/": {
                try self.makeDirectoryProtoData(entries: [
                    ("ACC0.REC", 10),
                    ("ACC1.REC", 20),
                    ("GYRO0.REC", 30),
                ])
            },
        ])
        let count = try awaitSingleAsync { [self] in try await v2Api.getSubRecordingCount(identifier: deviceId, entry: entry) }
        XCTAssertEqual(count, 2)
    }

    func test_getSubRecordingCount_directoryNotFound_returnsZero() throws {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        v2MockClient.requestReturnValue = .failure(BlePsFtpException.responseError(errorCode: 103))
        let count = try awaitSingleAsync { [self] in try await v2Api.getSubRecordingCount(identifier: deviceId, entry: entry) }
        XCTAssertEqual(count, 0)
    }

    func test_listSplitOfflineRecordings_v2Path_validDirectoryStructure_emitsEntry() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/U/0/": { try self.makeDirectoryProtoData(entries: [("20240615/", 0)]) },
            "/U/0/20240615/": { try self.makeDirectoryProtoData(entries: [("R/", 0)]) },
            "/U/0/20240615/R/": { try self.makeDirectoryProtoData(entries: [("103000/", 0)]) },
            "/U/0/20240615/R/103000/": { try self.makeDirectoryProtoData(entries: [("ACC0.REC", 2048)]) },
        ])
        let entries = try collectAllAsync(v2Api.listSplitOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 1)
        XCTAssertEqual(entries.first?.type, .acc)
        XCTAssertEqual(entries.first?.size, 2048)
    }

    func test_listSplitOfflineRecordings_h10_returnsOperationNotSupported() {
        var receivedError: Error?
        let exp = XCTestExpectation(description: "listSplitOfflineRecordings unsupported")
        Task {
            do {
                for try await _ in h10Api.listSplitOfflineRecordings(deviceId) { }
            } catch {
                receivedError = error
            }
            exp.fulfill()
        }
        wait(for: [exp], timeout: 2)
        XCTAssertNotNil(receivedError)
        if case PolarErrors.operationNotSupported = receivedError! { } else { XCTFail("Expected operationNotSupported") }
    }

    func test_getSplitOfflineRecord_h10_returnsOperationNotSupported() {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        let error = awaitErrorAsync { [self] in try await h10Api.getSplitOfflineRecord(deviceId, entry: entry, secret: nil) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    func test_getSplitOfflineRecord_requestsEntryPath_beforeParseFailure() {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        v2MockClient.requestReturnValue = .success(Data())
        let error = awaitErrorAsync { [self] in try await v2Api.getSplitOfflineRecord(deviceId, entry: entry, secret: nil) }
        XCTAssertNotNil(error)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let op = try? Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(op?.path, entry.path)
    }

    func test_removeOfflineRecord_h10_returnsOperationNotSupported() {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        let error = awaitErrorAsync { [self] in try await h10Api.removeOfflineRecord(deviceId, entry: entry) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    func test_removeOfflineRecord_removesSubrecordings_andDeletesEmptyParentDirectories() throws {
        let entry = PolarOfflineRecordingEntry(path: "/U/0/20240615/R/103000/ACC0.REC", size: 100, date: Date(), type: .acc)
        let get = Protocol_PbPFtpOperation.Command.get.rawValue
        let remove = Protocol_PbPFtpOperation.Command.remove.rawValue
        v2MockClient.requestReturnValueClosure = makeCommandAwareRequestClosure([
            "\(get)|/U/0/20240615/R/103000/": [
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
        try awaitVoidAsync { [self] in try await v2Api.removeOfflineRecord(deviceId, entry: entry) }
        let removedPaths = v2MockClient.requestCalls.compactMap { data -> String? in
            guard let op = try? Protocol_PbPFtpOperation(serializedBytes: data), op.command == .remove else { return nil }
            return op.path
        }
        XCTAssertTrue(removedPaths.contains("/U/0/20240615/R/103000/ACC0.REC"))
        XCTAssertTrue(removedPaths.contains("/U/0/20240615/R/103000/ACC1.REC"))
        XCTAssertTrue(removedPaths.contains("/U/0/20240615/R/103000/"))
        XCTAssertTrue(removedPaths.contains("/U/0/20240615/R/"))
        XCTAssertTrue(removedPaths.contains("/U/0/20240615/"))
    }

    func test_listOfflineRecordings_sessionNotReady_propagatesError() {
        XCTAssertNotNil(awaitErrorAsync { [self] in
            for try await _ in MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession).listOfflineRecordings(self.deviceId) {}
        })
    }

    func test_listOfflineRecordings_v2Path_singleEntry_emitsEntry() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 1024, path: "/U/0/20240615/R/103000/ACC.REC")]) },
        ])
        let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 1); XCTAssertEqual(entries.first?.type, .acc)
    }

    func test_listOfflineRecordings_v2Path_multipleEntries_allEmitted() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [
                (size: 1024, path: "/U/0/20240615/R/103000/ACC.REC"),
                (size: 2048, path: "/U/0/20240615/R/103000/GYRO.REC"),
            ]) },
        ])
        let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 2)
        let types = Set(entries.map { $0.type })
        XCTAssertTrue(types.contains(.acc)); XCTAssertTrue(types.contains(.gyro))
    }

    func test_listOfflineRecordings_v2Path_zeroSizeEntry_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 0, path: "/U/0/20240615/R/103000/ACC.REC")]) },
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v2Path_invalidPathTooFewComponents_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 1024, path: "/U/0/20240615/ACC.REC")]) },
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v2Path_unknownFileType_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 1024, path: "/U/0/20240615/R/103000/UNKNOWN.REC")]) },
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v2Path_entryTypesMappedCorrectly() throws {
        let cases: [(file: String, expected: PolarDeviceDataType)] = [
            ("ACC.REC", .acc), ("GYRO.REC", .gyro), ("MAGNETOMETER.REC", .magnetometer),
            ("PPG.REC", .ppg), ("PPI.REC", .ppi), ("HR.REC", .hr),
            ("TEMP.REC", .temperature), ("SKINTEMP.REC", .skinTemperature),
        ]
        for (fileName, expectedType) in cases {
            v2MockClient.requestReturnValueClosure = makeRequestClosure([
                "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 512, path: "/U/0/20240615/R/103000/\(fileName)")]) },
            ])
            let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
            XCTAssertEqual(entries.count, 1, "\(fileName) should produce one entry")
            XCTAssertEqual(entries.first?.type, expectedType, "\(fileName) -> \(expectedType)")
        }
    }

    func test_listOfflineRecordings_v2Path_dateAndSizeParsedCorrectly() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 4096, path: "/U/0/20240615/R/103000/ACC.REC")]) },
        ])
        let entry = try XCTUnwrap(collectAllAsync(v2Api.listOfflineRecordings(deviceId)).first)
        XCTAssertEqual(entry.size, 4096)
        let comps = Calendar(identifier: .gregorian).dateComponents(in: TimeZone(secondsFromGMT: 0)!, from: entry.date)
        XCTAssertEqual(comps.year, 2024); XCTAssertEqual(comps.month, 6); XCTAssertEqual(comps.day, 15)
    }

    func test_listOfflineRecordings_v2Path_emptyPmdFileTxt_fallsBackToV1WithNoEntries() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { Data() },
            "/U/0/": { try self.makeDirectoryProtoData(entries: []) },
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v1Path_validDirectoryStructure_emitsEntry() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { Data() },
            "/U/0/": { try self.makeDirectoryProtoData(entries: [("20240615/", 0)]) },
            "/U/0/20240615/": { try self.makeDirectoryProtoData(entries: [("R/", 0)]) },
            "/U/0/20240615/R/": { try self.makeDirectoryProtoData(entries: [("103000/", 0)]) },
            "/U/0/20240615/R/103000/": { try self.makeDirectoryProtoData(entries: [("ACC.REC", 2048)]) },
        ])
        let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 1); XCTAssertEqual(entries.first?.type, .acc)
        XCTAssertEqual(entries.first?.size, 2048)
    }

    func test_listOfflineRecordings_v1Path_zeroSizeFile_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { Data() },
            "/U/0/": { try self.makeDirectoryProtoData(entries: [("20240615/", 0)]) },
            "/U/0/20240615/": { try self.makeDirectoryProtoData(entries: [("R/", 0)]) },
            "/U/0/20240615/R/": { try self.makeDirectoryProtoData(entries: [("103000/", 0)]) },
            "/U/0/20240615/R/103000/": { try self.makeDirectoryProtoData(entries: [("ACC.REC", 0)]) },
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }
}
