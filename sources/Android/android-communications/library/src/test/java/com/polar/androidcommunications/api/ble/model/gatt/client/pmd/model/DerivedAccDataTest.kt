package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class DerivedAccDataTest {

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    // ----- Helpers ---------------------------------------------------------------

    /**
     * Builds the 10-byte PMD frame header:
     *   byte 0     : measurement type
     *   bytes 1..8 : timestamp little-endian
     *   byte 9     : frame type
     */
    private fun frameHeader(type: PmdMeasurementType, timeStamp: ULong, frameType: PmdDataFrame.PmdDataFrameType): ByteArray {
        val ts = ByteArray(8)
        var v = timeStamp.toLong()
        for (i in 0..7) { ts[i] = (v and 0xFF).toByte(); v = v ushr 8 }
        return byteArrayOf(type.numVal.toByte()) + ts + byteArrayOf(frameType.id.toByte())
    }

    private fun makeFrame(
        measurementType: PmdMeasurementType,
        timeStamp: ULong,
        frameType: PmdDataFrame.PmdDataFrameType,
        content: ByteArray,
        previousTimeStamp: ULong = 0uL
    ): PmdDataFrame = PmdDataFrame(
        data = frameHeader(measurementType, timeStamp, frameType) + content,
        getPreviousTimeStamp = { _, _ -> previousTimeStamp },
        getFactor = { 1.0f }
    ) { 0 }

    // ----- parseDataFromDataFrame: empty data ------------------------------------

    @Test
    fun `parseDataFromDataFrame returns empty result when data content is empty`() {
        val frame = makeFrame(PmdMeasurementType.ACC, 1000uL, PmdDataFrame.PmdDataFrameType.TYPE_0, byteArrayOf())
        val result = DerivedAccData.parseDataFromDataFrame(frame, setOf(0))
        assertTrue(result.derivedSamples.isEmpty())
    }

    @Test
    fun `parseDataFromDataFrame preserves activeMethods in returned object`() {
        val activeMethods = setOf(0, 1, 5)
        val frame = makeFrame(PmdMeasurementType.ACC, 1000uL, PmdDataFrame.PmdDataFrameType.TYPE_0, byteArrayOf())
        val result = DerivedAccData.parseDataFromDataFrame(frame, activeMethods)
        assertEquals(activeMethods, result.activeMethods)
    }

    // ----- parseDataFromDataFrame: firmware ACC frames ---------------------------

    @Test
    fun `parseDataFromDataFrame firmware ACC TYPE_0 component method produces signed xyz sample`() {
        // TYPE_0 = 2 bytes/value; method 0 is in COMPONENT_METHODS_FIRMWARE → x, y, z (signed int16 LE)
        val timeStamp = 2_000_000_000uL
        val content = byteArrayOf(
            0x01.toByte(), 0x00.toByte(),  // x = 1
            0xFE.toByte(), 0xFF.toByte(),  // y = -2
            0x2C.toByte(), 0x01.toByte()   // z = 300
        )
        val frame = makeFrame(PmdMeasurementType.ACC, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, content)
        val result = DerivedAccData.parseDataFromDataFrame(frame, setOf(0))

        assertEquals(1, result.derivedSamples.size)
        val sample = result.derivedSamples[0]
        assertEquals(timeStamp, sample.timeStamp)
        assertEquals(listOf(1, -2, 300), sample.methodValues[0])
    }

    @Test
    fun `parseDataFromDataFrame firmware ACC TYPE_0 scalar method produces single unsigned value`() {
        // method 5 is NOT in COMPONENT_METHODS_FIRMWARE and IS in UNSIGNED_METHODS → 1 unsigned value
        val timeStamp = 1000uL
        val content = byteArrayOf(0xFF.toByte(), 0xFF.toByte())  // unsigned = 65535
        val frame = makeFrame(PmdMeasurementType.ACC, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, content)
        val result = DerivedAccData.parseDataFromDataFrame(frame, setOf(5))

        assertEquals(1, result.derivedSamples.size)
        assertEquals(listOf(65535), result.derivedSamples[0].methodValues[5])
    }

    @Test
    fun `parseDataFromDataFrame firmware ACC TYPE_0 component and scalar methods parsed in order`() {
        // methods 0 (component, signed) and 5 (scalar, unsigned) in the same sample
        val timeStamp = 5000uL
        val content = byteArrayOf(
            0x0A.toByte(), 0x00.toByte(),  // x = 10
            0x14.toByte(), 0x00.toByte(),  // y = 20
            0x1E.toByte(), 0x00.toByte(),  // z = 30
            0x64.toByte(), 0x00.toByte()   // v = 100
        )
        val frame = makeFrame(PmdMeasurementType.ACC, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, content)
        val result = DerivedAccData.parseDataFromDataFrame(frame, setOf(0, 5))

        assertEquals(1, result.derivedSamples.size)
        val sample = result.derivedSamples[0]
        assertEquals(listOf(10, 20, 30), sample.methodValues[0])
        assertEquals(listOf(100), sample.methodValues[5])
    }

    @Test
    fun `parseDataFromDataFrame firmware ACC single sample always gets frame timestamp`() {
        // With a single sample, timestamp = frame.timeStamp even when previousTimeStamp != 0
        val previousTimeStamp = 500uL
        val timeStamp = 1000uL
        val content = byteArrayOf(
            0x05.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
        val frame = makeFrame(PmdMeasurementType.ACC, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, content, previousTimeStamp)
        val result = DerivedAccData.parseDataFromDataFrame(frame, setOf(0))

        assertEquals(1, result.derivedSamples.size)
        assertEquals(timeStamp, result.derivedSamples[0].timeStamp)
    }

    @Test
    fun `parseDataFromDataFrame firmware ACC multiple samples timestamps are interpolated`() {
        // 2 samples; timestamps interpolated linearly between previousTimeStamp and timeStamp
        // delta = (3_000_000_000 - 1_000_000_000) / 2 = 1_000_000_000
        // sample[0].ts = previousTimeStamp + delta = 2_000_000_000
        // sample[1].ts = timeStamp = 3_000_000_000
        val previousTimeStamp = 1_000_000_000uL
        val timeStamp = 3_000_000_000uL
        val content = byteArrayOf(
            0x01.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte(), 0x03.toByte(), 0x00.toByte(),
            0x04.toByte(), 0x00.toByte(), 0x05.toByte(), 0x00.toByte(), 0x06.toByte(), 0x00.toByte()
        )
        val frame = makeFrame(PmdMeasurementType.ACC, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, content, previousTimeStamp)
        val result = DerivedAccData.parseDataFromDataFrame(frame, setOf(0))

        assertEquals(2, result.derivedSamples.size)
        assertEquals(2_000_000_000uL, result.derivedSamples[0].timeStamp)
        assertEquals(timeStamp, result.derivedSamples[1].timeStamp)
    }

    @Test
    fun `parseDataFromDataFrame firmware ACC multiple samples with zero previousTimeStamp all get frame timestamp`() {
        // When previousTimeStamp == 0, all samples receive the frame timestamp
        val timeStamp = 9000uL
        val content = byteArrayOf(
            0x01.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte(), 0x03.toByte(), 0x00.toByte(),
            0x04.toByte(), 0x00.toByte(), 0x05.toByte(), 0x00.toByte(), 0x06.toByte(), 0x00.toByte()
        )
        val frame = makeFrame(PmdMeasurementType.ACC, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, content, previousTimeStamp = 0uL)
        val result = DerivedAccData.parseDataFromDataFrame(frame, setOf(0))

        assertEquals(2, result.derivedSamples.size)
        result.derivedSamples.forEach { assertEquals(timeStamp, it.timeStamp) }
    }

    @Test(expected = PmdDataParseException::class)
    fun `parseDataFromDataFrame firmware ACC throws when activeMethods do not fit in data`() {
        // methods 0 and 1 each need 3 × 2 = 6 bytes; providing only 6 bytes → data is inconsistent with requested methods
        val content = byteArrayOf(
            0x01.toByte(), 0x00.toByte(),
            0x02.toByte(), 0x00.toByte(),
            0x03.toByte(), 0x00.toByte()
        )
        val frame = makeFrame(PmdMeasurementType.ACC, 1000uL, PmdDataFrame.PmdDataFrameType.TYPE_0, content)
        DerivedAccData.parseDataFromDataFrame(frame, setOf(0, 1))
    }

    @Test(expected = PmdDataParseException::class)
    fun `parseDataFromDataFrame firmware ACC throws when no activeMethods fit in data`() {
        // Method 0 (component) needs 6 bytes but only 4 bytes provided
        val content = byteArrayOf(0x01.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte())
        val frame = makeFrame(PmdMeasurementType.ACC, 1000uL, PmdDataFrame.PmdDataFrameType.TYPE_0, content)
        DerivedAccData.parseDataFromDataFrame(frame, setOf(0))
    }

    @Test(expected = PmdDataParseException::class)
    fun `parseDataFromDataFrame throws PmdDataParseException for unsupported frame type`() {
        // TYPE_3 is not handled by sampleSizeFromFrameType → exception
        val content = byteArrayOf(0x01.toByte(), 0x00.toByte())
        val frame = makeFrame(PmdMeasurementType.ACC, 1000uL, PmdDataFrame.PmdDataFrameType.TYPE_3, content)
        DerivedAccData.parseDataFromDataFrame(frame, setOf(0))
    }

    // ----- parseDataFromDataFrame: spec DERIVED_MEASUREMENT frames ---------------

    @Test
    fun `parseDataFromDataFrame spec frame reads method bits from header`() {
        // The outer PMD frameType is passed to makeFrame(..., TYPE_0, ...). The first byte below is the
        // inner derived-measurement payload type, which belongs to dataContent and is not duplicated there.
        // Method bits = 0x0001 → method 0; method 0 is in COMPONENT_METHODS_SPEC → x, y, z
        val timeStamp = 2000uL
        val derivedFrameHeader = byteArrayOf(
            0x00.toByte(),  // derived frame type
            0x02.toByte(),  // source measurement type (ACC)
            0x01.toByte(),  // method bits low: bit 0 = method 0
            0x00.toByte()   // method bits high
        )
        val sampleData = byteArrayOf(
            0x05.toByte(), 0x00.toByte(),  // x = 5
            0x0A.toByte(), 0x00.toByte(),  // y = 10
            0x0F.toByte(), 0x00.toByte()   // z = 15
        )
        val frame = makeFrame(PmdMeasurementType.DERIVED_MEASUREMENT, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, derivedFrameHeader + sampleData)
        val result = DerivedAccData.parseDataFromDataFrame(frame, emptySet())

        assertEquals(1, result.derivedSamples.size)
        assertEquals(timeStamp, result.derivedSamples[0].timeStamp)
        assertEquals(listOf(5, 10, 15), result.derivedSamples[0].methodValues[0])
    }

    @Test
    fun `parseDataFromDataFrame spec frame std method is component in spec layout`() {
        // method 4 is in COMPONENT_METHODS_SPEC (methods 0-4 all component) → x, y, z
        // method bits = 0x0010 → bit 4 = method 4
        val timeStamp = 5000uL
        val derivedFrameHeader = byteArrayOf(
            0x00.toByte(),
            0x02.toByte(),
            0x10.toByte(),  // bit 4 → method 4
            0x00.toByte()
        )
        val sampleData = byteArrayOf(
            0x07.toByte(), 0x00.toByte(),  // x = 7
            0x08.toByte(), 0x00.toByte(),  // y = 8
            0x09.toByte(), 0x00.toByte()   // z = 9
        )
        val frame = makeFrame(PmdMeasurementType.DERIVED_MEASUREMENT, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, derivedFrameHeader + sampleData)
        val result = DerivedAccData.parseDataFromDataFrame(frame, emptySet())

        assertEquals(1, result.derivedSamples.size)
        assertEquals(listOf(7, 8, 9), result.derivedSamples[0].methodValues[4])
    }

    @Test
    fun `parseDataFromDataFrame spec frame norm method is scalar and unsigned`() {
        // method 5 is NOT in COMPONENT_METHODS_SPEC and IS in UNSIGNED_METHODS → 1 unsigned value
        // method bits = 0x0020 → bit 5 = method 5
        val timeStamp = 3000uL
        val derivedFrameHeader = byteArrayOf(
            0x00.toByte(),
            0x02.toByte(),
            0x20.toByte(),  // bit 5 → method 5
            0x00.toByte()
        )
        val sampleData = byteArrayOf(0xFF.toByte(), 0x00.toByte())  // unsigned = 255
        val frame = makeFrame(PmdMeasurementType.DERIVED_MEASUREMENT, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, derivedFrameHeader + sampleData)
        val result = DerivedAccData.parseDataFromDataFrame(frame, emptySet())

        assertEquals(1, result.derivedSamples.size)
        assertEquals(listOf(255), result.derivedSamples[0].methodValues[5])
    }

    @Test
    fun `parseDataFromDataFrame spec frame with too short header returns empty`() {
        // DERIVED_FRAME_HEADER_SIZE = 4; providing only 2 bytes
        val content = byteArrayOf(0x00.toByte(), 0x02.toByte())
        val frame = makeFrame(PmdMeasurementType.DERIVED_MEASUREMENT, 1000uL, PmdDataFrame.PmdDataFrameType.TYPE_0, content)
        val result = DerivedAccData.parseDataFromDataFrame(frame, emptySet())
        assertTrue(result.derivedSamples.isEmpty())
    }

    @Test
    fun `parseDataFromDataFrame spec frame with zero method bits returns empty`() {
        // No methods set in the bitmask → effectiveMethods is empty → no samples
        val derivedFrameHeader = byteArrayOf(0x00.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte())
        val frame = makeFrame(PmdMeasurementType.DERIVED_MEASUREMENT, 1000uL, PmdDataFrame.PmdDataFrameType.TYPE_0, derivedFrameHeader)
        val result = DerivedAccData.parseDataFromDataFrame(frame, emptySet())
        assertTrue(result.derivedSamples.isEmpty())
    }

    @Test
    fun `parseDataFromDataFrame spec frame method bits spanning both bytes are parsed correctly`() {
        // method 8 = bit 8 in method bits → high byte bit 0 = 0x01
        // method bits low=0x00, high=0x01
        val timeStamp = 6000uL
        val derivedFrameHeader = byteArrayOf(
            0x00.toByte(),
            0x02.toByte(),
            0x00.toByte(),  // low byte: no low methods
            0x01.toByte()   // high byte: bit 0 → method 8
        )
        val sampleData = byteArrayOf(0x2A.toByte(), 0x00.toByte())  // unsigned = 42
        val frame = makeFrame(PmdMeasurementType.DERIVED_MEASUREMENT, timeStamp, PmdDataFrame.PmdDataFrameType.TYPE_0, derivedFrameHeader + sampleData)
        val result = DerivedAccData.parseDataFromDataFrame(frame, emptySet())

        assertEquals(1, result.derivedSamples.size)
        assertEquals(listOf(42), result.derivedSamples[0].methodValues[8])
    }
}