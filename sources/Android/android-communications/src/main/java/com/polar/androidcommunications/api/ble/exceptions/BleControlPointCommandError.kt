package com.polar.androidcommunications.api.ble.exceptions

import com.polar.androidcommunications.api.ble.model.gatt.client.BlePMDClient

/**
 * Error indicating that requested control point command operation failed with error code
 */
class BleControlPointCommandError(
    message: String,
    val error: BlePMDClient.PmdControlPointResponse.PmdControlPointResponseCode
) :
    Exception("$message failed with error: $error")