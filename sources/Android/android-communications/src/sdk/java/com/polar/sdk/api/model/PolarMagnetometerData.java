package com.polar.sdk.api.model;

import androidx.annotation.NonNull;

import java.util.List;

public class PolarMagnetometerData {

    public static class PolarMagnetometerDataSample {
        /**
         * x axis in Gauss
         */
        public final float x;
        /**
         * y axis in Gauss
         */
        public final float y;
        /**
         * z axis in Gauss
         */
        public final float z;

        public PolarMagnetometerDataSample(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Acceleration samples list. Each sample contains signed x,y,z axis value in Gauss
     */
    public final List<PolarMagnetometerDataSample> samples;

    /**
     * Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
     */
    public final long timeStamp;

    /**
     * Constructor
     *
     * @param samples   magnetometer samples
     * @param timeStamp in nanoseconds
     */
    public PolarMagnetometerData(@NonNull List<PolarMagnetometerDataSample> samples, long timeStamp) {
        this.samples = samples;
        this.timeStamp = timeStamp;
    }
}
