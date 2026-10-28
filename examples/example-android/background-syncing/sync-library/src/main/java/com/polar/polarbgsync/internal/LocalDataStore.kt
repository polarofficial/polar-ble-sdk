// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.polar.polarbgsync.api.LocalRetentionConfig
import com.polar.polarbgsync.api.SyncableDataType
import com.polar.polarbgsync.api.SyncedDataEntry
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Manages the local filesystem storage for synced Polar device data.
 *
 * Directory layout:
 * ```
 * <app-files>/polar-sync/<deviceId>/<dataType>/<YYYYMMDD>.json
 * ```
 */
internal class LocalDataStore(
    context: Context,
    private val gson: Gson,
    private val syncStateTracker: SyncStateTracker
) {
    companion object {
        private const val TAG = "LocalDataStore"
        private const val SYNC_DIR = "polar-sync"
        private val DATE_FILE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val DATETIME_FILE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    private val rootDir = File(context.filesDir, SYNC_DIR)

    @Volatile
    private var retentionConfig: LocalRetentionConfig? = null

    init {
        rootDir.mkdirs()
    }

    /**
     * Save JSON data for a device/type/date combination.
     * @return Absolute path of the saved file.
     */
    fun saveData(
        deviceId: String,
        dataType: SyncableDataType,
        date: LocalDate,
        jsonContent: String
    ): String {
        val dir = getTypeDir(deviceId, dataType)
        dir.mkdirs()
        val fileName = "${date.format(DATE_FILE_FORMAT)}.json"
        val file = File(dir, fileName)
        file.writeText(jsonContent)
        Log.d(TAG, "Saved ${file.absolutePath} (${jsonContent.length} chars)")
        return file.absolutePath
    }

    /**
     * Save raw binary data for offline recordings.
     * Uses a unique filename based on date + time to avoid collisions.
     * @return Absolute path of the saved file.
     */
    fun saveRawData(
        deviceId: String,
        dataType: SyncableDataType,
        dateTime: LocalDateTime,
        data: ByteArray
    ): String {
        val dir = getTypeDir(deviceId, dataType)
        dir.mkdirs()
        val fileName = "${dateTime.format(DATETIME_FILE_FORMAT)}.dat"
        val file = File(dir, fileName)
        file.writeBytes(data)
        Log.d(TAG, "Saved raw ${file.absolutePath} (${data.size} bytes)")
        return file.absolutePath
    }

    /**
     * List all synced data entries for a device/type within an optional date range.
     */
    fun listEntries(
        deviceId: String,
        dataType: SyncableDataType,
        fromDate: LocalDate?,
        toDate: LocalDate?
    ): List<SyncedDataEntry> {
        val dir = getTypeDir(deviceId, dataType)
        if (!dir.exists()) {
            Log.d(TAG, "Directory does not exist: ${dir.absolutePath}")
            return emptyList()
        }

        return dir.listFiles()
            ?.filter { it.extension == "json" || it.extension == "dat" }
            ?.also { Log.d(TAG, "Found ${it.size} data files (.json or .dat) in ${dir.absolutePath}") }
            ?.mapNotNull { file ->
                val date = parseDateFromFileName(file.name) ?: return@mapNotNull null
                if (fromDate != null && date.isBefore(fromDate)) return@mapNotNull null
                if (toDate != null && date.isAfter(toDate)) return@mapNotNull null
                SyncedDataEntry(
                    deviceId = deviceId,
                    dataType = dataType,
                    date = date,
                    localFilePath = file.absolutePath,
                    syncedAt = LocalDateTime.now(), // Approximation; could store metadata
                    sizeBytes = file.length()
                )
            }
            ?.sortedBy { it.date }
            ?.also { Log.d(TAG, "After filtering by date: ${it.size} entries") }
            ?: emptyList()
    }

    /**
     * Read the content of a synced data file.
     */
    fun readFile(path: String): String? {
        val file = File(path)
        return if (file.exists()) file.readText() else null
    }

    /**
     * Read the raw bytes of a synced data file.
     */
    fun readRawFile(path: String): ByteArray? {
        val file = File(path)
        return if (file.exists()) file.readBytes() else null
    }

    /**
     * Get the root directory for a device's data.
     */
    fun getDeviceDir(deviceId: String): String {
        return File(rootDir, deviceId).absolutePath
    }

    /**
     * Check if data for a specific device/type/date exists locally.
     */
    fun hasData(deviceId: String, dataType: SyncableDataType, date: LocalDate): Boolean {
        val file = File(getTypeDir(deviceId, dataType), "${date.format(DATE_FILE_FORMAT)}.json")
        return file.exists()
    }

    /**
     * Get all dates that have been synced for a device/type.
     */
    fun getSyncedDates(deviceId: String, dataType: SyncableDataType): Set<LocalDate> {
        val dir = getTypeDir(deviceId, dataType)
        if (!dir.exists()) return emptySet()
        return dir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { parseDateFromFileName(it.name) }
            ?.toSet()
            ?: emptySet()
    }

    fun setRetentionConfig(config: LocalRetentionConfig?) {
        this.retentionConfig = config
    }

    fun getRetentionConfig(): LocalRetentionConfig? = retentionConfig

    /**
     * Purge locally stored files that exceed the retention age.
     * @return Number of files deleted.
     */
    fun purgeExpired(): Int {
        val config = retentionConfig ?: return 0
        val cutoffDate = LocalDate.now().minusDays(config.maxAgeDays.toLong())
        var count = 0

        for (dataType in config.dataTypes) {
            // Iterate all device directories
            rootDir.listFiles()?.forEach { deviceDir ->
                if (!deviceDir.isDirectory) return@forEach
                val deviceId = deviceDir.name
                val typeDir = File(deviceDir, dataType.name)
                if (!typeDir.exists()) return@forEach
                typeDir.listFiles()?.forEach { file ->
                    val date = parseDateFromFileName(file.name)
                    if (date != null && date.isBefore(cutoffDate)) {
                        if (file.delete()) {
                            count++
                            Log.d(TAG, "Purged expired file: ${file.absolutePath}")
                        }
                    }
                }
                // Reset the sync watermark so purged dates can be re-fetched if needed
                // Set watermark to the cutoff date (data before this was deleted)
                val currentWatermark = syncStateTracker.getWatermark(deviceId, dataType)
                if (currentWatermark != null && currentWatermark.isBefore(cutoffDate)) {
                    syncStateTracker.resetWatermark(deviceId, dataType, null)
                }
                // Clear any synced paths for this data type as well
                if (dataType.isOfflineRecording || dataType == SyncableDataType.TRAINING_SESSIONS) {
                    syncStateTracker.clearPaths(deviceId, dataType)
                }
            }
        }

        if (count > 0) Log.i(TAG, "Purged $count expired local files")
        return count
    }

    /**
     * Delete all locally stored data for a device.
     */
    fun deleteDeviceData(deviceId: String) {
        val dir = File(rootDir, deviceId)
        if (dir.exists()) {
            dir.deleteRecursively()
            Log.i(TAG, "Deleted all local data for device $deviceId")
        }
        syncStateTracker.clearAllSyncState(deviceId)
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun getTypeDir(deviceId: String, dataType: SyncableDataType): File {
        return File(rootDir, "$deviceId/${dataType.name}")
    }

    private fun parseDateFromFileName(fileName: String): LocalDate? {
        return try {
            val dateStr = fileName.removeSuffix(".json").removeSuffix(".dat")
            // Handle datetime format (yyyyMMdd_HHmmss) by taking just the date part
            val datePart = if (dateStr.contains('_')) dateStr.substringBefore('_') else dateStr
            LocalDate.parse(datePart, DATE_FILE_FORMAT)
        } catch (e: Exception) {
            null
        }
    }
}
