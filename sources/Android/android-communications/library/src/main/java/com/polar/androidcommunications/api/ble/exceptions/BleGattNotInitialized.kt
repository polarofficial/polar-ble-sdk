package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating gatt is not currently initialized
 */
class BleGattNotInitialized : Exception {
    constructor()

    constructor(message: String?) : super(message)
}
