//  Copyright © 2024 Polar. All rights reserved.

import XCTest
import RxSwift

class PolarFirmwareUpdateUtilsTest: XCTestCase {

    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient()
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testReadDeviceFirmwareInfo_shouldReturnFirmwareInfo() {
        // Arrange
        let expectedDeviceId = "123456"
        let expectedFirmwareVersion = "1.2.0"
        let expectedModelName = "Model"
        let expectedHardwareCode = "00.112233"
        
        let proto = Data_PbDeviceInfo.with {
            $0.deviceVersion = .with {
                $0.major = 1
                $0.minor = 2
                $0.patch = 0
            }
            $0.modelName = expectedModelName
            $0.hardwareCode = expectedHardwareCode
        }
    
        let mockResponseData = try! proto.serializedData()
        mockClient.requestReturnValue = Single.just(mockResponseData)

        // Act
        let firmwareInfo = PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: mockClient, deviceId: expectedDeviceId)

        // Assert
        XCTAssertEqual(firmwareInfo?.deviceFwVersion, expectedFirmwareVersion)
        XCTAssertEqual(firmwareInfo?.deviceModelName, expectedModelName)
        XCTAssertEqual(firmwareInfo?.deviceHardwareCode, expectedHardwareCode)
        XCTAssertEqual(mockClient.requestCalls.count, 1)
    }
    
    func testIsAvailableFirmwareVersionHigher_shouldReturnTrue_whenCurrentVersionIsSmallerThanAvailableVersion() {
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "1.0.0",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "2.0.1"
            )
        )
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "2.1.0"
            )
        )
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "3.0.0"
            )
        )
    }
    
    func testIsAvailableFirmwareVersionHigher_shouldReturnFalse_whenCurrentVersionIsSameOrHigherThanAvailableVersion() {
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "1.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.1",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.1.0",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "3.0.0",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "2.0.0"
            )
        )
    }
    
    func testFwFileComparatorSortsFilesCorrectly() {
           // Arrange
           let btFile = "BTUPDAT.BIN"
           let sysFile = "SYSUPDAT.IMG"
           let touchFile = "TCHUPDAT.BIN"
           var files = [btFile, sysFile, touchFile]
           
           // Act
           files.sort { PolarFirmwareUpdateUtils.FwFileComparator.compare($0, $1) == .orderedAscending }
           
           // Assert
           XCTAssertEqual(files[0], btFile, "First file should be BTUPDAT.BIN")
           XCTAssertEqual(files[1], touchFile, "Second file should be TCHUPDAT.BIN")
           XCTAssertEqual(files[2], sysFile, "Last file should be SYSUPDAT.IMG")
       }
       
       func testFwFileComparatorKeepsAlreadySortedFiles() {
           // Arrange
           let f1 = "BTUPDAT.BIN"
           let f2 = "TCHUPDAT.BIN"
           let f3 = "SYSUPDAT.IMG"
           var files = [f1, f2, f3]
           
           // Act
           files.sort { PolarFirmwareUpdateUtils.FwFileComparator.compare($0, $1) == .orderedAscending }
           
           // Assert
           XCTAssertEqual(files[0], f1, "Files should maintain initial order if already sorted")
           XCTAssertEqual(files[1], f2, "Files should maintain initial order if already sorted")
           XCTAssertEqual(files[2], f3, "Files should maintain initial order if already sorted")
       }
}
