// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest
import RxSwift
import RxBlocking
@testable import PolarBleSdk

final class PolarOfflineExerciseV2Tests: XCTestCase {

    private var identifier = "E123456F"
    private var mockClient: MockBlePsFtpClient!
    private var mockGatt: MockGattServiceTransmitterImpl!

    override func setUpWithError() throws {
        mockGatt = MockGattServiceTransmitterImpl()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: mockGatt)
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func test_startExercise_response_success() throws {

        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with {
            $0.result = .resultSuccess
            $0.dmDirectoryPath = "/U/0/20260225/"
        }
        
        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! startResult.serializedData())
        }

        // Act
        let response = try mockClient.request(Data())
            .map { data -> (result: Protocol_PbPftpStartDmExerciseResult.PbStartDmExerciseResult, path: String) in
                let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))
                return (proto.result, proto.dmDirectoryPath)
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertEqual(response.result, .resultSuccess)
        XCTAssertEqual(response.path, "/U/0/20260225/")
    }

    func test_startExercise_response_exerciseOngoing() throws {

        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with {
            $0.result = .resultExeOngoing
        }
        
        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! startResult.serializedData())
        }

        // Act
        let response = try mockClient.request(Data())
            .map { data -> Protocol_PbPftpStartDmExerciseResult.PbStartDmExerciseResult in
                let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))
                return proto.result
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertEqual(response, .resultExeOngoing)
    }

    func test_startExercise_response_lowBattery() throws {

        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with {
            $0.result = .resultLowBattery
        }
        
        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! startResult.serializedData())
        }

        // Act
        let response = try mockClient.request(Data())
            .map { data -> Protocol_PbPftpStartDmExerciseResult.PbStartDmExerciseResult in
                let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))
                return proto.result
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertEqual(response, .resultLowBattery)
    }

    func test_startExercise_response_sdkMode() throws {

        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with {
            $0.result = .resultSdkMode
        }
        
        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! startResult.serializedData())
        }

        // Act
        let response = try mockClient.request(Data())
            .map { data -> Protocol_PbPftpStartDmExerciseResult.PbStartDmExerciseResult in
                let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))
                return proto.result
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertEqual(response, .resultSdkMode)
    }

    func test_startExercise_response_unknownSport() throws {

        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with {
            $0.result = .resultUnknownSport
        }
        
        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! startResult.serializedData())
        }

        // Act
        let response = try mockClient.request(Data())
            .map { data -> Protocol_PbPftpStartDmExerciseResult.PbStartDmExerciseResult in
                let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))
                return proto.result
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertEqual(response, .resultUnknownSport)
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

    func test_stopExercise_response_success() throws {

        // Arrange
        mockClient.requestReturnValueClosure = { _ in
            return Single.just(Data())
        }

        // Act
        let result: PrimitiveSequence<CompletableTrait, Never>.Element? = try mockClient.request(Data())
            .map { _ in () }
            .asCompletable()
            .toBlocking()
            .first()
        
        // Assert
        XCTAssertNil(result)
    }

    func test_exerciseStatus_running() throws {

        // Arrange
        let status = Protocol_PbPftpGetExerciseStatusResult.with {
            $0.exerciseType = .exerciseTypeDataMerge
            $0.exerciseState = .exerciseStateRunning
        }

        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! status.serializedData())
        }

        // Act
        let result = try mockClient.request(Data())
            .map { data -> Bool in
                let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(data))
                return proto.exerciseType == .exerciseTypeDataMerge &&
                       proto.exerciseState == .exerciseStateRunning
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertTrue(result)
    }

    func test_exerciseStatus_paused() throws {

        // Arrange
        let status = Protocol_PbPftpGetExerciseStatusResult.with {
            $0.exerciseType = .exerciseTypeDataMerge
            $0.exerciseState = .exerciseStatePaused
        }

        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! status.serializedData())
        }

        // Act
        let result = try mockClient.request(Data())
            .map { data -> Bool in
                let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(data))
                return proto.exerciseType == .exerciseTypeDataMerge &&
                       proto.exerciseState == .exerciseStateRunning
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertFalse(result)
    }

    func test_exerciseStatus_notDataMerge() throws {

        // Arrange
        let status = Protocol_PbPftpGetExerciseStatusResult.with {
            $0.exerciseType = .exerciseTypeNormal
            $0.exerciseState = .exerciseStateRunning
        }

        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! status.serializedData())
        }

        // Act
        let result = try mockClient.request(Data())
            .map { data -> Bool in
                let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(data))
                return proto.exerciseType == .exerciseTypeDataMerge &&
                       proto.exerciseState == .exerciseStateRunning
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertFalse(result)
    }

    func test_fetchExercise_request_creation() throws {

        // Arrange
        var operation = Protocol_PbPFtpOperation()
        operation.command = Protocol_PbPFtpOperation.Command.get
        operation.path = "/U/0/20260225/SAMPLES.BPB"

        // Act
        let request = try operation.serializedData()
        
        // Assert
        XCTAssertFalse(request.isEmpty)
        
        let decoded = try Protocol_PbPFtpOperation(serializedBytes: request)
        XCTAssertEqual(decoded.command, Protocol_PbPFtpOperation.Command.get)
        XCTAssertEqual(decoded.path, "/U/0/20260225/SAMPLES.BPB")
    }

    func test_fetchExercise_hrSamples() throws {

        // Arrange
        let samples = Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 1 }
            $0.heartRateSamples = [60, 62, 65, 70]
        }

        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! samples.serializedData())
        }

        // Act
        let result = try mockClient.request(Data())
            .map { data -> (interval: UInt32, samples: [UInt32]) in
                let proto = try Data_PbExerciseSamples(serializedBytes: Data(data))
                return (proto.recordingInterval.seconds, proto.heartRateSamples)
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertEqual(result.interval, 1)
        XCTAssertEqual(result.samples, [60, 62, 65, 70])
    }

    func test_fetchExercise_emptySamples() throws {

        // Arrange
        let samples = Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 1 }
            $0.heartRateSamples = []
        }

        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! samples.serializedData())
        }

        // Act
        let result = try mockClient.request(Data())
            .map { data -> (interval: UInt32, samples: [UInt32]) in
                let proto = try Data_PbExerciseSamples(serializedBytes: Data(data))
                return (proto.recordingInterval.seconds, proto.heartRateSamples)
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertEqual(result.interval, 1)
        XCTAssertEqual(result.samples, [])
    }

    func test_fetchExercise_withMultipleSamples() throws {

        // Arrange
        let samples = Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 5 }
            $0.heartRateSamples = [120, 125, 130, 128, 132, 135]
        }

        mockClient.requestReturnValueClosure = { _ in
            return Single.just(try! samples.serializedData())
        }

        // Act
        let result = try mockClient.request(Data())
            .map { data -> (interval: UInt32, samples: [UInt32]) in
                let proto = try Data_PbExerciseSamples(serializedBytes: Data(data))
                return (proto.recordingInterval.seconds, proto.heartRateSamples)
            }
            .toBlocking()
            .single()

        // Assert
        XCTAssertEqual(result.interval, 5)
        XCTAssertEqual(result.samples.count, 6)
        XCTAssertEqual(result.samples, [120, 125, 130, 128, 132, 135])
    }

    func test_removeExercise_request_creation() throws {

        // Arrange
        var operation = Protocol_PbPFtpOperation()
        operation.command = Protocol_PbPFtpOperation.Command.remove
        operation.path = "/U/0/20260225/SAMPLES.BPB"

        // Act
        let request = try operation.serializedData()
        
        // Assert
        XCTAssertFalse(request.isEmpty)
        
        let decoded = try Protocol_PbPFtpOperation(serializedBytes: request)
        XCTAssertEqual(decoded.command, Protocol_PbPFtpOperation.Command.remove)
        XCTAssertEqual(decoded.path, "/U/0/20260225/SAMPLES.BPB")
    }

    func test_removeExercise_response_success() throws {

        // Arrange
        mockClient.requestReturnValueClosure = { _ in
            return Single.just(Data())
        }

        // Act
        let result: PrimitiveSequence<CompletableTrait, Never>.Element? = try mockClient.request(Data())
            .map { _ in () }
            .asCompletable()
            .toBlocking()
            .first()
        
        // Assert
        XCTAssertNil(result)
    }

    func test_exerciseEntry_path_structure() throws {
    
        // Arrange
        let entryPath = "/U/0/20260225/SAMPLES.BPB"
        let entryDate = Date()
        let entryId = "SAMPLES.BPB"
        
        // Act
        let entry: (path: String, date: Date, entryId: String) = (
            path: entryPath,
            date: entryDate,
            entryId: entryId
        )
        
        // Assert
        XCTAssertEqual(entry.path, entryPath)
        XCTAssertEqual(entry.entryId, entryId)
        XCTAssertTrue(entry.path.hasSuffix("SAMPLES.BPB"))
    }

    func test_request_error_handling() throws {

        // Arrange
        let expectedError = NSError(domain: "TestError", code: 500, userInfo: nil)
        
        mockClient.requestReturnValueClosure = { _ in
            return Single.error(expectedError)
        }

        // Act / Assert
        do {
            _ = try mockClient.request(Data())
                .toBlocking()
                .single()
            XCTFail("Should have thrown error")
        } catch {
            let nsError = error as NSError
            XCTAssertEqual(nsError.code, 500)
            XCTAssertEqual(nsError.domain, "TestError")
        }
    }
}
