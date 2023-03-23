// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

/**
 * Polar optical sensor raw data
 * @property samples from optical sensor. Each sample contains signed raw PPG (Photoplethysmography) data and ambient light value
 * @property type of data, which varies based on what is type of optical sensor used in the device
 */
@Deprecated("PolarOhrData is renamed to better describe data content, use PolarPpgData", replaceWith = ReplaceWith("PolarPpgData"))
data class PolarOhrData(
    val samples: List<PolarOhrSample>,
    val type: OhrDataType,
    @Deprecated("This field is deprecated as each sample has now own timeStamp")
    val timeStamp: Long
) {

    /**
     * Polar optical sensor raw data sample
     *  @property timeStamp moment sample is taken in nanoseconds. The epoch of timestamp is 1.1.2000
     *  @property channelSamples is the PPG (Photoplethysmography) raw value received from the optical sensor. Based on [type] the amount of
     *  channels varies. Typically ppg(n) channel + n ambient(s).
     */
    data class PolarOhrSample(
        val timeStamp: Long,
        val channelSamples: List<Int>,
        @Deprecated("status of the OHR data doesn't contain any relevant information. Will be removed in future releases.")
        val status: Long = 0L
    )

    enum class OhrDataType {
        /**
         * Polar ohr led data containing 3 ppg and 1 ambient channel
         */
        PPG3_AMBIENT1,
        UNKNOWN
    }
}