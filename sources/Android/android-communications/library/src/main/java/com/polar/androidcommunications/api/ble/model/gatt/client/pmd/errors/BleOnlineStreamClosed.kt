package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.errors

/**
 * Error indicating that online stream has been closed by the device
 */
class BleOnlineStreamClosed(detailMessage: String) : Exception(detailMessage)