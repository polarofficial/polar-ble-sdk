// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.api

import java.time.LocalDate

/**
 * API for reading synced data from local storage and managing local data retention.
 *
 * Synced data is stored on the filesystem in a structured directory layout:
 * ```
 * <app-files>/polar-sync/<deviceId>/<dataType>/<YYYYMMDD>.json
 * ```
 *
 * Usage:
 * ```
 * val localApi = PolarBackgroundSync.localDataApi(context)
 * val entries = localApi.getSyncedData("B5F8B5C3", SyncableDataType.STEPS,
 *     fromDate = LocalDate.now().minusDays(7), toDate = LocalDate.now())
 *
 * // Auto-delete local data older than 30 days
 * localApi.setLocalRetention(
 *     LocalRetentionConfig(
 *         dataTypes = SyncableDataType.entries.toSet(),
 *         maxAgeDays = 30
 *     )
 * )
 * ```
 */
interface LocalDataApi {

    /**
     * Get all locally synced data entries for a device and data type within a date range.
     *
     * @param deviceId Polar device ID
     * @param dataType Type of data to retrieve
     * @param fromDate Start date (inclusive), null for earliest available
     * @param toDate End date (inclusive), null for latest available
     * @return List of synced data entries, ordered by date ascending
     */
    fun getSyncedData(
        deviceId: String,
        dataType: SyncableDataType,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null
    ): List<SyncedDataEntry>

    /**
     * Read the raw JSON content of a synced data file.
     *
     * @param entry The synced data entry to read
     * @return JSON string content, or null if the file no longer exists
     */
    fun readSyncedData(entry: SyncedDataEntry): String?

    /**
     * Get the root directory path for a device's synced data.
     *
     * @param deviceId Polar device ID
     * @return Absolute path to the device's sync data directory
     */
    fun getDeviceDataPath(deviceId: String): String

    /**
     * Configure automatic local data retention.
     * Data files older than the specified age will be periodically purged.
     *
     * @param config Retention configuration, or null to disable auto-purge
     */
    fun setLocalRetention(config: LocalRetentionConfig?)

    /**
     * Get the current local retention configuration.
     */
    fun getLocalRetention(): LocalRetentionConfig?

    /**
     * Manually trigger a local data purge based on the current retention config.
     * @return Number of files deleted
     */
    fun purgeExpiredData(): Int

    /**
     * Delete all locally synced data for a device.
     */
    fun deleteAllDeviceData(deviceId: String)
}

/**
 * Configuration for automatic local data retention.
 */
data class LocalRetentionConfig(
    /** Data types to apply retention to. */
    val dataTypes: Set<SyncableDataType>,
    /** Maximum age in days. Files older than this are eligible for deletion. */
    val maxAgeDays: Int
)
