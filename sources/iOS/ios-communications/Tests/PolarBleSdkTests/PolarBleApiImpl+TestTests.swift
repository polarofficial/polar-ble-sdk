// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarBleApiImplTestTests: XCTestCase {

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

    func test_getSpo2TestData_whenFromDateAfterToDate_throwsInvalidArgument() async {
        let from = makeUtcDate(year: 2026, month: 7, day: 2)
        let to = makeUtcDate(year: 2026, month: 7, day: 1)

        do {
            _ = try await api.getSpo2TestData(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected invalidArgument")
        } catch let error as PolarErrors {
            if case let .invalidArgument(description) = error {
                XCTAssertEqual(description, "toDate cannot be before fromDate.")
            } else {
                XCTFail("Expected PolarErrors.invalidArgument, got \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func test_getSpo2TestData_whenSessionIsNotReady_propagatesError() async {
        let utils = MockDynamicServiceClientUtils(listener: MockCBDeviceListenerImpl())
        utils.ftpError = PolarErrors.deviceNotConnected
        let failingApi = MockDynamicBleApiImpl(serviceUtils: utils)
        let date = makeUtcDate(year: 2026, month: 7, day: 1)

        do {
            _ = try await failingApi.getSpo2TestData(identifier: deviceId, fromDate: date, toDate: date)
            XCTFail("Expected deviceNotConnected")
        } catch let error as PolarErrors {
            if case .deviceNotConnected = error {
                XCTAssertTrue(true)
            } else {
                XCTFail("Expected PolarErrors.deviceNotConnected, got \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func test_getSpo2TestData_whenFtpClientMissing_throwsServiceNotFound() async {
        let noClientSession = MockNoFtpClientBleDeviceSession()
        let apiWithoutFtp = PolarBleApiImplWithNoFtpSession(mockDeviceSession: noClientSession)
        let day = makeUtcDate(year: 2026, month: 7, day: 1)

        do {
            _ = try await apiWithoutFtp.getSpo2TestData(identifier: deviceId, fromDate: day, toDate: day)
            XCTFail("Expected serviceNotFound")
        } catch let error as PolarErrors {
            if case .serviceNotFound = error {
                XCTAssertTrue(true)
            } else {
                XCTFail("Expected PolarErrors.serviceNotFound, got \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func test_getSpo2TestData_singleDay_returnsParsedSpo2Result() async throws {
        let day = makeUtcDate(year: 2026, month: 4, day: 13)
        var proto = Data_PbSpo2TestResult()
        proto.bloodOxygenPercent = 97
        proto.testStatus = .spo2TestPassed

        mockClient.requestReturnValues = try makeDirThenFileResults(proto: proto, subDirName: "142507")

        let result = try await api.getSpo2TestData(identifier: deviceId, fromDate: day, toDate: day)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result.first?.bloodOxygenPercent, 97)
        XCTAssertEqual(result.first?.testStatus, .passed)
    }

    func test_getSpo2TestData_dateRange_queriesEachDayAndAggregatesResults() async throws {
        let from = makeUtcDate(year: 2026, month: 4, day: 13)
        let to = makeUtcDate(year: 2026, month: 4, day: 14)

        var dayOneProto = Data_PbSpo2TestResult()
        dayOneProto.bloodOxygenPercent = 97
        var dayTwoProto = Data_PbSpo2TestResult()
        dayTwoProto.bloodOxygenPercent = 95

        // Two days -> each day does directory listing + file fetch.
        mockClient.requestReturnValues = [
            .success(try makeSpo2DirectoryData(subDirName: "093635")),
            .success(try dayOneProto.serializedData()),
            .success(try makeSpo2DirectoryData(subDirName: "101010")),
            .success(try dayTwoProto.serializedData())
        ]

        let results = try await api.getSpo2TestData(identifier: deviceId, fromDate: from, toDate: to)

        XCTAssertEqual(results.count, 2)
        XCTAssertEqual(Set(results.compactMap(\ .bloodOxygenPercent)), Set([97, 95]))
        XCTAssertEqual(mockClient.requestCalls.count, 4)
    }

    private func makeDirThenFileResults(
        proto: Data_PbSpo2TestResult,
        subDirName: String
    ) throws -> [Result<Data, Error>] {
        [
            .success(try makeSpo2DirectoryData(subDirName: subDirName)),
            .success(try proto.serializedData())
        ]
    }

    private func makeSpo2DirectoryData(subDirName: String) throws -> Data {
        var directory = Protocol_PbPFtpDirectory()
        var entry = Protocol_PbPFtpEntry()
        entry.name = "\(subDirName)/"
        entry.size = 0
        directory.entries = [entry]
        return try directory.serializedData()
    }

    private func makeUtcDate(year: Int, month: Int, day: Int) -> Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = 0
        components.minute = 0
        components.second = 0
        components.timeZone = TimeZone(secondsFromGMT: 0)
        return Calendar(identifier: .gregorian).date(from: components)!
    }
}
