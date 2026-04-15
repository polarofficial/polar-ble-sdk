package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating requested operation timed out
 */
class BleTimeout : Exception {
    constructor()

    constructor(message: String?) : super(message)
}
