// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * For accelerometer data.
 */
public class PolarAccelerometerData {

    public static class PolarAccelerometerDataSample {
        /**
         * x axis in millig
         */
        public final int x;
        /**
         * y axis in millig
         */
        public final int y;
        /**
         * z axis in millig
         */
        public final int z;

        public PolarAccelerometerDataSample(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Acceleration samples list. Each sample contains signed x,y,z axis value in millig
     */
    public final List<PolarAccelerometerDataSample> samples;

    /**
     * Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
     */
    public final long timeStamp;

    /**
     * Class constructor
     *
     * @param samples   list of Accelerometer data samples
     * @param timeStamp in nanoseconds
     */
    public PolarAccelerometerData(@NonNull List<PolarAccelerometerDataSample> samples, long timeStamp) {
        this.samples = samples;
        this.timeStamp = timeStamp;
    }
}
