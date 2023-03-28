package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

enum class PmdControlPointCommandClientToService(val code: Int) {
    NULL_ITEM(0),
    GET_MEASUREMENT_SETTINGS(1),
    REQUEST_MEASUREMENT_START(2),
    STOP_MEASUREMENT(3),
    GET_SDK_MODE_MEASUREMENT_SETTINGS(4),
    GET_MEASUREMENT_STATUS(5),
    GET_SDK_MODE_STATUS(6),
    GET_OFFLINE_RECORDING_TRIGGER_STATUS(7),
    SET_OFFLINE_RECORDING_TRIGGER_MODE(8),
    SET_OFFLINE_RECORDING_TRIGGER_SETTINGS(9);
}

enum class PmdControlPointCommandServiceToClient(val code: Int) {
    ONLINE_MEASUREMENT_STOPPED(1);

    companion object {
        fun fromByte(cpByte: Byte): PmdControlPointCommandServiceToClient? {
            val byteValue = cpByte.toInt() and 0xFF
            return PmdControlPointCommandServiceToClient.values().firstOrNull { it.code == byteValue }
        }
    }
}