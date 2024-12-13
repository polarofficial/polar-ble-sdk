///  Copyright © 2024 Polar. All rights reserved.

import Foundation

public struct Polar247HrSamplesData: Codable {
    public let date: Date
    public let hrSamples: [Int]
    public let triggerType: AutomaticSampleTriggerType?

    enum CodingKeys: String, CodingKey {
        case date
        case hrSamples
        case triggerType
    }

    public init(date: Date, hrSamples: [Int], triggerType: AutomaticSampleTriggerType?) {
        self.date = date
        self.hrSamples = hrSamples
        self.triggerType = triggerType
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        let dateString = try container.decode(String.self, forKey: .date)
        guard let decodedDate = ISO8601DateFormatter().date(from: dateString) else {
            throw DecodingError.dataCorruptedError(forKey: .date, in: container, debugDescription: "Invalid date format")
        }
        date = decodedDate

        hrSamples = try container.decode([Int].self, forKey: .hrSamples)
        
        let triggerTypeString = try container.decode(String.self, forKey: .triggerType)
        guard let decodedTriggerType = AutomaticSampleTriggerType(stringValue: triggerTypeString) else {
            throw DecodingError.dataCorruptedError(forKey: .triggerType, in: container, debugDescription: "Invalid trigger type string")
        }
        triggerType = decodedTriggerType
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        let dateString = ISO8601DateFormatter().string(from: date)
        try container.encode(dateString, forKey: .date)
        try container.encode(hrSamples, forKey: .hrSamples)
        try container.encode(triggerType?.stringValue, forKey: .triggerType)
    }
}

public enum AutomaticSampleTriggerType: String, Codable {
    case highActivity = "highActivity"
    case lowActivity = "lowActivity"
    case timed = "timed"
    case manual = "manual"
    
    init?(stringValue: String) {
        switch stringValue {
        case "highActivity":
            self = .highActivity
        case "lowActivity":
            self = .lowActivity
        case "timed":
            self = .timed
        case "manual":
            self = .manual
        default:
            return nil
        }
    }
    
    var stringValue: String {
        return self.rawValue
    }
}
