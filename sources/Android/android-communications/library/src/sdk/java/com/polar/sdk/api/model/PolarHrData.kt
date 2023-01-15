// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import kotlin.math.roundToInt

/**
 * Polar heart rate data
 * @property hr value is heart rate in BPM (beats per minute).
 * @property rrs list of rrs values. R is the peak of the QRS complex in the ECG wave and RR is the interval between successive Rs. In 1/1024 format.
 * @property rrsMs RRs in milliseconds.
 * @property contactStatus true if the sensor has contact (with a measurable surface e.g. skin)
 * @property contactStatusSupported  true if the sensor supports contact status
 * @property rrAvailable true if RR data is available.
 */

data class PolarHrData(
    val hr: Int,
    val rrs: List<Int>,
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