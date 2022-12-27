/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk

struct DeviceSettingsView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @State private var isPerformingTimeSet = false
    @State private var isPerformingTimeGet = false
    @State private var isPerformingSdkModeStatusGet = false
    
    var body: some View {
        VStack {
            
            HStack {
                Text("Firmware version:")
                if(bleSdkManager.deviceInfoFeature.isSupported) {
                    Text("\(bleSdkManager.deviceInfoFeature.firmwareVersion)")
                } else {
                    Text("-")
                }
            }
            
            HStack {
                Text("Battery level: ")
                if bleSdkManager.batteryStatusFeature.isSupported {
                    Text("\(bleSdkManager.batteryStatusFeature.batteryLevel)%")
                } else {
                    Text("-")
                }
            }
            
            Button("Set time",
                   action: {
                isPerformingTimeSet = true
                Task {
                    await bleSdkManager.setTime()
                    isPerformingTimeSet = false
                }
            })
            .buttonStyle(SecondaryButtonStyle(buttonState: getTimeButtonState()))
            .disabled(isPerformingTimeSet)
            .overlay {
                if isPerformingTimeSet {
                    ProgressView()
                }
            }
            
            Button("Get time",
                   action: {
                isPerformingTimeGet = true
                Task {
                    await bleSdkManager.getTime()
                    isPerformingTimeGet = false
                }
            })
            .buttonStyle(SecondaryButtonStyle(buttonState: getTimeButtonState()))
            .disabled(isPerformingTimeGet)
            .overlay {
                if isPerformingTimeGet {
                    ProgressView()
                }
            }
            
            Button(bleSdkManager.sdkModeFeature.isEnabled ? "Disable SDK mode" : "Enable SDK mode",
                   action: {
                bleSdkManager.sdkModeToggle()
                
            })
            .buttonStyle(SecondaryButtonStyle(buttonState: getSdkModeButtonState()))
            .disabled(isPerformingSdkModeStatusGet)
            .overlay {
                if isPerformingSdkModeStatusGet {
                    ProgressView()
                }
            }
        }.task {
            isPerformingSdkModeStatusGet = true
            await bleSdkManager.getSdkModeStatus()
            isPerformingSdkModeStatusGet = false
        }
    }
    
    func getTimeButtonState() -> ButtonState {
        if bleSdkManager.deviceTimeSetupFeature.isSupported {
            return ButtonState.released
        } else {
            return ButtonState.disabled
        }
    }
    
    func getSdkModeButtonState() -> ButtonState {
        if bleSdkManager.sdkModeFeature.isSupported {
            if  bleSdkManager.sdkModeFeature.isEnabled {
                return ButtonState.pressedDown
            } else {
                return ButtonState.released
            }
        }
        return ButtonState.disabled
    }
}

struct DeviceSettingsView_Previews: PreviewProvider {
    private static let deviceInfoFeature = DeviceInfoFeature(
        isSupported: true,
        firmwareVersion: "2.1.0"
    )
    
    private static let batteryStatusFeature = BatteryStatusFeature(
        isSupported: true,
        batteryLevel: UInt(99)
    )
    
    private static let deviceTimeSetupFeature = DeviceTimeSetupFeature(
        isSupported: true
    )
    
    private static let sdkModeFeature = SdkModeFeature(
        isSupported: true,
        isEnabled: true
    )
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        polarBleSdkManager.sdkModeFeature = sdkModeFeature
        polarBleSdkManager.deviceTimeSetupFeature = deviceTimeSetupFeature
        polarBleSdkManager.deviceInfoFeature = deviceInfoFeature
        polarBleSdkManager.batteryStatusFeature = batteryStatusFeature
        return polarBleSdkManager
    }()
    
    static var previews: some View {
        ForEach(["iPhone 7 Plus", "iPad Pro (12.9-inch) (6th generation)"], id: \.self) { deviceName in
            DeviceSettingsView()
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
                .environmentObject(polarBleSdkManager)
        }
    }
}
