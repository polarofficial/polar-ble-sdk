package com.polar.sdk.api.model

data class LedConfig(
    val sdkModeLedEnabled: Boolean = true,
    val ppiModeLedEnabled: Boolean = true
) {
    companion object {
        const val LED_CONFIG_FILENAME = "/LEDCFG.BIN"
        const val LED_ANIMATION_DISABLE_BYTE: Byte = 0x00
        const val LED_ANIMATION_ENABLE_BYTE: Byte = 0x01
    }
}