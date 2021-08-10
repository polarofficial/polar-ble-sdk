// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model;

/**
 * For broadcasting heart rate data with signal strength and device info. Useful when using multiple sensors.
 */
public class PolarHrBroadcastData {

    /**
     * @see polar.com.sdk.api.model.PolarDeviceInfo
     */
    public final PolarDeviceInfo polarDeviceInfo;

    /**
     * Heart rate in beats per minute.
     */
    public final int hr;

    /**
     * Device battery status.
     * False if the battery needs to be replaced or recharged.
     */
    public final boolean batteryStatus;

    public PolarHrBroadcastData(PolarDeviceInfo deviceInfo, int hr, boolean batteryStatus) {
        this.polarDeviceInfo = deviceInfo;
        this.hr = hr;
        this.batteryStatus = batteryStatus;
    }
}
