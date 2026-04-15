package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating that ble chipset is currently off
 */
class BlePoweredOff : Exception {
    constructor()

    constructor(detailMessage: String?) : super(detailMessage)
}
