// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Configuration for the background sync process.
public struct SyncConfig {
    /// When true, synced data is automatically deleted from the device.
    /// Date-structured data is not deleted until at least `deviceRetentionDays` old.
    public let autoDeleteFromDevice: Bool

    /// Minimum age in days before date-structured data is eligible for deletion from device.
    public let deviceRetentionDays: Int

    /// Minimum age in days before offline recordings are eligible for deletion from device.
    /// Default is 0 (delete immediately after sync).
    /// Only applies when `autoDeleteFromDevice` is true.
    public let offlineRecordingRetentionDays: Int

    /// Interval in minutes between timed sync cycles.
    /// 0 means timed sync is disabled — only device-to-host notifications trigger sync.
    /// Valid range: 0–60.
    public let syncIntervalMinutes: Int

    public init(autoDeleteFromDevice: Bool = false, deviceRetentionDays: Int = 2,
                offlineRecordingRetentionDays: Int = 0, syncIntervalMinutes: Int = 0) {
        self.autoDeleteFromDevice = autoDeleteFromDevice
        self.deviceRetentionDays = deviceRetentionDays
        self.offlineRecordingRetentionDays = offlineRecordingRetentionDays
        self.syncIntervalMinutes = syncIntervalMinutes
    }
}

/// A single synced data entry stored locally.
public struct SyncedDataEntry: Identifiable {
    public let id: String
    public let deviceId: String
    public let dataType: SyncableDataType
    public let date: Date
    public let localFilePath: String
    public let syncedAt: Date
    public let sizeBytes: Int64

    public init(deviceId: String, dataType: SyncableDataType, date: Date,
                localFilePath: String, syncedAt: Date, sizeBytes: Int64) {
        self.id = "\(deviceId)_\(dataType.rawValue)_\(localFilePath)"
        self.deviceId = deviceId
        self.dataType = dataType
        self.date = date
        self.localFilePath = localFilePath
        self.syncedAt = syncedAt
        self.sizeBytes = sizeBytes
    }
}

/// Connection state of the Polar device.
public enum ConnectionState: String {
    case disconnected
    case connecting
    case connected
    case ftuCheck
    case syncing
}

/// Represents a sync error.
public struct SyncError {
    public let dataType: SyncableDataType?
    public let message: String
    public let cause: Error?
}

/// Result of syncing a specific data type/date.
public struct SyncResult {
    public let deviceId: String
    public let dataType: SyncableDataType
    public let date: Date
    public let localFilePath: String
    public let sizeBytes: Int64
}

/// Summary of a completed sync cycle for a device.
public struct SyncSummary {
    public let deviceId: String
    public let startTime: Date
    public let endTime: Date
    public let durationSeconds: Int
    public let totalItems: Int
    public let totalBytes: Int64
    public let perTypeStats: [SyncableDataType: TypeSyncStats]
}

/// Per-data-type sync statistics.
public struct TypeSyncStats {
    public let items: Int
    public let bytes: Int64
}

/// Configuration for automatic local data retention.
public struct LocalRetentionConfig {
    public let dataTypes: Set<SyncableDataType>
    public let maxAgeDays: Int

    public init(dataTypes: Set<SyncableDataType>, maxAgeDays: Int) {
        self.dataTypes = dataTypes
        self.maxAgeDays = maxAgeDays
    }
}
