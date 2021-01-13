package com.androidcommunications.polar.api.ble.exceptions;

/**
 * Error indicating that transport layer received disconnected event
 */
public class BleDisconnected extends Exception {
    public BleDisconnected() {
    }

    public BleDisconnected(String detailMessage) {
        super(detailMessage);
    }
}
