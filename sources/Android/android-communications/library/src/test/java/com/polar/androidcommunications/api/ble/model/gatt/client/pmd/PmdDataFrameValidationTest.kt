package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame.PmdDataFrameType
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PmdDataFrame] minimum-size validation added to prevent crashes on
 * malformed BLE packets from devices.
 */
class PmdDataFrameValidationTest {

    private val getPreviousTimeStamp: (PmdMeasurementType, PmdDataFrameType) -> ULong = { _, _ -> 0uL }
    private val getFactor: (PmdMeasurementType) -> Float = { 1.0f }
    private val getSampleRate: (PmdMeasurementType) -> Int = { 0 }

    @Test
    fun `PmdDataFrame throws PmdDataParseException when data is empty`() {
        val exception = assertThrows(PmdDataParseException::class.java) {
            PmdDataFrame(byteArrayOf(), getPreviousTimeStamp, getFactor, getSampleRate)
        }
        assertTrue(exception.message!!.contains("too short"))
    }

    @Test
    fun `PmdDataFrame throws PmdDataParseException when data has fewer than 10 bytes`() {
        // 9 bytes — one byte short of the required minimum (measurement type + 8-byte timestamp + frame type)
        val nineBytes = byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val exception = assertThrows(PmdDataParseException::class.java) {
            PmdDataFrame(nineBytes, getPreviousTimeStamp, getFactor, getSampleRate)
        }
        assertTrue(exception.message!!.contains("too short"))
        assertTrue(exception.message!!.contains("9"))
    }

    @Test
    fun `PmdDataFrame parses successfully with exactly 10 bytes`() {
        // Minimal valid frame: type=ECG(0x00), 8-byte timestamp, frame type=TYPE_0(0x00)
        val tenBytes = byteArrayOf(
            0x00.toByte(),                                                              // ECG
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,                            // timestamp
            0x00.toByte()                                                               // TYPE_0 raw
        )
        // Should not throw — content is empty but header is valid
        val frame = PmdDataFrame(tenBytes, getPreviousTimeStamp, getFactor, getSampleRate)
        assertTrue(frame.dataContent.isEmpty())
    }
}

