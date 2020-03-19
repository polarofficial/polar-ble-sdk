// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api.model;

import java.util.List;

/**
 * For bioz data
 */
public class PolarBiozData {

    /**
     * Last sample timestamp in nanoseconds.
     */
    public long timeStamp;

    /**
     * Bioz sample list. Each sample in signed 20bit adc value.
     */
    public List<Integer> samples;
    public byte status;
    public byte type;

    public PolarBiozData(long timeStamp, List<Integer> samples, byte status, byte type) {
        this.timeStamp = timeStamp;
        this.samples = samples;
        this.status = status;
        this.type = type;
    }
}
