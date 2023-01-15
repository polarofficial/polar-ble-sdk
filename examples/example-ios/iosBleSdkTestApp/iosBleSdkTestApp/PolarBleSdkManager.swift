/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import RxSwift
import CoreBluetooth

/// PolarBleSdkManager demonstrates how to user PolarBleSDK API
class PolarBleSdkManager : ObservableObject {
    
    // NOTICE this example utilises all available features
    private var api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: Features.allFeatures.rawValue)
    
    // TODO replace the device id with your device ID or use the auto connect to when connecting to device
    private var deviceId = "8C4CAD2D"
    
    @Published private(set) var isBluetoothOn: Bool
    @Published private(set) var isBroadcastListenOn: Bool = false
    @Published private(set) var isSearchOn: Bool = false
    
    @Published private(set) var deviceConnectionState: ConnectionState = ConnectionState.disconnected {
        didSet {
            switch deviceConnectionState {
            case .disconnected: isDeviceConnected = false
            case .connecting(_): isDeviceConnected = false
            case .connected(_): isDeviceConnected = true
            }
        }
    }
    
    @Published private(set) var isDeviceConnected: Bool = false
    @Published private var isEcgStreamOn: Bool = false
    @Published private var isAccStreamOn: Bool = false
    @Published private var isGyrStreamOn: Bool = false
    @Published private var isMagStreamOn: Bool = false
    @Published private var isPpgSreamOn: Bool = false
    @Published private var isPpiStreamOn: Bool = false
    @Published private(set) var supportedStreamFeatures: Set<DeviceStreamingFeature> = Set<DeviceStreamingFeature>()
    @Published private(set) var isSdkStreamModeEnabled: Bool = false
    @Published private(set) var isSdkFeatureSupported: Bool = false
    @Published private(set) var isFtpFeatureSupported: Bool = false
    @Published private(set) var isH10RecordingSupported: Bool = false
    @Published private(set) var isH10RecordingEnabled: Bool = false
    @Published private(set) var isExerciseFetchInProgress: Bool = false
    @Published var streamSettings: StreamSettings? = nil
    @Published var generalMessage: Message? = nil
    
    private var broadcastDisposable: Disposable?
    private var autoConnectDisposable: Disposable?
    private var searchDisposable: Disposable?
    private var ecgDisposable: Disposable?
    private var accDisposable: Disposable?
    private var gyroDisposable: Disposable?
    private var magDisposable: Disposable?
    private var ppgDisposable: Disposable?
    private var ppiDisposable: Disposable?
    private let disposeBag = DisposeBag()
    private var exerciseEntry: PolarExerciseEntry?
    
    init() {
        self.isBluetoothOn = api.isBlePowered
        
        api.polarFilter(true)
        api.observer = self
        api.deviceFeaturesObserver = self
        api.powerStateObserver = self
        api.deviceInfoObserver = self
        api.sdkModeFeatureObserver = self
        api.deviceHrObserver = self
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
    
    func getStreamSettings(feature: PolarBleSdk.DeviceStreamingFeature) {
        if case .connected(let deviceId) = deviceConnectionState {
            NSLog("Stream settings fetch for \(feature)")
            api.requestStreamSettings(deviceId, feature: feature)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .success(let settings):
                        NSLog("Stream settings fetch completed for \(feature)")
                        
                        var receivedSettings:[StreamSetting] = []
                        for setting in settings.settings {
                            var values:[Int] = []
                            for settingsValue in setting.value {
                                values.append(Int(settingsValue))
                            }
                            receivedSettings.append(StreamSetting(type: setting.key, values: values))
                        }
                        
                        self.streamSettings = StreamSettings(feature: feature, settings: receivedSettings)
                        
                    case .failure(let err):
                        self.somethingFailed(text: "Stream settings request failed: \(err)")
                        self.streamSettings = nil
                    }
                }.disposed(by: disposeBag)
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func streamStart(settings: StreamSettings) {
        var logString:String = "Stream \(settings.feature) start with settings: "
        
        var polarSensorSettings:[PolarSensorSetting.SettingType : UInt32] = [:]
        for setting in settings.settings {
            polarSensorSettings[setting.type] = UInt32(setting.values[0])
            logString.append(" \(setting.type) \(setting.values[0])")
        }
        NSLog(logString)
        
        switch settings.feature {
        case .ecg:
            ecgStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .acc:
            accStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .magnetometer:
            magStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .ppg:
            ppgStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        case .ppi:
            ppiStreamStart()
        case .gyro:
            gyrStreamStart(settings: PolarSensorSetting(polarSensorSettings))
        }
    }
    
    func streamStop(feature: PolarBleSdk.DeviceStreamingFeature) {
        switch feature {
        case .ecg:
            ecgStreamStop()
        case .acc:
            accStreamStop()
        case .magnetometer:
            magStreamStop()
        case .ppg:
            ppgStreamStop()
        case .ppi:
            ppiStreamStop()
        case .gyro:
            gyrStreamStop()
        }
    }
    
    func isStreamOn(feature: PolarBleSdk.DeviceStreamingFeature) -> Bool {
        switch feature {
        case .ecg:
            return isEcgStreamOn
        case .acc:
            return isAccStreamOn
        case .magnetometer:
            return isMagStreamOn
        case .ppg:
            return isPpgSreamOn
        case .ppi:
            return isPpiStreamOn
        case .gyro:
            return isGyrStreamOn
        }
    }
    
    func ecgStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            isEcgStreamOn = true
            ecgDisposable = api.startEcgStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("ECG    µV: \(item.voltage) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("ECG stream failed: \(err)")
                        self.isEcgStreamOn = false
                    case .completed:
                        NSLog("ECG stream completed")
                        self.isEcgStreamOn = false
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func ecgStreamStop() {
        isEcgStreamOn = false
        ecgDisposable?.dispose()
    }
    
    func accStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            isAccStreamOn = true
            NSLog("ACC stream start: \(deviceId)")
            accDisposable = api.startAccStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("ACC    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("ACC stream failed: \(err)")
                        self.isAccStreamOn = false
                    case .completed:
                        NSLog("ACC stream completed")
                        self.isAccStreamOn = false
                        break
                    }
                }
        } else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
        }
    }
    
    func accStreamStop() {
        isAccStreamOn = false
        accDisposable?.dispose()
    }
    
    func magStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            isMagStreamOn = true
            magDisposable = api.startMagnetometerStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("MAG    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("MAG stream failed: \(err)")
                        self.isMagStreamOn = false
                    case .completed:
                        NSLog("MAG stream completed")
                        self.isMagStreamOn = false
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func magStreamStop() {
        isMagStreamOn = false
        magDisposable?.dispose()
    }
    
    func gyrStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            isGyrStreamOn = true
            gyroDisposable = api.startGyroStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("GYR    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                    case .error(let err):
                        NSLog("GYR stream failed: \(err)")
                        self.isGyrStreamOn = false
                    case .completed:
                        NSLog("GYR stream completed")
                        self.isGyrStreamOn = false
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func gyrStreamStop() {
        isGyrStreamOn = false
        gyroDisposable?.dispose()
    }
    
    func ppgStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let deviceId) = deviceConnectionState {
            isPpgSreamOn = true
            ppgDisposable = api.startOhrStreaming(deviceId, settings: settings)
                .observe(on: MainScheduler.instance)
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
                        self.isPpgSreamOn = false
                    case .completed:
                        NSLog("PPG stream completed")
                        self.isPpgSreamOn = false
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func ppgStreamStop() {
        isPpgSreamOn = false
        ppgDisposable?.dispose()
    }
    
    func ppiStreamStart() {
        if case .connected(let deviceId) = deviceConnectionState {
            isPpiStreamOn = true
            ppiDisposable = api.startOhrPPIStreaming(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("PPI    PeakToPeak(ms): \(item.ppInMs) sample.blockerBit: \(item.blockerBit)  errorEstimate: \(item.ppErrorEstimate)")
                        }
                    case .error(let err):
                        NSLog("PPI stream failed: \(err)")
                        self.isPpiStreamOn = false
                    case .completed:
                        NSLog("PPI stream completed")
                        self.isPpiStreamOn = false
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func ppiStreamStop() {
        isPpiStreamOn = false
        ppiDisposable?.dispose()
    }
    
    func sdkModeToggle() {
        if case .connected(let deviceId) = deviceConnectionState {
            if isSdkStreamModeEnabled {
                api.disableSDKMode(deviceId)
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            NSLog("SDK mode disabled")
                            self.isSdkStreamModeEnabled = false
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
                            self.isSdkStreamModeEnabled = true
                        case .error(let err):
                            self.somethingFailed(text: "SDK mode enable failed: \(err)")
                        }
                    }.disposed(by: disposeBag)
            }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
            isSdkStreamModeEnabled = false
        }
    }
    
    func listExercises() {
        if case .connected(let deviceId) = deviceConnectionState {
            exerciseEntry = nil
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
                        self.exerciseEntry = polarExerciseEntry
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func readExercise() {
        if case .connected(let deviceId) = deviceConnectionState {
            guard let e = exerciseEntry else {
                somethingFailed(text: "No exercise to read, please list the exercises first")
                return
            }
            if(!isExerciseFetchInProgress) {
                isExerciseFetchInProgress = true
                api.fetchExercise(deviceId, entry: e)
                    .observe(on: MainScheduler.instance)
                    .do(onDispose: { self.isExerciseFetchInProgress = false})
                    .subscribe{ e in
                        switch e {
                        case .failure(let err):
                            NSLog("failed to read exercises: \(err)")
                        case .success(let data):
                            NSLog("exercise data count: \(data.samples.count) samples: \(data.samples)")
                        }
                    }.disposed(by: disposeBag)
            } else {
                NSLog("Reading of exercise is in progress at the moment.")
            }
        }
    }
    
    func removeExercise() {
        if case .connected(let deviceId) = deviceConnectionState {
            guard let entry = exerciseEntry else {
                somethingFailed(text: "No exercise to read, please list the exercises first")
                return
            }
            api.removeExercise(deviceId, entry: entry)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        self.exerciseEntry = nil
                        NSLog("remove completed")
                    case .error(let err):
                        NSLog("failed to remove exercise: \(err)")
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func h10RecordingToggle() {
        if case .connected(let deviceId) = deviceConnectionState {
            if isH10RecordingEnabled {
                api.stopRecording(deviceId)
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            NSLog("recording stopped")
                            self.isH10RecordingEnabled = false
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
                            self.isH10RecordingEnabled = true
                        case .error(let err):
                            self.somethingFailed(text: "recording start fail: \(err)")
                        }
                    }.disposed(by: disposeBag)
            }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
            isH10RecordingEnabled = false
        }
    }
    
    func getH10RecordingStatus() {
        if case .connected(let deviceId) = deviceConnectionState {
            api.requestRecordingStatus(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .failure(let err):
                        self.somethingFailed(text: "recording status request failed: \(err)")
                    case .success(let pair):
                        var recordingStatus = "Recording on: \(pair.ongoing)."
                        if pair.ongoing {
                            recordingStatus.append(" Recording started with id: \(pair.entryId)")
                            self.isH10RecordingEnabled = true
                        } else {
                            self.isH10RecordingEnabled = false
                        }
                        self.generalMessage = Message(text: recordingStatus)
                        NSLog(recordingStatus)
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func setTime() {
        if case .connected(let deviceId) = deviceConnectionState {
            let time = Date()
            let timeZone = TimeZone.current
            api.setLocalTime(deviceId, time: time, zone: timeZone)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        let formatter = DateFormatter()
                        formatter.dateStyle = .short
                        formatter.timeStyle = .medium
                        self.generalMessage = Message(text: "\(formatter.string(from: time)) set to device \(deviceId)")
                    case .error(let err):
                        self.somethingFailed(text: "time set failed: \(err)")
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func getTime() {
        if case .connected(let deviceId) = deviceConnectionState {
            api.getLocalTime(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .success(let date):
                        let formatter = DateFormatter()
                        formatter.dateStyle = .short
                        formatter.timeStyle = .medium
                        self.generalMessage = Message(text: "\(formatter.string(from: date)) read from the device \(deviceId)")
                    case .failure(let err):
                        self.somethingFailed(text: "time get failed: \(err)")
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    private func somethingFailed(text: String) {
        self.generalMessage = Message(text: "Error: \(text)")
        NSLog("Error \(text)")
    }
}

// MARK: - PolarBleApiPowerStateObserver
extension PolarBleSdkManager : PolarBleApiPowerStateObserver {
    func blePowerOn() {
        NSLog("BLE ON")
        isBluetoothOn = true
    }
    
    func blePowerOff() {
        NSLog("BLE OFF")
        isBluetoothOn = false
    }
}

// MARK: - PolarBleApiObserver
extension PolarBleSdkManager : PolarBleApiObserver {
    func deviceConnecting(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DEVICE CONNECTING: \(polarDeviceInfo)")
        
        deviceConnectionState = ConnectionState.connecting(polarDeviceInfo.deviceId)
    }
    
    func deviceConnected(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DEVICE CONNECTED: \(polarDeviceInfo)")
        
        if(polarDeviceInfo.name.contains("H10")){
            self.isH10RecordingSupported = true
            getH10RecordingStatus()
        }
        deviceConnectionState = ConnectionState.connected(polarDeviceInfo.deviceId)
    }
    
    func deviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DISCONNECTED: \(polarDeviceInfo)")
        
        deviceConnectionState = ConnectionState.disconnected
        self.isSdkStreamModeEnabled = false
        self.isSdkFeatureSupported = false
        self.isFtpFeatureSupported = false
        self.isH10RecordingSupported = false
        self.supportedStreamFeatures = Set<DeviceStreamingFeature>()
    }
}

// MARK: - PolarBleApiDeviceInfoObserver
extension PolarBleSdkManager : PolarBleApiDeviceInfoObserver {
    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        NSLog("battery level updated: \(batteryLevel)")
    }
    
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        NSLog("dis info: \(uuid.uuidString) value: \(value)")
    }
}

// MARK: - PolarBleApiDeviceFeaturesObserver
extension PolarBleSdkManager : PolarBleApiDeviceFeaturesObserver {
    func hrFeatureReady(_ identifier: String) {
        NSLog("HR ready")
    }
    
    func ftpFeatureReady(_ identifier: String) {
        NSLog("FTP ready")
        isFtpFeatureSupported = true
    }
    
    func streamingFeaturesReady(_ identifier: String, streamingFeatures: Set<DeviceStreamingFeature>) {
        supportedStreamFeatures = streamingFeatures
        for feature in streamingFeatures {
            NSLog("Feature \(feature) is ready.")
        }
    }
}

// MARK: - PolarBleApiSdkModeFeatureObserver
extension PolarBleSdkManager : PolarBleApiSdkModeFeatureObserver {
    func sdkModeFeatureAvailable(_ identifier: String) {
        isSdkFeatureSupported = true
        NSLog("SDK mode feature available. Device \(identifier)")
    }
}

// MARK: - PolarBleApiDeviceHrObserver
extension PolarBleSdkManager : PolarBleApiDeviceHrObserver {
    func hrValueReceived(_ identifier: String, data: PolarHrData) {
        NSLog("(\(identifier)) HR value: \(data.hr) rrsMs: \(data.rrsMs) rrs: \(data.rrs) contact: \(data.contact) contact supported: \(data.contactSupported)")
    }
}

// MARK: - PolarBleApiLogger
extension PolarBleSdkManager : PolarBleApiLogger {
    func message(_ str: String) {
        NSLog("Polar SDK log:  \(str)")
    }
}

extension PolarBleSdkManager {
    enum ConnectionState {
        case disconnected
        case connecting(String)
        case connected(String)
    }
}
