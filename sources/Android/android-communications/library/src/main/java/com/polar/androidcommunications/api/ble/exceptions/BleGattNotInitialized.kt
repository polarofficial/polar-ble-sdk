package com.polar.androidcommunications.api.ble.exceptions;

/**
 * Error indicating gatt is not currently initialized
 */
public class BleGattNotInitialized extends Exception {
    public BleGattNotInitialized() {
    }

    public BleGattNotInitialized(String message) {
        super(message);
    }
}
