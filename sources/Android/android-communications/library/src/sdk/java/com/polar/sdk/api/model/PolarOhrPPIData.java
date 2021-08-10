// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Class PolarOhrPPIData is used to output the peak-to-peak interval between heartbeats in milliseconds
 */

public class PolarOhrPPIData {

    public static class PolarOhrPPISample {

        /**
         * Pulse to Pulse interval in milliseconds.
         */
        public final int ppi;

        /**
         * Error estimate of the expected absolute error in PP-interval in milliseconds. The value
         * indicates the quality of PP-intervals. When error estimate is below 10ms the PP-intervals
         * are probably very accurate. Error estimate values over 30ms may be caused by movement
         * artefact or too loose sensor-skin contact.
         */
        public final int errorEstimate;

        /**
         * Heart rate in beats per minute.
         */
        public final int hr;

        /**
         * True if PPI measurement is invalid due to acceleration (or other reason).
         */
        public final boolean blockerBit;

        /**
         * False if the device detects poor or no contact with the skin.
         */
        public final boolean skinContactStatus;

        /**
         * True if the Sensor Contact feature is supported.
         */
        public final boolean skinContactSupported;

        public PolarOhrPPISample(int ppi, int errorEstimate, int hr, boolean blockerBit, boolean skinContactStatus, boolean skinContactSupported) {
            this.ppi = ppi;
            this.errorEstimate = errorEstimate;
            this.hr = hr;
            this.blockerBit = blockerBit;
            this.skinContactStatus = skinContactStatus;
            this.skinContactSupported = skinContactSupported;
        }
    }

    /**
     * timestamp N/A always 0
     */
    public final long timeStamp;

    /**
     * PPI samples list. Sample with ppi in ms, errorEstimate in ms, hr in BPM,
     * blockerBit = True if PPI measurement is invalid due to acceleration (or other reason) ,
     * skinContactSupported = True if the Sensor Contact feature is supported.
     */
    public final List<PolarOhrPPISample> samples;

    public PolarOhrPPIData(long timeStamp, @NonNull List<PolarOhrPPISample> samples) {
        this.timeStamp = timeStamp;
        this.samples = samples;
    }
}
