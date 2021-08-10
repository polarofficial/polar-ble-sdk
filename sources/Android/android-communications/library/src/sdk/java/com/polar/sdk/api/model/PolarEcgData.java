// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * For electrocardiography data.
 */

public class PolarEcgData {

    /**
     * ECG samples in microVolts.
     */
    public final List<Integer> samples;

    /**
     * Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
     */
    public final long timeStamp;

    public PolarEcgData(@NonNull List<Integer> samples, long timeStamp) {
        this.samples = samples;
        this.timeStamp = timeStamp;
    }
}
