// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api.model;

import java.util.ArrayList;
import java.util.List;

/**
 * For photoplethysmography data.
 */
public class PolarOhrPPGData {

    /**
     * Static class containing the data of a single PPG sample.
     */
    public static class PolarOhrPPGSample {
        /**
         * Integer value received from the LED in ppg0 channel. PPG = Photoplethysmography
         */
        public int ppg0;
        /**
         * Integer value received from the LED in ppg1 channel.
         */
        public int ppg1;
        /**
         * Integer value received from the LED in ppg2 channel.
         */
        public int ppg2;
        /**
         * Integer value that is used to cancel ambient light while the measurement is being made from the skin.
         */
        public int ambient;

        public List<Integer> ppgDataSamples;
        public int ambient2;
        public long status;

        public PolarOhrPPGSample(int ppg0, int ppg1, int ppg2, int ambient) {
            this.ppg0 = ppg0;
            this.ppg1 = ppg1;
            this.ppg2 = ppg2;
            this.ambient = ambient;
            this.ppgDataSamples = new ArrayList<>();
            this.ambient2 = 0;
        }

        public PolarOhrPPGSample(int ppg0, int ppg1, int ppg2, int ambient, List<Integer> ppgDataSamples, int ambient2, long status) {
            this.ppg0 = ppg0;
            this.ppg1 = ppg1;
            this.ppg2 = ppg2;
            this.ambient = ambient;
            this.ppgDataSamples = ppgDataSamples;
            this.ambient2 = ambient2;
            this.status = status;
        }
    }

    public byte type;

    /**
     * PPG samples list. Each sample contains signed LED in ppg0, LED in ppg1, LED in ppg2 and ambient light value
     */
    public List<PolarOhrPPGSample> samples;

    /**
     * Last sample timestamp in nanoseconds
     */
    public long timeStamp;

    /**
     * Class constructor
     * @param samples list of PPG data samples
     * @param timeStamp in nanoseconds
     */
    public PolarOhrPPGData(List<PolarOhrPPGSample> samples, long timeStamp, byte type) {
        this.samples = samples;
        this.timeStamp = timeStamp;
        this.type = type;
    }
}
