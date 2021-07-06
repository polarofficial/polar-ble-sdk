package com.polar.androidcommunications.api.ble.exceptions;

/**
 * Error indicating that requested service is not found from gatt
 */
public class BleServiceNotFound extends Exception {
    public BleServiceNotFound() {
    }

    public BleServiceNotFound(String detailMessage) {
        super(detailMessage);
    }
}
