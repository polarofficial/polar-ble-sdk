// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api.model;

import java.util.List;

/**
 * Class PolarOhrPPIData is used to output the peak-to-peak interval between heartbeats in milliseconds
 */

public class PolarOhrPPIData {

    public static class PolarOhrPPISample {

        /**
         * Pulse to Pulse interval in milliseconds.
         */
        public int ppi;

        /**
         * Error estimate in milliseconds.
         */
        public int errorEstimate;

        /**
         * Heart rate in beats per minute.
         */
        public int hr;

        /**
         * True if PPI measurement is invalid due to acceleration (or other reason).
         */
        public boolean blockerBit;

        /**
         * False if the device detects poor or no contact with the skin.
         */
        public boolean skinContactStatus;

        /**
         * True if the Sensor Contact feature is supported.
         */
        public boolean skinContactSupported;

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
     * Last sample timestamp in nanoseconds
     */
    public long timeStamp;


    /**
     * PPI samples list. Sample with ppi in ms, errorEstimate in ms, hr in BPM,
     * blockerBit = True if PPI measurement is invalid due to acceleration (or other reason) ,
     * skinContactSupported = True if the Sensor Contact feature is supported.
     */
    public List<PolarOhrPPISample> samples;

    public PolarOhrPPIData(long timeStamp, List<PolarOhrPPISample> samples) {
        this.timeStamp = timeStamp;
        this.samples = samples;
    }
}
