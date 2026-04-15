package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating that gatt service does not contain requested characteristic
 */
class BleCharacteristicNotFound : Exception {
    constructor()

    constructor(detailMessage: String?) : super(detailMessage)
}
