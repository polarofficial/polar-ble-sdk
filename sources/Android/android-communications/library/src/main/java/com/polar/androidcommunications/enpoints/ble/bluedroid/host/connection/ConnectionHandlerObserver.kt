package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl

interface ConnectionHandlerObserver {
    fun deviceSessionStateChanged(session: BDDeviceSessionImpl)

    fun deviceConnected(session: BDDeviceSessionImpl) // explicit connected event

    fun deviceDisconnected(session: BDDeviceSessionImpl) // explicit disconnected event

    fun deviceConnectionCancelled(session: BDDeviceSessionImpl) // explicit pending connection cancelled event
}
