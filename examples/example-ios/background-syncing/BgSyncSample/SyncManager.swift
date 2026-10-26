// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import Combine
import UIKit
import UserNotifications

enum DeviceSyncStatus {
    case none
    case syncing
    case cleanup
    case success
    case error
}

struct DeviceSyncInfo {
    var lastSyncTime: Date?
    var status: DeviceSyncStatus = .none
}

struct BatteryInfo {
    let level: UInt
    let receivedAt: Date
}

/// ObservableObject wrapping PolarBackgroundSyncImpl for SwiftUI consumption.
class SyncManager: NSObject, ObservableObject, SyncCallbackDelegate, UNUserNotificationCenterDelegate {

    static let shared = SyncManager()

    @Published var connectionStates: [String: ConnectionState] = [:]
    @Published var deviceSyncInfo: [String: DeviceSyncInfo] = [:]
    @Published var batteryLevels: [String: BatteryInfo] = [:]
    @Published var isSyncing = false
    @Published var syncLog: [SyncLogEntry] = []
    @Published var lastSyncResults: [SyncResult] = []
    @Published var enabledDataTypes: Set<SyncableDataType> = Set(SyncableDataType.allCases)
    @Published var autoDeleteEnabled = true {
        didSet { UserDefaults.standard.set(autoDeleteEnabled, forKey: Self.autoDeleteEnabledKey) }
    }
    @Published var deviceRetentionDays = 2 {
        didSet { UserDefaults.standard.set(deviceRetentionDays, forKey: Self.deviceRetentionDaysKey) }
    }
    @Published var offlineRecordingRetentionDays = 0 {
        didSet { UserDefaults.standard.set(offlineRecordingRetentionDays, forKey: Self.offlineRecordingRetentionDaysKey) }
    }
    @Published var syncIntervalMinutes = 0 {
        didSet { UserDefaults.standard.set(syncIntervalMinutes, forKey: Self.syncIntervalMinutesKey) }
    }
    @Published var localRetentionDays = 30 {
        didSet { UserDefaults.standard.set(localRetentionDays, forKey: Self.localRetentionDaysKey) }
    }
    @Published var localRetentionEnabled = false {
        didSet { UserDefaults.standard.set(localRetentionEnabled, forKey: Self.localRetentionEnabledKey) }
    }
    @Published var syncOnForeground = true {
        didSet { UserDefaults.standard.set(syncOnForeground, forKey: Self.syncOnForegroundKey) }
    }
    @Published var deviceIds: [String] = []
    @Published var deviceIdInput = ""
    @Published var failedSyncDevice: String? = nil

    private let bgSync = PolarBackgroundSyncImpl.shared
    private let logStore = SyncLogStore.shared
    private var logRefreshWork: DispatchWorkItem?
    /// Devices that were just added and haven't completed their first sync yet.
    private var pendingFirstSync: Set<String> = []

    private override init() {
        super.init()
        UNUserNotificationCenter.current().delegate = self
        NotificationCenter.default.addObserver(self, selector: #selector(scheduleLogRefresh),
                                               name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(cancelLogRefresh),
                                               name: UIApplication.willResignActiveNotification, object: nil)
        scheduleLogRefresh()
        let defaults = UserDefaults.standard
        // Restore saved data type toggles
        let restoredTypes = readEnabledDataTypes(defaults.stringArray(forKey: Self.enabledDataTypesKey))
        if !restoredTypes.isEmpty {
            enabledDataTypes = restoredTypes
            saveEnabledDataTypes()
        }
        // Restore settings (only if previously saved — check key existence)
        if defaults.object(forKey: Self.autoDeleteEnabledKey) != nil {
            autoDeleteEnabled = defaults.bool(forKey: Self.autoDeleteEnabledKey)
        }
        if defaults.object(forKey: Self.deviceRetentionDaysKey) != nil {
            deviceRetentionDays = defaults.integer(forKey: Self.deviceRetentionDaysKey)
        }
        if defaults.object(forKey: Self.offlineRecordingRetentionDaysKey) != nil {
            offlineRecordingRetentionDays = defaults.integer(forKey: Self.offlineRecordingRetentionDaysKey)
        }
        if defaults.object(forKey: Self.syncIntervalMinutesKey) != nil {
            syncIntervalMinutes = defaults.integer(forKey: Self.syncIntervalMinutesKey)
        }
        if defaults.object(forKey: Self.localRetentionDaysKey) != nil {
            localRetentionDays = defaults.integer(forKey: Self.localRetentionDaysKey)
        }
        if defaults.object(forKey: Self.localRetentionEnabledKey) != nil {
            localRetentionEnabled = defaults.bool(forKey: Self.localRetentionEnabledKey)
        }
        if defaults.object(forKey: Self.syncOnForegroundKey) != nil {
            syncOnForeground = defaults.bool(forKey: Self.syncOnForegroundKey)
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    private static let deviceIdsKey = "saved_device_ids"
    private static let previouslyConnectedKey = "previously_connected"
    private static let enabledDataTypesKey = "enabled_data_types"
    private static let autoDeleteEnabledKey = "auto_delete_enabled"
    private static let deviceRetentionDaysKey = "device_retention_days"
    private static let offlineRecordingRetentionDaysKey = "offline_recording_retention_days"
    private static let syncIntervalMinutesKey = "sync_interval_minutes"
    private static let localRetentionDaysKey = "local_retention_days"
    private static let localRetentionEnabledKey = "local_retention_enabled"
    private static let syncOnForegroundKey = "sync_on_foreground"

    var savedDeviceIds: [String] {
        UserDefaults.standard.stringArray(forKey: Self.deviceIdsKey) ?? []
    }

    var wasPreviouslyConnected: Bool {
        UserDefaults.standard.bool(forKey: Self.previouslyConnectedKey)
    }

    func addDevice() {
        let id = deviceIdInput.trimmingCharacters(in: .whitespaces).uppercased()
        guard !id.isEmpty else { return }
        let isNew = !deviceIds.contains(id)
        if isNew {
            deviceIds.append(id)
        }
        deviceIdInput = ""
        if isNew {
            pendingFirstSync.insert(id)
            addLog("[\(id)] Device added \u{2014} starting sync")
            startSyncForDevice(id)
        }
    }

    func removeDevice(_ deviceId: String) {
        pendingFirstSync.remove(deviceId)
        if isSyncing {
            bgSync.stopSync(deviceId: deviceId)
        }
        deviceIds.removeAll { $0 == deviceId }
        connectionStates.removeValue(forKey: deviceId)
        // Persist updated device list
        UserDefaults.standard.set(deviceIds, forKey: Self.deviceIdsKey)
        if deviceIds.isEmpty {
            UserDefaults.standard.removeObject(forKey: Self.previouslyConnectedKey)
        }
        if failedSyncDevice == deviceId {
            failedSyncDevice = nil
        }
        // If no devices left with active sessions, update isSyncing
        if !deviceIds.contains(where: { bgSync.isSyncing(deviceId: $0) }) {
            isSyncing = false
        }
    }

    func retrySyncForFailedDevice() {
        guard let deviceId = failedSyncDevice else { return }
        failedSyncDevice = nil
        addLog("[\(deviceId)] Retrying sync")
        startSyncForDevice(deviceId)
    }

    func dismissSyncFailure() {
        guard let deviceId = failedSyncDevice else { return }
        pendingFirstSync.remove(deviceId)
        failedSyncDevice = nil
    }

    func startSync() {
        guard !deviceIds.isEmpty else { return }

        enabledDataTypes = readEnabledDataTypes(UserDefaults.standard.stringArray(forKey: Self.enabledDataTypesKey))
        saveEnabledDataTypes()

        for deviceId in deviceIds {
            startSyncForDevice(deviceId)
        }
        isSyncing = true
        addLog("Sync started for \(deviceIds.count) device(s)")
    }

    private func startSyncForDevice(_ deviceId: String) {
        let config = SyncConfig(
            autoDeleteFromDevice: autoDeleteEnabled,
            deviceRetentionDays: deviceRetentionDays,
            offlineRecordingRetentionDays: offlineRecordingRetentionDays,
            syncIntervalMinutes: syncIntervalMinutes
        )
        bgSync.startSync(deviceId: deviceId, dataTypes: enabledDataTypes,
                         config: config, callback: self)
        isSyncing = true
    }

    func stopSync() {
        deviceIds.forEach { bgSync.stopSync(deviceId: $0) }
        isSyncing = false
        addLog("Sync stopped for all devices")
    }

    func pushDataTypesToActiveSessions() {
        guard isSyncing else { return }
        for deviceId in deviceIds {
            bgSync.updateDataTypes(deviceId: deviceId, dataTypes: enabledDataTypes)
        }
    }

    func saveEnabledDataTypes() {
        let names = enabledDataTypes.map { $0.rawValue }
        UserDefaults.standard.set(names, forKey: Self.enabledDataTypesKey)
    }

    private func readEnabledDataTypes(_ savedNames: [String]?) -> Set<SyncableDataType> {
        guard let savedNames = savedNames else {
            return Set(SyncableDataType.allCases)
        }

        var parsed = Set(savedNames.compactMap { name in
            SyncableDataType.allCases.first { $0.rawValue == name }
        })

        if parsed.isEmpty {
            return Set(SyncableDataType.allCases)
        }

        // Migration: older saved selections only had offlineTemperature.
        if parsed.contains(.offlineTemperature) {
            parsed.insert(.offlineSkinTemperature)
        }
        // Migration: derived ACC recordings now have a dedicated data type.
        if parsed.contains(.offlineAcc) {
            parsed.insert(.offlineDerivedAcc)
        }

        return parsed
    }

    func disconnectAndForget() {
        deviceIds.forEach { bgSync.stopSync(deviceId: $0) }
        UserDefaults.standard.removeObject(forKey: Self.deviceIdsKey)
        UserDefaults.standard.removeObject(forKey: Self.previouslyConnectedKey)
        connectionStates = [:]
        deviceSyncInfo = [:]
        isSyncing = false
        syncLog = []
        lastSyncResults = []
        deviceIds = []
        deviceIdInput = ""
        addLog("All devices forgotten")
    }

    func resumeSyncAfterBackgroundRelaunch() {
        let ids = savedDeviceIds
        guard wasPreviouslyConnected, !ids.isEmpty else { return }
        addLog("Background relaunch — resuming sync for \(ids.count) device(s)")
        deviceIds = ids
        enabledDataTypes = readEnabledDataTypes(UserDefaults.standard.stringArray(forKey: Self.enabledDataTypesKey))
        saveEnabledDataTypes()
        let config = SyncConfig(
            autoDeleteFromDevice: autoDeleteEnabled,
            deviceRetentionDays: deviceRetentionDays,
            offlineRecordingRetentionDays: offlineRecordingRetentionDays,
            syncIntervalMinutes: syncIntervalMinutes
        )
        for deviceId in ids {
            bgSync.startSync(deviceId: deviceId, dataTypes: enabledDataTypes,
                             config: config, callback: self)
        }
    }

    func triggerSyncNow() {
        deviceIds.forEach { bgSync.triggerSyncNow(deviceId: $0) }
        addLog("Manual sync triggered for all devices")
    }

    func onAppForegrounded() {
        if syncOnForeground && isSyncing {
            for deviceId in deviceIds {
                bgSync.triggerSyncNow(deviceId: deviceId)
            }
        }
    }

    func setLocalRetention() {
        let config = LocalRetentionConfig(dataTypes: enabledDataTypes,
                                          maxAgeDays: localRetentionDays)
        bgSync.setLocalRetention(config: config)
    }

    func purgeExpired() {
        let count = bgSync.purgeExpiredData()
        addLog("Purged \(count) expired local files")
    }

    func listEntries(deviceId: String, dataType: SyncableDataType) -> [SyncedDataEntry] {
        return bgSync.getSyncedData(deviceId: deviceId, dataType: dataType,
                                    fromDate: nil, toDate: nil)
    }

    func readEntry(entry: SyncedDataEntry) -> String? {
        return bgSync.readSyncedData(entry: entry)
    }

    // MARK: - SyncCallbackDelegate

    // ── Per-type accumulator: tracks item count between onDataTypeStarted calls ──
    private var currentSyncDataTypeByDevice: [String: SyncableDataType] = [:]
    private var currentSyncTypeItemsByDevice: [String: Int] = [:]
    private var currentSyncTypeBytesByDevice: [String: Int64] = [:]
    private var currentSyncTotalBytesByDevice: [String: Int64] = [:]
    private var currentOfflineGroupBytesByDevice: [String: Int64] = [:]
    private var pendingOfflineGroupEnd: Set<String> = []
    private var openOfflineRecordingGroupsByDevice: Set<String> = []
    private var lastPlogBatteryParamsByDevice: [String: String] = [:]

    private func logBatterySnapshot(deviceId: String) {
        guard let params = BatteryLevelMonitor.shared.plogBatteryParams(deviceId: deviceId, requireKnownDeviceCharging: true) else {
            return
        }
        // Avoid flooding the log with identical BAT snapshots emitted close together.
        if lastPlogBatteryParamsByDevice[deviceId] == params {
            return
        }
        lastPlogBatteryParamsByDevice[deviceId] = params
        addPlog(deviceId, event: "BAT", params: params)
    }

    private func closeCurrentDataType(deviceId: String) {
        guard let previous = currentSyncDataTypeByDevice.removeValue(forKey: deviceId) else { return }
        let itemCount = currentSyncTypeItemsByDevice.removeValue(forKey: deviceId) ?? 0
        let byteCount = currentSyncTypeBytesByDevice.removeValue(forKey: deviceId) ?? 0
        let event = previous.isOfflineRecording ? "SYNC_OFFLINE_\(previous.rawValue)_END" : "SYNC_\(previous.rawValue)_END"
        addPlog(deviceId, event: event, params: "items=\(itemCount) bytes=\(byteCount)")
        if previous.isOfflineRecording, pendingOfflineGroupEnd.remove(deviceId) != nil {
            let groupBytes = currentOfflineGroupBytesByDevice.removeValue(forKey: deviceId) ?? byteCount
            addPlog(deviceId, event: "SYNC_OFFLINE_END", params: "bytes=\(groupBytes)")
            openOfflineRecordingGroupsByDevice.remove(deviceId)
        }
    }

    func onSyncStarted(deviceId: String) {
        DispatchQueue.main.async {
            self.currentSyncDataTypeByDevice.removeValue(forKey: deviceId)
            self.currentSyncTypeItemsByDevice.removeValue(forKey: deviceId)
            self.currentSyncTypeBytesByDevice.removeValue(forKey: deviceId)
            self.currentSyncTotalBytesByDevice[deviceId] = 0
            self.currentOfflineGroupBytesByDevice[deviceId] = 0
            self.deviceSyncInfo[deviceId, default: DeviceSyncInfo()].status = .syncing
            self.addPlog(deviceId, event: "SYNC_START")
            self.logBatterySnapshot(deviceId: deviceId)
        }
    }

    func onDataTypeStarted(deviceId: String, dataType: SyncableDataType) {
        DispatchQueue.main.async {
            self.closeCurrentDataType(deviceId: deviceId)
            self.currentSyncDataTypeByDevice[deviceId] = dataType
            self.currentSyncTypeItemsByDevice[deviceId] = 0
            self.currentSyncTypeBytesByDevice[deviceId] = 0
            let event = dataType.isOfflineRecording ? "SYNC_OFFLINE_\(dataType.rawValue)_START" : "SYNC_\(dataType.rawValue)_START"
            self.addPlog(deviceId, event: event)
        }
    }

    func onDataSynced(result: SyncResult) {
        DispatchQueue.main.async {
            self.lastSyncResults.append(result)
            self.currentSyncTypeItemsByDevice[result.deviceId, default: 0] += 1
            self.currentSyncTypeBytesByDevice[result.deviceId, default: 0] += Int64(result.sizeBytes)
            self.currentSyncTotalBytesByDevice[result.deviceId, default: 0] += Int64(result.sizeBytes)
            if self.currentSyncDataTypeByDevice[result.deviceId]?.isOfflineRecording == true {
                self.currentOfflineGroupBytesByDevice[result.deviceId, default: 0] += Int64(result.sizeBytes)
            }
        }
    }

    func onSyncCompleted(deviceId: String) {
        DispatchQueue.main.async {
            self.closeCurrentDataType(deviceId: deviceId)
            let totalBytes = self.currentSyncTotalBytesByDevice.removeValue(forKey: deviceId) ?? 0
            self.addPlog(deviceId, event: "SYNC_END", params: "bytes=\(totalBytes)")
            self.logBatterySnapshot(deviceId: deviceId)
            self.pendingFirstSync.remove(deviceId)
            self.openOfflineRecordingGroupsByDevice.remove(deviceId)
            self.currentOfflineGroupBytesByDevice.removeValue(forKey: deviceId)
            var info = self.deviceSyncInfo[deviceId, default: DeviceSyncInfo()]
            info.lastSyncTime = Date()
            info.status = .success
            self.deviceSyncInfo[deviceId] = info
        }
    }

    func onOfflineRecordingGroupStarted(deviceId: String) {
        DispatchQueue.main.async {
            if self.openOfflineRecordingGroupsByDevice.contains(deviceId) {
                return
            }
            self.openOfflineRecordingGroupsByDevice.insert(deviceId)
            self.addPlog(deviceId, event: "SYNC_OFFLINE_START")
        }
    }

    func onOfflineRecordingGroupCompleted(deviceId: String) {
        DispatchQueue.main.async {
            if self.currentSyncDataTypeByDevice[deviceId]?.isOfflineRecording == true {
                self.pendingOfflineGroupEnd.insert(deviceId)
            } else {
                let groupBytes = self.currentOfflineGroupBytesByDevice.removeValue(forKey: deviceId) ?? 0
                self.addPlog(deviceId, event: "SYNC_OFFLINE_END", params: "bytes=\(groupBytes)")
                self.openOfflineRecordingGroupsByDevice.remove(deviceId)
            }
        }
    }

    func onSyncSummary(summary: SyncSummary) {
        DispatchQueue.main.async {
            let breakdown = summary.perTypeStats.map { "\($0.key.rawValue):\($0.value.items):\($0.value.bytes)" }.joined(separator: ",")
            self.addPlog(summary.deviceId, event: "SYNC_SUMMARY",
                params: "items=\(summary.totalItems) bytes=\(summary.totalBytes) duration=\(summary.durationSeconds) breakdown=\(breakdown)")
            self.postSyncNotification(summary: summary)
            let isOffline = summary.perTypeStats.keys.contains { $0.isOfflineRecording }
            let label = isOffline ? "Offline recordings" : "Sync complete"
            let bytesStr = Self.formatBytes(summary.totalBytes)
            self.addPlog(summary.deviceId, event: "NOTIFICATION_POSTED",
                params: "title=\(label) [\(summary.deviceId)] msg=\(summary.totalItems) items, \(bytesStr) in \(summary.durationSeconds)s")
        }
    }

    func onSyncError(deviceId: String, error: SyncError) {
        DispatchQueue.main.async {
            var info = self.deviceSyncInfo[deviceId, default: DeviceSyncInfo()]
            info.status = .error
            self.deviceSyncInfo[deviceId] = info
            let typeName = error.dataType?.rawValue ?? "general"
            self.addPlog(deviceId, event: "SYNC_ERROR", params: "type=\(typeName) msg=\(error.message)")
            if self.pendingFirstSync.contains(deviceId) {
                self.failedSyncDevice = deviceId
            }
        }
    }

    func onConnectionStateChanged(deviceId: String, state: ConnectionState) {
        DispatchQueue.main.async {
            self.connectionStates[deviceId] = state
            self.addPlog(deviceId, event: "CONNECTION", params: "state=\(state)")
            if state == .connected || state == .syncing {
                UserDefaults.standard.set(self.deviceIds, forKey: Self.deviceIdsKey)
                UserDefaults.standard.set(true, forKey: Self.previouslyConnectedKey)
            }
            if state == .connected || state == .disconnected {
                if self.deviceSyncInfo[deviceId]?.status == .syncing {
                    self.deviceSyncInfo[deviceId]?.status = .none
                }
            }
            if state == .disconnected {
                self.isSyncing = self.deviceIds.contains { self.bgSync.hasActiveSession(deviceId: $0) }
            }
        }
    }

    func onBatteryLevelReceived(deviceId: String, level: UInt) {
        DispatchQueue.main.async {
            self.batteryLevels[deviceId] = BatteryInfo(level: level, receivedAt: Date())
            self.logBatterySnapshot(deviceId: deviceId)
        }
    }

    func onDeviceCleanupStarted(deviceId: String) {
        DispatchQueue.main.async {
            var info = self.deviceSyncInfo[deviceId, default: DeviceSyncInfo()]
            info.status = .cleanup
            self.deviceSyncInfo[deviceId] = info
            self.addPlog(deviceId, event: "CLEANUP_START")
        }
    }

    func onDeviceCleanupCompleted(deviceId: String, deletedTypes: Int) {
        DispatchQueue.main.async {
            var info = self.deviceSyncInfo[deviceId, default: DeviceSyncInfo()]
            info.status = .success
            self.deviceSyncInfo[deviceId] = info
            self.addPlog(deviceId, event: "CLEANUP_END", params: "deleted=\(deletedTypes)")
        }
    }

    func onDiagnosticMessage(message: String) {
        // Persist diagnostics even when no UI is active.
        logStore.addEntry(message)
    }

    // MARK: - Helpers

    private static let logTimestampFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss.SSS"
        return f
    }()

    private static let plogTimestampFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        return f
    }()

    /// Emit a PLOG structured event and refresh the UI log.
    private func addPlog(_ deviceId: String, event: String, params: String = "") {
        let ts = Self.plogTimestampFormatter.string(from: Date())
        logStore.addEntry("PLOG|\(ts)|\(deviceId)|\(event)|\(params)")
        scheduleLogRefresh()
    }

    @objc private func scheduleLogRefresh() {
        guard UIApplication.shared.applicationState == .active, logRefreshWork == nil else { return }
        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.logRefreshWork = nil
            guard UIApplication.shared.applicationState == .active else { return }
            self.syncLog = self.logStore.getSyncLogEntries()
        }
        logRefreshWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25, execute: work)
    }

    @objc private func cancelLogRefresh() {
        logRefreshWork?.cancel()
        logRefreshWork = nil
    }

    /// Phone battery level 0-100, or -1 if unavailable.
    private static func getPhoneBatteryLevel() -> Int {
        UIDevice.current.isBatteryMonitoringEnabled = true
        let level = UIDevice.current.batteryLevel
        return level >= 0 ? Int(level * 100) : -1
    }

    private static func getPhoneChargingState() -> String {
        UIDevice.current.isBatteryMonitoringEnabled = true
        switch UIDevice.current.batteryState {
        case .charging: return "CHARGING"
        case .full: return "FULL"
        case .unplugged: return "UNPLUGGED"
        case .unknown: return "UNKNOWN"
        @unknown default: return "UNKNOWN"
        }
    }

    func clearLog() {
        cancelLogRefresh()
        logStore.clear()
        syncLog = []
    }

    func addLog(_ message: String) {
        logStore.addEntry(message)
        print("SYNC [\(Self.logTimestampFormatter.string(from: Date()))] \(message)")
        scheduleLogRefresh()
    }

    private static func formatDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateStyle = .short
        return f.string(from: date)
    }

    private static func formatBytes(_ bytes: Int64) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        if bytes < 1024 * 1024 { return String(format: "%.1f KB", Double(bytes) / 1024.0) }
        return String(format: "%.1f MB", Double(bytes) / (1024.0 * 1024.0))
    }

    private func postSyncNotification(summary: SyncSummary) {
        let tf = Self.logTimestampFormatter
        let startStr = tf.string(from: summary.startTime)
        let endStr = tf.string(from: summary.endTime)

        let isOffline = summary.perTypeStats.keys.contains { $0.isOfflineRecording }
        let label = isOffline ? "Offline recordings" : "Sync complete"

        let content = UNMutableNotificationContent()
        content.title = "\(label) [\(summary.deviceId)]"
        content.subtitle = "\(startStr) → \(endStr) (\(summary.durationSeconds)s)"
        var bodyLines = ["Total: \(summary.totalItems) items, \(Self.formatBytes(summary.totalBytes))"]
        for (type, stats) in summary.perTypeStats {
            bodyLines.append("\(type.rawValue): \(stats.items) items, \(Self.formatBytes(stats.bytes))")
        }
        content.body = bodyLines.joined(separator: "\n")
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "sync-\(summary.deviceId)-\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else {
                return  // silently skip — user was already warned at launch
            }
            UNUserNotificationCenter.current().add(request) { error in
                if let error = error {
                    print("Failed to post sync notification: \(error)")
                }
            }
        }
    }
}
