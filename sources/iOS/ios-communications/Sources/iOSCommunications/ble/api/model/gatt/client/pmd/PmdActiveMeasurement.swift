//  Copyright © 2022 Polar. All rights reserved.

import Foundation

private let MEASUREMENT_BIT_MASK: UInt8 = 0xC0

public enum PmdActiveMeasurement: UInt8 {
    case no_measurement_active = 0
    case online_measurement_active = 1
    case offline_measurement_active = 2
    case online_offline_measurement_active = 3
    
    static func fromStatusResponse(responseByte: UInt8) -> PmdActiveMeasurement {
        let masked = responseByte & MEASUREMENT_BIT_MASK
        switch (masked >> 6) {
        case online_measurement_active.rawValue:
            return .online_measurement_active
        case offline_measurement_active.rawValue:
            return .offline_measurement_active
        case online_offline_measurement_active.rawValue:
            return .online_offline_measurement_active
        case no_measurement_active.rawValue:
            return .no_measurement_active
        default:
            return .no_measurement_active
        }
    }
}
