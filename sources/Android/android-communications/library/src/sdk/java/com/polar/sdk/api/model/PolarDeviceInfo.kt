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
    val hasFileSystemService: Boolean = false,

    /**
     * true if device has SAGRFC filesystem, false otherwise.
     * If true, more abundant settings, like:
     * - User device settings
     * - User physical settings
     * If true, actions like:
     * - Offline measurement and measurement data reading and deletion
     * - Reading activity data files from device
     * - Device data logging
     * - Device activity data reading and deletion
     * - Reading device time
     * - Exercise support (if available in device sw)
     * are enabled.
     * If false, the device may have limited or none settings available.
     */
    val hasSAGRFCFileSystem: Boolean = false
)