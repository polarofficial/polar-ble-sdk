package com.androidcommunications.polar.api.ble.exceptions;

public class BleStartScanError extends Exception {
    private int error;

    public BleStartScanError(String message, int error) {
        super(message + " failed with error: " + error);
        this.error = error;
    }

    public int getError() {
        return error;
    }
}
