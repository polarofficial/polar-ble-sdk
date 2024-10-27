//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import XCTest

class PolarDeviceUuidTest: XCTestCase {

    func testFromDeviceIdShouldGeneratePolarDeviceUuidString() throws {
        // Arrange
        let deviceId = "89643A20"
        let expectedUuid = "0e030000-0084-0000-0000-000089643A20"

        // Act
        let generatedUuid = try PolarDeviceUuid.fromDeviceId(deviceId)

        // Assert
        XCTAssertEqual(expectedUuid, generatedUuid)
    }

    func testFromDeviceIdShouldThrowExceptionIfDeviceIdIsInvalid() {
        // Arrange
        let invalidDeviceId = "123456789"

        // Act & Assert
        XCTAssertThrowsError(try PolarDeviceUuid.fromDeviceId(invalidDeviceId)) { error in
            XCTAssertTrue(error is PolarDeviceUuid.PolarDeviceUuidError)
            if case let PolarDeviceUuid.PolarDeviceUuidError.invalidDeviceIdLength(expected, actual) = error {
                XCTAssertEqual(expected, 8)
                XCTAssertEqual(actual, 9)
            } else {
                XCTFail("Expected invalidDeviceIdLength error")
            }
        }
    }
}
