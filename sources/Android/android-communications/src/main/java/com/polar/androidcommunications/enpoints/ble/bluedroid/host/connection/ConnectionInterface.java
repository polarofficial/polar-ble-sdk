package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection;

import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl;

public interface ConnectionInterface {
    void connectDevice(BDDeviceSessionImpl session);

    void disconnectDevice(BDDeviceSessionImpl session);

    void cancelDeviceConnection(BDDeviceSessionImpl session);

    boolean isPowered();
}
