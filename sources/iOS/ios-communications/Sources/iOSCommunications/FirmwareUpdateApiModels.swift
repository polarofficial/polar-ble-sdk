//  Copyright © 2024 Polar. All rights reserved.

import Foundation

struct FirmwareUpdateRequest: Codable {
    let clientId: String
    let uuid: String
    let firmwareVersion: String
    let hardwareCode: String

    private enum CodingKeys: String, CodingKey {
        case clientId = "clientId"
        case uuid = "uuid"
        case firmwareVersion = "firmwareVersion"
        case hardwareCode = "hardwareCode"
    }
}

struct FirmwareUpdateResponse: Decodable {
    let version: String?
    let fileUrl: String?
    var statusCode: Int?
    var mandatory: Bool?

    enum CodingKeys: String, CodingKey {
        case version
        case fileUrl
        case mandatory
    }
}

struct FirmwareUpdateDeviceResponse: Decodable {
    let version: String?
    let fileUrl: String?
    var hardwareVersions: [FirmwareUpdateResponseHardwareVersion]?
    var statusCode: Int?

    enum CodingKeys: String, CodingKey {
        case version
        case fileUrl
        case hardwareVersions
    }
}

struct FirmwareUpdateResponseHardwareVersion: Decodable {
    let expired: Bool?
    let mandatory: Bool?
    let hardwareCode: String?

    enum CodingKeys: String, CodingKey {
        case expired
        case mandatory
        case hardwareCode = "erpNumber"
    }
}

struct FirmwareUpdateErrorResponse: Codable {
    let errors: [ErrorDetail]

    struct ErrorDetail: Codable {
        let fieldPath: String
        let code: String
        let message: String
    }
}
