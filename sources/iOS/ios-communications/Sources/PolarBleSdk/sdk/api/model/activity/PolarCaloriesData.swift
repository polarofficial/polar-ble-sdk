///  Copyright © 2025 Polar. All rights reserved.

import Foundation

public struct PolarCaloriesData: Codable {
    public let date: Date
    public let calories: Int
}

public enum CaloriesType: CaseIterable {
    case activity
    case training
    case bmr

    public var displayName: String {
        switch self {
            case .activity: return "Activity"
            case .training: return "Training"
            case .bmr: return "BMR"
        }
    }

    public static var allCases: [CaloriesType] {
        return [.activity, .training, .bmr]
    }
}