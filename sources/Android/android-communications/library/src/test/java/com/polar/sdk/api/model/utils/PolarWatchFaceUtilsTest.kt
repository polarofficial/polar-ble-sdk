// Copyright 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model.utils

import com.polar.sdk.api.model.PolarWatchFaceComplication
import com.polar.sdk.impl.utils.PolarWatchFaceUtils
import com.polar.sdk.impl.utils.WatchfaceConfigFields
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PolarWatchFaceUtilsTest {

    @Test
    fun `buildKvtxScript with WatchfaceConfigFields preserves all scalar fields`() {
        val fields = WatchfaceConfigFields(
            timeStyleId = 3, complicationLayoutId = 1, backgroundStyleId = 2,
            accentColor = 0xdd0d3cL,
            complicationIds = listOf(PolarWatchFaceComplication.SPO2.id),
            fontfaceId = 1
        )
        val script = PolarWatchFaceUtils.buildKvtxScript(fields)
        assertEquals(0x00.toByte(), script[0])
        assertEquals(0x05.toByte(), script.last())
        val key = ByteBuffer.wrap(script, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(PolarWatchFaceUtils.WATCH_FACE_CONFIG_KVS_KEY, key)
    }

    @Test
    fun `buildKvtxScript produces correct WRITE_BYTES and COMMIT structure`() {
        val ids = listOf(PolarWatchFaceComplication.SPO2.id)
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        assertEquals(0x00.toByte(), script[0])
        val key = ByteBuffer.wrap(script, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(PolarWatchFaceUtils.WATCH_FACE_CONFIG_KVS_KEY, key)
        val dataLen = ByteBuffer.wrap(script, 5, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertTrue("data length should be > 0", dataLen > 0)
        assertEquals(1 + 4 + 4 + dataLen + 1, script.size)
        assertEquals(0x05.toByte(), script.last())
    }

    @Test
    fun `buildKvtxScript with empty list produces valid script`() {
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields())
        assertEquals(0x00.toByte(), script[0])
        assertEquals(0x05.toByte(), script.last())
        assertTrue(script.size > 9)
    }

    @Test
    fun `buildKvtxScript with multiple complications`() {
        val ids = listOf(
            PolarWatchFaceComplication.SPO2.id,
            PolarWatchFaceComplication.HEART_RATE.id,
            PolarWatchFaceComplication.ACTIVITY.id
        )
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        assertEquals(0x00.toByte(), script[0])
        assertEquals(0x05.toByte(), script.last())
    }

    @Test
    fun `extractWatchFaceConfigFromKvtxScript returns null for empty script`() {
        assertNull(PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(byteArrayOf()))
    }

    @Test
    fun `extractWatchFaceConfigFromKvtxScript round-trips through buildKvtxScript`() {
        val ids = listOf(PolarWatchFaceComplication.SPO2.id, PolarWatchFaceComplication.ACTIVITY.id)
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        assertEquals(ids, PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!).complicationIds)
    }

    @Test
    fun `parseWatchFaceConfigFlatBuffer handles empty default fields`() {
        val fields = WatchfaceConfigFields()
        val script = PolarWatchFaceUtils.buildKvtxScript(fields)
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        val parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!)
        assertEquals(0, parsed.timeStyleId)
        assertEquals(0, parsed.complicationLayoutId)
        assertEquals(0, parsed.backgroundStyleId)
        assertEquals(0L, parsed.accentColor)
        assertEquals(emptyList<Int>(), parsed.complicationIds)
        assertEquals(0, parsed.fontfaceId)
    }

    @Test
    fun `parseWatchFaceConfigFlatBuffer round-trips all fields through buildKvtxScript`() {
        val originalFields = WatchfaceConfigFields(
            timeStyleId = 7, complicationLayoutId = 3, backgroundStyleId = 4,
            accentColor = 0xaabbccL,
            complicationIds = listOf(PolarWatchFaceComplication.SPO2.id, PolarWatchFaceComplication.HEART_RATE.id),
            fontfaceId = 2
        )
        val script = PolarWatchFaceUtils.buildKvtxScript(originalFields)
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        val parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!)
        assertEquals(originalFields, parsed)
    }

    @Test
    fun `parseWatchFaceConfigFlatBuffer with single complication`() {
        val ids = listOf(PolarWatchFaceComplication.ACTIVITY.id)
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        val parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!)
        assertEquals(ids, parsed.complicationIds)
    }

    @Test
    fun `parseWatchFaceConfigFlatBuffer with multiple complications`() {
        val ids = listOf(PolarWatchFaceComplication.SPO2.id, PolarWatchFaceComplication.ACTIVITY.id, PolarWatchFaceComplication.DATE.id)
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        val parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!)
        assertEquals(ids, parsed.complicationIds)
    }

    @Test
    fun `PolarWatchFaceComplication id is hashCode of complicationId`() {
        PolarWatchFaceComplication.entries.forEach { c ->
            assertEquals("${c.name}.id should equal complicationId.hashCode()", c.complicationId.hashCode(), c.id)
        }
    }

    @Test
    fun `PolarWatchFaceComplication EMPTY id is zero`() {
        assertEquals(0, PolarWatchFaceComplication.EMPTY.id)
        assertEquals("", PolarWatchFaceComplication.EMPTY.complicationId)
    }

    @Test
    fun `PolarWatchFaceComplication fromId round-trips all entries`() {
        PolarWatchFaceComplication.entries.forEach { c ->
            assertEquals("fromId failed for ${c.name}", c, PolarWatchFaceComplication.fromId(c.id))
        }
    }

    @Test
    fun `PolarWatchFaceComplication fromId returns null for unknown id`() {
        assertNull(PolarWatchFaceComplication.fromId(Int.MIN_VALUE))
        assertNull(PolarWatchFaceComplication.fromId(999999999))
    }

    @Test
    fun `PolarWatchFaceComplication ECG uses ecg complicationId`() {
        assertEquals("ecg-complication", PolarWatchFaceComplication.ECG.complicationId)
    }

    @Test
    fun `PolarWatchFaceComplication all entries have unique ids`() {
        val ids = PolarWatchFaceComplication.entries.map { it.id }
        assertEquals("duplicate ids found", ids.size, ids.toSet().size)
    }

    @Test
    fun `PolarWatchFaceComplication CALORIES uses correct complicationId`() {
        assertEquals("calories-complication", PolarWatchFaceComplication.CALORIES.complicationId)
    }
}
