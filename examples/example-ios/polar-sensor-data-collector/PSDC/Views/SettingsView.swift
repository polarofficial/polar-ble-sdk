/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import SwiftUI
import PolarBleSdk

struct SettingsView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    let streamedFeature: PolarDeviceDataType
    let streamSettings: RecordingSettings
    var isOfflineSettings: Bool = false
    /// When true the dialog only saves the chosen settings; the stream is NOT started.
    var saveOnly: Bool = false

    @Environment(\.presentationMode) var presentationMode
    
    @State private var selectedSampleRate:Int = 0
    @State private var selectedRange:Int = 0
    @State private var selectedResolution:Int = 0
    @State private var selectedChannels:Int = 0

    @State private var derivedEnabled: Bool = false
    @State private var selectedDerivedSourceRateIdx: Int = 0
    @State private var selectedDerivedTimeWindowIdx: Int = 0
    @State private var selectedDerivedMethods: Set<PolarDerivedMeasurementMethod> = [.downsample]

    private var derivedSourceRates: [Int] {
        guard let g = bleSdkManager.accDerivedSettingsGroup else { return [] }
        let rates = g.sourceSampleRates
        return (rates.contains(50) ? [50] : rates.sorted())
    }
    private var derivedTimeWindows: [Int] {
        guard let g = bleSdkManager.accDerivedSettingsGroup else { return [] }
        return g.timeWindowOptions.sorted()
    }
    private var showDerivedSection: Bool {
        isOfflineSettings && streamedFeature == .acc && bleSdkManager.accDerivedSettingsGroup != nil
    }

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                Form {
                    ForEach(streamSettings.sortedSettings) { settings in
                        if(settings.type == PolarSensorSetting.SettingType.sampleRate) {
                            HStack {
                                Picker("Sample rate", selection: $selectedSampleRate) {
                                    ForEach(settings.sortedValues.indices, id: \.self) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                            }
                        } else if(settings.type == PolarSensorSetting.SettingType.range) {
                            Picker("Range", selection: $selectedRange) {
                                ForEach(settings.sortedValues.indices, id: \.self) {
                                    Text("\(settings.sortedValues[$0])")
                                }
                            }
                        } else if(settings.type == PolarSensorSetting.SettingType.resolution) {
                            HStack {
                                Picker("Resolution", selection: $selectedResolution) {
                                    ForEach(settings.sortedValues.indices, id: \.self) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                            }
                        } else if(settings.type == PolarSensorSetting.SettingType.channels) {
                            HStack {
                                Picker("Channels", selection: $selectedChannels) {
                                    ForEach(settings.sortedValues.indices, id: \.self) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                            }
                        } else {
                            EmptyView()
                        }
                    }

                    if showDerivedSection, let group = bleSdkManager.accDerivedSettingsGroup {
                        Section(header: Text("Derived data")) {
                            Toggle("Enable derived data", isOn: $derivedEnabled)

                            if derivedEnabled {
                                if !derivedSourceRates.isEmpty {
                                    Picker("Source rate", selection: $selectedDerivedSourceRateIdx) {
                                        ForEach(derivedSourceRates.indices, id: \.self) { i in
                                            Text("\(derivedSourceRates[i]) Hz")
                                        }
                                    }
                                }

                                if !derivedTimeWindows.isEmpty {
                                    Picker("Time window", selection: $selectedDerivedTimeWindowIdx) {
                                        ForEach(derivedTimeWindows.indices, id: \.self) { i in
                                            Text(formatTimeWindow(derivedTimeWindows[i]))
                                        }
                                    }
                                }

                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Methods").font(.subheadline).foregroundColor(.secondary)
                                    ForEach(group.supportedMethods.sorted(by: { $0.rawValue < $1.rawValue }), id: \.self) { method in
                                        Button(action: { toggleMethod(method) }) {
                                            HStack {
                                                Image(systemName: selectedDerivedMethods.contains(method)
                                                      ? "checkmark.square.fill" : "square")
                                                Text(method.displayName)
                                            }
                                        }
                                        .foregroundColor(.primary)
                                        .buttonStyle(PlainButtonStyle())
                                    }
                                }
                                .padding(.vertical, 4)
                            }
                        }
                    }
                }

                Button(action: {
                    if saveOnly {
                        saveSettings()
                    } else {
                        startStream()
                    }
                    presentationMode.wrappedValue.dismiss()
                }) {
                    Text(saveOnly ? "SAVE" : "START")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.red)
                        .cornerRadius(10)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .navigationTitle("\(getShortNameForDataType(streamedFeature)) settings")
            .toolbar(content: {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        presentationMode.wrappedValue.dismiss()
                    } label: {
                        Text("Cancel")
                    }
                }
            })
            .onAppear { restorePreviousDerivedSettings() }
        }
    }

    private func restorePreviousDerivedSettings() {
        guard showDerivedSection else { return }
        derivedEnabled = false
        if let prev = bleSdkManager.accDerivedSettings {
            selectedDerivedMethods = prev.selectedMethods
            if let idx = derivedSourceRates.firstIndex(of: prev.sourceSampleRate) {
                selectedDerivedSourceRateIdx = idx
            }
            if let idx = derivedTimeWindows.firstIndex(of: prev.timeWindowMs) {
                selectedDerivedTimeWindowIdx = idx
            }
        }
    }

    private func toggleMethod(_ method: PolarDerivedMeasurementMethod) {
        if selectedDerivedMethods.contains(method) {
            if selectedDerivedMethods.count > 1 { selectedDerivedMethods.remove(method) }
        } else {
            selectedDerivedMethods.insert(method)
        }
    }

    private func formatTimeWindow(_ ms: Int) -> String {
        let duration: String
        switch ms {
        case ..<1_000:        duration = "\(ms) ms"
        case ..<60_000:       duration = "\(ms / 1_000) s"
        case ..<3_600_000:    duration = "\(ms / 60_000) min"
        default:              duration = "\(ms / 3_600_000) h"
        }
        let outputLabel: String
        switch ms {
        case 0:               outputLabel = ""
        case ...1_000:        outputLabel = " — \(1000 / ms) Hz output"
        case ..<60_000:       outputLabel = " — 1 sample / \(ms / 1_000) s"
        case ..<3_600_000:    outputLabel = " — 1 sample / \(ms / 60_000) min"
        default:              outputLabel = " — 1 sample / \(ms / 3_600_000) h"
        }
        return "\(duration)\(outputLabel)"
    }

    func saveSettings() {
        var settingValues: [TypeSetting] = []
        if let sampleRate = streamSettings.sortedSettings.first(where: { $0.type == PolarSensorSetting.SettingType.sampleRate })?.sortedValues[selectedSampleRate] {
            settingValues.append(TypeSetting(type: PolarSensorSetting.SettingType.sampleRate, values: [sampleRate]))
        }
        if let range = streamSettings.sortedSettings.first(where: { $0.type == PolarSensorSetting.SettingType.range })?.sortedValues[selectedRange] {
            settingValues.append(TypeSetting(type: PolarSensorSetting.SettingType.range, values: [range]))
        }
        if let resolution = streamSettings.sortedSettings.first(where: { $0.type == PolarSensorSetting.SettingType.resolution })?.sortedValues[selectedResolution] {
            settingValues.append(TypeSetting(type: PolarSensorSetting.SettingType.resolution, values: [resolution]))
        }
        if let channel = streamSettings.sortedSettings.first(where: { $0.type == PolarSensorSetting.SettingType.channels })?.sortedValues[selectedChannels] {
            settingValues.append(TypeSetting(type: PolarSensorSetting.SettingType.channels, values: [channel]))
        }
        bleSdkManager.saveOnlineStreamSettings(
            feature: streamedFeature,
            settings: RecordingSettings(feature: streamedFeature, settings: settingValues)
        )
    }

    func startStream() {
        var settingValues:[TypeSetting] = []
        
        if let sampleRate = streamSettings.sortedSettings.first(where: {$0.type ==  PolarSensorSetting.SettingType.sampleRate})?.sortedValues[selectedSampleRate] {
            settingValues.append(TypeSetting(type: PolarSensorSetting.SettingType.sampleRate, values: [sampleRate]))
        }
        
        if let range = streamSettings.sortedSettings.first(where: {$0.type ==  PolarSensorSetting.SettingType.range})?.sortedValues[selectedRange] {
            settingValues.append(TypeSetting(type: PolarSensorSetting.SettingType.range, values: [range]))
        }
        
        if let resolution = streamSettings.sortedSettings.first(where: {$0.type ==  PolarSensorSetting.SettingType.resolution})?.sortedValues[selectedResolution] {
            settingValues.append(TypeSetting(type: PolarSensorSetting.SettingType.resolution, values: [resolution]))
        }
        
        if let channel = streamSettings.sortedSettings.first(where: {$0.type ==  PolarSensorSetting.SettingType.channels})?.sortedValues[selectedChannels] {
            settingValues.append(TypeSetting(type: PolarSensorSetting.SettingType.channels, values: [channel]))
        }
        
        let selectedSettings = RecordingSettings(feature: streamedFeature, settings: settingValues)

        if isOfflineSettings && streamedFeature == .acc {
            bleSdkManager.accDerivedSettings = nil
        }
        if showDerivedSection, let group = bleSdkManager.accDerivedSettingsGroup {
            if derivedEnabled && !derivedSourceRates.isEmpty && !derivedTimeWindows.isEmpty {
                bleSdkManager.accDerivedSettings = PolarDerivedMeasurementSettings(
                    groupId: group.groupId,
                    sourceMeasurementType: .acc,
                    sourceSampleRate: derivedSourceRates[selectedDerivedSourceRateIdx],
                    timeWindowMs: derivedTimeWindows[selectedDerivedTimeWindowIdx],
                    selectedMethods: selectedDerivedMethods.isEmpty ? [.downsample] : selectedDerivedMethods
                )
            }
        }

        if(isOfflineSettings) {
            bleSdkManager.offlineRecordingStart(feature: streamedFeature, settings: selectedSettings)
        } else {
            bleSdkManager.onlineStreamStart(feature: streamedFeature, settings: selectedSettings)
        }
    }
}

private extension PolarDerivedMeasurementMethod {
    var displayName: String {
        switch self {
        case .downsample:  return "Downsample"
        case .min:         return "Min"
        case .max:         return "Max"
        case .avg:         return "Avg"
        case .std:         return "Std"
        case .norm:        return "Norm"
        case .minOfNorms:  return "Min of norms"
        case .maxOfNorms:  return "Max of norms"
        case .stdOfNorms:  return "Std of norms"
        case .normOfStds:  return "Norm of stds"
        }
    }
}

let tempStreamSettings: RecordingSettings =
RecordingSettings( feature: PolarDeviceDataType.ecg,
                   settings:[ TypeSetting(type: PolarSensorSetting.SettingType.sampleRate, values:[150,160]),
                              TypeSetting(type: PolarSensorSetting.SettingType.range, values: [3,4]),
                              TypeSetting(type: PolarSensorSetting.SettingType.resolution, values: [3,4]),
                              TypeSetting(type: PolarSensorSetting.SettingType.channels, values: [3,4])
                            ])


struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        ForEach(["iPhone 8", "iPAD Pro (12.9-inch)"], id: \.self) { deviceName in
            SettingsView( streamedFeature:PolarDeviceDataType.ecg, streamSettings: tempStreamSettings)
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
                .environmentObject(PolarBleSdkManager())
        }
    }
}
