package com.androidcommunications.polar.api.ble.exceptions;

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
