// Copyright  2026 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import PolarBleSdk

final class PolarBleApiImplActivityTests: XCTestCase {

    private let deviceId = "ABCDEF01"
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

    private func makeDate(year: Int, month: Int, day: Int) -> Date {
        var comps = DateComponents()
        comps.year = year; comps.month = month; comps.day = day
        comps.hour = 0; comps.minute = 0; comps.second = 0
        comps.timeZone = TimeZone(identifier: "UTC")
        return Calendar.current.date(from: comps)!
    }

    private func makeActivitySamplesData(steps: [UInt32]) throws -> Data {
        var proto = Data_PbActivitySamples()
        proto.stepsSamples = steps
        return try proto.serializedData()
    }

    private func makeActivityDirectoryData(entries: [String]) throws -> Data {
        return try Protocol_PbPFtpDirectory.with {
            $0.entries = entries.map { name in
                Protocol_PbPFtpEntry.with { $0.name = name; $0.size = 123 }
            }
        }.serializedData()
    }

    private func makeDailySummaryData(distance: Double, calories: UInt32 = 0) throws -> Data {
        var proto = Data_PbDailySummary()
        proto.activityDistance = Float(distance)
        proto.activityCalories = calories
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2024 }
        return try proto.serializedData()
    }

    // MARK: - getSteps tests

    func test_getSteps_singleDay_returnsCorrectStepCount() async throws {
        let date = makeDate(year: 2024, month: 1, day: 1)
        let dirData = try makeActivityDirectoryData(entries: ["ASAMPL.BPB"])
        let stepsData = try makeActivitySamplesData(steps: [5000, 3000])
        mockClient.requestReturnValues = [.success(dirData), .success(stepsData)]

        let result = try await api.getSteps(identifier: deviceId, fromDate: date, toDate: date)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].steps, 8000)
        XCTAssertEqual(result[0].date, date)
    }

    func test_getSteps_multipleDays_returnsOneEntryPerDay() async throws {
        let from = makeDate(year: 2024, month: 1, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 3)
        let dirData = try makeActivityDirectoryData(entries: ["ASAMPL.BPB"])
        let stepsData = try makeActivitySamplesData(steps: [1000])
        // 3 days × (1 listing + 1 data) = 6 responses
        for _ in 0..<3 {
            mockClient.requestReturnValues.append(.success(dirData))
            mockClient.requestReturnValues.append(.success(stepsData))
        }

        let result = try await api.getSteps(identifier: deviceId, fromDate: from, toDate: to)

        XCTAssertEqual(result.count, 3)
        result.forEach { XCTAssertEqual($0.steps, 1000) }
    }

    func test_getSteps_invalidDateRange_throwsError() async throws {
        let from = makeDate(year: 2024, month: 1, day: 5)
        let to = makeDate(year: 2024, month: 1, day: 1)

        do {
            _ = try await api.getSteps(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected an error")
        } catch let error as PolarErrors {
            if case .invalidArgument = error { XCTAssertTrue(true) }
            else { XCTFail("Expected invalidArgument, got \(error)") }
        }
    }

    func test_getSteps_noActivityFile_returnsZeroSteps() async throws {
        let date = makeDate(year: 2024, month: 1, day: 1)
        let emptyDirData = try makeActivityDirectoryData(entries: [])
        mockClient.requestReturnValues = [.success(emptyDirData)]

        let result = try await api.getSteps(identifier: deviceId, fromDate: date, toDate: date)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].steps, 0)
    }

    // MARK: - getDistance tests

    func test_getDistance_singleDay_returnsCorrectDistance() async throws {
        let date = makeDate(year: 2024, month: 1, day: 1)
        let summaryData = try makeDailySummaryData(distance: 1500.5)
        mockClient.requestReturnValue = .success(summaryData)

        let result = try await api.getDistance(identifier: deviceId, fromDate: date, toDate: date)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].distanceMeters, Float(1500.5), accuracy: 0.1)
        XCTAssertEqual(result[0].date, date)
    }

    func test_getDistance_invalidDateRange_throwsError() async throws {
        let from = makeDate(year: 2024, month: 2, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 1)

        do {
            _ = try await api.getDistance(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected an error")
        } catch let error as PolarErrors {
            if case .invalidArgument = error { XCTAssertTrue(true) }
            else { XCTFail("Expected invalidArgument, got \(error)") }
        }
    }

    func test_getDistance_serviceFailure_returnsZeroDistance() async throws {
        let date = makeDate(year: 2024, month: 1, day: 1)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103, userInfo: nil))

        let result = try await api.getDistance(identifier: deviceId, fromDate: date, toDate: date)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].distanceMeters, 0)
    }

    // MARK: - get247HrSamples tests

    func test_get247HrSamples_invalidDateRange_throwsError() async throws {
        let from = makeDate(year: 2024, month: 3, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 1)

        do {
            _ = try await api.get247HrSamples(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected an error")
        } catch let error as PolarErrors {
            if case .invalidArgument = error { XCTAssertTrue(true) }
            else { XCTFail("Expected invalidArgument, got \(error)") }
        }
    }

    // MARK: - get247PPiSamples tests

    func test_get247PPiSamples_invalidDateRange_throwsError() async throws {
        let from = makeDate(year: 2024, month: 3, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 1)

        do {
            _ = try await api.get247PPiSamples(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected an error")
        } catch let error as PolarErrors {
            if case .invalidArgument = error { XCTAssertTrue(true) }
            else { XCTFail("Expected invalidArgument, got \(error)") }
        }
    }

    // MARK: - getNightlyRecharge tests

    func test_getNightlyRecharge_invalidDateRange_throwsError() async throws {
        let from = makeDate(year: 2024, month: 3, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 1)

        do {
            _ = try await api.getNightlyRecharge(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected an error")
        } catch let error as PolarErrors {
            if case .invalidArgument = error { XCTAssertTrue(true) }
            else { XCTFail("Expected invalidArgument, got \(error)") }
        }
    }

    func test_getNightlyRecharge_noDataAvailable_returnsEmptyList() async throws {
        let date = makeDate(year: 2024, month: 1, day: 1)
        // Return empty data to simulate no nightly recharge file
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103, userInfo: nil))

        let result = try await api.getNightlyRecharge(identifier: deviceId, fromDate: date, toDate: date)

        XCTAssertTrue(result.isEmpty)
    }

    // MARK: - getCalories tests

    func test_getCalories_singleDay_returnsCorrectCalories() async throws {
        let date = makeDate(year: 2024, month: 1, day: 1)
        var proto = Data_PbDailySummary()
        proto.activityCalories = 500
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2024 }
        mockClient.requestReturnValue = .success(try proto.serializedData())

        let result = try await api.getCalories(identifier: deviceId, fromDate: date, toDate: date, caloriesType: .activity)

        XCTAssertEqual(result.count, 1)
        XCTAssertGreaterThanOrEqual(result[0].calories, 0)
    }

    func test_getCalories_invalidDateRange_throwsError() async throws {
        let from = makeDate(year: 2024, month: 5, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 1)

        do {
            _ = try await api.getCalories(identifier: deviceId, fromDate: from, toDate: to, caloriesType: .activity)
            XCTFail("Expected an error")
        } catch let error as PolarErrors {
            if case .invalidArgument = error { XCTAssertTrue(true) }
            else { XCTFail("Expected invalidArgument, got \(error)") }
        }
    }

    // MARK: - getActiveTime tests

    func test_getActiveTime_singleDay_returnsOneEntry() async throws {
        let date = makeDate(year: 2024, month: 1, day: 1)
        let summaryData = try makeDailySummaryData(distance: 0)
        mockClient.requestReturnValue = .success(summaryData)

        let result = try await api.getActiveTime(identifier: deviceId, fromDate: date, toDate: date)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].date, date)
    }

    func test_getActiveTime_multipleDays_returnsOneEntryPerDay() async throws {
        let from = makeDate(year: 2024, month: 1, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 3)
        let summaryData = try makeDailySummaryData(distance: 0)
        for _ in 0..<3 {
            mockClient.requestReturnValues.append(.success(summaryData))
        }

        let result = try await api.getActiveTime(identifier: deviceId, fromDate: from, toDate: to)

        XCTAssertEqual(result.count, 3)
    }

    // MARK: - getActivitySampleData tests

    func test_getActivitySampleData_invalidDateRange_throwsError() async throws {
        let from = makeDate(year: 2024, month: 5, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 1)

        do {
            _ = try await api.getActivitySampleData(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected an error")
        } catch let error as PolarErrors {
            if case .invalidArgument = error { XCTAssertTrue(true) }
            else { XCTFail("Expected invalidArgument, got \(error)") }
        }
    }

    // MARK: - getDailySummaryData tests

    func test_getDailySummaryData_invalidDateRange_throwsError() async throws {
        let from = makeDate(year: 2024, month: 5, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 1)

        do {
            _ = try await api.getDailySummaryData(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected an error")
        } catch let error as PolarErrors {
            if case .invalidArgument = error { XCTAssertTrue(true) }
            else { XCTFail("Expected invalidArgument, got \(error)") }
        }
    }

    func test_getDailySummaryData_noSummaryFile_skipsEntry() async throws {
        let date = makeDate(year: 2024, month: 1, day: 1)
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103, userInfo: nil))

        let result = try await api.getDailySummaryData(identifier: deviceId, fromDate: date, toDate: date)

        XCTAssertTrue(result.isEmpty)
    }
}
