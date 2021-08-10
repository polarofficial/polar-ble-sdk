// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model;

import java.util.ArrayList;
import java.util.List;

/**
 * For heart rate data.
 */
public class PolarHrData {

    /**
     * Heart rate in BPM (beats per minute).
     */
    public final int hr;

    /**
     * R is the peak of the QRS complex in the ECG wave and RR is the interval between successive Rs.
     * In 1/1024 format.
     */
    public final List<Integer> rrs;

    /**
     * RRs in milliseconds.
     */
    public final List<Integer> rrsMs;

    /**
     * Equals true if the sensor has contact (with a measurable surface e.g. skin).
     */
    public final boolean contactStatus;

    /**
     * Equals true if the sensor supports contact status
     */
    public final boolean contactStatusSupported;

    /**
     * Equals true if RR data is available.
     */
    public final boolean rrAvailable;

    public PolarHrData(int hr, List<Integer> rrs, boolean contactStatus, boolean contactStatusSupported, boolean rrAvailable) {
        this.hr = hr;
        this.rrs = rrs;
        this.contactStatus = contactStatus;
        this.contactStatusSupported = contactStatusSupported;
        this.rrAvailable = rrAvailable;
        rrsMs = new ArrayList<>();
        for (int rrRaw : rrs) {
            rrsMs.add((int) (Math.round(((float) rrRaw / 1024.0) * 1000.0)));
        }
    }
}