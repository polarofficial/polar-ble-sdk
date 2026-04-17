package com.polar.androidcommunications.api.ble.exceptions;

/**
 * Error indicating characteristic notification/indication is not enabled
 */
public class BleCharacteristicNotificationNotEnabled extends Exception {
    public int error = -1;

    public BleCharacteristicNotificationNotEnabled() {
    }

    public BleCharacteristicNotificationNotEnabled(String message) {
        super(message);
    }

    public BleCharacteristicNotificationNotEnabled(int error) {
        this.error = error;
    }

    public BleCharacteristicNotificationNotEnabled(String message, int error) {
        super(message);
        this.error = error;
    }
}
