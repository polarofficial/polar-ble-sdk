// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import java.util.*

/**
 * Polar exercise entry container.
 */
class PolarExerciseEntry(
    /**
     * Resource path in device.
     */
    val path: String,
    /**
     * Date object contains the date and time of the exercise. Only valid with OH1 and Verity Sense.
     */
    val date: Date,
    /**
     * unique identifier. Only valid with H10
     */
    val identifier: String
)