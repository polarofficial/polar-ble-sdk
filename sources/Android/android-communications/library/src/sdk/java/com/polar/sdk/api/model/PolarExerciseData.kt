// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

/**
 * Polar exercise data container.
 */
class PolarExerciseData(
    /**
     * Recording interval in seconds.
     */
    val recordingInterval: Int,
    /**
     * HR or RR samples.
     */
    val hrSamples: List<Int>
)