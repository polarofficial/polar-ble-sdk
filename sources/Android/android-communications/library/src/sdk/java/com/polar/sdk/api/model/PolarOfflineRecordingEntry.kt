// Copyright © 2022 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import com.polar.sdk.api.PolarBleApi
import java.util.*

/**
 * Polar offline recording entry container.
 */
data class PolarOfflineRecordingEntry(
    /**
     * Recording entry path in device.
     */
    @JvmField
    val path: String,
    /**
     * Recording size in bytes.
     */
    @JvmField
    val size: Long,
    /**
     * The date and time of the recording entry i.e. the moment recording is started
     */
    @JvmField
    val date: Date,
    /**
     * data type of the recording
     */
    @JvmField
    val type: PolarBleApi.PolarDeviceDataType
)