package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting
import com.polar.sdk.api.model.PolarSensorSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolarDataUtilsSettingsTest {
    @Test
    fun mapPolarSettingsToPmdSettings_mapsEveryPublicType() {
        // Arrange
        val settings = PolarSensorSetting(mapOf(
            PolarSensorSetting.SettingType.SAMPLE_RATE to 100,
            PolarSensorSetting.SettingType.RESOLUTION to 16,
            PolarSensorSetting.SettingType.RANGE to 8,
            PolarSensorSetting.SettingType.CHANNELS to 3
        ))
        settings.settings[PolarSensorSetting.SettingType.SAMPLE_RATE] = setOf(25, 100, 50)
        val expected = mapOf(
            PmdSetting.PmdSettingType.SAMPLE_RATE to 100,
            PmdSetting.PmdSettingType.RESOLUTION to 16,
            PmdSetting.PmdSettingType.RANGE to 8,
            PmdSetting.PmdSettingType.CHANNELS to 3
        )

        // Act
        val result = PolarDataUtils.mapPolarSettingsToPmdSettings(settings)

        // Assert
        assertEquals(expected, result.selected)
    }

    @Test
    fun mapPolarSettingsToPmdSettings_acceptsNull() {
        // Arrange
        val settings: PolarSensorSetting? = null

        // Act
        val result = PolarDataUtils.mapPolarSettingsToPmdSettings(settings)

        // Assert
        assertTrue(result.selected.isEmpty())
    }
}
