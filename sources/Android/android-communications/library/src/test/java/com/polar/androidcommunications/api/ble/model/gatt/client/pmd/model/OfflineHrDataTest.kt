package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import androidx.test.espresso.matcher.ViewMatchers
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.function.ThrowingRunnable

internal class OfflineHrDataTest {

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `test offline Type0 hr data sample`() {
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
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { 1.0f }
        ) { 0 }


        // Act
        val offlineHrData = OfflineHrData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSampleSize, offlineHrData.hrSamples.size)
        Assert.assertEquals(expectedSample0, offlineHrData.hrSamples.first().hr)
        Assert.assertEquals(expectedSample1, offlineHrData.hrSamples[1].hr)
        Assert.assertEquals(expectedSampleLast, offlineHrData.hrSamples.last().hr)
    }

    @Test
    fun `test offline Type0 compressed hr data sample throws`() {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        val offlineHrDataFrameHeader = byteArrayOf(
            0x0E.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 0uL

        val dataFrame = PmdDataFrame(
            data = offlineHrDataFrameHeader,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { 1.0f }
        ) { 0 }

        var throwingRunnable = ThrowingRunnable { OfflineHrData.parseDataFromDataFrame(dataFrame) }
        val exception = Assert.assertThrows(Exception::class.java, throwingRunnable)
        ViewMatchers.assertThat(
            exception.message,
            Matchers.equalTo("Compressed FrameType: TYPE_0 is not supported by Offline HR data parser")
        )
    }

    @Test
    fun `test offline type 1 hr data sample`() {

        val offlineHrDataFrameHeader = byteArrayOf(
            0x14.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(),
            0x0.toByte(), 0x0.toByte(), 0x1.toByte()
        )

        val offlineHrDataFrameContent = byteArrayOf(
            72.toByte(), 86.toByte(), 71.toByte(), 81.toByte(), 64.toByte(), 82.toByte()
        )

        val dataFrame = PmdDataFrame(
            data = offlineHrDataFrameHeader + offlineHrDataFrameContent,
            getPreviousTimeStamp = {  pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> 0uL },
            getFactor = { 1.0f }
        ) { 0 }

        val offlineHrData = OfflineHrData.parseDataFromDataFrame(dataFrame)

        Assert.assertEquals(2, offlineHrData.hrSamples.size)
        Assert.assertEquals(72, offlineHrData.hrSamples.first().hr)
        Assert.assertEquals(86, offlineHrData.hrSamples.first().ppgQuality)
        Assert.assertEquals(71, offlineHrData.hrSamples.first().correctedHr)
        Assert.assertEquals(81, offlineHrData.hrSamples.last().hr)
        Assert.assertEquals(64, offlineHrData.hrSamples.last().ppgQuality)
        Assert.assertEquals(82, offlineHrData.hrSamples.last().correctedHr)
    }

    @Test
    fun `test offline Type1 compressed hr data sample throws`() {

        val offlineHrDataFrameHeader = byteArrayOf(
            0x14.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(),
            0x0.toByte(), 0x0.toByte(), 0x81.toByte()
        )
        val previousTimeStamp = 0uL

        val dataFrame = PmdDataFrame(
            data = offlineHrDataFrameHeader,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { 1.0f }
        ) { 0 }

        var throwingRunnable = ThrowingRunnable { OfflineHrData.parseDataFromDataFrame(dataFrame) }
        val exception = Assert.assertThrows(Exception::class.java, throwingRunnable)
        ViewMatchers.assertThat(
            exception.message,
            Matchers.equalTo("Compressed FrameType: TYPE_1 is not supported by Offline HR data parser")
        )
    }
}