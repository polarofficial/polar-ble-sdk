// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.ui.graph

import java.time.LocalDateTime
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Represents parsed sync log data for a single device.
 * Extracted from structured PLOG lines in the sync log.
 *
 * PLOG line format:  PLOG|yyyy-MM-dd HH:mm:ss.SSS|<deviceId>|<EVENT>|<params>
 * Events: SYNC_START, SYNC_END, DATA, CLEANUP_START, CLEANUP_END, BAT
 */
data class LogGraphData(
    val deviceId: String,
    val batteryReadings: List<BatteryReading>,
    val syncPhases: List<SyncPhase>,                    // Overall sync bars (cyan)
    val activityTypePhases: List<DataTypePhase>,       // Individual activity types (color-coded)
    val recordingPhases: List<SyncPhase>,              // Overall offline recording (orange)
    val recordingTypePhases: List<DataTypePhase>,      // Individual recording types (color-coded)
    val cleanupPhases: List<CleanupPhase>,             // Cleanup phases (red/pink)
    val dataSyncedEvents: List<DataSyncedEvent>
) {
    companion object {
        private val PLOG_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        private fun coalesceBatteryReadings(readings: List<BatteryReading>): List<BatteryReading> {
            if (readings.isEmpty()) return readings

            fun isUnknown(state: String): Boolean = state.isBlank() || state.equals("UNKNOWN", ignoreCase = true)
            fun preferredState(old: String, newer: String): String {
                val oldUnknown = isUnknown(old)
                val newUnknown = isUnknown(newer)
                return when {
                    oldUnknown && !newUnknown -> newer
                    !oldUnknown && newUnknown -> old
                    else -> newer
                }
            }

            val out = mutableListOf<BatteryReading>()
            val mergeWindowMillis = 1000L

            for (reading in readings) {
                val last = out.lastOrNull()
                if (last == null) {
                    out.add(reading)
                    continue
                }

                val dtMillis = kotlin.math.abs(java.time.Duration.between(last.timestamp, reading.timestamp).toMillis())
                val samePhone = last.phoneBatteryPercent == reading.phoneBatteryPercent
                val compatibleDevice =
                    last.deviceBatteryPercent == reading.deviceBatteryPercent ||
                        last.deviceBatteryPercent == null ||
                        reading.deviceBatteryPercent == null

                if (dtMillis <= mergeWindowMillis && samePhone && compatibleDevice) {
                    out[out.lastIndex] = BatteryReading(
                        timestamp = reading.timestamp,
                        phoneBatteryPercent = reading.phoneBatteryPercent,
                        phoneChargingState = preferredState(last.phoneChargingState, reading.phoneChargingState),
                        deviceBatteryPercent = reading.deviceBatteryPercent ?: last.deviceBatteryPercent,
                        deviceChargingState = preferredState(last.deviceChargingState, reading.deviceChargingState)
                    )
                } else {
                    out.add(reading)
                }
            }

            return out
        }

        fun parse(deviceId: String, logLines: List<String>): LogGraphData {
            val plogBatteryReadings = mutableListOf<BatteryReading>()
            val polarBatReadings = mutableListOf<BatteryReading>()
            val syncPhases = mutableListOf<SyncPhase>()
            val activityTypePhases = mutableListOf<DataTypePhase>()
            val recordingPhases = mutableListOf<SyncPhase>()
            val recordingTypePhases = mutableListOf<DataTypePhase>()
            val cleanupPhases = mutableListOf<CleanupPhase>()
            val dataSyncedEvents = mutableListOf<DataSyncedEvent>()

            // Track sync/cleanup start times PER DEVICE to handle concurrent syncs
            val syncStartTimes = mutableMapOf<String, LocalDateTime>()
            val cleanupStartTimes = mutableMapOf<String, LocalDateTime>()
            val recordingStartTimes = mutableMapOf<String, LocalDateTime>()
            val activityTypeStartTimes = mutableMapOf<String, LocalDateTime>()  // Map<"deviceId:TYPE", startTime>
            val recordingTypeStartTimes = mutableMapOf<String, LocalDateTime>()  // Map<"deviceId:TYPE", startTime>
            
            // CRITICAL: Sort lines by structured timestamp (PLOG or POLARBAT), not write time.
            val sortedLines = logLines.sortedBy { line ->
                val plogIdx = line.indexOf("PLOG|")
                if (plogIdx >= 0) {
                    val plog = line.substring(plogIdx)
                    val parts = plog.split("|", limit = 5)
                    if (parts.size < 2) return@sortedBy Long.MAX_VALUE
                    return@sortedBy try {
                        LocalDateTime.parse(parts[1], PLOG_TS_FORMATTER).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        Long.MAX_VALUE
                    }
                }

                val polarBatIdx = line.indexOf("POLARBAT|")
                if (polarBatIdx >= 0) {
                    val polarBat = line.substring(polarBatIdx)
                    val parts = polarBat.split("|")
                    if (parts.size < 2) return@sortedBy Long.MAX_VALUE
                    return@sortedBy try {
                        Instant.parse(parts[1]).toEpochMilli()
                    } catch (_: Exception) {
                        Long.MAX_VALUE
                    }
                }

                Long.MAX_VALUE
            }
            
            android.util.Log.d("LogGraphData", "Starting parse for device=$deviceId with ${logLines.size} lines (sorted by timestamp)")

            for (line in sortedLines) {
                // Parse POLARBAT diagnostics (fallback source for older logs without PLOG BAT).
                val polarBatIdx = line.indexOf("POLARBAT|")
                if (polarBatIdx >= 0) {
                    val bat = line.substring(polarBatIdx)
                    val parts = bat.split("|")
                    if (parts.size >= 8) {
                        val lineDeviceId = parts.getOrNull(2)?.substringAfter("device=") ?: ""
                        if (lineDeviceId == deviceId) {
                            val tsFromBat = try {
                                Instant.parse(parts[1]).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                            } catch (_: Exception) {
                                null
                            }
                            if (tsFromBat != null) {
                                val map = parts.drop(4).mapNotNull { token ->
                                    val idx = token.indexOf('=')
                                    if (idx <= 0) null else token.substring(0, idx) to token.substring(idx + 1)
                                }.toMap()

                                val phoneBat = map["phone_bat"]?.removeSuffix("%")?.toIntOrNull()
                                val phoneCharging = map["phone_charging"] ?: "UNKNOWN"
                                val deviceBat = map["device_bat"]?.removeSuffix("%")?.toIntOrNull()
                                val deviceCharging = map["device_charging"] ?: "UNKNOWN"

                                if (phoneBat != null) {
                                    polarBatReadings.add(BatteryReading(tsFromBat, phoneBat, phoneCharging, deviceBat, deviceCharging))
                                }
                            }
                        }
                    }
                }

                // Find the PLOG| marker - works whether line is raw or has a display prefix
                val plogIdx = line.indexOf("PLOG|")
                if (plogIdx < 0) continue

                val plog = line.substring(plogIdx)
                val parts = plog.split("|", limit = 5)
                // parts[0]=PLOG, parts[1]=timestamp, parts[2]=deviceId, parts[3]=event, parts[4]=params
                if (parts.size < 4) continue

                val lineDeviceId = parts[2]
                if (lineDeviceId != deviceId) continue

                val ts = try {
                    LocalDateTime.parse(parts[1], PLOG_TS_FORMATTER)
                } catch (_: Exception) { continue }

                val event = parts[3]
                val params = if (parts.size > 4) parts[4] else ""

                when {
                    event == "SYNC_START" -> {
                        syncStartTimes[deviceId] = ts
                    }
                    event == "SYNC_END" -> {
                        syncStartTimes[deviceId]?.let { startTime ->
                            val bytes = params.substringAfter("bytes=").substringBefore(" ").toLongOrNull()
                            syncPhases.add(SyncPhase(startTime, ts, deviceId, bytes))
                        }
                        syncStartTimes.remove(deviceId)
                    }
                    // Individual activity type: SYNC_<TYPE>_START
                    event.startsWith("SYNC_") && !event.startsWith("SYNC_OFFLINE_") && event.endsWith("_START") && 
                    !event.endsWith("_SUMMARY") -> {
                        val dataType = event.removePrefix("SYNC_").removeSuffix("_START")
                        activityTypeStartTimes["$deviceId:$dataType"] = ts
                    }
                    // Individual activity type: SYNC_<TYPE>_END with items
                    event.startsWith("SYNC_") && !event.startsWith("SYNC_OFFLINE_") && event.endsWith("_END") && 
                    params.contains("items=") && !event.endsWith("_SUMMARY") -> {
                        val dataType = event.removePrefix("SYNC_").removeSuffix("_END")
                        val itemCount = params.substringAfter("items=").toIntOrNull() ?: 0
                        // Show all synced types (even if 0 items) to visualize time spent on each type
                        activityTypeStartTimes["$deviceId:$dataType"]?.let { startTime ->
                            val bytes = params.substringAfter("bytes=").substringBefore(" ").toLongOrNull()
                            activityTypePhases.add(DataTypePhase(startTime, ts, dataType, "ACTIVITY", deviceId, bytes))
                            android.util.Log.d("LogGraphData", "✓ Added activity type phase: $dataType ($itemCount items)")
                        } ?: run {
                            android.util.Log.w("LogGraphData", "⚠ No start time found for activity type: $dataType")
                        }
                        activityTypeStartTimes.remove("$deviceId:$dataType")
                    }
                    // Overall recording start
                    event == "SYNC_OFFLINE_START" -> {
                        recordingStartTimes[deviceId] = ts
                    }
                    // Overall recording end
                    event == "SYNC_OFFLINE_END" -> {
                        recordingStartTimes[deviceId]?.let { startTime ->
                            val bytes = params.substringAfter("bytes=").substringBefore(" ").toLongOrNull()
                            recordingPhases.add(SyncPhase(startTime, ts, deviceId, bytes))
                        }
                        recordingStartTimes.remove(deviceId)
                    }
                    // Individual recording type: SYNC_OFFLINE_<TYPE>_START
                    event.startsWith("SYNC_OFFLINE_") && event.endsWith("_START") && !event.endsWith("OFFLINE_START") -> {
                        val dataType = event.removePrefix("SYNC_OFFLINE_").removeSuffix("_START")
                        recordingTypeStartTimes["$deviceId:$dataType"] = ts
                        android.util.Log.d("LogGraphData", "→ Recording type start: $dataType")
                    }
                    // Individual recording type: SYNC_OFFLINE_<TYPE>_END with items
                    event.startsWith("SYNC_OFFLINE_") && event.endsWith("_END") && params.contains("items=") && 
                    !event.endsWith("OFFLINE_END") -> {
                        val dataType = event.removePrefix("SYNC_OFFLINE_").removeSuffix("_END")
                        val itemCount = params.substringAfter("items=").toIntOrNull() ?: 0
                        // Show all synced types (even if 0 items) to visualize time spent on each type
                        recordingTypeStartTimes["$deviceId:$dataType"]?.let { startTime ->
                            val bytes = params.substringAfter("bytes=").substringBefore(" ").toLongOrNull()
                            recordingTypePhases.add(DataTypePhase(startTime, ts, dataType, "OFFLINE", deviceId, bytes))
                            android.util.Log.d("LogGraphData", "✓ Added recording type phase: $dataType ($itemCount items)")
                        } ?: run {
                            android.util.Log.w("LogGraphData", "⚠ No start time found for recording type: $dataType")
                        }
                        recordingTypeStartTimes.remove("$deviceId:$dataType")
                    }
                    // Data synced event
                    event.startsWith("SYNC_") && event.endsWith("_END") && params.contains("items=") -> {
                        val dataType = event
                            .removePrefix("SYNC_")
                            .removePrefix("OFFLINE_")
                            .removeSuffix("_END")
                            .ifBlank { "UNKNOWN" }
                        dataSyncedEvents.add(DataSyncedEvent(ts, dataType))
                    }
                    // Old PLOG format fallback: DATA|type=...
                    event == "DATA" -> {
                        val dataType = params.substringAfter("type=").substringBefore(" ").ifBlank { "UNKNOWN" }
                        dataSyncedEvents.add(DataSyncedEvent(ts, dataType))
                    }
                    event == "CLEANUP_START" -> {
                        cleanupStartTimes[deviceId] = ts
                    }
                    event == "CLEANUP_END" -> {
                        cleanupStartTimes[deviceId]?.let { startTime ->
                            val bytes = params.substringAfter("bytes=").substringBefore(" ").toLongOrNull()
                            cleanupPhases.add(CleanupPhase(startTime, ts, deviceId, bytes))
                        }
                        cleanupStartTimes.remove(deviceId)
                    }
                    event == "BAT" -> {
                        val phoneBat = params.substringAfter("phone=").substringBefore(" ").toIntOrNull() ?: continue
                        val phoneCharging = params.substringAfter("phone_charging=").substringBefore(" ")
                        val deviceBat = params.substringAfter("device=").substringBefore(" ").toIntOrNull()
                        val deviceCharging = params.substringAfter("device_charging=").substringBefore(" ")
                        plogBatteryReadings.add(BatteryReading(ts, phoneBat, phoneCharging, deviceBat, deviceCharging))
                    }
                }

            }

            val selectedReadings = if (plogBatteryReadings.isNotEmpty()) plogBatteryReadings else polarBatReadings
            val mergedBatteryReadings = coalesceBatteryReadings(selectedReadings.sortedBy { it.timestamp })

            return LogGraphData(
                deviceId = deviceId,
                batteryReadings = mergedBatteryReadings,
                syncPhases = syncPhases,
                activityTypePhases = activityTypePhases,
                recordingPhases = recordingPhases,
                recordingTypePhases = recordingTypePhases,
                cleanupPhases = cleanupPhases,
                dataSyncedEvents = dataSyncedEvents
            ).also {
                android.util.Log.d("LogGraphData", "Parse complete for $deviceId: " +
                    "${it.syncPhases.size} sync, ${it.activityTypePhases.size} activity types, " +
                    "${it.recordingPhases.size} recording, ${it.recordingTypePhases.size} recording types, " +
                    "${it.cleanupPhases.size} cleanup")
            }
        }
    }
}

data class BatteryReading(
    val timestamp: LocalDateTime,
    val phoneBatteryPercent: Int,
    val phoneChargingState: String = "UNKNOWN",
    val deviceBatteryPercent: Int? = null,
    val deviceChargingState: String = "UNKNOWN"
)

data class SyncPhase(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val deviceId: String = "",
    val transferredBytes: Long? = null
) {
    val durationMs: Long get() = java.time.Duration.between(startTime, endTime).toMillis()
}

data class DataTypePhase(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val dataType: String,
    val category: String,  // "ACTIVITY" or "OFFLINE"
    val deviceId: String = "",
    val transferredBytes: Long? = null
) {
    val durationMs: Long get() = java.time.Duration.between(startTime, endTime).toMillis()
}

data class CleanupPhase(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val deviceId: String = "",
    val transferredBytes: Long? = null
) {
    val durationMs: Long get() = java.time.Duration.between(startTime, endTime).toMillis()
}

data class DataSyncedEvent(
    val timestamp: LocalDateTime,
    val dataType: String
)
