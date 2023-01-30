package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

internal class PmdActiveMeasurementTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `test offline recording is active`() {
        // Arrange
        val offlineRecActiveTest1: Byte = 0x80.toByte()
        val offlineRecActiveTest2: Byte = 0xBF.toByte()
        // Act
        val result1 = PmdActiveMeasurement.fromStatusResponse(offlineRecActiveTest1)
        val result2 = PmdActiveMeasurement.fromStatusResponse(offlineRecActiveTest2)
        // Assert
        Assert.assertEquals(result1, PmdActiveMeasurement.OFFLINE_MEASUREMENT_ACTIVE)
        Assert.assertEquals(result2, PmdActiveMeasurement.OFFLINE_MEASUREMENT_ACTIVE)
    }

    @Test
    fun `test online recording is active`() {
        // Arrange
        val onlineRecActiveTest1: Byte = 0x40.toByte()
        val onlineRecActiveTest2: Byte = 0x7F.toByte()
        // Act
        val result1 = PmdActiveMeasurement.fromStatusResponse(onlineRecActiveTest1)
        val result2 = PmdActiveMeasurement.fromStatusResponse(onlineRecActiveTest2)
        // Assert
        Assert.assertEquals(result1, PmdActiveMeasurement.ONLINE_MEASUREMENT_ACTIVE)
        Assert.assertEquals(result2, PmdActiveMeasurement.ONLINE_MEASUREMENT_ACTIVE)
    }

    @Test
    fun `test online and offline recording is active`() {
        // Arrange
        val onlineAndOfflineRecActiveTest1: Byte = 0xC0.toByte()
        val onlineAndOfflineRecActiveTest2: Byte = 0xFF.toByte()
        // Act
        val result1 = PmdActiveMeasurement.fromStatusResponse(onlineAndOfflineRecActiveTest1)
        val result2 = PmdActiveMeasurement.fromStatusResponse(onlineAndOfflineRecActiveTest2)
        // Assert
        Assert.assertEquals(result1, PmdActiveMeasurement.ONLINE_AND_OFFLINE_ACTIVE)
        Assert.assertEquals(result2, PmdActiveMeasurement.ONLINE_AND_OFFLINE_ACTIVE)
    }

    @Test
    fun `test no active recording`() {
        // Arrange
        val noRecordingsActiveTest1: Byte = 0x00.toByte()
        val noRecordingsActiveTest2: Byte = 0x3F.toByte()
        // Act
        val result1 = PmdActiveMeasurement.fromStatusResponse(noRecordingsActiveTest1)
        val result2 = PmdActiveMeasurement.fromStatusResponse(noRecordingsActiveTest2)
        // Assert
        Assert.assertEquals(result1, PmdActiveMeasurement.NO_ACTIVE_MEASUREMENT)
        Assert.assertEquals(result2, PmdActiveMeasurement.NO_ACTIVE_MEASUREMENT)
    }
}