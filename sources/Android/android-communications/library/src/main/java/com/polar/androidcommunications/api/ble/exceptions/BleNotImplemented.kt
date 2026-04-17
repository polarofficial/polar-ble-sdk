package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating that requested operation is not implemented
 */
class BleNotImplemented : Exception {
    constructor()

    constructor(detailMessage: String?) : super(detailMessage)
}
