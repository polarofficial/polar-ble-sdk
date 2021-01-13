package com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection;

import com.androidcommunications.polar.enpoints.ble.bluedroid.host.BDDeviceSessionImpl;

public interface ConnectionHandlerObserver {
    void deviceSessionStateChanged(BDDeviceSessionImpl session);

    void deviceConnected(BDDeviceSessionImpl session); // explicit connected event

    void deviceDisconnected(BDDeviceSessionImpl session); // explicit disconnected event

    void deviceConnectionCancelled(BDDeviceSessionImpl session); // explicit pending connection cancelled event
}
