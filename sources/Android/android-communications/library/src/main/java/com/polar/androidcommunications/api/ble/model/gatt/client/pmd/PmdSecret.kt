package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.exceptions.SecurityError
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

class PmdSecret(val strategy: SecurityStrategy, val key: ByteArray) {
    constructor(strategy: SecurityStrategy, key: SecretKeySpec) : this(strategy, key.encoded)

    init {
        when (strategy) {
            SecurityStrategy.NONE -> require(key.isEmpty()) { "key shall be empty for ${SecurityStrategy.NONE}, key size was ${key.size}" }
            SecurityStrategy.XOR -> require(key.isNotEmpty()) { "key shall not be empty for ${SecurityStrategy.XOR}, key size was ${key.size}" }
            SecurityStrategy.AES128 -> require(key.size == 16) { "key must be size of 16 bytes for ${SecurityStrategy.AES128}, key size was ${key.size}" }
            SecurityStrategy.AES256 -> require(key.size == 32) { "key must be size of 32 bytes for ${SecurityStrategy.AES256}, key size was ${key.size}" }
        }
    }

    fun serializeToPmdSettings(): ByteArray {
        when (strategy) {
            SecurityStrategy.NONE -> {
                val securitySetting = PmdSetting.PmdSettingType.SECURITY.numVal.toByte()
                val securityStrategyByte = SecurityStrategy.NONE.numVal.toByte()
                val length = 1.toByte()
                return byteArrayOf(securitySetting, length, securityStrategyByte)
            }
            SecurityStrategy.XOR -> {
                val securitySetting = PmdSetting.PmdSettingType.SECURITY.numVal.toByte()
                val securityStrategyByte = SecurityStrategy.XOR.numVal.toByte()
                val keyBytes = key
                val length = 1.toByte()
                return byteArrayOf(securitySetting, length, securityStrategyByte) + keyBytes
            }
            SecurityStrategy.AES128 -> {
                val securitySetting = PmdSetting.PmdSettingType.SECURITY.numVal.toByte()
                val securityStrategyByte = SecurityStrategy.AES128.numVal.toByte()
                val keyBytes = key
                val length = 1.toByte()
                return byteArrayOf(securitySetting, length, securityStrategyByte) + keyBytes
            }
            SecurityStrategy.AES256 -> {
                val securitySetting = PmdSetting.PmdSettingType.SECURITY.numVal.toByte()
                val securityStrategyByte = SecurityStrategy.AES256.numVal.toByte()
                val keyBytes = key
                val length = 1.toByte()
                return byteArrayOf(securitySetting, length, securityStrategyByte) + keyBytes
            }
        }
    }

    fun decryptArray(cipherArray: ByteArray): ByteArray {
        when (this.strategy) {
            SecurityStrategy.AES128 -> {
                val key = SecretKeySpec(this.key, "AES")
                val cipher = Cipher.getInstance("AES_128/ECB/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key)
                return cipher.doFinal(cipherArray)
            }
            SecurityStrategy.AES256 -> {
                val key = SecretKeySpec(this.key, "AES")
                val cipher = Cipher.getInstance("AES_256/ECB/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key)
                return cipher.doFinal(cipherArray)
            }
            SecurityStrategy.XOR -> {
                return cipherArray.map { it xor this.key.first() }.toByteArray()
            }
            SecurityStrategy.NONE -> {
                return cipherArray
            }
        }
    }

    enum class SecurityStrategy(val numVal: UByte) {
        NONE(0x00u),
        XOR(0x01u),
        AES128(0x02u),
        AES256(0x03u);

        companion object {
            fun fromByte(strategyByte: UByte): SecurityStrategy {
                return when {
                    (strategyByte == NONE.numVal) -> NONE
                    (strategyByte == XOR.numVal) -> XOR
                    (strategyByte == AES128.numVal) -> AES128
                    (strategyByte == AES256.numVal) -> AES256
                    else -> throw SecurityError.SecurityStrategyUnknown("Cannot decide security strategy from byte  ${"0x%x".format(strategyByte)}")
                }
            }
        }
    }
}