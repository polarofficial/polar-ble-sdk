// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import android.content.SharedPreferences
import android.util.Log
import com.polar.polarbgsync.api.SyncableDataType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tracks sync state to avoid re-fetching data that has already been synced
 * and is finalized (past days). Current-day data is always re-fetched.
 *
 * For date-range data types (steps, sleep, etc.): tracks a "watermark" date —
 * the latest finalized date for which data has been successfully synced.
 * All dates up to and including the watermark are considered synced.
 *
 * For reference-based data types (training sessions, offline recordings):
 * tracks the set of device filesystem paths that have already been synced.
 */
internal class SyncStateTracker(private val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "SyncStateTracker"
        private const val WATERMARK_PREFIX = "sync_watermark_"
        private const val SYNCED_PATHS_PREFIX = "sync_paths_"
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
    }

    // ── Date-watermark methods ──────────────────────────────────────

    /**
     * Get the sync watermark for a device/dataType.
     * All data up to and including this date is considered synced and finalized.
     * Returns null if no data has been synced yet for this combination.
     */
    fun getWatermark(deviceId: String, dataType: SyncableDataType): LocalDate? {
        val key = "${WATERMARK_PREFIX}${deviceId}_${dataType.name}"
        val dateStr = prefs.getString(key, null) ?: return null
        return try {
            LocalDate.parse(dateStr, DATE_FORMAT)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Advance the watermark after a successful sync.
     * The watermark is capped at yesterday — never set to today,
     * because today's data may still change.
     * Only advances forward, never backwards.
     */
    fun advanceWatermark(deviceId: String, dataType: SyncableDataType, syncedUpTo: LocalDate) {
        val yesterday = LocalDate.now().minusDays(1)
        val newWatermark = if (syncedUpTo.isAfter(yesterday)) yesterday else syncedUpTo

        val currentWatermark = getWatermark(deviceId, dataType)
        if (currentWatermark != null && !newWatermark.isAfter(currentWatermark)) return

        val key = "${WATERMARK_PREFIX}${deviceId}_${dataType.name}"
        prefs.edit().putString(key, newWatermark.format(DATE_FORMAT)).apply()
        Log.d(TAG, "Watermark advanced: $deviceId/$dataType → $newWatermark")
    }

    /**
     * Reset the watermark (e.g. when local data is purged).
     * Pass null to clear completely.
     */
    fun resetWatermark(deviceId: String, dataType: SyncableDataType, toDate: LocalDate?) {
        val key = "${WATERMARK_PREFIX}${deviceId}_${dataType.name}"
        if (toDate != null) {
            prefs.edit().putString(key, toDate.format(DATE_FORMAT)).apply()
            Log.d(TAG, "Watermark reset: $deviceId/$dataType → $toDate")
        } else {
            prefs.edit().remove(key).apply()
            Log.d(TAG, "Watermark cleared: $deviceId/$dataType")
        }
    }

    // ── Path-based tracking (training sessions, offline recordings) ──

    /**
     * Check if a specific device path has already been synced.
     */
    fun isPathSynced(deviceId: String, dataType: SyncableDataType, path: String): Boolean {
        return getSyncedPaths(deviceId, dataType).contains(path)
    }

    /**
     * Mark a device path as synced.
     */
    fun markPathSynced(deviceId: String, dataType: SyncableDataType, path: String) {
        val paths = getSyncedPaths(deviceId, dataType).toMutableSet()
        paths.add(path)
        saveSyncedPaths(deviceId, dataType, paths)
        Log.d(TAG, "Path synced: $deviceId/$dataType → $path")
    }

    /**
     * Remove a synced path entry.
     */
    fun clearPath(deviceId: String, dataType: SyncableDataType, path: String) {
        val paths = getSyncedPaths(deviceId, dataType).toMutableSet()
        if (paths.remove(path)) {
            saveSyncedPaths(deviceId, dataType, paths)
        }
    }

    /**
     * Clear all synced paths for a device/dataType.
     */
    fun clearPaths(deviceId: String, dataType: SyncableDataType) {
        val key = "${SYNCED_PATHS_PREFIX}${deviceId}_${dataType.name}"
        prefs.edit().remove(key).apply()
    }

    // ── Bulk cleanup ────────────────────────────────────────────────

    /**
     * Clear all sync state (watermarks + paths) for a device.
     * Called when a device is removed or all its local data is deleted.
     */
    fun clearAllSyncState(deviceId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter {
            it.startsWith("${WATERMARK_PREFIX}${deviceId}_") ||
                    it.startsWith("${SYNCED_PATHS_PREFIX}${deviceId}_")
        }.forEach { editor.remove(it) }
        editor.apply()
        Log.d(TAG, "Cleared all sync state for $deviceId")
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun getSyncedPaths(deviceId: String, dataType: SyncableDataType): Set<String> {
        val key = "${SYNCED_PATHS_PREFIX}${deviceId}_${dataType.name}"
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    private fun saveSyncedPaths(deviceId: String, dataType: SyncableDataType, paths: Set<String>) {
        val key = "${SYNCED_PATHS_PREFIX}${deviceId}_${dataType.name}"
        prefs.edit().putStringSet(key, paths).apply()
    }
}
