package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection;

import androidx.annotation.NonNull;

import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl;

public interface ConnectionHandlerObserver {
    void deviceSessionStateChanged(@NonNull BDDeviceSessionImpl session);

    void deviceConnected(@NonNull BDDeviceSessionImpl session); // explicit connected event

    void deviceDisconnected(@NonNull BDDeviceSessionImpl session); // explicit disconnected event

    void deviceConnectionCancelled(@NonNull BDDeviceSessionImpl session); // explicit pending connection cancelled event
}
