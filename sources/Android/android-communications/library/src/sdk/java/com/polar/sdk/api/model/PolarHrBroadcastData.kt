// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

/**
 * For broadcasting heart rate data with signal strength and device info. Useful when using multiple sensors.
 */
data class PolarHrBroadcastData(
    /**
     * Device information
     *
     * @see PolarDeviceInfo
     */
    val polarDeviceInfo: PolarDeviceInfo,
    /**
     * Heart rate in beats per minute.
     */
    val hr: Int,
    /**
     * Device battery status.
     * False if the battery needs to be replaced or recharged.
     */
    val batteryStatus: Boolean
)