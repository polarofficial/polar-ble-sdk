//  Copyright © 2022 Polar. All rights reserved.

import Foundation

struct PmdControlPointCommand {
    static let GET_MEASUREMENT_SETTINGS: UInt8 = 0x01
    static let REQUEST_MEASUREMENT_START: UInt8 = 0x02
    static let STOP_MEASUREMENT: UInt8 = 0x03
    static let GET_SDK_MODE_SETTINGS: UInt8 = 0x04
    static let GET_MEASUREMENT_STATUS: UInt8 = 0x05
    static let GET_SDK_MODE_STATUS: UInt8 = 0x06
    static let GET_OFFLINE_RECORDING_TRIGGER_STATUS: UInt8 = 0x06
    static let SET_OFFLINE_RECORDING_TRIGGER_MODE: UInt8 = 0x06
    static let SET_OFFLINE_RECORDING_TRIGGER_SETTINGS: UInt8 = 0x06
}
