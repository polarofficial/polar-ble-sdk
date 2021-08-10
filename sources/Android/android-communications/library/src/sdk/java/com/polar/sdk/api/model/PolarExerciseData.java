// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model;

import java.util.List;

/**
 * Polar exercise data container.
 */
public class PolarExerciseData {
    /**
     * Recording interval in seconds.
     */
    public final int recordingInterval;
    /**
     * HR or RR samples.
     */
    public final List<Integer> hrSamples;

    public PolarExerciseData(int recordingInterval, List<Integer> hrSamples) {
        this.recordingInterval = recordingInterval;
        this.hrSamples = hrSamples;
    }
}
