/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import SwiftUI
import PolarBleSdk

struct SettingsView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    let streamedFeature: PolarDeviceDataType
    let streamSettings: RecordingSettings
    var isOfflineSettings: Bool = false
    
    @Environment(\.presentationMode) var presentationMode
    
    @State private var selectedSampleRate:Int = 0
    @State private var selectedRange:Int = 0
    @State private var selectedResolution:Int = 0
    @State private var selectedChannels:Int = 0
    
    var body: some View {
        NavigationView {
            VStack() {
                Form {
                    ForEach(streamSettings.sortedSettings) { settings in
                        if(settings.type == PolarSensorSetting.SettingType.sampleRate) {
                            HStack {
                                Picker("Sample rate", selection: $selectedSampleRate) {
                                    ForEach(0..<settings.sortedValues.count) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                            }
                        } else if(settings.type == PolarSensorSetting.SettingType.range) {
                            Picker("Range", selection: $selectedRange) {
                                ForEach(0..<settings.sortedValues.count) {
                                    Text("\(settings.sortedValues[$0])")
                                }
                            }
                        } else if(settings.type == PolarSensorSetting.SettingType.resolution) {
                            HStack {
                                Picker("Resolution", selection: $selectedResolution) {
                                    ForEach(0..<settings.sortedValues.count) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                            }
                        } else if(settings.type == PolarSensorSetting.SettingType.channels) {
                            HStack {
                                Picker("Channels", selection: $selectedChannels) {
                                    ForEach(0..<settings.sortedValues.count) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                            }
                        } else {
                            EmptyView()
                        }
                    }
                    
                    Button( isOfflineSettings ?
                            "Start \(getShortNameForDataType(streamedFeature)) offline recording" :
                                "Start \(getShortNameForDataType(streamedFeature)) online stream",
                            action: {
                        startStream()
                        presentationMode.wrappedValue.dismiss()
                        
                    })
                    .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.released))
                    .padding(15)
                }
            }.navigationTitle("\(getShortNameForDataType(streamedFeature)) settings")
                .toolbar(content: {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            presentationMode.wrappedValue.dismiss()
                        } label: {
                            Text("Cancel")
                        }
                    }
                })
        }
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
        
        let selectedSettings = RecordingSettings(feature: streamedFeature,settings: settingValues)
        
        if(isOfflineSettings) {
            bleSdkManager.offlineRecordingStart(feature: streamedFeature, settings: selectedSettings)
        } else {
            bleSdkManager.onlineStreamStart(feature: streamedFeature, settings: selectedSettings)
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
