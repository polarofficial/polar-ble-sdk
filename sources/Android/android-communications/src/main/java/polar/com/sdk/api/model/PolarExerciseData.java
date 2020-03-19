// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api.model;

import java.util.List;

/**
 * Polar exercise data container.
 */
public class PolarExerciseData {
    /**
     * Recording interval in seconds.
     */
    public int recordingInterval;
    /**
     * HR or RR samples.
     */
    public List<Integer> hrSamples;

    public PolarExerciseData(int recordingInterval, List<Integer> hrSamples) {
        this.recordingInterval = recordingInterval;
        this.hrSamples = hrSamples;
    }
}
