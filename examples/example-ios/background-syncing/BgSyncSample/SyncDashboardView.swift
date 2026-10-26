// Copyright © 2026 Polar Electro Oy. All rights reserved.

import SwiftUI
import UniformTypeIdentifiers

private struct SyncLogExport: Transferable {
    static var transferRepresentation: some TransferRepresentation {
        DataRepresentation(exportedContentType: .plainText) { _ in
            let text = SyncLogStore.shared.getDisplayEntries().reversed().joined(separator: "\n")
            return Data(text.utf8)
        }
    }
}

private let dashboardSyncStatusColor = Color(red: 126.0/255.0, green: 87.0/255.0, blue: 194.0/255.0)
private let dashboardCleanupStatusColor = Color(red: 66.0/255.0, green: 165.0/255.0, blue: 245.0/255.0)

struct SyncDashboardView: View {
    @StateObject private var manager = SyncManager.shared
    @State private var showDataBrowser = false
    @State private var showLogGraph = false
    @State private var currentGraphData: LogGraphData?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    connectionSection
                    dataTypeTogglesSection
                    deletionSettingsSection
                    syncTriggersSection
                    localRetentionSection
                    syncLogSection
                    aboutSection
                }
                .padding()
            }
            .navigationTitle("Polar Background Sync")
            .navigationDestination(isPresented: $showDataBrowser) {
                DataBrowserView(deviceIds: manager.deviceIds)
            }
            .navigationDestination(isPresented: $showLogGraph) {
                if let graphData = currentGraphData {
                    LogGraphView(
                        graphData: graphData,
                        allDevices: manager.deviceIds,
                        loadGraphData: { deviceId in
                            LogGraphData.parse(
                                deviceId: deviceId,
                                logLines: SyncLogStore.shared.getAllMessages()
                            )
                        }
                    )
                }
            }
            .onAppear {
                if manager.deviceIds.isEmpty {
                    let saved = manager.savedDeviceIds
                    if !saved.isEmpty {
                        manager.deviceIds = saved
                        if manager.wasPreviouslyConnected && manager.connectionStates.isEmpty {
                            manager.startSync()
                        }
                    }
                }
            }
            .alert("Sync Failed", isPresented: Binding(
                get: { manager.failedSyncDevice != nil },
                set: { if !$0 { manager.dismissSyncFailure() } }
            )) {
                Button("Retry") { manager.retrySyncForFailedDevice() }
                Button("Remove", role: .destructive) {
                    if let deviceId = manager.failedSyncDevice {
                        manager.removeDevice(deviceId)
                    }
                }
            } message: {
                if let deviceId = manager.failedSyncDevice {
                    Text("Failed to sync with device \(deviceId). Would you like to retry or remove the device?")
                }
            }
        }
    }

    // MARK: - Sections

    private var connectionSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Devices").font(.headline)

            HStack {
                TextField("Device ID (e.g. B5F8B5C3)", text: $manager.deviceIdInput)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.allCharacters)
                    .disableAutocorrection(true)
                Button("Add") {
                    manager.addDevice()
                }
                .buttonStyle(.borderedProminent)
                .disabled(manager.deviceIdInput.isEmpty)
            }

            if !manager.deviceIds.isEmpty {
                Divider()

                ForEach(manager.deviceIds, id: \.self) { deviceId in
                    HStack(spacing: 6) {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(deviceId).font(.caption).fontWeight(.medium)
                                if let battery = manager.batteryLevels[deviceId] {
                                    let isStale = Date().timeIntervalSince(battery.receivedAt) > 300
                                    batteryIndicator(level: battery.level)
                                        .opacity(isStale ? 0.4 : 1.0)
                                }
                            }
                            if let info = manager.deviceSyncInfo[deviceId], let time = info.lastSyncTime {
                                Text("Last sync: \(Self.formatSyncTime(time))")
                                    .font(.caption2)
                                    .foregroundColor(info.status == .error ? .orange : .green)
                            }
                        }
                        Spacer()
                        statusChip(for: deviceId)
                        Button(role: .destructive) {
                            manager.removeDevice(deviceId)
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.red)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.vertical, 2)
                }

                Divider()
            }

            VStack(spacing: 8) {
                if !manager.isSyncing {
                    Button(action: { manager.startSync() }) {
                        Text("Start Background Sync")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.polarRed)
                    .disabled(manager.deviceIds.isEmpty)
                } else {
                    Button(action: { manager.stopSync() }) {
                        Text("Stop Background Sync")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)

                    Button(action: { manager.triggerSyncNow() }) {
                        Text("Synchronize Now")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }

                Button(action: { showDataBrowser = true }) {
                    Text("Browse Synced Data")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(manager.deviceIds.isEmpty)

                if !manager.deviceIds.isEmpty {
                    Button("Disconnect and Forget All") {
                        manager.disconnectAndForget()
                    }
                    .foregroundColor(.red)
                    .font(.caption)
                }
            }
            .padding(.horizontal, 16)
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }

    private static func formatSyncTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        if Calendar.current.isDateInToday(date) {
            formatter.dateFormat = "HH:mm:ss"
        } else {
            formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        }
        return formatter.string(from: date)
    }

    private func statusChip(for deviceId: String) -> some View {
        let syncInfo = manager.deviceSyncInfo[deviceId]
        let syncStatus = syncInfo?.status ?? .none
        let connectionState = manager.connectionStates[deviceId] ?? .disconnected
        
        // Show sync status if active, otherwise show connection state
        let (color, label): (Color, String) = syncStatus == .syncing ? (dashboardSyncStatusColor, "Syncing")
            : syncStatus == .cleanup ? (dashboardCleanupStatusColor, "Cleanup")
            : connectionDisplay(connectionState)
        
        return HStack(spacing: 4) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(label)
                .font(.caption)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(color.opacity(0.15))
        .cornerRadius(12)
    }

    private func connectionDisplay(_ state: ConnectionState) -> (Color, String) {
        switch state {
        case .disconnected: return (.gray, "Disconnected")
        case .connecting: return (.orange, "Connecting…")
        case .connected: return (.green, "Connected")
        case .ftuCheck: return (.blue, "FTU Check")
        case .syncing: return (dashboardSyncStatusColor, "Syncing")
        }
    }

    private func batteryIndicator(level: UInt) -> some View {
        let color: Color = level <= 20 ? .red : (level <= 50 ? .orange : .green)
        return HStack(spacing: 2) {
            Image(systemName: batteryIconName(level: level))
                .font(.caption2)
                .foregroundColor(color)
            Text("\(level)%")
                .font(.system(size: 10))
                .foregroundColor(color)
                .fontWeight(.medium)
        }
    }

    private func batteryIconName(level: UInt) -> String {
        switch level {
        case 0...25: return "battery.25"
        case 26...50: return "battery.50"
        case 51...75: return "battery.75"
        default: return "battery.100"
        }
    }

    private var dataTypeTogglesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Data Types").font(.headline)
            ForEach(SyncableDataType.allCases, id: \.self) { type in
                Toggle(type.displayName, isOn: Binding(
                    get: { manager.enabledDataTypes.contains(type) },
                    set: { enabled in
                        if enabled { manager.enabledDataTypes.insert(type) }
                        else { manager.enabledDataTypes.remove(type) }
                        manager.saveEnabledDataTypes()
                        manager.pushDataTypesToActiveSessions()
                    }
                ))
                .toggleStyle(.switch)
                .font(.caption)
            }
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }

    private var deletionSettingsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Device Cleanup").font(.headline)
            Toggle("Auto-delete from device after sync", isOn: $manager.autoDeleteEnabled)
            if manager.autoDeleteEnabled {
                HStack {
                    Text("Activity data:")
                    Stepper("\(manager.deviceRetentionDays) days",
                            value: $manager.deviceRetentionDays, in: 1...30)
                }
                .font(.caption)
                HStack {
                    Text("Offline recordings:")
                    Stepper("\(manager.offlineRecordingRetentionDays) days",
                            value: $manager.offlineRecordingRetentionDays, in: 0...14)
                }
                .font(.caption)
            }
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }

    private var syncTriggersSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Sync Triggers").font(.headline)

            // Sync on foreground toggle
            Toggle(isOn: $manager.syncOnForeground) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Sync when foregrounded")
                        .font(.body)
                    Text("Trigger a sync cycle when app returns to foreground")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Divider()

            // Timed sync interval
            Text("Timed sync interval")
                .font(.body)
            Text(manager.syncIntervalMinutes == 0
                 ? "Off — sync only on device notification"
                 : "Sync every \(manager.syncIntervalMinutes) min")
                .font(.caption)
                .foregroundColor(.secondary)
            Slider(value: Binding(
                get: { Double(manager.syncIntervalMinutes) },
                set: { manager.syncIntervalMinutes = Int($0) }
            ), in: 0...60, step: 1)
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }

    private var localRetentionSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Local Storage").font(.headline)
            HStack {
                VStack(alignment: .leading) {
                    Text("Local data auto-purge")
                        .font(.body)
                    Text("Delete local files older than \(manager.localRetentionDays) days")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Toggle("", isOn: $manager.localRetentionEnabled)
                    .labelsHidden()
            }
            if manager.localRetentionEnabled {
                Text("Local retention: \(manager.localRetentionDays) days")
                    .font(.caption)
                Slider(value: Binding(
                    get: { Double(manager.localRetentionDays) },
                    set: { manager.localRetentionDays = Int($0) }
                ), in: 7...90, step: 1)
            }
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }

    private var syncLogSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Sync Log").font(.headline)
                Spacer()
                if !manager.syncLog.isEmpty {
                    ShareLink(
                        item: SyncLogExport(),
                        subject: Text("Polar Background Sync Log"),
                        message: Text("Exported sync log"),
                        preview: SharePreview("Polar Background Sync Log")
                    ) {
                        Label("Share", systemImage: "square.and.arrow.up")
                            .font(.caption)
                    }
                    Button(action: {
                        let graphData = LogGraphData.parse(
                            deviceId: manager.deviceIds.first ?? "Unknown",
                            logLines: SyncLogStore.shared.getAllMessages()
                        )
                        currentGraphData = graphData
                        showLogGraph = true
                    }) {
                        Label("Graph", systemImage: "chart.line.uptrend.xyaxis")
                            .font(.caption)
                    }
                    .disabled(manager.deviceIds.isEmpty)
                    Button("Clear") {
                        manager.clearLog()
                    }
                    .font(.caption)
                }
            }
            if manager.syncLog.isEmpty {
                Text("No activity yet")
                    .font(.caption)
                    .foregroundColor(.secondary)
            } else {
                ScrollView(.vertical) {
                    LazyVStack(alignment: .leading, spacing: 4) {
                        ForEach(manager.syncLog) { entry in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(alignment: .top, spacing: 8) {
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(entry.dateText)
                                            .font(.system(size: 10, weight: .regular, design: .monospaced))
                                            .foregroundColor(.secondary)
                                        Text(entry.timeText)
                                            .font(.system(size: 10, weight: .regular, design: .monospaced))
                                            .foregroundColor(.secondary)
                                        Text(entry.deviceId)
                                            .font(.system(size: 10, weight: .regular, design: .monospaced))
                                            .foregroundColor(.secondary)
                                    }
                                    .frame(width: 92, alignment: .leading)
                                    Text(entry.message)
                                        .font(.caption)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                                Divider()
                                    .overlay(Color.secondary.opacity(0.35))
                            }
                        }
                    }
                }
                .frame(maxHeight: 320)
            }
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }

    private var aboutSection: some View {
        let info = Self.aboutInfo

        return VStack(alignment: .leading, spacing: 8) {
            Text("About").font(.headline)
            aboutRow(label: "App version", value: info.appVersion)
            aboutRow(label: "App commit", value: info.appCommit)
            aboutRow(label: "SDK version", value: info.sdkVersion)
            aboutRow(label: "SDK source", value: info.sdkSource)
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }

    private func aboutRow(label: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.body)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .font(.system(.body, design: .monospaced))
                .multilineTextAlignment(.trailing)
        }
    }

    // MARK: - Helpers

    private static var aboutInfo: (appVersion: String, appCommit: String, sdkVersion: String, sdkSource: String) {
        let bundle = Bundle.main
        let appVersion = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "Unknown"
        let appCommit = bundle.object(forInfoDictionaryKey: "BgSyncBuildRepoCommit") as? String ?? "Unknown"
        let sdkVersion = bundle.object(forInfoDictionaryKey: "BgSyncSdkVersion") as? String ?? "Unknown"
        let sdkSource = bundle.object(forInfoDictionaryKey: "BgSyncSdkSource") as? String ?? "Unknown"
        return (appVersion, appCommit, sdkVersion, sdkSource)
    }
}

private extension SyncableDataType {
    var displayName: String {
        switch self {
        case .steps: return "Steps"
        case .distance: return "Distance"
        case .activeTime: return "Active Time"
        case .calories: return "Calories"
        case .hrSamples247: return "HR 24/7"
        case .ppiSamples247: return "PPI 24/7"
        case .nightlyRecharge: return "Nightly Recharge"
        case .activitySamples: return "Activity Samples"
        case .dailySummary: return "Daily Summary"
        case .sleep: return "Sleep"
        case .trainingSessions: return "Training"
        case .skinTemperature: return "Skin Temp"
        case .offlineAcc: return "Offline ACC"
        case .offlineDerivedAcc: return "Offline Derived ACC"
        case .offlineEcg: return "Offline ECG"
        case .offlineGyro: return "Offline Gyro"
        case .offlineMagnetometer: return "Offline Mag"
        case .offlinePpg: return "Offline PPG"
        case .offlinePpi: return "Offline PPI"
        case .offlineHr: return "Offline HR"
        case .offlineTemperature: return "Offline Temperature"
        case .offlineSkinTemperature: return "Offline Skin Temp"
        }
    }
}

extension Color {
    static let polarRed = Color(red: 0.85, green: 0.16, blue: 0.20)
}
