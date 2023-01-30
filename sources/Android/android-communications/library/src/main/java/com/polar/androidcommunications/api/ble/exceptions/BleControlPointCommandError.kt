package com.polar.androidcommunications.api.ble.exceptions

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse

/**
 * Error indicating that requested control point command operation failed with error code
 */
class BleControlPointCommandError(
    message: String,
    val error: PmdControlPointResponse.PmdControlPointResponseCode
) : Exception("$message $error")