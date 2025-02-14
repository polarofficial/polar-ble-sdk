package com.polar.sdk.api.model

/**
 * Polar pressure data
 * @property samples from pressure sensor. Each sample contains pressure value in bar units
 */
data class PolarPressureData(val samples: List<PolarPressureDataSample>) {
    /**
     * Polar pressure data sample
     *  @property timeStamp moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
     *  @property pressure value in bar
     */
    class PolarPressureDataSample(
        val timeStamp: Long,
        val pressure: Float
    )
}