package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

enum class PmdMeasurementType(val numVal: UByte) {
    ECG(0u),
    PPG(1u),
    ACC(2u),
    PPI(3u),
    GYRO(5u),
    MAGNETOMETER(6u),
    SKIN_TEMP(7u),
    SDK_MODE(9u),
    LOCATION(10u),
    PRESSURE(11u),
    TEMPERATURE(12u),
    OFFLINE_RECORDING(13u),
    OFFLINE_HR(14u),
    OFFLINE_TEMP(15u),
    UNKNOWN_TYPE(0x3fu);

    fun isDataType(): Boolean {
        return when (this) {
            SDK_MODE,
            OFFLINE_RECORDING,
            UNKNOWN_TYPE -> false
            else -> true
        }
    }

    internal companion object {
        private const val MEASUREMENT_TYPE_BIT_MASK: UByte = 0x3Fu

        fun fromId(id: Byte): PmdMeasurementType {
            for (type in values()) {
                if (type.numVal == (id.toUByte() and MEASUREMENT_TYPE_BIT_MASK)) {
                    return type
                }
            }
            return UNKNOWN_TYPE
        }

        fun fromByteArray(data: ByteArray): Set<PmdMeasurementType> {

            val measurementTypes: MutableSet<PmdMeasurementType> = mutableSetOf()
            if ((data[1].toUInt() and 0x01u) != 0u) measurementTypes.add(ECG)
            if ((data[1].toUInt() and 0x02u) != 0u) measurementTypes.add(PPG)
            if ((data[1].toUInt() and 0x04u) != 0u) measurementTypes.add(ACC)
            if ((data[1].toUInt() and 0x08u) != 0u) measurementTypes.add(PPI)
            if ((data[1].toUInt() and 0x20u) != 0u) measurementTypes.add(GYRO)
            if ((data[1].toUInt() and 0x40u) != 0u) measurementTypes.add(MAGNETOMETER)
            if ((data[1].toUInt() and 0x80u) != 0u) measurementTypes.add(SKIN_TEMP)
            if ((data[2].toUInt() and 0x02u) != 0u) measurementTypes.add(SDK_MODE)
            if ((data[2].toUInt() and 0x04u) != 0u) measurementTypes.add(LOCATION)
            if ((data[2].toUInt() and 0x08u) != 0u) measurementTypes.add(PRESSURE)
            if ((data[2].toUInt() and 0x10u) != 0u) measurementTypes.add(TEMPERATURE)
            if ((data[2].toUInt() and 0x20u) != 0u) measurementTypes.add(OFFLINE_RECORDING)
            if ((data[2].toUInt() and 0x40u) != 0u) measurementTypes.add(OFFLINE_HR)
            return measurementTypes
        }
    }
}