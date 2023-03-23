package com.polar.androidcommunications.api.ble.exceptions

sealed class SecurityError(detailMessage: String) : Exception(detailMessage) {
    class SecurityStrategyUnknown(message: String) : SecurityError(message)
}
