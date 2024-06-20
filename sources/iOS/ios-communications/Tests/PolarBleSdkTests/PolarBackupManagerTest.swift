import XCTest
import RxSwift
import RxTest
import Foundation


class PolarBackupManagerTest: XCTestCase {

    var mockClient: MockBlePsFtpClient!
    var backupManager: PolarBackupManager!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient()
        backupManager = PolarBackupManager(client: mockClient)
    }

    override func tearDownWithError() throws {
        mockClient = nil
        backupManager = nil
    }

    func testBackupDevice() {
        // Arrange
        let mockBackupFileContent = "/SYS/BT/\n/U/*/USERID.BPB\n/RANDOM/FILE.TXT\n"
        
        var entry = Protocol_PbPFtpEntry()
        entry.name = "/SYS/BT/BTDEV.BPB"
        entry.size = 1234
        
        var entry2 = Protocol_PbPFtpEntry()
        entry2.name = "/SYS/BT/SVSTATUS.BPB"
        entry2.size = 5678
        
        var directory = Protocol_PbPFtpDirectory()
        directory.entries = [entry, entry2]
    
        let mockDirectoryContent = try! directory.serializedData()

        mockClient.requestReturnValue = Single.just(mockBackupFileContent.data(using: .utf8)!)
        
        mockClient.directoryContentReturnValue = Single.just(mockDirectoryContent)

        // Act
        let expectation = XCTestExpectation(description: "Backup device")
        let disposable = backupManager.backupDevice().subscribe { event in
            switch event {
            case .success(let files):
                // Assert
                XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/U/0/USERID.BPB" })
                XCTAssertTrue(files.contains { $0.directory + $0.fileName  == "/SYS/BT/BTDEV.BPB" })
                XCTAssertTrue(files.contains { $0.directory + $0.fileName  == "/SYS/BT/SVSTATUS.BPB" })
                XCTAssertTrue(files.contains { $0.directory + $0.fileName  == "/RANDOM/FILE.TXT" })
                expectation.fulfill()
            case .failure(let error):
                XCTFail("Backup failed with error: \(error)")
            }
        }
        XCTAssertEqual(mockClient.requestCalls.count, 6)
        
        wait(for: [expectation], timeout: 5)
        disposable.dispose()
    }

    func testRestoreBackup() {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/SYS/BT/", fileName: "BTDEV.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/SYS/BT/", fileName: "SVSTATUS.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/RANDOM/", fileName: "FILE.TXT")
        ]

        mockClient.writeReturnValue = Completable.empty()

        // Act
        let expectation = XCTestExpectation(description: "Restore backup")
        let disposable = backupManager.restoreBackup(backupFiles: mockFileData).subscribe {
            switch $0 {
            case .completed:
                expectation.fulfill()
            case .error(let error):
                XCTFail("Restore failed with error: \(error)")
            }
        }
        // Assert
        XCTAssertEqual(mockClient.writeCalls.count, 3)
        
        wait(for: [expectation], timeout: 5)
        disposable.dispose()

    }
}
