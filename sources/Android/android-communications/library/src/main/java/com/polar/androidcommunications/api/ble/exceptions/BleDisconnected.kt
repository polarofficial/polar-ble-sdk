package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating that transport layer received disconnected event
 */
class BleDisconnected : Exception {
    constructor()
    constructor(detailMessage: String) : super(detailMessage)
}