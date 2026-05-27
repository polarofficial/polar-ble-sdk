// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

import com.google.flatbuffers.FlatBufferBuilder
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarWatchFaceComplication
import protocol.PftpRequest
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "PolarWatchFaceUtils"

data class WatchfaceConfigFields(
    val timeStyleId: Int = 0,
    val complicationLayoutId: Int = 0,
    val backgroundStyleId: Int = 0,
    val accentColor: Long = 0L,
    val complicationIds: List<Int> = emptyList(),
    val fontfaceId: Int = 0
)

internal object PolarWatchFaceUtils {

    /** KVS key for "ui.watchface_config" */
    const val WATCH_FACE_CONFIG_KVS_KEY: Int = 1064434511

    private const val FB_FIELD_TIME_STYLE_ID = 0
    private const val FB_FIELD_COMPLICATION_LAYOUT_ID = 1
    private const val FB_FIELD_BACKGROUND_STYLE_ID = 2
    private const val FB_FIELD_ACCENT_COLOR = 3
    private const val FB_FIELD_COMPLICATION_IDS = 4
    private const val FB_FIELD_FONTFACE_ID = 5
    private const val FB_TABLE_FIELD_COUNT = 6

    private const val KVTX_FILE_PATH = "/SYS/KVTX"

    /**
     * Build a KVTXScript (WRITE_BYTES + COMMIT) carrying a full WatchfaceConfig FlatBuffer.
     * Delegates generic framing to [KvtxScriptUtils.buildWriteAndCommit].
     */
    fun buildKvtxScript(fields: WatchfaceConfigFields): ByteArray {
        val configData = buildWatchFaceConfigFlatBuffer(fields)
        return KvtxScriptUtils.buildWriteAndCommit(WATCH_FACE_CONFIG_KVS_KEY, configData)
    }

    /**
     * Build a complete WatchfaceConfig FlatBuffer preserving ALL fields from [fields].
     *
     * FlatBuffers encodes scalar fields only when they differ from their default (0),
     * so unchanged zero fields are omitted automatically.
     */
    internal fun buildWatchFaceConfigFlatBuffer(fields: WatchfaceConfigFields): ByteArray {
        val builder = FlatBufferBuilder(256)

        // Build complication_ids vector first (offsets must be created before startTable)
        builder.startVector(4, fields.complicationIds.size, 4)
        for (i in fields.complicationIds.indices.reversed()) {
            builder.addInt(fields.complicationIds[i])
        }
        val vectorOffset = builder.endVector()

        builder.startTable(FB_TABLE_FIELD_COUNT)

        // field 0: time_style_id (uint16)
        builder.addShort(FB_FIELD_TIME_STYLE_ID, fields.timeStyleId.toShort(), 0)
        // field 1: complication_layout_id (uint16)
        builder.addShort(FB_FIELD_COMPLICATION_LAYOUT_ID, fields.complicationLayoutId.toShort(), 0)
        // field 2: background_style_id (uint16)
        builder.addShort(FB_FIELD_BACKGROUND_STYLE_ID, fields.backgroundStyleId.toShort(), 0)
        // field 3: accent_color (uint32) — stored as int in FlatBuffers
        builder.addInt(FB_FIELD_ACCENT_COLOR, fields.accentColor.toInt(), 0)
        // field 4: complication_ids ([int32]) — offset field
        builder.addOffset(FB_FIELD_COMPLICATION_IDS, vectorOffset, 0)
        // field 5: fontface_id (byte)
        builder.addByte(FB_FIELD_FONTFACE_ID, fields.fontfaceId.toByte(), 0)

        val tableOffset = builder.endTable()
        builder.finish(tableOffset)
        return builder.sizedByteArray()
    }

    fun extractWatchFaceConfigFromKvtxScript(script: ByteArray): ByteArray? =
        KvtxScriptUtils.extractValueForKey(script, WATCH_FACE_CONFIG_KVS_KEY)

    fun parseWatchFaceConfigFlatBuffer(raw: ByteArray): WatchfaceConfigFields {
        val empty = WatchfaceConfigFields()

        if (raw.size < 4) {
            BleLogger.w(TAG, "parseWatchFaceConfigFlatBuffer: too short (${raw.size} bytes), returning defaults")
            return empty
        }

        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val rootOffset = bb.getInt(0)

        if (rootOffset + 4 > raw.size) {
            BleLogger.w(TAG, "parseWatchFaceConfigFlatBuffer: rootOffset out of bounds"); return empty
        }

        val vtableOffsetFromTable = bb.getInt(rootOffset)
        val vtablePos = rootOffset - vtableOffsetFromTable

        if (vtablePos < 0 || vtablePos + 4 > raw.size) {
            BleLogger.w(TAG, "parseWatchFaceConfigFlatBuffer: vtablePos out of bounds"); return empty
        }

        val vtableSize = bb.getShort(vtablePos).toInt() and 0xFFFF
        val fieldCount = (vtableSize - 4) / 2

        // Helper: read a scalar field offset from vtable (0 = absent/default)
        fun fieldOffset(fieldIdx: Int): Int {
            if (fieldIdx >= fieldCount) return 0
            return bb.getShort(vtablePos + 4 + fieldIdx * 2).toInt() and 0xFFFF
        }

        // field 0: time_style_id (uint16)
        val timeStyleId = fieldOffset(0).let { fo ->
            if (fo == 0) 0 else (bb.getShort(rootOffset + fo).toInt() and 0xFFFF)
        }

        // field 1: complication_layout_id (uint16)
        val complicationLayoutId = fieldOffset(1).let { fo ->
            if (fo == 0) 0 else (bb.getShort(rootOffset + fo).toInt() and 0xFFFF)
        }

        // field 2: background_style_id (uint16)
        val backgroundStyleId = fieldOffset(2).let { fo ->
            if (fo == 0) 0 else (bb.getShort(rootOffset + fo).toInt() and 0xFFFF)
        }

        // field 3: accent_color (uint32)
        val accentColor = fieldOffset(3).let { fo ->
            if (fo == 0) 0L else (bb.getInt(rootOffset + fo).toLong() and 0xFFFFFFFFL)
        }

        // field 4: complication_ids ([int32])
        val complicationIds: List<Int> = run {
            val fo = fieldOffset(4)
            if (fo == 0) return@run emptyList()
            val vectorRefPos = rootOffset + fo
            if (vectorRefPos + 4 > raw.size) {
                BleLogger.w(TAG, "parseWatchFaceConfigFlatBuffer: complication_ids ref out of bounds"); return@run emptyList()
            }
            val vectorPos = vectorRefPos + bb.getInt(vectorRefPos)
            if (vectorPos + 4 > raw.size) {
                BleLogger.w(TAG, "parseWatchFaceConfigFlatBuffer: complication_ids vector out of bounds"); return@run emptyList()
            }
            val vectorLength = bb.getInt(vectorPos)
            if (vectorLength !in 0..1000) {
                BleLogger.w(TAG, "parseWatchFaceConfigFlatBuffer: complication_ids length $vectorLength invalid"); return@run emptyList()
            }
            val dataStart = vectorPos + 4
            if (dataStart + vectorLength * 4 > raw.size) {
                BleLogger.w(TAG, "parseWatchFaceConfigFlatBuffer: complication_ids data overruns buffer"); return@run emptyList()
            }
            val ids = mutableListOf<Int>()
            for (i in 0 until vectorLength) {
                val id = bb.getInt(dataStart + i * 4)
                val known = PolarWatchFaceComplication.fromId(id)
                BleLogger.d(TAG, "  complication_ids[$i] = $id (0x${id.toUInt().toString(16).padStart(8,'0')})" +
                        if (known != null) " => ${known.name}" else " => UNKNOWN (not in enum)")
                ids += id
            }
            ids
        }

        // field 5: fontface_id (byte)
        val fontfaceId = fieldOffset(5).let { fo ->
            if (fo == 0) 0 else (bb.get(rootOffset + fo).toInt() and 0xFF)
        }

        return WatchfaceConfigFields(
            timeStyleId = timeStyleId,
            complicationLayoutId = complicationLayoutId,
            backgroundStyleId = backgroundStyleId,
            accentColor = accentColor,
            complicationIds = complicationIds,
            fontfaceId = fontfaceId
        )
    }

    internal suspend fun readWatchFaceConfigFields(
        identifier: String,
        listener: BleDeviceListener?,
        handleError: (Throwable) -> Exception
    ): WatchfaceConfigFields {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "readWatchFaceConfigFields: GET /SYS/KVTX")
        val requestBuilder = PftpRequest.PbPFtpOperation.newBuilder().apply {
            command = PftpRequest.PbPFtpOperation.Command.GET
            path = KVTX_FILE_PATH
        }
        val kvtxScript = try {
            client.request(requestBuilder.build().toByteArray()).toByteArray()
        } catch (throwable: Throwable) {
            BleLogger.e(TAG, "readWatchFaceConfigFields: GET failed: ${throwable.message}")
            throw handleError(throwable)
        }
        BleLogger.d(TAG, "readWatchFaceConfigFields: received ${kvtxScript.size} bytes")
        val flatBufferBytes = extractWatchFaceConfigFromKvtxScript(kvtxScript)
        if (flatBufferBytes == null) {
            BleLogger.w(TAG, "readWatchFaceConfigFields: key not found, returning all-defaults")
            return WatchfaceConfigFields()
        }
        return parseWatchFaceConfigFlatBuffer(flatBufferBytes)
    }

    internal suspend fun writeWatchFaceComplicationInts(
        identifier: String,
        int32Ids: List<Int>,
        listener: BleDeviceListener?,
        handleError: (Throwable) -> Exception
    ) {
        BleLogger.d(TAG, "writeWatchFaceComplicationInts: reading current config before write")
        val existingFields = readWatchFaceConfigFields(identifier, listener, handleError)
        BleLogger.d(TAG, "writeWatchFaceComplicationInts: existing=$existingFields")

        val mergedFields = existingFields.copy(complicationIds = int32Ids)
        BleLogger.d(TAG, "writeWatchFaceComplicationInts: merged=$mergedFields")

        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val kvtxScript = buildKvtxScript(mergedFields)
        BleLogger.d(TAG, "writeWatchFaceComplicationInts: PUT ${kvtxScript.size} bytes to $KVTX_FILE_PATH")
        val builder = PftpRequest.PbPFtpOperation.newBuilder().apply {
            command = PftpRequest.PbPFtpOperation.Command.PUT
            path = KVTX_FILE_PATH
        }
        client.write(builder.build().toByteArray(), ByteArrayInputStream(kvtxScript)).collect {}
    }
}
