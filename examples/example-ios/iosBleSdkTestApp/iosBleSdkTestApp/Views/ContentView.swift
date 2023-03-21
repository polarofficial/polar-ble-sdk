/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import SwiftUI
import PolarBleSdk

extension Text {
    func headerStyle() -> some View {
        self
            .font(.headline)
            .foregroundColor(.secondary)
            .fontWeight(.light)
    }
}

enum SelectedAction: String, CaseIterable {
    case online = "Online"
    case offline = "Offline"
    case h10Exercise = "H10 Exercise"
    case settings = "Settings"
}

struct ContentView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @State private var selectedTab: SelectedAction = .online
    @State private var isSearchingDevices = false
    
    var body: some View {
        VStack {
            Text("Polar BLE SDK example app")
                .bold()
            VStack(spacing: 10) {
                if !bleSdkManager.isBluetoothOn {
                    Text("Bluetooth OFF")
                        .bold()
                        .foregroundColor(.red)
                }
                
                Group {
                    Button( bleSdkManager.isBroadcastListenOn ? "Listening broadcast" : "Listen broadcast", action: {bleSdkManager.broadcastToggle()})
                        .buttonStyle(PrimaryButtonStyle(buttonState: getBroadcastButtonState()))
                    
                    switch bleSdkManager.deviceConnectionState {
                    case .disconnected(let deviceId):
                        Button("Connect \(deviceId)", action: {bleSdkManager.connectToDevice()})
                            .buttonStyle(PrimaryButtonStyle(buttonState: getConnectButtonState()))
                    case .connecting(let deviceId):
                        Button("Connecting \(deviceId)", action: {})
                            .buttonStyle(PrimaryButtonStyle(buttonState: getConnectButtonState()))
                            .disabled(true)
                    case .connected(let deviceId):
                        Button("Disconnect \(deviceId)", action: {bleSdkManager.disconnectFromDevice()})
                            .buttonStyle(PrimaryButtonStyle(buttonState: getConnectButtonState()))
                    }
                    
                    Button("Auto Connect", action: { bleSdkManager.autoConnect()})
                        .buttonStyle(PrimaryButtonStyle(buttonState: getAutoConnectButtonState()))
                    
                    VStack {
                        Button("Search devices", action: { self.isSearchingDevices = true})
                            .buttonStyle(PrimaryButtonStyle(buttonState: getSearchButtonState()))
                    }
                    .sheet(
                        isPresented: $isSearchingDevices,
                        onDismiss: { bleSdkManager.stopDevicesSearch()}
                    ) {
                        DeviceSearchView(isPresented: self.$isSearchingDevices)
                    }
                }.disabled(!bleSdkManager.isBluetoothOn)
                
                Picker("Choose operation", selection: $selectedTab) {
                    ForEach(SelectedAction.allCases, id: \.self) {
                        Text($0.rawValue)
                    }
                }.pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal)
                OperationModesTabView(chosenActionView: selectedTab)
                
            }.frame(maxWidth: .infinity)
            Spacer()
        }
        .alert(item: $bleSdkManager.generalMessage) { message in
            Alert(
                title: Text(message.text)
            )
        }
    }
    
    func getConnectButtonState() -> ButtonState {
        if bleSdkManager.isBluetoothOn {
            switch bleSdkManager.deviceConnectionState {
            case .disconnected:
                return ButtonState.released
            case .connecting(_):
                return ButtonState.disabled
            case .connected(_):
                return ButtonState.pressedDown
            }
        }
        return ButtonState.disabled
    }
    
    func getBroadcastButtonState() -> ButtonState {
        if bleSdkManager.isBluetoothOn {
            if bleSdkManager.isBroadcastListenOn {
                return ButtonState.pressedDown
            } else {
                return ButtonState.released
            }
        }
        return ButtonState.disabled
    }
    
    func getAutoConnectButtonState() -> ButtonState {
        if bleSdkManager.isBluetoothOn,
           case .disconnected = bleSdkManager.deviceConnectionState {
            return ButtonState.released
        } else {
            return ButtonState.disabled
        }
    }
    
    func getSearchButtonState() -> ButtonState {
        if bleSdkManager.isBluetoothOn {
            switch (bleSdkManager.deviceSearch.isSearching) {
            case .inProgress:
                return ButtonState.pressedDown
            case .success:
                return ButtonState.released
            case .failed(error: _):
                return ButtonState.released
            }
        }
        return ButtonState.disabled
    }
}

struct OperationModesTabView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    var chosenActionView: SelectedAction
    
    var body: some View {
        switch chosenActionView {
        case .online:
            OnlineStreamsView()
        case .offline:
            OfflineRecordingView()
        case .h10Exercise:
            H10ExerciseView()
        case .settings:
            DeviceSettingsView()
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    
    private static let offlineRecordingEntries = OfflineRecordingEntries(
        isFetching: false,
        entries: [
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .gyro),
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .acc),
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .magnetometer)]
    )
    
    private static let offlineRecordingFeature = OfflineRecordingFeature(
        isSupported: true,
        availableOfflineDataTypes: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: false],
        isRecording: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: true]
    )
    
    private static let polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        
        polarBleSdkManager.offlineRecordingFeature = offlineRecordingFeature
        polarBleSdkManager.offlineRecordingEntries = offlineRecordingEntries
        return polarBleSdkManager
    }()
    
    static var previews: some View {
        ForEach(["iPhone 8", "iPAD Pro (12.9-inch)"], id: \.self) { deviceName in
            ContentView()
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
                .environmentObject(polarBleSdkManager)
        }
    }
}
