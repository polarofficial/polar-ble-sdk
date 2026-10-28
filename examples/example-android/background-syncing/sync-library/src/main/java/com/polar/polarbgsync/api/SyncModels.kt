// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.api

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Configuration for the background sync process.
 */
data class SyncConfig(
    /**
     * When true, data that has been synced locally will be automatically
     * deleted from the device. Date-structured data will not be deleted
     * until it is at least [deviceRetentionDays] old.
     */
    val autoDeleteFromDevice: Boolean = false,

    /**
     * Minimum age in days before date-structured data is eligible for
     * automatic deletion from the device. Default is 2 days.
     * Only applies when [autoDeleteFromDevice] is true.
     */
    val deviceRetentionDays: Int = 2,

    /**
     * Minimum age in days before offline recordings are eligible for
     * deletion from the device after being synced. Default is 0 (delete immediately).
     * Only applies when [autoDeleteFromDevice] is true.
     */
    val offlineRecordingRetentionDays: Int = 0,

    /**
     * Interval in minutes between timed sync cycles.
     * 0 means timed sync is disabled — only device-to-host notifications trigger sync.
     * Valid range: 0–60.
     */
    val syncIntervalMinutes: Int = 0
)

/**
 * Represents a single entry of synced data stored locally.
 */
data class SyncedDataEntry(
    val deviceId: String,
    val dataType: SyncableDataType,
    val date: LocalDate,
    val localFilePath: String,
    val syncedAt: LocalDateTime,
    val sizeBytes: Long
)

/**
 * Connection state of the Polar device.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FTU_CHECK,
    SYNCING
}

/**
 * Represents a sync error.
 */
data class SyncError(
    val dataType: SyncableDataType?,
    val message: String,
    val cause: Throwable? = null
)

/**
 * Result of a sync cycle for a specific data type.
 */
data class SyncResult(
    val deviceId: String,
    val dataType: SyncableDataType,
    val date: LocalDate,
    val localFilePath: String,
    val sizeBytes: Long = 0
)

/**
 * Summary of a completed sync cycle for a device.
 */
data class SyncSummary(
    val deviceId: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val durationSeconds: Long,
    val totalItems: Int,
    val totalBytes: Long,
    val perTypeStats: Map<SyncableDataType, TypeSyncStats>
)

/**
 * Per-data-type sync statistics.
 */
data class TypeSyncStats(
    val items: Int,
    val bytes: Long
)
