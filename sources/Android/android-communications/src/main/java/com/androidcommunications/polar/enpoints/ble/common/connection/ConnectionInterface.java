package com.androidcommunications.polar.enpoints.ble.common.connection;

import com.androidcommunications.polar.enpoints.ble.common.BleDeviceSession2;

public interface ConnectionInterface {
    void connectDevice(BleDeviceSession2 session);
    void disconnectDevice(BleDeviceSession2 session);
    void cancelDeviceConnection(BleDeviceSession2 session);
}
