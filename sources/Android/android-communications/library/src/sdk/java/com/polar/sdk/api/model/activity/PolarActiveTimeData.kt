package com.polar.sdk.api.model.activity

import java.time.LocalDate

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