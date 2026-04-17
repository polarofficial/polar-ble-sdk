package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating that requested service is not found from gatt
 */
class BleServiceNotFound : Exception {
    constructor()

    constructor(detailMessage: String?) : super(detailMessage)
}
