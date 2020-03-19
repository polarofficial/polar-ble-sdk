package com.androidcommunications.polar.api.ble.exceptions;

/**
 * Error indicating that requested operation is not implemented
 */
public class BleNotImplemented extends Exception {

    public BleNotImplemented() {
    }

    public BleNotImplemented(String detailMessage) {
        super(detailMessage);
    }
}
