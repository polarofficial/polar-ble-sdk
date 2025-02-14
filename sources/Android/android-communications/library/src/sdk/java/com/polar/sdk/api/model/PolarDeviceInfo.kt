// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

/**
 * Contains information about the current Device.
 */
data class PolarDeviceInfo(
    /**
     * Polar device id
     */
    val deviceId: String,

    /**
     * Bt mac address
     */
    val address: String,

    /**
     * Received signal strength indication value in dBm.
     */
    val rssi: Int,

    /**
     * Device name.
     */
    val name: String,

    /**
     * true adv type is connectable
     */
    val isConnectable: Boolean,

    /**
     * true if device has heart rate service available
     */
    val hasHeartRateService: Boolean = false,

    /**
     * true if device has file system service (PSFTP) available
     */
    val hasFileSystemService: Boolean = false
)