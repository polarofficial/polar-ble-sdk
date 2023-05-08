//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarDiskSpaceDataTest: XCTestCase {
    
    func testFromProto() throws {
        // Arrange
        let proto = Protocol_PbPFtpDiskSpaceResult.with {
            $0.fragmentSize = 512
            $0.totalFragments = 2048
            $0.freeFragments = 1024
        }
        // Act
        let result = PolarDiskSpaceData.fromProto(proto: proto)
        
        // Assert
        XCTAssertEqual(1048576, result.totalSpace)
        XCTAssertEqual(524288, result.freeSpace)
    }
}
