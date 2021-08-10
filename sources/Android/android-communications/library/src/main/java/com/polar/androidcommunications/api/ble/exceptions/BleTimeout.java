package com.polar.androidcommunications.api.ble.exceptions;

/**
 * Error indicating requested operation timed out
 */
public class BleTimeout extends Exception {
    public BleTimeout() {
    }

    public BleTimeout(String message) {
        super(message);
    }
}
