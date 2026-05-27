// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KvtxScriptUtilsTest {

    @Test
    fun `buildWriteAndCommit produces correct structure`() {
        val key = 0x12345678
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val script = KvtxScriptUtils.buildWriteAndCommit(key, data)
        assertEquals(KvtxScriptUtils.CMD_WRITE_BYTES, script[0])
        val parsedKey = ByteBuffer.wrap(script, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(key, parsedKey)
        val parsedLen = ByteBuffer.wrap(script, 5, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(data.size, parsedLen)
        assertArrayEquals(data, script.sliceArray(9..11))
        assertEquals(KvtxScriptUtils.CMD_COMMIT, script.last())
        assertEquals(1 + 4 + 4 + data.size + 1, script.size)
    }

    @Test
    fun `extractValueForKey finds correct key among multiple`() {
        val key1 = 0x11110000
        val key2 = 0x22220000
        val data1 = byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        val data2 = byteArrayOf(0xBE.toByte(), 0xEF.toByte())
        val script = KvtxScriptUtils.buildWriteAndCommit(key1, data1) +
                KvtxScriptUtils.buildWriteAndCommit(key2, data2)
        assertArrayEquals(data1, KvtxScriptUtils.extractValueForKey(script, key1))
        assertArrayEquals(data2, KvtxScriptUtils.extractValueForKey(script, key2))
    }

    @Test
    fun `extractValueForKey returns null for missing key`() {
        val script = KvtxScriptUtils.buildWriteAndCommit(0x11111111, byteArrayOf(0x01))
        assertNull(KvtxScriptUtils.extractValueForKey(script, 0x22222222))
    }

    @Test
    fun `extractValueForKey handles empty script`() {
        assertNull(KvtxScriptUtils.extractValueForKey(byteArrayOf(), 0x12345678))
    }

    @Test
    fun `u32Le encodes little-endian 32-bit integers`() {
        val value = 0x12345678
        val bytes = KvtxScriptUtils.u32Le(value)
        assertEquals(4, bytes.size)
        assertEquals(0x78.toByte(), bytes[0])
        assertEquals(0x56.toByte(), bytes[1])
        assertEquals(0x34.toByte(), bytes[2])
        assertEquals(0x12.toByte(), bytes[3])
        assertEquals(value, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test
    fun `extractValueForKey APPEND appends to existing value`() {
        val key = 0x99999999.toInt()
        val part1 = byteArrayOf(0x11)
        val part2 = byteArrayOf(0x22, 0x33)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(part1.size) + part1 +
                    byteArrayOf(KvtxScriptUtils.CMD_APPEND_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(part2.size) + part2 +
                    byteArrayOf(KvtxScriptUtils.CMD_COMMIT)
        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33), KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey REMOVE clears the value`() {
        val key = 0x33333333.toInt()
        val data = byteArrayOf(0x42)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    byteArrayOf(KvtxScriptUtils.CMD_REMOVE) +
                    KvtxScriptUtils.u32Le(key) +
                    byteArrayOf(KvtxScriptUtils.CMD_COMMIT)
        assertNull(KvtxScriptUtils.extractValueForKey(script, key))
    }
}
