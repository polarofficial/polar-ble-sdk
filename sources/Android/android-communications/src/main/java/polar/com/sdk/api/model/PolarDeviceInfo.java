// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api.model;

/**
 * Contains information about the current Device.
 */
public class PolarDeviceInfo {

    /**
     * Polar device id
     */
    public String deviceId;

    /**
     * Bt mac address
     */
    public String address;

    /**
     * Received signal strength indication value in dBm.
     */
    public int rssi;

    /**
     * Device name.
     */
    public String name;

    /**
     * true adv type is connectable
     */
    public boolean isConnectable;

    public PolarDeviceInfo(String deviceId, String address, int rssi, String name, boolean isConnectable) {
        this.deviceId = deviceId;
        this.address = address;
        this.rssi = rssi;
        this.name = name;
        this.isConnectable = isConnectable;
    }
}
