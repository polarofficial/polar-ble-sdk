//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import XCTest
import RxSwift
@testable import PolarBleSdk

final class PolarFileUtilsTest: XCTestCase {
    
    private var identifier = "E123456F"
    private var mockClient: MockBlePsFtpClient!
    private var mockListener: MockCBDeviceListenerImpl!
    private var mockSession: MockBleDeviceSession!
    private var mockGattServiceTransmitterImpl: MockGattServiceTransmitterImpl!
    private var mockServiceClientUtils: MockPolarServiceClientUtils!
    private var fileUtils: PolarFileUtils!
    private let dateFormatter = DateFormatter()
    
    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        mockListener = MockCBDeviceListenerImpl()
        mockServiceClientUtils = MockPolarServiceClientUtils(listener: mockListener, session: mockSession)
        fileUtils = PolarFileUtils(listener: mockListener, serviceClientUtils: mockServiceClientUtils)
        dateFormatter.dateFormat = "yyyyMMdd"
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
    }
    
    func testListFiles_success() throws {
        
        // Arrange
        var condition: (_ p: String) -> Bool
        condition = { (entry) -> Bool in
            entry.contains(".")
        }
        
        let expectedFiles = ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260201/R/101010/PPG0.REC", "/U/0/20260201/R/101010/HR0.REC"]
        
        let dateEntry1 = Protocol_PbPFtpEntry.with { $0.name = "20260101/"; $0.size = 0 }
        let dateEntry2 = Protocol_PbPFtpEntry.with { $0.name = "20260201/"; $0.size = 0 }
        let dsumDirEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM/"; $0.size = 0 }
        let dsumEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM.BPB"; $0.size = 1024 }
        let offlineDirEntry = Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }
        let offlineTimeEntry = Protocol_PbPFtpEntry.with { $0.name = "101010/"; $0.size = 0 }
        let offlineEntry1 = Protocol_PbPFtpEntry.with { $0.name = "PPG0.REC"; $0.size = 1024 }
        let offlineEntry2 = Protocol_PbPFtpEntry.with { $0.name = "HR0.REC"; $0.size = 1024 }
        
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [dateEntry1, dateEntry2],
            "/U/0/20260101/": [dsumDirEntry],
            "/U/0/20260101/DSUM/": [dsumEntry],
            "/U/0/20260201/": [offlineDirEntry],
            "/U/0/20260201/R/": [offlineTimeEntry],
            "/U/0/20260201/R/101010/": [offlineEntry1, offlineEntry2]
        ]
        
        mockClient.requestReturnValueClosure = { header in
            let op = try! Protocol_PbPFtpOperation(serializedData: header)
            let path = op.path
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = responses[path, default: []]
            }
            return Single.just(try! dir.serializedData())
        }
        
        // Act
        let emitted = try fileUtils.listFiles(identifier: identifier, folderPath: "/U/0/", condition: condition)
            .toBlocking()
            .toArray()
        
        // Assert
        XCTAssertEqual(emitted.count, expectedFiles.count)
        XCTAssert(emitted.contains(expectedFiles[0]))
        XCTAssert(emitted.contains(expectedFiles[1]))
        XCTAssert(emitted.contains(expectedFiles[2]))
    }
    
    func testListFiles_failure() throws {
        
        // Arrange
        var condition: (_ p: String) -> Bool
        condition = { (entry) -> Bool in
            entry.contains(".")
        }
        
        mockClient.requestReturnValueClosure = { header in
            return Single.error(PolarErrors.deviceNotConnected)
        }
        
        // Act & Assert
        do {
            let _ = try fileUtils.listFiles(identifier: identifier, folderPath: "/U/0/", condition: condition)
                .toBlocking()
                .toArray()
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
        
        XCTFail("listFiles method did not throw an error")
    }
    
    func testCheckAutoSampleFile_can_delete_file() throws {
        
        // Arrange
        let mockFileContent = mockFileContent()
        
        //Act
        mockClient.requestReturnValues = [Single.just(mockFileContent)]
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd"
        let date = dateFormatter.date(from: "25251118") // Proto has date 2525-02-26 that is before this until date. checkAutoSampleFile should return true.
        
        let result = try fileUtils.checkAutoSampleFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB", until: date!).toBlocking().first()
        
        //Assert
        XCTAssertEqual(true, result.unsafelyUnwrapped)
    }
    
    func testCheckAutoSampleFile_cannot_delete_file() throws {
        
        // Arrange
        let mockFileContent = mockFileContent()
        
        //Act
        mockClient.requestReturnValues = [Single.just(mockFileContent)]
        
        let date = dateFormatter.date(from: "25250225") // Proto has date 2525-02-26 that is after this until date. checkAutoSampleFile should return false.
        
        let result = try fileUtils.checkAutoSampleFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB", until: date!).toBlocking().first()
        
        //Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertEqual(false, result.unsafelyUnwrapped)
    }
    
    func testCheckAutoSampleFile_failure() throws {
        
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            return Single.error(PolarErrors.deviceNotConnected)
        }
        let date = dateFormatter.date(from: "25250225")
        // Act
        do {
            let _ = try fileUtils.checkAutoSampleFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB", until: date!).toBlocking().first()
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
    }
    
    func testCheckIfDirectoryIsEmpty_is_empty() throws {
        
        // Arrange
        
        let dateEntry1 = Protocol_PbPFtpEntry.with { $0.name = "20260101/"; $0.size = 0 }
        let dsumDirEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM/"; $0.size = 0 }
        
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [dateEntry1],
            "/U/0/20260101/": [dsumDirEntry]
        ]
        
        mockClient.requestReturnValueClosure = { header in
            let op = try! Protocol_PbPFtpOperation(serializedData: header)
            let path = op.path
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = responses[path, default: []]
            }
            return Single.just(try! dir.serializedData())
        }
        
        // Act
        let result = try fileUtils.checkIfDirectoryIsEmpty(directoryPath: "U/0/20260101", client: mockClient).toBlocking().first()
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertTrue(result!)
    }
    
    func testCheckIfDirectoryIsEmpty_is_not_empty() throws {
        
        // Arrange
        let dsumEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM.BPB"; $0.size = 1024 }
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/20260101/DSUM/": [dsumEntry],
        ]
        
        mockClient.requestReturnValueClosure = { header in
            let op = try! Protocol_PbPFtpOperation(serializedData: header)
            let path = op.path
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = responses[path, default: []]
            }
            return Single.just(try! dir.serializedData())
        }
        
        // Act
        let result = try fileUtils.checkIfDirectoryIsEmpty(directoryPath: "/U/0/20260101/DSUM/", client: mockClient).toBlocking().first()
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertFalse(result!)
    }
    
    func testCheckIfDirectoryIsEmpty_failure() throws {
        
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            return Single.error(PolarErrors.deviceNotConnected)
        }
        
        // Act
        do {
            let _ = try fileUtils.checkIfDirectoryIsEmpty(directoryPath: "/U/0/20260101/DSUM/", client: mockClient).toBlocking().first()
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
    }
    
    func testCheckIfDirectoryIsEmpty_error103_file_not_found() throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in Single.error(BlePsFtpException.responseError(errorCode: 103)) }

        // Act
        let result = try fileUtils.checkIfDirectoryIsEmpty(directoryPath: "/U/0/20260101/DSUM/", client: mockClient).toBlocking().first()

        // Assert
        XCTAssertNotNil(result, "Error code 103 should be handled, not thrown")
        XCTAssertTrue(result!, "Error code 103 MUST return true")
    }

    func testRemoveSingleFile_success() throws {
        // Arrange
        mockClient.requestReturnValues = [Single.just(Data([0x00]))]
        
        // Act
        let res = try fileUtils.removeSingleFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB").toBlocking().first()

        if let data = res as? Data {
            let bytes = [UInt8](data)
            XCTAssertEqual(bytes[0], 0)
        } else {
            XCTFail("Expected Data type for removeSingleFile result, got: \(type(of: res))")
        }
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
    }
    
    func testRemoveSingleFile_failure() throws {
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            return Single.error(PolarErrors.deviceNotConnected)
        }
        
        // Act & Assert
        do {
            let _ = try fileUtils.removeSingleFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB").toBlocking().first()
        } catch {
            XCTAssertEqual(mockClient.requestCalls.count, 1)
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
        
        XCTFail("removeSingleFile method did not throw an error")
    }
    
    func testRemoveMultipleFile_success() throws {
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = [Protocol_PbPFtpEntry.with { $0.name = ""; $0.size = 1024 }, Protocol_PbPFtpEntry.with { $0.name = ""; $0.size = 1024 }]
            }
            return Single.just(try! dir.serializedData())
        }
        
        // Act
        _ = try fileUtils.removeMultipleFiles(identifier: identifier, filePaths: ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260102/DSUM/DSUM.BPB"]).toBlocking().first()
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 2)
    }
    
    func testRemoveMultipleFile_failure() throws {
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            return Single.error(PolarErrors.deviceNotConnected)
        }
        
        // Act & Assert
        do {
            let _ = try fileUtils.removeMultipleFiles(identifier: identifier, filePaths: ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260102/DSUM/DSUM.BPB"]).toBlocking().first()
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
        
        XCTFail("removeSingleFile method did not throw an error")
    }
    
    func testGetFile_success() throws {
        // Arrange
        let mockFileContent = mockFileContent()
        
        //Act
        mockClient.requestReturnValues = [
            Single.just(mockFileContent)
        ]
        
        let result = try fileUtils.getFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB").toBlocking().first()!
        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: Data(result))
        
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
    
    func testGetFile_failure() throws {
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            return Single.error(PolarErrors.deviceNotConnected)
        }
        
        // Act & Assert
        do {
            let _ = try fileUtils.getFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB").toBlocking().first()!
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
        
        XCTFail("getFile method did not throw an error")
    }
    
    // Low level API tests
    func testWriteFile_success() throws {
        
        // Arrange
        let mockFileContent = mockFileContent()
        mockClient.writeReturnValue = Observable.just(0)
        
        let _ = try fileUtils.writeFile(identifier: identifier, filePath: "/U/0/2525/DSUM/DSUM.BPB", fileData: mockFileContent).toBlocking().first()
        
        // Assert
        XCTAssertEqual(mockClient.writeCalls.count, 1)
    }
    
    func testWriteFile_failure() throws {
        
        // Arrange
        let mockFileContent = mockFileContent()
        mockClient.writeReturnValue = Observable.error(PolarErrors.deviceNotConnected)
        
        // Act & Assert
        do {
            let _ = try fileUtils.writeFile(identifier: identifier, filePath: "/U/0/2525/DSUM/DSUM.BPB", fileData: mockFileContent).toBlocking().first()
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
        
        XCTFail("writeFile method did not throw an error")
    }
    
    func testReadFile_success() throws {
        
        // Arrange
        let mockFileContent = mockFileContent()
        
        //Act
        mockClient.requestReturnValues = [
            Single.just(mockFileContent)
        ]
        
        let result = try fileUtils.readFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB").toBlocking().first()!
        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedData: Data(result))
        
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
    
    func testReadFile_failure() throws {
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            return Single.error(PolarErrors.deviceNotConnected)
        }
        
        // Act & Assert
        do {
            let _ = try fileUtils.readFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB").toBlocking().first()!
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
        
        XCTFail("getFile method did not throw an error")
    }
    
    func testListFiles_low_level_recurseDeepFalse_success() throws {
        // Arrange
        let expectedFiles = ["/U/0/20260101/", "/U/0/20260201/"]
        
        let dateEntry1 = Protocol_PbPFtpEntry.with { $0.name = "20260101/"; $0.size = 0 }
        let dateEntry2 = Protocol_PbPFtpEntry.with { $0.name = "20260201/"; $0.size = 0 }
        let dsumDirEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM/"; $0.size = 0 }
        let dsumEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM.BPB"; $0.size = 1024 }
        let offlineDirEntry = Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }
        let offlineTimeEntry = Protocol_PbPFtpEntry.with { $0.name = "101010/"; $0.size = 0 }
        let offlineEntry1 = Protocol_PbPFtpEntry.with { $0.name = "PPG0.REC"; $0.size = 1024 }
        let offlineEntry2 = Protocol_PbPFtpEntry.with { $0.name = "HR0.REC"; $0.size = 1024 }
        
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [dateEntry1, dateEntry2],
            "/U/0/20260101/": [dsumDirEntry],
            "/U/0/20260101/DSUM/": [dsumEntry],
            "/U/0/20260201/": [offlineDirEntry],
            "/U/0/20260201/R/": [offlineTimeEntry],
            "/U/0/20260201/R/101010/": [offlineEntry1, offlineEntry2]
        ]
        
        mockClient.requestReturnValueClosure = { header in
            let op = try! Protocol_PbPFtpOperation(serializedData: header)
            let path = op.path
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = responses[path, default: []]
            }
            return Single.just(try! dir.serializedData())
        }
        
        // Act
        let emitted = try fileUtils.listFiles(identifier: identifier, directoryPath: "/U/0/", recurseDeep: false)
            .toBlocking()
            .toArray()
        
        // Assert
        let _ = emitted.compactMap { it in
            XCTAssertEqual(it[0], expectedFiles[0])
            XCTAssertEqual(it[1], expectedFiles[1])
        }
    }
    
    func testListFiles_low_level_recurseDeepTrue_success() throws {
        // Arrange
        let expectedFiles = ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260201/R/101010/PPG0.REC", "/U/0/20260201/R/101010/HR0.REC"]
        
        let dateEntry1 = Protocol_PbPFtpEntry.with { $0.name = "20260101/"; $0.size = 0 }
        let dateEntry2 = Protocol_PbPFtpEntry.with { $0.name = "20260201/"; $0.size = 0 }
        let dsumDirEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM/"; $0.size = 0 }
        let dsumEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM.BPB"; $0.size = 1024 }
        let offlineDirEntry = Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }
        let offlineTimeEntry = Protocol_PbPFtpEntry.with { $0.name = "101010/"; $0.size = 0 }
        let offlineEntry1 = Protocol_PbPFtpEntry.with { $0.name = "PPG0.REC"; $0.size = 1024 }
        let offlineEntry2 = Protocol_PbPFtpEntry.with { $0.name = "HR0.REC"; $0.size = 1024 }
        
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [dateEntry1, dateEntry2],
            "/U/0/20260101/": [dsumDirEntry],
            "/U/0/20260101/DSUM/": [dsumEntry],
            "/U/0/20260201/": [offlineDirEntry],
            "/U/0/20260201/R/": [offlineTimeEntry],
            "/U/0/20260201/R/101010/": [offlineEntry1, offlineEntry2]
        ]
        
        mockClient.requestReturnValueClosure = { header in
            let op = try! Protocol_PbPFtpOperation(serializedData: header)
            let path = op.path
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = responses[path, default: []]
            }
            return Single.just(try! dir.serializedData())
        }
        
        // Act
        let emitted = try fileUtils.listFiles(identifier: identifier, directoryPath: "/U/0/", recurseDeep: true)
            .toBlocking()
            .toArray()
        
        // Assert
        let _ = emitted.compactMap { it in
            XCTAssertEqual(it[0], expectedFiles[0])
            XCTAssertEqual(it[1], expectedFiles[1])
            XCTAssertEqual(it[2], expectedFiles[2])
        }
    }
    
    func testListFiles_low_level_failure() throws {
        
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            return Single.error(PolarErrors.deviceNotConnected)
        }
        
        // Act & Assert
        do {
            let _ = try fileUtils.listFiles(identifier: identifier, directoryPath: "/U/0/", recurseDeep: true)
                .toBlocking()
                .toArray()
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
        
        XCTFail("listFiles method did not throw an error")
    }
    
    func testDeleteFile_success() throws {
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = [Protocol_PbPFtpEntry.with { $0.name = ""; $0.size = 0 }]
            }
            return Single.just(try! dir.serializedData())
        }
        
        // Act
        let _ = try fileUtils.deleteFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB").toBlocking().first()
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
    }
    
    func testDeleteFile_failure() throws {
        
        // Arrange
        mockClient.requestReturnValueClosure = { header in
            return Single.error(PolarErrors.deviceNotConnected)
        }
        
        // Act & Assert
        do {
            let _ = try fileUtils.deleteFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB").toBlocking().first()
        } catch {
            XCTAssertEqual(mockClient.requestCalls.count, 1)
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
            return
        }
        
        XCTFail("deleteFile method did not throw an error")
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
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = .ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 5; $0.minute = 6; $0.seconds = 7; $0.millis = 8 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0)}
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0)}
                        $0.status = [1,2,3,4].map { UInt32($0)}
                    }
                    $0.triggerType = .ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 26 }
        }.serializedData()
    }
}
