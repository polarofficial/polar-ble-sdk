// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KvtxScriptUtilsTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun writeExCmd(key: Int, idxBytes: ByteArray, data: ByteArray): ByteArray =
        byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES_EX) +
                KvtxScriptUtils.u32Le(key) +
                byteArrayOf(idxBytes.size.toByte()) + idxBytes +
                KvtxScriptUtils.u32Le(data.size) + data

    private fun appendExCmd(key: Int, idxBytes: ByteArray, data: ByteArray): ByteArray =
        byteArrayOf(KvtxScriptUtils.CMD_APPEND_BYTES_EX) +
                KvtxScriptUtils.u32Le(key) +
                byteArrayOf(idxBytes.size.toByte()) + idxBytes +
                KvtxScriptUtils.u32Le(data.size) + data

    private fun removeExCmd(key: Int, idxBytes: ByteArray): ByteArray =
        byteArrayOf(KvtxScriptUtils.CMD_REMOVE_EX) +
                KvtxScriptUtils.u32Le(key) +
                byteArrayOf(idxBytes.size.toByte()) + idxBytes

    private fun copyOrMoveCmd(cmd: Byte, srcKey: Int, dstKey: Int): ByteArray =
        byteArrayOf(cmd) + KvtxScriptUtils.u32Le(srcKey) + KvtxScriptUtils.u32Le(dstKey)

    // ── buildWriteAndCommit ──────────────────────────────────────────────────

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
    fun `buildWriteAndCommit with empty data produces minimal script`() {
        val key = 0xAABBCCDD.toInt()
        val script = KvtxScriptUtils.buildWriteAndCommit(key, byteArrayOf())

        assertEquals(1 + 4 + 4 + 0 + 1, script.size)  // cmd + key + len + data + commit
        val parsedLen = ByteBuffer.wrap(script, 5, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(0, parsedLen)
        assertEquals(KvtxScriptUtils.CMD_COMMIT, script.last())
    }

    @Test
    fun `buildWriteAndCommit round-trip recovers original data`() {
        val key = 0x00FF00FF
        val data = "roundtrip".toByteArray()
        val script = KvtxScriptUtils.buildWriteAndCommit(key, data)

        assertArrayEquals(data, KvtxScriptUtils.extractValueForKey(script, key))
    }

    // ── u32Le ────────────────────────────────────────────────────────────────

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
    fun `u32Le encodes zero`() {
        val bytes = KvtxScriptUtils.u32Le(0)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), bytes)
    }

    @Test
    fun `u32Le encodes Int MAX_VALUE`() {
        val bytes = KvtxScriptUtils.u32Le(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test
    fun `u32Le encodes negative value as unsigned 32-bit`() {
        val value = -1  // 0xFFFFFFFF unsigned
        val bytes = KvtxScriptUtils.u32Le(value)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), bytes)
    }

    // ── extractValueForKey – CMD_WRITE_BYTES / CMD_APPEND_BYTES ─────────────

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
    fun `extractValueForKey WRITE overwrites previous WRITE for same key`() {
        val key = 0x11111111
        val first = byteArrayOf(0xAA.toByte())
        val second = byteArrayOf(0xBB.toByte())
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(first.size) + first +
                    byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(second.size) + second

        assertArrayEquals(second, KvtxScriptUtils.extractValueForKey(script, key))
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
    fun `extractValueForKey APPEND with no prior WRITE starts from empty`() {
        val key = 0x55555555
        val data = byteArrayOf(0xAA.toByte())
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_APPEND_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data

        assertArrayEquals(data, KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey REMOVE clears the value`() {
        val key = 0x33333333
        val data = byteArrayOf(0x42)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    byteArrayOf(KvtxScriptUtils.CMD_REMOVE) +
                    KvtxScriptUtils.u32Le(key) +
                    byteArrayOf(KvtxScriptUtils.CMD_COMMIT)
        assertNull(KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey REMOVE does not affect other keys`() {
        val key1 = 0x11111111
        val key2 = 0x22222222
        val data1 = byteArrayOf(0x01)
        val data2 = byteArrayOf(0x02)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key1) + KvtxScriptUtils.u32Le(data1.size) + data1 +
                    byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key2) + KvtxScriptUtils.u32Le(data2.size) + data2 +
                    byteArrayOf(KvtxScriptUtils.CMD_REMOVE) + KvtxScriptUtils.u32Le(key1)

        assertNull(KvtxScriptUtils.extractValueForKey(script, key1))
        assertArrayEquals(data2, KvtxScriptUtils.extractValueForKey(script, key2))
    }

    @Test
    fun `extractValueForKey WRITE after REMOVE restores value`() {
        val key = 0x44444444
        val data = byteArrayOf(0x99.toByte())
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    byteArrayOf(KvtxScriptUtils.CMD_REMOVE) + KvtxScriptUtils.u32Le(key) +
                    byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data

        assertArrayEquals(data, KvtxScriptUtils.extractValueForKey(script, key))
    }

    // ── extractValueForKey – CMD_WRITE_BYTES_EX / CMD_APPEND_BYTES_EX ───────

    @Test
    fun `extractValueForKey WRITE_BYTES_EX with idxLen zero writes value`() {
        val key = 0x12121212
        val data = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val script = writeExCmd(key, byteArrayOf(), data)

        assertArrayEquals(data, KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey WRITE_BYTES_EX with non-zero idxLen is ignored`() {
        val key = 0x12121212
        val data = byteArrayOf(0xAB.toByte())
        val script = writeExCmd(key, byteArrayOf(0x01), data)

        assertNull(KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey APPEND_BYTES_EX with idxLen zero appends value`() {
        val key = 0x34343434
        val part1 = byteArrayOf(0x11)
        val part2 = byteArrayOf(0x22)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(part1.size) + part1 +
                    appendExCmd(key, byteArrayOf(), part2)

        assertArrayEquals(byteArrayOf(0x11, 0x22), KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey APPEND_BYTES_EX with non-zero idxLen is ignored`() {
        val key = 0x34343434
        val part1 = byteArrayOf(0x11)
        val part2 = byteArrayOf(0x22)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(part1.size) + part1 +
                    appendExCmd(key, byteArrayOf(0x01), part2)

        assertArrayEquals(part1, KvtxScriptUtils.extractValueForKey(script, key))
    }

    // ── extractValueForKey – CMD_REMOVE_EX ───────────────────────────────────

    @Test
    fun `extractValueForKey REMOVE_EX with idxLen zero clears value`() {
        val key = 0x56565656
        val data = byteArrayOf(0xFF.toByte())
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    removeExCmd(key, byteArrayOf())

        assertNull(KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey REMOVE_EX with non-zero idxLen does not clear value`() {
        val key = 0x56565656
        val data = byteArrayOf(0xFF.toByte())
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    removeExCmd(key, byteArrayOf(0x01))

        assertArrayEquals(data, KvtxScriptUtils.extractValueForKey(script, key))
    }

    // ── extractValueForKey – CMD_COPY / CMD_MOVE ─────────────────────────────

    @Test
    fun `extractValueForKey CMD_COPY is skipped without affecting target key`() {
        val key = 0x77777777
        val data = byteArrayOf(0x42)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    copyOrMoveCmd(KvtxScriptUtils.CMD_COPY, 0x11111111, 0x22222222)

        assertArrayEquals(data, KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey CMD_MOVE is skipped without affecting target key`() {
        val key = 0x77777777
        val data = byteArrayOf(0x42)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    copyOrMoveCmd(KvtxScriptUtils.CMD_MOVE, 0x11111111, 0x22222222)

        assertArrayEquals(data, KvtxScriptUtils.extractValueForKey(script, key))
    }

    // ── extractValueForKey – CMD_COMMIT ───────────────────────────────────────

    @Test
    fun `extractValueForKey CMD_COMMIT does not affect result`() {
        val key = 0x88888888.toInt()
        val data = byteArrayOf(0x01, 0x02)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    byteArrayOf(KvtxScriptUtils.CMD_COMMIT) +
                    byteArrayOf(KvtxScriptUtils.CMD_COMMIT)

        assertArrayEquals(data, KvtxScriptUtils.extractValueForKey(script, key))
    }

    // ── extractValueForKey – unknown command ─────────────────────────────────

    @Test
    fun `extractValueForKey stops on unknown command and returns accumulated result`() {
        val key = 0xAAAAAAAA.toInt()
        val data = byteArrayOf(0x01)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    byteArrayOf(0xFF.toByte()) // unknown command — scanner stops here

        // Result accumulated before the unknown command is returned
        assertArrayEquals(data, KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey stops on unknown command so later writes are not seen`() {
        val key = 0xAAAAAAAA.toInt()
        val first = byteArrayOf(0x01)
        val second = byteArrayOf(0x02)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(first.size) + first +
                    byteArrayOf(0xFF.toByte()) + // unknown command — scanner stops
                    byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(second.size) + second

        assertArrayEquals(first, KvtxScriptUtils.extractValueForKey(script, key))
    }
}
