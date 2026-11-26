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
        StreamSettingsLogic.derivedSourceRates(bleSdkManager.accDerivedSettingsGroup)
    }
    private var derivedTimeWindows: [Int] {
        StreamSettingsLogic.derivedTimeWindows(bleSdkManager.accDerivedSettingsGroup)
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
                                Picker("settings.sampleRate", selection: $selectedSampleRate) {
                                    ForEach(settings.sortedValues.indices, id: \.self) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                                    .accessibilityIdentifier("psdc.settings.sampleRatePicker")
                            }
                        } else if(settings.type == PolarSensorSetting.SettingType.range) {
                            Picker("settings.range", selection: $selectedRange) {
                                ForEach(settings.sortedValues.indices, id: \.self) {
                                    Text("\(settings.sortedValues[$0])")
                                }
                            }
                                .accessibilityIdentifier("psdc.settings.rangePicker")
                        } else if(settings.type == PolarSensorSetting.SettingType.resolution) {
                            HStack {
                                Picker("settings.resolution", selection: $selectedResolution) {
                                    ForEach(settings.sortedValues.indices, id: \.self) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                                    .accessibilityIdentifier("psdc.settings.resolutionPicker")
                            }
                        } else if(settings.type == PolarSensorSetting.SettingType.channels) {
                            HStack {
                                Picker("settings.channels", selection: $selectedChannels) {
                                    ForEach(settings.sortedValues.indices, id: \.self) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                                    .accessibilityIdentifier("psdc.settings.channelsPicker")
                            }
                        } else {
                            EmptyView()
                        }
                    }

                    if showDerivedSection, let group = bleSdkManager.accDerivedSettingsGroup {
                        Section(header: Text("settings.derived.title")) {
                            Toggle("settings.derived.enable", isOn: $derivedEnabled)
                                .accessibilityIdentifier("psdc.settings.derivedEnableToggle")

                            if derivedEnabled {
                                if !derivedSourceRates.isEmpty {
                                    Picker("settings.derived.sourceRate", selection: $selectedDerivedSourceRateIdx) {
                                        ForEach(derivedSourceRates.indices, id: \.self) { i in
                                            Text("\(derivedSourceRates[i]) Hz")
                                        }
                                    }
                                    .accessibilityIdentifier("psdc.settings.derivedSourceRatePicker")
                                }

                                if !derivedTimeWindows.isEmpty {
                                    Picker("settings.derived.timeWindow", selection: $selectedDerivedTimeWindowIdx) {
                                        ForEach(derivedTimeWindows.indices, id: \.self) { i in
                                            Text(StreamSettingsLogic.formatTimeWindow(derivedTimeWindows[i]))
                                        }
                                    }
                                    .accessibilityIdentifier("psdc.settings.derivedTimeWindowPicker")
                                }

                                VStack(alignment: .leading, spacing: 4) {
                                    Text("settings.derived.methods").font(.subheadline).foregroundColor(.secondary)
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
                                        .accessibilityIdentifier("psdc.settings.derivedMethodButton.\(method.rawValue)")
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
                    Text(LocalizedStringKey(saveOnly ? "settings.save" : "settings.start"))
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.red)
                        .cornerRadius(10)
                }
                .accessibilityIdentifier(saveOnly ? "psdc.settings.saveButton" : "psdc.settings.startButton")
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .navigationTitle(String(format: NSLocalizedString("settings.navTitle", comment: ""), getShortNameForDataType(streamedFeature)))
            .toolbar(content: {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        presentationMode.wrappedValue.dismiss()
                    } label: {
                        Text("common.cancel")
                    }
                    .accessibilityIdentifier("psdc.settings.cancelButton")
                }
            })
            .onAppear {
                restorePreviousSettings()
                restorePreviousDerivedSettings()
            }
        }
    }

    /// Selection comes from the controller's saved settings; the view holds no defaults of its own.
    private func restorePreviousSettings() {
        guard !isOfflineSettings,
              let saved = bleSdkManager.savedOnlineStreamSettings[streamedFeature] else { return }
        let indices = StreamSettingsLogic.restoredIndices(saved: saved, available: streamSettings)
        selectedSampleRate = indices.sampleRate
        selectedRange = indices.range
        selectedResolution = indices.resolution
        selectedChannels = indices.channels
    }

    private func restorePreviousDerivedSettings() {
        guard showDerivedSection else { return }
        let restored = StreamSettingsLogic.restoredDerived(
            saved: bleSdkManager.accDerivedSettings, group: bleSdkManager.accDerivedSettingsGroup)
        derivedEnabled = restored.enabled
        selectedDerivedMethods = restored.methods
        selectedDerivedSourceRateIdx = restored.sourceRateIndex
        selectedDerivedTimeWindowIdx = restored.timeWindowIndex
    }

    private func toggleMethod(_ method: PolarDerivedMeasurementMethod) {
        selectedDerivedMethods = StreamSettingsLogic.toggled(method, in: selectedDerivedMethods)
    }

    private var chosenSettings: RecordingSettings {
        StreamSettingsLogic.chosen(
            from: streamSettings, feature: streamedFeature,
            indices: .init(sampleRate: selectedSampleRate, range: selectedRange,
                           resolution: selectedResolution, channels: selectedChannels))
    }

    func saveSettings() {
        bleSdkManager.saveOnlineStreamSettings(feature: streamedFeature, settings: chosenSettings)
    }

    func startStream() {
        let selectedSettings = chosenSettings

        if isOfflineSettings && streamedFeature == .acc {
            bleSdkManager.accDerivedSettings = nil
        }
        if showDerivedSection, let group = bleSdkManager.accDerivedSettingsGroup, derivedEnabled,
           let derived = StreamSettingsLogic.derivedSettings(
                group: group, sourceRateIndex: selectedDerivedSourceRateIdx,
                timeWindowIndex: selectedDerivedTimeWindowIdx, methods: selectedDerivedMethods) {
            bleSdkManager.accDerivedSettings = derived
        }

        if(isOfflineSettings) {
            bleSdkManager.offlineRecordingStart(feature: streamedFeature, settings: selectedSettings)
        } else {
            bleSdkManager.saveOnlineStreamSettings(feature: streamedFeature, settings: selectedSettings)
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
