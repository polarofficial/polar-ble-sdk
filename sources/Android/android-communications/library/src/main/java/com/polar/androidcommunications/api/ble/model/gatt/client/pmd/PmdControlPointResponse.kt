package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import java.io.ByteArrayOutputStream

class PmdControlPointResponse(data: ByteArray) {
    val responseCode: Byte = data[0]
    val opCode: PmdControlPointCommand = PmdControlPointCommand.values()[data[1].toInt()]
    val measurementType: Byte = data[2]
    val status: PmdControlPointResponseCode = PmdControlPointResponseCode.values()[data[3].toInt()]
    val more: Boolean
    val parameters = ByteArrayOutputStream()

    enum class PmdControlPointResponseCode(val numVal: Int) {
        SUCCESS(0),
        ERROR_INVALID_OP_CODE(1),
        ERROR_INVALID_MEASUREMENT_TYPE(2),
        ERROR_NOT_SUPPORTED(3),
        ERROR_INVALID_LENGTH(4),
        ERROR_INVALID_PARAMETER(5),
        ERROR_ALREADY_IN_STATE(6),
        ERROR_INVALID_RESOLUTION(7),
        ERROR_INVALID_SAMPLE_RATE(8),
        ERROR_INVALID_RANGE(9),
        ERROR_INVALID_MTU(10),
        ERROR_INVALID_NUMBER_OF_CHANNELS(11),
        ERROR_INVALID_STATE(12),
        ERROR_DEVICE_IN_CHARGER(13);
    }

    init {
        if (status == PmdControlPointResponseCode.SUCCESS) {
            more = data.size > 4 && data[4] != 0.toByte()
            if (data.size > 5) {
                parameters.write(data, 5, data.size - 5)
            }
        } else {
            more = false
        }
    }
}