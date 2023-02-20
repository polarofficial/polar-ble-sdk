//  Copyright © 2023 Polar. All rights reserved.

import Foundation
import CryptoKit
import CommonCrypto

public struct PmdSecret {
    let strategy: SecurityStrategy
    let key: Data
    private let keySymmetric: SymmetricKey?
    
    init(strategy: SecurityStrategy, key: Data) throws {
        switch(strategy) {
        case .none:
            guard key.isEmpty else {
                throw BleGattException.gattSecurityError(description: "key shall be empty for \(SecurityStrategy.none), key size was \(key.count)")
            }
            self.keySymmetric = nil
        case .xor:
            guard !key.isEmpty else {
                throw BleGattException.gattSecurityError(description: "key shall not be empty for \(SecurityStrategy.xor), key size was \(key.count)")
            }
            self.keySymmetric = nil
        case .aes128:
            guard key.count == 16 else {
                throw BleGattException.gattSecurityError(description: "key must be size of 16 bytes for \(SecurityStrategy.aes128), key size was \(key.count)")
            }
            self.keySymmetric = SymmetricKey(data: key)
            
        case .aes256:
            guard key.count == 32 else {
                throw BleGattException.gattSecurityError(description: "key must be size of 32 bytes for \(SecurityStrategy.aes256), key size was \(key.count)")
            }
            self.keySymmetric = SymmetricKey(data: key)
        }
        self.strategy = strategy
        self.key = key
    }
    
    func serializeToPmdSettings() -> Data {
        switch(self.strategy) {
        case .none:
            let securitySetting = PmdSetting.PmdSettingType.security.rawValue
            let securityStrategyByte = SecurityStrategy.none.rawValue
            let length:UInt8 = 1
            return Data([securitySetting, length, securityStrategyByte])
        case .xor:
            let securitySetting = PmdSetting.PmdSettingType.security.rawValue
            let securityStrategyByte = SecurityStrategy.xor.rawValue
            let keyBytes = key
            let length:UInt8 = 1
            return [securitySetting, length, securityStrategyByte] + keyBytes
        case .aes128:
            let securitySetting = PmdSetting.PmdSettingType.security.rawValue
            let securityStrategyByte = SecurityStrategy.aes128.rawValue
            let keyBytes = key
            let length:UInt8 = 1
            return [securitySetting, length, securityStrategyByte] + keyBytes
        case .aes256:
            let securitySetting = PmdSetting.PmdSettingType.security.rawValue
            let securityStrategyByte = SecurityStrategy.aes256.rawValue
            let keyBytes = key
            let length:UInt8 = 1
            return [securitySetting, length, securityStrategyByte] + keyBytes
        }
    }
    
    func decryptArray(cipherArray: Data) throws -> Data {
        switch(self.strategy) {
        case .none:
            return cipherArray
        case .xor:
            return Data(cipherArray.map { $0^key.first! })
        case .aes128, .aes256:
            return try decryptAES(chipperData: cipherArray)
        }
    }
    
    private func decryptAES(chipperData: Data) throws -> Data {
        guard chipperData.count % 16 == 0 else {
            throw BleGattException.gattSecurityError(description: "AES decryption failed. Chipper data is not dividable by 16, chipper size was \(chipperData.count)")
        }
        
        let cryptoLength = chipperData.count + key.count
        var cryptoData = Data(count: cryptoLength)
        var bytesLength = Int(0)
        
        let status = cryptoData.withUnsafeMutableBytes { cryptoBytes in
            chipperData.withUnsafeBytes { dataBytes in
                         key.withUnsafeBytes { keyBytes in
                    CCCrypt(UInt32(kCCDecrypt), CCAlgorithm(kCCAlgorithmAES), CCOptions(kCCOptionECBMode), keyBytes.baseAddress, key.count, nil, dataBytes.baseAddress, chipperData.count, cryptoBytes.baseAddress, cryptoLength, &bytesLength)
                }
            }
        }
        
        guard status == kCCSuccess else {
            debugPrint("Error: Failed to crypt data. Status \(status)")
            throw BleGattException.gattSecurityError(description: "AES decryption failed. Failed to error \(status)")
        }
        
        cryptoData.removeSubrange(bytesLength..<cryptoData.count)
        return cryptoData
    }
    
    enum SecurityStrategy: UInt8 {
        case none = 0
        case xor = 1
        case aes128 = 2
        case aes256 = 3
        
        static func fromByte(strategyByte: UInt8) throws -> SecurityStrategy {
            switch(strategyByte) {
            case SecurityStrategy.none.rawValue:
                return SecurityStrategy.none
            case SecurityStrategy.xor.rawValue:
                return SecurityStrategy.xor
            case SecurityStrategy.aes128.rawValue:
                return SecurityStrategy.aes128
            case SecurityStrategy.aes256.rawValue:
                return SecurityStrategy.aes256
            default :
                throw BleGattException.gattSecurityError(description: "Cannot decide security strategy from byte \(String(format:"%02X", strategyByte))")
            }
        }
    }
}
