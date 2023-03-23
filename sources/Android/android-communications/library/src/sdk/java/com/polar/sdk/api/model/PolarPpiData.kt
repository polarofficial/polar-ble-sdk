// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

/**
 * Polar Peak-to-Peak interval (PPI) data measured by the optical heart rate sensor
 * @property samples from optical sensor. Each sample contains PPI related data
 */
data class PolarPpiData(
    val samples: List<PolarPpiSample>
) {
    /**
     * Polar PPI data sample
     *  @property ppi Pulse to Pulse interval in milliseconds.
     *  @property errorEstimate Error estimate of the expected absolute error in PP-interval in milliseconds.
     *  The value indicates the quality of PP-intervals. When error estimate is below 10ms the PP-intervals.
     *  When error estimate is below 10ms the PP-intervals are probably very accurate. Error estimate values
     *  over 30ms may be caused by movement artefact or too loose sensor-skin contact.
     *  @property hr heart rate in beats per minute.
     *  @property blockerBit true if PPI measurement is invalid due to acceleration (or other reason).
     *  @property skinContactStatus false if the device detects poor or no contact with the skin.
     *  @property skinContactSupported true if the Sensor Contact feature is supported.
     */
    data class PolarPpiSample(
        val ppi: Int,
        val errorEstimate: Int,
        val hr: Int,
        val blockerBit: Boolean,
        val skinContactStatus: Boolean,
        val skinContactSupported: Boolean
    )
}