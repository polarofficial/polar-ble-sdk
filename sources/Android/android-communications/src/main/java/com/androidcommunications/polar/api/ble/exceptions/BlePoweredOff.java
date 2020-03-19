package com.androidcommunications.polar.api.ble.exceptions;

/**
 * Error indicating that ble chipset is currently off
 */
public class BlePoweredOff extends Exception {
    public BlePoweredOff() {
    }

    public BlePoweredOff(String detailMessage) {
        super(detailMessage);
    }
}
