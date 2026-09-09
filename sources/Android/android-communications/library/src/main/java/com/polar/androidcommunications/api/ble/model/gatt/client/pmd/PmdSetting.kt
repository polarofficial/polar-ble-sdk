package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.common.ble.TypeUtils
import java.io.ByteArrayOutputStream
import java.util.AbstractMap
import java.util.Collections
import java.util.EnumMap
import java.util.Objects
import java.util.TreeMap

class PmdSetting {
    enum class PmdSettingType(val numVal: Int) {
        SAMPLE_RATE(0),
        RESOLUTION(1),
        RANGE(2),
        RANGE_MILLIUNIT(3),
        CHANNELS(4),
        FACTOR(5),
        SECURITY(6),

        /**
         * Derived measurement method bitmask
         * In selected map, stored as bitmask where bit N = method N selected.
         * Multiple method IDs are serialized individually (1 byte each) in the BLE packet.
         */
        DERIVED_MEASUREMENT_METHOD(7),

        /**
         * Source measurement type for derived measurement.
         * Value is the numeric value of the source PmdMeasurementType (e.g. 2 for ACC).
         */
        SOURCE_MEASUREMENT_TYPE(8),

        /**
         * Source measurement sample rate in Hz.
         */
        SOURCE_MEASUREMENT_SAMPLE_RATE(9),

        /**
         * Source measurement range chosen by device — returned in start response only,
         * not sent in Request Measurement Start.
         */
        SOURCE_MEASUREMENT_RANGE(10),

        /**
         * Derived measurement time window in milliseconds.
         * Determines the output cadence, e.g. 1000 ms = 1 Hz output.
         */
        DERIVED_MEASUREMENT_TIME_WINDOW(11),

        /**
         * Derived measurement settings group ID.
         */
        DERIVED_MEASUREMENT_SETTINGS_GROUP_ID(12);
    }

    // available settings
    var settings: EnumMap<PmdSettingType, Set<Int>> = EnumMap(PmdSettingType.values().associateWith { emptySet() })

    // selected by user
    var selected: MutableMap<PmdSettingType, Int> = mutableMapOf()
        private set

    constructor(data: ByteArray) {
        val parsedSettings = parsePmdSettingsData(data)
        validateSettings(parsedSettings)
        settings = parsedSettings
    }

    constructor(selected: Map<PmdSettingType, Int>) {
        validateSelected(selected)
        this.selected = selected.toMutableMap()
    }

    private fun parsePmdSettingsData(data: ByteArray): EnumMap<PmdSettingType, Set<Int>> {
        val parsedSettings = EnumMap<PmdSettingType, Set<Int>>(PmdSettingType::class.java)
        if (data.size <= 1) {
            return parsedSettings
        }
        var offset = 0
        while (offset < data.size) {
            val typeId = data[offset++].toInt() and 0xFF
            val type = PmdSettingType.values().firstOrNull { it.numVal == typeId }
                ?: throw IllegalArgumentException("Unknown PmdSettingType ID: $typeId in BLE settings data")
            var count = data[offset++].toInt()
            val items: MutableSet<Int> = HashSet()
            while (count-- > 0) {
                val fieldSize = typeToFieldSize(type)

                val item: Int? = try {
                    TypeUtils.convertArrayToSignedInt(data, offset, fieldSize)
                } catch (e: Exception) {
                    throw e
                }

                offset += fieldSize
                if (item != null) {
                    items.add(item)
                }
            }
            parsedSettings[type] = items
        }
        return parsedSettings
    }

    fun updateSelectedFromStartResponse(data: ByteArray) {

        val settingsFromStartResponse:
                EnumMap<PmdSettingType, Set<Int>>? = try {
            parsePmdSettingsData(data)
        } catch (e: Exception) {
            throw e
        }

        if (settingsFromStartResponse != null) {
            if (settingsFromStartResponse.containsKey(PmdSettingType.FACTOR)) {
                selected[PmdSettingType.FACTOR] = settingsFromStartResponse?.get(PmdSettingType.FACTOR)!!.iterator().next()
            }
        }
    }

    fun serializeSelected(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        for ((key, value) in selected) {
            // FACTOR and SOURCE_MEASUREMENT_RANGE are response-only; do not include in request
            if (key == PmdSettingType.FACTOR || key == PmdSettingType.SOURCE_MEASUREMENT_RANGE) {
                continue
            }

            // DERIVED_MEASUREMENT_METHOD: value is a bitmask (bit N = method N selected).
            // Expand to individual 1-byte method IDs
            // e.g. bitmask=1 (bit 0) → DOWNSAMPLE id=0 → [type=7][count=1][0x00]
            //      bitmask=3 (bits 0+1) → DOWNSAMPLE+MIN  → [type=7][count=2][0x00][0x01]
            if (key == PmdSettingType.DERIVED_MEASUREMENT_METHOD) {
                val methodIds = (0..15).filter { (value shr it) and 1 == 1 }
                if (methodIds.isEmpty()) continue
                outputStream.write(key.numVal)
                outputStream.write(methodIds.size)
                methodIds.forEach { outputStream.write(it) }
                continue
            }

            outputStream.write(key.numVal)
            outputStream.write(1)

            val fieldSize = Objects.requireNonNull(typeToFieldSize(key))
            for (i in 0 until fieldSize) {
                outputStream.write((value shr i * 8))
            }
        }

        return outputStream.toByteArray()
    }

    fun maxSettings(): PmdSetting {
        val set: MutableMap<PmdSettingType, Int> = TreeMap()
        for ((key, value) in settings) {
            set[key] = Collections.max(value)
        }
        return PmdSetting(set)
    }

    override fun toString(): String {
        val stringBuilder: StringBuilder = StringBuilder("\navailable settings: ")
        for (setting in settings) {
            stringBuilder.append("${setting.key} : ${setting.value} , ")
        }
        stringBuilder.append("\nselected settings: ")
        for (setting in selected) {
            stringBuilder.append("${setting.key} : ${setting.value} , ")
        }
        return stringBuilder.toString()

    }

    companion object {
        private fun typeToFieldSize(type: PmdSettingType): Int {
            return when (type) {
                PmdSettingType.SAMPLE_RATE -> 2
                PmdSettingType.RESOLUTION -> 2
                PmdSettingType.RANGE -> 2
                PmdSettingType.RANGE_MILLIUNIT -> 4
                PmdSettingType.CHANNELS -> 1
                PmdSettingType.FACTOR -> 4
                PmdSettingType.SECURITY -> 16
                PmdSettingType.DERIVED_MEASUREMENT_METHOD -> 1     // 1 byte per method ID
                PmdSettingType.SOURCE_MEASUREMENT_TYPE -> 1
                PmdSettingType.SOURCE_MEASUREMENT_SAMPLE_RATE -> 2
                PmdSettingType.SOURCE_MEASUREMENT_RANGE -> 4  // milliunit range (same width as RANGE_MILLIUNIT)
                PmdSettingType.DERIVED_MEASUREMENT_TIME_WINDOW -> 4
                PmdSettingType.DERIVED_MEASUREMENT_SETTINGS_GROUP_ID -> 1
            }
        }

        private fun validateSettings(settings: Map<PmdSettingType, Set<Int>>) {
            for ((key, value1) in settings) {
                for (value in value1) {
                    val entry: Map.Entry<PmdSettingType, Int> = AbstractMap.SimpleEntry(key, value)
                    validateSetting(entry)
                }
            }
        }

        private fun validateSelected(settings: Map<PmdSettingType, Int>) {
            for (setting in settings.entries) {
                validateSetting(setting)
            }
        }

        private fun validateSetting(setting: Map.Entry<PmdSettingType, Int>) {
            // DERIVED_MEASUREMENT_METHOD stores a bitmask (bits 0-9 correspond to methods 0-9);
            // skip the standard byte-range check since the bitmask may exceed 0xFF.
            if (setting.key == PmdSettingType.DERIVED_MEASUREMENT_METHOD) {
                return
            }
            val fieldSize = typeToFieldSize(setting.key)
            val value = setting.value
            if (fieldSize == 1 && (value < 0x0 || 0xFF < value)) {
                throw RuntimeException("PmdSetting not in valid range. Field size: $fieldSize value: $value")
            }
            if (fieldSize == 2 && (value < 0x0 || 0xFFFF < value)) {
                throw RuntimeException("PmdSetting not in valid range. Field size: $fieldSize value: $value")
            }
            if (fieldSize == 3 && (value < 0x0 || 0xFFFFFF < value)) {
                throw RuntimeException("PmdSetting not in valid range. Field size: $fieldSize value: $value")
            }
        }
    }
}