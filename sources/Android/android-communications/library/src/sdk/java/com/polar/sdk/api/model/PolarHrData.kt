// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import kotlin.math.roundToInt

data class PolarHrData(
    val samples: List<PolarHrSample>
) {
    /**
     * Polar heart rate sample
     * @property hr value is heart rate in BPM (beats per minute).
     * @property rrsMs RRs in milliseconds.
     * @property contactStatus true if the sensor has contact (with a measurable surface e.g. skin)
     * @property contactStatusSupported  true if the sensor supports contact status
     * @property rrAvailable true if RR data is available.
     */
    class PolarHrSample(
        val hr: Int,
        private val rrs: List<Int>,
        val contactStatus: Boolean,
        val contactStatusSupported: Boolean,
        val rrAvailable: Boolean
    ) {
        val rrsMs: List<Int>

        init {
            rrsMs = mutableListOf()
            for (rrRaw in rrs) {
                rrsMs.add((rrRaw.toFloat() / 1024.0 * 1000.0).roundToInt())
            }
        }
    }
}