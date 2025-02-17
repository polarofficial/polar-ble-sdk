// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

/**
 * Polar accelerometer data.
 * @property samples from acceleration sensor. Each sample contains signed x,y,z axis value in millig
 */
data class PolarAccelerometerData(
    val samples: List<PolarAccelerometerDataSample>
) {

    /**
     * Polar accelerometer data sample
     *  @property timeStamp moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
     *  @property x axis value in millig (including gravity)
     *  @property y axis value in millig (including gravity)
     *  @property z axis value in millig (including gravity)
     */
    data class PolarAccelerometerDataSample(
        val timeStamp: Long,
        val x: Int,
        val y: Int,
        val z: Int
    )
}