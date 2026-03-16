//  Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Protobuf parsing extension for PolarExerciseSession.ExerciseInfo
public extension PolarExerciseSession.ExerciseInfo {
    static func parse(from data: Data) throws -> Self {
        let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: data)
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
