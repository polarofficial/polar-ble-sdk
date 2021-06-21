/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import SwiftUI
import PolarBleSdk

struct StreamSettingsView: View {
    @ObservedObject var bleSdkManager: PolarBleSdkManager
    var streamedFeature: DeviceStreamingFeature
    var streamSettings: StreamSettings
    
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
                                Picker(selection: $selectedSampleRate, label: Text("Sample rate")) {
                                    ForEach(0..<settings.sortedValues.count) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                            }
                        }
                        
                        if(settings.type == PolarSensorSetting.SettingType.range) {
                            Picker("Range:", selection: $selectedRange) {
                                ForEach(0..<settings.sortedValues.count) {
                                    Text("\(settings.sortedValues[$0])")
                                }
                            }
                        }
                        
                        if(settings.type == PolarSensorSetting.SettingType.resolution) {
                            HStack {
                                Picker("Resolution:", selection: $selectedResolution) {
                                    ForEach(0..<settings.sortedValues.count) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                            }
                        }
                        
                        if(settings.type == PolarSensorSetting.SettingType.channels) {
                            HStack {
                                Picker("Channels:", selection: $selectedChannels) {
                                    ForEach(0..<settings.sortedValues.count) {
                                        Text("\(settings.sortedValues[$0])")
                                    }
                                }
                            }
                        }
                    }
                    
                    
                    Button("Start \(getStreamingFeatureString(streamedFeature)) stream", action: {
                        startStream()
                        presentationMode.wrappedValue.dismiss()
                        
                    })
                    .buttonStyle(PrimaryButtonStyle(buttonState: ButtonState.released))
                    .padding(15)
                }
            }.navigationTitle("Select \(getStreamingFeatureString(streamedFeature)) stream settings")
        }
    }
    
    func startStream() {
        var settingValues:[StreamSetting] = []
        
        if let sampleRate = streamSettings.sortedSettings.first(where: {$0.type ==  PolarSensorSetting.SettingType.sampleRate})?.sortedValues[selectedSampleRate] {
            settingValues.append(StreamSetting(type: PolarSensorSetting.SettingType.sampleRate, values: [sampleRate]))
        }
        
        if let range = streamSettings.sortedSettings.first(where: {$0.type ==  PolarSensorSetting.SettingType.range})?.sortedValues[selectedRange] {
            settingValues.append(StreamSetting(type: PolarSensorSetting.SettingType.range, values: [range]))
        }
        
        if let resolution = streamSettings.sortedSettings.first(where: {$0.type ==  PolarSensorSetting.SettingType.resolution})?.sortedValues[selectedResolution] {
            settingValues.append(StreamSetting(type: PolarSensorSetting.SettingType.resolution, values: [resolution]))
        }
        
        if let channel = streamSettings.sortedSettings.first(where: {$0.type ==  PolarSensorSetting.SettingType.channels})?.sortedValues[selectedChannels] {
            settingValues.append(StreamSetting(type: PolarSensorSetting.SettingType.channels, values: [channel]))
        }
        
        let selectedSettings = StreamSettings(feature: streamedFeature,settings: settingValues)
        bleSdkManager.streamStart(settings: selectedSettings)
    }
}

let tempStreamSettings: StreamSettings =
    StreamSettings( feature: DeviceStreamingFeature.ecg,
                    settings:[ StreamSetting(type: PolarSensorSetting.SettingType.sampleRate, values:[150,160]),
                               StreamSetting(type: PolarSensorSetting.SettingType.range, values: [3,4]),
                               StreamSetting(type: PolarSensorSetting.SettingType.resolution, values: [3,4]),
                               StreamSetting(type: PolarSensorSetting.SettingType.channels, values: [3,4])
                    ])


struct SheetView_Previews: PreviewProvider {
    
    static var previews: some View {
        ForEach(["iPhone 8", "iPAD Pro (12.9-inch)"], id: \.self) { deviceName in
            StreamSettingsView(bleSdkManager: PolarBleSdkManager(), streamedFeature:DeviceStreamingFeature.ecg, streamSettings: tempStreamSettings)
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
        }
    }
}
