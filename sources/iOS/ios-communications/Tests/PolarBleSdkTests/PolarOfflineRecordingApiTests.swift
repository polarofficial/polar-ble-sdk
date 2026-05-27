//
//  PolarOffilineRecordingApiTests.swift
//  iOSCommunicationsTests
//
//  Created by Pasi Hytönen on 25.3.2025.
//  Copyright © 2025 Polar. All rights reserved.
//

import XCTest
import CoreBluetooth
@testable import PolarBleSdk

final class PolarOfflineRecordingApiTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!
    var mockSession: MockBleDeviceSession!
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    
    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
        mockSession = nil
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
        let testTimeZone = TimeZone(identifier: "America/New_York")!
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
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-03-09, 03:01:30 Eastern Daylight Time")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20250309/R/030130/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingV1BeforeDaylightSavingsTimeShiftHourBackwardInNewYork2025_shouldReturnListing() async throws {
        // Sunday, 2 November 2025, 02:00:00 clocks turned backward 1 hour to 01:00:00.
        // Use 00:30:07 EDT — a valid, unambiguous time clearly before the fall-back.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "America/New_York")!
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
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-11-02, 00:30:07 Eastern Daylight Time")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20251102/R/003007/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV1AfterDaylightSavingsTimeShiftHourBackwardInNewYork2025_shouldReturnListing() async throws {
        // Sunday, 2 November 2025, 02:00:00 clocks turned backward 1 hour to 01:00:00.
        // Use 03:01:30 EST — a valid, unambiguous time clearly after the fall-back.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "America/New_York")!
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
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-11-02, 03:01:30 Eastern Standard Time")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20251102/R/030130/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV1AfterDaylightSavingsTimeShiftHourForwardInHelsinki2025_shouldReturnListing() async throws {
        // Sunday, 30 March 2025, 03:00:00 clocks turned forward 1 hour to 04:00:00.
        // Use 04:01:00 EEST — a valid, unambiguous time immediately after the spring-forward.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
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
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-03-30, 04:01:00 Itä-Euroopan kesäaika")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20250330/R/040100/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV1BeforeDaylightSavingsTimeShiftHourBackwardInHelsinki2025_shouldReturnListing() async throws {
        // Sunday, 26 October 2025, 04:00:00 clocks turned backward 1 hour to 03:00:00.
        // Use 02:00:00 EEST — a valid, unambiguous time clearly before the fall-back.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
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
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-10-26, 02:00:00 Itä-Euroopan kesäaika")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20251026/R/020000/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV1AfterDaylightSavingsTimeShiftHourBackwardInHelsinki2025_shouldReturnListing() async throws {
        // Sunday, 26 October 2025, 04:00:00 clocks turned backward 1 hour to 03:00:00.
        // Use 06:00:00 EET — a valid, unambiguous time clearly after the fall-back.
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
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
        XCTAssertEqual(dateFormatter.string(from: unwrappedEntry.date), "2025-10-26, 06:00:00 Itä-Euroopan normaaliaika")
        XCTAssertEqual(unwrappedEntry.path, "/U/0/20251026/R/060000/HR.REC")
        XCTAssertEqual(unwrappedEntry.size, 444)
        XCTAssertEqual(unwrappedEntry.type, PolarDeviceDataType.hr)
    }

    func testReadOfflineRecordingsV2_should_return_listing() async throws {
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
        let testTimeZone = TimeZone(identifier: "Europe/Helsinki")!
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
        XCTAssertEqual(dateFormatter.string(from: acc!.date), "2025-07-30, 10:10:10 Itä-Euroopan kesäaika")
        XCTAssertEqual(acc?.path, "/U/0/20250730/R/101010/ACC0.REC")
        XCTAssertEqual(acc?.size, 1102548)

        let hr = entries.first { $0.type == .hr }
        XCTAssertEqual(dateFormatter.string(from: hr!.date), "2025-07-30, 10:10:10 Itä-Euroopan kesäaika")
        XCTAssertEqual(hr?.path, "/U/0/20250730/R/101010/HR0.REC")
        XCTAssertEqual(hr?.size, 1000050)

        let ppg = entries.first { $0.type == .ppg }
        XCTAssertEqual(dateFormatter.string(from: ppg!.date), "2025-07-30, 10:10:10 Itä-Euroopan kesäaika")
        XCTAssertEqual(ppg?.path, "/U/0/20250730/R/101010/PPG0.REC")
        XCTAssertEqual(ppg?.size, 300)
    }
}
