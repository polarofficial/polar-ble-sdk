package com.polar.sdk.api.model

/**
 * Polar magnetometer data
 * @property samples from magnetometer. Each sample contains signed x,y,z axis value in Gauss
 */
data class PolarMagnetometerData(
    val samples: List<PolarMagnetometerDataSample>,
) {

    /**
     * Polar magnetometer data sample
     *  @property timeStamp moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
     *  @property x axis value in Gauss
     *  @property y axis value in Gauss
     *  @property z axis value in Gauss
     */
    data class PolarMagnetometerDataSample(
        val timeStamp: Long,
        val x: Float,
        val y: Float,
        val z: Float
    )
}