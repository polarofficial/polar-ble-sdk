//  Copyright © 2023 Polar. All rights reserved.

import Foundation

public struct LedConfig {
    let sdkModeLedEnabled: Bool
    let ppiModeLedEnabled: Bool

    static let LED_CONFIG_FILENAME = "/LEDCFG.BIN"
    static let LED_ANIMATION_DISABLE_BYTE: UInt8 = 0x00
    static let LED_ANIMATION_ENABLE_BYTE: UInt8 = 0x01
    
    public init(sdkModeLedEnabled: Bool, ppiModeLedEnabled: Bool) {
        self.sdkModeLedEnabled = sdkModeLedEnabled
        self.ppiModeLedEnabled = ppiModeLedEnabled
    }
}