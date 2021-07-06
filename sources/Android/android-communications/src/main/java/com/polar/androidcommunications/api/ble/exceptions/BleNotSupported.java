package com.polar.androidcommunications.api.ble.exceptions;

/**
 * Error indicating that requested operation is not supported
 */
public class BleNotSupported extends Exception {
    public BleNotSupported() {
    }

    public BleNotSupported(String detailMessage) {
        super(detailMessage);
    }
}
