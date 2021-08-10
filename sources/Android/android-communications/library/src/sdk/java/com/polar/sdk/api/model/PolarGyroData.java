package com.polar.sdk.api.model;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Polar gyro data
 */
public class PolarGyroData {

    public static class PolarGyroDataSample {
        /**
         * x axis in deg/sec
         */
        public final float x;
        /**
         * y axis in deg/sec
         */
        public final float y;
        /**
         * z axis in deg/sec
         */
        public final float z;

        public PolarGyroDataSample(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Signed 3-axis samples in deg/sec
     */
    public final List<PolarGyroDataSample> samples;

    /**
     * Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
     */
    public final long timeStamp;

    public PolarGyroData(@NonNull List<PolarGyroDataSample> samples, long timeStamp) {
        this.samples = samples;
        this.timeStamp = timeStamp;
    }
}
