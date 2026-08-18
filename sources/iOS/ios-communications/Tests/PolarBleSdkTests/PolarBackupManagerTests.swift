//  Copyright © 2026 Polar. All rights reserved.

import XCTest
import Foundation
@testable import PolarBleSdk

class PolarBackupManagerTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!
    var mockLowLevelApi: MockPolarBleLowLevelApi!
    var backupManager: PolarBackupManager!
    let testDeviceId = "12345678"

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockLowLevelApi = MockPolarBleLowLevelApi()
        backupManager = PolarBackupManager(client: mockClient, identifier: testDeviceId, lowLevelApi: mockLowLevelApi)
    }

    override func tearDownWithError() throws {
        mockClient = nil
        mockLowLevelApi = nil
        backupManager = nil
    }

    func testBackupDevice() async throws {
        // Arrange
        let mockBackupFileContent = "/SYS/BT/\n/U/*/USERID.BPB\n/RANDOM/FILE.TXT\n"

        var backupEntry = Protocol_PbPFtpEntry()
        backupEntry.name = "BACKUP.TXT"
        backupEntry.size = 1234

        var btEntry = Protocol_PbPFtpEntry()
        btEntry.name = "BT/"
        btEntry.size = 0

        var directory = Protocol_PbPFtpDirectory()
        directory.entries = [backupEntry, btEntry]
        let mockDirectoryContent = try directory.serializedData()

        var btDevEntry = Protocol_PbPFtpEntry()
        btDevEntry.name = "BTDEV.BPB"
        btDevEntry.size = 1234

        var svStatusEntry = Protocol_PbPFtpEntry()
        svStatusEntry.name = "SVSTATUS.BPB"
        svStatusEntry.size = 5678

        var btDetailDirectory = Protocol_PbPFtpDirectory()
        btDetailDirectory.entries = [btDevEntry, svStatusEntry]
        let mockBTDetailContent = try btDetailDirectory.serializedData()

        mockClient.requestReturnValueClosure = { requestData in
            let request = try Protocol_PbPFtpOperation(serializedBytes: requestData as Data)
            if request.path.contains("/SYS/BACKUP.TXT") {
                return mockBackupFileContent.data(using: .utf8)!
            } else if request.path.contains("/SYS/BT/") {
                return mockBTDetailContent
            } else if request.path.contains("/SYS/") {
                return mockDirectoryContent
            } else {
                return Data()
            }
        }

        // Act
        let files = try await backupManager.backupDevice()

        // Assert
        XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/U/0/USERID.BPB" })
        XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/SYS/BT/BTDEV.BPB" })
        XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/SYS/BT/SVSTATUS.BPB" })
        XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/RANDOM/FILE.TXT" })
        XCTAssertEqual(mockClient.requestCalls.count, 10)
    }

    func testRestoreBackup() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/SYS/BT/", fileName: "BTDEV.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/SYS/BT/", fileName: "SVSTATUS.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/RANDOM/", fileName: "FILE.TXT")
        ]

        mockClient.writeReturnValue = AsyncThrowingStream { $0.finish() }

        // Act
        try await backupManager.restoreBackup(backupFiles: mockFileData)

        // Assert
        XCTAssertEqual(mockClient.writeCalls.count, 3)
    }
    
    func testRestoreBackup_createsNestedFolderPaths() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/U/0/S/PHYSDATA/", fileName: "test.bpb")
        ]
        
        mockClient.writeReturnValue = AsyncThrowingStream { $0.finish() }
        
        // Act
        try await backupManager.restoreBackup(backupFiles: mockFileData)
        
        // Assert
        // Should create folders: /U/0/, /U/0/S/, /U/0/S/PHYSDATA/
        XCTAssertEqual(mockLowLevelApi.createFolderCalls.count, 3)
        XCTAssertEqual(mockLowLevelApi.createFolderCalls[0].identifier, testDeviceId)
        XCTAssertEqual(mockLowLevelApi.createFolderCalls[0].folderPath, "/U/0/")
        XCTAssertEqual(mockLowLevelApi.createFolderCalls[1].folderPath, "/U/0/S/")
        XCTAssertEqual(mockLowLevelApi.createFolderCalls[2].folderPath, "/U/0/S/PHYSDATA/")
        XCTAssertEqual(mockClient.writeCalls.count, 1)
    }
    
    func testRestoreBackup_onlyCreatesUserFolders() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/SYS/BT/", fileName: "BTDEV.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/U/0/S/", fileName: "PREFS.BPB")
        ]
        
        mockClient.writeReturnValue = AsyncThrowingStream { $0.finish() }
        
        // Act
        try await backupManager.restoreBackup(backupFiles: mockFileData)
        
        // Assert
        // Folder paths under /SYS/ should be filtered out
        // Only /U/0/ and /U/0/S/ should be created
        XCTAssertEqual(mockLowLevelApi.createFolderCalls.count, 2)
        XCTAssertEqual(mockLowLevelApi.createFolderCalls[0].folderPath, "/U/0/")
        XCTAssertEqual(mockLowLevelApi.createFolderCalls[1].folderPath, "/U/0/S/")
        XCTAssertEqual(mockClient.writeCalls.count, 2)
    }
    
    func testRestoreBackup_continuesOnFolderCreationFailure() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/U/0/S/", fileName: "PREFS.BPB")
        ]
        
        mockClient.writeReturnValue = AsyncThrowingStream { $0.finish() }
        mockLowLevelApi.createFolderShouldThrow = NSError(domain: "TestError", code: -1, userInfo: nil)
        
        // Act & Assert
        do {
            try await backupManager.restoreBackup(backupFiles: mockFileData)
            
            // Assert
            // Even though folder creation failed, the file restore should still proceed
            XCTAssertEqual(mockLowLevelApi.createFolderCalls.count, 2, "Should attempt to create 2 folders")
            XCTAssertEqual(mockClient.writeCalls.count, 1, "Should attempt to write 1 file")
        } catch {
            let nsError = error as NSError
            print("Unexpected error thrown: \(nsError)")
            print("Error message: \(nsError.localizedDescription)")
            print("Created folders: \(mockLowLevelApi.createFolderCalls.count)")
            print("Write calls: \(mockClient.writeCalls.count)")
            XCTFail("Should not throw error when only folder creation fails. Error: \(error)")
        }
    }
    
    func testRestoreBackup_handlesDuplicateFolderPaths() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/U/0/S/", fileName: "PHYSDATA.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/U/0/S/", fileName: "UDEVSET.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/U/0/S/", fileName: "PREFS.BPB")
        ]
        
        mockClient.writeReturnValue = AsyncThrowingStream { $0.finish() }
        
        // Act
        try await backupManager.restoreBackup(backupFiles: mockFileData)
        
        // Assert
        // Should create /U/0/ and /U/0/S/ only once each, even though they appear for all 3 files
        // Total folder creation calls should be 2 (one for /U/0/, one for /U/0/S/)
        XCTAssertEqual(mockLowLevelApi.createFolderCalls.count, 2)
        // Verify folders are created only once
        let folderPathsCreated = mockLowLevelApi.createFolderCalls.map { $0.folderPath }
        XCTAssertEqual(folderPathsCreated.filter { $0 == "/U/0/" }.count, 1)
        XCTAssertEqual(folderPathsCreated.filter { $0 == "/U/0/S/" }.count, 1)
        XCTAssertEqual(mockClient.writeCalls.count, 3)
    }
    
    func testRestoreBackup_throwsErrorWhenFileRestoreFails() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/U/0/", fileName: "USERID.BPB")
        ]
        
        mockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: NSError(domain: "TestError", code: -1, userInfo: nil))
        }
        
        // Act & Assert
        do {
            try await backupManager.restoreBackup(backupFiles: mockFileData)
            XCTFail("Expected error to be thrown")
        } catch {
            let nsError = error as NSError
            print("Error domain: \(nsError.domain)")
            print("Error code: \(nsError.code)")
            print("Error message: \(nsError.localizedDescription)")
            print("User info: \(nsError.userInfo)")
            XCTAssertEqual(nsError.domain, "PolarBackupManager")
            XCTAssertEqual(nsError.code, -1)
            let message = nsError.localizedDescription
            XCTAssertTrue(message.contains("Restore failed"), "Error message should mention restore failure: \(message)")
            // Check that exactly 1 file failed (the message format is "Restore failed for 1 file(s): ...")
            XCTAssertTrue(message.contains("1 file"), "Error message should mention 1 failed file: \(message)")
        }
    }
    
    func testRestoreBackup_continuesAfterPartialFailure() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/U/0/", fileName: "USERID.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/U/0/S/", fileName: "PREFS.BPB")
        ]
        
        mockClient.writeReturnValues = [
            AsyncThrowingStream { continuation in
                continuation.finish(throwing: NSError(domain: "TestError", code: -1, userInfo: nil))
            },
            AsyncThrowingStream { continuation in
                continuation.finish()
            }
        ]
        
        // Act & Assert
        do {
            try await backupManager.restoreBackup(backupFiles: mockFileData)
            XCTFail("Expected error to be thrown")
        } catch {
            // Should have attempted to restore both files despite first failure
            XCTAssertEqual(mockClient.writeCalls.count, 2, "Should attempt to restore both files")
            let nsError = error as NSError
            let message = nsError.localizedDescription
            print("Error message: \(message)")
            XCTAssertTrue(message.contains("Restore failed"), "Error message should mention restore failure: \(message)")
            // Check that exactly 1 file failed out of 2 attempted
            XCTAssertTrue(message.contains("1 file"), "Error message should mention 1 failed file (out of 2 attempted): \(message)")
        }
    }
    
    func testRestoreBackup_handlesRootDirectoryWithoutCrashing() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/", fileName: "ROOT_FILE.BPB")
        ]
        
        mockClient.writeReturnValue = AsyncThrowingStream { $0.finish() }
        
        // Act
        try await backupManager.restoreBackup(backupFiles: mockFileData)
        
        // Assert
        // Root directory has no segments, so no folder creation should occur
        XCTAssertEqual(mockLowLevelApi.createFolderCalls.count, 0, "Should not attempt folder creation for root directory")
        XCTAssertEqual(mockClient.writeCalls.count, 1, "Should still attempt to write the file")
    }
    
    func testRestoreBackup_handlesEmptyDirectoryWithoutCrashing() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "", fileName: "ORPHAN_FILE.BPB")
        ]
        
        mockClient.writeReturnValue = AsyncThrowingStream { $0.finish() }
        
        // Act
        try await backupManager.restoreBackup(backupFiles: mockFileData)
        
        // Assert
        // Empty directory has no segments, so no folder creation should occur
        XCTAssertEqual(mockLowLevelApi.createFolderCalls.count, 0, "Should not attempt folder creation for empty directory")
        XCTAssertEqual(mockClient.writeCalls.count, 1, "Should still attempt to write the file")
    }
}
