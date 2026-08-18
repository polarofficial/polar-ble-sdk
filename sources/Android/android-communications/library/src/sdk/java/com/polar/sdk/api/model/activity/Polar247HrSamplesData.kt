package com.polar.sdk.api.model.activity

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.sdk.impl.utils.PolarTimeUtils
import fi.polar.remote.representation.protobuf.AutomaticSamples
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import java.time.LocalDate
import java.time.LocalTime

private const val TAG = "Polar247HrSamplesData"

data class Polar247HrSamplesData(
    val date: LocalDate,
    val samples: List<Polar247HrSamples>
) {
    companion object {
        fun fromProto(proto: PbAutomaticSampleSessions): Polar247HrSamplesData {
            return Polar247HrSamplesData(
                PolarTimeUtils.pbDateToLocalDate(proto.day),
                Polar247HrSamples.fromProto(proto.samplesList)
            )
        }
    }
}

data class Polar247HrSamples(
    val startTime: LocalTime,
    val hrSamples: List<Int>,
    val triggerType: AutomaticSampleTriggerType
) {
    companion object {
        fun fromProto(heartRateSampleProtos: List<AutomaticSamples.PbAutomaticHeartRateSamples>): List<Polar247HrSamples> {
            var heartRateSampleList = mutableListOf<Polar247HrSamples>()
            for (heartRateSampleProto in heartRateSampleProtos ) {
                val triggerTypeValue = heartRateSampleProto.triggerType.number
                val triggerType = AutomaticSampleTriggerType.fromProto(triggerTypeValue)
                if (triggerType == null) {
                    BleLogger.w(TAG, "Skipping HR sample with unknown trigger type: $triggerTypeValue")
                    continue
                }
                val sample = Polar247HrSamples(
                    PolarTimeUtils.pbTimeToLocalTime(heartRateSampleProto.time),
                    heartRateSampleProto.heartRateList,
                    triggerType
                )
                heartRateSampleList.add(sample)
            }
            return heartRateSampleList
        }
    }
}

enum class AutomaticSampleTriggerType(val value: Int) {
    /**
     * Automatic measurement triggered by user high activity. Contains 1-n samples
     */
    TRIGGER_TYPE_HIGH_ACTIVITY(1),

    /**
     * Automatic measurement triggered by user low activity. Contains 1-n samples
     */
    TRIGGER_TYPE_LOW_ACTIVITY(2),

    /**
     * Automatic measurement triggered by timer. Contains 1 sample
     */
    TRIGGER_TYPE_TIMED(3),

    /**
     * Manual measurement (other than exercise) triggered by user. Contains 1-n samples
     */
    TRIGGER_TYPE_MANUAL(4);

    companion object {
        /**
         * Returns the matching [AutomaticSampleTriggerType] for the given protobuf number,
         * or **null** if the device reports an unknown trigger type (e.g. from newer firmware).
         * Callers should skip samples with a null trigger type and log the unknown value.
         */
        infix fun fromProto(value: Int): AutomaticSampleTriggerType? =
            AutomaticSampleTriggerType.values().firstOrNull { it.value == value }
    }
}