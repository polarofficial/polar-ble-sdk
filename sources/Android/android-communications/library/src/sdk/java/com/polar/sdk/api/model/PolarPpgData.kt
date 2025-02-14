// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

/**
 * Polar optical sensor raw data
 * @property samples from optical sensor. Each sample contains signed raw PPG (Photoplethysmography) data and ambient light value
 * @property type of data, which varies based on what is type of optical sensor used in the device
 */
data class PolarPpgData(
    val samples: List<PolarPpgSample>,
    val type: PpgDataType
) {

    /**
     * Polar optical sensor raw data sample
     *  @property timeStamp moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
     *  @property channelSamples is the PPG (Photoplethysmography) raw value received from the optical sensor. Based on [type] the amount of
     *  channels varies. Typically ppg(n) channel + n ambient(s).
     */
    data class PolarPpgSample(
        val timeStamp: Long,
        val channelSamples: List<Int>,
    )

    enum class PpgDataType {
        /**
         * Polar ohr led data containing 3 ppg and 1 ambient channel
         */
        PPG3_AMBIENT1,
        FRAME_TYPE_4,
        FRAME_TYPE_5,
        FRAME_TYPE_7,
        FRAME_TYPE_8,
        FRAME_TYPE_9,
        FRAME_TYPE_10,
        SPORT_ID,
        UNKNOWN
    }
}