//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

final class PolarFileUtilsTest: XCTestCase {
    
    private var identifier = "E123456F"
    private var mockClient: MockBlePsFtpClient!
    private var mockListener: MockCBDeviceListenerImpl!
    private var mockSession: MockBleDeviceSession!
    private var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    private var mockServiceClientUtils: MockPolarServiceClientUtils!
    private var fileUtils: PolarFileUtils!
    private let dateFormatter = DateFormatter()
    
    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        mockListener = MockCBDeviceListenerImpl()
        mockServiceClientUtils = MockPolarServiceClientUtils(listener: mockListener, session: mockSession)
        fileUtils = PolarFileUtils(listener: mockListener, serviceClientUtils: mockServiceClientUtils)
        dateFormatter.dateFormat = "yyyyMMdd"
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
    }
    
    func testListFiles_success() async throws {
        // Arrange
        let condition: (_ p: String) -> Bool = { $0.contains(".") }
        let expectedFiles = ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260201/R/101010/PPG0.REC", "/U/0/20260201/R/101010/HR0.REC"]
        
        let responses = makeDirectoryResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        var emitted: [String] = []
        for try await file in fileUtils.listFiles(identifier: identifier, folderPath: "/U/0/", condition: condition) {
            emitted.append(file)
        }
        
        // Assert
        XCTAssertEqual(emitted.count, expectedFiles.count)
        XCTAssert(emitted.contains(expectedFiles[0]))
        XCTAssert(emitted.contains(expectedFiles[1]))
        XCTAssert(emitted.contains(expectedFiles[2]))
    }
    
    func testListFiles_failure() async throws {
        // Arrange
        let condition: (_ p: String) -> Bool = { $0.contains(".") }
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            for try await _ in fileUtils.listFiles(identifier: identifier, folderPath: "/U/0/", condition: condition) {}
            XCTFail("listFiles method did not throw an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }
    
    func testCheckAutoSampleFile_can_delete_file() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(mockFileContent())]
        let date = DateFormatter().apply { $0.dateFormat = "yyyyMMdd" }.date(from: "25251118")!
        
        // Act
        let result = try await fileUtils.checkAutoSampleFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB", until: date)
        
        // Assert
        XCTAssertTrue(result)
    }
    
    func testCheckAutoSampleFile_cannot_delete_file() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(mockFileContent())]
        let date = dateFormatter.date(from: "25250225")!
        
        // Act
        let result = try await fileUtils.checkAutoSampleFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB", until: date)
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertFalse(result)
    }
    
    func testCheckAutoSampleFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        let date = dateFormatter.date(from: "25250225")!
        
        // Act & Assert
        do {
            _ = try await fileUtils.checkAutoSampleFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB", until: date)
            XCTFail("Expected error")
        } catch {
            XCTAssertNotNil(error)
        }
    }
    
    func testCheckIfDirectoryIsEmpty_is_empty() async throws {
        // Arrange
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/20260101/": []
        ]
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        let result = try await fileUtils.checkIfDirectoryIsEmpty(directoryPath: "U/0/20260101", client: mockClient)
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertTrue(result)
    }
    
    func testCheckIfDirectoryIsEmpty_is_not_empty() async throws {
        // Arrange
        let dsumEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM.BPB"; $0.size = 1024 }
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/20260101/DSUM/": [dsumEntry]
        ]
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        let result = try await fileUtils.checkIfDirectoryIsEmpty(directoryPath: "/U/0/20260101/DSUM/", client: mockClient)
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertFalse(result)
    }
    
    func testCheckIfDirectoryIsEmpty_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.checkIfDirectoryIsEmpty(directoryPath: "/U/0/20260101/DSUM/", client: mockClient)
            XCTFail("Expected error")
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }
    
    func testCheckIfDirectoryIsEmpty_error103_file_not_found() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw BlePsFtpException.responseError(errorCode: 103) }
        
        // Act
        let result = try await fileUtils.checkIfDirectoryIsEmpty(directoryPath: "/U/0/20260101/DSUM/", client: mockClient)
        
        // Assert
        XCTAssertTrue(result, "Error code 103 MUST return true")
    }

    func testRemoveSingleFile_success() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(Data([0x00]))]
        
        // Act
        let res = try await fileUtils.removeSingleFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertEqual([UInt8](res as Data)[0], 0)
    }
    
    func testRemoveSingleFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.removeSingleFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
            XCTFail("removeSingleFile method did not throw an error")
        } catch {
            XCTAssertEqual(mockClient.requestCalls.count, 1)
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }
    
    func testRemoveMultipleFile_success() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = [Protocol_PbPFtpEntry.with { $0.name = ""; $0.size = 1024 }]
            }
            return try dir.serializedData()
        }
        
        // Act
        try await fileUtils.removeMultipleFiles(identifier: identifier, filePaths: ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260102/DSUM/DSUM.BPB"])
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 2)
    }
    
    func testRemoveMultipleFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            try await fileUtils.removeMultipleFiles(identifier: identifier, filePaths: ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260102/DSUM/DSUM.BPB"])
            XCTFail("removeMultipleFiles method did not throw an error")
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }
    
    func testGetFile_success() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(mockFileContent())]
        
        // Act
        let result = try await fileUtils.getFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB")
        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: result as Data)
        
        // Assert
        XCTAssertEqual(2, sampleSessions.samples.count)
        XCTAssertEqual(3, sampleSessions.samples[0].heartRate.count)
        XCTAssertEqual(3, sampleSessions.samples[1].heartRate.count)
        XCTAssertEqual(2, sampleSessions.ppiSamples.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[0].ppi.ppiDelta.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[1].ppi.ppiDelta.count)
    }
    
    func testGetFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.getFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
            XCTFail("getFile method did not throw an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }
    
    // MARK: - Low level API tests

    func testWriteFile_success() async throws {
        // Arrange
        mockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }
        
        // Act
        try await fileUtils.writeFile(identifier: identifier, filePath: "/U/0/2525/DSUM/DSUM.BPB", fileData: mockFileContent())
        
        // Assert
        XCTAssertEqual(mockClient.writeCalls.count, 1)
    }
    
    func testWriteFile_failure() async throws {
        // Arrange
        mockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: PolarErrors.deviceNotConnected)
        }
        
        // Act & Assert
        do {
            try await fileUtils.writeFile(identifier: identifier, filePath: "/U/0/2525/DSUM/DSUM.BPB", fileData: mockFileContent())
            XCTFail("writeFile method did not throw an error")
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }
    
    func testReadFile_success() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(mockFileContent())]
        
        // Act
        let result = try await fileUtils.readFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB")
        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: result)
        
        // Assert
        XCTAssertEqual(2, sampleSessions.samples.count)
        XCTAssertEqual(3, sampleSessions.samples[0].heartRate.count)
        XCTAssertEqual(3, sampleSessions.samples[1].heartRate.count)
        XCTAssertEqual(2, sampleSessions.ppiSamples.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[0].ppi.ppiDelta.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[0].ppi.ppiErrorEstimateDelta.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[1].ppi.ppiDelta.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[1].ppi.ppiErrorEstimateDelta.count)
    }
    
    func testReadFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.readFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
            XCTFail("readFile method did not throw an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }
    
    func testListFiles_low_level_recurseDeepFalse_success() async throws {
        // Arrange
        let expectedFiles = ["/U/0/20260101/", "/U/0/20260201/"]
        let responses = makeDirectoryResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        let emitted = try await fileUtils.listFiles(identifier: identifier, directoryPath: "/U/0/", recurseDeep: false)
        
        // Assert
        XCTAssertTrue(emitted.contains(expectedFiles[0]))
        XCTAssertTrue(emitted.contains(expectedFiles[1]))
    }
    
    func testListFiles_low_level_recurseDeepTrue_success() async throws {
        // Arrange
        let expectedFiles = ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260201/R/101010/PPG0.REC", "/U/0/20260201/R/101010/HR0.REC"]
        let responses = makeDirectoryResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedData: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        let emitted = try await fileUtils.listFiles(identifier: identifier, directoryPath: "/U/0/", recurseDeep: true)
        
        // Assert
        XCTAssertTrue(emitted.contains(expectedFiles[0]))
        XCTAssertTrue(emitted.contains(expectedFiles[1]))
        XCTAssertTrue(emitted.contains(expectedFiles[2]))
    }
    
    func testListFiles_low_level_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.listFiles(identifier: identifier, directoryPath: "/U/0/", recurseDeep: true)
            XCTFail("listFiles method did not throw an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }
    
    func testDeleteFile_success() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = [Protocol_PbPFtpEntry.with { $0.name = ""; $0.size = 0 }]
            }
            return try dir.serializedData()
        }
        
        // Act
        try await fileUtils.deleteFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
    }
    
    func testDeleteFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            try await fileUtils.deleteFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
            XCTFail("deleteFile method did not throw an error")
        } catch {
            XCTAssertEqual(mockClient.requestCalls.count, 1)
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }

    // MARK: - Helpers

    private func makeDirectoryResponses() -> [String: [Protocol_PbPFtpEntry]] {
        return [
            "/U/0/": [
                Protocol_PbPFtpEntry.with { $0.name = "20260101/"; $0.size = 0 },
                Protocol_PbPFtpEntry.with { $0.name = "20260201/"; $0.size = 0 }
            ],
            "/U/0/20260101/": [Protocol_PbPFtpEntry.with { $0.name = "DSUM/"; $0.size = 0 }],
            "/U/0/20260101/DSUM/": [Protocol_PbPFtpEntry.with { $0.name = "DSUM.BPB"; $0.size = 1024 }],
            "/U/0/20260201/": [Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }],
            "/U/0/20260201/R/": [Protocol_PbPFtpEntry.with { $0.name = "101010/"; $0.size = 0 }],
            "/U/0/20260201/R/101010/": [
                Protocol_PbPFtpEntry.with { $0.name = "PPG0.REC"; $0.size = 1024 },
                Protocol_PbPFtpEntry.with { $0.name = "HR0.REC"; $0.size = 1024 }
            ]
        ]
    }
    
    private func mockFileContent() -> Data {
        return try! Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [70, 72, 74].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 16; $0.minute = 49; $0.seconds = 36; $0.millis = 0 }
                    $0.triggerType = .triggerTypeLowActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [90, 91, 93].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 18; $0.minute = 0; $0.seconds = 0; $0.millis = 0 }
                    $0.triggerType = .triggerTypeTimed
                }
            ]
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 3; $0.minute = 4; $0.seconds = 5; $0.millis = 6 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = .ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 5; $0.minute = 6; $0.seconds = 7; $0.millis = 8 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = .ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 26 }
        }.serializedData()
    }
}

// MARK: - DateFormatter helper
private extension DateFormatter {
    @discardableResult
    func apply(_ configure: (DateFormatter) -> Void) -> DateFormatter {
        configure(self)
        return self
    }
}
