package com.polar.androidcommunications.api.ble.exceptions

class BleStartScanError(message: String, val error: Int) : Exception("$message failed with error: $error")