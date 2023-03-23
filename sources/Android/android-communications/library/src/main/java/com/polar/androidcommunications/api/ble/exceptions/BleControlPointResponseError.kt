package com.polar.androidcommunications.api.ble.exceptions

/**
 * Error indicating the control point response has a problem
 */
class BleControlPointResponseError(
    message: String,
) : Exception(message)