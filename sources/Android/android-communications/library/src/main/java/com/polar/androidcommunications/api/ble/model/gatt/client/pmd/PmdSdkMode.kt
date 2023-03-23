package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

enum class PmdSdkMode {
    ENABLED,
    DISABLED;

    companion object {
        fun fromResponse(data: Byte): PmdSdkMode {
            return if (data != 0.toByte()) {
                ENABLED
            } else {
                DISABLED
            }
        }
    }
}