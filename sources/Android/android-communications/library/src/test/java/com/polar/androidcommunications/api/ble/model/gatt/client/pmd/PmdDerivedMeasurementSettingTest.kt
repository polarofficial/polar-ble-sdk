package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting.PmdSettingType
import org.junit.Assert
import org.junit.Test

class PmdDerivedMeasurementSettingTest {

    /**
     * Verifies that a Get Derived Measurement Settings Group response payload is parsed correctly.
     *
     * Byte sequence:
     *  0C 01 00          – GROUP_ID (12), count 1, value 0
     *  0B 01 E8 03 00 00 – TIME_WINDOW (11), count 1, value 1000 ms
     *  07 03 00 01 02    – METHOD (7), count 3, modes 0 (BASIC), 1 (MINIMAL_STATS), 2 (DETAILED_STATS)
     *  08 01 02          – SOURCE_TYPE (8), count 1, value 2 (ACC)
     *  09 01 32 00       – SOURCE_RATE (9), count 1, value 50 Hz
     */
    @Test
    fun testDerivedMeasurementSettingsGroupParsing() {
        val bytes = byteArrayOf(
            // GROUP_ID = 0
            0x0C, 0x01, 0x00,
            // TIME_WINDOW = 1000 ms (little-endian 4 bytes)
            0x0B, 0x01, 0xE8.toByte(), 0x03, 0x00, 0x00,
            // METHOD, count=3, modes 0, 1, 2
            0x07, 0x03, 0x00, 0x01, 0x02,
            // SOURCE_TYPE = 2 (ACC)
            0x08, 0x01, 0x02,
            // SOURCE_RATE = 50 Hz
            0x09, 0x01, 0x32, 0x00
        )

        val setting = PmdSetting(bytes)

        // Group ID
        Assert.assertTrue(setting.settings[PmdSettingType.DERIVED_MEASUREMENT_SETTINGS_GROUP_ID]!!.contains(0))
        Assert.assertEquals(1, setting.settings[PmdSettingType.DERIVED_MEASUREMENT_SETTINGS_GROUP_ID]!!.size)

        // Time window
        Assert.assertTrue(setting.settings[PmdSettingType.DERIVED_MEASUREMENT_TIME_WINDOW]!!.contains(1000))
        Assert.assertEquals(1, setting.settings[PmdSettingType.DERIVED_MEASUREMENT_TIME_WINDOW]!!.size)

        // Methods — device advertises all 3 preset modes
        val methods = setting.settings[PmdSettingType.DERIVED_MEASUREMENT_METHOD]!!
        Assert.assertEquals(3, methods.size)
        Assert.assertTrue(methods.containsAll(listOf(0, 1, 2)))

        // Source type
        Assert.assertTrue(setting.settings[PmdSettingType.SOURCE_MEASUREMENT_TYPE]!!.contains(2))
        Assert.assertEquals(1, setting.settings[PmdSettingType.SOURCE_MEASUREMENT_TYPE]!!.size)

        // Source rate
        Assert.assertTrue(setting.settings[PmdSettingType.SOURCE_MEASUREMENT_SAMPLE_RATE]!!.contains(50))
        Assert.assertEquals(1, setting.settings[PmdSettingType.SOURCE_MEASUREMENT_SAMPLE_RATE]!!.size)
    }

    /**
     * Verifies that a PmdSetting built from selected derived measurement values serializes
     * to the expected byte sequence for a Request Measurement Start command.
     *
     * With the spec-aligned single-mode protocol a single mode ID is sent (count=1).
     * Expected bytes for DETAILED_STATS (mode 2):
     *  0C 01 22          – GROUP_ID
     *  0B 01 E8 03 00 00 – TIME_WINDOW = 1000 ms
     *  07 01 02          – METHOD, count=1, mode=2 (DETAILED_STATS)
     *  08 01 02          – SOURCE_TYPE = 2
     *  09 01 32 00       – SOURCE_RATE = 50
     */
    @Test
    fun testDerivedMeasurementSettingsSerialization() {
        val selected: MutableMap<PmdSettingType, Int> = mutableMapOf(
            PmdSettingType.DERIVED_MEASUREMENT_SETTINGS_GROUP_ID to 0x22,
            PmdSettingType.DERIVED_MEASUREMENT_TIME_WINDOW to 1000,
            PmdSettingType.DERIVED_MEASUREMENT_METHOD to 4,   // bitmask: bit 2 set = method ID 2 (DETAILED_STATS)
            PmdSettingType.SOURCE_MEASUREMENT_TYPE to 2,
            PmdSettingType.SOURCE_MEASUREMENT_SAMPLE_RATE to 50
        )

        val pmdSetting = PmdSetting(selected)
        val serialized = pmdSetting.serializeSelected()

        // Parse the serialized bytes back to verify round-trip
        val parsed = PmdSetting(serialized)

        Assert.assertTrue(parsed.settings[PmdSettingType.DERIVED_MEASUREMENT_SETTINGS_GROUP_ID]!!.contains(0x22))
        Assert.assertTrue(parsed.settings[PmdSettingType.DERIVED_MEASUREMENT_TIME_WINDOW]!!.contains(1000))
        Assert.assertTrue(parsed.settings[PmdSettingType.SOURCE_MEASUREMENT_TYPE]!!.contains(2))
        Assert.assertTrue(parsed.settings[PmdSettingType.SOURCE_MEASUREMENT_SAMPLE_RATE]!!.contains(50))

        // Single mode ID on the wire → parsed back as a set with one element
        val methods = parsed.settings[PmdSettingType.DERIVED_MEASUREMENT_METHOD]!!
        Assert.assertEquals(1, methods.size)
        Assert.assertTrue(methods.contains(2))
    }

    /**
     * Verifies that FACTOR and SOURCE_MEASUREMENT_RANGE are not serialized in the request
     * (they are response-only fields).
     */
    @Test
    fun testResponseOnlyFieldsNotSerialized() {
        val selected: MutableMap<PmdSettingType, Int> = mutableMapOf(
            PmdSettingType.DERIVED_MEASUREMENT_SETTINGS_GROUP_ID to 0x22,
            PmdSettingType.DERIVED_MEASUREMENT_TIME_WINDOW to 1000,
            PmdSettingType.DERIVED_MEASUREMENT_METHOD to 1,  // method 0 only
            PmdSettingType.SOURCE_MEASUREMENT_TYPE to 2,
            PmdSettingType.SOURCE_MEASUREMENT_SAMPLE_RATE to 50,
            PmdSettingType.FACTOR to java.lang.Float.floatToIntBits(1.0f),
            PmdSettingType.SOURCE_MEASUREMENT_RANGE to 8
        )

        val pmdSetting = PmdSetting(selected)
        val serialized = pmdSetting.serializeSelected()
        val parsed = PmdSetting(serialized)

        // FACTOR and SOURCE_MEASUREMENT_RANGE must not appear in serialized output
        Assert.assertNull(parsed.settings[PmdSettingType.FACTOR])
        Assert.assertNull(parsed.settings[PmdSettingType.SOURCE_MEASUREMENT_RANGE])
        // Other fields must still be present
        Assert.assertNotNull(parsed.settings[PmdSettingType.DERIVED_MEASUREMENT_SETTINGS_GROUP_ID])
        Assert.assertNotNull(parsed.settings[PmdSettingType.DERIVED_MEASUREMENT_TIME_WINDOW])
    }

    /**
     * Verifies time window serialization for large values like one full day (86400000 ms).
     */
    @Test
    fun testLargeTimeWindowSerialization() {
        val oneDayMs = 86_400_000  // 1 day in milliseconds

        val selected: MutableMap<PmdSettingType, Int> = mutableMapOf(
            PmdSettingType.DERIVED_MEASUREMENT_SETTINGS_GROUP_ID to 0x01,
            PmdSettingType.DERIVED_MEASUREMENT_TIME_WINDOW to oneDayMs,
            PmdSettingType.DERIVED_MEASUREMENT_METHOD to 1,
            PmdSettingType.SOURCE_MEASUREMENT_TYPE to 2,
            PmdSettingType.SOURCE_MEASUREMENT_SAMPLE_RATE to 50
        )

        val pmdSetting = PmdSetting(selected)
        val serialized = pmdSetting.serializeSelected()
        val parsed = PmdSetting(serialized)

        Assert.assertTrue(parsed.settings[PmdSettingType.DERIVED_MEASUREMENT_TIME_WINDOW]!!.contains(oneDayMs))
    }
}

