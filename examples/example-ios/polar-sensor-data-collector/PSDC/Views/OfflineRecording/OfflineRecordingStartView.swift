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
                        
                        Button("offline.triggerSettings") {
                            showTriggerSettings = true
                        }
                        .accessibilityIdentifier("psdc.offlineRecording.triggerSettingsButton")
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
            Text("offline.notSupported")
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
                    .tabItem { Label("offline.tab.status", systemImage: "info.circle") }
                    .accessibilityIdentifier("psdc.offlineTrigger.statusTab")
                    .tag(0)
                
                TriggerSetupView(selectedTriggerMode: triggerMode)
                    .tabItem { Label("offline.tab.setup", systemImage: "slider.horizontal.3") }
                    .accessibilityIdentifier("psdc.offlineTrigger.setupTab")
                    .tag(1)
            }
            .navigationTitle("offline.title")
            .confirmationDialog("Discard unsaved trigger edits?", isPresented: $showDiscardEditsConfirmation, titleVisibility: .visible) {
                Button("offline.discardEdits", role: .destructive) {
                    resetSetupToFetchedState()
                    selectedTab = 0
                }
                .accessibilityIdentifier("psdc.offlineTrigger.discardEditsButton")
                Button("offline.keepEditing", role: .cancel) {
                    // stay on Setup tab
                }
                .accessibilityIdentifier("psdc.offlineTrigger.keepEditingButton")
            } message: {
                Text("offline.unsavedChanges")
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
        OfflineTriggerLogic.hasUnsavedChanges(
            triggerMode: triggerMode,
            fetchedTriggerMode: fetchedTriggerMode,
            selections: deviceOfflineDataTypes,
            fetchedEnabled: fetchedEnabledDataTypes,
            selectedSettings: selectedSettings,
            fetchedSettings: fetchedSettingsByDataType)
    }

    private func resetSetupToFetchedState() {
        triggerMode = fetchedTriggerMode
        let reset = OfflineTriggerLogic.resetToFetched(
            selections: deviceOfflineDataTypes,
            fetchedEnabled: fetchedEnabledDataTypes,
            fetchedSettings: fetchedSettingsByDataType,
            current: selectedSettings)
        deviceOfflineDataTypes = reset.selections
        enabledTriggers = reset.enabledTriggers
        selectedSettings = reset.settings
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
                Text(String(format: NSLocalizedString("offline.errorFormat", comment: ""), errorMessage))
                    .foregroundColor(.red)
            } else {
                Text("offline.currentTriggers").font(.headline)
                Text("offline.triggerMode").font(.subheadline)
                Text(statusTriggerMode).foregroundColor(.gray)

                Text("offline.enabledTriggers").font(.headline)
                let currentSelections = Array(statusDeviceOfflineDataTypes)
                ForEach(currentSelections) { selection in
                    if selection.isSelected == true {
                        VStack(alignment: .leading) {
                            Text("\(selection.datatype)").font(.body)
                            
                            if let settings = statusTriggerSettings[selection.datatype] {
                                if let sampleRate = settings.settings[.sampleRate]?.first {
                                    Text(String(format: NSLocalizedString("offline.status.sampleRate", comment: ""), "\(sampleRate)"))
                                }
                                if let resolution = settings.settings[.resolution]?.first {
                                    Text(String(format: NSLocalizedString("offline.status.resolution", comment: ""), "\(resolution)"))
                                }
                                if let range = settings.settings[.range]?.first {
                                    Text(String(format: NSLocalizedString("offline.status.range", comment: ""), "\(range)"))
                                }
                                if let channels = settings.settings[.channels]?.first {
                                    Text(String(format: NSLocalizedString("offline.status.channels", comment: ""), "\(channels)"))
                                }
                            } else {
                                Text("offline.noSettings")
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
        let status = OfflineTriggerLogic.statusSelections(
            recordable: bleSdkManager.offlineRecordingFeature.isRecording, triggerFeatures: triggerFeatures)
        statusTriggerSettings = status.settings
        statusDeviceOfflineDataTypes = status.selections
    }

    private func mapTriggerMode(_ mode: PolarOfflineRecordingTriggerMode) -> String {
        OfflineTriggerLogic.modeText(mode)
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
                Text(String(format: NSLocalizedString("offline.errorFormat", comment: ""), errorMessage))
                    .foregroundColor(.red)
            } else {
                Text("offline.setupTrigger")
                    .bold()
                Picker("offline.triggerModePicker", selection: $selectedTriggerMode) {
                    Text("offline.mode.disabled").tag("DISABLED")
                    Text("offline.mode.systemStart").tag("SYSTEM START")
                    Text("offline.mode.exerciseStart").tag("EXERCISE START")
                }
                .accessibilityIdentifier("psdc.offlineTrigger.triggerModePicker")
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
                        if ( hasSettingsOptions(for: selection.datatype)) {
                            Button(action: {
                                currentlySelectedDataType = mapStringToDeviceDataType(selection.datatype.displayName)
                                showSettingsDialog.toggle()
                            }) {
                                Text("offline.settings").foregroundColor(.red).font(.footnote)
                            }
                            .accessibilityIdentifier("psdc.offlineTrigger.settingsButton.\(getShortNameForDataType(selection.datatype))")
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

                                if ($0 && hasSettingsOptions(for: selection.datatype) ) {
                                    if let dataType = mapStringToDeviceDataType(selection.datatype.displayName) {
                                        currentlySelectedDataType = dataType
                                        showSettingsDialog.toggle()
                                    }
                                }
                            }
                        ))
                        .accessibilityIdentifier("psdc.offlineTrigger.enableToggle.\(getShortNameForDataType(selection.datatype))")
                    }
                }
            }

            Spacer()
            Button(action: {
                triggerMode = selectedTriggerMode
                setupTriggers()
            }) {
                Text("offline.setupTriggers")
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.red)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
            .accessibilityIdentifier("psdc.offlineTrigger.setupTriggersButton")
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

    private func hasSettingsOptions(for dataType: PolarDeviceDataType) -> Bool {
        OfflineTriggerLogic.hasOptions(for: dataType, in: settingsOptions)
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

            let available = bleSdkManager.offlineRecordingSettings(for: dataType.key)
            if available == nil {
                BleLogger.error("No settings available for \(dataType)")
            }
            let applied = triggerSetupFeatures[dataType.key] ?? nil
            let (selected, options) = OfflineTriggerLogic.settings(for: dataType.key, available: available, applied: applied)

            let isEnabled = triggerSetupFetchSucceeded
                ? triggerSetupFeatures.keys.contains(dataType.key)
                : (deviceOfflineDataTypes.first(where: { $0.datatype == dataType.key })?.isSelected ?? false)

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
        guard let trigger = OfflineTriggerLogic.makeTrigger(
            modeText: triggerMode, selections: deviceOfflineDataTypes, selectedSettings: selectedSettings) else {
            AppLogger.log("Invalid trigger mode: \(triggerMode)")
            return
        }
        let isDisabling = trigger.triggerMode == .triggerDisabled
        if isDisabling { enabledTriggers.removeAll() }
        let triggerFeatures = trigger.triggerFeatures

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
                    appState.bleSdkManager.generalMessage = Message(text: isDisabling ? "Offline triggers disabled" : "Offline trigger setup updated")
                }
                AppLogger.log("[TriggerSetupView] Successfully applied offline recording trigger: Mode: \(triggerMode), Features: \(triggerFeatures)")
            } catch {
                await MainActor.run {
                    appState.bleSdkManager.generalMessage = Message(text: isDisabling
                        ? "Error: Failed to disable offline triggers: \(error.localizedDescription)"
                        : "Error: Failed to set offline triggers: \(error.localizedDescription)")
                }
                AppLogger.log("[TriggerSetupView] Error applying offline recording trigger: \(error)")
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

                let synced = OfflineTriggerLogic.synced(
                    features: triggerSetup.triggerFeatures, selections: deviceOfflineDataTypes, selectedSettings: selectedSettings)
                deviceOfflineDataTypes = synced.selections
                enabledTriggers = synced.enabledTriggers
                selectedSettings = synced.settings
            }
        } catch {
            BleLogger.error("Failed to sync trigger setup from device: \(error.localizedDescription)")
        }
    }

    private func mapTriggerMode(_ mode: PolarOfflineRecordingTriggerMode) -> String {
        OfflineTriggerLogic.modeText(mode)
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
                Section(header: Text(String(format: NSLocalizedString("offline.sectionSettings", comment: ""), dataType.displayName))) {
                    
                    if !settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].sampleRateOptions.isEmpty {
                        Picker("offline.sampleRate", selection: $selectedSampleRate) {
                            ForEach(settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].sampleRateOptions, id: \.self) { rate in
                                Text("\(rate) Hz").tag(rate)
                            }
                        }
                            .accessibilityIdentifier("psdc.offlineTrigger.settingsDialog.sampleRatePicker")
                        .pickerStyle(MenuPickerStyle())
                    }

                    if !settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].resolutionOptions.isEmpty {
                        Picker("offline.resolution", selection: $selectedResolution) {
                            ForEach(settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].resolutionOptions, id: \.self) { resolution in
                                Text("\(resolution) bits").tag(resolution)
                            }
                        }
                            .accessibilityIdentifier("psdc.offlineTrigger.settingsDialog.resolutionPicker")
                        .pickerStyle(MenuPickerStyle())
                    }

                    if !settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].rangeOptions.isEmpty {
                        Picker("offline.range", selection: $selectedRange) {
                            ForEach(settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].rangeOptions, id: \.self) { range in
                                Text("\(range)").tag(range)
                            }
                        }
                            .accessibilityIdentifier("psdc.offlineTrigger.settingsDialog.rangePicker")
                        .pickerStyle(MenuPickerStyle())
                    }

                    if !settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].channelOptions.isEmpty {
                        Picker("offline.channels", selection: $selectedChannels) {
                            ForEach(settingsOptions![settingsOptions!.firstIndex { $0.selectedDataType == dataType }!].channelOptions, id: \.self) { channels in
                                Text("\(channels)").tag(channels)
                            }
                        }
                            .accessibilityIdentifier("psdc.offlineTrigger.settingsDialog.channelsPicker")
                        .pickerStyle(MenuPickerStyle())
                    }
                }

                Button("common.ok") {
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
                .accessibilityIdentifier("psdc.offlineTrigger.settingsDialog.okButton")
                
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
        .accessibilityIdentifier("psdc.offlineRecording.recordButton.\(getShortNameForDataType(dataType))")
    }

    private func getRecButtonText(_ feature:PolarDeviceDataType, _ isRecording:Bool?) -> String {
        OfflineTriggerLogic.recordButtonText(feature, isRecording: isRecording)
    }

    private func offlineRecButtonToggle(_ feature:PolarDeviceDataType, _ isRecording:Bool?) {
        AppLogger.log("Offline recording toggle for feature \(feature)")
        guard let toggleStartStop = isRecording else {
            return
        }

        if(toggleStartStop) {
            bleSdkManager.offlineRecordingStop(feature: feature)
        } else {
            if OfflineTriggerLogic.startsWithoutSettings(feature) {
                bleSdkManager.offlineRecordingStart(feature: feature)
            } else {
                bleSdkManager.getOfflineRecordingSettings(feature: feature)
            }
        }
    }

    private func getStreamButtonState(_ feature: PolarDeviceDataType, _ isRecording: Bool?) -> ButtonState {
        OfflineTriggerLogic.buttonState(isRecording: isRecording)
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
