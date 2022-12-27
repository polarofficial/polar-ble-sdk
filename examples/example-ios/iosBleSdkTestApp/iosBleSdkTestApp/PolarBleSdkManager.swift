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
    private var deviceId = "B5232722"
    
    @Published var isBluetoothOn: Bool
    @Published var isBroadcastListenOn: Bool = false
    @Published var isSearchOn: Bool = false
    
    @Published var deviceConnectionState: DeviceConnectionState = DeviceConnectionState.disconnected
    
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
    
    @Published var generalMessage: Message? = nil
    
    private var broadcastDisposable: Disposable?
    private var autoConnectDisposable: Disposable?
    private var searchDisposable: Disposable?
    private var onlineStreamingDisposables: [PolarDeviceDataType: Disposable?] = [:]
    
    private let disposeBag = DisposeBag()
    private var h10ExerciseEntry: PolarExerciseEntry?
    
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
    
    func connectToDevice() {
        do {
            try api.connectToDevice(deviceId)
        } catch let err {
            NSLog("Failed to connect to \(deviceId). Reason \(err)")
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
    
    func searchToggle() {
        if !isSearchOn {
            isSearchOn = true
            searchDisposable = api.searchForDevice()
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("search complete")
                        self.isSearchOn = false
                    case .error(let err):
                        NSLog("search error: \(err)")
                        self.isSearchOn = false
                    case .next(let item):
                        NSLog("polar device found: \(item.name) connectable: \(item.connectable) address: \(item.address.uuidString)")
                    }
                }
        } else {
            isSearchOn = false
            searchDisposable?.dispose()
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
    
    func getOfflineRecording(offlineRecordingEntry: PolarOfflineRecordingEntry) async {
        if case .connected(let deviceId) = deviceConnectionState {
            Task { @MainActor in
                self.offlineRecordingData.isFetching = true
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
                        self.offlineRecordingData.data = dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .gyroOfflineRecordingData(let data, startTime: let startTime, settings: let settings):
                    NSLog("GYR data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = settings
                        self.offlineRecordingData.data = dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .magOfflineRecordingData(let data, startTime: let startTime, settings: let settings):
                    NSLog("MAG data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = settings
                        self.offlineRecordingData.data = dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .ppgOfflineRecordingData(let data, startTime: let startTime, settings: let settings):
                    NSLog("PPG data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = settings
                        self.offlineRecordingData.data = dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .ppiOfflineRecordingData(let data, startTime: let startTime):
                    NSLog("PPI data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = nil
                        self.offlineRecordingData.data = dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .hrOfflineRecordingData(let data, startTime: let startTime):
                    NSLog("HR data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = nil
                        self.offlineRecordingData.data = dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                }
            } catch let err {
                NSLog("offline recording read failed: \(err)")
            }
            
            Task { @MainActor in
                self.offlineRecordingData.isFetching = false
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
            
            api.startOfflineRecording(deviceId, feature: feature, settings: PolarSensorSetting(polarSensorSettings))
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
        return self.onlineStreamingFeature.isStreaming[feature] ?? false
    }
    
    func ecgStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.ecg] = true
            }
            
            onlineStreamingDisposables[.ecg] = api.startEcgStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
                .do(onDispose: { self.onlineStreamingFeature.isStreaming[.ecg] = false})
                .subscribe { e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("ECG    µV: \(item.voltage) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("ECG stream failed: \(err)")
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
                self.onlineStreamingFeature.isStreaming[.acc] = true
            }
            
            NSLog("ACC stream start: \(deviceId)")
            onlineStreamingDisposables[.acc] = api.startAccStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
                .do(onDispose: { self.onlineStreamingFeature.isStreaming[.acc] = false})
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("ACC    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("ACC stream failed: \(err)")
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
                self.onlineStreamingFeature.isStreaming[.magnetometer] = true
            }
            
            onlineStreamingDisposables[.magnetometer] = api.startMagnetometerStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
                .do(onDispose: { self.onlineStreamingFeature.isStreaming[.magnetometer] = false })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("MAG    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("MAG stream failed: \(err)")
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
                self.onlineStreamingFeature.isStreaming[.gyro] = true
            }
            
            onlineStreamingDisposables[.gyro] = api.startGyroStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
                .do(onDispose: { self.onlineStreamingFeature.isStreaming[.gyro] = false })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("GYR    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("GYR stream failed: \(err)")
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
                self.onlineStreamingFeature.isStreaming[.ppg] = true
            }
            
            onlineStreamingDisposables[.ppg] = api.startPpgStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
                .do(onDispose: { self.onlineStreamingFeature.isStreaming[.ppg] = false })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if(data.type == OhrDataType.ppg3_ambient1) {
                            for item in data.samples {
                                NSLog("PPG  ppg0: \(item.channelSamples[0]) ppg1: \(item.channelSamples[1]) ppg2: \(item.channelSamples[2]) ambient: \(item.channelSamples[3]) timeStamp: \(item.timeStamp)")
                            }
                        }
                    case .error(let err):
                        NSLog("PPG stream failed: \(err)")
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
                self.onlineStreamingFeature.isStreaming[.ppi] = true
            }
            
            onlineStreamingDisposables[.ppi] = api.startOhrPPIStreaming(deviceId)
                .observe(on: MainScheduler.instance)
                .do(onDispose: { self.onlineStreamingFeature.isStreaming[.ppi] = false })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("PPI    PeakToPeak(ms): \(item.ppInMs) sample.blockerBit: \(item.blockerBit)  errorEstimate: \(item.ppErrorEstimate)")
                        }
                    case .error(let err):
                        NSLog("PPI stream failed: \(err)")
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
                self.onlineStreamingFeature.isStreaming[.hr] = true
            }
            
            onlineStreamingDisposables[.hr] = api.startHrStreaming(deviceId)
                .observe(on: MainScheduler.instance)
                .do(onDispose: { self.onlineStreamingFeature.isStreaming[.hr] = false })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        NSLog("HR    BPM: \(data.hr) rrsMs: \(data.rrsMs) rrs: \(data.rrs) contact: \(data.contact) contact supported: \(data.contactSupported)")
                    case .error(let err):
                        NSLog("Hr stream failed: \(err)")
                    case .completed:
                        NSLog("Hr stream completed")
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
    
    private func somethingFailed(text: String) {
        self.generalMessage = Message(text: "Error: \(text)")
        NSLog("Error \(text)")
    }
    
    private func dataToString<T>(_ data: T) -> String {
        var result = ""
        switch data {
        case let polarAccData as PolarAccData:
            result += "TIMESTAMP X(mg) Y(mg) Z(mg)\n"
            result += polarAccData.samples.map{ "\($0.timeStamp) \($0.x) \($0.y) \($0.z)" }.joined(separator: "\n")
        case let polarEcgData as PolarEcgData:
            result += "TIMESTAMP ECG(microV)\n"
            result +=  polarEcgData.samples.map{ "\($0.timeStamp) \($0.voltage)" }.joined(separator: "\n")
        case let polarGyroData as PolarGyroData:
            result +=  "TIMESTAMP X(deg/sec) Y(deg/sec) Z(deg/sec)\n"
            result +=  polarGyroData.samples.map{ "\($0.timeStamp) \($0.x) \($0.y) \($0.z)" }.joined(separator: "\n")
        case let polarMagnetometerData as PolarMagnetometerData:
            result +=  "TIMESTAMP X(Gauss) Y(Gauss) Z(Gauss)\n"
            result +=  polarMagnetometerData.samples.map{ "\($0.timeStamp) \($0.x) \($0.y) \($0.z)" }.joined(separator: "\n")
        case let polarPpgData as PolarOhrData:
            if polarPpgData.type == OhrDataType.ppg3_ambient1 {
                result +=  "TIMESTAMP PPG0 PPG1 PPG2 AMBIENT\n"
                result += polarPpgData.samples.map{ "\($0.timeStamp) \($0.channelSamples[0]) \($0.channelSamples[1]) \($0.channelSamples[2]) \($0.channelSamples[3])" }.joined(separator: "\n")
                
            }
        case let polarPpiData as PolarPpiData:
            result += "PPI(ms) HR ERROR_ESTIMATE BLOCKER_BIT SKIN_CONTACT_SUPPORT SKIN_CONTACT_STATUS\n"
            result += polarPpiData.samples.map{ "\($0.ppInMs) \($0.hr) \($0.ppErrorEstimate) \($0.blockerBit) \($0.skinContactSupported) \($0.skinContactStatus)" }.joined(separator: "\n")
            
        case let polarHrData as [PolarHrData]:
            result += "HR CONTACT_SUPPORTED CONTACT_STATUS RR(ms)\n"
            result += polarHrData.map{ "\($0.hr) \($0.contactSupported) \($0.contact) \($0.rrsMs.map { String($0) }.joined(separator: " "))" }.joined(separator: "\n")

        default:
            result = "Data type not supported"
        }
        return result
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
    
    func deviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DISCONNECTED: \(polarDeviceInfo)")
        Task { @MainActor in
            self.deviceConnectionState = DeviceConnectionState.disconnected
            self.offlineRecordingFeature = OfflineRecordingFeature()
            self.onlineStreamingFeature = OnlineStreamingFeature()
            self.deviceTimeSetupFeature = DeviceTimeSetupFeature()
            self.sdkModeFeature = SdkModeFeature()
            self.h10RecordingFeature = H10RecordingFeature()
            self.deviceInfoFeature = DeviceInfoFeature()
            self.batteryStatusFeature = BatteryStatusFeature()
        }
    }
}

// MARK: - PolarBleApiDeviceInfoObserver
extension PolarBleSdkManager : PolarBleApiDeviceInfoObserver {
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
