// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

/**
 * For electrocardiography data.
 * @property samples Ecg data samples
 */
data class PolarEcgData(
    val samples: List<PolarEcgDataSample>
)

/**
 * Polar ecg data sample types
 *  @property timeStamp moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
 */
sealed class PolarEcgDataSample(
    val timeStamp: Long
)

class EcgSample(timeStamp: Long, val voltage: Int) : PolarEcgDataSample(timeStamp)
class FecgSample(timeStamp: Long, val ecg: Int, val bioz: Int, val status: UByte) : PolarEcgDataSample(timeStamp)