// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Represents parsed sync log data for a single device.
/// Extracted from structured PLOG lines in the sync log.
///
/// PLOG line format:  PLOG|yyyy-MM-dd HH:mm:ss.SSS|<deviceId>|<EVENT>|<params>
/// Events: SYNC_START, SYNC_END, SYNC_<TYPE>_START, SYNC_<TYPE>_END, SYNC_OFFLINE_START, SYNC_OFFLINE_<TYPE>_START, SYNC_OFFLINE_<TYPE>_END, CLEANUP_START, CLEANUP_END, BAT
struct LogGraphData {
    let deviceId: String
    let batteryReadings: [BatteryReading]
    let syncPhases: [SyncPhase]                          // Overall sync bars (cyan)
    let activityTypePhases: [DataTypePhase]              // Individual activity types (color-coded)
    let recordingPhases: [SyncPhase]                     // Overall offline recording (orange)
    let recordingTypePhases: [DataTypePhase]             // Individual recording types (color-coded)
    let cleanupPhases: [SyncPhase]                       // Cleanup phases (magenta)

    static func parse(deviceId: String, logLines: [String]) -> LogGraphData {
        var plogBatteryReadings: [BatteryReading] = []
        var polarBatReadings: [BatteryReading] = []
        var syncPhases: [SyncPhase] = []
        var activityTypePhases: [DataTypePhase] = []
        var recordingPhases: [SyncPhase] = []
        var recordingTypePhases: [DataTypePhase] = []
        var cleanupPhases: [SyncPhase] = []

        // Track start times PER DEVICE for concurrent syncs
        var syncStartTimes: [String: Date] = [:]
        var cleanupStartTimes: [String: Date] = [:]
        var activityTypeStartTimes: [String: Date] = [:]      // Key: "deviceId:TYPE"
        var recordingTypeStartTimes: [String: Date] = [:]     // Key: "deviceId:TYPE"
        var recordingStartTimes: [String: Date] = [:]         // For overall offline recording

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        
        // CRITICAL: Sort lines by PLOG timestamp (not by write time!) to ensure START events are processed before END events
        let sortedLines = logLines.sorted { line1, line2 in
            let getTimestamp = { (line: String) -> Date? in
                if let plogRange = line.range(of: "PLOG|") {
                    let plog = String(line[plogRange.lowerBound...])
                    let parts = plog.components(separatedBy: "|")
                    guard parts.count >= 2 else { return nil }
                    return formatter.date(from: parts[1])
                }
                if let polRange = line.range(of: "POLARBAT|") {
                    let bat = String(line[polRange.lowerBound...])
                    let parts = bat.components(separatedBy: "|")
                    guard parts.count >= 2 else { return nil }
                    return isoFormatter.date(from: parts[1])
                }
                return nil
            }
            guard let ts1 = getTimestamp(line1), let ts2 = getTimestamp(line2) else {
                return line1.count < line2.count  // stable sort fallback
            }
            if ts1 != ts2 { return ts1 < ts2 }
            // For identical timestamps, process PLOG BAT first and POLARBAT after,
            // so the coalescer can prefer richer POLARBAT charging states.
            let rank1 = line1.contains("POLARBAT|") ? 1 : 0
            let rank2 = line2.contains("POLARBAT|") ? 1 : 0
            if rank1 != rank2 { return rank1 < rank2 }
            return line1 < line2
        }

        for line in sortedLines {
            // Parse POLARBAT diagnostics as battery points.
            if let polRange = line.range(of: "POLARBAT|") {
                let bat = String(line[polRange.lowerBound...])
                let parts = bat.components(separatedBy: "|")
                if parts.count >= 8 {
                    let lineDeviceId = parts[2].replacingOccurrences(of: "device=", with: "")
                    if lineDeviceId == deviceId, let ts = isoFormatter.date(from: parts[1]) {
                        var kv: [String: String] = [:]
                        for token in parts.dropFirst(4) {
                            let pair = token.split(separator: "=", maxSplits: 1).map(String.init)
                            if pair.count == 2 { kv[pair[0]] = pair[1] }
                        }
                        let phoneBatStr = kv["phone_bat"]?.replacingOccurrences(of: "%", with: "")
                        let phoneBat = phoneBatStr != nil ? (Int(phoneBatStr!) ?? -1) : -1
                        let phoneCharging = kv["phone_charging"] ?? "UNKNOWN"
                        let deviceBatStr = kv["device_bat"]?.replacingOccurrences(of: "%", with: "")
                        let deviceBat = deviceBatStr != nil ? Int(deviceBatStr!) : nil
                        let deviceCharging = kv["device_charging"] ?? "UNKNOWN"
                        if phoneBat >= 0 {
                            polarBatReadings.append(BatteryReading(timestamp: ts, phoneBatteryPercent: phoneBat, phoneChargingState: phoneCharging, deviceBatteryPercent: deviceBat, deviceChargingState: deviceCharging))
                        }
                    }
                }
            }

            // Find PLOG| marker — works whether line is raw or has a display prefix
            guard let plogRange = line.range(of: "PLOG|") else { continue }
            let plog = String(line[plogRange.lowerBound...])
            let parts = plog.components(separatedBy: "|")
            // parts[0]=PLOG, parts[1]=timestamp, parts[2]=deviceId, parts[3]=event, parts[4...]=params
            guard parts.count >= 4 else { continue }
            guard parts[2] == deviceId else { continue }
            guard let ts = formatter.date(from: parts[1]) else { continue }

            let event = parts[3]
            let params = parts.count > 4 ? parts[4...].joined(separator: "|") : ""

            if event == "SYNC_START" {
                syncStartTimes[deviceId] = ts
            } else if event == "SYNC_END" {
                if let startTime = syncStartTimes[deviceId] {
                    let bytes = params.components(separatedBy: " ")
                        .first(where: { $0.hasPrefix("bytes=") })
                        .flatMap { Int64($0.replacingOccurrences(of: "bytes=", with: "")) }
                    syncPhases.append(SyncPhase(startTime: startTime, endTime: ts, deviceId: deviceId, transferredBytes: bytes))
                }
                syncStartTimes.removeValue(forKey: deviceId)
            } else if event.hasPrefix("SYNC_") && event.hasSuffix("_START") && !event.hasPrefix("SYNC_OFFLINE") {
                // SYNC_<TYPE>_START for activity types
                var dataType = event
                    .replacingOccurrences(of: "SYNC_", with: "")
                    .replacingOccurrences(of: "_START", with: "")
                if dataType.isEmpty { dataType = "UNKNOWN" }
                let key = "\(deviceId):\(dataType)"
                activityTypeStartTimes[key] = ts
            } else if event.hasPrefix("SYNC_") && event.hasSuffix("_END") && !event.hasPrefix("SYNC_OFFLINE") {
                // SYNC_<TYPE>_END for activity types
                var dataType = event
                    .replacingOccurrences(of: "SYNC_", with: "")
                    .replacingOccurrences(of: "_END", with: "")
                if dataType.isEmpty { dataType = "UNKNOWN" }
                let key = "\(deviceId):\(dataType)"
                
                // Parse itemCount from params
                let itemCount = params.components(separatedBy: " ")
                    .first(where: { $0.hasPrefix("items=") })
                    .flatMap { Int($0.replacingOccurrences(of: "items=", with: "")) } ?? 0
                
                // Show all synced types (even if 0 items) to visualize time spent on each type
                if let startTime = activityTypeStartTimes[key] {
                    let bytes = params.components(separatedBy: " ")
                        .first(where: { $0.hasPrefix("bytes=") })
                        .flatMap { Int64($0.replacingOccurrences(of: "bytes=", with: "")) }
                    activityTypePhases.append(DataTypePhase(
                        startTime: startTime,
                        endTime: ts,
                        dataType: dataType,
                        category: "ACTIVITY",
                        deviceId: deviceId,
                        transferredBytes: bytes
                    ))
                    print("✓ Added activity type phase: \(dataType) (\(itemCount) items)")
                } else {
                    print("⚠ No start time found for activity type: \(dataType)")
                }
                activityTypeStartTimes.removeValue(forKey: key)
            } else if event == "SYNC_OFFLINE_START" {
                recordingStartTimes[deviceId] = ts
            } else if event == "SYNC_OFFLINE_END" {
                if let startTime = recordingStartTimes[deviceId] {
                    let bytes = params.components(separatedBy: " ")
                        .first(where: { $0.hasPrefix("bytes=") })
                        .flatMap { Int64($0.replacingOccurrences(of: "bytes=", with: "")) }
                    recordingPhases.append(SyncPhase(startTime: startTime, endTime: ts, deviceId: deviceId, transferredBytes: bytes))
                }
                recordingStartTimes.removeValue(forKey: deviceId)
            } else if event.hasPrefix("SYNC_OFFLINE_") && event.hasSuffix("_START") {
                // SYNC_OFFLINE_<TYPE>_START for recording types
                var dataType = event
                    .replacingOccurrences(of: "SYNC_OFFLINE_", with: "")
                    .replacingOccurrences(of: "_START", with: "")
                if dataType.isEmpty { dataType = "UNKNOWN" }
                let key = "\(deviceId):\(dataType)"
                recordingTypeStartTimes[key] = ts
            } else if event.hasPrefix("SYNC_OFFLINE_") && event.hasSuffix("_END") {
                // SYNC_OFFLINE_<TYPE>_END for recording types
                var dataType = event
                    .replacingOccurrences(of: "SYNC_OFFLINE_", with: "")
                    .replacingOccurrences(of: "_END", with: "")
                if dataType.isEmpty { dataType = "UNKNOWN" }
                let key = "\(deviceId):\(dataType)"
                
                // Parse itemCount from params
                let itemCount = params.components(separatedBy: " ")
                    .first(where: { $0.hasPrefix("items=") })
                    .flatMap { Int($0.replacingOccurrences(of: "items=", with: "")) } ?? 0
                
                // Show all synced types (even if 0 items) to visualize time spent on each type
                if let startTime = recordingTypeStartTimes[key] {
                    let bytes = params.components(separatedBy: " ")
                        .first(where: { $0.hasPrefix("bytes=") })
                        .flatMap { Int64($0.replacingOccurrences(of: "bytes=", with: "")) }
                    recordingTypePhases.append(DataTypePhase(
                        startTime: startTime,
                        endTime: ts,
                        dataType: dataType,
                        category: "OFFLINE",
                        deviceId: deviceId,
                        transferredBytes: bytes
                    ))
                    print("✓ Added recording type phase: \(dataType) (\(itemCount) items)")
                } else {
                    print("⚠ No start time found for recording type: \(dataType)")
                }
                recordingTypeStartTimes.removeValue(forKey: key)
            } else if event == "CLEANUP_START" {
                cleanupStartTimes[deviceId] = ts
            } else if event == "CLEANUP_END" {
                if let startTime = cleanupStartTimes[deviceId] {
                    let bytes = params.components(separatedBy: " ")
                        .first(where: { $0.hasPrefix("bytes=") })
                        .flatMap { Int64($0.replacingOccurrences(of: "bytes=", with: "")) }
                    cleanupPhases.append(SyncPhase(startTime: startTime, endTime: ts, deviceId: deviceId, transferredBytes: bytes))
                }
                cleanupStartTimes.removeValue(forKey: deviceId)
            } else if event == "BAT" {
                let kvPairs = params.components(separatedBy: " ")
                let phoneBat = kvPairs.first(where: { $0.hasPrefix("phone=") })
                    .flatMap { Int($0.replacingOccurrences(of: "phone=", with: "")) } ?? -1
                let phoneCharging = kvPairs.first(where: { $0.hasPrefix("phone_charging=") })
                    .flatMap { String($0.replacingOccurrences(of: "phone_charging=", with: "")) } ?? "UNKNOWN"
                let deviceBat = kvPairs.first(where: { $0.hasPrefix("device=") })
                    .flatMap { Int($0.replacingOccurrences(of: "device=", with: "")) }
                let deviceCharging = kvPairs.first(where: { $0.hasPrefix("device_charging=") })
                    .flatMap { String($0.replacingOccurrences(of: "device_charging=", with: "")) } ?? "UNKNOWN"
                if phoneBat >= 0 {
                    plogBatteryReadings.append(BatteryReading(timestamp: ts, phoneBatteryPercent: phoneBat, phoneChargingState: phoneCharging, deviceBatteryPercent: deviceBat, deviceChargingState: deviceCharging))
                }
            }
        }

        // Prefer PLOG BAT for plotting. Use POLARBAT only as a backward-compatible fallback.
        let selectedReadings = plogBatteryReadings.isEmpty ? polarBatReadings : plogBatteryReadings
        let mergedBatteryReadings = coalesceBatteryReadings(selectedReadings.sorted { $0.timestamp < $1.timestamp })

        return LogGraphData(
            deviceId: deviceId,
            batteryReadings: mergedBatteryReadings,
            syncPhases: syncPhases,
            activityTypePhases: activityTypePhases,
            recordingPhases: recordingPhases,
            recordingTypePhases: recordingTypePhases,
            cleanupPhases: cleanupPhases
        )
    }

    /// Coalesce battery readings that are very close in time and represent the same sample,
    /// preferring richer/non-UNKNOWN charging states to avoid overlapping contradictory markers.
    private static func coalesceBatteryReadings(_ readings: [BatteryReading]) -> [BatteryReading] {
        guard !readings.isEmpty else { return readings }

        func isUnknown(_ state: String) -> Bool {
            let s = state.uppercased()
            return s.isEmpty || s == "UNKNOWN"
        }

        func preferredState(_ a: String, _ b: String) -> String {
            let aUnknown = isUnknown(a)
            let bUnknown = isUnknown(b)
            if aUnknown && !bUnknown { return b }
            if !aUnknown && bUnknown { return a }
            // If both unknown or both known, prefer the later sample (b).
            return b
        }

        func merge(_ older: BatteryReading, _ newer: BatteryReading) -> BatteryReading {
            BatteryReading(
                timestamp: newer.timestamp,
                phoneBatteryPercent: newer.phoneBatteryPercent,
                phoneChargingState: preferredState(older.phoneChargingState, newer.phoneChargingState),
                deviceBatteryPercent: newer.deviceBatteryPercent ?? older.deviceBatteryPercent,
                deviceChargingState: preferredState(older.deviceChargingState, newer.deviceChargingState)
            )
        }

        var result: [BatteryReading] = []
        let mergeWindowSeconds: TimeInterval = 1.0

        for reading in readings {
            guard let last = result.last else {
                result.append(reading)
                continue
            }

            let dt = reading.timestamp.timeIntervalSince(last.timestamp)
            let closeInTime = abs(dt) <= mergeWindowSeconds
            let samePhone = reading.phoneBatteryPercent == last.phoneBatteryPercent
            let compatibleDevice =
                reading.deviceBatteryPercent == last.deviceBatteryPercent ||
                reading.deviceBatteryPercent == nil ||
                last.deviceBatteryPercent == nil

            if closeInTime && samePhone && compatibleDevice {
                result[result.count - 1] = merge(last, reading)
            } else {
                result.append(reading)
            }
        }

        return result
    }
}

struct BatteryReading {
    let timestamp: Date
    let phoneBatteryPercent: Int
    let phoneChargingState: String
    let deviceBatteryPercent: Int?
    let deviceChargingState: String
}

struct SyncPhase {
    let startTime: Date
    let endTime: Date
    let deviceId: String
    let transferredBytes: Int64?
    var durationSeconds: TimeInterval { endTime.timeIntervalSince(startTime) }
}

struct DataTypePhase {
    let startTime: Date
    let endTime: Date
    let dataType: String                           // e.g., "STEPS", "SLEEP", "ACC", etc.
    let category: String                           // "ACTIVITY" or "OFFLINE"
    let deviceId: String
    let transferredBytes: Int64?
    var durationSeconds: TimeInterval { endTime.timeIntervalSince(startTime) }
}
