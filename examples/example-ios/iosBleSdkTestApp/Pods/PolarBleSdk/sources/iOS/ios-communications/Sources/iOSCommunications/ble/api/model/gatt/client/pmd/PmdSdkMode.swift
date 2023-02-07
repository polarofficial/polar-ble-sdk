//  Copyright © 2023 Polar. All rights reserved.

import Foundation

public enum PmdSdkMode: UInt8, CaseIterable {
    case disabled = 0
    case enabled = 1
    
    static func fromResponse(sdkModeByte: UInt8) -> PmdSdkMode {
        if let pmdSdkMode = PmdSdkMode(rawValue: sdkModeByte) {
            return pmdSdkMode
        } else {
            BleLogger.error("the byte \(sdkModeByte) is not in the range of the PmdSdkMode valid values")
            return .disabled
        }
    }
}
