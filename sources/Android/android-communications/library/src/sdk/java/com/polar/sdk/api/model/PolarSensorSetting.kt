// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import com.polar.sdk.api.errors.PolarInvalidSensorSettingsError
import java.util.Collections

class PolarSensorSetting {

    enum class SettingType(val numVal: Int) {
        /**
         * sample rate key in hz
         */
        SAMPLE_RATE(0),

        /**
         * resolution key in bits
         */
        RESOLUTION(1),

        /**
         * range key
         */
        RANGE(2),

        /**
         * amount of channels
         */
        CHANNELS(4);

    }

    enum class SensorLocation(val numVal: Int) {
        SL_UNKNOWN (0),

        /**
         * Distal from core
         */
        SL_DISTAL(1),

        /**
         * Proximal from core
         */
        SL_PROXIMAL(2)
    }

    enum class TemperatureMeasurementType(val numVal: Int) {
        TM_UNKNOWN(0),
        TM_SKIN_TEMPERATURE(1),
        TM_CORE_TEMPERATURE(2)
    }
    var settings: MutableMap<SettingType, Set<Int>> = mutableMapOf()
        private set

    /**
     * Constructor with selected settings
     *
     * @param settings selected
     * @throws PolarInvalidSensorSettingsError if any value is <= 0
     */
    @Throws(PolarInvalidSensorSettingsError::class)
    constructor(settings: Map<SettingType, Int>) {
        for ((key, value) in settings) {
            if (value <= 0) {
                throw PolarInvalidSensorSettingsError("Invalid value $value for $key, must be > 0")
            }
            this.settings[key] = setOf(value)
        }
    }

    /**
     * Internal Constructor for SDK internal usage.
     *
     * @param setting map of setting types to integer values.
     * @throws PolarInvalidSensorSettingsError if any values are empty or <= 0
     */
    @Throws(PolarInvalidSensorSettingsError::class)
    internal constructor(setting: List<Pair<SettingType, Set<Int>>>) {
        settings = setting.toMap().toMutableMap()

        for ((key, values) in settings) {
            if (values.isEmpty()) {
                throw PolarInvalidSensorSettingsError("Missing value for $key")
            }
            val value = values.first()
            if (value <= 0) {
                throw PolarInvalidSensorSettingsError("Invalid value $value for $key, must be > 0")
            }
        }
    }

    /**
     * Helper to get max settings available
     *
     * @return PolarSensorSetting with only max settings available
     */
    fun maxSettings(): PolarSensorSetting {
        val selected: MutableMap<SettingType, Int> = mutableMapOf()
        for ((key, value) in settings) {
            selected[key] = Collections.max(value)
        }
        return PolarSensorSetting(selected)
    }
}