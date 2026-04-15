package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating characteristic notification/indication is not enabled
 */
class BleCharacteristicNotificationNotEnabled : Exception {
    var error: Int = -1

    constructor()

    constructor(message: String?) : super(message)

    constructor(error: Int) {
        this.error = error
    }

    constructor(message: String?, error: Int) : super(message) {
        this.error = error
    }
}
