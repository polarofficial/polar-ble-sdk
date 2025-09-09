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

        public init(status: ExerciseStatus, sportProfile: SportProfile) {
            self.status = status
            self.sportProfile = sportProfile
        }

        public var description: String {
            "ExerciseInfo(status: \(status), sport: \(sportProfile))"
        }
    }
}

extension PolarExerciseSession.SportProfile: CaseIterable {
    public static var allCases: [Self] { [.running, .cycling, .otherOutdoor] }
}

/// High-level info of current session state.
public extension PolarExerciseSession.ExerciseInfo {
    static func parse(from data: Data) throws -> Self {
        let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedData: data)
        BleLogger.trace(
            "EX_STATUS raw: state=\(proto.exerciseState) " +
            "hasSport=\(proto.hasSportIdentifier) " +
            "sport=\(proto.hasSportIdentifier ? proto.sportIdentifier.value : 0)"
        )

        let status: PolarExerciseSession.ExerciseStatus
        switch proto.exerciseState {
        case .exerciseStateRunning: status = .inProgress
        case .exerciseStatePaused:  status = .paused
        case .exerciseStateOff:     status = .stopped
        default:                    status = .notStarted
        }

        let sport: PolarExerciseSession.SportProfile = {
            if proto.hasSportIdentifier, proto.sportIdentifier.value <= UInt64(Int.max) {
                return .from(id: Int(proto.sportIdentifier.value))
            } else {
                return .unknown
            }
        }()

        return .init(status: status, sportProfile: sport)
    }
}
