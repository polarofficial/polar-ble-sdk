// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "KvtxScriptUtils"

/**
 * Generic KVTXScript builder and scanner.
 */
internal object KvtxScriptUtils {

    const val CMD_WRITE_BYTES: Byte = 0x00
    const val CMD_APPEND_BYTES: Byte = 0x01
    const val CMD_REMOVE: Byte = 0x02
    const val CMD_COPY: Byte = 0x03
    const val CMD_MOVE: Byte = 0x04
    const val CMD_COMMIT: Byte = 0x05
    const val CMD_WRITE_BYTES_EX: Byte = 0x06
    const val CMD_APPEND_BYTES_EX: Byte = 0x07
    const val CMD_REMOVE_EX: Byte = 0x08

    /**
     * Build a KVTXScript that writes [data] to [kvKey] and commits.
     *
     * Structure: WRITE_BYTES(key, data) + COMMIT
     */
    fun buildWriteAndCommit(kvKey: Int, data: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(byteArrayOf(CMD_WRITE_BYTES))
            write(u32Le(kvKey))
            write(u32Le(data.size))
            write(data)
            write(byteArrayOf(CMD_COMMIT))
        }.toByteArray()

    /**
     * Scan a full KVTXScript binary and extract the raw value bytes stored under [kvKey].
     *
     * Returns `null` if the key is not present (or was removed) in the script.
     */
    fun extractValueForKey(script: ByteArray, kvKey: Int): ByteArray? {
        val buf = ByteBuffer.wrap(script).order(ByteOrder.LITTLE_ENDIAN)
        val targetKey = kvKey.toUInt().toLong()
        var result: ByteArray? = null

        while (buf.hasRemaining()) {
            val pos = buf.position()
            when (val cmdByte = buf.get().toInt() and 0xFF) {
                CMD_WRITE_BYTES.toInt(), CMD_APPEND_BYTES.toInt() -> {
                    val key = buf.int; val length = buf.int
                    val data = ByteArray(length); buf.get(data)
                    if (key.toUInt().toLong() == targetKey) {
                        result = if (cmdByte == CMD_WRITE_BYTES.toInt()) data else (result ?: byteArrayOf()) + data
                    }
                }
                CMD_REMOVE.toInt() -> {
                    val key = buf.int
                    if (key.toUInt().toLong() == targetKey) result = null
                }
                CMD_COPY.toInt(), CMD_MOVE.toInt() -> { buf.int; buf.int }
                CMD_COMMIT.toInt() -> { }
                CMD_WRITE_BYTES_EX.toInt(), CMD_APPEND_BYTES_EX.toInt() -> {
                    val key = buf.int
                    val idxLen = buf.get().toInt() and 0xFF
                    val idxBytes = ByteArray(idxLen); buf.get(idxBytes)
                    val length = buf.int
                    val data = ByteArray(length); buf.get(data)
                    if (key.toUInt().toLong() == targetKey && idxLen == 0) {
                        result = if (cmdByte == CMD_WRITE_BYTES_EX.toInt()) data else (result ?: byteArrayOf()) + data
                    }
                }
                CMD_REMOVE_EX.toInt() -> {
                    val key = buf.int
                    val idxLen = buf.get().toInt() and 0xFF
                    val idxBytes = ByteArray(idxLen); buf.get(idxBytes)
                    if (key.toUInt().toLong() == targetKey && idxLen == 0) result = null
                }
                else -> {
                    BleLogger.w(TAG, "extractValueForKey: unknown command 0x${cmdByte.toString(16)} @ $pos — stopping")
                    break
                }
            }
        }
        return result
    }

    fun u32Le(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}

