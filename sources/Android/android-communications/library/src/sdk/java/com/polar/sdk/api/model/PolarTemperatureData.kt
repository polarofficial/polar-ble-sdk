package com.polar.sdk.api.model

/**
 * Polar temperature data
 * @property samples from temperature sensor. Each sample contains temperature value in celsius units
 */
data class PolarTemperatureData(
    val samples: List<PolarTemperatureDataSample>
) {
    /**
     * Polar temperature data sample
     *  @property timeStamp moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
     *  @property temperature value in celsius
     */
    class PolarTemperatureDataSample(
        val timeStamp: Long,
        val temperature: Float
    )
}