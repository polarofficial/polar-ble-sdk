package com.polar.sdk.api.model

object PolarDeviceUuid {
    private const val POLAR_UUID_PREFIX = "0e030000-0084-0000-0000-0000"
    private const val REQUIRED_DEVICE_ID_LENGTH = 8

    fun fromDeviceId(deviceId: String): String {
        require(deviceId.length == REQUIRED_DEVICE_ID_LENGTH) {
            "deviceId must be $REQUIRED_DEVICE_ID_LENGTH characters long, was: ${deviceId.length}"
        }
        return POLAR_UUID_PREFIX + deviceId
    }
}