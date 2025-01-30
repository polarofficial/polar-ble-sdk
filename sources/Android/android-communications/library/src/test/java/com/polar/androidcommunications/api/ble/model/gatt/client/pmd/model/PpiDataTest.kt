package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class PpiDataTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `process ppi raw data type 0`() {
        // Arrange
        // HEX: 03 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           03 (PPI)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        val ppiDataFrameHeader = byteArrayOf(
            0x03.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX:  80 80 80 80 80 FF 00 01 00 01 00 00
        // index    type                                            data:
        // 0        HR                                              0x80 (128)
        val heartRate = 128
        // 1..2     PP                                              0x80 0x80 (32896)
        val intervalInMs = 32896
        // 3..4     PP Error Estimate                               0x80 0x80 (32896)
        val errorEstimate = 32896
        // 5        PP flags                                        0xFF
        val ppFlags = 0xFF
        val blockerBit = if (ppFlags and 0x01 != 0) 0x01 else 0x00
        val skinContactStatus = if (ppFlags and 0x02 != 0) 0x01 else 0x00
        val skinContactSupported = if (ppFlags and 0x04 != 0) 0x01 else 0x00

        // 6        HR                                              0x00 (0)
        val heartRate2 = 0
        // 7..8     PP                                              0x01 0x00 (1)
        val intervalInMs2 = 1
        // 9..10     PP Error Estimate                              0x01 0x00 (1)
        val errorEstimate2 = 1
        // 11        PP flags                                       0x00
        val ppFlags2 = 0x00
        val blockerBit2 = if (ppFlags2 and 0x01 != 0) 0x01 else 0x00
        val skinContactStatus2 = if (ppFlags2 and 0x02 != 0) 0x01 else 0x00
        val skinContactSupported2 = if (ppFlags2 and 0x04 != 0) 0x01 else 0x00

        val ppiDataFrameContent = byteArrayOf(
            0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(),
            0x80.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte()
        )

        val dataFrame = PmdDataFrame(
            data = ppiDataFrameHeader + ppiDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { 1.0f },
            getSampleRate = { 0 })


        val ppiData = PpiData.parseDataFromDataFrame(dataFrame)

        // Assert
        assertEquals(heartRate.toLong(), ppiData.ppiSamples[0].hr.toLong())
        assertEquals(intervalInMs.toLong(), ppiData.ppiSamples[0].ppInMs.toLong())
        assertEquals(errorEstimate.toLong(), ppiData.ppiSamples[0].ppErrorEstimate.toLong())
        assertEquals(blockerBit.toLong(), ppiData.ppiSamples[0].blockerBit.toLong())
        assertEquals(skinContactStatus.toLong(), ppiData.ppiSamples[0].skinContactStatus.toLong())
        assertEquals(skinContactSupported.toLong(), ppiData.ppiSamples[0].skinContactSupported.toLong())

        assertEquals(heartRate2.toLong(), ppiData.ppiSamples[1].hr.toLong())
        assertEquals(intervalInMs2.toLong(), ppiData.ppiSamples[1].ppInMs.toLong())
        assertEquals(errorEstimate2.toLong(), ppiData.ppiSamples[1].ppErrorEstimate.toLong())
        assertEquals(blockerBit2.toLong(), ppiData.ppiSamples[1].blockerBit.toLong())
        assertEquals(skinContactStatus2.toLong(), ppiData.ppiSamples[1].skinContactStatus.toLong())
        assertEquals(skinContactSupported2.toLong(), ppiData.ppiSamples[1].skinContactSupported.toLong())

        assertEquals(2, ppiData.ppiSamples.size)
    }
}