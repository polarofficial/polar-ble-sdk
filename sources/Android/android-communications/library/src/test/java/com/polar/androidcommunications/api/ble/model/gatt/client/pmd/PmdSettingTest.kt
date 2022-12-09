package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting.PmdSettingType
import org.junit.Assert
import org.junit.Test

class PmdSettingTest {
    @Test
    fun testPmdSettingsWithRange() {
        //Arrange
        val bytes = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x34.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0x02.toByte(), 0x04.toByte(),
            0xF5.toByte(), 0x00.toByte(), 0xF4.toByte(), 0x01.toByte(), 0xE8.toByte(), 0x03.toByte(), 0xD0.toByte(), 0x07.toByte(), 0x04.toByte(), 0x01.toByte(),
            0x03.toByte()
        )

        // Parameters
        // Setting Type : 00 (Sample Rate)
        // array_length : 01
        // array of settings values: 34 00 (52Hz)
        val sampleRate = 52
        //Setting Type : 01 (Resolution)
        // array_length : 01
        // array of settings values: 10 00 (16)
        val resolution = 16
        // Setting Type : 02 (Range)
        // array_length : 04
        // array of settings values: F5 00 (245)
        val range1 = 245
        // array of settings values: F4 01 (500)
        val range2 = 500
        // array of settings values: E8 03 (1000)
        val range3 = 1000
        // array of settings values: D0 07 (2000)
        val range4 = 2000
        // Setting Type : 04 (Channels)
        // array_length : 01
        // array of settings values: 03 (3 Channels)
        val channels = 3
        val numberOfSettings = 4

        //Act
        val pmdSetting = PmdSetting(bytes)

        // Assert
        Assert.assertEquals(numberOfSettings, pmdSetting.settings.size)
        Assert.assertEquals(sampleRate, pmdSetting.settings[PmdSettingType.SAMPLE_RATE]!!.iterator().next())
        Assert.assertEquals(1, pmdSetting.settings[PmdSettingType.SAMPLE_RATE]!!.size)
        Assert.assertEquals(resolution, pmdSetting.settings[PmdSettingType.RESOLUTION]!!.iterator().next())
        Assert.assertEquals(1, pmdSetting.settings[PmdSettingType.RESOLUTION]!!.size)
        Assert.assertTrue(pmdSetting.settings[PmdSettingType.RANGE]!!.contains(range1))
        Assert.assertTrue(pmdSetting.settings[PmdSettingType.RANGE]!!.contains(range2))
        Assert.assertTrue(pmdSetting.settings[PmdSettingType.RANGE]!!.contains(range3))
        Assert.assertTrue(pmdSetting.settings[PmdSettingType.RANGE]!!.contains(range4))
        Assert.assertEquals(4, pmdSetting.settings[PmdSettingType.RANGE]!!.size)
        Assert.assertEquals(channels, pmdSetting.settings[PmdSettingType.CHANNELS]!!.iterator().next())
        Assert.assertEquals(1, pmdSetting.settings[PmdSettingType.CHANNELS]!!.size)
        Assert.assertNull(pmdSetting.settings[PmdSettingType.RANGE_MILLIUNIT])
        Assert.assertNull(pmdSetting.settings[PmdSettingType.FACTOR])
    }

    @Test
    fun testPmdSettingWithRangeMilliUnit() {
        //Arrange
        val bytes = byteArrayOf(
            PmdSettingType.RANGE_MILLIUNIT.numVal.toByte(), 0x02.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            PmdSettingType.RESOLUTION.numVal.toByte(), 0x01.toByte(), 0x0E.toByte(), 0x00
        )
        // Parameters
        // Setting Type : 03 (Range milli unit)
        // array_length : 02
        // array of settings values: FF FF FF FF(52Hz)
        // array of settings values: FF 00 00 00(52Hz)
        // Setting Type : 01 (Resolution)
        // array_length : 01
        // array of settings values: 0E 00 (16)
        val resolution = 14
        val numberOfSettings = 2

        // Act
        val settings = PmdSetting(bytes)

        // Assert
        Assert.assertEquals(numberOfSettings, settings.settings.size)
        Assert.assertTrue(settings.settings.containsKey(PmdSettingType.RANGE_MILLIUNIT))
        Assert.assertEquals(2, settings.settings[PmdSettingType.RANGE_MILLIUNIT]?.size)
        Assert.assertTrue(settings.settings[PmdSettingType.RANGE_MILLIUNIT]!!.contains(-1))
        Assert.assertTrue(settings.settings[PmdSettingType.RANGE_MILLIUNIT]!!.contains(0xff))
        Assert.assertTrue(settings.settings.containsKey(PmdSettingType.RESOLUTION))
        Assert.assertEquals(1, settings.settings[PmdSettingType.RESOLUTION]?.size)
        Assert.assertTrue(settings.settings[PmdSettingType.RESOLUTION]!!.contains(resolution))
    }

    @Test
    fun testPmdSelectedSerialization() {
        //Arrange
        val selected: MutableMap<PmdSettingType, Int> = mutableMapOf()
        val sampleRate = 0x7FFF
        selected[PmdSettingType.SAMPLE_RATE] = sampleRate
        val resolution = 0
        selected[PmdSettingType.RESOLUTION] = resolution
        val range = 15
        selected[PmdSettingType.RANGE] = range
        val rangeMilliUnit = Int.MAX_VALUE
        selected[PmdSettingType.RANGE_MILLIUNIT] = rangeMilliUnit
        val channels = 4
        selected[PmdSettingType.CHANNELS] = channels
        val factor = 15
        selected[PmdSettingType.FACTOR] = factor
        val numberOfSettings = 5

        //Act
        val settingsFromSelected = PmdSetting(selected)
        val serializedSelected = settingsFromSelected.serializeSelected()
        val settings = PmdSetting(serializedSelected)

        //Assert
        Assert.assertEquals(numberOfSettings, settings.settings.size)
        Assert.assertTrue(settings.settings[PmdSettingType.SAMPLE_RATE]!!.contains(sampleRate))
        Assert.assertEquals(1, settings.settings[PmdSettingType.SAMPLE_RATE]?.size)
        Assert.assertTrue(settings.settings[PmdSettingType.RESOLUTION]!!.contains(resolution))
        Assert.assertTrue(settings.settings[PmdSettingType.RANGE]!!.contains(range))
        Assert.assertTrue(settings.settings[PmdSettingType.RANGE_MILLIUNIT]!!.contains(rangeMilliUnit))
        Assert.assertTrue(settings.settings[PmdSettingType.CHANNELS]!!.contains(channels))
        Assert.assertNull(settings.settings[PmdSettingType.FACTOR])
    }
}