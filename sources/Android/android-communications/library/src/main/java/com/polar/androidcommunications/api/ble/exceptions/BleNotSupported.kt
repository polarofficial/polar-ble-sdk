package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating that requested operation is not supported
 */
class BleNotSupported : Exception {
    constructor()

    constructor(detailMessage: String?) : super(detailMessage)
}
