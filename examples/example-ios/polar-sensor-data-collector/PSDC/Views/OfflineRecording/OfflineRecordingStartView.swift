/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct SelectedSettingsForType {
    @State var selectedSampleRate: UInt32?
    @State var selectedResolution: UInt32?
    @State var selectedRange: UInt32?
    @State var selectedChannels: UInt32?
    @State var selectedDataType: PolarDeviceDataType?
}

struct SettingsOptions {
    @State var sampleRateOptions: [UInt32]
    @State var resolutionOptions: [UInt32]
    @State var rangeOptions: [UInt32]
    @State var channelOptions: [UInt32]
    @State var selectedDataType: PolarDeviceDataType?
}

private var settingsOptions: [SettingsOptions]? = []
private var selectedSettings: [SelectedSettingsForType]? = []
private var triggerSettings: [PolarDeviceDataType: PolarSensorSetting] = [:]
private var deviceOfflineDataTypes: [DatatypeSelection] = []
private var currentlySelectedDataType: PolarDeviceDataType?
private var triggerMode: String = "DISABLED"
private var enabledTriggers: [String: Bool] = [:]
private var fetchedTriggerMode: String = "DISABLED"
private var fetchedEnabledDataTypes: Set<PolarDeviceDataType> = []
private var fetchedSettingsByDataType: [PolarDeviceDataType: SelectedSettingsForType] = [:]

struct DatatypeSelection : Identifiable{
    let id = UUID()
    let datatype: PolarDeviceDataType
    var isSelected: Bool
}

struct OfflineRecordingStartView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    @State private var showTriggerSettings = false
    
    var body: some View {
        if(bleSdkManager.offlineRecordingFeature.isSupported) {
            Group {
                Group {
                    VStack {
                        OfflineRecStartButton(dataType: .ecg)
                        OfflineRecStartButton(dataType: .acc)
                        OfflineRecStartButton(dataType: .gyro)
                        OfflineRecStartButton(dataType: .magnetometer)
                        OfflineRecStartButton(dataType: .ppg)
                        OfflineRecStartButton(dataType: .ppi)
                        OfflineRecStartButton(dataType: .hr)
                        OfflineRecStartButton(dataType: .temperature)
                        OfflineRecStartButton(dataType: .skinTemperature)
                        Spacer()
                        
                        Button("Trigger Settings") {
                            showTriggerSettings = true
                        }
                    }
                    .foregroundColor(.red)
                    .padding()
                    .frame(maxWidth: .infinity)
                    .cornerRadius(10)
                    .padding()
                }
                .fullScreenCover(item: $bleSdkManager.offlineRecordingSettings) { offlineRecSettings in
                    let settings = offlineRecSettings
                    SettingsView(streamedFeature: settings.feature, streamSettings: settings, isOfflineSettings: true)
                }
                .sheet(isPresented: $showTriggerSettings) {
                    TriggerSettingsView()
                }
                .task {
                    await bleSdkManager.getOfflineRecordingStatus()
                }
            }
        } else {
            Text("Offline recording is not supported")
        }
    }
}

struct TriggerSettingsView: View {
    @State private var selectedTab = 0
    @State private var showDiscardEditsConfirmation = false

    var body: some View {
        NavigationView {
            TabView(selection: Binding(
                get: { selectedTab },
                set: { handleTabSelectionChange($0) }
            )) {
                TriggerStatusView()
                    .tabItem { Label("Status", systemImage: "info.circle") }
                    .tag(0)
                
                TriggerSetupView(selectedTriggerMode: triggerMode)
                    .tabItem { Label("Setup", systemImage: "slider.horizontal.3") }
                    .tag(1)
            }
            .navigationTitle("Offline Trigger")
            .confirmationDialog("Discard unsaved trigger edits?", isPresented: $showDiscardEditsConfirmation, titleVisibility: .visible) {
                Button("Discard Edits", role: .destructive) {
                    resetSetupToFetchedState()
                    selectedTab = 0
                }
                Button("Keep Editing", role: .cancel) {
                    // stay on Setup tab
                }
            } message: {
                Text("You have unsaved trigger setup changes.")
            }
        }
    }

    private func handleTabSelectionChange(_ newValue: Int) {
        if selectedTab == 1 && newValue == 0 && hasUnsavedSetupChanges() {
            showDiscardEditsConfirmation = true
            return
        }
        selectedTab = newValue
    }

    private func hasUnsavedSetupChanges() -> Bool {
        if triggerMode != fetchedTriggerMode {
            return true
        }

        let currentEnabled = Set(deviceOfflineDataTypes.filter { $0.isSelected }.map { $0.datatype })
        if currentEnabled != fetchedEnabledDataTypes {
            return true
        }

        let currentSettingsByType: [PolarDeviceDataType: SelectedSettingsForType] =
            Dictionary(uniqueKeysWithValues: (selectedSettings ?? []).compactMap { item in
                guard let dataType = item.selectedDataType else { return nil }
                return (dataType, item)
            })

        for dataType in currentEnabled {
            let current = currentSettingsByType[dataType]
            let fetched = fetchedSettingsByDataType[dataType]
            if !isSameSettingSelection(current, fetched) {
                return true
            }
        }

        return false
    }

    private func isSameSettingSelection(_ lhs: SelectedSettingsForType?, _ rhs: SelectedSettingsForType?) -> Bool {
        guard let lhs = lhs, let rhs = rhs else {
            return lhs == nil && rhs == nil
        }
        return lhs.selectedSampleRate == rhs.selectedSampleRate
            && lhs.selectedResolution == rhs.selectedResolution
            && lhs.selectedRange == rhs.selectedRange
            && lhs.selectedChannels == rhs.selectedChannels
    }

    private func resetSetupToFetchedState() {
        triggerMode = fetchedTriggerMode

        var updatedTypes: [DatatypeSelection] = []
        for item in deviceOfflineDataTypes {
            updatedTypes.append(.init(datatype: item.datatype, isSelected: fetchedEnabledDataTypes.contains(item.datatype)))
        }
        deviceOfflineDataTypes = updatedTypes

        enabledTriggers.removeAll()
        for item in deviceOfflineDataTypes {
            enabledTriggers[getShortNameForDataType(item.datatype)] = item.isSelected
        }

        var updatedSettings: [SelectedSettingsForType] = []
        for item in deviceOfflineDataTypes {
            if let fetched = fetchedSettingsByDataType[item.datatype] {
                updatedSettings.append(fetched)
            } else {
                let existing = (selectedSettings ?? []).first { $0.selectedDataType == item.datatype }
                updatedSettings.append(
                    existing ?? SelectedSettingsForType(
                        selectedSampleRate: nil,
                        selectedResolution: nil,
                        selectedRange: nil,
                        selectedChannels: nil,
                        selectedDataType: item.datatype
                    )
                )
            }
        }
        selectedSettings = updatedSettings
    }
}

struct TriggerStatusView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager

    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var statusTriggerMode: String = "DISABLED"
    @State private var statusDeviceOfflineDataTypes: [DatatypeSelection] = []
    @State private var statusTriggerSettings: [PolarDeviceDataType: PolarSensorSetting] = [:]
    @State private var sampleRateOptions: [UInt32] = []
    @State private var resolutionOptions: [UInt32] = []
    @State private var rangeOptions: [UInt32] = []
    @State private var channelOptions: [UInt32] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if isLoading {
                ProgressView("Fetching trigger setup...")
            } else if let errorMessage = errorMessage {
                Text("Error: \(errorMessage)")
                    .foregroundColor(.red)
            } else {
                Text("Currently set triggers").font(.headline)
                Text("Trigger mode").font(.subheadline)
                Text(statusTriggerMode).foregroundColor(.gray)

                Text("Enabled triggers and settings").font(.headline)
                let currentSelections = Array(statusDeviceOfflineDataTypes)
                ForEach(currentSelections) { selection in
                    if selection.isSelected == true {
                        VStack(alignment: .leading) {
                            Text("\(selection.datatype)").font(.body)
                            
                            if let settings = statusTriggerSettings[selection.datatype] {
                                if let sampleRate = settings.settings[.sampleRate]?.first {
                                    Text("Sample Rate: \(sampleRate) Hz")
                                }
                                if let resolution = settings.settings[.resolution]?.first {
                                    Text("Resolution: \(resolution) bits")
                                }
                                if let range = settings.settings[.range]?.first {
                                    Text("Range: \(range)")
                                }
                                if let channels = settings.settings[.channels]?.first {
                                    Text("Channels: \(channels)")
                                }
                            } else {
                                Text("No settings available")
                            }
                        }
                    }
                }
            }
        }
        .padding()
        .task {
            await fetchTriggerSetup()
        } .offset(.init(width: 0, height: 0))
    }

    private func fetchTriggerSetup() async {
        isLoading = true
        errorMessage = nil
        
        isLoading = true
        do {
            let triggerSetup = try await bleSdkManager.getOfflineRecordingTriggerSetup()
            
            DispatchQueue.main.async {
                statusTriggerMode = mapTriggerMode(triggerSetup.triggerMode)
                
                fetchTriggerSettings(from: triggerSetup.triggerFeatures)
                self.isLoading = false
            }
        } catch {
            DispatchQueue.main.async {
                self.errorMessage = "Failed to fetch trigger setup: \(error.localizedDescription)"
                self.isLoading = false
            }
        }
    }

    private func fetchTriggerSettings(from triggerFeatures: [PolarDeviceDataType: PolarSensorSetting?]) {

        statusTriggerSettings.removeAll()
        for (dataType, sensorSetting) in triggerFeatures {
            guard let setting = sensorSetting else { continue }
            statusTriggerSettings[dataType] = setting
        }
        statusDeviceOfflineDataTypes = []
        for item in bleSdkManager.offlineRecordingFeature.isRecording {
            var isSelected: Bool = false
                if ( statusTriggerSettings.contains(where: { $0.key == item.key }) ) {
                    isSelected = true
                } else if ( triggerFeatures.contains(where: { $0.key == item.key }) ) {
                    isSelected = true
                }
            let selection: DatatypeSelection = .init(datatype: item.key, isSelected: isSelected)
            statusDeviceOfflineDataTypes.append(selection)
            statusDeviceOfflineDataTypes.sort { $0.datatype.displayName < $1.datatype.displayName }
        }
    }

    private func mapTriggerMode(_ mode: PolarOfflineRecordingTriggerMode) -> String {
        switch mode {
        case .triggerDisabled: return "DISABLED"
        case .triggerSystemStart: return "SYSTEM START"
        case .triggerExerciseStart: return "EXERCISE START"
        }
    }

}

extension PolarDeviceDataType {
    var displayName: String {
        let mapping: [PolarDeviceDataType: String] = [
            .acc: "ACC",
            .ppg: "PPG",
            .ppi: "PPI",
            .gyro: "GYR",
            .magnetometer: "MAG",
            .hr: "HR",
            .skinTemperature: "SKINTEMP",
            .temperature: "TEMP",
            .pressure: "PRESSURE",
            .ecg: "ECG"
            
        ]
        return mapping[self] ?? ""
    }
}

struct TriggerSetupView: View {
    @State private var isLoading = true
    @State private var isApplyingTriggerSetup = false
    @State private var refreshGeneration = 0
    @State private var errorMessage: String?
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    @State var allowedTriggers: [String] = []
    @State var selectedTriggerMode: String
    @State private var showSettingsDialog = false
    @State private var showError = false
    @EnvironmentObject private var appState: AppState

    var body: some View {
        VStack(alignment: .leading) {
            
            if isLoading {
                ProgressView("Fetching trigger setup...")
            } else if let errorMessage = errorMessage {
                Text("Error: \(errorMessage)")
                    .foregroundColor(.red)
            } else {
                Text("Setup the trigger:")
                    .bold()
                Picker("Trigger Mode", selection: $selectedTriggerMode) {
                    Text("DISABLED").tag("DISABLED")
                    Text("SYSTEM START").tag("SYSTEM START")
                    Text("EXERCISE START").tag("EXERCISE START")
                }
                .task {
                    selectedTriggerMode = triggerMode
                }
                .pickerStyle(MenuPickerStyle())
                .padding()
                .background(Color.red.opacity(0.2))
                .cornerRadius(8)
                
                let currentDataTypes = Array(deviceOfflineDataTypes)
                ForEach(currentDataTypes) { selection in
                    HStack {
                        Text(selection.datatype.displayName)
                        if ( settingsOptions!.contains(where: { $0.selectedDataType == selection.datatype })) {
                            Button(action: {
                                currentlySelectedDataType = mapStringToDeviceDataType(selection.datatype.displayName)
                                showSettingsDialog.toggle()
                            }) {
                                Text("Settings").foregroundColor(.red).font(.footnote)
                            }
                        }

                        Spacer()
                        Toggle("", isOn: Binding(
                            get: {
                                deviceOfflineDataTypes.first(where: { $0.id == selection.id })?.isSelected ?? false
                            },
                            set: {
                                if let currentIndex = deviceOfflineDataTypes.firstIndex(where: { $0.id == selection.id }) {
                                    deviceOfflineDataTypes[currentIndex].isSelected = $0
                                }
                                enabledTriggers[getShortNameForDataType(selection.datatype)] = $0

                                if ($0 && settingsOptions!.contains(where: { $0.selectedDataType == selection.datatype }) ) {
                                    if let dataType = mapStringToDeviceDataType(selection.datatype.displayName) {
                                        currentlySelectedDataType = dataType
                                        showSettingsDialog.toggle()
                                    }
                                }
                            }
                        ))
                    }
                }
            }

            Spacer()
            Button(action: {
                triggerMode = selectedTriggerMode
                setupTriggers()
            }) {
                Text("SETUP TRIGGERS")
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.red)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
            .disabled(isApplyingTriggerSetup || isLoading)
            .padding()
        }
        .padding()
        .sheet(isPresented: $showSettingsDialog) {
            if let dataType = currentlySelectedDataType {
                let settings = selectedSettings![selectedSettings!.firstIndex { $0.selectedDataType == dataType }!]
                let range = settings.selectedRange
                let rate = settings.selectedSampleRate
                let resolution = settings.selectedResolution
                let channels = settings.selectedChannels
                SettingsDialog(
                    dataType: dataType,
                    bleSdkManager: bleSdkManager,
                    selectedSampleRate: rate ?? 0 > 0 ? rate : nil,
                    selectedResolution: resolution ?? 0 > 0 ? resolution : nil,
                    selectedRange: range ?? 0 > 0 ? range : nil,
                    selectedChannels: channels ?? 0 > 0 ? channels : nil
                )
            }
        }
        .onAppear {
            refreshGeneration += 1
            let generation = refreshGeneration
            Task {
                await fetchSettings(generation: generation)
            }
        }
    }

    private func fetchSettings(generation: Int) async {

        self.isLoading = true
        var nextSelectedSettings: [SelectedSettingsForType] = []
        var nextSettingsOptions: [SettingsOptions] = []
        var nextEnabledTriggers: [String: Bool] = [:]
        var nextDeviceOfflineDataTypes: [DatatypeSelection] = []

        var triggerSetupFeatures: [PolarDeviceDataType: PolarSensorSetting?] = [:]
        var currentTriggerMode: PolarOfflineRecordingTriggerMode?
        var triggerSetupFetchSucceeded = false
        do {
            let setup = try await bleSdkManager.getOfflineRecordingTriggerSetup()
            triggerSetupFeatures = setup.triggerFeatures
            currentTriggerMode = setup.triggerMode
            triggerSetupFetchSucceeded = true
        } catch {
            BleLogger.error("Failed to retrieve current trigger setup: \(error.localizedDescription)")
        }

        guard generation == refreshGeneration else {
            return
        }

        if let mode = currentTriggerMode {
            await MainActor.run {
                let modeText = mapTriggerMode(mode)
                triggerMode = modeText
                selectedTriggerMode = modeText
            }
        }

        for dataType in bleSdkManager.offlineRecordingFeature.isRecording {
            do {
                try await bleSdkManager.getOfflineRecordingTriggerSettings(feature: dataType.key)
            } catch {
                BleLogger.error("Failed to retrieve settings for \(dataType), error: \(error.localizedDescription)")
            }

            var range: UInt32 = 0
            var resolution: UInt32 = 0
            var sampleRate: UInt32 = 0
            var channels: UInt32 = 0
            var resolutionOptions: [UInt32] = []
            var sampleRateOptions: [UInt32] = []
            var channelOptions: [UInt32] = []
            var rangeOptions: [UInt32] = []
            guard let settings = bleSdkManager.offlineRecordingSettings(for: dataType.key) else {
                BleLogger.error("Failed to retrieve settings for \(dataType)")
                continue
            }

            if let sampleRateSetting = settings.settings.first(where: { $0.type == .sampleRate }) {
                sampleRateOptions = sampleRateSetting.sortedValues.compactMap { UInt32($0) }
                if let firstSampleRate = sampleRateOptions.first {
                    sampleRate = firstSampleRate
                }
            }

            if let resolutionSetting = settings.settings.first(where: { $0.type == .resolution }) {
                resolutionOptions = resolutionSetting.sortedValues.compactMap { UInt32($0) }
                if let firstResolution = resolutionOptions.first {
                    resolution = firstResolution
                }
            }

            if let rangeSetting = settings.settings.first(where: { $0.type == .range }) {
                rangeOptions = rangeSetting.sortedValues.compactMap { UInt32($0) }
                if let firstRange = rangeOptions.first {
                    range = firstRange
                }
            }

            if let channelSetting = settings.settings.first(where: { $0.type == .channels }) {
                channelOptions = channelSetting.sortedValues.compactMap { UInt32($0) }
                if let firstChannel = channelOptions.first {
                    channels = firstChannel
                }
            }

            if let appliedSetting = triggerSetupFeatures[dataType.key] ?? nil {
                if let v = appliedSetting.settings[.sampleRate]?.first { sampleRate = v }
                if let v = appliedSetting.settings[.resolution]?.first { resolution = v }
                if let v = appliedSetting.settings[.range]?.first { range = v }
                if let v = appliedSetting.settings[.channels]?.first { channels = v }
            }

            let isEnabled = triggerSetupFetchSucceeded
                ? triggerSetupFeatures.keys.contains(dataType.key)
                : (deviceOfflineDataTypes.first(where: { $0.datatype == dataType.key })?.isSelected ?? false)
            let selected = SelectedSettingsForType(
                selectedSampleRate: sampleRate > 0 ? sampleRate : nil,
                selectedResolution: resolution > 0 ? resolution : nil,
                selectedRange: range > 0 ? range : nil,
                selectedChannels: channels > 0 ? channels : nil,
                selectedDataType: dataType.key
            )
            let options = SettingsOptions(
                sampleRateOptions: sampleRateOptions,
                resolutionOptions: resolutionOptions,
                rangeOptions: rangeOptions,
                channelOptions: channelOptions,
                selectedDataType: dataType.key
            )

            nextSelectedSettings.append(selected)
            nextSettingsOptions.append(options)
            nextDeviceOfflineDataTypes.append(.init(datatype: dataType.key, isSelected: isEnabled))
            nextEnabledTriggers[getShortNameForDataType(dataType.key)] = isEnabled
        }

        guard generation == refreshGeneration else {
            return
        }

        await MainActor.run {
            nextDeviceOfflineDataTypes.sort { $0.datatype.displayName < $1.datatype.displayName }
            selectedSettings = nextSelectedSettings
            settingsOptions = nextSettingsOptions
            enabledTriggers = nextEnabledTriggers
            deviceOfflineDataTypes = nextDeviceOfflineDataTypes

            if triggerSetupFetchSucceeded {
                fetchedTriggerMode = triggerMode
                fetchedEnabledDataTypes = Set(nextDeviceOfflineDataTypes.filter { $0.isSelected }.map { $0.datatype })
                fetchedSettingsByDataType = Dictionary(uniqueKeysWithValues: nextSelectedSettings.compactMap { item in
                    guard let dataType = item.selectedDataType else { return nil }
                    return (dataType, item)
                })
            }
        }
        self.isLoading = false
    }

    private func setupTriggers() {
        let mode: PolarOfflineRecordingTriggerMode
        var triggerFeatures: [PolarDeviceDataType: PolarSensorSetting?] = [:]
        
        switch triggerMode {
        case "DISABLED":
            mode = .triggerDisabled
        case "SYSTEM START":
            mode = .triggerSystemStart
        case "EXERCISE START":
            mode = .triggerExerciseStart
        default:
            AppLogger.log("Invalid trigger mode: \(triggerMode)")
            return
        }

        // Align with Android behavior: disabling triggers must send an empty
        // triggerFeatures map to ensure all existing trigger settings are cleared.
        if mode == .triggerDisabled {
            enabledTriggers.removeAll()
            triggerFeatures.removeAll()
            let trigger = PolarOfflineRecordingTrigger(triggerMode: mode, triggerFeatures: triggerFeatures)

            AppLogger.log("[TriggerSetupView] Applying trigger mode: \(triggerMode), Features: \(triggerFeatures)")

            Task {
                await MainActor.run { isApplyingTriggerSetup = true }
                defer { Task { @MainActor in isApplyingTriggerSetup = false } }
                do {
                    try await bleSdkManager.setOfflineRecordingTrigger(trigger: trigger, secret: nil)
                    await syncTriggerSetupFromDevice()
                    refreshGeneration += 1
                    let generation = refreshGeneration
                    await fetchSettings(generation: generation)
                    await MainActor.run {
                        appState.bleSdkManager.generalMessage = Message(text: "Offline triggers disabled")
                    }
                    AppLogger.log("[TriggerSetupView] Successfully disabled offline recording triggers")
                } catch {
                    await MainActor.run {
                        appState.bleSdkManager.generalMessage = Message(text: "Error: Failed to disable offline triggers: \(error.localizedDescription)")
                    }
                    AppLogger.log("[TriggerSetupView] Error disabling offline recording triggers: \(error)")
                }
            }
            return
        }

        let enabledDataTypes = deviceOfflineDataTypes
            .filter { $0.isSelected }
            .map { $0.datatype }

        for dataType in enabledDataTypes {
            var settings: [PolarSensorSetting.SettingType: UInt32] = [:]
            if let currentSettings = selectedSettings?.first(where: { $0.selectedDataType == dataType }) {
                settings[.range] = currentSettings.selectedRange
                settings[.channels] = currentSettings.selectedChannels
                settings[.sampleRate] = currentSettings.selectedSampleRate
                settings[.resolution] = currentSettings.selectedResolution
            }

            do {
                triggerFeatures[dataType] = try PolarSensorSetting(settings)
            } catch let err {
                BleLogger.trace("Settings validation failed for datatype \(dataType), error: \(err)")
            }
        }

        let trigger = PolarOfflineRecordingTrigger(triggerMode: mode, triggerFeatures: triggerFeatures)

        AppLogger.log("[TriggerSetupView] Applying trigger mode: \(triggerMode), Features: \(triggerFeatures)")

        Task {
            await MainActor.run { isApplyingTriggerSetup = true }
            defer { Task { @MainActor in isApplyingTriggerSetup = false } }
            do {
                try await bleSdkManager.setOfflineRecordingTrigger(trigger: trigger, secret: nil)
                await syncTriggerSetupFromDevice()
                refreshGeneration += 1
                let generation = refreshGeneration
                await fetchSettings(generation: generation)
                await MainActor.run {
                    appState.bleSdkManager.generalMessage = Message(text: "Offline trigger setup updated")
                }
                AppLogger.log("[TriggerSetupView] Successfully set offline recording trigger: Mode: \(triggerMode), Features: \(triggerFeatures)")
            } catch {
                await MainActor.run {
                    appState.bleSdkManager.generalMessage = Message(text: "Error: Failed to set offline triggers: \(error.localizedDescription)")
                }
                AppLogger.log("[TriggerSetupView] Error setting offline recording trigger: \(error)")
            }
        }
    }

    private func syncTriggerSetupFromDevice() async {
        do {
            let triggerSetup = try await bleSdkManager.getOfflineRecordingTriggerSetup()
            await MainActor.run {
                let modeText = mapTriggerMode(triggerSetup.triggerMode)
                triggerMode = modeText
                selectedTriggerMode = modeText

                let enabledTypes = Set(triggerSetup.triggerFeatures.keys)
                for index in deviceOfflineDataTypes.indices {
                    deviceOfflineDataTypes[index].isSelected = enabledTypes.contains(deviceOfflineDataTypes[index].datatype)
                }

                enabledTriggers.removeAll()
                for item in deviceOfflineDataTypes {
                    enabledTriggers[getShortNameForDataType(item.datatype)] = item.isSelected
                }

                if selectedSettings == nil { selectedSettings = [] }
                for (dataType, maybeSetting) in triggerSetup.triggerFeatures {
                    guard let setting = maybeSetting else { continue }
                    let updated = SelectedSettingsForType(
                        selectedSampleRate: setting.settings[.sampleRate]?.first,
                        selectedResolution: setting.settings[.resolution]?.first,
                        selectedRange: setting.settings[.range]?.first,
                        selectedChannels: setting.settings[.channels]?.first,
                        selectedDataType: dataType
                    )
                    if let idx = selectedSettings?.firstIndex(where: { $0.selectedDataType == dataType }) {
                        selectedSettings?[idx] = updated
                    } else {
                        selectedSettings?.append(updated)
                    }
                }
            }
        } catch {
            BleLogger.error("Failed to sync trigger setup from device: \(error.localizedDescription)")
        }
    }

    private func mapTriggerMode(_ mode: PolarOfflineRecordingTriggerMode) -> String {
        switch mode {
        case .triggerDisabled: return "DISABLED"
        case .triggerSystemStart: return "SYSTEM START"
        case .triggerExerciseStart: return "EXERCISE START"
        }
    }
}

struct SettingsDialog: View {
    let dataType: PolarDeviceDataType
    @ObservedObject var bleSdkManager: PolarBleSdkManager
    @Environment(\.presentationMode) var presentationMode
    @State var selectedSampleRate: UInt32?
    @State var selectedResolution: UInt32?
    @State var selectedRange: UInt32?
    @State var selectedChannels: UInt32?

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("\(dataType.displayName) Settings")) {
                    
                    if !settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].sampleRateOptions.isEmpty {
                        Picker("Sample Rate", selection: $selectedSampleRate) {
                            ForEach(settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].sampleRateOptions, id: \.self) { rate in
                                Text("\(rate) Hz").tag(rate)
                            }
                        }
                        .pickerStyle(MenuPickerStyle())
                    }

                    if !settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].resolutionOptions.isEmpty {
                        Picker("Resolution", selection: $selectedResolution) {
                            ForEach(settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].resolutionOptions, id: \.self) { resolution in
                                Text("\(resolution) bits").tag(resolution)
                            }
                        }
                        .pickerStyle(MenuPickerStyle())
                    }

                    if !settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].rangeOptions.isEmpty {
                        Picker("Range", selection: $selectedRange) {
                            ForEach(settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].rangeOptions, id: \.self) { range in
                                Text("\(range)").tag(range)
                            }
                        }
                        .pickerStyle(MenuPickerStyle())
                    }

                    if !settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].channelOptions.isEmpty {
                        Picker("Channels", selection: $selectedChannels) {
                            ForEach(settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].channelOptions, id: \.self) { channels in
                                Text("\(channels)").tag(channels)
                            }
                        }
                        .pickerStyle(MenuPickerStyle())
                    }
                }

                Button("OK") {
                    Task {
                        if ( settingsOptions!.contains(where: { $0.selectedDataType == dataType }) ) {
                            selectedSettings!.remove(at: selectedSettings!.firstIndex { $0.selectedDataType == dataType }!)
                            selectedSettings!.append(SelectedSettingsForType(selectedSampleRate: selectedSampleRate, selectedResolution: selectedResolution, selectedRange: selectedRange, selectedChannels: selectedChannels, selectedDataType: dataType))
                        } else {
                            selectedSettings!.append(SelectedSettingsForType(selectedSampleRate: selectedSampleRate, selectedResolution: selectedResolution, selectedRange: selectedRange, selectedChannels: selectedChannels, selectedDataType: dataType))
                        }
                        await exit()
                    }
                }.buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                
            }.navigationBarTitle("\(dataType.displayName) Settings", displayMode: .inline)
        }
    }

    func exit() async {
        await MainActor.run {
            presentationMode.wrappedValue.dismiss()
        }
    }
}

struct OfflineRecStartButton: View {
    let dataType: PolarDeviceDataType
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager

    var body: some View {
        Button(getRecButtonText(dataType, bleSdkManager.offlineRecordingFeature.isRecording[dataType]),
               action: { offlineRecButtonToggle(dataType, bleSdkManager.offlineRecordingFeature.isRecording[dataType])
        })
        .buttonStyle(SecondaryButtonStyle(buttonState: getStreamButtonState(dataType, bleSdkManager.offlineRecordingFeature.isRecording[dataType])))
    }

    private func getRecButtonText(_ feature:PolarDeviceDataType, _ isRecording:Bool?) -> String {
        let text = getShortNameForDataType(feature)
        let buttonText: String
        if let enabled = isRecording {
            buttonText = enabled ? "Stop \(text) Recording" : "Start \(text) Recording"
        } else {
            buttonText = "Start \(text) Recording"
        }
        return buttonText
    }

    private func offlineRecButtonToggle(_ feature:PolarDeviceDataType, _ isRecording:Bool?) {
        AppLogger.log("Offline recording toggle for feature \(feature)")
        guard let toggleStartStop = isRecording else {
            return
        }

        if(toggleStartStop) {
            bleSdkManager.offlineRecordingStop(feature: feature)
        } else {
            if(feature == PolarDeviceDataType.ppi || feature == PolarDeviceDataType.hr) {
                bleSdkManager.offlineRecordingStart(feature: feature)
            } else {
                bleSdkManager.getOfflineRecordingSettings(feature: feature)
            }
        }
    }

    private func getStreamButtonState(_ feature: PolarDeviceDataType, _ isRecording: Bool?) -> ButtonState {
        guard let statePressedReleased = isRecording else {
            return ButtonState.disabled
        }

        if statePressedReleased {
            return ButtonState.pressedDown
        } else {
            return ButtonState.released
        }
    }
}

struct OfflineRecordingStartView_Previews: PreviewProvider {
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        let offlineRecordingFeature = OfflineRecordingFeature(
            isSupported: true,
            availableOfflineDataTypes: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: false, PolarDeviceDataType.temperature: true, PolarDeviceDataType.skinTemperature: true],
            isRecording: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: true, PolarDeviceDataType.temperature: true, PolarDeviceDataType.skinTemperature: true]
        )
        polarBleSdkManager.offlineRecordingFeature = offlineRecordingFeature
        return polarBleSdkManager
    }()

    static var previews: some View {
        ForEach(["iPhone 8", "iPAD Pro (12.9-inch)"], id: \.self) { deviceName in
            OfflineRecordingStartView()
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
                .environmentObject(polarBleSdkManager)
        }
    }
}
