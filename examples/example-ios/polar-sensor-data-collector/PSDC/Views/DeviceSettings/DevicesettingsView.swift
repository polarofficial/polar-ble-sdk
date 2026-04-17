/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import SwiftUI
import PolarBleSdk
import UniformTypeIdentifiers
import UserNotifications

struct DeviceSettingsView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @State private var isPerformingTimeSet = false
    @State private var isPerformingTimeGet = false
    @State private var isPerformingDiskSpaceGet = false
    @State private var isPerformingSdkModeStatusGet = false
    @State private var isSdkModeLedAnimationEnabled = false
    @State private var isPpiModeLedAnimationEnabled = false
    @State private var isPerformingFirmwareUpdate = false
    @State private var isPerformingMultiBleStatusGet = false
    @State private var isObservingDeviceToHostNotifications = false
    @State private var showNotificationsAsAlerts = UserDefaults.standard.bool(forKey: "showUNNotificationsAsAlerts")
    
    @State private var showSettingsView = false
    @State private var showDatePickerView = false
    @State private var selectedDataTypeForDeletion: String = String(describing: PolarStoredDataType.StoredDataType.ACTIVITY)
    @State private var showDateFolderSelectionView = false
    @State private var showAlert = false
    
    @State private var deviceId: String?
    @State private var showPhysicalDataConfig = false
    @State private var showUserDeviceSettingsConfig = false
    @State private var showFilePicker = false
    @State private var showExercise = false
    @State private var errorMessage = ""
    @State private var showError = false
    @State private var selectedFirmwareFileURL: URL?
    @State private var showFactoryResetAlert = false
    @State private var showTelemetryDeleteAlert = false
    @State private var showPhysicalInfo = false
    @State private var physicalInfoMessage = ""
    @State private var genericApiButtonClickCount = 0
    @State private var showGenericApiButton = false
    @State private var showGenericApiView = false
    @State private var genericButtonColor = Color.clear
    @State private var toast: String? = nil
    @State private var bleSignalStrengthText: String = ""
    
    var body: some View {
        VStack {
            if case .connected = bleSdkManager.deviceConnectionState {
                ScrollView {
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
                                Text("\(bleSdkManager.batteryStatusFeature.batteryLevel.map { "\($0)%" } ?? "Unknown")")
                                Text("\(bleSdkManager.batteryStatusFeature.chargeState)")
                            } else {
                                Text("-")
                            }
                        }
                        
                        HStack {
                            Text("Power sources: ")
                            if bleSdkManager.batteryStatusFeature.isSupported {
                                Text("\(bleSdkManager.batteryStatusFeature.powerSourcesState.description)")
                            } else {
                                Text("-")
                            }
                        }
                        
                        HStack {
                            Button(getFirmwareUpdateButtonText(),
                                   action: {
                                Task {
                                    await bleSdkManager.updateFirmware(withFirmwareURL: selectedFirmwareFileURL)
                                }
                            })
                            .buttonStyle(SecondaryButtonStyle(buttonState: getFirmwareUpdateButtonState()))
                            .disabled(updateAvailable() == false)
                            .overlay {
                                if  bleSdkManager.checkFirmwareUpdateFeature.inProgress ||
                                        bleSdkManager.firmwareUpdateFeature.inProgress {
                                    ProgressView()
                                }
                            }
                            Button(action: { showFilePicker = true }) {
                                Text(getFirmwareFileButtonText())
                            }
                            .padding(.trailing, 30)
                            .disabled(bleSdkManager.firmwareUpdateFeature.inProgress == true)
                            .sheet(isPresented: $showFilePicker) {
                                FilePicker(types: [UTType.zip], asCopy: true, multiSelection: false) { urls in
                                    selectedFirmwareFileURL = urls.first
                                    showFilePicker = false
                                }
                            }
                        }
                        .padding(.trailing)
                        
                        HStack {
                            Text("Status:")
                            Text(bleSdkManager.firmwareUpdateFeature.status)
                                .fontWeight(.bold)
                        }
                        .padding(.top, 8)
                        
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
                        if bleSdkManager.fileTransferFeature.isSupported  {
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
                        }
                        if bleSdkManager.fileTransferFeature.isSupported {
                            Button("Get disk space",
                                   action: {
                                isPerformingDiskSpaceGet = true
                                Task {
                                    await bleSdkManager.getDiskSpace()
                                    isPerformingDiskSpaceGet = false
                                }
                            })
                            .buttonStyle(SecondaryButtonStyle(buttonState: getTimeButtonState()))
                            .disabled(isPerformingDiskSpaceGet)
                            .overlay {
                                if isPerformingDiskSpaceGet {
                                    ProgressView()
                                }
                            }
                        }
                        
                        if bleSdkManager.fileTransferFeature.isSupported {
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
                        }
                    }
                    .task {
                        isPerformingSdkModeStatusGet = true
                        await bleSdkManager.getSdkModeStatus()
                        isPerformingSdkModeStatusGet = false
                    }
                    
                    Button(isSdkModeLedAnimationEnabled ? "Enable SDK mode LED animation" : "Disable SDK mode LED animation",
                           action: {
                        Task {
                            let ledConfig = LedConfig(sdkModeLedEnabled: isSdkModeLedAnimationEnabled, ppiModeLedEnabled: !isPpiModeLedAnimationEnabled)
                            await bleSdkManager.setLedConfig(ledConfig: ledConfig)
                            isSdkModeLedAnimationEnabled = !isSdkModeLedAnimationEnabled
                        }
                    }).buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                    
                    Button(isPpiModeLedAnimationEnabled ? "Enable PPI mode LED animation" : "Disable PPI mode LED animation",
                           action: {
                        Task {
                            let ledConfig = LedConfig(sdkModeLedEnabled: !isSdkModeLedAnimationEnabled, ppiModeLedEnabled: isPpiModeLedAnimationEnabled)
                            await bleSdkManager.setLedConfig(ledConfig: ledConfig)
                            isPpiModeLedAnimationEnabled = !isPpiModeLedAnimationEnabled
                        }
                    }).buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                    
                    Button((bleSdkManager.deviceToHostNotificationDisposable != nil) ? "Stop observing device notifications" : "Start observing device notifications",
                           action: {
                        Task {
                            bleSdkManager.toggleDeviceToHostNotificationObservation()
                        }
                    }).buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                        .padding(.bottom, 24)
                    
                    Toggle("iOS notifications as alerts", isOn: $showNotificationsAsAlerts)
                        .padding(.horizontal, 40)
                        .onChange(of: showNotificationsAsAlerts) { newValue in
                            if let delegate = UNUserNotificationCenter.current().delegate as? NotificationCenterDelegate {
                                delegate.showUNNotificationsAsAlerts = newValue
                            }
                        }
                    
                    HStack {
                        Button("Do factory reset") {
                            showFactoryResetAlert = true
                        }
                        .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                    }
                    .padding()
                    .alert("Confirm Factory Reset", isPresented: $showFactoryResetAlert) {
                        Button("Reset", role: .destructive) {
                            Task {
                                await bleSdkManager.doFactoryReset()
                            }
                        }
                        Button("Cancel", role: .cancel) {}
                    } message: {
                        Text("Are you sure you want to factory reset the device?")
                    }
                    .alert(errorMessage, isPresented: $showError) {
                        Button("OK", role: .cancel) {}
                    }
                    
                    Button("Do restart",
                           action: {
                        Task {
                            await bleSdkManager.doRestart()
                        }
                    }).buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                    
                    if bleSdkManager.fileTransferFeature.isSupported {
                        Button("Do physical data config") {
                            if bleSdkManager.checkIfDeviceIdSet() {
                                showPhysicalDataConfig = true
                            } else {
                                errorMessage = "No device ID available"
                                showError = true
                            }
                        }
                        .sheet(isPresented: $showPhysicalDataConfig) {
                            if let deviceId = bleSdkManager.deviceId {
                                VStack {
                                    PhysicalDataConfigView(deviceId: deviceId)
                                        .environmentObject(bleSdkManager)
#if targetEnvironment(macCatalyst)
                                    Button("Close", action: {
                                        showPhysicalDataConfig = false
                                    })
                                    .padding(.bottom)
                                    .padding(.top)
#endif
                                }
                            } else {
                                Text("No device ID available")
                            }
                        }
                        .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                    }
                    if bleSdkManager.fileTransferFeature.isSupported {
                        Button("Get physical data config") {
                            Task {
                                guard let info = await bleSdkManager.getUserPhysicalConfiguration() else {
                                    errorMessage = "No physical configuration stored on device."
                                    showError = true
                                    return
                                }
                                
                                let df = DateFormatter()
                                df.dateFormat = "yyyy-MM-dd"
                                
                                let sleepMessage = if (info.sleepGoalMinutes > 0) {
                                    "\nSleep goal: \(info.sleepGoalMinutes) min"
                                } else {
                                    ""
                                }
                                physicalInfoMessage = """
                        Gender: \(info.gender)
                        Birthdate: \(df.string(from: info.birthDate))
                        Height: \(info.height) cm
                        Weight: \(info.weight) kg
                        Max HR: \(info.maxHeartRate) bpm
                        VO₂ Max: \(info.vo2Max) ml/kg/min
                        Resting HR: \(info.restingHeartRate) bpm
                        Training background: \(info.trainingBackground)
                        Typical day: \(info.typicalDay)
                        """ + sleepMessage
                                showPhysicalInfo = true
                            }
                        }
                        .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                        .alert("User physical data", isPresented: $showPhysicalInfo) {
                            Button("OK", role: .cancel) { }
                        } message: {
                            Text(physicalInfoMessage)
                        }
                        .alert("Error", isPresented: $showError) {
                            Button("OK", role: .cancel) { }
                        } message: {
                            Text(errorMessage)
                        }
                    }
                    if bleSdkManager.fileTransferFeature.isSupported {
                        Button("Get FTU status",
                               action: {
                            Task {
                                await bleSdkManager.getFtuStatus()
                            }
                        })
                        .buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                    }
                    if bleSdkManager.fileTransferFeature.isSupported {
                        Button("Set up exercise") {
                            if bleSdkManager.checkIfDeviceIdSet() {
                                showExercise = true
                            } else {
                                errorMessage = "No device ID available"
                                showError = true
                            }
                        }
                        .sheet(isPresented: $showExercise) {
                            if let _ = bleSdkManager.deviceId {
                                VStack {
                                    ExerciseView()
                                        .environmentObject(bleSdkManager)
                                    
#if targetEnvironment(macCatalyst)
                                    Button("Close") { showExercise = false }
                                        .padding(.bottom)
                                        .padding(.top)
#endif
                                }
                            } else {
                                Text("No device ID available")
                            }
                        }
                        .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                        .alert("Error", isPresented: $showError) {
                            Button("OK", role: .cancel) { }
                        } message: {
                            Text(errorMessage)
                        }
                    }
                    if bleSdkManager.fileTransferFeature.isSupported {
                        HStack {
                            Button("Set warehouse sleep",
                                   action: {
                                Task {
                                    await bleSdkManager.setWarehouseSleep()
                                }
                            }).buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                        }
                    }
                    if bleSdkManager.fileTransferFeature.isSupported {
                        HStack {
                            Button("Turn device off",
                                   action: {
                                Task {
                                    await bleSdkManager.turnDeviceOff()
                                }
                            }).buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                        }
                    }
                    
                    Button(bleSdkManager.multiBleFeature.isEnabled ? "Disable multi BLE mode" : "Enable multi BLE mode",
                           action: {
                        bleSdkManager.multiBLEModeToggle()
                    })
                    .buttonStyle(SecondaryButtonStyle(buttonState: getMultiBleModeButtonState()))
                    .disabled(isPerformingMultiBleStatusGet || !bleSdkManager.multiBleFeature.isSupported)
                    .overlay {
                        if isPerformingMultiBleStatusGet {
                            ProgressView()
                        }
                    }
                    .task {
                        isPerformingMultiBleStatusGet = true
                        await bleSdkManager.getMultiBleModeStatus()
                        isPerformingMultiBleStatusGet = false
                    }
                    if bleSdkManager.fileTransferFeature.isSupported {
                        HStack {
                            Button("Delete data") {
                                showSettingsView = false
                                showDatePickerView = true
                            }.alignmentGuide(.leading) { d in d[.leading] }
                                .sheet(isPresented: $showDatePickerView) {
                                    DataDeletionSelections(isPresented: $showDatePickerView, showSettingsView: $showSettingsView)
                                }
                                .sheet(isPresented: $showSettingsView) {
                                    DeviceSettingsView()
                                }
                        }.padding(.top, 20)
                            .buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                    }
                    if bleSdkManager.fileTransferFeature.isSupported {
                        HStack {
                            Button("Delete telemetry data") {
                                showTelemetryDeleteAlert = true
                            }
                            .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                        }
                        .padding()
                        .alert("Confirm telemetry data delete", isPresented: $showTelemetryDeleteAlert) {
                            Button("Delete", role: .destructive) {
                                Task {
                                    await bleSdkManager.deleteTelemetryData()
                                }
                            }
                            Button("Cancel", role: .cancel) {}
                        } message: {
                            Text("Are you sure you want to delete telemetry data?")
                        }
                    }
                    if bleSdkManager.fileTransferFeature.isSupported {
                        HStack {
                            Button("Delete date folders") {
                                showSettingsView = false
                                showDateFolderSelectionView = true
                            }
                            .alignmentGuide(.leading) { d in d[.leading] }
                            .sheet(isPresented: $showDateFolderSelectionView) {
                                DateFolderDeletionView(isPresented: $showDateFolderSelectionView, showSettingsView: $showSettingsView)
                            }
                            .sheet(isPresented: $showSettingsView) {
                                DeviceSettingsView()
                            }
                        }.padding(.bottom, 10)
                            .buttonStyle(SecondaryButtonStyle(buttonState: ButtonState.released))
                    }
                    HStack {
                        Button("Do user device settings config") {
                            if bleSdkManager.checkIfDeviceIdSet() {
                                showUserDeviceSettingsConfig = true
                            } else {
                                errorMessage = "No device ID available"
                                showError = true
                            }
                        }
                        .sheet(isPresented: $showUserDeviceSettingsConfig) {
                            if let deviceId = bleSdkManager.deviceId {
                                VStack {
                                    UserDeviceSettingsView(deviceId: deviceId)
                                        .environmentObject(bleSdkManager)
#if targetEnvironment(macCatalyst)
                                    Button("Close", action: {
                                        showUserDeviceSettingsConfig = false
                                    })
                                    .padding(.bottom)
                                    .padding(.top)
#endif
                                }
                            } else {
                                Text("No device ID available")
                            }
                        }
                        .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                    }
                    if bleSdkManager.fileTransferFeature.isSupported {
                        HStack(spacing: 0) {
                            Text("Wait for connection")
                                .font(.headline)
                                .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 20)
                            
                            Button("Wait") {
                                bleSdkManager.waitForConnection()
                            }
                            .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                            .padding(.horizontal, 8)
                            
                            Text(bleSdkManager.deviceConnected ? "Connected" : "Not checked yet")
                                .font(.headline)
                                .padding(.trailing, 16)
                        }
                        .padding(.top, 10)
                    }

                    HStack(spacing: 0) {
                        Text("Read battery charge level")
                            .font(.headline)
                            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 20)
                        
                        Button("Get") {
                            Task {
                                try bleSdkManager.getBatteryChargeLevel()
                                try bleSdkManager.getChargeStatus()
                                toast = "\(bleSdkManager.deviceChargeStatus) Battery: \(bleSdkManager.batteryChargeLevel)%"
                            }
                        }
                        .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                        .padding(.horizontal, 8)
                    }
                    .padding()
                    .overlay(alignment: .bottom) {
                        if let toast {
                            Text(toast)
                                .padding(.horizontal, 14).padding(.vertical, 10)
                                .background(.ultraThinMaterial, in: Capsule())
                                .transition(.opacity.combined(with: .move(edge: .bottom)))
                                .padding(.bottom, 24)
                                .lineLimit(3)
                                .onAppear {
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                                        withAnimation { self.toast = nil }
                                    }
                                }
                        }
                    }

                    HStack(spacing: 0) {
                        Text("Get BLE signal strength")
                            .font(.headline)
                            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 20)

                        Button("Get") {
                            bleSdkManager.checkIfDeviceDisconnectedDueToRemovedPairing()
                            bleSdkManager.getRSSIValue()
                            Task {
                                if bleSdkManager.didDisconnect {
                                    bleSignalStrengthText = "Device was disconnected due to removed pairing. Please reconnect the device."
                                } else {
                                    bleSignalStrengthText = "RSSI: \(bleSdkManager.rssi) dBm"
                                }
                            }
                        }
                        .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                        .padding(.horizontal, 8)

                        Text(bleSignalStrengthText)
                            .font(.headline)
                            .padding(.trailing, 16)
                    }
                    .padding(.top, 10)

                    if bleSdkManager.fileTransferFeature.isSupported {
                        
                        Button(action: {
                            if !bleSdkManager.checkIfDeviceIdSet() {
                                errorMessage = "No device ID available"
                                showError = true
                            }
                            
                            if (genericApiButtonClickCount >= 8) {
                                genericButtonColor = Color.red
                                showGenericApiButton = true
                            }
                            if (genericApiButtonClickCount >= 9 ) {
                                showGenericApiView = true
                            }
                            genericApiButtonClickCount+=1
                        }) {
                            Text("Experimental Low Level Api")
                                .font(.headline)
                                .foregroundColor(showGenericApiButton ? .red : .black.opacity(0))
                                .padding()
                                .frame(width: 300, height: 50)
                                .background(showGenericApiButton ? Color.black.opacity(1) : Color.clear)
                                .contentShape(RoundedRectangle(cornerRadius: 20))
                        }
                        .buttonStyle(PlainButtonStyle())
                        .padding(.horizontal, 8)
                        .sheet(isPresented: $showGenericApiView) {
                            VStack {
                                GenericApiView().environmentObject(bleSdkManager)
#if targetEnvironment(macCatalyst)
                                Button("Close", action: {
                                    showGenericApiView = false
                                })
                                .padding(.bottom)
                                .padding(.top)
#endif
                            }
                        }
                        .buttonStyle(SecondaryButtonStyle(buttonState: .released))
                    }
                }
            } else {
                Text("Not connected")
            }
        }
    }

    func getTimeButtonState() -> ButtonState {
        if bleSdkManager.deviceTimeSetupFeature.isSupported {
            return ButtonState.released
        } else {
            return ButtonState.disabled
        }
    }

    func getFirmwareUpdateButtonText() -> String {
        var buttonText = "Do firmware update"
        if let deviceId = bleSdkManager.deviceId,
           deviceId == bleSdkManager.checkFirmwareUpdateFeature.polarDeviceInfo?.deviceId,
           let version = selectedFirmwareFileURL?.lastPathComponent ?? bleSdkManager.checkFirmwareUpdateFeature.firmwareVersionAvailable {
            buttonText += " to \(version)"
        }
        return buttonText
    }

    func getMultiBleModeButtonState() -> ButtonState {
        if bleSdkManager.multiBleFeature.isSupported {
            if  bleSdkManager.multiBleFeature.isEnabled {
                return ButtonState.pressedDown
            } else {
                return ButtonState.released
            }
        }
        return ButtonState.disabled
    }

    func getFirmwareFileButtonText() -> String {
        return selectedFirmwareFileURL?.lastPathComponent ?? "Select file"
    }

    func updateAvailable() -> Bool {
        if self.selectedFirmwareFileURL != nil {
            return true
        }
        return bleSdkManager.checkFirmwareUpdateFeature.firmwareVersionAvailable != nil &&
        bleSdkManager.firmwareUpdateFeature.inProgress == false
    }

    func getFirmwareUpdateButtonState() -> ButtonState {
        if updateAvailable() {
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
        batteryLevel: nil,
        chargeStatus: .unknown
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

extension BleBasClient.PowerSourcesState {
    var description: String {
        let powerCableStatus = self.wiredExternalPowerConnected == .connected ? "Power cable connected" : "Power cable disconnected"
        return powerCableStatus
    }
}
