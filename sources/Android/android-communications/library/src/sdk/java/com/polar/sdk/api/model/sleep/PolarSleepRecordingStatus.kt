// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model.sleep

/**
 * Sleep recording status reported by the device.
 */
enum class PolarSleepRecordingStatus {
    /** Sleep recording is on. */
    ENABLED,

    /** Sleep recording is off. */
    DISABLED,

    /** The device did not report the state. */
    UNKNOWN
}
