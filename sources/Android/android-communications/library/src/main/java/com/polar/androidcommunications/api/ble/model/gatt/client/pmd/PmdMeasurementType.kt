package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

enum class PmdMeasurementType(val numVal: Int) {
    ECG(0),
    PPG(1),
    ACC(2),
    PPI(3),
    GYRO(5),
    MAGNETOMETER(6),
    SDK_MODE(9),
    LOCATION(10),
    PRESSURE(11),
    UNKNOWN_TYPE(0xff);

    companion object {
        @JvmStatic
        fun fromId(id: Byte): PmdMeasurementType {
            for (type in values()) {
                if (type.numVal == id.toInt()) {
                    return type
                }
            }
            return UNKNOWN_TYPE
        }
    }
}