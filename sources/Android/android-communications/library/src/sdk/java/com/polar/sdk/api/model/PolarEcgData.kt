// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

/**
 * For electrocardiography data.
 * @property samples Ecg data samples
 */
data class PolarEcgData(
    val samples: List<PolarEcgDataSample>,
    @Deprecated("use the timestamp found in each sample")
    val timeStamp: Long
) {
    /**
     * Polar ecg data sample
     *  @property timeStamp moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
     *  @property voltage ECG in microVolts.
     */
    data class PolarEcgDataSample(
        val timeStamp: Long,
        val voltage: Int
    )
}
