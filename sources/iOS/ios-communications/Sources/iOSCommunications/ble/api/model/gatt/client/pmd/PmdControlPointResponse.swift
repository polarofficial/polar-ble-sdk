//  Copyright © 2023 Polar. All rights reserved.

import Foundation

internal struct PmdControlPointResponse {
    static let CONTROL_POINT_RESPONSE_CODE: UInt8 = 0xF0
    public let response: UInt8
    public let opCode: UInt8
    public let type: PmdMeasurementType
    public let errorCode: PmdResponseCode
    public let more: Bool
    public let parameters = NSMutableData()
    public init(_ data: Data) {
        response = data[0]
        opCode = data[1]
        type = PmdMeasurementType(rawValue: data[2]) ?? PmdMeasurementType.unknown_type
        errorCode = PmdResponseCode(rawValue: Int(data[3])) ?? PmdResponseCode.unknown_error
        if data.count > 4 {
            more = data[4] != 0
            parameters.append(data.subdata(in: 5..<data.count))
        } else {
            more = false
        }
    }
}

public enum PmdResponseCode: Int {
    case success = 0
    case errorInvalidOpCode = 1
    case errorInvalidMeasurementType = 2
    case errorNotSupported = 3
    case errorInvalidLength = 4
    case errorInvalidParameter = 5
    case errorAlreadyInState = 6
    case errorInvalidResolution = 7
    case errorInvalidSampleRate = 8
    case errorInvalidRange = 9
    case errorInvalidMTU = 10
    case errorInvalidNumberOfChannels = 11
    case errorInvalidState = 12
    case errorDeviceInCharger = 13
    case errorDiskFull = 14
    case unknown_error = 0xffff
    
    var description : String {
        switch self {
        case .success: return "Success"
        case .errorInvalidOpCode: return "Invalid op code"
        case .errorInvalidMeasurementType: return "Invalid measurement type"
        case .errorNotSupported: return "Not supported"
        case .errorInvalidLength: return "Invalid length"
        case .errorInvalidParameter: return "Invalid parameter"
        case .errorAlreadyInState: return "Already in state"
        case .errorInvalidResolution: return "Invalid Resolution"
        case .errorInvalidSampleRate: return "Invalid Sample rate"
        case .errorInvalidRange: return "Invalid Range"
        case .errorInvalidMTU: return "Invalid MTU"
        case .errorInvalidNumberOfChannels: return "Invalid Number of channels"
        case .errorInvalidState: return "Invalid state"
        case .errorDeviceInCharger: return "Device in charger"
        case .errorDiskFull: return "Disk full"
        case .unknown_error: return "unknown error"
        }
    }
}
