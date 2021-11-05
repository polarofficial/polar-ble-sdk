package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating the device is not supporting BLE
 */
class BleNotAvailableInDevice(message: String) : Exception(message)