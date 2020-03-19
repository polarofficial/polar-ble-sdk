// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api.model;

import java.util.List;

/**
 * For electrocardiography data.
 */

public class PolarEcgData {

    /**
     * ECG samples in microVolts.
     */
    public List<Integer> samples;

    /**
     * Last sample timestamp in nanoseconds.
     */
    public long timeStamp;

    public PolarEcgData(List<Integer> samples, long timeStamp) {
        this.samples = samples;
        this.timeStamp = timeStamp;
    }
}
