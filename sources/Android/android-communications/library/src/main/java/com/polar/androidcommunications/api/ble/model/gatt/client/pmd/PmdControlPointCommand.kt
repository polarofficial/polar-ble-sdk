package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

enum class PmdControlPointCommand(val numVal: Int) {
    NULL_ITEM(0),
    GET_MEASUREMENT_SETTINGS(1),
    REQUEST_MEASUREMENT_START(2),
    STOP_MEASUREMENT(3),
    GET_SDK_MODE_MEASUREMENT_SETTINGS(4);
}