//  Copyright © 2025 Polar. All rights reserved.

import Foundation

/// Represents a live exercise session on a Polar device.
public enum PolarExerciseSession {

    /// Supported sport profiles.
    public enum SportProfile: Int {
        case unknown      = 0
        case running      = 1
        case cycling      = 2
        case otherOutdoor = 16

        public static func from(id: Int) -> Self { Self(rawValue: id) ?? .unknown }

        public var displayName: String {
            switch self {
            case .running:      return "Running"
            case .cycling:      return "Cycling"
            case .otherOutdoor: return "Other outdoor"
            case .unknown:      return "Unknown"
            }
        }
    }

    /// Status of an exercise session.
    public enum ExerciseStatus {
        case notStarted
        case inProgress
        case paused
        case stopped
        case syncRequired
    }

    /// High-level info of current session state.
    public struct ExerciseInfo: Equatable, CustomStringConvertible {
        public let status: ExerciseStatus
        public let sportProfile: SportProfile
        public let startTime: Date?

        public init(status: ExerciseStatus, sportProfile: SportProfile, startTime: Date? = nil) {
            self.status = status
            self.sportProfile = sportProfile
            self.startTime = startTime
        }

        public var description: String {
            "ExerciseInfo(status: \(status), sport: \(sportProfile), startTime: \(startTime?.description ?? "nil"))"
        }
    }
}

extension PolarExerciseSession.SportProfile: CaseIterable {
    public static var allCases: [Self] { [.running, .cycling, .otherOutdoor] }
}
