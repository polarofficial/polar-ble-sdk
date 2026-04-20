//  Copyright © 2026 Polar. All rights reserved.

import XCTest
import RxSwift
import RxTest
@testable import PolarBleSdk

class PolarTestUtilsTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyyMMdd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockGattServiceTransmitterImpl())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    // MARK: - Success: full proto

    func testReadSpo2TestFromDayDirectory_Success_AllFields() throws {
        // Arrange
        let date = makeDate(year: 2026, month: 4, day: 13)
        let proto = buildSpo2TestProto()
        mockClient.requestReturnValues = try makeDirThenFileSingles(proto: proto)

        // Act
        let result = try awaitFirst(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: date))

        // Assert – all non-zero/non-empty fields should be mapped
        XCTAssertEqual(result.bloodOxygenPercent, 97)
        XCTAssertEqual(result.testStatus, .passed)
        XCTAssertEqual(result.spo2Class, .normal)
        XCTAssertEqual(result.averageHeartRateBpm, 65)
        XCTAssertEqual(result.recordingDevice, "Polar Vantage V3")
        XCTAssertEqual(result.spo2ValueDeviationFromBaseline, .usual)
        XCTAssertEqual(result.spo2HrvDeviationFromBaseline, .noBaseline)
        XCTAssertNil(result.triggerType)  // triggerType is not in the proto
        XCTAssertEqual(result.timeZoneOffsetMinutes, 120)
        XCTAssertEqual(result.spo2QualityAveragePercent ?? 0, Float(90.0), accuracy: 0.001)
        XCTAssertEqual(result.heartRateVariabilityMs ?? 0, 45.5, accuracy: 0.001)
        XCTAssertEqual(result.altitudeMeters ?? 0, 100.0, accuracy: 0.001)
    }

    // MARK: - Success: date from testTime proto field (UInt64 ms since epoch)

    func testReadSpo2TestFromDayDirectory_Success_UsesTestTimeWhenPresent() throws {
        // Arrange – testTime = ms since epoch for 2026-04-13 14:25:07 UTC
        // 1744554307000 ms = 2026-04-13T14:25:07Z
        let fallbackDate = makeDate(year: 2026, month: 4, day: 13)
        var proto = Data_PbSpo2TestResult()
        proto.bloodOxygenPercent = 95
        proto.testTime = 1744554307000  // 2026-04-13 14:25:07 UTC
        mockClient.requestReturnValues = try makeDirThenFileSingles(proto: proto)

        // Act
        let result = try awaitFirst(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: fallbackDate)
        )

        // Assert – result date should not equal fallback (testTime was decoded)
        XCTAssertNotEqual(result.date, fallbackDate)
        // The date should be in the right ballpark (2026-04-13)
        let components = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: result.date)
        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 4)
        XCTAssertEqual(components.day, 13)
    }

    // MARK: - Success: fallback date used when testTime is zero

    func testReadSpo2TestFromDayDirectory_Success_FallsBackToDateWhenTestTimeAbsent() throws {
        // Arrange – testTime left at default 0
        let fallbackDate = makeDate(year: 2026, month: 4, day: 13)
        var proto = Data_PbSpo2TestResult()
        proto.bloodOxygenPercent = 95
        // testTime == 0 → treated as absent
        mockClient.requestReturnValues = try makeDirThenFileSingles(proto: proto)

        // Act
        let result = try awaitFirst(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: fallbackDate)
        )

        // Assert – when folder name is "142507" (from makeDirThenFileSingles default), date should be parsed from folder
        // but since we used fallbackDate as dayDate the year/month/day should match
        let components = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: result.date)
        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 4)
        XCTAssertEqual(components.day, 13)
    }

    // MARK: - triggerType is always nil (not in proto)

    func testReadSpo2TestFromDayDirectory_Success_ZeroValuesMapToNil() throws {
        // Arrange – empty proto: all numeric fields are zero, strings empty
        let proto = Data_PbSpo2TestResult()
        mockClient.requestReturnValues = try makeDirThenFileSingles(proto: proto)

        // Act
        let result = try awaitFirst(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date())
        )

        // Assert – optional fields should be nil when proto value is zero / empty
        XCTAssertNil(result.recordingDevice)
        XCTAssertNil(result.timeZoneOffsetMinutes)
        XCTAssertNil(result.bloodOxygenPercent)
        XCTAssertNil(result.spo2QualityAveragePercent)
        XCTAssertNil(result.averageHeartRateBpm)
        XCTAssertNil(result.heartRateVariabilityMs)
        XCTAssertNil(result.altitudeMeters)
    }

    // MARK: - Success: correct paths are requested

    func testReadSpo2TestFromDayDirectory_CorrectPathsRequested() throws {
        // Arrange
        let date = makeDate(year: 2026, month: 4, day: 13)
        let proto = buildSpo2TestProto()
        mockClient.requestReturnValues = try makeDirThenFileSingles(proto: proto, subDirName: "142507")

        _ = try awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: date)
        )

        // Assert – first call lists the SPO2TEST directory, second fetches the file
        XCTAssertEqual(mockClient.requestCalls.count, 2)

        let firstOp = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
        XCTAssertEqual(firstOp.path, "/U/0/20260413/SPO2TEST/")

        let secondOp = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[1])
        XCTAssertEqual(secondOp.path, "/U/0/20260413/SPO2TEST/142507/SPO2TRES.BPB")
    }

    // MARK: - Empty directory (no subdirectory entries) → completes empty

    func testReadSpo2TestFromDayDirectory_EmptyDirectory_CompletesEmpty() throws {
        // Arrange – SPO2TEST/ directory exists but has no entries
        var emptyDir = Protocol_PbPFtpDirectory()
        emptyDir.entries = []
        mockClient.requestReturnValues = [Single.just(try emptyDir.serializedData())]

        // Act & Assert
        let results = try awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date())
        )
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - Directory has only file entries (no subdirectory) → completes empty

    func testReadSpo2TestFromDayDirectory_DirectoryHasNoSubdirs_CompletesEmpty() throws {
        // Arrange – entry without trailing slash is a file, not a subdir
        var dir = Protocol_PbPFtpDirectory()
        var fileEntry = Protocol_PbPFtpEntry()
        fileEntry.name = "SPO2TRES.BPB"   // no trailing slash
        fileEntry.size = 0
        dir.entries = [fileEntry]
        mockClient.requestReturnValues = [Single.just(try dir.serializedData())]

        // Act & Assert
        let results = try awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date())
        )
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - Directory listing fails → completes empty

    func testReadSpo2TestFromDayDirectory_DirectoryListingFails_CompletesEmpty() {
        // Arrange – simulate device error 103 (file/directory not found)
        mockClient.requestReturnValues = [
            Single.error(NSError(domain: "BLE", code: 103, userInfo: nil))
        ]

        let results = try! awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date())
        )
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - File fetch fails after directory found → completes empty

    func testReadSpo2TestFromDayDirectory_FileFetchFails_CompletesEmpty() throws {
        // Arrange – directory listing succeeds, but file request errors
        var dir = Protocol_PbPFtpDirectory()
        var entry = Protocol_PbPFtpEntry()
        entry.name = "142507/"
        entry.size = 0
        dir.entries = [entry]

        mockClient.requestReturnValues = [
            Single.just(try dir.serializedData()),
            Single.error(NSError(domain: "BLE", code: 103, userInfo: nil))
        ]

        // Act & Assert
        let results = try awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date())
        )
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - Malformed file data → completes empty

    func testReadSpo2TestFromDayDirectory_MalformedFileData_CompletesEmpty() throws {
        // Arrange – directory listing succeeds but the proto bytes are garbage
        var dir = Protocol_PbPFtpDirectory()
        var entry = Protocol_PbPFtpEntry()
        entry.name = "142507/"
        entry.size = 0
        dir.entries = [entry]

        mockClient.requestReturnValues = [
            Single.just(try dir.serializedData()),
            Single.just(Data([0xFF, 0xFE, 0xFD]))
        ]

        // Act & Assert
        let results = try awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date())
        )
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - Multiple subdirectories → all are returned

    func testReadSpo2TestFromDayDirectory_MultipleSubdirs_ReturnsAll() throws {
        // Arrange – two time subdirectories; both should be read and returned
        var dir = Protocol_PbPFtpDirectory()
        var entry1 = Protocol_PbPFtpEntry(); entry1.name = "093635/"; entry1.size = 0
        var entry2 = Protocol_PbPFtpEntry(); entry2.name = "093751/"; entry2.size = 0
        dir.entries = [entry1, entry2]

        var proto1 = buildSpo2TestProto()
        proto1.bloodOxygenPercent = 97

        var proto2 = buildSpo2TestProto()
        proto2.bloodOxygenPercent = 95

        mockClient.requestReturnValues = [
            Single.just(try dir.serializedData()),
            Single.just(try proto1.serializedData()),
            Single.just(try proto2.serializedData())
        ]

        let date = makeDate(year: 2026, month: 4, day: 13)
        let results = try awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: date)
        )

        // Both subdirectories should produce a result
        XCTAssertEqual(results.count, 2)
        let oxygenValues = Set(results.compactMap { $0.bloodOxygenPercent })
        XCTAssertEqual(oxygenValues, Set([97, 95]))

        // Both file paths should have been requested
        XCTAssertEqual(mockClient.requestCalls.count, 3)
        let paths = try mockClient.requestCalls.dropFirst().map {
            try Protocol_PbPFtpOperation(serializedBytes: $0).path
        }
        XCTAssertTrue(paths.contains("/U/0/20260413/SPO2TEST/093635/SPO2TRES.BPB"))
        XCTAssertTrue(paths.contains("/U/0/20260413/SPO2TEST/093751/SPO2TRES.BPB"))
    }

    // MARK: - Enum mapping: Spo2Class

    func testSpo2ClassMapping() {
        XCTAssertEqual(PolarSpo2TestData.Spo2Class(rawValue: 0), .unknown)
        XCTAssertEqual(PolarSpo2TestData.Spo2Class(rawValue: 1), .veryLow)
        XCTAssertEqual(PolarSpo2TestData.Spo2Class(rawValue: 2), .low)
        XCTAssertEqual(PolarSpo2TestData.Spo2Class(rawValue: 3), .normal)
        XCTAssertNil(PolarSpo2TestData.Spo2Class(rawValue: 99))
    }

    // MARK: - Enum mapping: Spo2TestStatus

    func testSpo2TestStatusMapping() {
        XCTAssertEqual(PolarSpo2TestData.Spo2TestStatus(rawValue: 0), .passed)
        XCTAssertEqual(PolarSpo2TestData.Spo2TestStatus(rawValue: 1), .inconclusiveTooLowQualityInSamples)
        XCTAssertEqual(PolarSpo2TestData.Spo2TestStatus(rawValue: 2), .inconclusiveTooLowOverallQuality)
        XCTAssertEqual(PolarSpo2TestData.Spo2TestStatus(rawValue: 3), .inconclusiveTooManyMissingSamples)
        XCTAssertNil(PolarSpo2TestData.Spo2TestStatus(rawValue: 99))
    }

    // MARK: - Enum mapping: DeviationFromBaseline

    func testDeviationFromBaselineMapping() {
        XCTAssertEqual(PolarSpo2TestData.DeviationFromBaseline(rawValue: 0), .noBaseline)
        XCTAssertEqual(PolarSpo2TestData.DeviationFromBaseline(rawValue: 1), .belowUsual)
        XCTAssertEqual(PolarSpo2TestData.DeviationFromBaseline(rawValue: 2), .usual)
        XCTAssertEqual(PolarSpo2TestData.DeviationFromBaseline(rawValue: 3), .aboveUsual)
        XCTAssertNil(PolarSpo2TestData.DeviationFromBaseline(rawValue: 99))
    }

    // MARK: - Enum mapping: Spo2TestTriggerType

    func testTriggerTypeMapping() {
        XCTAssertEqual(PolarSpo2TestData.Spo2TestTriggerType(rawValue: 0), .manual)
        XCTAssertEqual(PolarSpo2TestData.Spo2TestTriggerType(rawValue: 1), .automatic)
        XCTAssertNil(PolarSpo2TestData.Spo2TestTriggerType(rawValue: 99))
    }

    // MARK: - Enum mapping: each Spo2TestStatus value round-trips from proto

    func testAllSpo2TestStatuses_RoundTripFromProto() throws {
        let statuses: [(Data_PbSpo2TestStatus, PolarSpo2TestData.Spo2TestStatus)] = [
            (.spo2TestPassed, .passed),
            (.spo2TestInconclusiveTooLowQualityInSamples, .inconclusiveTooLowQualityInSamples),
            (.spo2TestInconclusiveTooLowOverallQuality, .inconclusiveTooLowOverallQuality),
            (.spo2TestInconclusiveTooManyMissingSamples, .inconclusiveTooManyMissingSamples)
        ]
        for (protoStatus, expected) in statuses {
            var proto = Data_PbSpo2TestResult()
            proto.testStatus = protoStatus
            proto.bloodOxygenPercent = 95
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockGattServiceTransmitterImpl())
            mockClient.requestReturnValues = try makeDirThenFileSingles(proto: proto)

            let result = try awaitFirst(
                PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date())
            )
            XCTAssertEqual(result.testStatus, expected, "testStatus mismatch for \(protoStatus)")
        }
    }

    // MARK: - Enum mapping: each Spo2Class value round-trips from proto

    func testAllSpo2Classes_RoundTripFromProto() throws {
        let classes: [(Data_PbSpo2Class, PolarSpo2TestData.Spo2Class)] = [
            (.spo2ClassUnknown, .unknown),
            (.spo2ClassVeryLow, .veryLow),
            (.spo2ClassLow, .low),
            (.spo2ClassNormal, .normal)
        ]
        for (protoClass, expected) in classes {
            var proto = Data_PbSpo2TestResult()
            proto.spo2Class = protoClass
            proto.bloodOxygenPercent = 95
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockGattServiceTransmitterImpl())
            mockClient.requestReturnValues = try makeDirThenFileSingles(proto: proto)

            let result = try awaitFirst(
                PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date())
            )
            XCTAssertEqual(result.spo2Class, expected, "spo2Class mismatch for \(protoClass)")
        }
    }

    // MARK: - Helpers

    /// Build a fully-populated SPO2 test proto using the real generated field names.
    private func buildSpo2TestProto() -> Data_PbSpo2TestResult {
        var proto = Data_PbSpo2TestResult()
        proto.recordingDevice = "Polar Vantage V3"
        proto.testStatus = .spo2TestPassed
        proto.bloodOxygenPercent = 97
        proto.spo2Class = .spo2ClassNormal
        proto.spo2ValueDeviationFromBaseline = .deviationUsual
        proto.spo2HrvDeviationFromBaseline = .deviationNoBaseline
        proto.spo2QualityAveragePercent = 90.0
        proto.averageHeartRateBpm = 65
        proto.heartRateVariabilityMs = 45.5
        proto.altitudeMeters = 100.0
        proto.timeZoneOffset = 120
        proto.testTime = 1744574400000  // 2026-04-14 00:00:00 UTC
        return proto
    }

    /// Build [directorySingle, fileSingle] to feed requestReturnValues.
    private func makeDirThenFileSingles(
        proto: Data_PbSpo2TestResult,
        subDirName: String = "142507"
    ) throws -> [Single<Data>] {
        var dir = Protocol_PbPFtpDirectory()
        var entry = Protocol_PbPFtpEntry()
        entry.name = "\(subDirName)/"
        entry.size = 0
        dir.entries = [entry]
        return [
            Single.just(try dir.serializedData()),
            Single.just(try proto.serializedData())
        ]
    }

    /// Convenience: collect all emitted values from an Observable.
    @discardableResult
    private func awaitAll<T>(_ observable: Observable<T>, timeout: TimeInterval = 2.0) throws -> [T] {
        var results: [T] = []
        var capturedError: Error?
        let exp = expectation(description: "awaitAll")
        let disposable = observable.subscribe(
            onNext: { results.append($0) },
            onError: { capturedError = $0; exp.fulfill() },
            onCompleted: { exp.fulfill() }
        )
        wait(for: [exp], timeout: timeout)
        disposable.dispose()
        if let error = capturedError { throw error }
        return results
    }

    /// Convenience: assert the Observable emits at least one value and return the first.
    @discardableResult
    private func awaitFirst<T>(_ observable: Observable<T>, timeout: TimeInterval = 2.0) throws -> T {
        let results = try awaitAll(observable, timeout: timeout)
        return try XCTUnwrap(results.first, "Expected at least one emitted value")
    }

    /// Create a Date at midnight UTC for a given year/month/day.
    private func makeDate(year: Int, month: Int, day: Int) -> Date {
        var c = DateComponents()
        c.year = year; c.month = month; c.day = day
        c.hour = 0; c.minute = 0; c.second = 0
        return Calendar(identifier: .gregorian).date(from: c)!
    }
}

