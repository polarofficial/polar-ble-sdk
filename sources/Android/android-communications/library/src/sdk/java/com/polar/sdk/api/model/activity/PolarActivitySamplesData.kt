package com.polar.sdk.api.model.activity

import com.polar.sdk.impl.utils.PolarTimeUtils
import fi.polar.remote.representation.protobuf.ActivitySamples
import java.time.LocalDateTime

/**
 * [PolarActivitySamplesData] data for given [startTime].
 */

data class PolarActivitySamplesData(var startTime: LocalDateTime? = null,
                                    var metRecordingInterval: Int? = null,
                                    var metSamples: List<Float> = emptyList(),
                                    var stepRecordingInterval: Int? = null,
                                    var stepSamples: List<Int> = emptyList(),
                                    var activityInfoList: List<PolarActivityInfo> = emptyList()
    )

data class PolarActivitySamplesDayData(var polarActivitySamplesDataList: List<PolarActivitySamplesData>? = null)
data class PolarActivityInfo(var activityClass: PolarActivityClass?, var timeStamp: LocalDateTime, var factor: Float)

enum class PolarActivityClass(val value: Int) {
    SLEEP(1),
    SEDENTARY(2),
    LIGHT(3),
    CONTINUOUS_MODERATE(4),
    INTERMITTENT_MODERATE(5),
    CONTINUOUS_VIGOROUS(6),
    INTERMITTENT_VIGOROUS(7),
    NON_WEAR(8);

    companion object {
        infix fun from(value: Int): PolarActivityClass? =
            PolarActivityClass.values().firstOrNull { it.value == value }
    }
}

fun parsePbActivityInfo(pbActivityInfoList: List<ActivitySamples.PbActivityInfo>): List<PolarActivityInfo> {

    val polarActivityInfoList: MutableList<PolarActivityInfo> = mutableListOf()

    for (pbActivityInfo in pbActivityInfoList) {
        polarActivityInfoList.add(
            PolarActivityInfo(
                PolarActivityClass.from(pbActivityInfo.value.number),
                PolarTimeUtils.pbLocalDateTimeToLocalDateTime(pbActivityInfo.timeStamp),
                pbActivityInfo.factor
            )
        )
    }
    return polarActivityInfoList
}