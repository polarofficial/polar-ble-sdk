import XCTest
import Foundation
@testable import PolarBleSdk

class PolarBackupManagerTest: XCTestCase {

    var mockClient: MockBlePsFtpClient!
    var backupManager: PolarBackupManager!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        backupManager = PolarBackupManager(client: mockClient)
    }

    override func tearDownWithError() throws {
        mockClient = nil
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
            let request = try Protocol_PbPFtpOperation(serializedData: requestData, partial: false)
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
}
