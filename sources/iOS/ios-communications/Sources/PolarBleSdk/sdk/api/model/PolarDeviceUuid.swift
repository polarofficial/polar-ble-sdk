//  Copyright © 2024 Polar. All rights reserved.

import Foundation

struct PolarDeviceUuid {
    private static let polarUuidPrefix = "0e030000-0084-0000-0000-0000"
    private static let requiredDeviceIdLength = 8
    
    enum PolarDeviceUuidError: Error {
        case invalidDeviceIdLength(expected: Int, actual: Int)
    }
    
    static func fromDeviceId(_ deviceId: String) throws -> String {
        guard deviceId.count == requiredDeviceIdLength else {
            throw PolarDeviceUuidError.invalidDeviceIdLength(expected: requiredDeviceIdLength, actual: deviceId.count)
        }
        return polarUuidPrefix + deviceId
    }
}
