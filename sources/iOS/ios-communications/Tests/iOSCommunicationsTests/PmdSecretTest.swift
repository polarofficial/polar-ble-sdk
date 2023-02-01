//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class PmdSecretTest: XCTestCase {
    
    let key16bytes = Data([
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
    ])
    
    func testSerializationStrategyNONE() throws {
        //Arrange
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.none, key: Data())
        
        //Act
        let serialized = pmdSecret.serializeToPmdSettings()
        
        //Assert
        XCTAssertEqual(3, serialized.count)
        XCTAssertEqual(PmdSetting.PmdSettingType.security.rawValue, serialized[0])
        XCTAssertEqual(1, serialized[1])
        XCTAssertEqual(PmdSecret.SecurityStrategy.none.rawValue, serialized[2])
    }
    
    func testSerializationStrategyXOR() throws {
        //Arrange
        let expectedKey = Data([0xFF])
        //val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.XOR, key = expectedKey)
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.xor, key: expectedKey)
        
        //Act
        let serialized = pmdSecret.serializeToPmdSettings()
        
        //Assert
        XCTAssertEqual(1 + 1 + 1 + 1, serialized.count)
        XCTAssertEqual(PmdSetting.PmdSettingType.security.rawValue, serialized[0])
        XCTAssertEqual(1, serialized[1])
        XCTAssertEqual(PmdSecret.SecurityStrategy.xor.rawValue, serialized[2])
        XCTAssertEqual(expectedKey, serialized[3...])
    }
    
    func testSerializationStrategy128() throws {
        //Arrange
        let expectedKey = Data(key16bytes.reversed())
        //val expectedKey = key16bytes.reversed().toByteArray()
        //val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES128, key = expectedKey)
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.aes128, key: expectedKey)
        
        //Act
        let serialized = pmdSecret.serializeToPmdSettings()
        
        //Assert
        XCTAssertEqual(1 + 1 + 1 + 16, serialized.count)
        XCTAssertEqual(PmdSetting.PmdSettingType.security.rawValue, serialized[0])
        XCTAssertEqual(1, serialized[1])
        XCTAssertEqual(PmdSecret.SecurityStrategy.aes128.rawValue, serialized[2])
        XCTAssertEqual(expectedKey, serialized[3...])
    }
    
    func testSerializationStrategy256() throws {
        //Arrange
        let expectedKey = key16bytes + Data(key16bytes.reversed())
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.aes256, key: expectedKey)
        
        //Act
        let serialized = pmdSecret.serializeToPmdSettings()
        
        //Assert
        XCTAssertEqual(1 + 1 + 1 + 32, serialized.count)
        XCTAssertEqual(PmdSetting.PmdSettingType.security.rawValue, serialized[0])
        XCTAssertEqual(1, serialized[1])
        XCTAssertEqual(PmdSecret.SecurityStrategy.aes256.rawValue, serialized[2])
        XCTAssertEqual(expectedKey, serialized[3...])
    }
    
    func testDecryptionStrategyNone() throws {
        //Arrange
        let chipper = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
        ])
        
        let expectedData = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
        ])
        
        let key = Data()
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.none, key: key)
        
        //Act
        let decrypted = try pmdSecret.decryptArray(cipherArray: chipper)
        
        //Assert
        XCTAssertEqual(expectedData, decrypted)
    }
    
    func testDecryptionStrategyXOR() throws {
        //Arrange
        let chipper = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
        ])
        
        let expectedData = Data([
            0x55, 0x54, 0x57, 0x56, 0x51, 0x50, 0x53, 0x52, 0x5D, 0x5C, 0x5F, 0x5E, 0x59, 0x58, 0x5B, 0xAA,
        ])
        
        let key = Data([0x55])
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.xor, key: key)
        
        //Act
        let decrypted = try pmdSecret.decryptArray(cipherArray: chipper)
        
        //Assert
        XCTAssertEqual(expectedData, decrypted)
    }
    
    func testDecryptionStrategy128() throws {
        //Arrange
        let chipper = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF,
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF,
            0xFF, 0xFF, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
            
        ])
        
        let expectedData = Data([
            0x60, 0x08, 0x6b, 0xda, 0x00, 0xdb, 0x42, 0x62, 0x34, 0x60, 0x27, 0x43, 0x71, 0xa7, 0x53, 0x68,
            0x60, 0x08, 0x6b, 0xda, 0x00, 0xdb, 0x42, 0x62, 0x34, 0x60, 0x27, 0x43, 0x71, 0xa7, 0x53, 0x68,
            0x6f, 0x5e, 0x05, 0x8b, 0x37, 0xdd, 0xd1, 0xed, 0x0e, 0xf2, 0x89, 0xef, 0xf8, 0xb2, 0x85, 0x54,
        ])
        
        let key = key16bytes
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.aes128, key: key)
        
        //Act
        let decrypted = try pmdSecret.decryptArray(cipherArray: chipper)
        
        //Assert
        XCTAssertEqual(expectedData, decrypted)
    }
    
    func testDecryptionStrategy256() throws {
        //Arrange
        let chipper = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF,
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF,
            0xFF, 0xFF, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
            
        ])
        
        let expectedData = Data([
            0xc8, 0x0d, 0x56, 0xbb, 0x97, 0x7a, 0x42, 0x5f, 0x5a, 0xa1, 0xcd, 0xfc, 0x24, 0xa2, 0x78, 0x12,
            0xc8, 0x0d, 0x56, 0xbb, 0x97, 0x7a, 0x42, 0x5f, 0x5a, 0xa1, 0xcd, 0xfc, 0x24, 0xa2, 0x78, 0x12,
            0x30, 0x04, 0xb9, 0x9f, 0x6f, 0xfa, 0x3b, 0xb7, 0x73, 0xb1, 0x75, 0xa5, 0x23, 0x5d, 0xcb, 0x93
            
        ])
        
        let key = key16bytes + key16bytes
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.aes256, key: key)
        
        //Act
        let decrypted = try pmdSecret.decryptArray(cipherArray: chipper)
        
        //Assert
        XCTAssertEqual(expectedData, decrypted)
    }
}
