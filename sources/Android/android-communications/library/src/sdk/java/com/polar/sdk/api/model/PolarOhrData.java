// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model;

import java.util.List;

/**
 * Polar optical sensor data
 */
public class PolarOhrData {

    /**
     * Static class containing the data of a single PPG (Photoplethysmography) sample.
     */
    public static class PolarOhrSample {
        /**
         * Integer value received from the LED in ppg(n) channel + n ambient(s).
         */
        public final List<Integer> channelSamples;

        public final long status;

        public PolarOhrSample(List<Integer> channelSamples, long status) {
            this.channelSamples = channelSamples;
            this.status = status;
        }
    }

    public enum OHR_DATA_TYPE {
        /**
         * Polar ohr led data containing 3 ppg and 1 ambient channel
         */
        PPG3_AMBIENT1,
        UNKNOWN
    }

    /**
     * source type
     */
    public final OHR_DATA_TYPE type;

    /**
     * PPG samples list. Each sample contains signed LED in ppg0, LED in ppg1, LED in ppg2 and ambient light value
     */
    public final List<PolarOhrSample> samples;

    /**
     * Last sample timestamp in nanoseconds. The epoch of timestamp is 1.1.2000
     */
    public final long timeStamp;

    /**
     * Class constructor
     *
     * @param samples   list of PPG data samples
     * @param type      data type of PGG data samples
     * @param timeStamp in nanoseconds
     */
    public PolarOhrData(List<PolarOhrSample> samples, OHR_DATA_TYPE type, long timeStamp) {
        this.samples = samples;
        this.timeStamp = timeStamp;
        this.type = type;
    }
}
