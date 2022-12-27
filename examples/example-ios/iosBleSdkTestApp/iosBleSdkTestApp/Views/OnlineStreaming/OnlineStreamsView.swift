/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct OnlineStreamsView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    
    var body: some View {
        Group {
            Group {
                OnlineStreamingStartButton(dataType: PolarDeviceDataType.ecg)
                OnlineStreamingStartButton(dataType: PolarDeviceDataType.hr)
                OnlineStreamingStartButton(dataType: PolarDeviceDataType.acc)
                OnlineStreamingStartButton(dataType: PolarDeviceDataType.gyro)
                OnlineStreamingStartButton(dataType: PolarDeviceDataType.magnetometer)
                OnlineStreamingStartButton(dataType: PolarDeviceDataType.ppg)
                OnlineStreamingStartButton(dataType: PolarDeviceDataType.ppi)
            }.fullScreenCover(item: $bleSdkManager.onlineStreamSettings) { streamSettings in
                if let settings = streamSettings {
                    SettingsView(streamedFeature: settings.feature, streamSettings: settings)
                }
            }
        }
    }
}

struct OnlineStreamingStartButton: View {
    let dataType: PolarDeviceDataType
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    
    var body: some View {
        Button(getStreamButtonText(dataType, bleSdkManager.isStreamOn(feature: dataType)), action: { streamButtonToggle(dataType) })
            .buttonStyle(SecondaryButtonStyle(buttonState: getStreamButtonState(dataType)))
    }
    
    private func getStreamButtonText(_ feature:PolarDeviceDataType, _ isStreaming: Bool?) -> String {
        let text = getShortNameForDataType(feature)
        let buttonText:String
        if let enabled = isStreaming {
            buttonText = enabled ? "Stop \(text) Stream" : "Start \(text) Stream"
        } else {
            buttonText = "Start \(text) Stream"
        }
        return buttonText
    }
    
    private func streamButtonToggle(_ feature:PolarDeviceDataType) {
        NSLog("Stream toggle for feature \(feature)")
        if(bleSdkManager.isStreamOn(feature: feature)) {
            bleSdkManager.onlineStreamStop(feature: feature)
        } else {
            if(feature == PolarDeviceDataType.ppi || feature == PolarDeviceDataType.hr) {
                bleSdkManager.onlineStreamStart(feature: feature)
            } else {
                bleSdkManager.getOnlineStreamSettings(feature: feature)
            }
        }
    }
    
    private func getStreamButtonState(_ feature: PolarDeviceDataType) -> ButtonState {
        if(bleSdkManager.onlineStreamingFeature.availableOnlineDataTypes[feature] ?? false) {
            if bleSdkManager.isStreamOn(feature: feature) {
                return ButtonState.pressedDown
            } else {
                return ButtonState.released
            }
        } else {
            return ButtonState.disabled
        }
    }
}

struct OnlineStreamsView_Previews: PreviewProvider {
    static var previews: some View {
        ForEach(["iPhone 8", "iPAD Pro (12.9-inch)"], id: \.self) { deviceName in
            OnlineStreamsView()
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
                .environmentObject(PolarBleSdkManager())
        }
    }
}
