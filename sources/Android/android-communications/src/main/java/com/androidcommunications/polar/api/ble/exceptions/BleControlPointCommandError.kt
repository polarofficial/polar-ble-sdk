package com.androidcommunications.polar.api.ble.exceptions

import com.androidcommunications.polar.api.ble.model.gatt.client.BlePMDClient

/**
 * Error indicating that requested control point command operation failed with error code
 */
class BleControlPointCommandError(message: String, val error: BlePMDClient.PmdControlPointResponse.PmdControlPointResponseCode) :
    Exception("$message failed with error: $error")