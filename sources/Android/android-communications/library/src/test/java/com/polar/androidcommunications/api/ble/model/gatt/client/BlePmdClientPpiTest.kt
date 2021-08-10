package com.polar.androidcommunications.api.ble.model.gatt.client

import org.junit.Assert.assertEquals
import org.junit.Test

class BlePmdClientPpiTest {
    @Test
    fun test_PPI_DataSample() {
        // Arrange
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

        val measurementFrame = byteArrayOf(
            0x80.toByte(),
            0x80.toByte(),
            0x80.toByte(),
            0x80.toByte(),
            0x80.toByte(),
            0xFF.toByte(),
            0x00.toByte(),
            0x01.toByte(),
            0x00.toByte(),
            0x01.toByte(),
            0x00.toByte(),
            0x00.toByte()
        )
        val timeStamp: Long = Long.MAX_VALUE

        // Act
        val ppiData = BlePMDClient.PpiData(measurementFrame, timeStamp)

        // Assert
        assertEquals(heartRate.toLong(), ppiData.ppSamples[0].hr.toLong())
        assertEquals(intervalInMs.toLong(), ppiData.ppSamples[0].ppInMs.toLong())
        assertEquals(errorEstimate.toLong(), ppiData.ppSamples[0].ppErrorEstimate.toLong())
        assertEquals(blockerBit.toLong(), ppiData.ppSamples[0].blockerBit.toLong())
        assertEquals(skinContactStatus.toLong(), ppiData.ppSamples[0].skinContactStatus.toLong())
        assertEquals(
            skinContactSupported.toLong(),
            ppiData.ppSamples[0].skinContactSupported.toLong()
        )

        assertEquals(heartRate2.toLong(), ppiData.ppSamples[1].hr.toLong())
        assertEquals(intervalInMs2.toLong(), ppiData.ppSamples[1].ppInMs.toLong())
        assertEquals(errorEstimate2.toLong(), ppiData.ppSamples[1].ppErrorEstimate.toLong())
        assertEquals(blockerBit2.toLong(), ppiData.ppSamples[1].blockerBit.toLong())
        assertEquals(skinContactStatus2.toLong(), ppiData.ppSamples[1].skinContactStatus.toLong())
        assertEquals(
            skinContactSupported2.toLong(),
            ppiData.ppSamples[1].skinContactSupported.toLong()
        )

        assertEquals(2, ppiData.ppSamples.size)
    }
}