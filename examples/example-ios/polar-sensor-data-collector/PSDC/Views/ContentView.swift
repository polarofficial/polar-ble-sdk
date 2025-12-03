/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import SwiftUI
import PolarBleSdk

enum SelectedAction: String, CaseIterable {
    case online = "Online"
    case offline = "Offline"
    case h10Exercise = "H10 Exercise"
    case settings = "Settings"
    case logging = "Logging"
    case activityRecordingView = "Load"
}

struct ContentView: View {
    @EnvironmentObject private var appState: AppState
    @State private var selectedTab: SelectedAction = .online
    @State private var isSearchingDevices = false
    @State private var connectedDevicesText = ""
    @State private var showHrBroadcastView = false

    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    @EnvironmentObject private var bleDeviceManager: PolarBleDeviceManager
    @State private var appHeader: String = NSLocalizedString("APP_NAME", comment: "")
    @State private var localModifications: String = ""
    @State private var presenting: Bool = false
    
    var body: some View {
        
        Spacer()
        VStack {
            Text(appHeader)
                .multilineTextAlignment(.center)
                .bold()
                .onTapGesture {
                    toggleBuildInformation()
                }
            Text(localModifications)
                .multilineTextAlignment(.center)
                .font(.footnote)
                .foregroundColor(.secondary)
                .onTapGesture {
                    toggleBuildInformation()
                }
            VStack(spacing: 10) {
                if !bleSdkManager.isBluetoothOn {
                    Text("Bluetooth OFF")
                        .bold()
                        .foregroundColor(.red)
                }
                
                Group {
                    switch bleSdkManager.deviceConnectionState {
                    case .noDevice:
                        Button("No device connected"){}.disabled(true)
                    case .disconnected:
                        Button("No device connected"){}.disabled(true)
                    case .connecting(let device):
                        Button("Connecting \(device.deviceId)", action: {
                            bleSdkManager.updateSelectedDevice(device: device)
                        }).buttonStyle(PrimaryButtonStyle(buttonState: getConnectButtonState()))
                        .disabled(true)
                    case .connected(let device):
                        Button("Disconnect \(device.deviceId)", action: {
                            self.disconnect(device: device, deviceManager: bleDeviceManager)
                        }).buttonStyle(PrimaryButtonStyle(buttonState: getConnectButtonState()))
                        if bleDeviceManager.connectedDevices().count > 1 {
                            Button("Disconnect all", action: {
                                bleDeviceManager.disconnectAll()
                            }).buttonStyle(PrimaryButtonStyle(buttonState: getConnectButtonState()))
                        }
                    }
                    VStack {
                        Button("Select device", action: { self.isSearchingDevices = true})
                            .buttonStyle(PrimaryButtonStyle(buttonState: getSearchButtonState()))
                    }
                    
                }.disabled(!bleSdkManager.isBluetoothOn)
                    .sheet(
                        isPresented: $isSearchingDevices,
                        onDismiss: {
                            bleDeviceManager.stopDevicesSearch()
                        }
                    ) {
                        VStack {
                            DeviceSearchView(isPresented: $isSearchingDevices)
                                .environmentObject(bleDeviceManager)
                                .environmentObject(appState)
#if targetEnvironment(macCatalyst)
                            Button("Close", action: {
                                isSearchingDevices = false
                            })
                            .padding(.bottom)
                            .padding(.top)
#endif
                        }
                    }
                    .onTapGesture {
                        self.isSearchingDevices = !(self.isSearchingDevices)
                    }

                if case .noDevice = bleSdkManager.deviceConnectionState {
                    Button("Listen HR Broadcasts", action: {
                        self.showHrBroadcastView = true
                    })
                    .buttonStyle(PrimaryButtonStyle(buttonState: getSearchButtonState()))
                    .disabled(!bleSdkManager.isBluetoothOn)
                    .sheet(isPresented: $showHrBroadcastView) {
                        HrBroadcastView()
                            .environmentObject(bleSdkManager)
                    }
                } else if case .disconnected = bleSdkManager.deviceConnectionState {
                    Button("Listen HR Broadcasts", action: {
                        self.showHrBroadcastView = true
                    })
                    .buttonStyle(PrimaryButtonStyle(buttonState: getSearchButtonState()))
                    .disabled(!bleSdkManager.isBluetoothOn)
                    .sheet(isPresented: $showHrBroadcastView) {
                        HrBroadcastView()
                            .environmentObject(bleSdkManager)
                    }
                }
                
                Text("\(bleSdkManager.connectedDevicesText)")
                
                ForEach(bleSdkManager.switchableDevices, id: \.deviceId) { connectedDevice in
                    Button(connectedDevice.name, action: {
                        bleSdkManager.connectToDevice(withId: connectedDevice.deviceId)
                    })
                }
                
                Picker("Choose operation", selection: $selectedTab) {
                    ForEach(SelectedAction.allCases, id: \.self) {
                        let action = $0.rawValue
                        if (action == "Online" || action == "Settings") {
                            Text(action)
                        } else if( (action == "Offline" && bleSdkManager.deviceConnectionState.get().hasSAGRFCFileSystem) ||
                                    (action == "Logging" && bleSdkManager.deviceConnectionState.get().hasSAGRFCFileSystem) ||
                                    (action == "Load" && bleSdkManager.deviceConnectionState.get().hasSAGRFCFileSystem) ||
                                    (action == "H10 Exercise" && !bleSdkManager.deviceConnectionState.get().hasSAGRFCFileSystem)
                        ){
                            Text(action)
                        }
                    }
                }.pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal)
                
                switch bleSdkManager.deviceConnectionState {
                case .noDevice, .disconnected:
                    Spacer()
                case .connecting:
                   ProgressView()
                case .connected:
                    OperationModesTabView(chosenActionView: selectedTab)
                        .environmentObject(bleSdkManager)
                    
                }
                
            }.frame(maxWidth: .infinity)
            Spacer()
        }
        .alert("", isPresented: $presenting, actions: {
            // do nothing
        }, message: {
            Text(appState.bleSdkManager.generalMessage?.text ?? "?")
        })
        .onChange(of: appState.bleSdkManager.generalMessage?.text) { text in
           presenting = text != nil
        }
    }
    
    func toggleBuildInformation() {
        if appHeader == NSLocalizedString("APP_NAME", comment: "") {
            
            appHeader = NSLocalizedString("APP_NAME", comment: "") + "\n" + getBuildInfo()
            
            let appLocalModifications = 
                Bundle.main.object(forInfoDictionaryKey: "GIT_APP_LOCALLY_MODIFIED") as? String ?? ""
            let sdkLocalModifications = 
                Bundle.main.object(forInfoDictionaryKey: "GIT_SDK_LOCALLY_MODIFIED") as? String ?? ""
            localModifications = ""
            
            if appLocalModifications != "" {
                localModifications += "\nApp locally modified:\n\(appLocalModifications)\n"
            }
            if sdkLocalModifications != "" {
                localModifications += "\nSDK locally modified:\n\(sdkLocalModifications)\n"
            }
            
        } else {
            appHeader = NSLocalizedString("APP_NAME", comment: "")
            localModifications = ""
        }
    }
    
    func getBuildInfo() -> String {
        
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
        
        let appCommit = Bundle.main.object(forInfoDictionaryKey: "APP_GIT_COMMIT") as? String ?? ""
        let appBranch = Bundle.main.object(forInfoDictionaryKey: "APP_GIT_BRANCH") as? String ?? ""
        
        let sdkCommit = Bundle.main.object(forInfoDictionaryKey: "SDK_GIT_COMMIT") as? String ?? ""
        let sdkBranch = Bundle.main.object(forInfoDictionaryKey: "SDK_GIT_BRANCH") as? String ?? ""
        
        return "Version: \(String(describing: version)) Build:\(String(describing: build)) \napp commit: \(appCommit)@\(appBranch) \nsdk commit: \(sdkCommit)@\(sdkBranch)"
    }
    
    func getConnectButtonState() -> ButtonState {
        if bleSdkManager.isBluetoothOn {
            switch bleSdkManager.deviceConnectionState {
            case .noDevice(_):
                return ButtonState.disabled
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
    
    func getSearchButtonState() -> ButtonState {
        if bleSdkManager.isBluetoothOn {
            switch (bleSdkManager.deviceSearch.isSearching) {
            case .inProgress:
                return ButtonState.pressedDown
            case .notStarted, .success:
                return ButtonState.released
            case .failed(error: _):
                return ButtonState.released
            }
        }
        return ButtonState.disabled
    }
    
    func disconnect(device: PolarDeviceInfo, deviceManager: PolarBleDeviceManager) {
        if let (nextDevice, nextSdkManager) = deviceManager.disconnect(device) {
            nextSdkManager.connectToDevice(withId: nextDevice.deviceId)
            appState.switchTo(nextSdkManager)
        } else {
            appState.switchTo(PolarBleSdkManager())
        }
    }
}

struct OperationModesTabView: View {
    @EnvironmentObject private var bleSdkManager: PolarBleSdkManager
    var chosenActionView: SelectedAction
    
    var body: some View {
        ScrollView {
            switch chosenActionView {
            case .online:
                OnlineStreamsView()
                    .environmentObject(bleSdkManager)
            case .offline:
                if (bleSdkManager.deviceConnectionState.get().hasSAGRFCFileSystem) {
                    OfflineRecordingView()
                        .environmentObject(bleSdkManager)
                }
            case .h10Exercise:
                if (!bleSdkManager.deviceConnectionState.get().hasSAGRFCFileSystem) {
                    H10ExerciseView()
                        .environmentObject(bleSdkManager)
                }
            case .settings:
                DeviceSettingsView()
                    .environmentObject(bleSdkManager)
            case .logging:
                if (bleSdkManager.deviceConnectionState.get().hasSAGRFCFileSystem) {
                    SensorDatalogSettingsView()
                        .environmentObject(bleSdkManager)
                }
            case .activityRecordingView:
                if (bleSdkManager.deviceConnectionState.get().hasSAGRFCFileSystem) {
                    ActivityRecordingView()
                        .environmentObject(bleSdkManager)
                }
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    
    private static let offlineRecordingEntries = OfflineRecordingEntries(
        isFetching: false,
        entries: [
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .gyro),
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .acc),
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .magnetometer),
            PolarOfflineRecordingEntry(path: "/test/url", size: 500, date:Date(), type: .temperature)
        ]
    )
    
    private static let offlineRecordingFeature = OfflineRecordingFeature(
        isSupported: true,
        availableOfflineDataTypes: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: false, PolarDeviceDataType.temperature: false, PolarDeviceDataType.skinTemperature: false],
        isRecording: [PolarDeviceDataType.hr: true, PolarDeviceDataType.acc: false, PolarDeviceDataType.ppi: true, PolarDeviceDataType.gyro: false, PolarDeviceDataType.magnetometer: true, PolarDeviceDataType.ecg: true, PolarDeviceDataType.temperature: false, PolarDeviceDataType.skinTemperature: false]
    )
    
    @State static var polarBleSdkManager: PolarBleSdkManager = {
        let polarBleSdkManager = PolarBleSdkManager()
        
        polarBleSdkManager.offlineRecordingFeature = offlineRecordingFeature
        polarBleSdkManager.offlineRecordingEntries = offlineRecordingEntries
        return polarBleSdkManager
    }()
    
    private static let polarBlebleDeviceManager: PolarBleDeviceManager = {
        let polarBlebleDeviceManager = PolarBleDeviceManager()
        return polarBlebleDeviceManager
    }()
    
    private static let appState: AppState = {
        let appState = AppState()
        appState.bleSdkManager = polarBleSdkManager
        appState.bleDeviceManager = polarBlebleDeviceManager

        return appState
    }()

    static var previews: some View {
        let appState = appState
        ForEach(["iPhone 8", "iPAD Pro (12.9-inch)"], id: \.self) { deviceName in
            ContentView()
                .id(nullPolarDeviceInfo.deviceId)
                .previewDevice(PreviewDevice(rawValue: deviceName))
                .previewDisplayName(deviceName)
                .environmentObject(appState)
            
        }
    }
}
