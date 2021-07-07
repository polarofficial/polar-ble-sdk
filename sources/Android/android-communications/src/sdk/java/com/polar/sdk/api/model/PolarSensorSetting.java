// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model;

import com.polar.androidcommunications.api.ble.model.gatt.client.BlePMDClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PolarSensorSetting {

    public enum SettingType {
        SAMPLE_RATE(0), /*!< sample rate key in hz */
        RESOLUTION(1), /*!< resolution key in bits */
        RANGE(2), /*!< range key*/
        RANGE_MILLIUNIT(3), /*!< range key milliunit. Note Set contains range values from min to max */
        CHANNELS(4); /*!< amount of channels */

        private final int numVal;

        SettingType(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }

    public final Map<SettingType, Set<Integer>> settings;
    private BlePMDClient.PmdMeasurementType type;

    /**
     * Internal Constructor with PmdSetting and Type
     *
     * @param settings available settings
     * @param type     measurement type
     */
    public PolarSensorSetting(Map<BlePMDClient.PmdSetting.PmdSettingType, Set<Integer>> settings,
                              BlePMDClient.PmdMeasurementType type) {
        this.settings = new HashMap<>();
        this.type = type;
        for (Map.Entry<BlePMDClient.PmdSetting.PmdSettingType, Set<Integer>> e : settings.entrySet()) {
            this.settings.put(SettingType.values()[e.getKey().getNumVal()], e.getValue());
        }
    }

    /**
     * Constructor with selected settings
     *
     * @param settings selected
     */
    public PolarSensorSetting(Map<SettingType, Integer> settings) {
        this.settings = new HashMap<>();
        for (Map.Entry<SettingType, Integer> e : settings.entrySet()) {
            this.settings.put(e.getKey(), new HashSet<>(Collections.singletonList(e.getValue())));
        }
    }

    /**
     * Helper to map from PolarSensorSetting to PmdSetting
     *
     * @return PmdSetting
     */
    public BlePMDClient.PmdSetting map2PmdSettings() {
        Map<BlePMDClient.PmdSetting.PmdSettingType, Integer> selected = new HashMap<>();
        for (Map.Entry<SettingType, Set<Integer>> e : settings.entrySet()) {
            selected.put(BlePMDClient.PmdSetting.PmdSettingType.values()[e.getKey().numVal],
                    Collections.max(e.getValue()));
        }
        return new BlePMDClient.PmdSetting(selected);
    }

    /**
     * Helper to get max settings available
     *
     * @return PolarSensorSetting with only max settings available
     */
    public PolarSensorSetting maxSettings() {
        Map<SettingType, Integer> selected = new HashMap<>();
        for (Map.Entry<SettingType, Set<Integer>> e : settings.entrySet()) {
            selected.put(e.getKey(), Collections.max(e.getValue()));
        }
        return new PolarSensorSetting(selected);
    }

    public static class Builder {
        public final Map<SettingType, Integer> selected = new HashMap<>();
        private final PolarSensorSetting sourceSettings;

        private Builder(PolarSensorSetting sourceSettings) {
            this.sourceSettings = sourceSettings;
        }

        public static Builder newBuilder(PolarSensorSetting sourceSettings) {
            return new Builder(sourceSettings);
        }

        public Builder setSampleRate(int rate) {
            selected.put(SettingType.SAMPLE_RATE, rate);
            return this;
        }

        public Builder setResolution(int resolution) {
            selected.put(SettingType.RESOLUTION, resolution);
            return this;
        }

        public Builder setRange(int range) {
            selected.put(SettingType.RANGE, range);
            return this;
        }

        public Builder setRangeMilliunit(int range) {
            selected.put(SettingType.RANGE_MILLIUNIT, range);
            return this;
        }

        public PolarSensorSetting build() {
            if (!selected.containsKey(SettingType.RESOLUTION) &&
                    sourceSettings.settings.containsKey(SettingType.RESOLUTION)) {
                selected.put(SettingType.RESOLUTION,
                        Collections.max(Objects.requireNonNull(sourceSettings.settings.get(SettingType.RESOLUTION))));
            }
            if (!selected.containsKey(SettingType.CHANNELS) &&
                    sourceSettings.settings.containsKey(SettingType.CHANNELS)) {
                selected.put(SettingType.CHANNELS,
                        Collections.max(Objects.requireNonNull(sourceSettings.settings.get(SettingType.CHANNELS))));
            }
            return new PolarSensorSetting(selected);
        }
    }
}
