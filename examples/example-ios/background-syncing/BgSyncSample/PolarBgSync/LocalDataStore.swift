// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import os.log

/// Manages the local filesystem storage for synced Polar device data.
///
/// Directory layout:
/// `<Documents>/polar-sync/<deviceId>/<dataType>/<YYYYMMDD>.json`
class LocalDataStore {

    private static let logger = Logger(subsystem: "com.polar.bgsync", category: "LocalDataStore")
    private static let syncDirName = "polar-sync"

    private let rootDir: URL
    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyyMMdd"
        f.locale = Locale(identifier: "en_US_POSIX")
        // Use local timezone: SDK dates represent local calendar dates (midnight local)
        f.timeZone = TimeZone.current
        return f
    }()

    private let dateTimeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyyMMdd_HHmmss"
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        return f
    }()

    var retentionConfig: LocalRetentionConfig?
    var syncStateTracker: SyncStateTracker?

    init(rootDirectory: URL? = nil) {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        rootDir = rootDirectory ?? docs.appendingPathComponent(Self.syncDirName)
        try? FileManager.default.createDirectory(at: rootDir, withIntermediateDirectories: true)
    }

    /// Save JSON data and return the absolute file path.
    @discardableResult
    func saveData(deviceId: String, dataType: SyncableDataType,
                  date: Date, jsonContent: String) throws -> String {
        try Task.checkCancellation()
        let dir = getTypeDir(deviceId: deviceId, dataType: dataType)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let fileName = "\(dateFormatter.string(from: date)).json"
        let fileURL = dir.appendingPathComponent(fileName)

        do {
            try jsonContent.write(to: fileURL, atomically: true, encoding: .utf8)
            Self.logger.debug("Saved \(fileURL.lastPathComponent) (\(jsonContent.count) chars)")
        } catch {
            Self.logger.error("Failed to save \(fileURL.path): \(error)")
            throw error
        }

        return fileURL.path
    }

    /// Save raw binary data for offline recordings.
    /// Uses a unique filename based on date + time to avoid collisions.
    @discardableResult
    func saveRawData(deviceId: String, dataType: SyncableDataType,
                     dateTime: Date, data: Data) throws -> String {
        try Task.checkCancellation()
        let dir = getTypeDir(deviceId: deviceId, dataType: dataType)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let fileName = "\(dateTimeFormatter.string(from: dateTime)).dat"
        let fileURL = dir.appendingPathComponent(fileName)

        do {
            try data.write(to: fileURL, options: .atomic)
            Self.logger.debug("Saved raw \(fileURL.lastPathComponent) (\(data.count) bytes)")
        } catch {
            Self.logger.error("Failed to save raw \(fileURL.path): \(error)")
            throw error
        }

        return fileURL.path
    }
    func listEntries(deviceId: String, dataType: SyncableDataType,
                     fromDate: Date?, toDate: Date?) -> [SyncedDataEntry] {
        let dir = getTypeDir(deviceId: deviceId, dataType: dataType)
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: dir, includingPropertiesForKeys: [.fileSizeKey]) else {
            return []
        }

        let validExtension = dataType.isOfflineRecording ? "dat" : "json"

        return files
            .filter { $0.pathExtension == validExtension }
            .compactMap { fileURL -> SyncedDataEntry? in
                guard let date = parseDateFromFileName(fileURL.lastPathComponent) else { return nil }
                if let from = fromDate, date < from { return nil }
                if let to = toDate, date > to { return nil }
                let size = (try? fileURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
                return SyncedDataEntry(
                    deviceId: deviceId, dataType: dataType, date: date,
                    localFilePath: fileURL.path, syncedAt: Date(),
                    sizeBytes: Int64(size))
            }
            .sorted { $0.date < $1.date }
    }

    func readFile(path: String) -> String? {
        return try? String(contentsOfFile: path, encoding: .utf8)
    }

    /// Read raw binary data from a file.
    func readRawFile(path: String) -> Data? {
        return FileManager.default.contents(atPath: path)
    }

    func getDeviceDir(deviceId: String) -> String {
        return rootDir.appendingPathComponent(deviceId).path
    }

    func hasData(deviceId: String, dataType: SyncableDataType, date: Date) -> Bool {
        let dir = getTypeDir(deviceId: deviceId, dataType: dataType)
        let fileName = "\(dateFormatter.string(from: date)).json"
        return FileManager.default.fileExists(atPath: dir.appendingPathComponent(fileName).path)
    }

    func getSyncedDates(deviceId: String, dataType: SyncableDataType) -> Set<Date> {
        let dir = getTypeDir(deviceId: deviceId, dataType: dataType)
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: dir, includingPropertiesForKeys: nil) else { return [] }
        return Set(files.compactMap { parseDateFromFileName($0.lastPathComponent) })
    }

    /// Purge locally stored files older than the retention config.
    @discardableResult
    func purgeExpired() -> Int {
        guard let config = retentionConfig else { return 0 }
        let cutoffDate = Calendar.current.date(byAdding: .day, value: -config.maxAgeDays, to: Calendar.current.startOfDay(for: Date()))!
        var count = 0

        for dataType in config.dataTypes {
            guard let deviceDirs = try? FileManager.default.contentsOfDirectory(
                at: rootDir, includingPropertiesForKeys: nil) else { continue }
            for deviceDir in deviceDirs where deviceDir.hasDirectoryPath {
                let deviceId = deviceDir.lastPathComponent
                let typeDir = deviceDir.appendingPathComponent(dataType.rawValue)
                guard let files = try? FileManager.default.contentsOfDirectory(
                    at: typeDir, includingPropertiesForKeys: nil) else { continue }
                for file in files {
                    if let date = parseDateFromFileName(file.lastPathComponent), date < cutoffDate {
                        try? FileManager.default.removeItem(at: file)
                        count += 1
                    }
                }
                // Reset the sync watermark so purged dates can be re-fetched if needed
                if let tracker = syncStateTracker {
                    let currentWatermark = tracker.getWatermark(deviceId: deviceId, dataType: dataType)
                    if let wm = currentWatermark, wm < cutoffDate {
                        tracker.resetWatermark(deviceId: deviceId, dataType: dataType, toDate: nil)
                    }
                    // Clear any synced paths for this data type as well
                    if dataType.isOfflineRecording || dataType == .trainingSessions {
                        tracker.clearPaths(deviceId: deviceId, dataType: dataType)
                    }
                }
            }
        }

        if count > 0 { Self.logger.info("Purged \(count) expired local files") }
        return count
    }

    func deleteDeviceData(deviceId: String) {
        let dir = rootDir.appendingPathComponent(deviceId)
        try? FileManager.default.removeItem(at: dir)
        Self.logger.info("Deleted all local data for \(deviceId)")
        syncStateTracker?.clearAllSyncState(deviceId: deviceId)
    }

    // MARK: - Private

    private func getTypeDir(deviceId: String, dataType: SyncableDataType) -> URL {
        return rootDir.appendingPathComponent(deviceId).appendingPathComponent(dataType.rawValue)
    }

    private func parseDateFromFileName(_ name: String) -> Date? {
        var dateStr = name.replacingOccurrences(of: ".json", with: "")
                          .replacingOccurrences(of: ".dat", with: "")
        // Handle datetime format (yyyyMMdd_HHmmss) by taking just the date part
        if dateStr.contains("_") {
            dateStr = String(dateStr.prefix(while: { $0 != "_" }))
        }
        return dateFormatter.date(from: dateStr)
    }
}
