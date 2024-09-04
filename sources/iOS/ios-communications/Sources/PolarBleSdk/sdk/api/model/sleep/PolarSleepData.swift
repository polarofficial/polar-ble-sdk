//
//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation

public class PolarSleepData {

    public enum SleepWakeState: Int, Codable {
        case UNKNOWN = 0
        case WAKE = -2
        case REM = -3
        case NONREM12 = -5
        case NONREM3 = -6

        static func getByValue(value: Int) -> SleepWakeState {
            guard let status = SleepWakeState(rawValue: value) else {
                BleLogger.error("Invalid SleepWakeState value: \(value)")
                return UNKNOWN
            }
            return status
        }
    }

    public enum SleepRating: Int, Codable {
        case SLEPT_UNDEFINED = -1
        case SLEPT_POORLY = 0
        case SLEPT_SOMEWHAT_POORLY = 1
        case SLEPT_NEITHER_POORLY_NOR_WELL = 2
        case SLEPT_SOMEWHAT_WELL = 3
        case SLEPT_WELL = 4

        static func getByValue(value: Int) -> SleepRating {
            guard let status = SleepRating(rawValue: value) else {
                BleLogger.error("Invalid SleepRating value: \(value)")
                return SLEPT_UNDEFINED
            }
            return status
        }
    }

    public struct PolarSleepAnalysisResult: Codable {
        public let sleepStartTime: Date!
        public let sleepEndTime: Date!
        public let lastModified: Date!
        public let sleepGoalMinutes: UInt32!
        public let sleepWakePhases: [SleepWakePhase]!
        public let snoozeTime: [Date]?
        public let alarmTime: Date?
        public let sleepStartOffsetSeconds: Int32!
        public let sleepEndOffsetSeconds: Int32!
        public let userSleepRating: SleepRating?
        public let deviceId: String?
        public let batteryRanOut: Bool?
        public let sleepCycles: [SleepCycle]!
        public let sleepResultDate: Date?
        public  let originalSleepRange: OriginalSleepRange?
    }

    public struct SleepWakePhase: Codable {
        public var secondsFromSleepStart: UInt32!
        public var state: SleepWakeState!
    }

    public struct SleepCycle: Codable {
        public let secondsFromSleepStart: UInt32!
        public let sleepDepthStart: Float!
    }

    public struct OriginalSleepRange: Codable {
        public let startTime: Date?
        public let endTime: Date?
        
    }

    static func fromPbSleepwakePhasesListProto(pbSleepwakePhasesList: [Data_PbSleepWakePhase]) -> [PolarSleepData.SleepWakePhase] {
        
        var sleepwakePhasesList = [SleepWakePhase]()
        
        for pbSleepWakePhase in pbSleepwakePhasesList {
            let sleepWakePhase = PolarSleepData.SleepWakePhase(secondsFromSleepStart: pbSleepWakePhase.secondsFromSleepStart, state: PolarSleepData.SleepWakeState.getByValue(value: pbSleepWakePhase.sleepwakeState.rawValue))
            sleepwakePhasesList.append(sleepWakePhase)
        }
        return sleepwakePhasesList
    }

    static func fromPbSleepCyclesList(pbSleepCyclesList: [Data_PbSleepCycle]) -> [SleepCycle] {

        var sleepCyclesList = [SleepCycle]()

        for pbSleepCycle in pbSleepCyclesList {
            let sleepCycle = SleepCycle(secondsFromSleepStart: pbSleepCycle.secondsFromSleepStart, sleepDepthStart: pbSleepCycle.sleepDepthStart)
            sleepCyclesList.append(sleepCycle)
        }

        return sleepCyclesList
    }

    static func fromPbOriginalSleepRange(pbOriginalSleepRange: PbLocalDateTimeRange) throws -> OriginalSleepRange {

        var startTime = pbOriginalSleepRange.hasStartTime ? try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: pbOriginalSleepRange.startTime) : nil
        var endTime = pbOriginalSleepRange.hasEndTime ?  try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: pbOriginalSleepRange.endTime) : nil

        return OriginalSleepRange(
            startTime: startTime,
            endTime: endTime)
    }

    static func convertSnoozeTimeListToLocalTime(snoozeTimeList: [PbLocalDateTime]) throws -> [Date] {

        var snoozeTimes = [Date]()

        for snoozeTime in snoozeTimeList {

            var date = try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: snoozeTime)
            snoozeTimes.append(date)
        }

        return snoozeTimes
    }
}
