package com.polar.sdk.api.model.sleep

import com.polar.sdk.impl.utils.PolarTimeUtils
import fi.polar.remote.representation.protobuf.SleepanalysisResult.PbSleepCycle
import fi.polar.remote.representation.protobuf.SleepanalysisResult.PbSleepWakePhase
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import java.time.LocalDate
import java.time.LocalDateTime

enum class SleepWakeState(val value: Int) {
    UNKNOWN(0),
    WAKE(-2),
    REM(-3),
    NONREM12(-5),
    NONREM3(-6);

    companion object {
        infix fun from(value: Int): SleepWakeState? = SleepWakeState.values().firstOrNull {it.value == value}
    }
}

enum class SleepRating(val value: Int) {
    SLEPT_UNDEFINED(-1),
    SLEPT_POORLY(0),
    SLEPT_SOMEWHAT_POORLY(1),
    SLEPT_NEITHER_POORLY_NOR_WELL(2),
    SLEPT_SOMEWHAT_WELL(3),
    SLEPT_WELL(4);

    companion object {
        infix fun from(value: Int): SleepRating? = SleepRating.values().firstOrNull {it.value == value}
    }
}
data class PolarSleepData(val date: LocalDate? = null, val result: PolarSleepAnalysisResult? = null) {
    companion object {
    }
}

data class SleepWakePhase(val secondsFromSleepStart: Int, val state: SleepWakeState)
data class SleepCycle(val secondsFromSleepStart: Int, val sleepDepthStart: Float)
data class OriginalSleepRange(val startTime: LocalDateTime, val endTime: LocalDateTime)

data class PolarSleepAnalysisResult(
    val sleepStartTime: LocalDateTime?,
    val sleepEndTime: LocalDateTime?,
    val lastModified: LocalDateTime?,
    val sleepGoalMinutes: Int?,
    val sleepWakePhases: List<SleepWakePhase>?,
    val snoozeTime: List<LocalDateTime>?,
    val alarmTime: LocalDateTime?,
    val sleepStartOffsetSeconds: Int?,
    val sleepEndOffsetSeconds: Int?,
    val userSleepRating: SleepRating?,
    val deviceId: String?,
    val batteryRanOut: Boolean?,
    val sleepCycles: List<SleepCycle>?,
    val sleepResultDate: LocalDate?,
    val originalSleepRange: OriginalSleepRange?
)

fun fromPbSleepwakePhasesListProto(pbSleepwakePhasesList: List<PbSleepWakePhase>): List<SleepWakePhase> {

    var sleepwakePhasesList = mutableListOf<SleepWakePhase>()

    for (pbSleepWakePhase in pbSleepwakePhasesList) {
        sleepwakePhasesList.add(SleepWakePhase(pbSleepWakePhase.secondsFromSleepStart, SleepWakeState.from(pbSleepWakePhase.sleepwakeState.number)!!))
    }
    return sleepwakePhasesList
}


fun fromPbSleepCyclesList(pbSleepCyclesList: List<PbSleepCycle>): List<SleepCycle> {
    var sleepCyclesList = mutableListOf<SleepCycle>()

    for (pbSleepCycle in pbSleepCyclesList) {
        sleepCyclesList.add(SleepCycle(pbSleepCycle.secondsFromSleepStart, pbSleepCycle.sleepDepthStart))
    }

    return sleepCyclesList
}

fun fromPbOriginalSleepRange(pbOriginalSleepRange: Types.PbLocalDateTimeRange): OriginalSleepRange {
    return OriginalSleepRange(
        PolarTimeUtils.pbLocalDateTimeToLocalDateTime(pbOriginalSleepRange.startTime),
        PolarTimeUtils.pbLocalDateTimeToLocalDateTime(pbOriginalSleepRange.endTime)
    )
}

fun convertSnoozeTimeListToLocalTime(snoozeTimeList : List<PbLocalDateTime>): List<LocalDateTime> {

    var snoozeTimes = mutableListOf<LocalDateTime>()

    for (snoozeTime in snoozeTimeList) {

        snoozeTimes.add(
            LocalDateTime.of(
                snoozeTime.date.year,
                snoozeTime.date.month,
                snoozeTime.date.day,
                snoozeTime.time.hour,
                snoozeTime.time.minute,
                snoozeTime.time.seconds,
                snoozeTime.time.millis * 1000000
            ))
    }

    return snoozeTimes
}