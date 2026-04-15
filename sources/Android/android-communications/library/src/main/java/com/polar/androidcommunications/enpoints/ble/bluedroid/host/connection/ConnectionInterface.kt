package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl

interface ConnectionInterface {
    fun connectDevice(session: BDDeviceSessionImpl?)

    fun disconnectDevice(session: BDDeviceSessionImpl?)

    fun cancelDeviceConnection(session: BDDeviceSessionImpl?)

    fun setPhy(session: BDDeviceSessionImpl?)

    fun readPhy(session: BDDeviceSessionImpl?)

    val isPowered: Boolean

    fun startServiceDiscovery(session: BDDeviceSessionImpl?): Boolean

    fun setMtu(session: BDDeviceSessionImpl?): Boolean
}
