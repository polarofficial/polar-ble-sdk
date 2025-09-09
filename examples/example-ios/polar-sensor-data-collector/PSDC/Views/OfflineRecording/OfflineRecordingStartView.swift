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
    @State private var triggerMode: String = "DISABLED"

    var body: some View {
        NavigationView {
            TabView(selection: $selectedTab) {
                TriggerStatusView()
                    .tabItem { Label("Status", systemImage: "info.circle") }
                    .tag(0)
                
                TriggerSetupView(selectedTriggerMode: triggerMode)
                    .tabItem { Label("Setup", systemImage: "slider.horizontal.3") }
                    .tag(1)
            }
            .navigationTitle("Offline Trigger")
        }
    }
}

struct TriggerStatusView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager

    @State private var isLoading = true
    @State private var errorMessage: String?
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
                Text(triggerMode).foregroundColor(.gray)

                Text("Enabled triggers and settings").font(.headline)
                ForEach(deviceOfflineDataTypes.indices) { index in
                    if deviceOfflineDataTypes[index].isSelected == true {
                        VStack(alignment: .leading) {
                            Text("\(deviceOfflineDataTypes[index].datatype)").font(.body)
                            
                            if let settings = triggerSettings[deviceOfflineDataTypes[index].datatype] {
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
        settingsOptions = []
        
        isLoading = true
        do {
            let triggerSetup = try await bleSdkManager.getOfflineRecordingTriggerSetup().value
            
            DispatchQueue.main.async {
                triggerMode = mapTriggerMode(triggerSetup.triggerMode)
                enabledTriggers.removeAll()
                enabledTriggers = mapTriggerFeatures(triggerSetup.triggerFeatures)
                
                fetchTriggerSettings(from: triggerSetup.triggerFeatures)
                
                if (triggerSetup.triggerFeatures.isEmpty) {
                    selectedSettings = []
                }
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

        triggerSettings.removeAll()
        for (dataType, sensorSetting) in triggerFeatures {
            guard let setting = sensorSetting else { continue }
            triggerSettings[dataType] = setting
        }
        deviceOfflineDataTypes = []
        for item in bleSdkManager.offlineRecordingFeature.isRecording {
            var isSelected: Bool = false
                if ( triggerSettings.contains(where: { $0.key == item.key }) ) {
                    isSelected = true
                } else if ( triggerFeatures.contains(where: { $0.key == item.key }) ) {
                    isSelected = true
                }
            let selection: DatatypeSelection = .init(datatype: item.key, isSelected: isSelected)
            deviceOfflineDataTypes.append(selection)
            deviceOfflineDataTypes.sort { $0.datatype.displayName < $1.datatype.displayName }
        }
    }

    private func mapTriggerMode(_ mode: PolarOfflineRecordingTriggerMode) -> String {
        switch mode {
        case .triggerDisabled: return "DISABLED"
        case .triggerSystemStart: return "SYSTEM START"
        case .triggerExerciseStart: return "EXERCISE START"
        }
    }

    private func mapTriggerFeatures(_ features: [PolarDeviceDataType : PolarSensorSetting?]) -> [String: Bool] {
        let mapping: [PolarDeviceDataType: String] = [
            .acc: "ACC",
            .ecg: "ECG",
            .gyro: "GYR",
            .hr: "HR",
            .magnetometer: "MAG",
            .ppg: "PPG",
            .ppi: "PPI",
            .pressure: "PRESSURE",
            .skinTemperature: "SKINTEMP",
            .temperature: "TEMPERATURE"
        ]

        var mappedFeatures = Dictionary(uniqueKeysWithValues: mapping.values.map { ($0, false) })

        for (dataType, _) in features {
            if let key = mapping[dataType] {
                mappedFeatures[key] = true
            }
        }
        return mappedFeatures
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
                
                ForEach(deviceOfflineDataTypes.indices) { index in
                    HStack {
                        Text(deviceOfflineDataTypes[index].datatype.displayName)
                        if ( settingsOptions!.contains(where: { $0.selectedDataType == deviceOfflineDataTypes[index].datatype })) {
                            Button(action: {
                                currentlySelectedDataType = mapStringToDeviceDataType(deviceOfflineDataTypes[index].datatype.displayName)
                                showSettingsDialog.toggle()
                            }) {
                                Text("Settings").foregroundColor(.red).font(.footnote)
                            }
                        }

                        Spacer()
                        Toggle("", isOn: Binding(
                            get: {
                                deviceOfflineDataTypes[index].isSelected
                            },
                            set: {
                                deviceOfflineDataTypes[index].isSelected = $0
                                enabledTriggers[getShortNameForDataType(deviceOfflineDataTypes[index].datatype)] = $0

                                if ($0 && settingsOptions!.contains(where: { $0.selectedDataType == deviceOfflineDataTypes[index].datatype }) ) {
                                    if let dataType = mapStringToDeviceDataType(deviceOfflineDataTypes[index].datatype.displayName) {
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
            .alert(item: $appState.bleSdkManager.generalMessage) { message in
                Alert(
                    title: Text(message.text)
                )
            }
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
        }.task {
            await fetchSettings()
        }
    }

    private func fetchSettings() async {

        self.isLoading = true
        for dataType in bleSdkManager.offlineRecordingFeature.isRecording {
            do {
                try await bleSdkManager.getOfflineRecordingTriggerSettings(feature: dataType.key).value
            } catch {
                BleLogger.error("Failed to retrieve settings for \(dataType), error: \(error.localizedDescription)")
            }

            DispatchQueue.main.async() {
                var range: UInt32 = 0
                var resolution: UInt32 = 0
                var sampleRate: UInt32 = 0
                var channels: UInt32 = 0
                var resolutionOptions: [UInt32] = []
                var sampleRateOptions: [UInt32] = []
                var channelOptions: [UInt32] = []
                var rangeOptions: [UInt32] = []
                guard let settings = self.bleSdkManager.offlineRecordingSettings(for: dataType.key) else {
                    BleLogger.error("Failed to retrieve settings for \(dataType)")
                    return
                }

                if let sampleRateSetting = settings.settings.first(where: { $0.type == .sampleRate }) {
                    sampleRateOptions = sampleRateSetting.sortedValues.compactMap { UInt32($0) }
                    if let firstSampleRate = sampleRateOptions.first {
                        sampleRate = firstSampleRate
                    } else {
                        BleLogger.trace("No valid sample rate values found")
                    }
                } else {
                    BleLogger.trace("No sample rate settings found")
                }

                if let resolutionSetting = settings.settings.first(where: { $0.type == .resolution }) {
                    resolutionOptions = resolutionSetting.sortedValues.compactMap { UInt32($0) }
                    if let firstResolution = resolutionOptions.first {
                        resolution = firstResolution
                    } else {
                        BleLogger.trace("No valid resolution values found")
                    }
                } else {
                    BleLogger.trace("No resolution settings found")
                }

                if let rangeSetting = settings.settings.first(where: { $0.type == .range }) {
                    rangeOptions = rangeSetting.sortedValues.compactMap { UInt32($0) }
                    if let firstRange = rangeOptions.first {
                        BleLogger.trace("Range option set to: \(firstRange)")
                        range = firstRange
                    } else {
                        BleLogger.trace("No valid range values found")
                    }
                } else {
                    BleLogger.trace("No range settings found")
                }

                if let channelSetting = settings.settings.first(where: { $0.type == .channels }) {
                    channelOptions = channelSetting.sortedValues.compactMap { UInt32($0) }
                    if let firstChannel = channelOptions.first {
                        BleLogger.trace("Channel option set to: \(firstChannel)")
                        channels = firstChannel
                    } else {
                        BleLogger.trace("No channel values found")
                    }
                } else {
                    BleLogger.trace("No channel settings found")
                }
                selectedSettings?.append(SelectedSettingsForType.init(selectedSampleRate: sampleRate, selectedResolution: resolution, selectedRange: range > 0 ? range : nil, selectedChannels: channels, selectedDataType: dataType.key))
                settingsOptions?.append(SettingsOptions(sampleRateOptions: sampleRateOptions,
                                                        resolutionOptions: resolutionOptions,
                                                        rangeOptions: rangeOptions,
                                                        channelOptions: channelOptions,
                                                        selectedDataType: dataType.key))
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
            NSLog("Invalid trigger mode: \(triggerMode)")
            return
        }

        for (key, isEnabled) in enabledTriggers {
            guard isEnabled else { continue }
                if let dataType = mapStringToDeviceDataType(key) {
                    if ( enabledTriggers.contains(where: { $0.key == dataType.displayName }) ) {
                        var settings: [PolarSensorSetting.SettingType: UInt32] = [:]
                        if ( selectedSettings!.contains(where: { $0.selectedDataType == dataType }) ) {
                            let currentSettings = selectedSettings![selectedSettings!.firstIndex { $0.selectedDataType == dataType }!]
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
                    } else {
                        NSLog("[TriggerSetupView] Unknown data type: \(key)")
                    }
                }
        }

        let trigger = PolarOfflineRecordingTrigger(triggerMode: mode, triggerFeatures: triggerFeatures)

        NSLog("[TriggerSetupView] Applying trigger mode: \(triggerMode), Features: \(triggerFeatures)")

        _ = bleSdkManager.setOfflineRecordingTrigger(trigger: trigger, secret: nil)
            .subscribe(
                onCompleted: {
                    NSLog("[TriggerSetupView] Successfully set offline recording trigger: Mode: \(triggerMode), Features: \(enabledTriggers)")
                },
                onError: { error in
                    NSLog("[TriggerSetupView] Error setting offline recording trigger: \(error)")
                }
            )
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
        NSLog("Offline recording toggle for feature \(feature)")
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
