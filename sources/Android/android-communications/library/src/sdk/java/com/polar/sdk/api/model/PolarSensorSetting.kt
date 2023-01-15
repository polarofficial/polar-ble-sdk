// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import java.util.*

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
         * range key milliunit. Note Set contains range values from min to max
         */
        @Deprecated("This enum is not used anymore to set any settings")
        RANGE_MILLIUNIT(3),

        /**
         * amount of channels
         */
        CHANNELS(4);

    }

    var settings: MutableMap<SettingType, Set<Int>> = mutableMapOf()
        private set

    /**
     * Constructor with selected settings
     *
     * @param settings selected
     */
    constructor(settings: Map<SettingType, Int>) {
        for ((key, value) in settings) {
            this.settings[key] = setOf(value)
        }
    }

    /**
     * Internal Constructor for SDK internal usage.
     *
     * @param setting available settings
     */
    internal constructor(setting: List<Pair<SettingType, Set<Int>>>) {
        settings = setting.toMap().toMutableMap()
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