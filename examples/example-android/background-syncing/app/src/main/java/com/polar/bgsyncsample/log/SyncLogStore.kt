// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.log

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Persistent log store for sync events. Writes log entries to a file so they survive
 * process kills and app relaunches. Entries are retained for 30 days.
 *
 * Thread-safe — can be called from any thread (sync callbacks, ViewModel, service).
 */
class SyncLogStore private constructor(private val context: Context) {

    data class DisplayLogEntry(
        val date: String,
        val time: String,
        val deviceId: String,
        val message: String
    )

    companion object {
        private const val TAG = "SyncLogStore"
        private const val LOG_FILE_NAME = "sync_log.txt"
        private const val MAX_ENTRIES = 5000
        private const val RETENTION_DAYS = 30L
        private const val SEPARATOR = "|"

        @Volatile
        private var instance: SyncLogStore? = null

        fun getInstance(context: Context): SyncLogStore {
            return instance ?: synchronized(this) {
                instance ?: SyncLogStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val logFile = File(context.filesDir, LOG_FILE_NAME)
    private val entries = CopyOnWriteArrayList<LogEntry>()

    init {
        loadFromDisk()
    }

    data class LogEntry(
        val timestampMillis: Long,
        val message: String
    )

    /**
     * Add a log entry. Persists immediately to disk.
     */
    @Synchronized
    fun addEntry(message: String) {
        val entry = LogEntry(
            timestampMillis = System.currentTimeMillis(),
            message = message
        )
        entries.add(0, entry)

        // Trim to max
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.size - 1)
        }

        appendToDisk(entry)
    }

    /**
     * Get all log entries as structured rows (newest first).
     */
    fun getDisplayLogEntries(): List<DisplayLogEntry> {
        return entries.mapNotNull { entry ->
            if (entry.message.startsWith("PLOG|")) {
                formatPlogToDisplayEntry(entry.message)
            } else if (entry.message.startsWith("POLARBAT|")) {
                null // Suppress raw diagnostic lines
            } else if (shouldSuppressDiagnosticLine(entry.message)) {
                null
            } else {
                val dt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(entry.timestampMillis),
                    ZoneId.systemDefault()
                )
                DisplayLogEntry(
                    date = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    time = dt.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    deviceId = "-",
                    message = entry.message
                )
            }
        }.filter { it.message.isNotBlank() }
    }

    /**
     * Get all log entries as display strings (newest first) for sharing/export.
     */
    fun getDisplayEntries(): List<String> {
        return getDisplayLogEntries().map { entry ->
            val deviceSegment = if (entry.deviceId == "-") "" else " [${entry.deviceId}]"
            "[${entry.date} ${entry.time}]$deviceSegment ${entry.message}"
        }
    }

    /**
     * Get raw messages for all entries (newest first), including PLOG lines.
     * Used by the graph parser which needs PLOG structured data.
     */
    fun getRawMessages(): List<String> {
        return entries.map { it.message }
    }

    /**
     * Get raw messages for a specific device (newest first), including PLOG lines.
     * Filters by deviceId from PLOG lines: PLOG|timestamp|deviceId|event|params
     */
    fun getRawMessagesForDevice(deviceId: String): List<String> {
        return entries.mapNotNull { entry ->
            if (!entry.message.startsWith("PLOG|")) {
                return@mapNotNull entry.message  // Keep non-PLOG entries
            }
            val parts = entry.message.split("|", limit = 5)
            if (parts.size < 3) return@mapNotNull null
            if (parts[2] != deviceId) return@mapNotNull null
            entry.message
        }
    }

    private fun formatPlogToDisplayEntry(message: String): DisplayLogEntry? {
        // PLOG|yyyy-MM-dd HH:mm:ss.SSS|deviceId|EVENT|params
        val parts = message.split("|", limit = 5)
        if (parts.size < 4) return null
        val ts = parts[1]
        val deviceId = parts[2]
        val event = parts[3]
        val params = if (parts.size > 4) parts[4] else ""

        // "yyyy-MM-dd HH:mm:ss" from "yyyy-MM-dd HH:mm:ss.SSS"
        val dateTime = ts.substringBefore(".")

        fun p(key: String): String {
            if (key == "msg") {
                val idx = params.indexOf("msg=")
                return if (idx >= 0) params.substring(idx + 4) else ""
            }
            return Regex("$key=([^ ,]+)").find(params)?.groupValues?.getOrElse(1) { "" } ?: ""
        }

        val text: String? = when {
            event == "SYNC_START" -> "Sync started"
            event == "SYNC_END" -> "Sync complete"
            event == "SYNC_OFFLINE_START" -> "📼 Offline recordings"
            event == "SYNC_OFFLINE_END" -> null // Summary covers it
            event.startsWith("SYNC_OFFLINE_") && event.endsWith("_START") -> {
                val type = event.removePrefix("SYNC_OFFLINE_").removeSuffix("_START")
                "↳ $type…"
            }
            event.startsWith("SYNC_OFFLINE_") && event.endsWith("_END") -> {
                val type = event.removePrefix("SYNC_OFFLINE_").removeSuffix("_END")
                val items = p("items")
                "✓ $type ($items items)"
            }
            event.startsWith("SYNC_") && event.endsWith("_START") -> {
                val type = event.removePrefix("SYNC_").removeSuffix("_START")
                "↳ $type…"
            }
            event.startsWith("SYNC_") && event.endsWith("_END") -> {
                val type = event.removePrefix("SYNC_").removeSuffix("_END")
                val items = p("items")
                "✓ $type ($items items)"
            }
            event == "SYNC_SUMMARY" -> buildSummaryText(params)
            event == "SYNC_ERROR" -> "⚠ Error (${p("type")}): ${p("msg")}"
            event == "CLEANUP_START" -> "Cleaning device…"
            event == "CLEANUP_END" -> "Cleanup done (${p("deleted")} types)"
            event == "BAT" -> {
                val phone = p("phone")
                val phoneCharging = p("phone_charging")
                val device = p("device")
                val deviceCharging = p("device_charging")
                "Battery\nDevice: ${device.ifBlank { "?" }}%($deviceCharging)\nPhone: ${phone.ifBlank { "?" }}%($phoneCharging)"
            }
            event == "CONNECTION" -> "Connection: ${p("state")}"
            event == "NOTIFICATION_POSTED" -> "🔔 ${p("title")} — ${p("msg")}"
            else -> null
        }
        if (text == null) return null
        val partsDt = dateTime.split(" ", limit = 2)
        val date = partsDt.getOrElse(0) { "" }
        val time = partsDt.getOrElse(1) { "" }
        return DisplayLogEntry(
            date = date,
            time = time,
            deviceId = deviceId,
            message = text
        )
    }

    private fun buildSummaryText(params: String): String {
        fun p(key: String) = Regex("$key=([^ ,]+)").find(params)?.groupValues?.getOrElse(1) { "" } ?: ""
        val items = p("items"); val bytes = formatBytes(p("bytes").toLongOrNull() ?: 0)
        val duration = p("duration"); val breakdown = p("breakdown")
        val sb = StringBuilder("── Sync Summary ──")
        sb.append("\nDuration: ${duration}s   Total: $items items, $bytes")
        if (breakdown.isNotEmpty()) {
            breakdown.split(",").forEach { entry ->
                val seg = entry.split(":"); if (seg.size < 2) return@forEach
                val typeBytes = formatBytes(seg.getOrElse(2) { "0" }.toLongOrNull() ?: 0)
                sb.append("\n${seg[0]}: ${seg[1]} items, $typeBytes")
            }
        }
        return sb.toString()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    private fun shouldSuppressDiagnosticLine(message: String): Boolean {
        return Regex("^SYNC\\s*\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3}]\\s*(\\[[^\\]]+])?\\s*PHASE:.*$")
            .matches(message)
    }

    /**
     * Get entry count.
     */
    fun size(): Int = entries.size

    /**
     * Clear all log entries and delete the file.
     */
    @Synchronized
    fun clear() {
        entries.clear()
        try {
            logFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete log file", e)
        }
    }

    private fun loadFromDisk() {
        if (!logFile.exists()) return

        try {
            val cutoffMillis = System.currentTimeMillis() - (RETENTION_DAYS * 24 * 60 * 60 * 1000)
            val lines = logFile.readLines()
            val loaded = mutableListOf<LogEntry>()

            for (line in lines) {
                if (line.isBlank()) continue
                val separatorIdx = line.indexOf(SEPARATOR)
                if (separatorIdx < 0) continue

                val timestamp = line.substring(0, separatorIdx).toLongOrNull() ?: continue
                if (timestamp < cutoffMillis) continue // Skip expired entries

                val message = line.substring(separatorIdx + 1).unescapeNewlines()
                loaded.add(LogEntry(timestamp, message))
            }

            // Sort newest first
            loaded.sortByDescending { it.timestampMillis }

            // Trim to max
            val trimmed = loaded.take(MAX_ENTRIES)

            entries.clear()
            entries.addAll(trimmed)

            // Rewrite file if we pruned expired entries
            if (loaded.size != lines.count { it.isNotBlank() }) {
                rewriteDisk()
            }

            Log.d(TAG, "Loaded ${entries.size} log entries from disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load log file", e)
        }
    }

    private fun appendToDisk(entry: LogEntry) {
        try {
            // Prepend to file (newest first on disk too)
            val existingContent = if (logFile.exists()) logFile.readText() else ""
            val newLine = "${entry.timestampMillis}${SEPARATOR}${entry.message.escapeNewlines()}\n"
            logFile.writeText(newLine + existingContent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to append to log file", e)
        }
    }

    private fun rewriteDisk() {
        try {
            val content = entries.joinToString("\n") { "${it.timestampMillis}${SEPARATOR}${it.message.escapeNewlines()}" }
            logFile.writeText(content + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to rewrite log file", e)
        }
    }

    /** Escape newlines so each log entry is a single line on disk. */
    private fun String.escapeNewlines(): String =
        replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

    /** Unescape newlines when reading back from disk. */
    private fun String.unescapeNewlines(): String =
        replace("\\r", "\r").replace("\\n", "\n").replace("\\\\", "\\")
}
