/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct OfflineRecordingStartView: View {
    @EnvironmentObject var bleSdkManager: PolarBleSdkManager
    
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
                    }
                }.fullScreenCover(item: $bleSdkManager.offlineRecordingSettings) { offlineRecSettings in
                    let settings = offlineRecSettings
                    SettingsView(streamedFeature: settings.feature, streamSettings: settings, isOfflineSettings: true)
                }
            }.task {
                await bleSdkManager.getOfflineRecordingStatus()
            }
        } else {
            Text("Offline recording is not supported")
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
            availableOfflineDataTypes: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: false],
            isRecording: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: true]
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
