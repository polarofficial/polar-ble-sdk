package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

interface ScannerInterface {
    /**
     * connection handler exited connecting state, and scanning can be resumed if needed
     */
    fun connectionHandlerResumeScanning()

    /**
     * connection handler has requested to stop scanning, while there is connection attempt started
     */
    fun connectionHandlerRequestStopScanning()
}
