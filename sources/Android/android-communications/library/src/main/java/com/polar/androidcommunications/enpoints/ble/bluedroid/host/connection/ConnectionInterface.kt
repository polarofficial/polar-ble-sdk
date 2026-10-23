package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl

interface ConnectionInterface {
    fun connectDevice(session: BDDeviceSessionImpl?)

    /**
     * Issue a background connection request (connectGatt with autoConnect = true). The Bluetooth
     * stack keeps the request open and connects when the device becomes reachable, without the app
     * scanning, also while the screen is off or the process is in the background.
     */
    fun connectDeviceInBackground(session: BDDeviceSessionImpl?)

    fun disconnectDevice(session: BDDeviceSessionImpl?)

    fun cancelDeviceConnection(session: BDDeviceSessionImpl?)

    fun setPhy(session: BDDeviceSessionImpl?)

    fun readPhy(session: BDDeviceSessionImpl?)

    val isPowered: Boolean

    fun startServiceDiscovery(session: BDDeviceSessionImpl?): Boolean

    fun setMtu(session: BDDeviceSessionImpl?): Boolean
}
