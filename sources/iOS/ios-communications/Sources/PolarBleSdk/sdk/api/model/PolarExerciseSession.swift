//  Copyright © 2025 Polar. All rights reserved.

import Foundation

/// Represents a live exercise session on a Polar device.
public enum PolarExerciseSession {

    /// Convenience names for a subset of Polar sport identifiers, not a complete list
    /// of sports supported by devices. Use numeric sport IDs for other sports.
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
        /// Raw sport identifier, including IDs absent from `SportProfile`. Zero if unavailable.
        public let sportId: UInt64

        public init(status: ExerciseStatus, sportProfile: SportProfile, startTime: Date? = nil) {
            self.init(status: status, sportProfile: sportProfile, startTime: startTime, sportId: UInt64(sportProfile.rawValue))
        }

        public init(status: ExerciseStatus, sportProfile: SportProfile, startTime: Date? = nil, sportId: UInt64) {
            self.status = status
            self.sportProfile = sportProfile
            self.startTime = startTime
            self.sportId = sportId
        }

        public var description: String {
            "ExerciseInfo(status: \(status), sport: \(sportProfile), sportId: \(sportId), startTime: \(startTime?.description ?? "nil"))"
        }
    }
}

extension PolarExerciseSession.SportProfile: CaseIterable {
    public static var allCases: [Self] { [.running, .cycling, .otherOutdoor] }
}
