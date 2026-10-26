// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import os

/// Persistent file-backed log store for sync events. Entries survive app kills/relaunches
/// and are retained for 30 days. Thread-safe.
class SyncLogStore {

    static let shared = SyncLogStore()

    private static let logFileName = "sync_log.txt"
    private static let maxEntries = 5000
    private static let retentionDays: TimeInterval = 30 * 24 * 60 * 60
    private static let separator: Character = "|"

    private let logFileURL: URL
    private let queue = DispatchQueue(label: "com.polar.bgsyncsample.synclogstore", qos: .utility)
    private var entries: [LogEntry] = []
    private let now: () -> Date
    private var nextPruneDate: Date
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
    private let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        return formatter
    }()

    struct LogEntry {
        let timestampMillis: Int64
        let message: String
        var displayEntry: SyncLogEntry?
        var isFormatted = false
    }

    private convenience init() {
        let documentsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        self.init(logFileURL: documentsDir.appendingPathComponent(Self.logFileName))
    }

    init(logFileURL: URL, now: @escaping () -> Date = Date.init) {
        self.logFileURL = logFileURL
        self.now = now
        nextPruneDate = now().addingTimeInterval(24 * 60 * 60)
        loadFromDisk()
    }

    /// Add a log entry. Persists immediately to disk.
    func addEntry(_ message: String) {
        queue.sync {
            let entry = LogEntry(
                timestampMillis: Int64(now().timeIntervalSince1970 * 1000),
                message: message
            )
            entries.insert(entry, at: 0)

            // Trim to max
            if entries.count > Self.maxEntries {
                entries.removeLast(entries.count - Self.maxEntries)
            }

            appendToDisk(entry)
            if now() >= nextPruneDate {
                loadFromDisk()
                nextPruneDate = now().addingTimeInterval(24 * 60 * 60)
            }
        }
    }

    /// Get all log entries as display strings (newest first).
    /// PLOG entries are formatted to human-readable text; other entries are shown as-is.
    func getDisplayEntries() -> [String] {
        return queue.sync {
            displayEntries().map { row in
                let deviceSegment = row.deviceId == "-" ? "" : " [\(row.deviceId)]"
                return "[\(row.dateText) \(row.timeText)]\(deviceSegment) \(row.message)"
            }
        }
    }

    /// Get all entries as SyncLogEntry objects for SwiftUI (newest first).
    /// Derives display text from PLOG events.
    func getSyncLogEntries() -> [SyncLogEntry] {
        return queue.sync {
            displayEntries()
        }
    }

    private func displayEntries() -> [SyncLogEntry] {
        entries.indices.compactMap { index in
            if !entries[index].isFormatted {
                let entry = entries[index]
                if entry.message.hasPrefix("PLOG|") {
                    entries[index].displayEntry = Self.formatPlogToDisplayEntry(entry.message)
                } else if !entry.message.hasPrefix("POLARBAT|") && !Self.shouldSuppressDiagnosticLine(entry.message) {
                    let date = Date(timeIntervalSince1970: Double(entry.timestampMillis) / 1000.0)
                    entries[index].displayEntry = SyncLogEntry(
                        dateText: dateFormatter.string(from: date),
                        timeText: timeFormatter.string(from: date),
                        deviceId: "-", message: entry.message)
                }
                entries[index].isFormatted = true
            }
            return entries[index].displayEntry
        }
    }

    /// Get all raw messages (newest first), including PLOG lines.
    /// Used by the graph parser which needs PLOG structured data.
    func getAllMessages() -> [String] {
        return queue.sync { entries.map { $0.message } }
    }

    /// Get raw messages for a specific device (newest first), including PLOG lines.
    /// Filters by deviceId from PLOG lines: PLOG|timestamp|deviceId|event|params
    func getMessagesForDevice(_ deviceId: String) -> [String] {
        return queue.sync {
            entries.compactMap { entry -> String? in
                guard entry.message.hasPrefix("PLOG|") else { return entry.message }
                let parts = entry.message.components(separatedBy: "|")
                guard parts.count >= 3 else { return nil }
                guard parts.indices.contains(2) else { return nil }
                guard parts[2] == deviceId else { return nil }
                return entry.message
            }
        }
    }

    private static func formatPlogToDisplayEntry(_ message: String) -> SyncLogEntry? {
        // PLOG|yyyy-MM-dd HH:mm:ss.SSS|deviceId|EVENT|params
        let parts = message.components(separatedBy: "|")
        guard parts.count >= 4 else { return nil }
        guard parts.indices.contains(1), parts.indices.contains(2), parts.indices.contains(3) else { return nil }

        let ts = parts[1]
        let deviceId = parts[2]
        let event = parts[3]
        let params = parts.count > 4 ? parts[4...].joined(separator: "|") : ""

        guard !ts.isEmpty, !deviceId.isEmpty, !event.isEmpty else { return nil }

        let dateText = String(ts.prefix(10))
        let timeText = String(ts.dropFirst(11).prefix(8))

        func p(_ key: String) -> String {
            if key == "msg" {
                guard let range = params.range(of: "msg=") else { return "" }
                return String(params[range.upperBound...])
            }
            guard let match = params.range(of: "\(key)=") else { return "" }
            let rest = params[match.upperBound...]
            return String(rest.prefix(while: { $0 != " " && $0 != "," }))
        }

        let text: String?
        switch event {
        case "SYNC_START":        text = "Sync started"
        case "SYNC_END":          text = "Sync complete"
        case "SYNC_OFFLINE_START": text = "📼 Offline recordings"
        case "SYNC_OFFLINE_END":  text = nil
        case "SYNC_SUMMARY":      text = buildSummaryText(params: params)
        case "SYNC_ERROR":        text = "⚠ Error (\(p("type"))): \(p("msg"))"
        case "CLEANUP_START":     text = "Cleaning device…"
        case "CLEANUP_END":       text = "Cleanup done (\(p("deleted")) types)"
        case "BAT":
            text = "Battery\nDevice: \(p("device"))%(\(p("device_charging")))\nPhone: \(p("phone"))%(\(p("phone_charging")))"
        case "CONNECTION":        text = "Connection: \(p("state"))"
        case "NOTIFICATION_POSTED": text = "🔔 \(p("title")) — \(p("msg"))"
        default:
            if event.hasPrefix("SYNC_OFFLINE_") && event.hasSuffix("_START") {
                let type = String(event.dropFirst("SYNC_OFFLINE_".count).dropLast("_START".count))
                text = "↳ \(type)…"
            } else if event.hasPrefix("SYNC_OFFLINE_") && event.hasSuffix("_END") {
                let type = String(event.dropFirst("SYNC_OFFLINE_".count).dropLast("_END".count))
                text = "✓ \(type) (\(p("items")) items)"
            } else if event.hasSuffix("_START") {
                let type = String(event.dropFirst("SYNC_".count).dropLast("_START".count))
                text = "↳ \(type)…"
            } else if event.hasSuffix("_END") {
                let type = String(event.dropFirst("SYNC_".count).dropLast("_END".count))
                text = "✓ \(type) (\(p("items")) items)"
            } else { text = nil }
        }
        guard let displayText = text else { return nil }
        return SyncLogEntry(dateText: dateText, timeText: timeText, deviceId: deviceId, message: displayText)
    }

    private static func buildSummaryText(params: String) -> String {
        func p(_ key: String) -> String {
            guard let range = params.range(of: "\(key)=") else { return "" }
            let rest = params[range.upperBound...]
            return String(rest.prefix(while: { $0 != " " && $0 != "," && $0 != "|" }))
        }
        let items = p("items")
        let bytes = formatBytes(Int64(p("bytes")) ?? 0)
        let duration = p("duration")
        let breakdown = p("breakdown")

        var lines = ["── Sync Summary ──",
                 "Duration: \(duration)s   Total: \(items) items, \(bytes)"]

        if !breakdown.isEmpty {
            let entries = breakdown.components(separatedBy: ",")
            for entry in entries {
                let trimmed = entry.trimmingCharacters(in: .whitespaces)
                guard !trimmed.isEmpty else { continue }

                let seg = trimmed.components(separatedBy: ":")
                guard seg.count >= 2 else { continue }
                guard seg.indices.contains(0), seg.indices.contains(1) else { continue }

                let typeNameRaw = seg[0].trimmingCharacters(in: .whitespaces)
                let itemCountRaw = seg[1].trimmingCharacters(in: .whitespaces)
                guard !typeNameRaw.isEmpty, !itemCountRaw.isEmpty else { continue }

                let bytesStr = seg.indices.contains(2) ? seg[2] : "0"
                let typeBytes = formatBytes(Int64(bytesStr) ?? 0)
                lines.append("\(typeNameRaw): \(itemCountRaw) items, \(typeBytes)")
            }
        }
        return lines.joined(separator: "\n")
    }

    private static func formatBytes(_ bytes: Int64) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        if bytes < 1024 * 1024 { return String(format: "%.1f KB", Double(bytes) / 1024.0) }
        return String(format: "%.1f MB", Double(bytes) / (1024.0 * 1024.0))
    }

    private static func shouldSuppressDiagnosticLine(_ message: String) -> Bool {
        return message.range(
            of: "^SYNC\\s*\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3}]\\s*(\\[[^\\]]+])?\\s*PHASE:.*$",
            options: .regularExpression
        ) != nil
    }

    /// Get entry count.
    var count: Int {
        return queue.sync { entries.count }
    }

    /// Clear all log entries and delete the file.
    func clear() {
        queue.sync {
            entries.removeAll()
            guard FileManager.default.fileExists(atPath: logFileURL.path) else { return }
            do {
                try FileManager.default.removeItem(at: logFileURL)
            } catch {
                os_log("SyncLogStore: Failed to clear log file: %{public}@", error.localizedDescription)
            }
        }
    }

    // MARK: - Private

    private func loadFromDisk() {
        guard FileManager.default.fileExists(atPath: logFileURL.path) else { return }

        do {
            let content = try String(contentsOf: logFileURL, encoding: .utf8)
            // An interrupted append may leave a partial last record; do not join it to the next one.
            let lines = content.components(separatedBy: "\n").dropLast()
            let cutoffMillis = Int64(now().timeIntervalSince1970 * 1000) - Int64(Self.retentionDays * 1000)
            var loaded: [LogEntry] = []

            for line in lines where !line.isEmpty {
                guard let sepIndex = line.firstIndex(of: Self.separator) else { continue }
                let tsString = String(line[line.startIndex..<sepIndex])
                guard let timestamp = Int64(tsString) else { continue }
                if timestamp < cutoffMillis { continue } // Skip expired

                let message = Self.unescapeNewlines(String(line[line.index(after: sepIndex)...]))
                loaded.append(LogEntry(timestampMillis: timestamp, message: message))
            }

            // Sort newest first
            loaded.sort { $0.timestampMillis > $1.timestampMillis }

            // Trim to max
            entries = Array(loaded.prefix(Self.maxEntries))

            // Keep the complete 30-day diagnostic history on disk, not just the UI's 5000 rows.
            if !content.hasSuffix("\n") || loaded.count != lines.filter({ !$0.isEmpty }).count {
                try rewriteDisk(loaded)
            }

            os_log("SyncLogStore: Loaded %d log entries from disk", entries.count)
        } catch {
            os_log("SyncLogStore: Failed to load log file: %{public}@", error.localizedDescription)
        }
    }

    private func appendToDisk(_ entry: LogEntry) {
        let newLine = "\(entry.timestampMillis)\(Self.separator)\(Self.escapeNewlines(entry.message))\n"
        do {
            let data = Data(newLine.utf8)
            if FileManager.default.fileExists(atPath: logFileURL.path) {
                let file = try FileHandle(forWritingTo: logFileURL)
                defer { file.closeFile() }
                try file.seekToEnd()
                try file.write(contentsOf: data)
            } else {
                try data.write(to: logFileURL, options: .atomic)
            }
        } catch {
            os_log("SyncLogStore: Failed to append to log file: %{public}@", error.localizedDescription)
        }
    }

    private func rewriteDisk(_ entries: [LogEntry]) throws {
        let content = entries.map { "\($0.timestampMillis)\(Self.separator)\(Self.escapeNewlines($0.message))" }.joined(separator: "\n")
        try (content + "\n").write(to: logFileURL, atomically: true, encoding: .utf8)
    }

    /// Escape newlines so each log entry is a single line on disk.
    private static func escapeNewlines(_ str: String) -> String {
        return str
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
    }

    /// Unescape newlines when reading back from disk.
    private static func unescapeNewlines(_ str: String) -> String {
        return str
            .replacingOccurrences(of: "\\r", with: "\r")
            .replacingOccurrences(of: "\\n", with: "\n")
            .replacingOccurrences(of: "\\\\", with: "\\")
    }
}

struct SyncLogEntry: Identifiable {
    let id = UUID()
    let dateText: String
    let timeText: String
    let deviceId: String
    let message: String

    init(message: String) {
        self.init(storedTimestamp: Date(), message: message)
    }

    init(storedTimestamp: Date, message: String) {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "HH:mm:ss"
        self.dateText = dateFormatter.string(from: storedTimestamp)
        self.timeText = timeFormatter.string(from: storedTimestamp)
        self.deviceId = "-"
        self.message = message
    }

    init(dateText: String, timeText: String, deviceId: String, message: String) {
        self.dateText = dateText
        self.timeText = timeText
        self.deviceId = deviceId
        self.message = message
    }
}
