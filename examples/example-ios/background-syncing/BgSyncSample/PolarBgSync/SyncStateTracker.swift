// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import os.log

/// Tracks sync state to avoid re-fetching data that has already been synced
/// and is finalized (past days). Current-day data is always re-fetched.
///
/// For date-range data types (steps, sleep, etc.): tracks a "watermark" date —
/// the latest finalized date for which data has been successfully synced.
/// All dates up to and including the watermark are considered synced.
///
/// For reference-based data types (training sessions, offline recordings):
/// tracks the set of device filesystem paths that have already been synced.
class SyncStateTracker {

    private static let logger = Logger(subsystem: "com.polar.bgsync", category: "SyncStateTracker")
    private static let watermarkPrefix = "sync_watermark_"
    private static let syncedPathsPrefix = "sync_paths_"

    private let defaults = UserDefaults.standard
    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    // MARK: - Date-watermark methods

    /// Get the sync watermark for a device/dataType.
    /// All data up to and including this date is considered synced and finalized.
    func getWatermark(deviceId: String, dataType: SyncableDataType) -> Date? {
        let key = "\(Self.watermarkPrefix)\(deviceId)_\(dataType.rawValue)"
        guard let dateStr = defaults.string(forKey: key) else { return nil }
        return dateFormatter.date(from: dateStr)
    }

    /// Advance the watermark after a successful sync.
    /// The watermark is capped at yesterday — never set to today,
    /// because today's data may still change.
    /// Only advances forward, never backwards.
    func advanceWatermark(deviceId: String, dataType: SyncableDataType, syncedUpTo: Date) {
        let calendar = Calendar.current
        let yesterday = calendar.date(byAdding: .day, value: -1, to: calendar.startOfDay(for: Date()))!
        let newWatermark = syncedUpTo > yesterday ? yesterday : calendar.startOfDay(for: syncedUpTo)

        if let current = getWatermark(deviceId: deviceId, dataType: dataType),
           newWatermark <= current {
            return
        }

        let key = "\(Self.watermarkPrefix)\(deviceId)_\(dataType.rawValue)"
        defaults.set(dateFormatter.string(from: newWatermark), forKey: key)
        Self.logger.debug("Watermark advanced: \(deviceId)/\(dataType.rawValue) → \(self.dateFormatter.string(from: newWatermark))")
    }

    /// Reset the watermark (e.g. when local data is purged).
    /// Pass nil to clear completely.
    func resetWatermark(deviceId: String, dataType: SyncableDataType, toDate: Date?) {
        let key = "\(Self.watermarkPrefix)\(deviceId)_\(dataType.rawValue)"
        if let date = toDate {
            defaults.set(dateFormatter.string(from: date), forKey: key)
            Self.logger.debug("Watermark reset: \(deviceId)/\(dataType.rawValue) → \(self.dateFormatter.string(from: date))")
        } else {
            defaults.removeObject(forKey: key)
            Self.logger.debug("Watermark cleared: \(deviceId)/\(dataType.rawValue)")
        }
    }

    // MARK: - Path-based tracking (training sessions, offline recordings)

    /// Check if a specific device path has already been synced.
    func isPathSynced(deviceId: String, dataType: SyncableDataType, path: String) -> Bool {
        return getSyncedPaths(deviceId: deviceId, dataType: dataType).contains(path)
    }

    /// Mark a device path as synced.
    func markPathSynced(deviceId: String, dataType: SyncableDataType, path: String) {
        var paths = getSyncedPaths(deviceId: deviceId, dataType: dataType)
        paths.insert(path)
        saveSyncedPaths(deviceId: deviceId, dataType: dataType, paths: paths)
        Self.logger.debug("Path synced: \(deviceId)/\(dataType.rawValue) → \(path)")
    }

    /// Remove a synced path entry.
    func clearPath(deviceId: String, dataType: SyncableDataType, path: String) {
        var paths = getSyncedPaths(deviceId: deviceId, dataType: dataType)
        if paths.remove(path) != nil {
            saveSyncedPaths(deviceId: deviceId, dataType: dataType, paths: paths)
        }
    }

    /// Clear all synced paths for a device/dataType.
    func clearPaths(deviceId: String, dataType: SyncableDataType) {
        let key = "\(Self.syncedPathsPrefix)\(deviceId)_\(dataType.rawValue)"
        defaults.removeObject(forKey: key)
    }

    // MARK: - Bulk cleanup

    /// Clear all sync state (watermarks + paths) for a device.
    /// Called when a device is removed or all its local data is deleted.
    func clearAllSyncState(deviceId: String) {
        let allKeys = defaults.dictionaryRepresentation().keys
        for key in allKeys where
            key.hasPrefix("\(Self.watermarkPrefix)\(deviceId)_") ||
            key.hasPrefix("\(Self.syncedPathsPrefix)\(deviceId)_") {
            defaults.removeObject(forKey: key)
        }
        Self.logger.debug("Cleared all sync state for \(deviceId)")
    }

    // MARK: - Private helpers

    private func getSyncedPaths(deviceId: String, dataType: SyncableDataType) -> Set<String> {
        let key = "\(Self.syncedPathsPrefix)\(deviceId)_\(dataType.rawValue)"
        guard let array = defaults.stringArray(forKey: key) else { return [] }
        return Set(array)
    }

    private func saveSyncedPaths(deviceId: String, dataType: SyncableDataType, paths: Set<String>) {
        let key = "\(Self.syncedPathsPrefix)\(deviceId)_\(dataType.rawValue)"
        defaults.set(Array(paths), forKey: key)
    }
}
