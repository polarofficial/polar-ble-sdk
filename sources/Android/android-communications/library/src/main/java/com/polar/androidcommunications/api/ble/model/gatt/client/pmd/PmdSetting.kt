package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.common.ble.TypeUtils
import java.io.ByteArrayOutputStream
import java.util.*

class PmdSetting {
    enum class PmdSettingType(val numVal: Int) {
        SAMPLE_RATE(0),
        RESOLUTION(1),
        RANGE(2),
        RANGE_MILLIUNIT(3),
        CHANNELS(4),
        FACTOR(5),
        SECURITY(6);
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
            val type = PmdSettingType.values()[data[offset++].toInt()]
            var count = data[offset++].toInt()
            val items: MutableSet<Int> = HashSet()
            while (count-- > 0) {
                val fieldSize = typeToFieldSize(type)
                val item = TypeUtils.convertArrayToSignedInt(data, offset, fieldSize)
                offset += fieldSize
                items.add(item)
            }
            parsedSettings[type] = items
        }
        return parsedSettings
    }

    fun updateSelectedFromStartResponse(data: ByteArray) {
        val settingsFromStartResponse = parsePmdSettingsData(data)
        if (settingsFromStartResponse.containsKey(PmdSettingType.FACTOR)) {
            selected[PmdSettingType.FACTOR] = settingsFromStartResponse[PmdSettingType.FACTOR]!!.iterator().next()
        }
    }

    fun serializeSelected(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        for ((key, value) in selected) {
            if (key == PmdSettingType.FACTOR) {
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