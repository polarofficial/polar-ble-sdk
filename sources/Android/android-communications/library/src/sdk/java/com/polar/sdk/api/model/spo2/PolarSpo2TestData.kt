package com.polar.sdk.api.model

data class PolarSpo2TestData(
    val recordingDevice: String? = null,
    val testTime: String? = null,
    val timeZoneOffsetMinutes: Int? = null,
    val testStatus: Spo2TestStatus? = null,
    val bloodOxygenPercent: Int? = null,
    val spo2Class: Spo2Class? = null,
    val spo2ValueDeviationFromBaseline: DeviationFromBaseline? = null,
    val spo2QualityAveragePercent: Float? = null,
    val averageHeartRateBpm: UInt? = null,
    val heartRateVariabilityMs: Float? = null,
    val spo2HrvDeviationFromBaseline: DeviationFromBaseline? = null,
    val altitudeMeters: Float? = null
)

enum class Spo2Class(val value: Int) {
    UNKNOWN(0),
    VERY_LOW(1),
    LOW(2),
    NORMAL(3);

    companion object {
        fun from(value: Int): Spo2Class? = entries.firstOrNull { it.value == value }
    }
}

enum class Spo2TestTriggerType(val value: Int) {
    MANUAL(0),
    AUTOMATIC(1);

    companion object {
        fun from(value: Int): Spo2TestTriggerType? = entries.firstOrNull { it.value == value }
    }
}

enum class Spo2TestStatus(val value: Int) {
    PASSED(0),
    INCONCLUSIVE_TOO_LOW_QUALITY_IN_SAMPLES(1),
    INCONCLUSIVE_TOO_LOW_OVERALL_QUALITY(2),
    INCONCLUSIVE_TOO_MANY_MISSING_SAMPLES(3);

    companion object {
        fun from(value: Int): Spo2TestStatus? = entries.firstOrNull { it.value == value }
    }
}

enum class DeviationFromBaseline(val value: Int) {
    NO_BASELINE(0),
    BELOW_USUAL(1),
    USUAL(2),
    ABOVE_USUAL(3);

    companion object {
        fun from(value: Int): DeviationFromBaseline? = entries.firstOrNull { it.value == value }
    }
}