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

    // MARK: - convertArrayToUnsignedInt
    // Values with the top bit set must not be sign-extended.

    func testArrayConversionToUnsignedIntZero() throws {
        XCTAssertEqual(0, TypeUtils.convertArrayToUnsignedInt(Data([0x00, 0x00])))
    }

    func testArrayConversionToUnsignedIntBelowSignBoundary() throws {
        XCTAssertEqual(32767, TypeUtils.convertArrayToUnsignedInt(Data([0xFF, 0x7F])))
    }

    func testArrayConversionToUnsignedIntAtSignBoundary() throws {
        // Previously sign-extended to 0xFFFF_FFFF_FFFF_8000.
        XCTAssertEqual(32768, TypeUtils.convertArrayToUnsignedInt(Data([0x00, 0x80])))
    }

    func testArrayConversionToUnsignedIntMaxTwoBytes() throws {
        XCTAssertEqual(65535, TypeUtils.convertArrayToUnsignedInt(Data([0xFF, 0xFF])))
    }

    func testArrayConversionToUnsignedIntMaxFourBytes() throws {
        XCTAssertEqual(4294967295, TypeUtils.convertArrayToUnsignedInt(Data([0xFF, 0xFF, 0xFF, 0xFF])))
    }

    func testArrayConversionToUnsignedIntHeaderMagic() throws {
        // Distinct bytes verify little-endian order.
        XCTAssertEqual(0x3D7C4C2B, TypeUtils.convertArrayToUnsignedInt(Data([0x2B, 0x4C, 0x7C, 0x3D])))
    }

    func testArrayConversionToUnsignedIntWithOffset() throws {
        let buffer = Data([0xAA, 0x00, 0x80, 0xBB])
        XCTAssertEqual(32768, TypeUtils.convertArrayToUnsignedInt(buffer, offset: 1, size: 2))
    }
}
