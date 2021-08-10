package com.polar.androidcommunications.api.ble.model.gatt.client;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BlePmdClientPmdSettingsTest {

    @Test
    public void testPmdSettingsWithRange() {
        //Arrange
        byte[] bytes = {(byte) 0x00, (byte) 0x01, (byte) 0x34, (byte) 0x00, (byte) 0x01, (byte) 0x01, (byte) 0x10, (byte) 0x00, (byte) 0x02, (byte) 0x04, (byte) 0xF5, (byte) 0x00, (byte) 0xF4, (byte) 0x01, (byte) 0xE8, (byte) 0x03, (byte) 0xD0, (byte) 0x07, (byte) 0x04, (byte) 0x01, (byte) 0x03};
        // Parameters
        // Setting Type : 00 (Sample Rate)
        // array_length : 01
        // array of settings values: 34 00 (52Hz)
        int sampleRate = 52;
        //Setting Type : 01 (Resolution)
        // array_length : 01
        // array of settings values: 10 00 (16)
        int resolution = 16;
        // Setting Type : 02 (Range)
        // array_length : 04
        // array of settings values: F5 00 (245)
        int range1 = 245;
        // array of settings values: F4 01 (500)
        int range2 = 500;
        // array of settings values: E8 03 (1000)
        int range3 = 1000;
        // array of settings values: D0 07 (2000)
        int range4 = 2000;
        // Setting Type : 04 (Channels)
        // array_length : 01
        // array of settings values: 03 (3 Channels)
        int channels = 3;
        int numberOfSettings = 4;

        //Act
        BlePMDClient.PmdSetting pmdSetting = new BlePMDClient.PmdSetting(bytes);

        // Assert
        Assert.assertEquals(numberOfSettings, pmdSetting.settings.size());

        assertEquals(sampleRate, (int) pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.SAMPLE_RATE).iterator().next());
        assertEquals(1, pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.SAMPLE_RATE).size());

        assertEquals(resolution, (int) pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RESOLUTION).iterator().next());
        assertEquals(1, pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RESOLUTION).size());

        assertTrue(pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE).contains(range1));
        assertTrue(pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE).contains(range2));
        assertTrue(pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE).contains(range3));
        assertTrue(pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE).contains(range4));
        assertEquals(4, pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE).size());

        assertEquals(channels, (int) pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.CHANNELS).iterator().next());
        assertEquals(1, pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.CHANNELS).size());

        assertNull(pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE_MILLIUNIT));
        assertNull(pmdSetting.settings.get(BlePMDClient.PmdSetting.PmdSettingType.FACTOR));
    }

    @Test
    public void testPmdSettingWithRangeMilliUnit() {
        //Arrange
        byte[] bytes = new byte[]{(byte) BlePMDClient.PmdSetting.PmdSettingType.RANGE_MILLIUNIT.getNumVal(),
                (byte) 0x02, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) BlePMDClient.PmdSetting.PmdSettingType.RESOLUTION.getNumVal(), (byte) 0x01, (byte) 0x0E, 0x00};
        // Parameters
        // Setting Type : 03 (Range milli unit)
        // array_length : 02
        // array of settings values: FF FF FF FF(52Hz)
        // array of settings values: FF 00 00 00(52Hz)
        // Setting Type : 01 (Resolution)
        // array_length : 01
        // array of settings values: 0E 00 (16)
        int resolution = 14;
        int numberOfSettings = 2;

        // Act
        BlePMDClient.PmdSetting settings = new BlePMDClient.PmdSetting(bytes);

        // Assert
        Assert.assertEquals(numberOfSettings, settings.settings.size());
        Assert.assertTrue(settings.settings.containsKey(BlePMDClient.PmdSetting.PmdSettingType.RANGE_MILLIUNIT));
        Assert.assertEquals(2, Objects.requireNonNull(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE_MILLIUNIT)).size());
        Assert.assertTrue(Objects.requireNonNull(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE_MILLIUNIT)).contains(-1));
        Assert.assertTrue(Objects.requireNonNull(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE_MILLIUNIT)).contains(0xff));
        Assert.assertTrue(settings.settings.containsKey(BlePMDClient.PmdSetting.PmdSettingType.RESOLUTION));

        Assert.assertEquals(1, Objects.requireNonNull(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RESOLUTION)).size());
        Assert.assertTrue(Objects.requireNonNull(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RESOLUTION)).contains(resolution));
    }

    @Test
    public void testPmdSelectedSerialization() {
        //Arrange
        Map<BlePMDClient.PmdSetting.PmdSettingType, Integer> selected = new HashMap<>();
        int sampleRate = 0xFFFF;
        selected.put(BlePMDClient.PmdSetting.PmdSettingType.SAMPLE_RATE, sampleRate);
        int resolution = 0;
        selected.put(BlePMDClient.PmdSetting.PmdSettingType.RESOLUTION, resolution);
        int range = 15;
        selected.put(BlePMDClient.PmdSetting.PmdSettingType.RANGE, range);
        int rangeMilliUnit = Integer.MAX_VALUE;
        selected.put(BlePMDClient.PmdSetting.PmdSettingType.RANGE_MILLIUNIT, rangeMilliUnit);
        int channels = 4;
        selected.put(BlePMDClient.PmdSetting.PmdSettingType.CHANNELS, channels);
        int factor = 15;
        selected.put(BlePMDClient.PmdSetting.PmdSettingType.FACTOR, factor);
        int numberOfSettings = 5;

        //Act
        BlePMDClient.PmdSetting settingsFromSelected = new BlePMDClient.PmdSetting(selected);
        byte[] serializedSelected = settingsFromSelected.serializeSelected();
        BlePMDClient.PmdSetting settings = new BlePMDClient.PmdSetting(serializedSelected);

        //Assert
        Assert.assertEquals(numberOfSettings, settings.settings.size());
        assertTrue(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.SAMPLE_RATE).contains(sampleRate));
        Assert.assertEquals(1, Objects.requireNonNull(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.SAMPLE_RATE)).size());

        assertTrue(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RESOLUTION).contains(resolution));
        assertTrue(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE).contains(range));
        assertTrue(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.RANGE_MILLIUNIT).contains(rangeMilliUnit));
        assertTrue(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.CHANNELS).contains(channels));
        assertNull(settings.settings.get(BlePMDClient.PmdSetting.PmdSettingType.FACTOR));
    }
}