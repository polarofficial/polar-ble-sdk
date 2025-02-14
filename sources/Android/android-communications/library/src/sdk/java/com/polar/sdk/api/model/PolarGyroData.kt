package com.polar.sdk.api.model

/**
 * Polar gyro data
 * @property samples from gyroscope. Each sample contains signed 3-axis samples in deg/sec
 */
data class PolarGyroData(
    val samples: List<PolarGyroDataSample>
) {
    /**
     * Polar gyroscope data sample
     *  @property timeStamp moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
     *  @property x axis value in deg/sec
     *  @property y axis value in deg/sec
     *  @property z axis in deg/sec
     */
    data class PolarGyroDataSample(
        val timeStamp: Long,
        val x: Float,
        val y: Float,
        val z: Float
    )
}