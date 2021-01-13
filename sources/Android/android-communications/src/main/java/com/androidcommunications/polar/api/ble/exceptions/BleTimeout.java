package com.androidcommunications.polar.api.ble.exceptions;

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
