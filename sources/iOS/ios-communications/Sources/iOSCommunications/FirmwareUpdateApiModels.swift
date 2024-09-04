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
    
    enum CodingKeys: String, CodingKey {
        case version
        case fileUrl
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
