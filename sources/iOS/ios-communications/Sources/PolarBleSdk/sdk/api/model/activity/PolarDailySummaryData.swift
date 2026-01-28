//  Copyright © 2025 Polar. All rights reserved.


import Foundation

public var polarDailySummary: PolarDailySummary?

public struct PolarDailySummary: Codable {
    public var date: Date
    public var activityCalories: UInt32
    public var trainingCalories: UInt32
    public var bmrCalories: UInt32
    public var steps: UInt32
    public var activityGoalSummary: PolarActivityGoalSummary
    public var activityClassTimes: PolarActiveTimeData
    public var activityDistance: Float
    public var dailyBalanceFeedback: PolarDailyBalanceFeedBack
    public var readinessForSpeedAndStrengthTraining: PolarReadinessForSpeedAndStrengthTraining
    
    static func fromProto(proto: Data_PbDailySummary) throws -> PolarDailySummary {
        var polarDailySummary: PolarDailySummary
        var date: Date
        var activeTimeData: PolarActiveTimeData
        do {
            date = try PolarTimeUtils.pbDateToDate(pbDate: proto.date).localDate()
        } catch let err {
            BleLogger.error("Failed to parse PolarDailySummary data from DailySummary proto: \(err)")
            throw PolarErrors.dateTimeFormatFailed(description: "Failed to parse date for PolarActiveTimeData from DailySummary proto: \(err)")
        }

        do {
            activeTimeData = try PolarActiveTimeData.fromProto(proto: proto)
        } catch let err {
            BleLogger.error("Failed to parse ActiveTimeData for PolarDailySummary data from DailySummary proto: \(err)")
            throw PolarErrors.dateTimeFormatFailed(description: "Failed to parse PolarActiveTimeData from DailySummary proto: \(err)")
        }
        
        polarDailySummary = PolarDailySummary(
            date: date,
            activityCalories: proto.activityCalories,
            trainingCalories: proto.trainingCalories,
            bmrCalories: proto.bmrCalories,
            steps: proto.steps,
            activityGoalSummary: PolarActivityGoalSummary.fromProto(proto: proto),
            activityClassTimes: activeTimeData,
            activityDistance: proto.activityDistance,
            dailyBalanceFeedback: PolarDailyBalanceFeedBack.getByValue(value: proto),
            readinessForSpeedAndStrengthTraining: PolarReadinessForSpeedAndStrengthTraining.getByValue(value: proto)
        )
        return polarDailySummary
    }
}

public struct PolarActivityGoalSummary: Codable {
    var activityGoal: Float = 0.0
    var achievedActivity: Float = 0.0
    var timeToGoUp: PolarActiveTime? = PolarActiveTime()
    var timeToGoWalk: PolarActiveTime? = PolarActiveTime()
    var timeToGoJog: PolarActiveTime? = PolarActiveTime()
    
    static func fromProto(proto: Data_PbDailySummary ) -> PolarActivityGoalSummary {
        var polarActivityGoalSummary: PolarActivityGoalSummary
        polarActivityGoalSummary = PolarActivityGoalSummary(
            activityGoal: proto.activityGoalSummary.activityGoal,
            achievedActivity: proto.activityGoalSummary.achievedActivity,
            timeToGoUp: PolarActiveTime.fromProto(proto.activityGoalSummary.timeToGoUp),
            timeToGoWalk: PolarActiveTime.fromProto(proto.activityGoalSummary.timeToGoWalk),
            timeToGoJog: PolarActiveTime.fromProto(proto.activityGoalSummary.timeToGoJog)
        )
        
        return polarActivityGoalSummary
    }
}

public enum PolarDailyBalanceFeedBack: String, Codable {
    case NOT_CALCULATED = "NOT_CALCULATED"
    case SICK = "SICK"
    case FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD_INJURED = "FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD_INJURED"
    case FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD = "FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD"
    case LIMITED_TRAINING_RESPONSE_OTHER_INJURED = "LIMITED_TRAINING_RESPONSE_OTHER_INJURED"
    case LIMITED_TRAINING_RESPONSE_OTHER = "LIMITED_TRAINING_RESPONSE_OTHER"
    case RESPONDING_WELL_CAN_CONTINUE_IF_INJURY_ALLOWS = "RESPONDING_WELL_CAN_CONTINUE_IF_INJURY_ALLOWS"
    case RESPONDING_WELL_CAN_CONTINUE = "RESPONDING_WELL_CAN_CONTINUE"
    case YOU_COULD_DO_MORE_TRAINING_IF_INJURY_ALLOWS = "YOU_COULD_DO_MORE_TRAINING_IF_INJURY_ALLOWS"
    case YOU_COULD_DO_MORE_TRAINING = "YOU_COULD_DO_MORE_TRAINING"
    case YOU_SEEM_TO_BE_STRAINED_INJURED = "YOU_SEEM_TO_BE_STRAINED_INJURED"
    case YOU_SEEM_TO_BE_STRAINED = "YOU_SEEM_TO_BE_STRAINED"
    
    static func getByValue(value: Data_PbDailySummary) -> PolarDailyBalanceFeedBack {
        switch value.dailyBalanceFeedback {
        case .dbNotCalculated:
            return .NOT_CALCULATED
        case .dbSick:
            return .SICK
        case .dbFatigueTryToReduceTrainingLoadInjured:
            return .FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD_INJURED
        case .dbFatigueTryToReduceTrainingLoad:
            return .FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD
        case .dbLimitedTrainingResponseOtherInjured:
            return .LIMITED_TRAINING_RESPONSE_OTHER_INJURED
        case .dbLimitedTrainingResponseOther:
            return .LIMITED_TRAINING_RESPONSE_OTHER
        case .dbRespondingWellCanContinue:
            return .RESPONDING_WELL_CAN_CONTINUE
        case .dbRespondingWellCanContinueIfInjuryAllows:
            return .RESPONDING_WELL_CAN_CONTINUE_IF_INJURY_ALLOWS
        case .dbYouCouldDoMoreTrainingIfInjuryAllows:
            return .YOU_COULD_DO_MORE_TRAINING_IF_INJURY_ALLOWS
        case .dbYouCouldDoMoreTraining:
            return .YOU_COULD_DO_MORE_TRAINING
        case .dbYouSeemToBeStrained:
            return .YOU_SEEM_TO_BE_STRAINED
        case .dbYouSeemToBeStrainedInjured:
            return .YOU_SEEM_TO_BE_STRAINED_INJURED
        }
    }
}

public enum PolarReadinessForSpeedAndStrengthTraining: String, Codable {
    case NOT_CALCULATED = "NOT_CALCULATED"
    case RECOVERED_READY_FOR_ALL_TRAINING = "RECOVERED_READY_FOR_ALL_TRAINING"
    case RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_NIGHTLY_RECHARGE_COMPROMISED = "RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_NIGHTLY_RECHARGE_COMPROMISED"
    case RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_POSSIBLY_STRESSED = "RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_POSSIBLY_STRESSED"
    case RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING = "RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING"
    case RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO = "RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO"
    case RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_NIGHTLY_RECHARGE = "RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_NIGHTLY_RECHARGE"
    case RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_CARDIO_RECOVERY = "RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_CARDIO_RECOVERY"
    case NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO = "NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO"
    case NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE = "NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE"
    case NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO = "NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO"
    case NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE = "NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE"
    case RECOVERED_BUT_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING = "RECOVERED_BUT_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING"
    case NOT_RECOVERED_AND_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING = "NOT_RECOVERED_AND_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING"
    
    static func getByValue(value: Data_PbDailySummary) -> PolarReadinessForSpeedAndStrengthTraining {
        
        switch value.readinessForSpeedAndStrengthTraining {
        case .rsstNotCalculated:
            return .NOT_CALCULATED
        case .rsstA1RecoveredReadyForAllTraining:
            return .RECOVERED_READY_FOR_ALL_TRAINING
        case .rsstA2RecoveredReadyForAllTrainingIfFeelingOkNightlyRechargeCompromised:
            return .RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_NIGHTLY_RECHARGE_COMPROMISED
        case .rsstA3RecoveredReadyForAllTrainingIfFeelingOkPossiblyStressed:
            return .RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_POSSIBLY_STRESSED
        case .rsstA4RecoveredReadyForSpeedAndStrengthTraining:
            return .RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING
        case .rsstB1RecoveredReadyForSpeedAndStrengthTrainingAndLightCardio:
            return .RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO
        case .rsstB2RecoveredReadyForSpeedAndStrengthTrainingAndLightCardioPoorNightlyRecharge:
            return .RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_NIGHTLY_RECHARGE
        case .rsstB3RecoveredReadyForSpeedAndStrengthTrainingAndLightCardioPoorCardioRecovery:
            return .RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_CARDIO_RECOVERY
        case .rsstB4NotRecoveredNoLegTrainingOrIntensiveCardio:
            return .NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO
        case .rsstB5NotRecoveredNoLegTrainingOrIntensiveCardioPoorNightlyRecharge:
            return .NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE
        case .rsstC1NotRecoveredNoStrengthOrIntensiveCardio:
            return .NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO
        case .rsstC2NotRecoveredNoStrengthOrIntensiveCardioPoorNightlyRecharge:
            return .NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE
        case .rsstD1RecoveredButInjuryAndIllnessRiskCausedByCardioTraining:
            return .RECOVERED_BUT_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING
        case .rsstD2NotRecoveredAndInjuryAndIllnessRiskCausedByCardioTraining:
            return .NOT_RECOVERED_AND_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING
        }
    }
}

public struct PolarActiveTimeData: Codable  {
    public let date: Date
    public let timeNonWear: PolarActiveTime
    public let timeSleep: PolarActiveTime
    public let timeSedentary: PolarActiveTime
    public let timeLightActivity: PolarActiveTime
    public let timeContinuousModerateActivity: PolarActiveTime
    public let timeIntermittentModerateActivity: PolarActiveTime
    public let timeContinuousVigorousActivity: PolarActiveTime
    public let timeIntermittentVigorousActivity: PolarActiveTime
    
    public init(date: Date,
                timeNonWear: PolarActiveTime = PolarActiveTime(),
                timeSleep: PolarActiveTime = PolarActiveTime(),
                timeSedentary: PolarActiveTime = PolarActiveTime(),
                timeLightActivity: PolarActiveTime = PolarActiveTime(),
                timeContinuousModerateActivity: PolarActiveTime = PolarActiveTime(),
                timeIntermittentModerateActivity: PolarActiveTime = PolarActiveTime(),
                timeContinuousVigorousActivity: PolarActiveTime = PolarActiveTime(),
                timeIntermittentVigorousActivity: PolarActiveTime = PolarActiveTime()) {
        self.date = date
        self.timeNonWear = timeNonWear
        self.timeSleep = timeSleep
        self.timeSedentary = timeSedentary
        self.timeLightActivity = timeLightActivity
        self.timeContinuousModerateActivity = timeContinuousModerateActivity
        self.timeIntermittentModerateActivity = timeIntermittentModerateActivity
        self.timeContinuousVigorousActivity = timeContinuousVigorousActivity
        self.timeIntermittentVigorousActivity = timeIntermittentVigorousActivity
    }
    
    static func fromProto(proto: Data_PbDailySummary) throws -> PolarActiveTimeData {
        var date: Date
        do {
            date = try PolarTimeUtils.pbDateToDate(pbDate: proto.date).localDate()
        } catch let err {
            BleLogger.error("Failed to parse PolarActiveTimeData from DailySummary proto: \(err)")
            throw PolarErrors.dateTimeFormatFailed(description: "Failed to parse PolarActiveTimeData from DailySummary proto: \(err)")
        }
        return PolarActiveTimeData(
            date: date,
            timeNonWear: PolarActiveTime.fromProto(proto.activityClassTimes.timeNonWear),
            timeSleep: PolarActiveTime.fromProto(proto.activityClassTimes.timeSleep),
            timeSedentary: PolarActiveTime.fromProto(proto.activityClassTimes.timeSedentary),
            timeLightActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeLightActivity),
            timeContinuousModerateActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeContinuousModerate),
            timeIntermittentModerateActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeIntermittentModerate),
            timeContinuousVigorousActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeContinuousVigorous),
            timeIntermittentVigorousActivity: PolarActiveTime.fromProto(proto.activityClassTimes.timeIntermittentVigorous)
        )
    }
}

public struct PolarActiveTime: Codable, Equatable {
    public let hours: Int
    public let minutes: Int
    public let seconds: Int
    public let millis: Int
    
    public init(hours: Int = 0, minutes: Int = 0, seconds: Int = 0, millis: Int = 0) {
        self.hours = hours
        self.minutes = minutes
        self.seconds = seconds
        self.millis = millis
    }

    static func fromProto(_ proto: PbDuration) -> PolarActiveTime {
        return PolarActiveTime(
            hours: Int(proto.hours),
            minutes: Int(proto.minutes),
            seconds: Int(proto.seconds),
            millis: Int(proto.millis)
        )
    }
}
