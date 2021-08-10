package com.polar.androidcommunications.api.ble.exceptions;

/**
 * Error indicating that gatt service does not contain requested characteristic
 */
public class BleCharacteristicNotFound extends Exception {
    public BleCharacteristicNotFound() {
    }

    public BleCharacteristicNotFound(String detailMessage) {
        super(detailMessage);
    }
}
