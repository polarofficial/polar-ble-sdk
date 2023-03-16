// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

data class PolarHrData(
    val samples: List<PolarHrSample>
) {
    /**
     * Polar heart rate sample
     * @property hr value is heart rate in BPM (beats per minute).
     * @property rrsMs RRs in milliseconds. R is a the top highest peak in the QRS complex of the ECG wave and RR is the interval between successive Rs.
     * @property rrAvailable true if RR data is available.
     * @property contactStatus true if the sensor has contact (with a measurable surface e.g. skin)
     * @property contactStatusSupported  true if the sensor supports contact status
     */
    data class PolarHrSample(
        val hr: Int,
        val rrsMs: List<Int>,
        val rrAvailable: Boolean,
        val contactStatus: Boolean,
        val contactStatusSupported: Boolean,
    )
}