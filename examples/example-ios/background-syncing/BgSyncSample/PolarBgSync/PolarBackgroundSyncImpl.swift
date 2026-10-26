// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import UIKit
import CoreBluetooth
import PolarBleSdk
import os.log

/// Core implementation of BackgroundSyncApi and LocalDataApi for iOS.
public class PolarBackgroundSyncImpl: NSObject, BackgroundSyncApi, LocalDataApi {
    /// Ensure the device is connected if a sync session exists. If disconnected, attempts to reconnect.
    /// No-op if no session exists for the device.
    public func ensureConnected(deviceId: String) {
        syncQueue.async { [weak self] in
            guard let self = self else { return }
            guard let session = self.activeSessions[deviceId] else { return }
            guard !self.isFetchDraining(deviceId) else { return }
            if session.state == .disconnected {
                Self.logger.debug("ensureConnected: connectToDevice(\(deviceId))")
                do {
                    try self.api.connectToDevice(deviceId)
                } catch {
                    Self.logger.warning("ensureConnected failed for \(deviceId): \(error)")
                }
            }
        }
    }

    private static let logger = Logger(subsystem: "com.polar.bgsync", category: "SyncEngine")

    public static let shared = PolarBackgroundSyncImpl()

    private var api: PolarBleApi
    private let localDataStore: LocalDataStore
    private let syncStateTracker: SyncStateTracker
    private var activeSessions: [String: SyncSession] = [:]
    private let pendingFetches = SyncFetchRegistry<[SyncResult]>()
#if BGSYNC_VERIFICATION
    private var simulateLowBudget = ProcessInfo.processInfo.arguments.contains("--verify-low-background-budget")
    private var simulateFetchExpiration = ProcessInfo.processInfo.arguments.contains("--verify-fetch-expiration")
#endif
    private let syncQueue = DispatchQueue(label: "com.polar.bgsync.sync", qos: .utility)
    private var reconnectionScanTask: Task<Void, Never>?
    private var reconnectionScanTimer: DispatchSourceTimer?
    private var reconnectionNudgeTimer: DispatchSourceTimer?
    private static let reconnectionScanRestartInterval: TimeInterval = 30
    private static let reconnectionNudgeInterval: TimeInterval = 15
    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyyMMdd"
        return f
    }()

    private let baseRequiredFeatures: Set<PolarBleSdkFeature> = [
        .feature_polar_activity_data,
        .feature_polar_file_transfer,
        .feature_polar_device_control
    ]

    private func requiredFeatures(for dataTypes: Set<SyncableDataType>) -> Set<PolarBleSdkFeature> {
        var required = baseRequiredFeatures

        if dataTypes.contains(.sleep) {
            required.insert(.feature_polar_sleep_data)
        }
        if dataTypes.contains(.trainingSessions) {
            required.insert(.feature_polar_training_data)
        }
        if dataTypes.contains(.skinTemperature) ||
            dataTypes.contains(.offlineTemperature) ||
            dataTypes.contains(.offlineSkinTemperature) {
            required.insert(.feature_polar_temperature_data)
        }
        if dataTypes.contains(where: { $0.isOfflineRecording }) {
            required.insert(.feature_polar_offline_recording)
        }

        return required
    }

    override init() {
        api = PolarBleApiDefaultImpl.polarImplementation(
            DispatchQueue.main,
            features: [
                .feature_polar_activity_data,
                .feature_polar_sleep_data,
                .feature_polar_file_transfer,
                .feature_battery_info,
                .feature_polar_temperature_data,
                .feature_polar_device_control,
                .feature_polar_training_data,
                .feature_polar_offline_recording
            ],
            restoreIdentifier: "com.polar.bgsync.central"
        )
        localDataStore = LocalDataStore()
        syncStateTracker = SyncStateTracker()
        localDataStore.syncStateTracker = syncStateTracker
        super.init()

        api.observer = self
        api.deviceFeaturesObserver = self
        api.deviceInfoObserver = self
        api.powerStateObserver = self
        api.automaticReconnection = true
        api.logger = self

        startReconnectionScan()
        startReconnectionNudge()
    }

    // MARK: - BLE scan-based reconnection

    /// Starts a recurring BLE scan that detects when disconnected devices come back
    /// into range (advertising). When a known disconnected device is found, triggers
    /// connectToDevice() immediately rather than waiting for the SDK's auto-reconnection.
    /// The scan is restarted periodically since searchForDevice() emits each device only once.
    private func startReconnectionScan() {
        let timer = DispatchSource.makeTimerSource(queue: syncQueue)
        timer.schedule(deadline: .now(), repeating: Self.reconnectionScanRestartInterval)
        timer.setEventHandler { [weak self] in
            self?.restartReconnectionScanIfNeeded()
        }
        timer.resume()
        reconnectionScanTimer = timer
    }

    private func restartReconnectionScanIfNeeded() {
        let hasDisconnected = activeSessions.values.contains { $0.state == .disconnected }
        guard hasDisconnected else {
            // No disconnected sessions — stop any active scan to save battery
            reconnectionScanTask?.cancel()
            reconnectionScanTask = nil
            return
        }

        // Cancel previous scan and start fresh (to re-detect devices)
        reconnectionScanTask?.cancel()
        Self.logger.info("Reconnection scan: starting BLE scan for disconnected devices")
        reconnectionScanTask = Task { [weak self] in
            do {
                guard let self else { return }
                for try await deviceInfo in self.api.searchForDevice() {
                    if let session = self.activeSessions[deviceInfo.deviceId],
                       !self.isFetchDraining(deviceInfo.deviceId),
                       session.state == .disconnected {
                        self.syncLog("Reconnection scan: detected \(deviceInfo.deviceId) advertising, triggering connect")
                        do {
                            try self.api.connectToDevice(deviceInfo.deviceId)
                        } catch {
                            Self.logger.warning("Reconnection scan: connect failed for \(deviceInfo.deviceId): \(error)")
                        }
                    }
                }
            } catch {
                Self.logger.warning("Reconnection scan error: \(error)")
            }
        }
    }

    // MARK: - Periodic reconnection nudge

    /// Periodically calls connectToDevice() for disconnected sessions to ensure the SDK
    /// keeps them in its reconnection queue. This handles devices (like sport watches) that
    /// don't advertise readily and may not be detected by the scan-based approach.
    private func startReconnectionNudge() {
        let timer = DispatchSource.makeTimerSource(queue: syncQueue)
        timer.schedule(deadline: .now() + Self.reconnectionNudgeInterval,
                       repeating: Self.reconnectionNudgeInterval)
        timer.setEventHandler { [weak self] in
            self?.nudgeDisconnectedSessions()
        }
        timer.resume()
        reconnectionNudgeTimer = timer
    }

    private func nudgeDisconnectedSessions() {
        for (deviceId, session) in activeSessions {
            if session.state == .disconnected && !isFetchDraining(deviceId) {
                Self.logger.debug("Reconnection nudge: connectToDevice(\(deviceId))")
                do {
                    try api.connectToDevice(deviceId)
                } catch {
                    Self.logger.warning("Reconnection nudge failed for \(deviceId): \(error)")
                }
            }
        }
    }

    // MARK: - Internal helpers

    private static let logDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss.SSS"
        return f
    }()

    private func syncLog(_ message: String) {
        let ts = Self.logDateFormatter.string(from: Date())
        let line = "SYNC [\(ts)] \(message)"
        Self.logger.info("\(line)")
        for session in activeSessions.values {
            session.callback.onDiagnosticMessage(message: line)
        }
    }

    /// Session-scoped log that auto-prepends [deviceId] and sends only to that session's callback
    private func syncLog(_ message: String, session: SyncSession) {
        let ts = Self.logDateFormatter.string(from: Date())
        let line = "SYNC [\(ts)] [\(session.deviceId)] \(message)"
        Self.logger.info("\(line)")
        session.callback.onDiagnosticMessage(message: line)
    }

    // MARK: - BackgroundSyncApi

    public func startSync(
        deviceId: String,
        dataTypes: Set<SyncableDataType>,
        config: SyncConfig,
        callback: SyncCallbackDelegate
    ) {
        if activeSessions[deviceId] != nil {
            Self.logger.warning("Sync already active for \(deviceId), restarting")
            stopSync(deviceId: deviceId)
        }

        let session = SyncSession(
            deviceId: deviceId,
            dataTypes: dataTypes,
            config: config,
            callback: callback,
            state: .disconnected,
            readyFeatures: []
        )
        activeSessions[deviceId] = session

        syncLog("PHASE:START — startSync(types: \(dataTypes.map { $0.rawValue }))", session: session)
        guard !isFetchDraining(deviceId) else {
            syncLog("PHASE:CONNECT — waiting for previous fetch to drain and disconnect", session: session)
            callback.onConnectionStateChanged(deviceId: deviceId, state: .disconnected)
            return
        }
        callback.onConnectionStateChanged(deviceId: deviceId, state: .connecting)
        session.state = .connecting

        do {
            try api.connectToDevice(deviceId)
            syncLog("PHASE:CONNECT — connectToDevice() called, waiting for BLE callbacks…", session: session)
        } catch {
            syncLog("PHASE:CONNECT — ERROR connectToDevice() threw: \(error)", session: session)
            callback.onSyncError(deviceId: deviceId, error: SyncError(
                dataType: nil, message: "Failed to connect", cause: error))
        }
    }

    public func stopSync(deviceId: String) {
        guard let session = activeSessions.removeValue(forKey: deviceId) else { return }
        syncLog("PHASE:STOP — stopSync", session: session)
        session.timedSyncWorkItem?.cancel()
        session.timedSyncWorkItem = nil
        // End any active background task so iOS doesn't complain
        endBackgroundTask(session: session)
        // Cancel all in-flight Tasks immediately so ongoing fetches
        // don't block the disconnect or stall the UI.
        session.d2hTask?.cancel()
        session.d2hTask = nil
        session.syncTask?.cancel()
        session.syncTask = nil
        session.cleanupTask?.cancel()
        session.cleanupTask = nil
        if let fetch = pendingFetches[deviceId] {
            pendingFetches.requestDisconnect(deviceId)
            Task { @MainActor in fetch.cancel() }
        }
        do {
            try api.disconnectFromDevice(deviceId)
        } catch {
            Self.logger.warning("Error disconnecting \(deviceId): \(error)")
        }
        session.callback.onConnectionStateChanged(deviceId: deviceId, state: .disconnected)
        session.state = .disconnected
    }

    public func isSyncing(deviceId: String) -> Bool {
        return activeSessions[deviceId]?.state == .syncing
    }

    public func updateDataTypes(deviceId: String, dataTypes: Set<SyncableDataType>) {
        guard let session = activeSessions[deviceId] else { return }
        syncLog("Updating data types: \(dataTypes.map { $0.rawValue })", session: session)
        session.dataTypes = dataTypes
    }

    public func getConnectionState(deviceId: String) -> ConnectionState {
        return activeSessions[deviceId]?.state ?? .disconnected
    }

    public func triggerSyncNow(deviceId: String) {
        guard let session = activeSessions[deviceId] else { return }
        guard !isFetchDraining(deviceId) else {
            syncLog("PHASE:FETCH — waiting for previous request to drain", session: session)
            return
        }
        if session.state == .connected || session.state == .syncing {
            executeSyncCycle(session: session)
        } else if session.state == .disconnected {
            syncLog("triggerSyncNow — device disconnected, requesting reconnection (sync will run on connect)", session: session)
            do {
                try api.connectToDevice(deviceId)
            } catch {
                Self.logger.warning("Reconnect attempt failed for \(deviceId): \(error)")
            }
        }
    }

    public func hasActiveSession(deviceId: String) -> Bool {
        return activeSessions[deviceId] != nil
    }

    @discardableResult
    public func updateCallback(deviceId: String, callback: SyncCallbackDelegate) -> Bool {
        guard let session = activeSessions[deviceId] else { return false }
        session.callback = callback
        return true
    }

    public func shutdown() {
        syncLog("PHASE:SHUTDOWN — shutting down PolarBackgroundSync")
        activeSessions.keys.forEach { stopSync(deviceId: $0) }
        api.cleanup()
    }

    // MARK: - LocalDataApi

    public func getSyncedData(
        deviceId: String,
        dataType: SyncableDataType,
        fromDate: Date?,
        toDate: Date?
    ) -> [SyncedDataEntry] {
        return localDataStore.listEntries(
            deviceId: deviceId, dataType: dataType,
            fromDate: fromDate, toDate: toDate)
    }

    public func readSyncedData(entry: SyncedDataEntry) -> String? {
        let fileContent: String?
        if entry.dataType.isOfflineRecording {
            guard let data = localDataStore.readRawFile(path: entry.localFilePath) else { return nil }
            fileContent = String(data: data, encoding: .utf8)
        } else {
            fileContent = localDataStore.readFile(path: entry.localFilePath)
        }
        
        // Pretty-print all JSON for consistent display across platforms
        guard let jsonStr = fileContent else { return nil }
        if let jsonData = jsonStr.data(using: .utf8),
           let jsonObj = try? JSONSerialization.jsonObject(with: jsonData),
           let prettyData = try? JSONSerialization.data(withJSONObject: jsonObj, options: .prettyPrinted),
           let prettyStr = String(data: prettyData, encoding: .utf8) {
            return prettyStr
        }
        return jsonStr
    }

    public func getDeviceDataPath(deviceId: String) -> String {
        return localDataStore.getDeviceDir(deviceId: deviceId)
    }

    public func setLocalRetention(config: LocalRetentionConfig?) {
        localDataStore.retentionConfig = config
    }

    public func getLocalRetention() -> LocalRetentionConfig? {
        return localDataStore.retentionConfig
    }

    @discardableResult
    public func purgeExpiredData() -> Int {
        return localDataStore.purgeExpired()
    }

    public func deleteAllDeviceData(deviceId: String) {
        localDataStore.deleteDeviceData(deviceId: deviceId)
    }

    // MARK: - Internal sync orchestration

    /// Delay (seconds) after all features are ready before issuing PsFTP commands.
    /// Gives the watch firmware time to fully stabilize its file-transfer session state.
    private static let postFeaturesReadyDelay: TimeInterval = 3.0

    /// Maximum number of retries for the FTU-check + sync-init sequence when timeouts occur.
    private static let maxSyncInitRetries = 2
    /// Backoff delay (seconds) before retrying after a timeout in FTU check or sync init.
    private static let syncInitRetryBackoffSeconds: TimeInterval = 5.0

    private func checkAllFeaturesReady(session: SyncSession) {
        guard activeSessions[session.deviceId] === session,
              !isFetchDraining(session.deviceId) else { return }
        let requiredFeatures = requiredFeatures(for: session.dataTypes)
        guard !session.ftuAndSyncTriggered else { return }
        let ready = session.readyFeatures.map { String(describing: $0) }
        let needed = requiredFeatures.map { String(describing: $0) }
        syncLog("PHASE:FEATURES — ready: \(ready), required: \(needed)", session: session)
        if session.readyFeatures.isSuperset(of: requiredFeatures) {
            session.ftuAndSyncTriggered = true
            syncLog("PHASE:FEATURES — all required features ready", session: session)
            syncLog("PHASE:FEATURES — waiting \(Self.postFeaturesReadyDelay)s for PsFTP stabilization…", session: session)
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.postFeaturesReadyDelay) { [weak self] in
                guard let self = self else { return }
                guard self.activeSessions[session.deviceId] === session,
                      !self.isFetchDraining(session.deviceId),
                      session.state != .disconnected else {
                    self.syncLog("PHASE:FEATURES — device disconnected during stabilization delay, aborting", session: session)
                    return
                }
                self.performFtuCheckAndSync(session: session, attempt: session.syncInitAttempt)
            }
        } else {
            let missing = requiredFeatures.subtracting(session.readyFeatures).map { String(describing: $0) }
            syncLog("PHASE:FEATURES — still waiting for: \(missing)", session: session)
        }
    }

    private func performFtuCheckAndSync(session: SyncSession, attempt: Int) {
        syncLog("PHASE:FTU_CHECK — checking isFtuDone (attempt \(attempt)/\(Self.maxSyncInitRetries + 1))", session: session)
        session.state = .ftuCheck
        session.callback.onConnectionStateChanged(deviceId: session.deviceId, state: .ftuCheck)

        session.syncTask?.cancel()
        session.syncTask = Task { @MainActor [weak self] in
            guard let self, self.activeSessions[session.deviceId] != nil else { return }
            do {
                let isFtuDone = try await self.api.isFtuDone(session.deviceId)
                if isFtuDone {
                    self.syncLog("PHASE:FTU_CHECK — FTU already done", session: session)
                } else {
                    self.syncLog("PHASE:FTU — FTU not done, performing initialization…", session: session)
                    await self.performFtuAsync(session: session)
                }
                self.startD2HListeningAndSync(session: session, syncInitAttempt: attempt)
            } catch {
                guard !Task.isCancelled else { return }
                if self.isTimeoutError(error) && attempt <= Self.maxSyncInitRetries {
                    self.syncLog("PHASE:FTU_CHECK — timeout (attempt \(attempt)), retrying after \(Self.syncInitRetryBackoffSeconds)s…", session: session)
                    self.retrySyncInitAfterBackoff(session: session, attempt: attempt)
                } else {
                    self.syncLog("PHASE:FTU_CHECK — ERROR: \(error), proceeding anyway", session: session)
                    self.startD2HListeningAndSync(session: session, syncInitAttempt: attempt)
                }
            }
        }
    }

    @MainActor
    private func performFtuAsync(session: SyncSession) async {
        let now = Date()
        let formatter = ISO8601DateFormatter()
        let calendar = Calendar.current
        let birthDate = calendar.date(from: DateComponents(year: 1990, month: 1, day: 1))!

        let ftuConfig = PolarFirstTimeUseConfig(
            gender: .male,
            birthDate: birthDate,
            height: 175.0,
            weight: 75.0,
            maxHeartRate: 190,
            vo2Max: 40,
            restingHeartRate: 65,
            trainingBackground: .regular,
            deviceTime: formatter.string(from: now),
            typicalDay: .mostlyMoving,
            sleepGoalMinutes: 480
        )
        do {
            try await self.api.doFirstTimeUse(session.deviceId, ftuConfig: ftuConfig)
            self.syncLog("PHASE:FTU — completed successfully", session: session)
        } catch {
            self.syncLog("PHASE:FTU — ERROR: \(error)", session: session)
            session.callback.onSyncError(
                deviceId: session.deviceId,
                error: SyncError(dataType: nil, message: "FTU failed", cause: error))
        }
    }

    private func isTimeoutError(_ error: Error) -> Bool {
        if error is FetchTimeoutError { return true }
        let desc = String(describing: error).lowercased()
        return desc.contains("timeout") || desc.contains("waittimeout") || desc.contains("timed out")
    }

    private func retrySyncInitAfterBackoff(session: SyncSession, attempt: Int) {
        syncLog("PHASE:RETRY — disconnecting for clean retry…", session: session)
        do {
            try api.disconnectFromDevice(session.deviceId)
        } catch {
            syncLog("PHASE:RETRY — disconnect failed: \(error)", session: session)
        }
        // Wait for backoff, then reconnect
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.syncInitRetryBackoffSeconds) { [weak self] in
            guard let self = self else { return }
            guard self.activeSessions[session.deviceId] === session,
                  !self.isFetchDraining(session.deviceId) else { return }
            self.syncLog("PHASE:RETRY — reconnecting (attempt \(attempt + 1))…", session: session)
            // Reset the feature-ready gate so the flow re-triggers on reconnection
            session.ftuAndSyncTriggered = false
            session.readyFeatures.removeAll()
            session.syncInitAttempt = attempt + 1
            do {
                try self.api.connectToDevice(session.deviceId)
            } catch {
                self.syncLog("PHASE:RETRY — reconnect failed: \(error)", session: session)
            }
        }
    }

    private func startD2HListeningAndSync(session: SyncSession, syncInitAttempt: Int) {
        syncLog("PHASE:D2H — subscribing to device-to-host notifications", session: session)
        session.syncInitAttempt = syncInitAttempt

        session.d2hTask?.cancel()
        session.d2hTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                for try await notification in self.api.observeDeviceToHostNotifications(identifier: session.deviceId) {
                    self.syncLog("PHASE:D2H — notification received: \(notification.notificationType)", session: session)
                    switch notification.notificationType {
                    case .syncRequired, .filesystemModified:
                        self.syncLog("PHASE:D2H — new data detected, triggering sync for this device only", session: session)
                        self.executeSyncCycle(session: session)
                    default:
                        break
                    }
                }
            } catch {
                self.syncLog("PHASE:D2H — ERROR: \(error)", session: session)
            }
        }

        if session.waitingForNextTrigger {
            syncLog("PHASE:SYNC_CYCLE — background-limited cycle closed; waiting for next sync trigger", session: session)
            scheduleTimedSync(session: session)
        } else {
            syncLog("PHASE:SYNC_CYCLE — starting initial sync cycle", session: session)
            executeSyncCycle(session: session)
        }
    }

    /// Minimum background time (seconds) required before starting the next data type fetch.
    /// If remaining time drops below this, close the cycle with saved data.
    private static let backgroundTimeThreshold: TimeInterval = 5.0

    /// Per-data-type fetch timeout (seconds). Prevents the sync task from hanging forever
    /// if the SDK never completes a call (neither success nor error).
    private static let perFetchTimeoutSeconds: TimeInterval = 120

    /// Ends the current background task (if any) and starts a fresh one.
    /// This prevents the iOS "created over 30 seconds ago" warning by keeping each task short-lived.
    private func renewBackgroundTask(session: SyncSession, onExpiration: (() -> Void)? = nil) {
        if let oldId = session.backgroundTaskId, oldId != .invalid {
            UIApplication.shared.endBackgroundTask(oldId)
            session.backgroundTaskId = nil
        }
        var taskId: UIBackgroundTaskIdentifier = .invalid
        taskId = UIApplication.shared.beginBackgroundTask(withName: "PolarSync-\(session.deviceId)") { [weak self] in
            // CRITICAL: end THIS specific task immediately — iOS requires it
            // before the expiration handler returns.
            UIApplication.shared.endBackgroundTask(taskId)
            // Dispatch session cleanup and suspension to main queue for thread safety
            // (the expiration handler fires on an arbitrary system queue).
            DispatchQueue.main.async {
                guard session.backgroundTaskId == taskId else { return }
                session.backgroundTaskId = nil
                self?.syncLog("PHASE:SYNC_CYCLE — background task \(taskId.rawValue) expired", session: session)
                if let onExpiration {
                    onExpiration()
                } else {
                    self?.suspendSyncCycle(session: session)
                }
            }
        }
        session.backgroundTaskId = taskId
    }

    /// Ends the session's current background task if one is active.
    private func endBackgroundTask(session: SyncSession) {
        if let taskId = session.backgroundTaskId, taskId != .invalid {
            UIApplication.shared.endBackgroundTask(taskId)
            session.backgroundTaskId = nil
        }
    }

    private func executeSyncCycle(session: SyncSession) {
        guard activeSessions[session.deviceId] === session,
              !isFetchDraining(session.deviceId) else { return }
        guard session.state != .syncing else {
            syncLog("PHASE:SYNC_CYCLE — already in progress, skipping", session: session)
            return
        }

        // Transfers use an expiration-aware background task to bridge SDK requests;
        // this is a finite budget, not a guarantee of continuous execution.

        let isNewCycle = session.fetchPlan.beginCycle(dataTypes: Array(session.dataTypes))
#if BGSYNC_VERIFICATION
        if isNewCycle && simulateLowBudget {
            syncLog("VERIFICATION: low-budget scenario armed; will simulate once in background after two completed types and saved data", session: session)
        }
        if isNewCycle && simulateFetchExpiration {
            syncLog("VERIFICATION: fetch-expiration scenario armed; waiting for background HR/PPI request with previously saved data", session: session)
        }
#endif
        session.waitingForNextTrigger = false
        syncLog(isNewCycle ? "PHASE:SYNC_CYCLE — beginning sync cycle"
            : "PHASE:SYNC_CYCLE — continuing \(session.fetchPlan.pendingTypes.count) remaining data types", session: session)
        session.state = .syncing
        if isNewCycle {
            session.syncStartTime = Date()
            session.offlineSyncStartTime = nil
            session.offlineSyncEndTime = nil
            session.syncCycleResults = []
            let batteryLine = BatteryLevelMonitor.shared.logEvent(deviceId: session.deviceId, event: "SYNC_STARTED")
            session.callback.onDiagnosticMessage(message: batteryLine)
            session.callback.onSyncStarted(deviceId: session.deviceId)
        }
        session.callback.onConnectionStateChanged(deviceId: session.deviceId, state: .syncing)

        syncLog("PHASE:SYNC_INIT — sending initialization and start sync notifications", session: session)

        session.syncTask?.cancel()
        session.syncTask = Task { @MainActor [weak self] in
            guard let self, self.activeSessions[session.deviceId] != nil else { return }
            do {
                try await self.api.sendInitializationAndStartSyncNotifications(identifier: session.deviceId)
                self.syncLog("PHASE:SYNC_INIT — completed, fetching data types…", session: session)
                // Reset attempt counter on success for future D2H-triggered cycles
                session.syncInitAttempt = 1
                await self.syncAllDataTypesAsync(session: session)
            } catch {
                guard !Task.isCancelled else { return }
                self.syncLog("PHASE:SYNC_INIT — ERROR: \(error)", session: session)
                if self.isTimeoutError(error) && session.syncInitAttempt <= Self.maxSyncInitRetries {
                    self.syncLog("PHASE:SYNC_INIT — timeout on attempt \(session.syncInitAttempt), will disconnect and retry…", session: session)
                    session.state = .connected  // Allow executeSyncCycle to re-enter
                    self.retrySyncInitAfterBackoff(session: session, attempt: session.syncInitAttempt)
                } else {
                    session.callback.onSyncError(
                        deviceId: session.deviceId,
                        error: SyncError(dataType: nil, message: "Sync init failed", cause: error))
                    self.finishSyncCycle(session: session)
                }
            }
        }
    }

    @MainActor
    private func syncAllDataTypesAsync(session: SyncSession) async {
        syncLog("PHASE:FETCH — pending data types: \(session.fetchPlan.pendingTypes.map { $0.rawValue })", session: session)

        while let dataType = session.fetchPlan.next {
            if Task.isCancelled || session.state == .disconnected { break }
            guard activeSessions[session.deviceId] != nil else { break }

#if BGSYNC_VERIFICATION
            if simulateLowBudget, UIApplication.shared.applicationState == .background,
               session.fetchPlan.completedTypeCount >= 2, !session.syncCycleResults.isEmpty {
                simulateLowBudget = false
                syncLog("VERIFICATION: SIMULATED low background budget after \(session.fetchPlan.completedTypeCount) completed types; not an iOS expiration", session: session)
                suspendSyncCycle(session: session)
                return
            }
#endif
            // Leave enough time to publish the partial cycle before suspension.
            let remaining = UIApplication.shared.backgroundTimeRemaining
            if remaining != .greatestFiniteMagnitude && remaining < Self.backgroundTimeThreshold {
                syncLog("PHASE:FETCH — background time low (\(String(format: "%.1f", remaining))s)", session: session)
                suspendSyncCycle(session: session)
                return
            }

            // Track offline recording timing + fire offline group start
            if dataType.isOfflineRecording && session.offlineSyncStartTime == nil {
                session.offlineSyncStartTime = Date()
                session.callback.onOfflineRecordingGroupStarted(deviceId: session.deviceId)
            }

            // Notify app that this data type is about to be fetched
            session.callback.onDataTypeStarted(deviceId: session.deviceId, dataType: dataType)

            let fetcher = DataTypeFetcher(api: api, localDataStore: localDataStore,
                syncStateTracker: syncStateTracker,
                autoDeleteFromDevice: session.config.autoDeleteFromDevice,
                offlineRecordingRetentionDays: session.config.offlineRecordingRetentionDays,
                onDiagnosticMessage: { [weak self] message in
                    self?.syncLog(message, session: session)
#if BGSYNC_VERIFICATION
                    if message.contains("PHASE:FETCH_DAY"), message.hasSuffix(": requesting") {
                        self?.scheduleVerificationExpiration(session: session, dataType: dataType)
                    }
#endif
                },
                resumeDate: session.resumeDates[dataType],
                onDayCompleted: { nextDay in
                    session.resumeDates[dataType] = nextDay
                },
                onResults: { [weak self] results in
                    guard let self, self.activeSessions[session.deviceId] === session,
                          !Task.isCancelled else { return }
                    session.syncCycleResults.append(contentsOf: results)
                    if !results.isEmpty { session.typesWithSavedResults.insert(dataType) }
                    results.forEach { session.callback.onDataSynced(result: $0) }
                },
                hadSavedResults: session.typesWithSavedResults.contains(dataType))

            do {
                let fetch = SyncFetchOperation<[SyncResult]>()
                pendingFetches[session.deviceId] = fetch
                session.fetchingDataType = dataType
                renewBackgroundTask(session: session, onExpiration: { [weak self] in
                    guard let self, self.pendingFetches[session.deviceId] === fetch else { return }
                    self.expirePendingFetch(session: session, fetch: fetch)
                })
                defer { endBackgroundTask(session: session) }
                let results = try await fetch.run(
                    seconds: Self.perFetchTimeoutSeconds,
                    operation: {
                        try await fetcher.fetchAndSave(deviceId: session.deviceId, dataType: dataType)
                    },
                    onTimeout: { [weak self] in
                        self?.recoverFailedFetch(
                            session: session, dataType: dataType,
                            reason: "Fetch exceeded \(Int(Self.perFetchTimeoutSeconds))s",
                            cause: FetchTimeoutError())
                    },
                    onDrained: { [weak self] in
                        self?.pendingFetches[session.deviceId] = nil
                        session.fetchingDataType = nil
                        self?.resumeAfterFetchDrain(deviceId: session.deviceId)
                    })
                try Task.checkCancellation()
                guard activeSessions[session.deviceId] === session else { return }
                session.fetchPlan.complete(dataType)
                session.resumeDates[dataType] = nil
                session.typesWithSavedResults.remove(dataType)
                Self.logger.info("PHASE:FETCH — \(dataType.rawValue) — \(results.count) entries")
                if dataType.isOfflineRecording {
                    session.offlineSyncEndTime = Date()
                    // Fire offline group completed when moving to a non-offline type or last type
                    let nextDataType = session.fetchPlan.next
                    if nextDataType == nil || !nextDataType!.isOfflineRecording {
                        session.callback.onOfflineRecordingGroupCompleted(deviceId: session.deviceId)
                    }
                }
            } catch {
                if error is FetchTimeoutError { return }
                if error is CancellationError { return }
                if Task.isCancelled { break }
                let willRetry = session.fetchPlan.recordFailure(dataType)
                let action = willRetry ? "retrying after remaining data types"
                    : "retry limit reached; skipping until next notification, timed or foreground sync"
                syncLog("PHASE:FETCH — \(dataType.rawValue) ERROR: \(error); \(action)", session: session)
                if dataType.isOfflineRecording {
                    session.offlineSyncEndTime = Date()
                }
                session.callback.onSyncError(
                    deviceId: session.deviceId,
                    error: SyncError(dataType: dataType, message: "Failed to sync \(dataType): \(action)", cause: error))
            }
        }

        if !Task.isCancelled, session.state == .syncing,
           activeSessions[session.deviceId] === session {
            performDeviceCleanupAndFinish(session: session)
        }
    }

    private func isFetchDraining(_ deviceId: String) -> Bool {
        pendingFetches.isBlocked(deviceId)
    }

    @MainActor
    private func expirePendingFetch(session: SyncSession, fetch: SyncFetchOperation<[SyncResult]>) {
        guard activeSessions[session.deviceId] === session,
              pendingFetches[session.deviceId] === fetch, session.state == .syncing else { return }
        fetch.cancel()
        suspendSyncCycle(session: session)
    }

#if BGSYNC_VERIFICATION
    private func scheduleVerificationExpiration(session: SyncSession, dataType: SyncableDataType) {
        guard simulateFetchExpiration, [.hrSamples247, .ppiSamples247].contains(dataType),
              !session.syncCycleResults.isEmpty,
              UIApplication.shared.applicationState == .background,
              let fetch = pendingFetches[session.deviceId] else { return }
        Task { @MainActor [weak self] in
            do {
                try await Task.sleep(nanoseconds: 2_000_000_000)
            } catch { return }
            guard let self, self.simulateFetchExpiration,
                  self.activeSessions[session.deviceId] === session,
                  self.pendingFetches[session.deviceId] === fetch,
                  UIApplication.shared.applicationState == .background,
                  session.state == .syncing else { return }
            self.simulateFetchExpiration = false
            self.syncLog("VERIFICATION: SIMULATED background expiration during \(dataType.rawValue) request; not an iOS expiration", session: session)
            self.expirePendingFetch(session: session, fetch: fetch)
        }
    }
#endif

    private func recoverFailedFetch(
        session: SyncSession, dataType: SyncableDataType, reason: String, cause: Error
    ) {
        guard activeSessions[session.deviceId] === session, session.state == .syncing else { return }
        let willRetry = session.fetchPlan.recordFailure(dataType)
        let action = willRetry ? "retrying after remaining data types"
            : "retry limit reached; skipping until next notification, timed or foreground sync"
        syncLog("PHASE:FETCH — \(dataType.rawValue): \(reason); \(action); disconnecting and draining SDK request before continuing", session: session)
        session.timedSyncWorkItem?.cancel()
        session.d2hTask?.cancel()
        session.state = .disconnected
        session.readyFeatures.removeAll()
        session.ftuAndSyncTriggered = false
        pendingFetches.requestDisconnect(session.deviceId)
        endBackgroundTask(session: session)
        session.callback.onSyncError(
            deviceId: session.deviceId,
            error: SyncError(dataType: dataType,
                             message: "\(reason); \(action)",
                             cause: cause))
        session.callback.onConnectionStateChanged(deviceId: session.deviceId, state: .disconnected)
        do {
            try api.disconnectFromDevice(session.deviceId)
        } catch {
            syncLog("PHASE:FETCH — recovery disconnect failed: \(error)", session: session)
            session.callback.onSyncError(
                deviceId: session.deviceId,
                error: SyncError(dataType: dataType, message: "Fetch recovery disconnect failed", cause: error))
        }
    }

    private func resumeAfterFetchDrain(deviceId: String) {
        guard let session = activeSessions[deviceId], !isFetchDraining(deviceId) else { return }
        if session.state == .disconnected {
            syncLog("PHASE:FETCH — request drained; reconnecting", session: session)
            do {
                try api.connectToDevice(deviceId)
            } catch {
                syncLog("PHASE:FETCH — recovery reconnect failed: \(error)", session: session)
                session.callback.onSyncError(deviceId: deviceId,
                    error: SyncError(dataType: nil, message: "Fetch recovery reconnect failed", cause: error))
            }
        } else {
            checkAllFeaturesReady(session: session)
        }
    }

    private func performDeviceCleanupAndFinish(session: SyncSession) {
        let allTypesCompleted = session.fetchPlan.exhaustedTypes.isEmpty &&
            session.fetchPlan.postponedTypes.isEmpty
        // Finish the sync cycle first — emit summary, notify completion, release sync channel.
        // Device cleanup runs independently afterwards so it doesn't block multi-device sync.
        finishSyncCycle(session: session)

        // Run cleanup asynchronously after sync is done
        if session.config.autoDeleteFromDevice && allTypesCompleted {
            performDeviceCleanup(session: session)
        } else if session.config.autoDeleteFromDevice {
            syncLog("PHASE:CLEANUP — skipped because some data types failed", session: session)
        }
    }

    /// Runs device data cleanup (deletion of old synced data) independently from the sync cycle.
    private func performDeviceCleanup(session: SyncSession) {
        guard activeSessions[session.deviceId] != nil else { return }

        syncLog("PHASE:CLEANUP — deleting old data from device (retention: \(session.config.deviceRetentionDays) days)", session: session)
        let batteryLine = BatteryLevelMonitor.shared.logEvent(deviceId: session.deviceId, event: "CLEANUP_STARTED")
        session.callback.onDiagnosticMessage(message: batteryLine)
        session.callback.onDeviceCleanupStarted(deviceId: session.deviceId)

        let deleter = DeviceDataDeleter(api: api, localDataStore: localDataStore)
        session.cleanupTask?.cancel()
        session.cleanupTask = Task { @MainActor [weak self] in
            guard let self, self.activeSessions[session.deviceId] === session else { return }
            do {
                try Task.checkCancellation()
                try await deleter.deleteOldSyncedData(
                    deviceId: session.deviceId,
                    dataTypes: session.dataTypes,
                    retentionDays: session.config.deviceRetentionDays
                )
                try Task.checkCancellation()
                self.syncLog("PHASE:CLEANUP — device cleanup completed", session: session)
                let batteryLine = BatteryLevelMonitor.shared.logEvent(deviceId: session.deviceId, event: "CLEANUP_COMPLETED")
                session.callback.onDiagnosticMessage(message: batteryLine)
                session.callback.onDeviceCleanupCompleted(deviceId: session.deviceId, deletedTypes: session.dataTypes.count)
            } catch {
                guard !Task.isCancelled else { return }
                self.syncLog("PHASE:CLEANUP — ERROR: \(error)", session: session)
                session.callback.onSyncError(
                    deviceId: session.deviceId,
                    error: SyncError(dataType: nil, message: "Cleanup failed", cause: error))
                session.callback.onDeviceCleanupCompleted(deviceId: session.deviceId, deletedTypes: 0)
            }
        }
    }

    /// Closes a background-limited cycle without scheduling more work.
    private func suspendSyncCycle(session: SyncSession) {
        guard session.state == .syncing else { return }
        session.fetchPlan.postponeRemaining()
        session.waitingForNextTrigger = true
        syncLog("PHASE:POSTPONE — background budget exhausted; postponed data types: \(session.fetchPlan.postponedTypes.map { $0.rawValue }); closing cycle with saved data", session: session)
        let hasPendingRequest = pendingFetches[session.deviceId] != nil
        if hasPendingRequest {
            pendingFetches.requestDisconnect(session.deviceId)
            session.state = .disconnected
            session.readyFeatures.removeAll()
            session.ftuAndSyncTriggered = false
            session.d2hTask?.cancel()
            session.callback.onSyncError(
                deviceId: session.deviceId,
                error: SyncError(dataType: session.fetchingDataType,
                    message: "Background execution expired; unfinished data types postponed to next sync trigger",
                    cause: FetchBackgroundTimeExpiredError()))
        }
        finishSyncCycle(session: session, backgroundLimited: true)
        if hasPendingRequest {
            do {
                try api.disconnectFromDevice(session.deviceId)
            } catch {
                syncLog("PHASE:POSTPONE — disconnect failed: \(error)", session: session)
                session.callback.onSyncError(deviceId: session.deviceId,
                    error: SyncError(dataType: nil, message: "Background-expiration disconnect failed", cause: error))
            }
        }
    }

    private func finishSyncCycle(session: SyncSession, backgroundLimited: Bool = false) {
        if !session.fetchPlan.exhaustedTypes.isEmpty {
            syncLog("PHASE:FINISH — exhausted data types: \(session.fetchPlan.exhaustedTypes.map { $0.rawValue }); keeping session active for next notification, timed or foreground sync", session: session)
        }
        session.fetchPlan.finishCycle()
        // Brief background task for CPU-bound summary building, callbacks, and local purge.
        // This completes in well under a second — no risk of exceeding the 30s limit.
        if !backgroundLimited {
            renewBackgroundTask(session: session)
        }

        if !isFetchDraining(session.deviceId) {
            syncLog("PHASE:FINISH — sending terminate and stop sync notifications", session: session)
            // Do not issue terminate while an interrupted fetch is still draining.
            Task { @MainActor [weak self] in
                do {
                    try await self?.api.sendTerminateAndStopSyncNotifications(identifier: session.deviceId)
                    self?.syncLog("PHASE:FINISH — sync cycle complete", session: session)
                } catch {
                    self?.syncLog("PHASE:FINISH — terminate notifications error: \(error)", session: session)
                }
            }
        }

        if activeSessions[session.deviceId] != nil {
            // Build sync summary
            let endTime = Date()
            let startTime = session.syncStartTime ?? endTime
            let durationSeconds = Int(endTime.timeIntervalSince(startTime))

            // Split results into activity data and offline recordings
            let activityResults = session.syncCycleResults.filter { !$0.dataType.isOfflineRecording }
            let offlineResults = session.syncCycleResults.filter { $0.dataType.isOfflineRecording }
            let requestedOfflineTypes = session.dataTypes.filter { $0.isOfflineRecording }

            // Activity data summary
            if !activityResults.isEmpty {
                let summary = buildSummary(
                    deviceId: session.deviceId, results: activityResults,
                    startTime: startTime, endTime: endTime, durationSeconds: durationSeconds)
                logSyncSummary("Sync Summary", session: session, summary: summary)
                session.callback.onSyncSummary(summary: summary)
            }

            // Offline recordings summary (separate notification) — always emit if offline types were requested
            if !requestedOfflineTypes.isEmpty {
                let offlineStart = session.offlineSyncStartTime ?? startTime
                let offlineEnd = session.offlineSyncEndTime ?? endTime
                let offlineDuration = Int(offlineEnd.timeIntervalSince(offlineStart))
                // Include zero-count entries for all requested offline types
                var perTypeStats: [SyncableDataType: TypeSyncStats] = [:]
                for type in requestedOfflineTypes {
                    let matching = offlineResults.filter { $0.dataType == type }
                    perTypeStats[type] = TypeSyncStats(
                        items: matching.count,
                        bytes: matching.reduce(0) { $0 + $1.sizeBytes })
                }
                let summary = SyncSummary(
                    deviceId: session.deviceId, startTime: offlineStart, endTime: offlineEnd,
                    durationSeconds: offlineDuration,
                    totalItems: offlineResults.count,
                    totalBytes: offlineResults.reduce(0) { $0 + $1.sizeBytes },
                    perTypeStats: perTypeStats)
                logSyncSummary("Offline Recordings Summary", session: session, summary: summary)
                session.callback.onSyncSummary(summary: summary)
            }

            // If nothing was synced and no offline types requested, still emit an empty summary
            if activityResults.isEmpty && requestedOfflineTypes.isEmpty {
                let summary = SyncSummary(
                    deviceId: session.deviceId, startTime: startTime, endTime: endTime,
                    durationSeconds: durationSeconds, totalItems: 0, totalBytes: 0, perTypeStats: [:])
                logSyncSummary("Sync Summary", session: session, summary: summary)
                session.callback.onSyncSummary(summary: summary)
            }

            if session.state != .disconnected {
                session.state = .connected
            }
            let batteryLine = BatteryLevelMonitor.shared.logEvent(deviceId: session.deviceId, event: "SYNC_COMPLETED")
            session.callback.onDiagnosticMessage(message: batteryLine)
            session.callback.onSyncCompleted(deviceId: session.deviceId)
            session.callback.onConnectionStateChanged(deviceId: session.deviceId, state: session.state)
        }

        // End background task now that sync is done
        endBackgroundTask(session: session)

        localDataStore.purgeExpired()

        scheduleTimedSync(session: session)
    }

    private static func formatBytes(_ bytes: Int64) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        if bytes < 1024 * 1024 { return String(format: "%.1f KB", Double(bytes) / 1024.0) }
        return String(format: "%.1f MB", Double(bytes) / (1024.0 * 1024.0))
    }

    private func buildSummary(
        deviceId: String, results: [SyncResult],
        startTime: Date, endTime: Date, durationSeconds: Int
    ) -> SyncSummary {
        var perTypeStats: [SyncableDataType: TypeSyncStats] = [:]
        for result in results {
            let existing = perTypeStats[result.dataType]
            perTypeStats[result.dataType] = TypeSyncStats(
                items: (existing?.items ?? 0) + 1,
                bytes: (existing?.bytes ?? 0) + result.sizeBytes
            )
        }
        return SyncSummary(
            deviceId: deviceId, startTime: startTime, endTime: endTime,
            durationSeconds: durationSeconds,
            totalItems: results.count,
            totalBytes: results.reduce(0) { $0 + $1.sizeBytes },
            perTypeStats: perTypeStats
        )
    }

    private func logSyncSummary(_ label: String, session: SyncSession, summary: SyncSummary) {
        let tf = Self.logDateFormatter
        var logLines = ["── \(label) [\(session.deviceId)] ──"]
        logLines.append("  Start: \(tf.string(from: summary.startTime))  End: \(tf.string(from: summary.endTime))  Duration: \(summary.durationSeconds)s")
        logLines.append("  Total: \(summary.totalItems) items, \(Self.formatBytes(summary.totalBytes))")
        for (type, stats) in summary.perTypeStats {
            logLines.append("    \(type.rawValue): \(stats.items) items, \(Self.formatBytes(stats.bytes))")
        }
        Self.logger.info("\(logLines.joined(separator: "\n"))")
    }

    private func scheduleTimedSync(session: SyncSession) {
        session.timedSyncWorkItem?.cancel()
        session.timedSyncWorkItem = nil
        let intervalMinutes = session.config.syncIntervalMinutes
        guard intervalMinutes > 0 else { return }
        syncLog("PHASE:TIMER — timed sync scheduled in \(intervalMinutes) min", session: session)
        let workItem = DispatchWorkItem { [weak self] in
            guard let self = self,
                  self.activeSessions[session.deviceId] != nil,
                  session.state != .disconnected else { return }
            self.syncLog("PHASE:TIMER — timed sync firing", session: session)
            self.executeSyncCycle(session: session)
        }
        session.timedSyncWorkItem = workItem
        syncQueue.asyncAfter(deadline: .now() + .seconds(intervalMinutes * 60), execute: workItem)
    }

}

// MARK: - PolarBleApiObserver

extension PolarBackgroundSyncImpl: PolarBleApiObserver {
    public func deviceConnecting(_ identifier: PolarDeviceInfo) {
        syncLog("BLE: deviceConnecting(\(identifier.deviceId))")
        guard let session = activeSessions[identifier.deviceId] else {
            syncLog("BLE: ⚠ no active session for '\(identifier.deviceId)' (sessions: \(Array(activeSessions.keys)))")
            return
        }
        session.state = .connecting
        session.callback.onConnectionStateChanged(deviceId: identifier.deviceId, state: .connecting)
    }

    public func deviceConnected(_ identifier: PolarDeviceInfo) {
        syncLog("BLE: deviceConnected(\(identifier.deviceId))")
        guard let session = activeSessions[identifier.deviceId] else {
            syncLog("BLE: ⚠ no active session for '\(identifier.deviceId)' (sessions: \(Array(activeSessions.keys)))")
            return
        }
        session.state = .connected
        // Note: readyFeatures is NOT cleared here — it was already cleared in
        // deviceDisconnected(). Clearing here would race with bleSdkFeatureReady()
        // which can fire before deviceConnected on reconnection (cached services).
        session.callback.onConnectionStateChanged(deviceId: identifier.deviceId, state: .connected)
    }

    public func deviceDisconnected(_ identifier: PolarDeviceInfo, pairingError: Bool) {
        syncLog("BLE: deviceDisconnected(\(identifier.deviceId), pairingError: \(pairingError))")
        let wasAwaitingFetchDisconnect = pendingFetches.didDisconnect(identifier.deviceId)
        guard let session = activeSessions[identifier.deviceId] else { return }
        if session.state == .syncing, let dataType = session.fetchingDataType {
            let retry = session.fetchPlan.recordFailure(dataType)
            let action = retry ? "retrying after remaining data types" : "retry limit reached; skipping until next cycle"
            syncLog("PHASE:FETCH — \(dataType.rawValue) interrupted by disconnect; \(action)", session: session)
            session.callback.onSyncError(deviceId: session.deviceId,
                error: SyncError(dataType: dataType, message: "Fetch interrupted by disconnect; \(action)", cause: nil))
        }
        session.state = .disconnected
        session.readyFeatures = []
        session.ftuAndSyncTriggered = false
        session.timedSyncWorkItem?.cancel()
        session.timedSyncWorkItem = nil
        // Cancel all in-flight Tasks (D2H subscription, sync cycle) for this session
        session.d2hTask?.cancel()
        session.d2hTask = nil
        session.syncTask?.cancel()
        session.syncTask = nil
        session.cleanupTask?.cancel()
        session.cleanupTask = nil
        session.callback.onConnectionStateChanged(deviceId: identifier.deviceId, state: .disconnected)
        if wasAwaitingFetchDisconnect {
            resumeAfterFetchDrain(deviceId: identifier.deviceId)
        }
    }
}

// MARK: - PolarBleApiPowerStateObserver

extension PolarBackgroundSyncImpl: PolarBleApiPowerStateObserver {
    public func blePowerOn() {
        syncLog("BLE: power ON")
    }

    public func blePowerOff() {
        syncLog("BLE: ⚠ power OFF — Bluetooth is disabled")
    }
}

// MARK: - PolarBleApiDeviceFeaturesObserver

extension PolarBackgroundSyncImpl: PolarBleApiDeviceFeaturesObserver {
    public func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdkFeature) {
        syncLog("BLE: featureReady(\(identifier), \(feature))")
        guard let session = activeSessions[identifier] else { return }
        session.readyFeatures.insert(feature)
        checkAllFeaturesReady(session: session)
    }
}

// MARK: - PolarBleApiLogger

extension PolarBackgroundSyncImpl: PolarBleApiLogger {
    public func message(_ str: String) {
        Self.logger.debug("SDK: \(str)")
    }
}

// MARK: - PolarBleApiDeviceInfoObserver

extension PolarBackgroundSyncImpl: PolarBleApiDeviceInfoObserver {
    public func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        Self.logger.info("Battery level for \(identifier): \(batteryLevel)%")
        BatteryLevelMonitor.shared.updateDeviceBattery(identifier, level: batteryLevel)
        activeSessions[identifier]?.callback.onBatteryLevelReceived(deviceId: identifier, level: batteryLevel)
    }

    public func batteryChargingStatusReceived(_ identifier: String, chargingStatus: BleBasClient.ChargeState) {
        Self.logger.debug("Battery charging status for \(identifier): \(String(describing: chargingStatus))")
        BatteryLevelMonitor.shared.updateDeviceChargingState(identifier, chargingStatus: chargingStatus)
    }

    public func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        Self.logger.debug("DIS info: \(uuid) = \(value)")
    }

    public func disInformationReceivedWithKeysAsStrings(_ identifier: String, key: String, value: String) {
        Self.logger.debug("DIS info: \(key) = \(value)")
    }
}

// MARK: - SyncSession

class SyncSession {
    let deviceId: String
    var dataTypes: Set<SyncableDataType>
    let config: SyncConfig
    var callback: SyncCallbackDelegate
    var d2hTask: Task<Void, Never>?
    var syncTask: Task<Void, Never>?
    var cleanupTask: Task<Void, Never>?
    var state: ConnectionState
    var readyFeatures: Set<PolarBleSdkFeature>
    var ftuAndSyncTriggered: Bool
    var timedSyncWorkItem: DispatchWorkItem?
    var backgroundTaskId: UIBackgroundTaskIdentifier?
    var syncStartTime: Date?
    var offlineSyncStartTime: Date?
    var offlineSyncEndTime: Date?
    var syncCycleResults: [SyncResult]
    var syncInitAttempt: Int
    var fetchPlan = SyncFetchPlan(maxRetries: 3)
    var fetchingDataType: SyncableDataType?
    var waitingForNextTrigger = false
    var resumeDates: [SyncableDataType: Date] = [:]
    var typesWithSavedResults: Set<SyncableDataType> = []

    init(deviceId: String, dataTypes: Set<SyncableDataType>, config: SyncConfig,
         callback: SyncCallbackDelegate,
         state: ConnectionState, readyFeatures: Set<PolarBleSdkFeature>) {
        self.deviceId = deviceId
        self.dataTypes = dataTypes
        self.config = config
        self.callback = callback
        self.state = state
        self.readyFeatures = readyFeatures
        self.ftuAndSyncTriggered = false
        self.timedSyncWorkItem = nil
        self.backgroundTaskId = nil
        self.syncStartTime = nil
        self.offlineSyncStartTime = nil
        self.offlineSyncEndTime = nil
        self.syncCycleResults = []
        self.syncInitAttempt = 1
    }
}
