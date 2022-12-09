package com.polar.androidcommunications.api.ble.model.advertisement

import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class BlePolarHrAdvertisementTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    private lateinit var blePolarHrAdvertisement: BlePolarHrAdvertisement

    @Before
    fun setUp() {
        blePolarHrAdvertisement = BlePolarHrAdvertisement()
    }

    @Test
    fun `test battery status`() {
        // Arrange
        val batteryStatusOk = byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())
        val batteryStatusNok = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())

        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(batteryStatusOk)
        Assert.assertEquals(0, blePolarHrAdvertisement.batteryStatus)
        blePolarHrAdvertisement.processPolarManufacturerData(batteryStatusNok)
        Assert.assertEquals(1, blePolarHrAdvertisement.batteryStatus)
    }

    @Test
    fun `test frame counter`() {
        // Arrange
        val counter0 = byteArrayOf(0xE3.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())
        val counter1 = byteArrayOf(0xE7.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())
        val counter7 = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())

        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(counter0)
        Assert.assertEquals(0, blePolarHrAdvertisement.advFrameCounter)
        Assert.assertEquals(true, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter1)
        Assert.assertEquals(1, blePolarHrAdvertisement.advFrameCounter)
        Assert.assertEquals(true, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter1)
        Assert.assertEquals(1, blePolarHrAdvertisement.advFrameCounter)
        Assert.assertEquals(false, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter1)
        Assert.assertEquals(1, blePolarHrAdvertisement.advFrameCounter)
        Assert.assertEquals(false, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter7)
        Assert.assertEquals(7, blePolarHrAdvertisement.advFrameCounter)
        Assert.assertEquals(true, blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter0)
        Assert.assertEquals(0, blePolarHrAdvertisement.advFrameCounter)
        Assert.assertEquals(true, blePolarHrAdvertisement.isHrDataUpdated)
    }

    @Test
    fun `test status flag`() {
        // Arrange
        val statusFlagUp = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())
        val statusFlagDown = byteArrayOf(0x7F.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())

        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(statusFlagUp)
        Assert.assertEquals(1, blePolarHrAdvertisement.statusFlags)
        blePolarHrAdvertisement.processPolarManufacturerData(statusFlagDown)
        Assert.assertEquals(0, blePolarHrAdvertisement.statusFlags)
    }

    @Test
    fun `test kHz code`() {
        // Arrange
        val code0 = byteArrayOf(0xFF.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val code255 = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(code0)
        Assert.assertEquals(0, blePolarHrAdvertisement.khzCode)
        blePolarHrAdvertisement.processPolarManufacturerData(code255)
        Assert.assertEquals(255, blePolarHrAdvertisement.khzCode)
    }

    @Test
    fun `test fast and slow avg hr`() {
        // Arrange
        val fastAvgAndLowAvg0 = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x00.toByte())
        val fastAvgAndSlowAvg255 = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val fastAvg0SlowMissing = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00.toByte())
        val fastAvg255SlowMissing = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(fastAvgAndLowAvg0)
        Assert.assertEquals(0, blePolarHrAdvertisement.fastAverageHr)
        Assert.assertEquals(0, blePolarHrAdvertisement.slowAverageHr)
        Assert.assertEquals(0, blePolarHrAdvertisement.hrForDisplay)
        blePolarHrAdvertisement.processPolarManufacturerData(fastAvgAndSlowAvg255)
        Assert.assertEquals(255, blePolarHrAdvertisement.fastAverageHr)
        Assert.assertEquals(255, blePolarHrAdvertisement.slowAverageHr)
        Assert.assertEquals(255, blePolarHrAdvertisement.hrForDisplay)
        blePolarHrAdvertisement.processPolarManufacturerData(fastAvg0SlowMissing)
        Assert.assertEquals(0, blePolarHrAdvertisement.fastAverageHr)
        Assert.assertEquals(0, blePolarHrAdvertisement.slowAverageHr)
        Assert.assertEquals(0, blePolarHrAdvertisement.hrForDisplay)
        blePolarHrAdvertisement.processPolarManufacturerData(fastAvg255SlowMissing)
        Assert.assertEquals(255, blePolarHrAdvertisement.fastAverageHr)
        Assert.assertEquals(255, blePolarHrAdvertisement.slowAverageHr)
        Assert.assertEquals(255, blePolarHrAdvertisement.hrForDisplay)
    }

    @Test
    fun `test reset to defaults`() {
        // Arrange
        val counter0 = byteArrayOf(0xE3.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())
        val counter1 = byteArrayOf(0xE7.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())

        // Act & Assert
        blePolarHrAdvertisement.processPolarManufacturerData(counter0)
        Assert.assertTrue(blePolarHrAdvertisement.isPresent)
        Assert.assertTrue(blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.resetToDefault()
        Assert.assertFalse(blePolarHrAdvertisement.isPresent)
        Assert.assertFalse(blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter0)
        Assert.assertTrue(blePolarHrAdvertisement.isPresent)
        Assert.assertTrue(blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter0)
        Assert.assertTrue(blePolarHrAdvertisement.isPresent)
        Assert.assertFalse(blePolarHrAdvertisement.isHrDataUpdated)
        blePolarHrAdvertisement.processPolarManufacturerData(counter1)
        Assert.assertTrue(blePolarHrAdvertisement.isPresent)
        Assert.assertTrue(blePolarHrAdvertisement.isHrDataUpdated)
    }
}