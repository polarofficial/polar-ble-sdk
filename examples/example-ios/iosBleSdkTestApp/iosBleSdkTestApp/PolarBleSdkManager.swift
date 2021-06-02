/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import RxSwift
import CoreBluetooth

class PolarBleSdkManager : ObservableObject {
    
    // NOTICE this example utilizes all available features
    private var api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: Features.allFeatures.rawValue)
    
    private var deviceId = "8C4CAD2D" //TODO replace this with your device id
    
    private var broadcastDisposable: Disposable?
    private var autoConnectDisposable: Disposable?
    private var searchDisposable: Disposable?
    private var ecgDisposable: Disposable?
    private var accDisposable: Disposable?
    private var gyroDisposable: Disposable?
    private var magnetometerDisposable: Disposable?
    private var ppgDisposable: Disposable?
    private var ppiDisposable: Disposable?
    private var exerciseEntry: PolarExerciseEntry?
    @Published private(set) var bluetoothPowerOn: Bool
    @Published private(set) var broadcastEnabled: Bool = false
    @Published private(set) var seachEnabled: Bool = false
    @Published private(set) var ecgEnabled: Bool = false
    @Published private(set) var accEnabled: Bool = false
    @Published private(set) var gyroEnabled: Bool = false
    @Published private(set) var magnetometerEnabled: Bool = false
    @Published private(set) var ppgEnabled: Bool = false
    @Published private(set) var ppiEnabled: Bool = false
    @Published private(set) var sdkModeEnabled: Bool = false
    @Published private(set) var deviceConnectionState: ConnectionState = ConnectionState.disconnected
    @Published var error: ErrorMessage? = nil
    
    init() {
        self.bluetoothPowerOn = api.isBlePowered
        
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
        if broadcastEnabled == false {
            broadcastDisposable = api.startListenForPolarHrBroadcasts(nil)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        self.broadcastEnabled = false
                        NSLog("Broadcast listener completed")
                    case .error(let err):
                        self.broadcastEnabled = false
                        NSLog("Broadcast listener failed. Reason: \(err)")
                    case .next(let broadcast):
                        self.broadcastEnabled = true
                        NSLog("HR BROADCAST \(broadcast.deviceInfo.name) HR:\(broadcast.hr) Batt: \(broadcast.batteryStatus)")
                    }
                }
        } else {
            broadcastEnabled = false
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
        if searchDisposable == nil {
            seachEnabled = true
            searchDisposable = api.searchForDevice()
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("search complete")
                        self.seachEnabled = false
                    case .error(let err):
                        NSLog("search error: \(err)")
                        self.seachEnabled = false
                    case .next(let item):
                        NSLog("polar device found: \(item.name) connectable: \(item.connectable) address: \(item.address.uuidString)")
                    }
                }
        } else {
            seachEnabled = false
            searchDisposable?.dispose()
            searchDisposable = nil
        }
    }
    
    func ecgToggle() {
        if ecgDisposable == nil, case .connected(let deviceId) = deviceConnectionState {
            ecgEnabled = true
            ecgDisposable = api.requestStreamSettings(deviceId, feature: DeviceStreamingFeature.ecg)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarEcgData> in
                    return self.api.startEcgStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for µv in data.samples {
                            NSLog("    µV: \(µv)")
                        }
                    case .error(let err):
                        NSLog("ECG stream failed: \(err)")
                        self.ecgEnabled = false
                    case .completed:
                        NSLog("ECG stream completed")
                        self.ecgEnabled = false
                    }
                }
        } else {
            ecgEnabled = false
            ecgDisposable?.dispose()
            ecgDisposable = nil
        }
    }
    
    func accToggle() {
        if accDisposable == nil, case .connected(let deviceId) = deviceConnectionState {
            accEnabled = true
            accDisposable = api.requestStreamSettings(deviceId, feature: DeviceStreamingFeature.acc)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarAccData> in
                    NSLog("settings: \(settings.settings)")
                    return self.api.startAccStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance).subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("    x: \(item.x) y: \(item.y) z: \(item.z)")
                        }
                    case .error(let err):
                        NSLog("ACC stream failed: \(err)")
                        self.accEnabled = false
                    case .completed:
                        NSLog("ACC stream completed")
                        self.accEnabled = false
                        break
                    }
                }
        } else {
            accEnabled = false
            accDisposable?.dispose()
            accDisposable = nil
        }
    }
    
    func gyroToggle() {
        if gyroDisposable == nil, case .connected(let deviceId) = deviceConnectionState {
            gyroEnabled = true
            gyroDisposable = api.requestStreamSettings(deviceId, feature:DeviceStreamingFeature.gyro)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarGyroData> in
                    return self.api.startGyroStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("    x: \(item.x) y: \(item.y) z: \(item.z)")
                        }
                    case .error(let err):
                        NSLog("GYR stream failed: \(err)")
                        self.gyroEnabled = false
                    case .completed:
                        NSLog("GYR stream completed")
                        self.gyroEnabled = false
                    }
                }
        } else {
            gyroEnabled = false
            gyroDisposable?.dispose()
            gyroDisposable = nil
        }
    }
    
    func magnetometerToggle() {
        if magnetometerDisposable == nil, case .connected(let deviceId) = deviceConnectionState {
            magnetometerEnabled = true
            magnetometerDisposable = api.requestStreamSettings(deviceId, feature:DeviceStreamingFeature.magnetometer)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarGyroData> in
                    return self.api.startMagnetometerStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("    x: \(item.x) y: \(item.y) z: \(item.z)")
                        }
                    case .error(let err):
                        NSLog("MAG stream failed: \(err)")
                        self.magnetometerEnabled = false
                    case .completed:
                        NSLog("MAG stream completed")
                        self.magnetometerEnabled = false
                    }
                }
        } else {
            magnetometerEnabled = false
            magnetometerDisposable?.dispose()
            magnetometerDisposable = nil
        }
    }
    
    func ppgToggle() {
        if ppgDisposable == nil, case .connected(let deviceId) = deviceConnectionState {
            ppgEnabled = true
            ppgDisposable = api.requestStreamSettings(deviceId, feature: DeviceStreamingFeature.ppg)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarOhrData> in
                    return self.api.startOhrStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if(data.type == OhrDataType.ppg3_ambient1) {
                            for item in data.samples {
                                NSLog("    ppg0: \(item[0]) ppg1: \(item[1]) ppg2: \(item[2]) ambient: \(item[3])")
                            }
                        }
                    case .error(let err):
                        NSLog("PPG stream failed: \(err)")
                        self.ppgEnabled = false
                    case .completed:
                        NSLog("PPG stream completed")
                        self.ppgEnabled = false
                    }
                }
        } else {
            ppgEnabled = false
            ppgDisposable?.dispose()
            ppgDisposable = nil
        }
    }
    
    func ppiToggle() {
        if ppiDisposable == nil, case .connected(let deviceId) = deviceConnectionState {
            ppiEnabled = true
            ppiDisposable = api.startOhrPPIStreaming(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("    PPI: \(item.ppInMs) sample.blockerBit: \(item.blockerBit)  errorEstimate: \(item.ppErrorEstimate)")
                        }
                    case .error(let err):
                        self.somethingFailed(text: "PPI stream failed: \(err)")
                        self.ppiEnabled = false
                    case .completed:
                        NSLog("PPI stream completed")
                        self.ppiEnabled = false
                    }
                }
        } else {
            ppiEnabled = false
            ppiDisposable?.dispose()
            ppiDisposable = nil
        }
    }
    
    func sdkModeEnable() {
        if case .connected(let deviceId) = deviceConnectionState {
            _ = api.enableSDKMode(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("SDK mode enabled")
                        self.sdkModeEnabled = true
                    case .error(let err):
                        self.somethingFailed(text: "SDK mode enable failed: \(err)")
                    }
                }
        }
    }
    
    func sdkModeDisable() {
        if case .connected(let deviceId) = deviceConnectionState {
            _ = api.disableSDKMode(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("SDK mode disabled")
                        self.sdkModeEnabled = false
                    case .error(let err):
                        self.somethingFailed(text: "SDK mode disable failed: \(err)")
                    }
                }
        }
    }
    
    func listExercises() {
        if case .connected(let deviceId) = deviceConnectionState {
            _ = api.fetchStoredExerciseList(deviceId)
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
                }
        }
    }
    
    func readExercise() {
        if case .connected(let deviceId) = deviceConnectionState {
            guard let e = exerciseEntry else {
                somethingFailed(text: "No exercise to read, please list the exercises first")
                return
            }
            _ = api.fetchExercise(deviceId, entry: e)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .failure(let err):
                        NSLog("failed to read exercises: \(err)")
                    case .success(let data):
                        NSLog("exercise data count: \(data.samples.count) samples: \(data.samples)")
                    }
                }
        }
    }
    
    func removeExercise() {
        if case .connected(let deviceId) = deviceConnectionState {
            guard let e = exerciseEntry else {
                somethingFailed(text: "No exercise to read, please list the exercises first")
                return
            }
            _ = api.removeExercise(deviceId, entry: e)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("remove completed")
                    case .error(let err):
                        NSLog("failed to remove exercise: \(err)")
                    }
                }
        }
    }
    
    func startH10Recording() {
        if case .connected(let deviceId) = deviceConnectionState {
            _ = api.startRecording(deviceId, exerciseId: "TEST_APP_ID", interval: .interval_1s, sampleType: .rr)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("recording started")
                    case .error(let err):
                        NSLog("recording start fail: \(err)")
                    }
                }
        }
    }
    
    func stopH10Recording() {
        if case .connected(let deviceId) = deviceConnectionState {
            _ = api.stopRecording(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("recording stopped")
                    case .error(let err):
                        NSLog("recording stop fail: \(err)")
                    }
                }
        }
    }
    
    func getH10RecordingStatus() {
        if case .connected(let deviceId) = deviceConnectionState {
            _ = api.requestRecordingStatus(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .failure(let err):
                        NSLog("recording status request failed: \(err)")
                    case .success(let pair):
                        NSLog("recording on: \(pair.ongoing) id: \(pair.entryId)")
                    }
                }
        }
    }
    
    func setTime() {
        if case .connected(let deviceId) = deviceConnectionState {
            let time = Date()
            let timeZone = TimeZone.current
            _ = api.setLocalTime(deviceId, time: time, zone: timeZone)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("time set to device completed")
                    case .error(let err):
                        self.somethingFailed(text: "time set failed: \(err)")
                    }
                }
        }
    }
    
    private func somethingFailed(text: String) {
        error = ErrorMessage(text:text)
        NSLog("Error \(text)")
    }
}

// MARK: - PolarBleApiPowerStateObserver
extension PolarBleSdkManager : PolarBleApiPowerStateObserver {
    func blePowerOn() {
        NSLog("BLE ON")
        bluetoothPowerOn = true
    }
    
    func blePowerOff() {
        NSLog("BLE OFF")
        bluetoothPowerOn = false
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
        deviceConnectionState = ConnectionState.connected(polarDeviceInfo.deviceId)
    }
    
    func deviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DISCONNECTED: \(polarDeviceInfo)")
        deviceConnectionState = ConnectionState.disconnected
        self.sdkModeEnabled = false
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

// MARK: - PolarBleApiSdkModeFeatureObserver
extension PolarBleSdkManager : PolarBleApiDeviceFeaturesObserver {
    func hrFeatureReady(_ identifier: String) {
        NSLog("HR ready")
    }
    
    func ftpFeatureReady(_ identifier: String) {
        NSLog("FTP ready")
    }
    
    func streamingFeaturesReady(_ identifier: String, streamingFeatures: Set<DeviceStreamingFeature>) {
        for feature in streamingFeatures {
            NSLog("Feature \(feature) is ready.")
        }
    }
}

// MARK: - PolarBleApiSdkModeFeatureObserver
extension PolarBleSdkManager : PolarBleApiSdkModeFeatureObserver {
    func sdkModeFeatureAvailable(_ identifier: String) {
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
