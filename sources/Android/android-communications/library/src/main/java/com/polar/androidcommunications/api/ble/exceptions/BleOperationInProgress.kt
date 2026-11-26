package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating that the same operation is already in flight and the new request was rejected.
 */
class BleOperationInProgress(message: String?) : Exception(message)
