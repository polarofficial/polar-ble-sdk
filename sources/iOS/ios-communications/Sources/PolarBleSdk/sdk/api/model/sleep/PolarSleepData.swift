//
//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation

public class PolarSleepData {

    enum SleepWakeState: Int {
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

    enum SleepRating: Int {
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

   public  struct PolarSleepAnalysisResult {
        let sleepStartTime: Date!
        let sleepEndTime: Date!
        let lastModified: Date!
        let sleepGoalMinutes: UInt32!
        let sleepWakePhases: [SleepWakePhase]!
        let snoozeTime: [Date]?
        let alarmTime: Date?
        let sleepStartOffsetSeconds: Int32!
        let sleepEndOffsetSeconds: Int32!
        let userSleepRating: SleepRating?
        let deviceId: String?
        let batteryRanOut: Bool?
        let sleepCycles: [SleepCycle]!
        let sleepResultDate: Date?
        let originalSleepRange: OriginalSleepRange?
    }

    struct SleepWakePhase {
        var secondsFromSleepStart: UInt32!
        var state: SleepWakeState!
    }

    struct SleepCycle {
        let secondsFromSleepStart: UInt32!
        let sleepDepthStart: Float!
    }

    struct OriginalSleepRange {
        let startTime: Date?
        let endTime: Date?
        
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
