/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import RxSwift
import CoreBluetooth

/// PolarBleSdkManager demonstrates how to user PolarBleSDK API
class PolarBleSdkManager : ObservableObject {
    // NOTICE this example utilises all available features
    private var api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main,
                                                                 features: [PolarBleSdkFeature.feature_hr,
                                                                            PolarBleSdkFeature.feature_polar_sdk_mode,
                                                                            PolarBleSdkFeature.feature_battery_info,
                                                                            PolarBleSdkFeature.feature_device_info,
                                                                            PolarBleSdkFeature.feature_polar_online_streaming,
                                                                            PolarBleSdkFeature.feature_polar_offline_recording,
                                                                            PolarBleSdkFeature.feature_polar_device_time_setup,
                                                                            PolarBleSdkFeature.feature_polar_h10_exercise_recording]
    )
    
    // TODO replace the device id with your device ID or use the auto connect to when connecting to device
    private static let deviceId = "C015D22B"
    
    @Published var isBluetoothOn: Bool
    @Published var isBroadcastListenOn: Bool = false
    
    @Published var deviceConnectionState: DeviceConnectionState = DeviceConnectionState.disconnected(deviceId)
    
    @Published var deviceSearch: DeviceSearch = DeviceSearch()
    
    @Published var onlineStreamingFeature: OnlineStreamingFeature = OnlineStreamingFeature()
    @Published var onlineStreamSettings: RecordingSettings? = nil
    
    @Published var offlineRecordingFeature = OfflineRecordingFeature()
    @Published var offlineRecordingSettings: RecordingSettings? = nil
    @Published var offlineRecordingEntries: OfflineRecordingEntries = OfflineRecordingEntries()
    @Published var offlineRecordingData: OfflineRecordingData = OfflineRecordingData()
    
    @Published var deviceTimeSetupFeature: DeviceTimeSetupFeature = DeviceTimeSetupFeature()
    
    @Published var sdkModeFeature: SdkModeFeature = SdkModeFeature()
    
    @Published var h10RecordingFeature: H10RecordingFeature = H10RecordingFeature()
    
    @Published var deviceInfoFeature: DeviceInfoFeature = DeviceInfoFeature()
    
    @Published var batteryStatusFeature: BatteryStatusFeature = BatteryStatusFeature()
    
    @Published var ledAnimationFeature: LedAnimationFeature = LedAnimationFeature()
    
    @Published var generalMessage: Message? = nil
    
    private var broadcastDisposable: Disposable?
    private var autoConnectDisposable: Disposable?
    private var onlineStreamingDisposables: [PolarDeviceDataType: Disposable?] = [:]
    
    private let disposeBag = DisposeBag()
    private var h10ExerciseEntry: PolarExerciseEntry?
    
    private var searchDevicesTask: Task<Void, Never>? = nil
    
    init() {
        self.isBluetoothOn = api.isBlePowered
        
        api.polarFilter(true)
        api.observer = self
        api.deviceFeaturesObserver = self
        api.powerStateObserver = self
        api.deviceInfoObserver = self
        api.logger = self
    }
    
    func broadcastToggle() {
        if isBroadcastListenOn == false {
            isBroadcastListenOn = true
            broadcastDisposable = api.startListenForPolarHrBroadcasts(nil)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        self.isBroadcastListenOn = false
                        NSLog("Broadcast listener completed")
                    case .error(let err):
                        self.isBroadcastListenOn = false
                        NSLog("Broadcast listener failed. Reason: \(err)")
                    case .next(let broadcast):
                        NSLog("HR BROADCAST \(broadcast.deviceInfo.name) HR:\(broadcast.hr) Batt: \(broadcast.batteryStatus)")
                    }
                }
        } else {
            isBroadcastListenOn = false
            broadcastDisposable?.dispose()
        }
    }
    
    func updateSelectedDevice( deviceId : String) {
        if case .disconnected = deviceConnectionState {
            Task { @MainActor in
                self.deviceConnectionState = DeviceConnectionState.disconnected(deviceId)
            }
        }
    }
    
    func connectToDevice() {
        if case .disconnected(let deviceId) = deviceConnectionState {
            do {
                try api.connectToDevice(deviceId)
            } catch let err {
                NSLog("Failed to connect to \(deviceId). Reason \(err)")
            }
        }
    }
    
    func disconnectFromDevice() {
        if case .connected(let deviceId) = deviceConnectionState {
            do {
                try api.disconnectFromDevice(deviceId)
            } catch let err {
                NSLog("Failed to disconnect from \(deviceId). Reason \(err)")
            }
        }
    }
    
    func autoConnect() {
        autoConnectDisposable?.dispose()
        autoConnectDisposable = api.startAutoConnectToDevice(-55, service: nil, polarDeviceType: nil)
            .subscribe{ e in
                switch e {
                case .completed:
                    NSLog("auto connect search complete")
                case .error(let err):
                    NSLog("auto connect failed: \(err)")
                }
            }
    }
    
    func startDevicesSearch() {
        searchDevicesTask = Task {
            await searchDevicesAsync()
        }
    }
    
    func stopDevicesSearch() {
        searchDevicesTask?.cancel()
        searchDevicesTask = nil
        Task { @MainActor in
            self.deviceSearch.isSearching = DeviceSearchState.success
        }
    }
    
    private func searchDevicesAsync() async {
        Task { @MainActor in
            self.deviceSearch.foundDevices.removeAll()
            self.deviceSearch.isSearching = DeviceSearchState.inProgress
        }
        
        do {
            for try await value in api.searchForDevice().values {
                Task { @MainActor in
                    self.deviceSearch.foundDevices.append(value)
                }
            }
            Task { @MainActor in
                self.deviceSearch.isSearching = DeviceSearchState.success
            }
        } catch let err {
            let deviceSearchFailed = "device search failed: \(err)"
            NSLog(deviceSearchFailed)
            Task { @MainActor in
                self.deviceSearch.isSearching = DeviceSearchState.failed(error: deviceSearchFailed)
            }
        }
    }
    
    func getOnlineStreamSettings(feature: PolarBleSdk.PolarDeviceDataType) {
        if case .connected(let deviceId) = deviceConnectionState {
            NSLog("Online stream settings fetch for \(feature)")
            api.requestStreamSettings(deviceId, feature: feature)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .success(let settings):
                        NSLog("Online stream settings fetch completed for \(feature)")
                        
                        var receivedSettings:[TypeSetting] = []
                        for setting in settings.settings {
                            var values:[Int] = []
                            for settingsValue in setting.value {
                                values.append(Int(settingsValue))
                            }
                            receivedSettings.append(TypeSetting(type: setting.key, values: values))
                        }
                        
                        self.onlineStreamSettings = RecordingSettings(feature: feature, settings: receivedSettings)
                        
                    case .failure(let err):
                        self.somethingFailed(text: "Online stream settings request failed: \(err)")
                        self.onlineStreamSettings = nil
                    }
                }.disposed(by: disposeBag)
        } else {
            NSLog("Online stream settings request failed. Device is not connected \(deviceConnectionState)")
        }
    }
    
    func getOfflineRecordingSettings(feature: PolarBleSdk.PolarDeviceDataType) {
        if case .connected(let deviceId) = deviceConnectionState {
            NSLog("Offline recording settings fetch for \(feature)")
            api.requestOfflineRecordingSettings(deviceId, feature: feature)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .success(let settings):
                        NSLog("Offline recording settings fetch completed for \(feature)")
                        var receivedSettings:[TypeSetting] = []
                        for setting in settings.settings {
                            var values:[Int] = []
                            for settingsValue in setting.value {
                                values.append(Int(settingsValue))
                            }
                            receivedSettings.append(TypeSetting(type: setting.key, values: values))
                        }
                        self.offlineRecordingSettings = RecordingSettings(feature: feature, settings: receivedSettings)
                    case .failure(let err):
                        self.somethingFailed(text: "Offline recording settings request failed: \(err)")
                        self.onlineStreamSettings = nil
                    }
                }.disposed(by: disposeBag)
        } else {
            NSLog("Offline recording settings request failed. Device is not connected \(deviceConnectionState)")
        }
    }
    
    func onlineStreamStart(feature: PolarDeviceDataType, settings: RecordingSettings? = nil) {
        var logString:String = "Request \(feature) online stream start with settings: "
        
        var polarSensorSettings: [PolarSensorSetting.SettingType : UInt32] = [:]
        settings?.settings.forEach {
            polarSensorSettings[$0.type] = UInt32($0.values[0])
            logString.append(" \($0.type) \($0.values[0])")
        }
        NSLog(logString)
        
        switch feature {
        case .ecg:
            ecgStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .acc:
            accStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .magnetometer:
            magStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .ppg:
            ppgStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .gyro:
            gyrStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .ppi:
            ppiStreamStart()
        case .hr:
            hrStreamStart()
        case .temperature:
            temperatureStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .pressure:
            pressureStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        }
    }
    
    func onlineStreamStop(feature: PolarBleSdk.PolarDeviceDataType) {
        onlineStreamingDisposables[feature]??.dispose()
    }
    
    func listOfflineRecordings() async {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.offlineRecordingEntries.entries.removeAll()
                self.offlineRecordingEntries.isFetching = true
            }
            NSLog("Start offline recording listing")
            api.listOfflineRecordings(deviceId)
                .observe(on: MainScheduler.instance)
                .debug("listOfflineRecordings")
                .do(
                    onDispose: {
                        self.offlineRecordingEntries.isFetching = false
                    })
                .subscribe{ e in
                    switch e {
                    case .next(let entry):
                        self.offlineRecordingEntries.entries.append(entry)
                    case .error(let err):
                        NSLog("Offline recording listing error: \(err)")
                    case .completed:
                        NSLog("Offline recording listing completed")
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func getOfflineRecordingStatus() async {
        if case .connected(let deviceId) = deviceConnectionState {
            NSLog("getOfflineRecordingStatus")
            api.getOfflineRecordingStatus(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe { e in
                    switch e {
                    case .success(let offlineRecStatus):
                        NSLog("Enabled offline rec features \(offlineRecStatus)")
                        self.offlineRecordingFeature.isRecording = offlineRecStatus
                        
                    case .failure(let err):
                        NSLog("Failed to get status of offline recording \(err)")
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func removeOfflineRecording(offlineRecordingEntry: PolarOfflineRecordingEntry) async {
        if case .connected(let deviceId) = deviceConnectionState {
            do {
                NSLog("start offline recording removal")
                let _: Void = try await api.removeOfflineRecord(deviceId, entry: offlineRecordingEntry).value
                NSLog("offline recording removal completed")
                Task { @MainActor in
                    self.offlineRecordingEntries.entries.removeAll{$0 == offlineRecordingEntry}
                }
            } catch let err {
                NSLog("offline recording remove failed: \(err)")
            }
        }
    }
    
    func getOfflineRecording(offlineRecordingEntry: PolarOfflineRecordingEntry) async {
        if case .connected(let deviceId) = deviceConnectionState {
            Task { @MainActor in
                self.offlineRecordingData.loadState = OfflineRecordingDataLoadingState.inProgress
            }
            
            do {
                NSLog("start offline recording \(offlineRecordingEntry.path) fetch")
                let readStartTime = Date()
                let offlineRecording: PolarOfflineRecordingData = try await api.getOfflineRecord(deviceId, entry: offlineRecordingEntry, secret: nil).value
                let elapsedTime = Date().timeIntervalSince(readStartTime)
                
                switch offlineRecording {
                case .accOfflineRecordingData(let data, let startTime, let settings):
                    NSLog("ACC data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = settings
                        self.offlineRecordingData.data = dataHeaderString(.acc) + dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .gyroOfflineRecordingData(let data, startTime: let startTime, settings: let settings):
                    NSLog("GYR data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = settings
                        self.offlineRecordingData.data = dataHeaderString(.gyro) + dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .magOfflineRecordingData(let data, startTime: let startTime, settings: let settings):
                    NSLog("MAG data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = settings
                        self.offlineRecordingData.data = dataHeaderString(.magnetometer) + dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .ppgOfflineRecordingData(let data, startTime: let startTime, settings: let settings):
                    NSLog("PPG data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = settings
                        self.offlineRecordingData.data = dataHeaderString(.ppg) + dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .ppiOfflineRecordingData(let data, startTime: let startTime):
                    NSLog("PPI data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = nil
                        self.offlineRecordingData.data = dataHeaderString(.ppi) + dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .hrOfflineRecordingData(let data, startTime: let startTime):
                    NSLog("HR data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = nil
                        self.offlineRecordingData.data = dataHeaderString(.hr) + dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .temperatureOfflineRecordingData(let data, startTime: let startTime):
                    NSLog("TEMP data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = nil
                        self.offlineRecordingData.data = dataHeaderString(.temperature) + dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                }
                Task { @MainActor in
                    self.offlineRecordingData.loadState = OfflineRecordingDataLoadingState.success
                }
            } catch let err {
                NSLog("offline recording read failed: \(err)")
                Task { @MainActor in
                    self.offlineRecordingData.loadState = OfflineRecordingDataLoadingState.failed(error: "offline recording read failed: \(err)")
                }
            }
        }
    }
    
    func offlineRecordingStart(feature: PolarDeviceDataType, settings: RecordingSettings? = nil) {
        if case .connected(let deviceId) = deviceConnectionState {
            var logString:String = "Request offline recording \(feature) start with settings: "
            
            var polarSensorSettings:[PolarSensorSetting.SettingType : UInt32] = [:]
            settings?.settings.forEach {
                polarSensorSettings[$0.type] = UInt32($0.values[0])
                logString.append(" \($0.type) \($0.values[0])")
            }
            NSLog(logString)
            
            api.startOfflineRecording(deviceId, feature: feature, settings: PolarSensorSetting(polarSensorSettings), secret: nil)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        self.offlineRecordingFeature.isRecording[feature] = true
                        NSLog("offline recording \(feature) successfully started")
                    case .error(let err):
                        NSLog("failed to start offline recording \(feature). Reason: \(err)")
                    }
                }.disposed(by: disposeBag)
        } else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
        }
    }
    
    func offlineRecordingStop(feature: PolarDeviceDataType) {
        if case .connected(let deviceId) = deviceConnectionState {
            NSLog("Request offline recording \(feature) stop")
            api.stopOfflineRecording(deviceId, feature: feature)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        self.offlineRecordingFeature.isRecording[feature] = false
                        NSLog("offline recording \(feature) successfully stopped")
                    case .error(let err):
                        NSLog("failed to stop offline recording \(feature). Reason: \(err)")
                    }
                }.disposed(by: disposeBag)
        } else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
        }
    }
    
    func isStreamOn(feature: PolarBleSdk.PolarDeviceDataType) -> Bool {
        if case .inProgress = self.onlineStreamingFeature.isStreaming[feature] {
            return true
        } else {
            return false
        }
    }
    
    func ecgStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.ecg] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .ecg)
            
            onlineStreamingDisposables[.ecg] = api.startEcgStreaming(deviceId, settings: settings)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.ecg] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe { e in
                    switch e {
                    case .next(let data):
                        if let fileHandle = logFile?.fileHandle {
                            self.writeOnlineStreamLogFile(fileHandle, data)
                        }
                        
                        for item in data.samples {
                            NSLog("ECG    µV: \(item.voltage) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("ECG stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("ECG stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func accStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.acc] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .acc)
            
            NSLog("ACC stream start: \(deviceId)")
            onlineStreamingDisposables[.acc] = api.startAccStreaming(deviceId, settings: settings)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.acc] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if let fileHandle = logFile?.fileHandle {
                            self.writeOnlineStreamLogFile(fileHandle, data)
                        }
                        for item in data.samples {
                            NSLog("ACC    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("ACC stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("ACC stream completed")
                        break
                    }
                }
        } else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
        }
    }
    
    func magStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.magnetometer] = OnlineStreamingState.inProgress
            }
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .magnetometer)
            
            onlineStreamingDisposables[.magnetometer] = api.startMagnetometerStreaming(deviceId, settings: settings)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.magnetometer] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if let fileHandle = logFile?.fileHandle {
                            self.writeOnlineStreamLogFile(fileHandle, data)
                        }
                        for item in data.samples {
                            NSLog("MAG    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("MAG stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("MAG stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func gyrStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.gyro] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .gyro)
            
            onlineStreamingDisposables[.gyro] = api.startGyroStreaming(deviceId, settings: settings)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.gyro] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if let fileHandle = logFile?.fileHandle {
                            self.writeOnlineStreamLogFile(fileHandle, data)
                        }
                        for item in data.samples {
                            NSLog("GYR    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("GYR stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("GYR stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func ppgStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.ppg] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .ppg)
            
            onlineStreamingDisposables[.ppg] = api.startPpgStreaming(deviceId, settings: settings)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.ppg] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if(data.type == PpgDataType.ppg3_ambient1) {
                            if let fileHandle = logFile?.fileHandle {
                                self.writeOnlineStreamLogFile(fileHandle, data)
                            }
                            
                            for item in data.samples {
                                NSLog("PPG  ppg0: \(item.channelSamples[0]) ppg1: \(item.channelSamples[1]) ppg2: \(item.channelSamples[2]) ambient: \(item.channelSamples[3]) timeStamp: \(item.timeStamp)")
                            }
                        }
                    case .error(let err):
                        NSLog("PPG stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("PPG stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func ppiStreamStart() {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.ppi] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .ppi)
            
            onlineStreamingDisposables[.ppi] = api.startPpiStreaming(deviceId)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.ppi] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if let fileHandle = logFile?.fileHandle {
                            self.writeOnlineStreamLogFile(fileHandle, data)
                        }
                        
                        for item in data.samples {
                            NSLog("PPI    PeakToPeak(ms): \(item.ppInMs) sample.blockerBit: \(item.blockerBit)  errorEstimate: \(item.ppErrorEstimate)")
                        }
                    case .error(let err):
                        NSLog("PPI stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("PPI stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func hrStreamStart() {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.hr] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .hr)
            
            onlineStreamingDisposables[.hr] = api.startHrStreaming(deviceId)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.hr] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if let fileHandle = logFile?.fileHandle {
                            self.writeOnlineStreamLogFile(fileHandle, data)
                        }
                        
                        NSLog("HR    BPM: \(data[0].hr) rrs: \(data[0].rrsMs) rrAvailable: \(data[0].rrAvailable) contact status: \(data[0].contactStatus) contact supported: \(data[0].contactStatusSupported)")
                    case .error(let err):
                        NSLog("Hr stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("Hr stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func temperatureStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.temperature] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .temperature)
            
            onlineStreamingDisposables[.temperature] = api.startTemperatureStreaming(deviceId, settings: settings)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.temperature] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if let fileHandle = logFile?.fileHandle {
                            self.writeOnlineStreamLogFile(fileHandle, data)
                        }
                        
                        for item in data.samples {
                            NSLog("Temperature    °C: \(item.temperature) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("Temperature stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("Temperature stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func pressureStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.pressure] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .pressure)
            
            onlineStreamingDisposables[.pressure] = api.startPressureStreaming(deviceId, settings: settings)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.pressure] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if let fileHandle = logFile?.fileHandle {
                            self.writeOnlineStreamLogFile(fileHandle, data)
                        }
                        
                        for item in data.samples {
                            NSLog("Pressure    hPa: \(item.pressure) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("Pressure stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("Pressure stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }

    
    func sdkModeToggle() {
        if case .connected(let deviceId) = deviceConnectionState {
            if self.sdkModeFeature.isEnabled {
                api.disableSDKMode(deviceId)
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            NSLog("SDK mode disabled")
                            Task { @MainActor in
                                self.sdkModeFeature.isEnabled = false
                            }
                        case .error(let err):
                            self.somethingFailed(text: "SDK mode disable failed: \(err)")
                        }
                    }.disposed(by: disposeBag)
            } else {
                api.enableSDKMode(deviceId)
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            NSLog("SDK mode enabled")
                            Task { @MainActor in
                                self.sdkModeFeature.isEnabled = true
                            }
                        case .error(let err):
                            self.somethingFailed(text: "SDK mode enable failed: \(err)")
                        }
                    }.disposed(by: disposeBag)
            }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
            Task { @MainActor in
                self.sdkModeFeature.isEnabled = false
            }
        }
    }
    
    func getSdkModeStatus() async {
        if case .connected(let deviceId) = deviceConnectionState, self.sdkModeFeature.isSupported == true  {
            do {
                NSLog("get SDK mode status")
                let isSdkModeEnabled: Bool = try await api.isSDKModeEnabled(deviceId).value
                NSLog("SDK mode currently enabled: \(isSdkModeEnabled)")
                Task { @MainActor in
                    self.sdkModeFeature.isEnabled = isSdkModeEnabled
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "SDK mode status request failed: \(err)")
                }
            }
        }
    }
    
    func listH10Exercises() {
        if case .connected(let deviceId) = deviceConnectionState {
            h10ExerciseEntry = nil
            api.fetchStoredExerciseList(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("list exercises completed")
                    case .error(let err):
                        NSLog("failed to list exercises: \(err)")
                    case .next(let polarExerciseEntry):
                        NSLog("entry: \(polarExerciseEntry.date.description) path: \(polarExerciseEntry.path) id: \(polarExerciseEntry.entryId)");
                        self.h10ExerciseEntry = polarExerciseEntry
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func h10ReadExercise() async {
        if case .connected(let deviceId) = deviceConnectionState {
            guard let e = h10ExerciseEntry else {
                somethingFailed(text: "No exercise to read, please list the exercises first")
                return
            }
            
            do {
                Task { @MainActor in
                    self.h10RecordingFeature.isFetchingRecording = true
                }
                
                let data:PolarExerciseData = try await api.fetchExercise(deviceId, entry: e).value
                NSLog("exercise data count: \(data.samples.count) samples: \(data.samples)")
                Task { @MainActor in
                    self.h10RecordingFeature.isFetchingRecording = false
                }
                
            } catch let err {
                Task { @MainActor in
                    self.h10RecordingFeature.isFetchingRecording = false
                    self.somethingFailed(text: "read H10 exercise failed: \(err)")
                }
            }
        }
    }
    
    func h10RemoveExercise() {
        if case .connected(let deviceId) = deviceConnectionState {
            guard let entry = h10ExerciseEntry else {
                somethingFailed(text: "No exercise to read, please list the exercises first")
                return
            }
            api.removeExercise(deviceId, entry: entry)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        self.h10ExerciseEntry = nil
                        NSLog("remove completed")
                    case .error(let err):
                        NSLog("failed to remove exercise: \(err)")
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func h10RecordingToggle() {
        if case .connected(let deviceId) = deviceConnectionState {
            if self.h10RecordingFeature.isEnabled {
                api.stopRecording(deviceId)
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            NSLog("recording stopped")
                            Task { @MainActor in
                                self.h10RecordingFeature.isEnabled = false
                            }
                        case .error(let err):
                            self.somethingFailed(text: "recording stop fail: \(err)")
                        }
                    }.disposed(by: disposeBag)
            } else {
                api.startRecording(deviceId, exerciseId: "TEST_APP_ID", interval: .interval_1s, sampleType: .rr)
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            NSLog("recording started")
                            Task { @MainActor in
                                self.h10RecordingFeature.isEnabled = true
                            }
                        case .error(let err):
                            self.somethingFailed(text: "recording start fail: \(err)")
                        }
                    }.disposed(by: disposeBag)
            }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
            Task { @MainActor in
                self.h10RecordingFeature.isEnabled = false
            }
        }
    }
    
    func getH10RecordingStatus() {
        if case .connected(let deviceId) = deviceConnectionState, self.h10RecordingFeature.isSupported {
            api.requestRecordingStatus(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .failure(let err):
                        self.somethingFailed(text: "H10 recording status request failed: \(err)")
                    case .success(let pair):
                        var recordingStatus = "Recording on: \(pair.ongoing)."
                        if pair.ongoing {
                            recordingStatus.append(" Recording started with id: \(pair.entryId)")
                            Task { @MainActor in
                                self.h10RecordingFeature.isEnabled = true
                            }
                        } else {
                            Task { @MainActor in
                                self.h10RecordingFeature.isEnabled = false
                            }
                        }
                        NSLog(recordingStatus)
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func setTime() async {
        if case .connected(let deviceId) = deviceConnectionState {
            do {
                let time = Date()
                let timeZone = TimeZone.current
                
                let _: Void = try await api.setLocalTime(deviceId, time: time, zone: timeZone).value
                Task { @MainActor in
                    let formatter = DateFormatter()
                    formatter.dateStyle = .short
                    formatter.timeStyle = .medium
                    self.generalMessage = Message(text: "\(formatter.string(from: time)) set to device \(deviceId)")
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "time set failed: \(err)")
                }
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "time set failed. No device connected)")
            }
        }
    }
    
    func getTime() async {
        if case .connected(let deviceId) = deviceConnectionState {
            do {
                let date: Date = try await api.getLocalTime(deviceId).value
                Task { @MainActor in
                    let formatter = DateFormatter()
                    formatter.dateStyle = .short
                    formatter.timeStyle = .medium
                    self.generalMessage = Message(text: "\(formatter.string(from: date)) read from the device \(deviceId)")
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "time get failed: \(err)")
                }
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "time get failed. No device connected)")
            }
        }
    }
    
    func getDiskSpace() async {
        if case .connected(let deviceId) = deviceConnectionState {
            do {
                let diskSpace: PolarDiskSpaceData = try await api.getDiskSpace(deviceId).value
                Task { @MainActor in
                    self.generalMessage = Message(text: String(describing: diskSpace))
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "disk space get failed: \(err)")
                }
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "disk space get failed. No device connected)")
            }
        }
    }
    
    func setLedConfig(ledConfig: LedConfig) async {
        if case .connected(let deviceId) = deviceConnectionState {
            do {
                let _: Void = try await api.setLedConfig(deviceId, ledConfig: ledConfig).value
                Task { @MainActor in
                    self.generalMessage = Message(text: "setLedConfig() set to: \(ledConfig)")
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "setLedConfig() error: \(err)")
                }

            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "setLedConfig() failed. No device connected)")
            }
        }
    }

    func doFactoryReset(preservePairingInformation: Bool) async {
        if case .connected(let deviceId) = deviceConnectionState {
            do {
                let _: Void = try await api.doFactoryReset(deviceId, preservePairingInformation: preservePairingInformation).value
                Task { @MainActor in
                    self.generalMessage = Message(text: "Send factory reset notification to device: \(deviceId)")
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "doFactoryReset() error: \(err)")
                }

            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "doFactoryReset() failed. No device connected)")
            }
        }
    }

    
    private func somethingFailed(text: String) {
        self.generalMessage = Message(text: "Error: \(text)")
        NSLog("Error \(text)")
    }
    
    private func dataHeaderString(_ type: PolarDeviceDataType) -> String {
        var result = ""
        switch type {
        case .ecg:
            result = "TIMESTAMP ECG(microV)\n"
        case .acc:
            result = "TIMESTAMP X(mg) Y(mg) Z(mg)\n"
        case .ppg:
            result =  "TIMESTAMP PPG0 PPG1 PPG2 AMBIENT\n"
        case .ppi:
            result = "PPI(ms) HR ERROR_ESTIMATE BLOCKER_BIT SKIN_CONTACT_SUPPORT SKIN_CONTACT_STATUS\n"
        case .gyro:
            result =  "TIMESTAMP X(deg/sec) Y(deg/sec) Z(deg/sec)\n"
        case .magnetometer:
            result =  "TIMESTAMP X(Gauss) Y(Gauss) Z(Gauss)\n"
        case .hr:
            result = "HR CONTACT_SUPPORTED CONTACT_STATUS RR_AVAILABLE RR(ms)\n"
        case .temperature:
            result = "TIMESTAMP TEMPERATURE(Celcius)\n"
        case .pressure:
            result = "TIMESTAMP PRESSURE(mBar)\n"
        }
        return result
    }
    
    private func dataToString<T>(_ data: T) -> String {
        var result = ""
        switch data {
        case let polarAccData as PolarAccData:
            result += polarAccData.samples.map{ "\($0.timeStamp) \($0.x) \($0.y) \($0.z)" }.joined(separator: "\n")
        case let polarEcgData as PolarEcgData:
            result +=  polarEcgData.samples.map{ "\($0.timeStamp) \($0.voltage)" }.joined(separator: "\n")
        case let polarGyroData as PolarGyroData:
            result +=  polarGyroData.samples.map{ "\($0.timeStamp) \($0.x) \($0.y) \($0.z)" }.joined(separator: "\n")
        case let polarMagnetometerData as PolarMagnetometerData:
            result +=  polarMagnetometerData.samples.map{ "\($0.timeStamp) \($0.x) \($0.y) \($0.z)" }.joined(separator: "\n")
        case let polarPpgData as PolarPpgData:
            if polarPpgData.type == PpgDataType.ppg3_ambient1 {
                result += polarPpgData.samples.map{ "\($0.timeStamp) \($0.channelSamples[0]) \($0.channelSamples[1]) \($0.channelSamples[2]) \($0.channelSamples[3])" }.joined(separator: "\n")
            }
        case let polarPpiData as PolarPpiData:
            result += polarPpiData.samples.map{ "\($0.ppInMs) \($0.hr) \($0.ppErrorEstimate) \($0.blockerBit) \($0.skinContactSupported) \($0.skinContactStatus)" }.joined(separator: "\n")
            
        case let polarHrData as PolarHrData:
            result += polarHrData.map{ "\($0.hr) \($0.contactStatusSupported) \($0.contactStatus) \($0.rrAvailable) \($0.rrsMs.map { String($0) }.joined(separator: " "))" }.joined(separator: "\n")
        
        case let polarTemperatureData as PolarTemperatureData:
            result +=  polarTemperatureData.samples.map{ "\($0.timeStamp) \($0.temperature)" }.joined(separator: "\n")

        case let polarPressureData as PolarPressureData:
            result +=  polarPressureData.samples.map{ "\($0.timeStamp) \($0.pressure)" }.joined(separator: "\n")
            
        default:
            result = "Data type not supported"
        }
        return result + "\n"
    }
    
    private func openOnlineStreamLogFile(type: PolarDeviceDataType) -> (URL, FileHandle)? {
        let firstRow = dataHeaderString(type).data(using: .utf8)!
        let fileManager = FileManager.default
        do {
            let documentsDirectory = try fileManager.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false)
            
            let dateFormatter = DateFormatter()
            dateFormatter.dateFormat = "yyyyMMdd_HHmmss"
            
            let currentDate = Date()
            let dateString = dateFormatter.string(from: currentDate)
            
            let fileName = type.stringValue + "_" + dateString + ".txt"
            let fileURL = documentsDirectory.appendingPathComponent(fileName)
            
            do {
                try firstRow.write(to: fileURL)
                let fileHandle = try FileHandle(forWritingTo: fileURL)
                return (fileURL, fileHandle)
            } catch let err {
                NSLog("Failed create log file for data \(type). Reason \(err)")
                return nil
            }
        } catch let err {
            NSLog("Failed to get documents directory while trying to create log file for data \(type). Reason \(err)")
            return nil
        }
    }
    
    private func writeOnlineStreamLogFile<T>(_ fileHandle: FileHandle, _ data: T) {
        let dataRow = dataToString(data).data(using: .utf8)!
        fileHandle.seekToEndOfFile()
        fileHandle.write(dataRow)
    }
    
    private func writeErrorOnlineStreamLogFile(_ fileHandle: FileHandle, _ err: Error) {
        let errorString = "ERROR. Online stream closed because of error: \(err)"
        let dataRow = errorString.data(using: .utf8)!
        fileHandle.seekToEndOfFile()
        fileHandle.write(dataRow)
    }
    
    private func closeOnlineStreamLogFile(_ fileHandle: FileHandle) {
        fileHandle.closeFile()
    }
    
    func onlineStreamLogFileShared(at url: URL) {
        let fileManager = FileManager.default
        do {
            try fileManager.removeItem(at: url)
            resetStreamingURL(for: url, in: &self.onlineStreamingFeature)
            NSLog("Online stream file deleted at: \(url)")
        } catch {
            NSLog("Error Online stream file delete: \(error)")
        }
    }
    
    private func resetStreamingURL(for url: URL, in feature: inout OnlineStreamingFeature) {
        for (dataType, streamingState) in feature.isStreaming {
            if case let .success(currentURL) = streamingState, currentURL == url {
                feature.isStreaming[dataType] = .success(url: nil)
                break
            }
        }
    }
}

fileprivate extension PolarDeviceDataType {
    var stringValue: String {
        switch self {
        case .ecg:
            return "ECG"
        case .acc:
            return "ACC"
        case .ppg:
            return "PPG"
        case .ppi:
            return "PPI"
        case .gyro:
            return "GYR"
        case .magnetometer:
            return "MAG"
        case .hr:
            return "HR"
        case .temperature:
          return "TEMP"
        case .pressure:
          return "PRE"
        }
    }
}


// MARK: - PolarBleApiPowerStateObserver
extension PolarBleSdkManager : PolarBleApiPowerStateObserver {
    func blePowerOn() {
        NSLog("BLE ON")
        Task { @MainActor in
            isBluetoothOn = true
        }
    }
    
    func blePowerOff() {
        NSLog("BLE OFF")
        Task { @MainActor in
            isBluetoothOn = false
        }
    }
}

// MARK: - PolarBleApiObserver
extension PolarBleSdkManager : PolarBleApiObserver {
    func deviceConnecting(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DEVICE CONNECTING: \(polarDeviceInfo)")
        Task { @MainActor in
            self.deviceConnectionState = DeviceConnectionState.connecting(polarDeviceInfo.deviceId)
        }
    }
    
    func deviceConnected(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DEVICE CONNECTED: \(polarDeviceInfo)")
        Task { @MainActor in
            self.deviceConnectionState = DeviceConnectionState.connected(polarDeviceInfo.deviceId)
        }
    }
    
    func deviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo, pairingError: Bool) {
        NSLog("DISCONNECTED: \(polarDeviceInfo)")
        Task { @MainActor in
            self.deviceConnectionState = DeviceConnectionState.disconnected(polarDeviceInfo.deviceId)
            self.offlineRecordingFeature = OfflineRecordingFeature()
            self.onlineStreamingFeature = OnlineStreamingFeature()
            self.deviceTimeSetupFeature = DeviceTimeSetupFeature()
            self.sdkModeFeature = SdkModeFeature()
            self.h10RecordingFeature = H10RecordingFeature()
            self.deviceInfoFeature = DeviceInfoFeature()
            self.batteryStatusFeature = BatteryStatusFeature()
            self.ledAnimationFeature = LedAnimationFeature()
        }
    }
}

// MARK: - PolarBleApiDeviceInfoObserver
extension PolarBleSdkManager : PolarBleApiDeviceInfoObserver {
  func disInformationReceivedWithKeysAsStrings(_ identifier: String, key: String, value: String) {
    // Not implemented
  }
  
    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        NSLog("battery level updated: \(batteryLevel)")
        Task { @MainActor in
            self.batteryStatusFeature.batteryLevel = batteryLevel
        }
    }
    
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        NSLog("dis info: \(uuid.uuidString) value: \(value)")
        if(uuid == BleDisClient.SOFTWARE_REVISION_STRING) {
            Task { @MainActor in
                self.deviceInfoFeature.firmwareVersion = value
            }
        }
    }
}

// MARK: - PolarBleApiDeviceFeaturesObserver
extension PolarBleSdkManager : PolarBleApiDeviceFeaturesObserver {
    func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdk.PolarBleSdkFeature) {
        NSLog("Feature is ready: \(feature)")
        switch(feature) {
            
        case .feature_hr:
            //nop
            break
            
        case .feature_battery_info:
            Task { @MainActor in
                self.batteryStatusFeature.isSupported = true
            }
            break
            
        case .feature_device_info:
            Task { @MainActor in
                self.deviceInfoFeature.isSupported = true
            }
            break
            
        case .feature_polar_h10_exercise_recording:
            Task { @MainActor in
                self.h10RecordingFeature.isSupported = true
            }
            break
            
        case .feature_polar_device_time_setup:
            Task { @MainActor in
                self.deviceTimeSetupFeature.isSupported = true
                
                let dateFormatter = ISO8601DateFormatter()
                dateFormatter.formatOptions = [.withInternetDateTime]
                dateFormatter.timeZone = TimeZone(secondsFromGMT: 0)
                
                let ftuConfig = PolarFirstTimeUseConfig(
                    gender: PolarFirstTimeUseConfig.Gender.female,
                    birthDate: Date(),
                    height: 180,
                    weight: 80,
                    maxHeartRate: 180,
                    vo2Max: 80,
                    restingHeartRate: 100,
                    trainingBackground: PolarFirstTimeUseConfig.TrainingBackground.frequent,
                    deviceTime: dateFormatter.string(from: Date()),
                    typicalDay: PolarFirstTimeUseConfig.TypicalDay.mostlyMoving,
                    sleepGoalMinutes: 480
                )
                
                /// TODO: calling doFirstTimeUse deserves a better place in the
                /// application flow
                api
                    .doFirstTimeUse(identifier, ftuConfig: ftuConfig)
                    .subscribe(
                        onCompleted: {
                            NSLog("FTU Completed")
                        },
                        onError: { err in
                            NSLog("FTU Error: \(err)")
                        },
                        onDisposed: {
                            NSLog("FTU Disposed")
                        }
                    ).disposed(by: disposeBag)
            }
            
            Task {
                getH10RecordingStatus()
            }
            
            break
            
        case  .feature_polar_sdk_mode:
            Task { @MainActor in
                self.sdkModeFeature.isSupported = true
            }
            Task {
                await getSdkModeStatus()
            }
            break
            
        case .feature_polar_online_streaming:
            Task { @MainActor in
                self.onlineStreamingFeature.isSupported = true
            }
            
            api.getAvailableOnlineStreamDataTypes(identifier)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .success(let availableOnlineDataTypes):
                        for dataType in availableOnlineDataTypes {
                            self.onlineStreamingFeature.availableOnlineDataTypes[dataType] = true
                        }
                    case .failure(let err):
                        self.somethingFailed(text: "Failed to get available online streaming data types: \(err)")
                    }
                }.disposed(by: disposeBag)
            break
            
        case .feature_polar_offline_recording:
            Task { @MainActor in
                self.offlineRecordingFeature.isSupported = true
            }
            Task {
                await getOfflineRecordingStatus()
            }
            break
    
        case .feature_polar_led_animation:
            Task { @MainActor in
                self.ledAnimationFeature.isSupported = true
            }
            Task {
                await getSdkModeStatus()
            }
            break
        case .feature_polar_firmware_update:
          break
        case .feature_polar_activity_data:
          break
        }
    }
    
    // deprecated
    func hrFeatureReady(_ identifier: String) {
        NSLog("HR ready")
    }
    
    // deprecated
    func ftpFeatureReady(_ identifier: String) {
        NSLog("FTP ready")
    }
    
    // deprecated
    func streamingFeaturesReady(_ identifier: String, streamingFeatures: Set<PolarDeviceDataType>) {
        for feature in streamingFeatures {
            NSLog("Feature \(feature) is ready.")
        }
    }
}

// MARK: - PolarBleApiLogger
extension PolarBleSdkManager : PolarBleApiLogger {
    func message(_ str: String) {
        NSLog("Polar SDK log:  \(str)")
    }
}
