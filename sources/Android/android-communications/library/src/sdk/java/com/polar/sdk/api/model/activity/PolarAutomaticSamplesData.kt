package com.polar.sdk.api.model.activity

import java.util.Date

data class Polar247HrSamplesData(
        val date: Date,
        val hrSamples: List<Int>,
        val triggerType: AutomaticSampleTriggerType
)

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
    TRIGGER_TYPE_MANUAL(4)
}