package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

class PmdFeature(data: ByteArray) {
    @JvmField
    val ecgSupported: Boolean = (data[1].toUInt() and 0x01u) != 0u

    @JvmField
    val ppgSupported: Boolean = (data[1].toUInt() and 0x02u) != 0u

    @JvmField
    val accSupported: Boolean = (data[1].toUInt() and 0x04u) != 0u

    @JvmField
    val ppiSupported: Boolean = (data[1].toUInt() and 0x08u) != 0u

    @JvmField
    val gyroSupported: Boolean = (data[1].toUInt() and 0x20u) != 0u

    @JvmField
    val magnetometerSupported: Boolean = (data[1].toUInt() and 0x40u) != 0u

    @JvmField
    val barometerSupported: Boolean = (data[2].toUInt() and 0x08u) != 0u

    @JvmField
    val locationSupported: Boolean = (data[2].toUInt() and 0x04u) != 0u

    @JvmField
    val sdkModeSupported: Boolean = (data[2].toUInt() and 0x02u) != 0u

}