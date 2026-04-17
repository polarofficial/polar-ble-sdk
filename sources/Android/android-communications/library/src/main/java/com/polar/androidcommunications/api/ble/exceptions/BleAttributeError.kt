package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating that requested attribute operation failed with error code
 */
class BleAttributeError(message: String, val error: Int) :
    Exception("$message failed with error: $error")
