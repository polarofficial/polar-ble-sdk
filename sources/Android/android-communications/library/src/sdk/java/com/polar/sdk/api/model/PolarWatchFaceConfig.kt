// Copyright 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

enum class PolarWatchFaceComplication(val complicationId: String) {
    ALARM("alarm-complication"),
    ALTITUDE("altitude-complication"),
    ACTIVITY("activity-percentage-complication"),
    BATTERY("battery-complication"),
    BREATHING_EXERCISE("serene-complication"),
    CALORIES("calories-complication"),
    COMPASS("compass-complication"),
    COUNTDOWN_TIMER("countdownTimer-complication"),
    DATE("date-complication"),
    DAYLIGHT("daylight-complication"),
    ECG("ecg-complication"),
    EMPTY(""),
    FLASHLIGHT("flashlight-complication"),
    HEART_RATE("heart-rate-complication"),
    JUMP_TEST("jump-test-complication"),
    LATEST_TRAINING("latest-training-complication"),
    NAVIGATION("navigation-complication"),
    NIGHTLY_RECHARGE("nightly-recharge-complication"),
    POLAR_LOGO("polar-logo-complication"),
    SECONDS_ANALOG("analog-seconds-complication"),
    SECONDS_DIGITAL("digital-seconds-complication"),
    SPO2("spo2-complication"),
    TIMER("timer-complication"),
    USER_NAME("user-name-complication"),
    WEATHER("weather-complication"),
    WEEKLY_SUMMARY("weeklysummary-complication");

    val id: Int get() = complicationId.hashCode()

    companion object {
        fun fromId(id: Int): PolarWatchFaceComplication? =
            entries.firstOrNull { it.id == id }
    }
}

data class PolarWatchFaceConfig(
    val enabledComplications: List<PolarWatchFaceComplication>
)