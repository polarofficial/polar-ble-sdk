package com.androidcommunications.polar.enpoints.ble.common.connection;

import com.androidcommunications.polar.enpoints.ble.common.BleDeviceSession2;

public interface ConnectionHandlerObserver {
    void deviceSessionStateChanged(BleDeviceSession2 session);
    void deviceConnected(BleDeviceSession2 session); // explicit connected event
    void deviceDisconnected(BleDeviceSession2 session); // explicit disconnected event
    void deviceConnectionCancelled(BleDeviceSession2 session); // explicit pending connection cancelled event
}
