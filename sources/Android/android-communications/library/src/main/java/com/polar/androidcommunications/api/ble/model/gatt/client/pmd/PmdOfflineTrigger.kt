package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.BleControlPointResponseError

internal enum class PmdOfflineRecTriggerMode(val value: UByte) {
    TRIGGER_DISABLE(0u),
    TRIGGER_SYSTEM_START(1u),
    TRIGGER_EXERCISE_START(2u);

    companion object {
        fun parseFromResponse(byte: Byte): PmdOfflineRecTriggerMode {
            return values().first { it.value == byte.toUByte() }
        }
    }
}

internal enum class PmdOfflineRecTriggerStatus(val value: UByte) {
    TRIGGER_DISABLED(0u),
    TRIGGER_ENABLED(1u);

    companion object {
        fun parseFromResponse(byte: Byte): PmdOfflineRecTriggerStatus {
            return try {
                values().first { it.value == byte.toUByte() }
            } catch (e: NoSuchElementException) {
                throw BleControlPointResponseError("PmdOfflineTriggerStatus parsing failed no matching status for byte ${"0x%x".format(byte)}")
            }
        }
    }
}

internal data class PmdOfflineTrigger(
    val triggerMode: PmdOfflineRecTriggerMode,
    val triggers: Map<PmdMeasurementType, Pair<PmdOfflineRecTriggerStatus, PmdSetting?>>
) {
    companion object {
        private const val TAG = "PmdOfflineTrigger"
        private const val TRIGGER_MODE_INDEX = 0
        private const val TRIGGER_MODE_FIELD_LENGTH = 1
        private const val TRIGGER_STATUS_FIELD_LENGTH = 1
        private const val TRIGGER_MEASUREMENT_TYPE_FIELD_LENGTH = 1
        private const val TRIGGER_MEASUREMENT_SETTINGS_SIZE_FIELD_LENGTH = 1

        fun parseFromResponse(bytes: ByteArray): PmdOfflineTrigger {
            BleLogger.d(TAG, "parse offline trigger from response ")
            var offset = TRIGGER_MODE_INDEX
            val triggerMode = PmdOfflineRecTriggerMode.parseFromResponse(bytes[TRIGGER_MODE_INDEX])
            offset += TRIGGER_MODE_FIELD_LENGTH

            val triggers: MutableMap<PmdMeasurementType, Pair<PmdOfflineRecTriggerStatus, PmdSetting?>> = mutableMapOf()
            while (offset < bytes.size) {
                val triggerStatus = PmdOfflineRecTriggerStatus.parseFromResponse(bytes[offset])
                offset += TRIGGER_STATUS_FIELD_LENGTH
                val triggerMeasurementType = PmdMeasurementType.fromId(bytes[offset])
                offset += TRIGGER_MEASUREMENT_TYPE_FIELD_LENGTH
                if (triggerStatus == PmdOfflineRecTriggerStatus.TRIGGER_ENABLED) {
                    val triggerSettingsLength = bytes[offset]
                    offset += TRIGGER_MEASUREMENT_SETTINGS_SIZE_FIELD_LENGTH
                    val settingBytes = bytes.sliceArray(offset until (offset + triggerSettingsLength))
                    val pmdSetting = if (settingBytes.isNotEmpty()) {
                        PmdSetting(settingBytes)
                    } else {
                        null
                    }
                    offset += triggerSettingsLength
                    triggers[triggerMeasurementType] = Pair(triggerStatus, pmdSetting)
                } else {
                    triggers[triggerMeasurementType] = Pair(triggerStatus, null)
                }
            }
            return PmdOfflineTrigger(triggerMode = triggerMode, triggers = triggers)
        }
    }
}
