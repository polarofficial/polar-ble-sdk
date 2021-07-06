package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection;

public interface ScannerInterface {
    /**
     * connection handler exited connecting state, and scanning can be resumed if needed
     */
    void connectionHandlerResumeScanning();

    /**
     * connection handler has requested to stop scanning, while there is connection attempt started
     */
    void connectionHandlerRequestStopScanning();
}
