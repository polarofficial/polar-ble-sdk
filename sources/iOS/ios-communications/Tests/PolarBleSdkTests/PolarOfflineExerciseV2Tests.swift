// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarOfflineExerciseV2Tests: XCTestCase {

    private var identifier = "E123456F"
    private var mockClient: MockBlePsFtpClient!
    private var mockGatt: MockPolarGattServiceTransmitter!

    override func setUpWithError() throws {
        mockGatt = MockPolarGattServiceTransmitter()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: mockGatt)
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func test_startExercise_response_success() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with {
            $0.result = .resultSuccess
            $0.dmDirectoryPath = "/U/0/20260225/"
        }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultSuccess)
        XCTAssertEqual(proto.dmDirectoryPath, "/U/0/20260225/")
    }

    func test_startExercise_response_exerciseOngoing() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with { $0.result = .resultExeOngoing }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultExeOngoing)
    }

    func test_startExercise_response_lowBattery() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with { $0.result = .resultLowBattery }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultLowBattery)
    }

    func test_startExercise_response_sdkMode() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with { $0.result = .resultSdkMode }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultSdkMode)
    }

    func test_startExercise_response_unknownSport() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with { $0.result = .resultUnknownSport }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultUnknownSport)
    }

    func test_startExercise_request_creation() throws {
        // Arrange
        var sportId = PbSportIdentifier()
        sportId.value = UInt64(PolarExerciseSession.SportProfile.otherOutdoor.rawValue)
        var params = Protocol_PbPFtpStartDmExerciseParams()
        params.sportIdentifier = sportId

        // Act
        let request = try params.serializedData()

        // Assert
        XCTAssertFalse(request.isEmpty)
        let decoded = try Protocol_PbPFtpStartDmExerciseParams(serializedBytes: request)
        XCTAssertEqual(decoded.sportIdentifier.value, UInt64(16))
    }

    func test_stopExercise_request_creation() throws {
        // Arrange
        var params = Protocol_PbPFtpStopExerciseParams()
        params.save = true

        // Act
        let request = try params.serializedData()

        // Assert
        XCTAssertFalse(request.isEmpty)
        let decoded = try Protocol_PbPFtpStopExerciseParams(serializedBytes: request)
        XCTAssertTrue(decoded.save)
    }

    func test_stopExercise_response_success() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in Data() }

        // Act – request completes without error, no meaningful return value
        let data = try await mockClient.request(Data())

        // Assert
        XCTAssertEqual(data.length, 0)
    }

    func test_exerciseStatus_running() async throws {
        // Arrange
        let status = Protocol_PbPftpGetExerciseStatusResult.with {
            $0.exerciseType = .exerciseTypeDataMerge
            $0.exerciseState = .exerciseStateRunning
        }
        mockClient.requestReturnValueClosure = { _ in try status.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(data))

        // Assert
        XCTAssertTrue(proto.exerciseType == .exerciseTypeDataMerge && proto.exerciseState == .exerciseStateRunning)
    }

    func test_exerciseStatus_paused() async throws {
        // Arrange
        let status = Protocol_PbPftpGetExerciseStatusResult.with {
            $0.exerciseType = .exerciseTypeDataMerge
            $0.exerciseState = .exerciseStatePaused
        }
        mockClient.requestReturnValueClosure = { _ in try status.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(data))

        // Assert
        XCTAssertFalse(proto.exerciseType == .exerciseTypeDataMerge && proto.exerciseState == .exerciseStateRunning)
    }

    func test_exerciseStatus_notDataMerge() async throws {
        // Arrange
        let status = Protocol_PbPftpGetExerciseStatusResult.with {
            $0.exerciseType = .exerciseTypeNormal
            $0.exerciseState = .exerciseStateRunning
        }
        mockClient.requestReturnValueClosure = { _ in try status.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(data))

        // Assert
        XCTAssertFalse(proto.exerciseType == .exerciseTypeDataMerge && proto.exerciseState == .exerciseStateRunning)
    }

    func test_fetchExercise_request_creation() throws {
        // Arrange
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = "/U/0/20260225/SAMPLES.BPB"

        // Act
        let request = try operation.serializedData()

        // Assert
        XCTAssertFalse(request.isEmpty)
        let decoded = try Protocol_PbPFtpOperation(serializedBytes: request)
        XCTAssertEqual(decoded.command, .get)
        XCTAssertEqual(decoded.path, "/U/0/20260225/SAMPLES.BPB")
    }

    func test_fetchExercise_hrSamples() async throws {
        // Arrange
        let samples = Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 1 }
            $0.heartRateSamples = [60, 62, 65, 70]
        }
        mockClient.requestReturnValueClosure = { _ in try samples.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Data_PbExerciseSamples(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.recordingInterval.seconds, 1)
        XCTAssertEqual(proto.heartRateSamples, [60, 62, 65, 70])
    }

    func test_fetchExercise_emptySamples() async throws {
        // Arrange
        let samples = Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 1 }
            $0.heartRateSamples = []
        }
        mockClient.requestReturnValueClosure = { _ in try samples.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Data_PbExerciseSamples(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.recordingInterval.seconds, 1)
        XCTAssertEqual(proto.heartRateSamples, [])
    }

    func test_fetchExercise_withMultipleSamples() async throws {
        // Arrange
        let samples = Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 5 }
            $0.heartRateSamples = [120, 125, 130, 128, 132, 135]
        }
        mockClient.requestReturnValueClosure = { _ in try samples.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Data_PbExerciseSamples(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.recordingInterval.seconds, 5)
        XCTAssertEqual(proto.heartRateSamples.count, 6)
        XCTAssertEqual(proto.heartRateSamples, [120, 125, 130, 128, 132, 135])
    }

    func test_removeExercise_request_creation() throws {
        // Arrange
        var operation = Protocol_PbPFtpOperation()
        operation.command = .remove
        operation.path = "/U/0/20260225/SAMPLES.BPB"

        // Act
        let request = try operation.serializedData()

        // Assert
        XCTAssertFalse(request.isEmpty)
        let decoded = try Protocol_PbPFtpOperation(serializedBytes: request)
        XCTAssertEqual(decoded.command, .remove)
        XCTAssertEqual(decoded.path, "/U/0/20260225/SAMPLES.BPB")
    }

    func test_removeExercise_response_success() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in Data() }

        // Act – should complete without throwing
        let data = try await mockClient.request(Data())

        // Assert
        XCTAssertEqual(data.length, 0)
    }

    func test_exerciseEntry_path_structure() throws {
        // Arrange
        let entryPath = "/U/0/20260225/SAMPLES.BPB"
        let entryDate = Date()
        let entryId = "SAMPLES.BPB"

        // Act
        let entry: (path: String, date: Date, entryId: String) = (entryPath, entryDate, entryId)

        // Assert
        XCTAssertEqual(entry.path, entryPath)
        XCTAssertEqual(entry.entryId, entryId)
        XCTAssertTrue(entry.path.hasSuffix("SAMPLES.BPB"))
    }

    func test_request_error_handling() async throws {
        // Arrange
        let expectedError = NSError(domain: "TestError", code: 500, userInfo: nil)
        mockClient.requestReturnValueClosure = { _ in throw expectedError }

        // Act / Assert
        do {
            _ = try await mockClient.request(Data())
            XCTFail("Should have thrown error")
        } catch {
            let nsError = error as NSError
            XCTAssertEqual(nsError.code, 500)
            XCTAssertEqual(nsError.domain, "TestError")
        }
    }
}
