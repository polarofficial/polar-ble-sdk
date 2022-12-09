//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class TypeUtilsTest: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    func testArrayConversionToSignedIntMaxValue() throws {
        // Arrange
        let byteArray = Data([0xFF, 0xFF, 0xFF, 0xFF])
        let expectedValue:Int32 = -1

        // Act
        let result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        XCTAssertEqual(expectedValue, result)
    }
    
    func testArrayConversionToSignedIntMinValue() throws {
        // Arrange
        let byteArray = Data([0x00, 0x00, 0x00, 0x00])
        let expectedValue:Int32 = 0

        // Act
        let result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        XCTAssertEqual(expectedValue, result)
    }
    
    func testArrayConversionToSignedIntMaxPositiveInt() throws {
        // Arrange
        let byteArray = Data([0xFF, 0xFF, 0xFF, 0x7F])
        let expectedValue = Int32.max

        // Act
        let result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        XCTAssertEqual(expectedValue, result)
    }

    func testArrayConversionToSignedIntSmallArray() throws {
        // Arrange
        let byteArray = Data([0xFF])
        let expectedValue:Int32 = -1

        // Act
        let result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        XCTAssertEqual(expectedValue, result)
    }
}
