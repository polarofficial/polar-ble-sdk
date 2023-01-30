package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

internal class OfflineHrDataTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `test offline hr data sample`() {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        val offlineHrDataFrameHeader = byteArrayOf(
            0x0E.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 0uL

        // index                                                   data:
        // 0             sample0                                   00
        val expectedSample0 = 0
        // 1             sample0                                   FF
        val expectedSample1 = 255
        // last index    sampleN                                   7F
        val expectedSampleLast = 127
        val expectedSampleSize = 9
        val offlineHrDataFrameContent = byteArrayOf(
            0x00.toByte(), 0xFF.toByte(), 0x32.toByte(), 0x32.toByte(), 0x33.toByte(), 0x33.toByte(), 0x34.toByte(), 0x35.toByte(), 0x7F.toByte(),
        )

        val dataFrame = PmdDataFrame(
            data = offlineHrDataFrameHeader + offlineHrDataFrameContent,
            getPreviousTimeStamp = { previousTimeStamp },
            getFactor = { 1.0f },
            getSampleRate = { 0 })


        // Act
        val offlineHrData = OfflineHrData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSampleSize, offlineHrData.hrSamples.size)
        Assert.assertEquals(expectedSample0, offlineHrData.hrSamples.first().hr)
        Assert.assertEquals(expectedSample1, offlineHrData.hrSamples[1].hr)
        Assert.assertEquals(expectedSampleLast, offlineHrData.hrSamples.last().hr)

    }
}