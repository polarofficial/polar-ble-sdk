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

        var backupEntry = Protocol_PbPFtpEntry()
        backupEntry.name = "BACKUP.TXT"
        backupEntry.size = 1234

        var btEntry = Protocol_PbPFtpEntry()
        btEntry.name = "BT/"
        btEntry.size = 0

        var directory = Protocol_PbPFtpDirectory()
        directory.entries = [backupEntry, btEntry]

        let mockDirectoryContent = try! directory.serializedData()

        var btDevEntry = Protocol_PbPFtpEntry()
        btDevEntry.name = "BTDEV.BPB"
        btDevEntry.size = 1234

        var svStatusEntry = Protocol_PbPFtpEntry()
        svStatusEntry.name = "SVSTATUS.BPB"
        svStatusEntry.size = 5678

        var btDetailDirectory = Protocol_PbPFtpDirectory()
        btDetailDirectory.entries = [btDevEntry, svStatusEntry]

        let mockBTDetailContent = try! btDetailDirectory.serializedData()

        mockClient.requestReturnValueClosure = { requestData in
            let requestDataString = String(data: requestData, encoding: .utf8) ?? ""
            
            if requestDataString.contains("/SYS/") {
                return Single.just(mockDirectoryContent)
            } else if requestDataString.contains("/SYS/BACKUP.TXT") {
                return Single.just(mockBackupFileContent.data(using: .utf8)!)
            } else if requestDataString.contains("/SYS/BT/") {
                return Single.just(mockBTDetailContent)
            } else {
                return Single.just(Data())
            }
        }

        // Act
        let expectation = XCTestExpectation(description: "Backup device")
        let disposable = backupManager.backupDevice().subscribe { event in
            switch event {
            case .success(let files):
                // Assert
                XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/U/0/USERID.BPB" })
                XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/SYS/BT/BTDEV.BPB" })
                XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/SYS/BT/SVSTATUS.BPB" })
                XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/RANDOM/FILE.TXT" })
                expectation.fulfill()
            case .failure(let error):
                XCTFail("Backup failed with error: \(error)")
            }
        }
        XCTAssertEqual(mockClient.requestCalls.count, 7)

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
