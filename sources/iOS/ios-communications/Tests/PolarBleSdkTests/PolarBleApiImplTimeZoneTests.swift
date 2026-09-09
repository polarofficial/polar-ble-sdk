// Copyright © 2026 Polar Electro Oy. All rights reserved.

/// Time zone correctness tests
///
/// These tests verify that data is fetched from the correct device file-system paths
/// regardless of the timezone the test (or user device) is running in.
///
/// All SDK utils (PolarActivityUtils, PolarSleepUtils, PolarNightlyRechargeUtils, etc.)
/// build device paths using a UTC-based DateFormatter:
///   "/U/0/yyyyMMdd/..."
///
/// The API contract therefore requires that callers pass dates whose UTC representation
/// matches the desired calendar date. Concretely:
///   - A UTC midnight date  →  always yields the correct path on every machine.
///   - A local midnight date in UTC+N  →  its UTC representation is the *previous* calendar
///     day, so the path will target the previous UTC day.
///   - A local midnight date in UTC-N  →  its UTC representation is still on the same
///     calendar day (as long as N < 24), so the path is correct.
///
/// The tests below verify all three scenarios for each API method.

import XCTest
import CoreBluetooth
@testable import PolarBleSdk

final class PolarBleApiImplTimeZoneTests: XCTestCase {

    // MARK: - Setup

    private let deviceId = "TZTEST01"
    private var mockClient: MockBlePsFtpClient!
    private var mockSession: MockBleDeviceSession!
    private var api: PolarBleApiImplWithMockSession!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
    }

    override func tearDownWithError() throws {
        api = nil
        mockSession = nil
        mockClient = nil
    }

    // MARK: - Helpers

    /// Returns a Date representing midnight (00:00:00) on the given calendar date in the given timezone.
    private func makeDate(year: Int, month: Int, day: Int, timeZone: TimeZone) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let comps = DateComponents(
            timeZone: timeZone,
            year: year, month: month, day: day,
            hour: 0, minute: 0, second: 0, nanosecond: 0
        )
        return cal.date(from: comps)!
    }

    private var utc: TimeZone { TimeZone(identifier: "UTC")! }

    /// UTC+10 — Australia/Sydney (no DST ambiguity for the fixed dates used in these tests)
    private var utcPlusTen: TimeZone { TimeZone(identifier: "Australia/Brisbane")! }

    /// UTC-5 — America/New_York standard time offset
    private var utcMinusFive: TimeZone { TimeZone(identifier: "America/New_York")! }

    /// Decode the PSFTP path from a raw serialised `Protocol_PbPFtpOperation`.
    private func decodePath(from requestData: Data) throws -> String {
        return try Protocol_PbPFtpOperation(serializedBytes: requestData).path
    }

    /// Empty `Protocol_PbPFtpDirectory` response — makes list-based APIs short-circuit cleanly.
    private func emptyDirectoryData() throws -> Data {
        return try Protocol_PbPFtpDirectory().serializedData()
    }

    /// Minimal valid `Data_PbDailySummary` response.
    private func makeDailySummary(steps: UInt32 = 0, distance: Float = 0, calories: UInt32 = 0) throws -> Data {
        var proto = Data_PbDailySummary()
        proto.activityCalories = calories
        proto.activityDistance = distance
        proto.date = PbDate.with { $0.year = 2024; $0.month = 1; $0.day = 15 }
        return try proto.serializedData()
    }

    /// Minimal valid `Data_PbActivitySamples` with step counts.
    private func makeActivitySamples(steps: [UInt32]) throws -> Data {
        var proto = Data_PbActivitySamples()
        proto.stepsSamples = steps
        return try proto.serializedData()
    }

    // MARK: - getSteps — path correctness

    /// UTC midnight date → path uses the same calendar date as UTC ("20240115").
    func test_getSteps_utcMidnightDate_requestsCorrectUTCPath() async throws {
        let date = makeDate(year: 2024, month: 1, day: 15, timeZone: utc)
        // Return empty directory so the test short-circuits after the listing request.
        mockClient.requestReturnValue = .success(try emptyDirectoryData())

        let result = try await api.getSteps(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(
            path.contains("20240115"),
            "Expected UTC date '20240115' in path; got: \(path)"
        )
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].steps, 0)
    }

    /// UTC+10 midnight date — its UTC representation is the *previous* calendar day.
    /// getSteps uses PolarTimeUtils.utcCalendar.startOfDay, so it requests the UTC day ("20240114").
    func test_getSteps_utcPlusTenMidnightDate_requestsPreviousUTCDayPath() async throws {
        // 2024-01-15 00:00:00 UTC+10 == 2024-01-14 14:00:00 UTC
        let date = makeDate(year: 2024, month: 1, day: 15, timeZone: utcPlusTen)
        mockClient.requestReturnValue = .success(try emptyDirectoryData())

        let _ = try await api.getSteps(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        // utcCalendar.startOfDay(2024-01-14 14:00 UTC) == 2024-01-14 00:00 UTC → "20240114"
        XCTAssertTrue(
            path.contains("20240114"),
            "Expected UTC date '20240114' in path for a UTC+10 local midnight; got: \(path)"
        )
    }

    /// UTC-5 midnight date — its UTC representation is still the same calendar day.
    /// 2024-01-15 00:00:00 UTC-5 == 2024-01-15 05:00:00 UTC → path "20240115".
    func test_getSteps_utcMinusFiveMidnightDate_requestsSameDayUTCPath() async throws {
        // 2024-01-15 00:00:00 UTC-5 (EST) == 2024-01-15 05:00:00 UTC
        let date = makeDate(year: 2024, month: 1, day: 15, timeZone: utcMinusFive)
        mockClient.requestReturnValue = .success(try emptyDirectoryData())

        let _ = try await api.getSteps(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        // utcCalendar.startOfDay(2024-01-15 05:00 UTC) == 2024-01-15 00:00 UTC → "20240115"
        XCTAssertTrue(
            path.contains("20240115"),
            "Expected UTC date '20240115' in path for a UTC-5 local midnight; got: \(path)"
        )
    }

    /// Multi-day UTC range → each path contains the expected consecutive UTC date string.
    func test_getSteps_multiDayUTCRange_requestsConsecutiveUTCPaths() async throws {
        let from = makeDate(year: 2024, month: 1, day: 13, timeZone: utc)
        let to   = makeDate(year: 2024, month: 1, day: 15, timeZone: utc)
        // 3 empty-directory responses (one per day)
        mockClient.requestReturnValues = [
            .success(try emptyDirectoryData()),
            .success(try emptyDirectoryData()),
            .success(try emptyDirectoryData())
        ]

        let result = try await api.getSteps(identifier: deviceId, fromDate: from, toDate: to)

        XCTAssertEqual(mockClient.requestCalls.count, 3, "Expected one listing request per day")
        let paths = try mockClient.requestCalls.map { try decodePath(from: Data($0)) }
        XCTAssertTrue(paths[0].contains("20240113"), "Day 0 path wrong: \(paths[0])")
        XCTAssertTrue(paths[1].contains("20240114"), "Day 1 path wrong: \(paths[1])")
        XCTAssertTrue(paths[2].contains("20240115"), "Day 2 path wrong: \(paths[2])")
        XCTAssertEqual(result.count, 3)
    }

    /// Verify that step values are correctly associated with their request-date even across a
    /// month boundary.
    func test_getSteps_monthBoundary_utcDates_returnsCorrectDataPerDay() async throws {
        let from = makeDate(year: 2024, month: 1, day: 31, timeZone: utc)
        let to   = makeDate(year: 2024, month: 2, day: 1,  timeZone: utc)

        let dir1     = try makeActivityDirectoryData(entries: ["ASAMPL1.BPB"])
        let dir2     = try makeActivityDirectoryData(entries: ["ASAMPL2.BPB"])
        let steps1   = try makeActivitySamples(steps: [3000])
        let steps2   = try makeActivitySamples(steps: [7000])
        mockClient.requestReturnValues = [
            .success(dir1), .success(steps1),
            .success(dir2), .success(steps2)
        ]

        let result = try await api.getSteps(identifier: deviceId, fromDate: from, toDate: to)

        XCTAssertEqual(result.count, 2)
        XCTAssertEqual(result[0].steps, 3000, "Steps on Jan 31 are wrong")
        XCTAssertEqual(result[1].steps, 7000, "Steps on Feb 1 are wrong")

        let paths = try mockClient.requestCalls.map { try decodePath(from: Data($0)) }
        XCTAssertTrue(paths[0].contains("20240131"), "Jan 31 path wrong: \(paths[0])")
        XCTAssertTrue(paths[2].contains("20240201"), "Feb 1 path wrong: \(paths[2])")
    }

    // MARK: - getDistance — path correctness

    /// UTC midnight date → DSUM path contains "20240115".
    func test_getDistance_utcMidnightDate_requestsCorrectUTCPath() async throws {
        let date = makeDate(year: 2024, month: 1, day: 15, timeZone: utc)
        mockClient.requestReturnValue = .success(try makeDailySummary(distance: 1500))

        let result = try await api.getDistance(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(
            path.contains("20240115"),
            "Expected '20240115' in DSUM path; got: \(path)"
        )
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].distanceMeters, 1500, accuracy: 0.1)
    }

    /// UTC+10 midnight → UTC representation is the previous day; path uses "20240114".
    func test_getDistance_utcPlusTenMidnightDate_requestsPreviousUTCDayPath() async throws {
        let date = makeDate(year: 2024, month: 1, day: 15, timeZone: utcPlusTen)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let _ = try await api.getDistance(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(
            path.contains("20240114"),
            "Expected UTC date '20240114' in DSUM path for UTC+10 local midnight; got: \(path)"
        )
    }

    /// UTC-5 midnight → UTC representation is the same day; path uses "20240115".
    func test_getDistance_utcMinusFiveMidnightDate_requestsSameDayUTCPath() async throws {
        let date = makeDate(year: 2024, month: 1, day: 15, timeZone: utcMinusFive)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let _ = try await api.getDistance(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(
            path.contains("20240115"),
            "Expected UTC date '20240115' in DSUM path for UTC-5 local midnight; got: \(path)"
        )
    }

    /// Multi-day UTC range → three consecutive DSUM paths.
    func test_getDistance_multiDayUTCRange_requestsConsecutiveUTCPaths() async throws {
        let from = makeDate(year: 2024, month: 3, day: 1, timeZone: utc)
        let to   = makeDate(year: 2024, month: 3, day: 3, timeZone: utc)
        mockClient.requestReturnValues = [
            .success(try makeDailySummary(distance: 1000)),
            .success(try makeDailySummary(distance: 2000)),
            .success(try makeDailySummary(distance: 3000))
        ]

        let result = try await api.getDistance(identifier: deviceId, fromDate: from, toDate: to)

        XCTAssertEqual(result.count, 3)
        let paths = try mockClient.requestCalls.map { try decodePath(from: Data($0)) }
        XCTAssertTrue(paths[0].contains("20240301"), "Day 0 path wrong: \(paths[0])")
        XCTAssertTrue(paths[1].contains("20240302"), "Day 1 path wrong: \(paths[1])")
        XCTAssertTrue(paths[2].contains("20240303"), "Day 2 path wrong: \(paths[2])")
        XCTAssertEqual(result[0].distanceMeters, 1000, accuracy: 0.1)
        XCTAssertEqual(result[1].distanceMeters, 2000, accuracy: 0.1)
        XCTAssertEqual(result[2].distanceMeters, 3000, accuracy: 0.1)
    }

    // MARK: - getCalories — path correctness

    func test_getCalories_utcMidnightDate_requestsCorrectUTCPath() async throws {
        let date = makeDate(year: 2024, month: 6, day: 20, timeZone: utc)
        var proto = Data_PbDailySummary()
        proto.activityCalories = 350
        proto.date = PbDate.with { $0.year = 2024; $0.month = 6; $0.day = 20 }
        mockClient.requestReturnValue = .success(try proto.serializedData())

        let result = try await api.getCalories(
            identifier: deviceId, fromDate: date, toDate: date, caloriesType: .activity
        )

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240620"), "Expected '20240620' in path; got: \(path)")
        XCTAssertEqual(result.count, 1)
        XCTAssertGreaterThanOrEqual(result[0].calories, 0)
    }

    func test_getCalories_utcPlusTenMidnightDate_requestsPreviousUTCDayPath() async throws {
        let date = makeDate(year: 2024, month: 6, day: 20, timeZone: utcPlusTen)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let _ = try await api.getCalories(
            identifier: deviceId, fromDate: date, toDate: date, caloriesType: .activity
        )

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        // 2024-06-20 00:00 UTC+10 == 2024-06-19 14:00 UTC → "20240619"
        XCTAssertTrue(path.contains("20240619"), "Expected '20240619' in path; got: \(path)")
    }

    // MARK: - getNightlyRecharge — path correctness

    func test_getNightlyRecharge_utcMidnightDate_requestsCorrectUTCPath() async throws {
        let date = makeDate(year: 2024, month: 2, day: 28, timeZone: utc)
        // Let the request fail silently — we only need to inspect the captured path.
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let result = try await api.getNightlyRecharge(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240228"), "Expected '20240228' in NR path; got: \(path)")
        XCTAssertTrue(result.isEmpty, "Expected empty result when data unavailable")
    }

    func test_getNightlyRecharge_utcPlusTenMidnightDate_requestsPreviousUTCDayPath() async throws {
        // 2024-02-28 00:00 UTC+10 == 2024-02-27 14:00 UTC → "20240227"
        let date = makeDate(year: 2024, month: 2, day: 28, timeZone: utcPlusTen)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let _ = try await api.getNightlyRecharge(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240227"), "Expected '20240227' in NR path; got: \(path)")
    }

    func test_getNightlyRecharge_multiDayUTCRange_requestsConsecutiveUTCPaths() async throws {
        let from = makeDate(year: 2024, month: 2, day: 26, timeZone: utc)
        let to   = makeDate(year: 2024, month: 2, day: 28, timeZone: utc)
        mockClient.requestReturnValues = [
            .failure(NSError(domain: "test", code: 103)),
            .failure(NSError(domain: "test", code: 103)),
            .failure(NSError(domain: "test", code: 103))
        ]

        let _ = try await api.getNightlyRecharge(identifier: deviceId, fromDate: from, toDate: to)

        XCTAssertEqual(mockClient.requestCalls.count, 3)
        let paths = try mockClient.requestCalls.map { try decodePath(from: Data($0)) }
        XCTAssertTrue(paths[0].contains("20240226"), "Day 0 path wrong: \(paths[0])")
        XCTAssertTrue(paths[1].contains("20240227"), "Day 1 path wrong: \(paths[1])")
        XCTAssertTrue(paths[2].contains("20240228"), "Day 2 path wrong: \(paths[2])")
    }

    // MARK: - getDailySummaryData — path correctness

    func test_getDailySummaryData_utcMidnightDate_requestsCorrectUTCPath() async throws {
        let date = makeDate(year: 2024, month: 4, day: 10, timeZone: utc)
        // Return failure so the method silently skips; path is still captured.
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let result = try await api.getDailySummaryData(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240410"), "Expected '20240410' in DSUM path; got: \(path)")
        XCTAssertTrue(result.isEmpty)
    }

    func test_getDailySummaryData_utcPlusTenMidnightDate_requestsPreviousUTCDayPath() async throws {
        // 2024-04-10 00:00 UTC+10 == 2024-04-09 14:00 UTC → "20240409"
        let date = makeDate(year: 2024, month: 4, day: 10, timeZone: utcPlusTen)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let _ = try await api.getDailySummaryData(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240409"), "Expected '20240409' in DSUM path; got: \(path)")
    }

    // MARK: - getActiveTime — path correctness

    func test_getActiveTime_utcMidnightDate_requestsCorrectUTCPath() async throws {
        let date = makeDate(year: 2024, month: 7, day: 4, timeZone: utc)
        mockClient.requestReturnValue = .success(try makeDailySummary())

        let result = try await api.getActiveTime(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240704"), "Expected '20240704' in DSUM path; got: \(path)")
        XCTAssertEqual(result.count, 1)
    }

    func test_getActiveTime_utcPlusTenMidnightDate_requestsPreviousUTCDayPath() async throws {
        // 2024-07-04 00:00 UTC+10 == 2024-07-03 14:00 UTC → "20240703"
        let date = makeDate(year: 2024, month: 7, day: 4, timeZone: utcPlusTen)
        mockClient.requestReturnValue = .success(try makeDailySummary())

        let _ = try await api.getActiveTime(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240703"), "Expected '20240703' in DSUM path; got: \(path)")
    }

    func test_getActiveTime_utcMinusFiveMidnightDate_requestsSameDayUTCPath() async throws {
        // 2024-07-04 00:00 UTC-5 == 2024-07-04 05:00 UTC → "20240704"
        let date = makeDate(year: 2024, month: 7, day: 4, timeZone: utcMinusFive)
        mockClient.requestReturnValue = .success(try makeDailySummary())

        let _ = try await api.getActiveTime(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240704"), "Expected '20240704' in DSUM path; got: \(path)")
    }

    // MARK: - getSleep — path correctness

    func test_getSleep_utcMidnightDate_requestsCorrectUTCPath() async throws {
        let date = makeDate(year: 2024, month: 5, day: 22, timeZone: utc)
        // Let the request fail — getSleep swallows per-date errors.
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let result = try await api.getSleep(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240522"), "Expected '20240522' in SLEEP path; got: \(path)")
        XCTAssertTrue(result.isEmpty)
    }

    func test_getSleep_utcPlusTenMidnightDate_requestsPreviousUTCDayPath() async throws {
        // 2024-05-22 00:00 UTC+10 == 2024-05-21 14:00 UTC → "20240521"
        let date = makeDate(year: 2024, month: 5, day: 22, timeZone: utcPlusTen)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let _ = try await api.getSleep(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240521"), "Expected '20240521' in SLEEP path; got: \(path)")
    }

    func test_getSleep_utcMinusFiveMidnightDate_requestsSameDayUTCPath() async throws {
        // 2024-05-22 00:00 UTC-5 == 2024-05-22 05:00 UTC → "20240522"
        let date = makeDate(year: 2024, month: 5, day: 22, timeZone: utcMinusFive)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let _ = try await api.getSleep(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240522"), "Expected '20240522' in SLEEP path; got: \(path)")
    }

    func test_getSleep_multiDayUTCRange_requestsConsecutiveUTCPaths() async throws {
        let from = makeDate(year: 2024, month: 5, day: 20, timeZone: utc)
        let to   = makeDate(year: 2024, month: 5, day: 22, timeZone: utc)
        mockClient.requestReturnValues = [
            .failure(NSError(domain: "test", code: 103)),
            .failure(NSError(domain: "test", code: 103)),
            .failure(NSError(domain: "test", code: 103))
        ]

        let _ = try await api.getSleep(identifier: deviceId, fromDate: from, toDate: to)

        // getSleep requests SLEEPRES.BPB for each day (first request per day)
        let paths = try mockClient.requestCalls.map { try decodePath(from: Data($0)) }
        let sleepPaths = paths.filter { $0.contains("SLEEP") }
        XCTAssertEqual(sleepPaths.count, 3, "Expected 3 sleep file requests, one per day")
        XCTAssertTrue(sleepPaths[0].contains("20240520"), "Day 0 sleep path wrong: \(sleepPaths[0])")
        XCTAssertTrue(sleepPaths[1].contains("20240521"), "Day 1 sleep path wrong: \(sleepPaths[1])")
        XCTAssertTrue(sleepPaths[2].contains("20240522"), "Day 2 sleep path wrong: \(sleepPaths[2])")
    }

    // MARK: - getActivitySampleData — path correctness

    func test_getActivitySampleData_utcMidnightDate_requestsCorrectUTCPath() async throws {
        let date = makeDate(year: 2024, month: 8, day: 8, timeZone: utc)
        // Return empty directory to short-circuit cleanly.
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let result = try await api.getActivitySampleData(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240808"), "Expected '20240808' in ACT path; got: \(path)")
        XCTAssertEqual(result.count, 1)
    }

    func test_getActivitySampleData_utcPlusTenMidnightDate_requestsPreviousUTCDayPath() async throws {
        // 2024-08-08 00:00 UTC+10 == 2024-08-07 14:00 UTC → "20240807"
        let date = makeDate(year: 2024, month: 8, day: 8, timeZone: utcPlusTen)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let _ = try await api.getActivitySampleData(identifier: deviceId, fromDate: date, toDate: date)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(path.contains("20240807"), "Expected '20240807' in ACT path; got: \(path)")
    }

    // MARK: - Year boundary — path correctness across Dec 31 → Jan 1

    func test_getSteps_yearBoundary_utcDates_requestsCorrectUTCPaths() async throws {
        let from = makeDate(year: 2023, month: 12, day: 31, timeZone: utc)
        let to   = makeDate(year: 2024, month: 1,  day: 1,  timeZone: utc)
        mockClient.requestReturnValues = [
            .success(try emptyDirectoryData()),
            .success(try emptyDirectoryData())
        ]

        let result = try await api.getSteps(identifier: deviceId, fromDate: from, toDate: to)

        XCTAssertEqual(result.count, 2)
        let paths = try mockClient.requestCalls.map { try decodePath(from: Data($0)) }
        XCTAssertTrue(paths[0].contains("20231231"), "Dec 31 path wrong: \(paths[0])")
        XCTAssertTrue(paths[1].contains("20240101"), "Jan 1  path wrong: \(paths[1])")
    }

    func test_getDistance_yearBoundary_utcPlusTenDates_requestsPreviousUTCDayPaths() async throws {
        // Local Jan 1 00:00 UTC+10 == Dec 31 14:00 UTC → path "20231231"
        let from = makeDate(year: 2024, month: 1, day: 1, timeZone: utcPlusTen)
        let to   = makeDate(year: 2024, month: 1, day: 1, timeZone: utcPlusTen)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103))

        let _ = try await api.getDistance(identifier: deviceId, fromDate: from, toDate: to)

        let path = try decodePath(from: Data(mockClient.requestCalls[0]))
        XCTAssertTrue(
            path.contains("20231231"),
            "Expected '20231231' (previous UTC day) in path; got: \(path)"
        )
    }

    // MARK: - Private helpers

    private func makeActivityDirectoryData(entries: [String]) throws -> Data {
        return try Protocol_PbPFtpDirectory.with {
            $0.entries = entries.map { name in
                Protocol_PbPFtpEntry.with { $0.name = name; $0.size = 64 }
            }
        }.serializedData()
    }
}
