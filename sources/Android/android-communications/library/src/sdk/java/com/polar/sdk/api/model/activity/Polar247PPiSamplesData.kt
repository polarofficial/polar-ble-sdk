package com.polar.sdk.api.model.activity

import com.polar.sdk.impl.utils.PolarTimeUtils
import fi.polar.remote.representation.protobuf.AutomaticSamples
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType
import java.time.LocalTime
import java.util.Date

/**
 * Polar Peak-to-peak interval data
 * @property samples from sensor. Each sample contains PPi value, PPi error estimate and status.
 */
data class Polar247PPiSamplesData(
    val date: Date,
    val samples: PolarPpiDataSample
)

/**
 * Polar 24/7 PPi data sample
 *  @property startTime, start time of the sample session
 *  @property triggerType, describes how the measurement was triggered
 *  @property ppiValueList, list of Peak-to-Peak interval values in the sample session
 *  @property ppiErrorEstimateList, List of error estimate  values in the sample session
 *  @property statusList, status values in the sample session
 */
data class PolarPpiDataSample(
    val startTime: LocalTime,
    val triggerType: PPiSampleTriggerType,
    val ppiValueList: List<Int>,
    val ppiErrorEstimateList: List<Int>,
    val statusList: List<PPiSampleStatus>
)

fun fromPbPPiDataSamples(pbPPiData: AutomaticSamples.PbPpIntervalAutoSamples): PolarPpiDataSample {

    var ppiSampleStatusList = mutableListOf<PPiSampleStatus>()
    var ppiValueList = mutableListOf<Int>()
    var ppiErrorEstimateList = mutableListOf<Int>()
    var previousSample = 0

    pbPPiData.ppi.ppiDeltaList.forEach { sample ->
        val uncompressedSample = previousSample + sample
        ppiValueList.add(uncompressedSample)
        previousSample = uncompressedSample
    }

    previousSample = 0

    pbPPiData.ppi.ppiErrorEstimateDeltaList.forEach { sample ->
        val uncompressedSample = previousSample + sample
        ppiErrorEstimateList.add(uncompressedSample)
        previousSample = uncompressedSample
    }

    pbPPiData.ppi.statusList.forEach { sample ->
        PPiSampleStatus.from(sample)?.let { ppiSampleStatusList.add(it) }
    }
    return PolarPpiDataSample(
        PolarTimeUtils.pbTimeToLocalTime(pbPPiData.recordingTime),
        PPiSampleTriggerType.from(pbPPiData.triggerType),
        ppiValueList,
        ppiErrorEstimateList,
        ppiSampleStatusList
    )
}

enum class PPiSampleTriggerType(val value: Int) {
    /**
     * Undefined
     */
    TRIGGER_TYPE_UNDEFINED(0),

    /**
     * Automatic recording (for example 24/7 recording)
     */
    TRIGGER_TYPE_AUTOMATIC(1),

    /**
     * Manual recording (started by user)
     */
    TRIGGER_TYPE_MANUAL(2);

    companion object {
        infix fun from(value: PbPpIntervalRecordingTriggerType): PPiSampleTriggerType{
            return when(value) {
                PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_MANUAL -> TRIGGER_TYPE_MANUAL
                PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC -> TRIGGER_TYPE_AUTOMATIC
                PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_UNDEFINED -> TRIGGER_TYPE_UNDEFINED
            }
        }
    }
};

data class PPiSampleStatus(val skinContact: SkinContact, val movement: Movement, val intervalStatus: IntervalStatus) {

    companion object {
        fun from(value: Int): PPiSampleStatus? {
            val binary = Integer.toBinaryString((1 shl 3) or `value`).substring(1)
            // Start reading the binary from LSB (from the right side end of the string)
            return SkinContact.from(Integer.valueOf(binary[2].toString()))?.let {
                Movement.from(Integer.valueOf(binary[1].toString()))?.let { it1 ->
                    IntervalStatus.from(Integer.valueOf(binary[0].toString()))?.let { it2 ->
                        PPiSampleStatus(
                            it,
                            it1,
                            it2
                        )
                    }
                }
            }
        }
    }
};

enum class SkinContact(val value: Int) {
    NO_SKIN_CONTACT(0),
    SKIN_CONTACT_DETECTED(1);

    companion object {
        infix fun from(value: Int): SkinContact? =
            SkinContact.values().firstOrNull { it.value == value }
    }
}

enum class Movement(val value: Int) {
    NO_MOVING_DETECTED(0),
    MOVING_DETECTED(1);

    companion object {
        infix fun from(value: Int): Movement? =
            Movement.values().firstOrNull { it.value == value }
    }
}

enum class IntervalStatus(val value: Int) {
    INTERVAL_IS_ONLINE(0),
    INTERVAL_DENOTES_OFFLINE_PERIOD(1);

    companion object {
        infix fun from(value: Int): IntervalStatus? =
            IntervalStatus.values().firstOrNull { it.value == value }
    }
}

