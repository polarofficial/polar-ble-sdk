// Copyright © 2024 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import com.polar.sdk.api.PolarBleApi
import java.util.*

/**
 * Polar offline recording entry container.
 */
data class PolarActivityRecordingEntry(
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
     * type of the recorded activity data
     */
    @JvmField
    val type: PolarBleApi.PolarActivityDataType
)
