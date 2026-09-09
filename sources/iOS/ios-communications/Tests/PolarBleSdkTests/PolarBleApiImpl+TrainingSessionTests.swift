// Copyright  2026 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import PolarBleSdk

final class PolarBleApiImplTrainingSessionTests: XCTestCase {

    private let deviceId = "ABCDEF01"
    private var mockClient: MockBlePsFtpClient!
    private var mockSession: MockBleDeviceSession!
    private var api: PolarBleApiImplWithMockSession!

    override func setUpWithError() throws {
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: ["h10": .h10FileSystem],
            defaultFileSystemType: .polarFileSystemV2,
            defaultRecordingSupported: false
        )
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

    private func makeDirectoryResponse(entries: [(name: String, size: UInt64)]) throws -> Data {
        return try Protocol_PbPFtpDirectory.with {
            $0.entries = entries.map { e in
                Protocol_PbPFtpEntry.with { $0.name = e.name; $0.size = e.size }
            }
        }.serializedData()
    }

    private func makeEmptyDirectoryResponse() throws -> Data {
        return try Protocol_PbPFtpDirectory.with { $0.entries = [] }.serializedData()
    }

    private func makeTrainingSessionSummary() throws -> Data {
        let proto = Data_PbTrainingSession.with {
            $0.start = PbLocalDateTime.with {
                $0.date.year = 2024
                $0.date.month = 1
                $0.date.day = 1
                $0.time.hour = 12
                $0.time.minute = 0
                $0.time.seconds = 0
                $0.time.millis = 0
                $0.obsoleteTrusted = true
            }
            $0.exerciseCount = 1
        }
        return try proto.serializedData()
    }

    // MARK: - getTrainingSessionReferences tests

    func test_getTrainingSessionReferences_noSessions_returnsEmptyList() async throws {
        // Return empty directory at every level to simulate no sessions
        let emptyDir = try makeEmptyDirectoryResponse()
        // The recursive listing will request the root directory and get nothing
        mockClient.requestReturnValue = .success(emptyDir)

        let result = try await api.getTrainingSessionReferences(identifier: deviceId)

        XCTAssertTrue(result.isEmpty)
    }

    func test_getTrainingSessionReferences_withSession_returnsReference() async throws {
        // Set up directory structure: /U/0/ -> 20240101/ -> E/ -> 120000/ -> TSESS.BPB
        let rootDir = try makeDirectoryResponse(entries: [("20240101/", 0)])
        let dateDir = try makeDirectoryResponse(entries: [("E/", 0)])
        let exerciseRootDir = try makeDirectoryResponse(entries: [("120000/", 0)])
        let sessionDir = try makeDirectoryResponse(entries: [("TSESS.BPB", 1024)])

        mockClient.requestReturnValues = [
            .success(rootDir),
            .success(dateDir),
            .success(exerciseRootDir),
            .success(sessionDir)
        ]

        let result = try await api.getTrainingSessionReferences(identifier: deviceId)

        XCTAssertEqual(result.count, 1)
        XCTAssertTrue(result[0].path.contains("TSESS.BPB"))
    }

    func test_getTrainingSessionReferences_invalidDateRange_throwsError() async throws {
        let from = makeDate(year: 2024, month: 5, day: 1)
        let to = makeDate(year: 2024, month: 1, day: 1)

        do {
            _ = try await api.getTrainingSessionReferences(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected an error")
        } catch let error as PolarErrors {
            if case .invalidArgument = error { XCTAssertTrue(true) }
            else { XCTFail("Expected invalidArgument, got \(error)") }
        }
    }

    func test_getTrainingSessionReferences_serviceNotFound_throwsError() async throws {
        mockClient.requestReturnValue = .failure(PolarErrors.serviceNotFound)

        do {
            _ = try await api.getTrainingSessionReferences(identifier: deviceId)
            XCTFail("Expected an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }

    // MARK: - getTrainingSession tests

    func test_getTrainingSession_returnsSession() async throws {
        let reference = PolarTrainingSessionReference(
            date: makeDate(year: 2024, month: 1, day: 1),
            path: "/U/0/20240101/E/120000/TSESS.BPB",
            trainingDataTypes: [],
            exercises: [],
            fileSize: 1024
        )
        let sessionData = try makeTrainingSessionSummary()
        mockClient.requestReturnValue = .success(sessionData)

        let session = try await api.getTrainingSession(identifier: deviceId, trainingSessionReference: reference)

        XCTAssertNotNil(session)
        XCTAssertEqual(session.reference.path, reference.path)
    }

    func test_getTrainingSession_ftpError_throwsError() async throws {
        let reference = PolarTrainingSessionReference(
            date: makeDate(year: 2024, month: 1, day: 1),
            path: "/U/0/20240101/E/120000/TSESS.BPB",
            trainingDataTypes: [],
            exercises: [],
            fileSize: 1024
        )
        mockClient.requestReturnValue = .failure(NSError(domain: "test", code: 103, userInfo: nil))

        do {
            _ = try await api.getTrainingSession(identifier: deviceId, trainingSessionReference: reference)
            XCTFail("Expected an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }

    // MARK: - getTrainingSessionWithProgress tests

    func test_getTrainingSessionWithProgress_reportProgress() async throws {
        let reference = PolarTrainingSessionReference(
            date: makeDate(year: 2024, month: 1, day: 1),
            path: "/U/0/20240101/E/120000/TSESS.BPB",
            trainingDataTypes: [],
            exercises: [],
            fileSize: 512
        )
        let sessionData = try makeTrainingSessionSummary()
        mockClient.requestReturnValue = .success(sessionData)

        var progressValues: [Int] = []
        let session = try await api.getTrainingSessionWithProgress(
            identifier: deviceId,
            trainingSessionReference: reference
        ) { progress in
            progressValues.append(progress.progressPercent)
        }

        XCTAssertNotNil(session)
    }

    // MARK: - deleteTrainingSession tests

    func test_deleteTrainingSession_singleSession_removesEntireExerciseFolder() async throws {
        let reference = PolarTrainingSessionReference(
            date: makeDate(year: 2024, month: 1, day: 1),
            path: "/U/0/20240101/E/120000/TSESS.BPB",
            trainingDataTypes: [],
            exercises: [],
            fileSize: 1024
        )
        // First request: GET /U/0/20240101/E/ → returns 1 entry (only this session)
        let dirData = try makeDirectoryResponse(entries: [("120000/", 0)])
        // Second request: GET with REMOVE command → success
        let emptyResponse = Data()
        mockClient.requestReturnValues = [.success(dirData), .success(emptyResponse)]

        // The delete should succeed without throwing
        try await api.deleteTrainingSession(identifier: deviceId, reference: reference)

        XCTAssertEqual(mockClient.requestCalls.count, 2)
    }

    func test_deleteTrainingSession_multipleSessions_removesOnlyTargetSession() async throws {
        let reference = PolarTrainingSessionReference(
            date: makeDate(year: 2024, month: 1, day: 1),
            path: "/U/0/20240101/E/120000/TSESS.BPB",
            trainingDataTypes: [],
            exercises: [],
            fileSize: 1024
        )
        // First request: GET /U/0/20240101/E/ → returns 2 entries (multiple sessions)
        let dirData = try makeDirectoryResponse(entries: [("110000/", 0), ("120000/", 0)])
        // Second request: GET with REMOVE command for specific session folder → success
        let emptyResponse = Data()
        mockClient.requestReturnValues = [.success(dirData), .success(emptyResponse)]

        try await api.deleteTrainingSession(identifier: deviceId, reference: reference)

        XCTAssertEqual(mockClient.requestCalls.count, 2)
    }

    // MARK: - stopExercise

    func test_stopExercise_defaultsSave_true() async throws {
        mockClient.queryReturnValue = .success(Data())

        try await api.stopExercise(identifier: deviceId)

        XCTAssertEqual(mockClient.queryCalls.count, 1)
        let params = mockClient.queryCalls[0].parameters.map { Data($0) } ?? Data()
        let payload = try Protocol_PbPFtpStopExerciseParams(serializedBytes: params)
        XCTAssertTrue(payload.save)
    }

    func test_stopExercise_savefalse_discardsSession() async throws {
        mockClient.queryReturnValue = .success(Data())

        try await api.stopExercise(identifier: deviceId, save: false)

        XCTAssertEqual(mockClient.queryCalls.count, 1)
        let params = mockClient.queryCalls[0].parameters.map { Data($0) } ?? Data()
        let payload = try Protocol_PbPFtpStopExerciseParams(serializedBytes: params)
        XCTAssertFalse(payload.save)
    }
}
