package com.polar.sdk.api.model

import com.polar.sdk.api.errors.PolarInvalidSensorSettingsError
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.Assert.assertThrows

class PolarSensorSettingTest {

    @Test
    fun `constructor should store valid settings`() {
        val settings = mapOf(
            PolarSensorSetting.SettingType.SAMPLE_RATE to 10,
            PolarSensorSetting.SettingType.RESOLUTION to 16
        )

        val polarSettings = PolarSensorSetting(settings)

        assertEquals(setOf(10), polarSettings.settings[PolarSensorSetting.SettingType.SAMPLE_RATE])
        assertEquals(setOf(16), polarSettings.settings[PolarSensorSetting.SettingType.RESOLUTION])
    }

    @Test
    fun `constructor should throw on zero value`() {
        assertThrows(PolarInvalidSensorSettingsError::class.java) {
            PolarSensorSetting(
                mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 0)
            )
        }
    }

    @Test
    fun `constructor should throw on negative value`() {
        assertThrows(PolarInvalidSensorSettingsError::class.java) {
            PolarSensorSetting(
                mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to -1)
            )
        }
    }

    @Test
    fun `internal constructor should throw on empty values`() {
        assertThrows(PolarInvalidSensorSettingsError::class.java) {
            PolarSensorSetting(
                listOf(PolarSensorSetting.SettingType.SAMPLE_RATE to emptySet())
            )
        }
    }

    @Test
    fun `internal constructor should throw on zero value`() {
        assertThrows(PolarInvalidSensorSettingsError::class.java) {
            PolarSensorSetting(
                listOf(PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(0))
            )
        }
    }
}