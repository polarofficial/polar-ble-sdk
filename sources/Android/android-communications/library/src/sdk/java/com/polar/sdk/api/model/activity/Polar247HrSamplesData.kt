package com.polar.sdk.api.model.activity

import com.polar.sdk.impl.utils.PolarTimeUtils
import fi.polar.remote.representation.protobuf.AutomaticSamples
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import java.time.LocalDate
import java.time.LocalTime

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
                val sample = Polar247HrSamples(
                    PolarTimeUtils.pbTimeToLocalTime(heartRateSampleProto.time),
                    heartRateSampleProto.heartRateList,
                    AutomaticSampleTriggerType.fromProto(heartRateSampleProto.triggerType.number)
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
        infix fun fromProto(value: Int): AutomaticSampleTriggerType =
            AutomaticSampleTriggerType.values().first { it.value == value }
    }
}