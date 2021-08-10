package com.polar.androidcommunications.api.ble.exceptions;

public class BleStartScanError extends Exception {
    private final int error;

    public BleStartScanError(String message, int error) {
        super(message + " failed with error: " + error);
        this.error = error;
    }

    public int getError() {
        return error;
    }
}
