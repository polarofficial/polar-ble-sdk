package com.polar.sdk.api.model.activity

import com.polar.sdk.impl.utils.PolarTimeUtils
import fi.polar.remote.representation.protobuf.DailySummary.PbDailySummary
import fi.polar.remote.representation.protobuf.Types.PbDuration
import java.time.LocalDate

data class PolarDailySummaryData(
    val date: LocalDate? = null,
    val activityCalories: Int? = null,
    val trainingCalories: Int? = null,
    val bmrCalories: Int? = null,
    val steps: Int? = null,
    val activityGoalSummary: PolarActivityGoalSummary? = null,
    val activityClassTimes: PolarActiveTimeData? = null,
    val activityDistance: Float? = null,
    val dailyBalanceFeedback: PolarDailyBalanceFeedBack? = null,
    val readinessForSpeedAndStrengthTraining: PolarReadinessForSpeedAndStrengthTraining? = null
)

data class PolarActiveTimeData(
    val date: LocalDate,
    val timeNonWear: PolarActiveTime = PolarActiveTime(),
    val timeSleep: PolarActiveTime = PolarActiveTime(),
    val timeSedentary: PolarActiveTime = PolarActiveTime(),
    val timeLightActivity: PolarActiveTime = PolarActiveTime(),
    val timeContinuousModerateActivity: PolarActiveTime = PolarActiveTime(),
    val timeIntermittentModerateActivity: PolarActiveTime = PolarActiveTime(),
    val timeContinuousVigorousActivity: PolarActiveTime = PolarActiveTime(),
    val timeIntermittentVigorousActivity: PolarActiveTime = PolarActiveTime()
)

data class PolarActiveTime(
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val millis: Int = 0
)

data class PolarActivityGoalSummary(
    val activityGoal: Float = 0.0f,
    val achievedActivity: Float = 0.0f,
    val timeToGoUp: PolarActiveTime? = PolarActiveTime(),
    val timeToGoWalk: PolarActiveTime? = PolarActiveTime(),
    val timeToGoJog: PolarActiveTime? = PolarActiveTime()
)

enum class PolarDailyBalanceFeedBack(val numVal: Int) {
    NOT_CALCULATED(-1),
    SICK(0),
    FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD_INJURED(1),
    FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD(2),
    LIMITED_TRAINING_RESPONSE_OTHER_INJURED(3),
    LIMITED_TRAINING_RESPONSE_OTHER(4),
    RESPONDING_WELL_CAN_CONTINUE_IF_INJURY_ALLOWS(5),
    RESPONDING_WELL_CAN_CONTINUE(6),
    YOU_COULD_DO_MORE_TRAINING_IF_INJURY_ALLOWS(7),
    YOU_COULD_DO_MORE_TRAINING(8),
    YOU_SEEM_TO_BE_STRAINED_INJURED(9),
    YOU_SEEM_TO_BE_STRAINED(10);

    companion object {
        infix fun from(value: Int): PolarDailyBalanceFeedBack? =
            PolarDailyBalanceFeedBack.values().firstOrNull { it.numVal == value }
    }
}

enum class PolarReadinessForSpeedAndStrengthTraining(val numVal: Int) {
    NOT_CALCULATED(-1),
    RECOVERED_READY_FOR_ALL_TRAINING(0),
    RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_NIGHTLY_RECHARGE_COMPROMISED(1),
    RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_POSSIBLY_STRESSED(2),
    RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING(3),
    RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO(4),
    RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_NIGHTLY_RECHARGE(5),
    RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_CARDIO_RECOVERY(6),
    NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO(7),
    NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE(8),
    NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO(9),
    NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE(10),
    RECOVERED_BUT_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING(11),
    NOT_RECOVERED_AND_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING(12);

    companion object {
        infix fun from(value: Int): PolarReadinessForSpeedAndStrengthTraining? =
            PolarReadinessForSpeedAndStrengthTraining.values().firstOrNull { it.numVal == value }
    }
}

fun parsePbDailySummary(pbDailySummary: PbDailySummary): PolarDailySummaryData {
    return PolarDailySummaryData(
        PolarTimeUtils.pbDateToLocalDate(pbDailySummary.date),
        pbDailySummary.activityCalories,
        pbDailySummary.trainingCalories,
        pbDailySummary.bmrCalories,
        activityGoalSummary = PolarActivityGoalSummary(
            pbDailySummary.activityGoalSummary.activityGoal,
            pbDailySummary.activityGoalSummary.achievedActivity,
            polarActiveTimeFromProto(pbDailySummary.activityGoalSummary.timeToGoUp),
            polarActiveTimeFromProto(pbDailySummary.activityGoalSummary.timeToGoWalk),
            polarActiveTimeFromProto(pbDailySummary.activityGoalSummary.timeToGoJog)
        ),
        activityClassTimes = PolarActiveTimeData(
            PolarTimeUtils.pbDateToLocalDate(pbDailySummary.date),
            polarActiveTimeFromProto(pbDailySummary.activityClassTimes.timeNonWear),
            polarActiveTimeFromProto(pbDailySummary.activityClassTimes.timeSleep),
            polarActiveTimeFromProto(pbDailySummary.activityClassTimes.timeSedentary),
            polarActiveTimeFromProto(pbDailySummary.activityClassTimes.timeLightActivity),
            polarActiveTimeFromProto(pbDailySummary.activityClassTimes.timeContinuousModerate),
            polarActiveTimeFromProto(pbDailySummary.activityClassTimes.timeIntermittentModerate),
            polarActiveTimeFromProto(pbDailySummary.activityClassTimes.timeContinuousVigorous),
            polarActiveTimeFromProto(pbDailySummary.activityClassTimes.timeIntermittentVigorous)
        ),
        activityDistance = pbDailySummary.activityDistance,
        dailyBalanceFeedback = PolarDailyBalanceFeedBack.from(pbDailySummary.dailyBalanceFeedback.number),
        readinessForSpeedAndStrengthTraining = PolarReadinessForSpeedAndStrengthTraining.from(pbDailySummary.readinessForSpeedAndStrengthTraining.number),
        steps = pbDailySummary.steps
    )
}

fun polarActiveTimeFromProto(proto: PbDuration): PolarActiveTime {
    return PolarActiveTime(
        hours = proto.hours,
        minutes = proto.minutes,
        seconds = proto.seconds,
        millis = proto.millis
    )
}