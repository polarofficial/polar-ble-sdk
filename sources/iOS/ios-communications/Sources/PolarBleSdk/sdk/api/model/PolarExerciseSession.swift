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

/// High-level info of current session state.
public extension PolarExerciseSession.ExerciseInfo {
    static func parse(from data: Data) throws -> Self {
        let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedData: data)
        BleLogger.trace(
            "EX_STATUS raw: state=\(proto.exerciseState) " +
            "hasSport=\(proto.hasSportIdentifier) " +
            "sport=\(proto.hasSportIdentifier ? proto.sportIdentifier.value : 0) " +
            "startTime=\(String(describing: proto.hasStartTime ? proto.startTime : nil))"
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

        let startTime: Date? = { () -> Date? in
            guard proto.hasStartTime else { return nil }
            let st = proto.startTime

            let year  = Int(st.date.year)
            let month = Int(st.date.month)
            let day   = Int(st.date.day)

            let hour   = Int(st.time.hour)
            let minute = Int(st.time.minute)
            let second = Int(st.time.seconds)
            let millis = Int(st.time.millis)

            let tzOffsetSec = Int(st.timeZoneOffset) * 60
            let calendar = Calendar(identifier: .gregorian)
            var components = DateComponents()
            components.year = year
            components.month = month
            components.day = day
            components.hour = hour
            components.minute = minute
            components.second = second
            components.nanosecond = millis * 1_000_000
            components.timeZone = TimeZone(secondsFromGMT: tzOffsetSec)

            return calendar.date(from: components)
        }()

        return .init(status: status, sportProfile: sport, startTime: startTime)
    }
}
