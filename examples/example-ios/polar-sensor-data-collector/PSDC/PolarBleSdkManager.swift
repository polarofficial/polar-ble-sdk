/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import RxSwift
import CoreBluetooth
import UserNotifications

/// PolarBleSdkManager demonstrates how to user PolarBleSDK API to access data and control devices
/// PSDC Uses separate managers per device connection to support multiple simultaneous connections
///
/// See PolarBleDeviceManager for scanning connectable devices
/// 
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
                                                                            PolarBleSdkFeature.feature_polar_h10_exercise_recording,
                                                                            PolarBleSdkFeature.feature_polar_features_configuration_service]
    )
    
    var connectedDevices: [(PolarDeviceInfo, Disposable?)] = []
    var updatingDevices:[PolarDeviceInfo] = []
    var disconnectedDevicesPairingErrors: [String: Bool] = [:]

    var connectedDevicesText = ""
    
    @Published var isBluetoothOn: Bool
    
    var deviceId: String? {
        if case .connected(let device) = deviceConnectionState {
            return device.deviceId
        }
        return nil
    }
    
    @Published var deviceConnectionState: DeviceConnectionState = DeviceConnectionState.noDevice(nullPolarDeviceInfo)
    
    @Published var deviceSearch: DeviceSearch = DeviceSearch()
    
    @Published var switchableDevices: [PolarDeviceInfo] = []
    
    @Published var onlineStreamingFeature: OnlineStreamingFeature = OnlineStreamingFeature()
    @Published var onlineStreamSettings: RecordingSettings? = nil
    
    @Published var offlineRecordingFeature = OfflineRecordingFeature()
    @Published var offlineRecordingSettings: RecordingSettings? = nil
    @Published var offlineRecordingSettingsMap: [PolarDeviceDataType: RecordingSettings] = [:]
    @Published var offlineRecordingEntries: OfflineRecordingEntries = OfflineRecordingEntries()
    @Published var offlineRecordingData: OfflineRecordingData = OfflineRecordingData()
    @Published var offlineRecordingTriggerSetup: PolarOfflineRecordingTrigger?
    @Published var activityRecordingData: ActivityRecordingData = ActivityRecordingData()
    @Published var onlineRecordingDataTypes: [PolarDeviceDataType] = []
    
    @Published var deviceTimeSetupFeature: DeviceTimeSetupFeature = DeviceTimeSetupFeature()
    
    @Published var sdkModeFeature: SdkModeFeature = SdkModeFeature()
    
    @Published var h10RecordingFeature: H10RecordingFeature = H10RecordingFeature()
    
    @Published var deviceInfoFeature: DeviceInfoFeature = DeviceInfoFeature()
    
    @Published var batteryStatusFeature: BatteryStatusFeature = BatteryStatusFeature()
    
    @Published var ledAnimationFeature: LedAnimationFeature = LedAnimationFeature()
    
    @Published var checkFirmwareUpdateFeature: CheckFirmwareUpdateFeature = CheckFirmwareUpdateFeature()
    @Published var firmwareUpdateFeature: FirmwareUpdateFeature = FirmwareUpdateFeature()
    
    @Published var generalMessage: Message? = nil
    @Published var sdLogConfig: SDLogConfig? = nil
    @Published var hrRecordingData: HrRecordingFeature = HrRecordingFeature()
    @Published var ecgRecordingData: EcgRecordingFeature = EcgRecordingFeature()
    @Published var accRecordingData: AccRecordingFeature = AccRecordingFeature()
    @Published var ppgRecordingData: PpgRecordingFeature = PpgRecordingFeature()
    @Published var ppiRecordingData: PpiRecordingFeature = PpiRecordingFeature()
    @Published var gyroRecordingData: GyroRecordingFeature = GyroRecordingFeature()
    @Published var magnetometerRecordingData: MagnetometerRecordingFeature = MagnetometerRecordingFeature()
    @Published var temperatureRecordingData: TemperatureRecordingFeature = TemperatureRecordingFeature()
    @Published var skinTemperatureRecordingData: SkinTemperatureRecordingFeature = SkinTemperatureRecordingFeature()
    @Published var pressureRecordingData: PressureRecordingFeature = PressureRecordingFeature()
    @Published var sleepRecordingFeature: SleepRecordingFeature = SleepRecordingFeature()
    
    @Published var trainingSessionEntries: TrainingSessionEntries = TrainingSessionEntries()
    @Published var trainingSessionData: TrainingSessionData = TrainingSessionData()
    @Published var exerciseState = ExerciseUIState()
    
    @Published var deviceConnected: Bool = false
    
    @Published var userDeviceSettings: UserDeviceSettingsFeature
    @Published var multiBleFeature: MultiBleFeature = MultiBleFeature()

    private var exerciseRefreshTimer: DispatchSourceTimer?
    private var broadcastDisposable: Disposable?
    private var autoConnectDisposable: Disposable?
    private var onlineStreamingDisposables: [PolarDeviceDataType: Disposable?] = [:]
    
    private let disposeBag = DisposeBag()
    private var h10ExerciseEntry: PolarExerciseEntry?
    
    private var searchDevicesTask: Task<Void, Never>? = nil
    
    private let encoder = JSONEncoder()
    private let dateFormatter = DateFormatter()
    
    init() {
        self.isBluetoothOn = api.isBlePowered
        self.userDeviceSettings = UserDeviceSettingsFeature(deviceUserLocation: PolarUserDeviceSettings.DeviceLocation.UNDEFINED)
        
        api.polarFilter(true)
        api.observer = self
        api.deviceFeaturesObserver = self
        api.powerStateObserver = self
        api.deviceInfoObserver = self
        
        if let logFileURL = getAppLogsFile() {
            logFileHandle = openLogFile(logFileURL: logFileURL)
        }
        api.logger = self
    }
    
    func updateSelectedDevice(device : PolarDeviceInfo) {
        
        if case .disconnected = deviceConnectionState {
            self.deviceConnectionState = DeviceConnectionState.disconnected(device)
        }
        
        if case .connected = deviceConnectionState {
            let device = self.deviceConnectionState.get()
            self.disconnectFromDevice(device: device)
        }
        
        if case .noDevice = deviceConnectionState {
            self.deviceConnectionState = DeviceConnectionState.noDevice(device)
        }
        
        if case .connecting = deviceConnectionState {
            self.deviceConnectionState = DeviceConnectionState.connecting(device)
        }
    }
    
}

extension PolarBleSdkManager {
    
    func connectToDevice(withId deviceId : String) {
        do {
            for connectedDevice in connectedDevices {
                if (connectedDevice.0.deviceId == deviceId) {
                    return
                }
            }
            try api.connectToDevice(deviceId)
            // Successful connection to device is handled in PolarBleApiObserver.deviceConnected(_ polarDeviceInfo: PolarDeviceInfo)
            // where control is returned to updateStateWhenDeviceConnected(_ polarDeviceInfo: PolarDeviceInfo)
        } catch let err {
            NSLog("Failed to connect to \(deviceId). Reason \(err)")
        }
    }
    
    private func updateStateWhenDeviceConnected(device : PolarDeviceInfo) {
        connectedDevices.insert((device, nil), at:0)
        self.deviceConnectionState = DeviceConnectionState.connected(device)
        self.updateDisplayedConnectedDevices(with: device)
    }
    
    
    func disconnectFromDevice(device : PolarDeviceInfo) {
        do {
            guard connectedDevices.contains(where: { $0.0.deviceId == deviceId }) else {
                NSLog("Not connected to \(device.deviceId), ignoring disconnect")
                return
            }
            guard deviceConnectionState.get().deviceId == device.deviceId else {
                NSLog("Not connected to \(device.deviceId), ignoring disconnect")
                return
            }
            NSLog("disconnectFromDevice \(device.deviceId)")
            try api.disconnectFromDevice(device.deviceId)
            // Disconnects are handled in PolarBleApiObserver.deviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo)
            // Where control is returned to updateStateWhenDeviceDisconnected(device : PolarDeviceInfo)
        } catch let err {
            NSLog("Failed to disconnect from \(device.deviceId). Reason \(err)")
        }
    }
    
    func devicesWithPairingErrors() -> [String] {
        return Array(disconnectedDevicesPairingErrors.keys)
    }
    
    private func updateStateWhenDeviceDisconnected(withId deviceId : String, pairingError: Bool) {
        
        guard false == updatingDevices.contains(where: { $0.deviceId == deviceId}) else {
            NSLog("Device \(deviceId) is being updated, ignoring disconnect during firmware update")
            return
        }
        
        if let index = connectedDevices.firstIndex(where: { $0.0.deviceId == deviceId }) {
            print("dispose timer")
            connectedDevices[index].1?.dispose()
            connectedDevices.remove(at: index)
            disconnectedDevicesPairingErrors[deviceId] = pairingError
        }
        
        if pairingError && generalMessage == nil {
            Task { @MainActor in
                self.generalMessage = Message(text: "Pairing error for \(deviceId). Remove previous Bluetooth pairing from phone and from sensor/watch to enable pairing again, restart app, and retry connecting.")
            }
        }
        updateDisplayedConnectedDevices()
    }
    
    private func updateDisplayedConnectedDevices(with device: PolarDeviceInfo? = nil) {
        if (connectedDevices.isEmpty) {
            connectedDevicesText = "No device connected"
            deviceConnectionState = DeviceConnectionState.noDevice(nullPolarDeviceInfo)
        } else {
            let selectedDevice = (connectedDevices.first(where: { $0.0.deviceId == device?.deviceId }) ?? connectedDevices.first!).0
            connectedDevicesText = "Connected: \(selectedDevice.name)"
            deviceConnectionState = DeviceConnectionState.connected(selectedDevice)
        }
        
        self.switchableDevices = self.connectedDevices.compactMap { (device, disposable) in
            return device.deviceId != self.deviceId ? device : nil
        }
        
        if checkFirmwareUpdateNeeded() {
            Task {
                sleep(3) // TODO: remove need for sleeping
                await checkFirmwareUpdate()
            }
        }
    }
    
    private func checkFirmwareUpdateNeeded() -> Bool {
        if let deviceId = deviceId {
            if (false == firmwareUpdateFeature.inProgress &&
                nil == checkFirmwareUpdateFeature.firmwareVersionAvailable) {
                return true
            }
            if deviceId != checkFirmwareUpdateFeature.polarDeviceInfo?.deviceId {
                return true
            }
        }
        return false
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
}

extension PolarBleSdkManager {
    
    func getOnlineStreamSettings(feature: PolarBleSdk.PolarDeviceDataType) {
        if case .connected(let device) = deviceConnectionState {
            NSLog("Online stream settings fetch for \(feature)")
            api.requestStreamSettings(device.deviceId, feature: feature)
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
        if case .connected(let device) = deviceConnectionState {
            NSLog("Offline recording settings fetch for \(feature)")
            api.requestOfflineRecordingSettings(device.deviceId, feature: feature)
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
    
    func offlineRecordingSettings(for feature: PolarDeviceDataType) -> RecordingSettings? {
        return offlineRecordingSettingsMap[feature]
    }
    
    func getOfflineRecordingTriggerSetup() -> Single<PolarOfflineRecordingTrigger> {
        return Single.create { single in
            if case .connected(let device) = self.deviceConnectionState {
                let identifier = device.deviceId
                NSLog("Offline recording trigger setup fetch for \(identifier)")
                
                self.api.getOfflineRecordingTriggerSetup(identifier)
                    .observe(on: MainScheduler.instance)
                    .subscribe { event in
                        switch event {
                        case .success(let triggerSetup):
                            NSLog("Offline recording trigger setup fetch completed for \(identifier)")
                            single(.success(triggerSetup))
                        case .failure(let error):
                            self.somethingFailed(text: "Offline recording trigger setup request failed: \(error)")
                            single(.failure(error))
                        }
                    }.disposed(by: self.disposeBag)
            } else {
                NSLog("Offline recording trigger setup request failed. Device is not connected \(self.deviceConnectionState)")
                single(.failure(PolarErrors.deviceNotConnected))
            }
            return Disposables.create()
        }
    }
    
    func selectSportProfile(_ sport: PolarExerciseSession.SportProfile) {
        exerciseState.selectedSport = sport
    }

    func refreshExerciseStatus() async {
        guard case .connected(let device) = deviceConnectionState else { return }
        await MainActor.run { self.exerciseState.isRefreshing = true }
        do {
            let info = try await api.getExerciseStatus(identifier: device.deviceId).value
            await MainActor.run {
                self.exerciseState.apply(status: info.status, sport: info.sportProfile)
                self.exerciseState.isRefreshing = false
            }
        } catch {
            NSLog("Exercise status read failed: \(error)")
            await MainActor.run { self.exerciseState.isRefreshing = false }
        }
    }

    func startExercise() {
        guard case .connected(let device) = deviceConnectionState else { return }
        NSLog("Start exercise pressed for \(device.deviceId) with sport=\(exerciseState.selectedSport)")
        api.startExercise(identifier: device.deviceId, profile: exerciseState.selectedSport)
            .andThen(api.getExerciseStatus(identifier: device.deviceId))
            .observe(on: MainScheduler.instance)
            .subscribe(
                onSuccess: { [weak self] info in
                    self?.exerciseState.apply(status: info.status, sport: info.sportProfile)
                    NSLog("Start exercise succeeded for \(device.deviceId)")
                },
                onFailure: { err in
                    NSLog("Start exercise failed for \(device.deviceId): \(err)")
                }
            )
            .disposed(by: disposeBag)
    }

    func pauseExercise() {
        guard case .connected(let device) = deviceConnectionState else { return }
        NSLog("Pause exercise pressed for \(device.deviceId)")
        api.pauseExercise(identifier: device.deviceId)
            .andThen(api.getExerciseStatus(identifier: device.deviceId))
            .observe(on: MainScheduler.instance)
            .subscribe(
                onSuccess: { [weak self] info in
                    self?.exerciseState.apply(status: info.status, sport: info.sportProfile)
                    NSLog("Pause exercise succeeded for \(device.deviceId)")
                },
                onFailure: { err in
                    NSLog("Pause exercise failed for \(device.deviceId): \(err)")
                }
            )
            .disposed(by: disposeBag)
    }

    func resumeExercise() {
        guard case .connected(let device) = deviceConnectionState else { return }
        NSLog("Resume exercise pressed for \(device.deviceId)")
        api.resumeExercise(identifier: device.deviceId)
            .andThen(api.getExerciseStatus(identifier: device.deviceId))
            .observe(on: MainScheduler.instance)
            .subscribe(
                onSuccess: { [weak self] info in
                    self?.exerciseState.apply(status: info.status, sport: info.sportProfile)
                    NSLog("Resume exercise succeeded for \(device.deviceId)")
                },
                onFailure: { err in
                    NSLog("Resume exercise failed for \(device.deviceId): \(err)")
                }
            )
            .disposed(by: disposeBag)
    }

    func stopExercise() {
        guard case .connected(let device) = deviceConnectionState else { return }
        NSLog("Stop exercise pressed for \(device.deviceId)")
        api.stopExercise(identifier: device.deviceId)
            .andThen(api.getExerciseStatus(identifier: device.deviceId))
            .observe(on: MainScheduler.instance)
            .subscribe(
                onSuccess: { [weak self] info in
                    self?.exerciseState.apply(status: info.status, sport: info.sportProfile)
                    NSLog("Stop exercise succeeded for \(device.deviceId)")
                },
                onFailure: { err in
                    NSLog("Stop exercise failed for \(device.deviceId): \(err)")
                }
            )
            .disposed(by: disposeBag)
    }

    func startExerciseAutoRefresh(intervalSec: Int = 5) {
        stopExerciseAutoRefresh()
        let timer = DispatchSource.makeTimerSource(queue: .main)
        timer.schedule(deadline: .now(), repeating: .seconds(intervalSec))
        timer.setEventHandler { [weak self] in
            Task { await self?.refreshExerciseStatus() }
        }
        exerciseRefreshTimer = timer
        timer.resume()
    }

    func stopExerciseAutoRefresh() {
        exerciseRefreshTimer?.cancel()
        exerciseRefreshTimer = nil
    }
    
    func setOfflineRecordingTrigger(
        trigger: PolarOfflineRecordingTrigger,
        secret: PolarRecordingSecret?
    ) -> Completable {
        return Completable.create { completable in
            if case .connected(let device) = self.deviceConnectionState {
                let identifier = device.deviceId
                NSLog("Setting offline recording trigger for \(identifier)")
                
                self.api.setOfflineRecordingTrigger(identifier, trigger: trigger, secret: secret)
                    .observe(on: MainScheduler.instance)
                    .subscribe(
                        onCompleted: {
                            NSLog("Offline recording trigger set successfully for \(identifier)")
                            completable(.completed)
                        },
                        onError: { error in
                            self.somethingFailed(text: "Setting offline recording trigger failed: \(error)")
                            completable(.error(error))
                        }
                    )
                    .disposed(by: self.disposeBag)
            } else {
                NSLog("Setting offline recording trigger request failed. Device is not connected \(self.deviceConnectionState)")
                completable(.error(PolarErrors.deviceNotConnected))
            }
            return Disposables.create()
        }
    }

    func getOfflineRecordingTriggerSettings(feature: PolarDeviceDataType) -> Completable {
        return Completable.create { [self] completable in
            if case .connected(let device) = self.deviceConnectionState {
                let identifier = device.deviceId
                NSLog("Offline recording trigger setup fetch for \(identifier)")

                api.requestOfflineRecordingSettings(device.deviceId, feature: feature)
                    .observe(on: MainScheduler.instance)
                    .subscribe { [weak self] event in
                        switch event {
                        case .success(let settings):
                            BleLogger.trace("Offline recording settings fetched for feature: \(feature)")
                            let recordingSettings = self?.convertToRecordingSettings(feature: feature, sensorSettings: settings)
                            if let recordingSettings = recordingSettings {
                                self?.offlineRecordingSettingsMap[feature] = recordingSettings
                            }
                            completable(.completed)
                        case .failure(let error):
                            BleLogger.error("Failed to fetch offline recording settings for feature \(feature): \(error)")
                            completable(.error(error))
                        }
                    }.disposed(by: self.disposeBag)
            } else {
                NSLog("Device is not connected. Cannot fetch offline recording settings.")
                completable(.error(PolarErrors.deviceNotConnected))
            }
            return Disposables.create()
        }
    }

    private func convertToRecordingSettings(feature: PolarDeviceDataType, sensorSettings: PolarSensorSetting) -> RecordingSettings {
        var typeSettings: [TypeSetting] = []
        
        for (settingType, values) in sensorSettings.settings {
            let typeSetting = TypeSetting(type: settingType, values: Array(values).map { Int($0) })
            typeSettings.append(typeSetting)
        }
        
        return RecordingSettings(feature: feature, settings: typeSettings)
    }
    
    func onlineStreamStart(feature: PolarDeviceDataType, settings: RecordingSettings? = nil) {
        var logString:String = "Request \(feature) online stream start with settings: "
        
        var polarSensorSettings: [PolarSensorSetting.SettingType : UInt32] = [:]
        settings?.settings.forEach {
            polarSensorSettings[$0.type] = UInt32($0.values[0])
            logString.append(" \($0.type) \($0.values[0])")
        }
        NSLog(logString)
        onlineRecordingDataTypes.insert(feature, at:0)
        do {
            switch feature {
            case .ecg:
                try ecgStreamStart(settings: PolarSensorSetting(polarSensorSettings))
            case .acc:
                try accStreamStart(settings: PolarSensorSetting(polarSensorSettings))
            case .magnetometer:
                try magStreamStart(settings: PolarSensorSetting(polarSensorSettings))
            case .ppg:
                try ppgStreamStart(settings: PolarSensorSetting(polarSensorSettings))
            case .gyro:
                try gyrStreamStart(settings: PolarSensorSetting(polarSensorSettings))
            case .ppi:
                ppiStreamStart()
            case .hr:
                hrStreamStart()
            case .temperature:
                try temperatureStreamStart(settings: PolarSensorSetting(polarSensorSettings))
            case .pressure:
                try pressureStreamStart(settings: PolarSensorSetting(polarSensorSettings))
            case .skinTemperature:
                try skinTemperatureStreamStart(settings: PolarSensorSetting(polarSensorSettings))
            }
        } catch let err {
            NSLog("Settings validation failed for datatype \(feature.displayName), error: \(err)")
            self.somethingFailed(text: "Settings validation failed for datatype \(feature.displayName).")
        }
    }
    
    func onlineStreamStop(feature: PolarBleSdk.PolarDeviceDataType) {
        onlineRecordingDataTypes.removeAll { dataType in
            dataType == feature
        }
        onlineStreamingDisposables[feature]??.dispose()
    }
    
    func listOfflineRecordings() async {
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.offlineRecordingEntries.entries.removeAll()
                self.offlineRecordingEntries.isFetching = true
            }
            NSLog("Start offline recording listing")
            api.listOfflineRecordings(device.deviceId)
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
        if case .connected(let device) = deviceConnectionState {
            NSLog("getOfflineRecordingStatus")
            api.getOfflineRecordingStatus(device.deviceId)
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
    
    func removeOfflineRecording(offlineRecordingEntry: PolarOfflineRecordingEntry) {
        if case .connected(let device) = deviceConnectionState {
            NSLog("start offline recording removal")
            api.removeOfflineRecord(device.deviceId, entry: offlineRecordingEntry)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("offline recording removal completed")
                        Task { @MainActor in
                            self.offlineRecordingEntries.entries.removeAll{$0 == offlineRecordingEntry}
                        }
                    case .error(let err):
                        NSLog("offline recording remove failed: \(err)")
                    }
                }.disposed(by: disposeBag)
        } else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
        }
    }
    
    func getOfflineRecording(offlineRecordingEntry: PolarOfflineRecordingEntry) async {
        if case .connected(let device) = deviceConnectionState {
            Task { @MainActor in
                self.offlineRecordingData.loadState = OfflineRecordingDataLoadingState.inProgress
            }
            
            do {
                NSLog("start offline recording \(offlineRecordingEntry.path) fetch")
                let readStartTime = Date()
                let offlineRecording: PolarOfflineRecordingData
                offlineRecording = try await api.getOfflineRecord(device.deviceId, entry: offlineRecordingEntry, secret: nil).value
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
                        self.offlineRecordingData.data = ppgDataHeaderString(data) + dataToString(data)
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
                    NSLog("Temperature data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = nil
                        self.offlineRecordingData.data = dataHeaderString(.temperature) + dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .skinTemperatureOfflineRecordingData(let data, startTime: let startTime):
                    NSLog("Skin temperature data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
                        self.offlineRecordingData.usedSettings = nil
                        self.offlineRecordingData.data = dataHeaderString(.skinTemperature) + dataToString(data)
                        self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                        self.offlineRecordingData.downLoadTime = elapsedTime
                    }
                case .emptyData(startTime: let startTime):
                    NSLog("Empty data received")
                    Task { @MainActor in
                        self.offlineRecordingData.startTime = startTime
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
        if case .connected(let device) = deviceConnectionState {
            var logString:String = "Request offline recording \(feature) start with settings: "
            
            var polarSensorSettings:[PolarSensorSetting.SettingType : UInt32] = [:]
            settings?.settings.forEach {
                polarSensorSettings[$0.type] = UInt32($0.values[0])
                logString.append(" \($0.type) \($0.values[0])")
            }
            NSLog(logString)
            
            do {
                try api.startOfflineRecording(device.deviceId, feature: feature, settings: PolarSensorSetting(polarSensorSettings), secret: nil)
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            Task { @MainActor in
                                self.offlineRecordingFeature.isRecording[feature] = true
                                NSLog("offline recording \(feature) successfully started")
                            }
                        case .error(let err):
                            NSLog("failed to start offline recording \(feature). Reason: \(err)")
                        }
                    }.disposed(by: disposeBag)
            } catch {
                somethingFailed(text: "Settings validation failed for datatype \(feature.displayName).")
            }
        } else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
        }
    }
    
    func offlineRecordingStop(feature: PolarDeviceDataType) {
        if case .connected(let device) = deviceConnectionState {
            NSLog("Request offline recording \(feature) stop")
            api.stopOfflineRecording(device.deviceId, feature: feature)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        Task { @MainActor in
                            self.offlineRecordingFeature.isRecording[feature] = false
                            NSLog("offline recording \(feature) successfully stopped")
                        }
                    case .error(let err):
                        NSLog("failed to stop offline recording \(feature). Reason: \(err)")
                        Task { @MainActor in
                            self.offlineRecordingFeature.isRecording[feature] = false
                        }
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
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.ecg] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .ecg)
            
            onlineStreamingDisposables[.ecg] = api.startEcgStreaming(device.deviceId, settings: settings)
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
                        Task { @MainActor in
                            for item in data {
                                NSLog("ECG    µV: \(item.voltage) timeStamp: \(item.timeStamp)")
                                self.ecgRecordingData.voltage = item.voltage
                                self.ecgRecordingData.timestamp = item.timeStamp
                            }
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
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.acc] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .acc)
            
            NSLog("ACC stream start: \(device.deviceId)")
            onlineStreamingDisposables[.acc] = api.startAccStreaming(device.deviceId, settings: settings)
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
                        for item in data {
                            NSLog("ACC    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                        Task { @MainActor in
                            self.accRecordingData.x = data.last!.x
                            self.accRecordingData.y = data.last!.y
                            self.accRecordingData.z = data.last!.z
                            self.accRecordingData.timestamp = data.last!.timeStamp
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
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.magnetometer] = OnlineStreamingState.inProgress
            }
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .magnetometer)
            
            onlineStreamingDisposables[.magnetometer] = api.startMagnetometerStreaming(device.deviceId, settings: settings)
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
                        for item in data {
                            NSLog("MAG    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                        Task { @MainActor in
                            self.magnetometerRecordingData.x = data.last!.x
                            self.magnetometerRecordingData.y = data.last!.y
                            self.magnetometerRecordingData.z = data.last!.z
                            self.magnetometerRecordingData.timestamp = data.last!.timeStamp
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
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.gyro] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .gyro)
            
            onlineStreamingDisposables[.gyro] = api.startGyroStreaming(device.deviceId, settings: settings)
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
                        for item in data {
                            NSLog("GYR    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)")
                        }
                        Task { @MainActor in
                            self.gyroRecordingData.x = data.last!.x
                            self.gyroRecordingData.y = data.last!.y
                            self.gyroRecordingData.z = data.last!.z
                            self.gyroRecordingData.timestamp = data.last!.timeStamp
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
        
        var previousDataType: PpgDataType = PpgDataType.unknown
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.ppg] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .ppg)
            
            onlineStreamingDisposables[.ppg] = api.startPpgStreaming(device.deviceId, settings: settings)
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
                        if (previousDataType != data.type) {
                            if let fileHandle = logFile?.fileHandle {
                                fileHandle.write(self.ppgDataHeaderString(data).data(using: .utf8)!)
                            }
                            previousDataType = data.type
                        }
                        
                        if(data.type == PpgDataType.ppg3_ambient1) {
                            if let fileHandle = logFile?.fileHandle {
                                self.writeOnlineStreamLogFile(fileHandle, data)
                            }
                            
                            for item in data.samples {
                                NSLog("PPG  ppg0: \(item.channelSamples[0]) ppg1: \(item.channelSamples[1]) ppg2: \(item.channelSamples[2]) ambient: \(item.channelSamples[3]) timeStamp: \(item.timeStamp)")
                            }
                            Task { @MainActor in
                                self.ppgRecordingData.ppg0 = data.samples[0].channelSamples[0]
                                self.ppgRecordingData.ppg1 = data.samples[0].channelSamples[1]
                                self.ppgRecordingData.ppg2 = data.samples[0].channelSamples[2]
                                self.ppgRecordingData.ambient = data.samples[0].channelSamples[3]
                            }
                        }
                        if(data.type == PpgDataType.ppg3) {
                            if let fileHandle = logFile?.fileHandle {
                                self.writeOnlineStreamLogFile(fileHandle, data)
                            }
                            
                            for item in data.samples {
                                NSLog("PPG  ppg0: \(item.channelSamples[0]) ppg1: \(item.channelSamples[1]) ppg2: \(item.channelSamples[2]) timeStamp: \(item.timeStamp)")
                            }
                            Task { @MainActor in
                                self.ppgRecordingData.ppg0 = data.samples[0].channelSamples[0]
                                self.ppgRecordingData.ppg1 = data.samples[0].channelSamples[1]
                                self.ppgRecordingData.ppg2 = data.samples[0].channelSamples[2]
                            }
                        }
                        
                        if (data.type == PpgDataType.ppg17) {
                            if let fileHandle = logFile?.fileHandle {
                                self.writeOnlineStreamLogFile(fileHandle, data)
                            }
                            for item in data.samples {
                                NSLog("PPG  ppg0: \(item.channelSamples[0]) ppg1: \(item.channelSamples[1]) ppg2: \(item.channelSamples[2]) ppg3: \(item.channelSamples[3])")
                            }
                            Task { @MainActor in
                                self.ppgRecordingData.ppg0 = data.samples.last!.channelSamples[0]
                                self.ppgRecordingData.ppg1 = data.samples.last!.channelSamples[1]
                                self.ppgRecordingData.ppg2 = data.samples.last!.channelSamples[2]
                                self.ppgRecordingData.ppg3 = data.samples.last!.channelSamples[3]
                            }
                        }
                        if (data.type == PpgDataType.ppg21) {
                            if let fileHandle = logFile?.fileHandle {
                                self.writeOnlineStreamLogFile(fileHandle, data)
                            }
                            for item in data.samples {
                                NSLog("PPG  Green: \(item.channelSamples[0]) red: \(item.channelSamples[8]) ir: \(item.channelSamples[14])")
                            }
                            Task { @MainActor in
                                self.ppgRecordingData.ppg0 = data.samples.last!.channelSamples[0]
                                self.ppgRecordingData.ppg1 = data.samples.last!.channelSamples[8]
                                self.ppgRecordingData.ppg2 = data.samples.last!.channelSamples[14]
                            }
                        }
                        if (data.type == PpgDataType.ppg1) {
                            if let fileHandle = logFile?.fileHandle {
                                self.writeOnlineStreamLogFile(fileHandle, data)
                            }
                            NSLog("PPG  SportId: \(data.samples.last!.channelSamples[0])")
                            Task { @MainActor in
                                self.ppgRecordingData.sportId = data.samples.last!.channelSamples[0]
                            }
                        }
                        if (data.type == PpgDataType.ppg2) {
                            if let fileHandle = logFile?.fileHandle {
                                self.writeOnlineStreamLogFile(fileHandle, data)
                            }
                            
                            for item in data.samples {
                                NSLog("PPG  ppg0: \(item.channelSamples[0]) ppg1: \(item.channelSamples[1]) status: \(item.channelSamples[2]) timeStamp: \(item.timeStamp)")
                            }
                            Task { @MainActor in
                                self.ppgRecordingData.ppg0 = data.samples[0].channelSamples[0]
                                self.ppgRecordingData.ppg1 = data.samples[0].channelSamples[1]
                                self.ppgRecordingData.status = data.samples[0].channelSamples[2]
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
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.ppi] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .ppi)
            
            onlineStreamingDisposables[.ppi] = api.startPpiStreaming(device.deviceId)
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
                        Task { @MainActor in
                            for item in data.samples {
                                NSLog("PPI    TimeStamp: \(item.timeStamp)    PeakToPeak(ms): \(item.ppInMs) sample.blockerBit: \(item.blockerBit)  errorEstimate: \(item.ppErrorEstimate)")
                                self.ppiRecordingData.ppInMs = item.ppInMs
                                self.ppiRecordingData.blockerBit = item.blockerBit
                                self.ppiRecordingData.ppErrorEstimate = item.ppErrorEstimate
                                self.ppiRecordingData.timeStamp = item.timeStamp
                            }
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
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.hr] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .hr)
            
            onlineStreamingDisposables[.hr] = api.startHrStreaming(device.deviceId)
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
                        Task { @MainActor in
                            self.hrRecordingData.hr = data[0].hr
                            // self.hrRecordingData.rrs = data[0].rrsMs[0]
                            self.hrRecordingData.rrAvailable = data[0].rrAvailable
                            self.hrRecordingData.contactStatus = data[0].contactStatus
                            self.hrRecordingData.contactStatusSupported = data[0].contactStatusSupported
                        }
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
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.temperature] = OnlineStreamingState.inProgress
            }
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .temperature)
            
            onlineStreamingDisposables[.temperature] = api.startTemperatureStreaming(device.deviceId, settings: settings)
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
                        Task { @MainActor in
                            for item in data.samples {
                                NSLog("TEMP    temp: \(item.temperature) timestamp: \(item.timeStamp)")
                                self.temperatureRecordingData.temperature = item.temperature
                                self.temperatureRecordingData.timestamp = item.timeStamp
                            }
                        }
                    case .error(let err):
                        NSLog("TEMP stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("TEMP stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func pressureStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.pressure] = OnlineStreamingState.inProgress
            }
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .pressure)
            
            onlineStreamingDisposables[.pressure] = api.startPressureStreaming(device.deviceId, settings: settings)
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
                        Task { @MainActor in
                            for item in data.samples {
                                NSLog("PRE    pressure: \(item.pressure) timestamp: \(item.timeStamp)")
                                self.pressureRecordingData.pressure = item.pressure
                                self.pressureRecordingData.timestamp = item.timeStamp
                            }
                        }
                    case .error(let err):
                        NSLog("PRE stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("PRE stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func skinTemperatureStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.skinTemperature] = OnlineStreamingState.inProgress
            }
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .skinTemperature)
            
            onlineStreamingDisposables[.skinTemperature] = api.startSkinTemperatureStreaming(device.deviceId, settings: settings)
                .do(onDispose: {
                    if let fileHandle = logFile?.fileHandle {
                        self.closeOnlineStreamLogFile(fileHandle)
                    }
                    Task { @MainActor in
                        self.onlineStreamingFeature.isStreaming[.skinTemperature] = OnlineStreamingState.success(url: logFile?.url)
                    }
                })
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if let fileHandle = logFile?.fileHandle {
                            self.writeOnlineStreamLogFile(fileHandle, data)
                        }
                        Task { @MainActor in
                            for item in data.samples {
                                NSLog("SKIN TEMP    temp: \(item.temperature) timestamp: \(item.timeStamp)")
                                self.skinTemperatureRecordingData.temperature = item.temperature
                                self.skinTemperatureRecordingData.timestamp = item.timeStamp
                            }
                        }
                    case .error(let err):
                        NSLog("SKIN TEMP stream failed: \(err)")
                        if let fileHandle = logFile?.fileHandle {
                            self.writeErrorOnlineStreamLogFile(fileHandle, err)
                        }
                    case .completed:
                        NSLog("SKIN TEMP stream completed")
                    }
                }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func sdkModeToggle() {
        guard self.sdkModeFeature.isSupported else {
            return
        }
        
        if case .connected(let device) = deviceConnectionState {
            if self.sdkModeFeature.isEnabled {
                api.disableSDKMode(device.deviceId)
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
                api.enableSDKMode(device.deviceId)
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
        if case .connected(let device) = deviceConnectionState,
           self.sdkModeFeature.isSupported == true,
           self.firmwareUpdateFeature.inProgress == false {
            do {
                NSLog("get SDK mode status")
                let isSdkModeEnabled: Bool = try await api.isSDKModeEnabled(device.deviceId).value
                NSLog("SDK mode currently enabled: \(isSdkModeEnabled)")
                Task { @MainActor in
                    self.sdkModeFeature.isEnabled = isSdkModeEnabled
                }
            } catch let err {
                Task { @MainActor in
                    let errorMessage = "\(err)"
                    if errorMessage.contains("gattAttributeError") && errorMessage.contains("errorCode: 3") {
                        NSLog("SDK mode not supported by device")
                        self.sdkModeFeature.isSupported = false
                    } else {
                        self.somethingFailed(text: "SDK mode status request failed: \(err)")
                    }
                }
            }
        }
    }
    
    func listH10Exercises() {
        if case .connected(let device) = deviceConnectionState {
            h10ExerciseEntry = nil
            api.fetchStoredExerciseList(device.deviceId)
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
        if case .connected(let device) = deviceConnectionState {
            guard let e = h10ExerciseEntry else {
                somethingFailed(text: "No exercise to read, please list the exercises first")
                return
            }
            
            do {
                Task { @MainActor in
                    self.h10RecordingFeature.isFetchingRecording = true
                }
                
                let data:PolarExerciseData = try await api.fetchExercise(device.deviceId, entry: e).value
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
        if case .connected(let device) = deviceConnectionState {
            guard let entry = h10ExerciseEntry else {
                somethingFailed(text: "No exercise to read, please list the exercises first")
                return
            }
            api.removeExercise(device.deviceId, entry: entry)
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
        if case .connected(let device) = deviceConnectionState {
            if self.h10RecordingFeature.isEnabled {
                api.stopRecording(device.deviceId)
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
                api.startRecording(device.deviceId, exerciseId: "TEST_APP_ID", interval: .interval_1s, sampleType: .rr)
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
        if case .connected(let device) = deviceConnectionState, self.h10RecordingFeature.isSupported {
            api.requestRecordingStatus(device.deviceId)
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
        if case .connected(let device) = deviceConnectionState {
            do {
                let time = Date()
                let timeZone = TimeZone.current
                
                let _: Void = try await api.setLocalTime(device.deviceId, time: time, zone: timeZone).value
                Task { @MainActor in
                    let formatter = DateFormatter()
                    formatter.dateStyle = .short
                    formatter.timeStyle = .medium
                    self.generalMessage = Message(text: "\(formatter.string(from: time)) set to device \(device.deviceId)")
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
        if case .connected(let device) = deviceConnectionState {
            do {
                let date: Date = try await api.getLocalTime(device.deviceId).value
                Task { @MainActor in
                    let formatter = DateFormatter()
                    formatter.dateStyle = .short
                    formatter.timeStyle = .medium
                    self.generalMessage = Message(text: "\(formatter.string(from: date)) read from the device \(device.deviceId)")
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
        if case .connected(let device) = deviceConnectionState {
            do {
                let diskSpace: PolarDiskSpaceData = try await api.getDiskSpace(device.deviceId).value
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
        if case .connected(let device) = deviceConnectionState {
            do {
                let _: Void = try await api.setLedConfig(device.deviceId, ledConfig: ledConfig).value
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
    
    func doRestart(preservePairingInformation: Bool) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let _: Void = try await api.doRestart(device.deviceId, preservePairingInformation: preservePairingInformation).value
                Task { @MainActor in
                    self.generalMessage = Message(text: "restarting notification to device: \(device.deviceId)")
                }
            } catch let err as BleGattException {
                switch err {
                case .gattDisconnected:
                    // Expected case
                    return
                default:
                    Task { @MainActor in
                        self.somethingFailed(text: "doRestart() BleGattException: \(err)")
                    }
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "doRestart() error: \(err)")
                }
                
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "doRestart() failed. No device connected)")
            }
        }
    }
    
    func doFactoryReset(preservePairingInformation: Bool) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let _: Void = try await api.doFactoryReset(device.deviceId, preservePairingInformation: preservePairingInformation).value
                Task { @MainActor in
                    self.generalMessage = Message(text: "Send factory reset notification to device: \(device.deviceId)")
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
    
    func checkIfDeviceIdSet() -> Bool {
        
        return !(self.deviceId?.isEmpty ?? true)
    }
    
    func sendPhysicalConfig(ftuConfig: PolarFirstTimeUseConfig) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.doFirstTimeUse(device.deviceId, ftuConfig: ftuConfig).value
                
                Task { @MainActor in
                    self.generalMessage = Message(text: "Physical data sent to device: \(device.deviceId)")
                }
                
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "doPhysicalConfig() error: \(err.localizedDescription)")
                }
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "doPhysicalConfig() failed. No connected device.")
            }
        }
    }
    
    func getFtuStatus() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let ftuDone = try await api.isFtuDone(device.deviceId).value
                Task { @MainActor in
                    self.generalMessage = Message(text: "Has FTU been done? \(ftuDone)")
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "Fetching FTU status failed: \(err)")
                }
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "Fetching FTU status failed. No device connected)")
            }
        }
    }
    
    func getUserPhysicalConfiguration() async -> PolarPhysicalConfiguration? {
        if case .connected(let device) = deviceConnectionState {
            do {
                guard let physInfo = try await api.getUserPhysicalConfiguration(device.deviceId).value else {
                    Task { @MainActor in
                        self.somethingFailed(text: "No physical configuration stored on device.")
                    }
                    return nil
                }
                return physInfo

            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "getUserPhysicalConfiguration() error: \(err)")
                }
                return nil
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "getUserPhysicalConfiguration() failed. No device connected.")
            }
            return nil
        }
    }

    func setWarehouseSleep() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let _: Void = try await api.setWarehouseSleep(device.deviceId).value
                Task { @MainActor in
                    self.generalMessage = Message(text: "Set warehouse sleep on device: \(device.deviceId) to true.")
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "setWarehouseSleep() error: \(err)")
                }
                
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "setWarehouseSleep() failed. No device connected)")
            }
        }
    }
    
    func turnDeviceOff() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let _: Void = try await api.turnDeviceOff(device.deviceId).value
                Task { @MainActor in
                    self.generalMessage = Message(text: "Turn device \(device.deviceId) off.")
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "turnDeviceOff() error: \(err)")
                }
                
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "turnDeviceOff() failed. No device connected)")
            }
        }
    }
    
    func setSDLogSettings(logConfig: SDLogConfig) {
        
        if case .connected(let device) = deviceConnectionState {
            Task.detached {
                do {
                    try await self.api.setSDLogConfiguration(device.deviceId, logConfiguration: logConfig).value
                }
                catch let err {
                    NSLog("Setting Sensor Datalog failed: \(err)")
                }
            }
            do {
                Task.detached {
                    do {
                        await self.getSDLogSettings()
                    }
                }
            }
        }
    }
    
    func checkFirmwareUpdate() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    self.checkFirmwareUpdateFeature = CheckFirmwareUpdateFeature(isSupported: true, status: "starting...", inProgress: true, polarDeviceInfo: device)
                }
                let checkFirmwareUpdateStatusObservable = api.checkFirmwareUpdate(device.deviceId)
                checkFirmwareUpdateStatusObservable.subscribe(onNext: { [weak self] status in
                    guard let self = self else { return }
                    Task { @MainActor [weak self] in
                        guard let self = self else { return }
                        switch status {
                        case .checkFwUpdateAvailable(let version):
                            self.checkFirmwareUpdateFeature.firmwareVersionAvailable = version
                            self.checkFirmwareUpdateFeature.status = "checkFwUpdateAvailable: \(version)"
                        case .checkFwUpdateFailed(let details):
                            self.checkFirmwareUpdateFeature.status = "checkFwUpdateFailed: \(details)"
                        case .checkFwUpdateNotAvailable(let details):
                            self.checkFirmwareUpdateFeature.status = "checkFwUpdateNotAvailable: \(details)"
                        }
                        self.checkFirmwareUpdateFeature.inProgress = false
                    }
                }, onError: { [weak self] error in
                    Task { @MainActor [weak self] in
                        guard let self = self else { return }
                        self.checkFirmwareUpdateFeature.status = "Error: \(error.localizedDescription)"
                        self.checkFirmwareUpdateFeature.inProgress = false
                    }
                }).disposed(by: disposeBag)
            }
        } else {
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                self.checkFirmwareUpdateFeature.status = "No device connected."
                self.checkFirmwareUpdateFeature.inProgress = false
            }
        }
        
    }
    
    func updateFirmware(withFirmwareURL firmwareURL: URL?) async {
        if case .connected(let device) = deviceConnectionState {
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                self.firmwareUpdateFeature = FirmwareUpdateFeature(isSupported: true, status: "starting...", inProgress: true)
            }
            do {
                
                let firmwareUpdateStatusObservable =
                firmwareURL != nil ?
                api.updateFirmware(device.deviceId, fromFirmwareURL: firmwareURL!)
                :
                api.updateFirmware(device.deviceId)
                
                let firmwareUpdateStatusSubscription = firmwareUpdateStatusObservable
                    .do(onNext: { [weak self] status in
                    guard let self = self else { return }
                    let badgeId = "FWU-\(deviceId ?? "")"
                    let title = "Updating firmware"
                    Task { @MainActor [weak self] in
                        guard let self = self else { return }
                        switch status {
                        case .fetchingFwUpdatePackage(let details):
                            let message = "fetchingFwUpdatePackage: \(details)"
                            self.firmwareUpdateFeature.status = message
                            self.showFWUNotification(id: badgeId, title: "Fetching firmware", body: details)
                            self.updatingDevices.append(device)
                        case .preparingDeviceForFwUpdate(let details):
                            let message = "preparingDeviceForFwUpdate: \(details)"
                            self.firmwareUpdateFeature.status = message
                            self.showFWUNotification(id: badgeId, title: "Preparing device for update", body: details)
                        case .writingFwUpdatePackage(let details):
                            let message = "writingFwUpdatePackage: \(details)"
                            self.firmwareUpdateFeature.status = message
                            self.showFWUNotification(id: badgeId, title: "Writing firmware to device", body: details)
                        case .finalizingFwUpdate(let details):
                            let message = "finalizingFwUpdate: \(details)"
                            self.firmwareUpdateFeature.status = message
                            self.showFWUNotification(id: badgeId, title: "Finalizing update", body: details)
                        case .fwUpdateCompletedSuccessfully(let details):
                            let message = "fwUpdateCompletedSuccessfully: \(details)"
                            self.firmwareUpdateFeature.status = message
                            self.showFWUNotification(id: badgeId, title: "Firmware update complete", body: details)
                            self.firmwareUpdateFeature.inProgress = false
                            await self.checkFirmwareUpdate()
                        case .fwUpdateNotAvailable(let details):
                            let message = "fwUpdateNotAvailable: \(details)"
                            self.firmwareUpdateFeature.status = message
                            self.showFWUNotification(id: badgeId, title: "Firmware update not available", body: details)
                            self.firmwareUpdateFeature.inProgress = false
                        case .fwUpdateFailed(let details):
                            let message = "fwUpdateFailed: \(details)"
                            self.firmwareUpdateFeature.status = message
                            self.showFWUNotification(id: badgeId, title: "Firmware update failed", body: details)
                            self.firmwareUpdateFeature.inProgress = false
                        }
                    }
                }).takeLast(1)
                    .subscribe(onError: { [weak self] error in
                        Task { @MainActor [weak self] in
                            guard let self = self else { return }
                            self.firmwareUpdateFeature.status = "Error: \(error.localizedDescription)"
                            self.firmwareUpdateFeature.inProgress = false
                            self.updatingDevices.removeAll { $0.deviceId == device.deviceId }
                            self.connectToDevice(withId: device.deviceId)
                        }
                    }, onCompleted: {
                        self.updatingDevices.removeAll { $0.deviceId == device.deviceId }
                    })
                firmwareUpdateStatusSubscription.disposed(by: disposeBag)
            }
        } else {
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                self.firmwareUpdateFeature.status = "No device connected."
                self.firmwareUpdateFeature.inProgress = false
            }
        }
    }
    
    func getSDLogSettings() async {
        if case .connected(let device) = deviceConnectionState {
            api.getSDLogConfiguration(device.deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .success(let config):
                        self.sdLogConfig = SDLogConfig(
                            ppiLogEnabled: config.ppiLogEnabled,
                            accelerationLogEnabled: config.accelerationLogEnabled,
                            caloriesLogEnabled: config.caloriesLogEnabled,
                            gpsLogEnabled: config.gpsLogEnabled,
                            gpsNmeaLogEnabled: config.gpsNmeaLogEnabled,
                            magnetometerLogEnabled: config.magnetometerLogEnabled,
                            tapLogEnabled: config.tapLogEnabled,
                            barometerLogEnabled: config.barometerLogEnabled,
                            gyroscopeLogEnabled: config.gyroscopeLogEnabled,
                            sleepLogEnabled: config.sleepLogEnabled,
                            slopeLogEnabled: config.slopeLogEnabled,
                            ambientLightLogEnabled: config.ambientLightLogEnabled,
                            tlrLogEnabled: config.tlrLogEnabled,
                            ondemandLogEnabled: config.ondemandLogEnabled,
                            capsenseLogEnabled: config.capsenseLogEnabled,
                            fusionLogEnabled: config.fusionLogEnabled,
                            metLogEnabled: config.metLogEnabled,
                            ohrLogEnabled: config.ohrLogEnabled,
                            verticalAccLogEnabled: config.verticalAccLogEnabled,
                            amdLogEnabled: config.amdLogEnabled,
                            skinTemperatureLogEnabled: config.skinTemperatureLogEnabled,
                            compassLogEnabled: config.compassLogEnabled,
                            speed3DLogEnabled: config.speed3DLogEnabled,
                            logTrigger: config.logTrigger,
                            magnetometerFrequency: config.magnetometerFrequency
                        )
                    case .failure(let err):
                        print("Failed to load sensor datalog settings, \(err)")
                    }
                }.disposed(by: disposeBag)
        }
    }

    func getSleep(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let sleepData = try await api.getSleepData(identifier: device.deviceId, fromDate: start, toDate: end).value
                Task {@MainActor in
                    if (!sleepData.isEmpty) {
                        dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss Z"
                        encoder.dateEncodingStrategy = .formatted(dateFormatter)
                        let sleepJson = try encoder.encode(sleepData)
                        self.activityRecordingData.data = String(data: sleepJson, encoding: String.Encoding.utf8)!
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        dateFormatter.dateFormat = "yyyy-MM-dd"
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No sleep data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load sleep analysis result, \(err)")
            }
        }
    }

    func getSteps(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let stepsData = try await api.getSteps(identifier: device.deviceId, fromDate: start, toDate: end).value
                Task {@MainActor in
                    if (!stepsData.isEmpty) {
                        let stepsJson = try encoder.encode(stepsData)
                        self.activityRecordingData.data = String(data: stepsJson, encoding: String.Encoding.utf8)!
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No steps data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load steps, \(err)")
            }
        }
    }
    
    func get247HrSamples(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let calendar = Calendar(identifier: .gregorian)
                let endOfDayOfEnd =
                    calendar.date(byAdding: .day, value: 1, to: end)!
                    .addingTimeInterval(TimeInterval(-Double.leastNonzeroMagnitude))
                let hrSamplesData = try await api.get247HrSamples(identifier: device.deviceId, fromDate: start, toDate: endOfDayOfEnd).value
                Task {@MainActor in
                    if (!hrSamplesData.isEmpty) {
                        let hrSamplesJson = try encoder.encode(hrSamplesData)
                        self.activityRecordingData.data = String(data: hrSamplesJson, encoding: String.Encoding.utf8)!
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No 24/7 HR samples data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load 24/7 HR samples, \(err)")
            }
        }
    }
    
    func get247PPiSamples(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let calendar = Calendar(identifier: .gregorian)
                let endOfDayOfEnd =
                    calendar.date(byAdding: .day, value: 1, to: end)!
                    .addingTimeInterval(TimeInterval(-Double.leastNonzeroMagnitude))
                let ppiSamplesData = try await api.get247PPiSamples(identifier: device.deviceId, fromDate: start, toDate: endOfDayOfEnd).value
                Task {@MainActor in
                    if (!ppiSamplesData.isEmpty) {
                        let ppiSamplesJson = try encoder.encode(ppiSamplesData)
                        self.activityRecordingData.data = String(data: ppiSamplesJson, encoding: String.Encoding.utf8)!
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No 24/7 PPi samples data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load 24/7 PPi samples, \(err)")
            }
        }
    }

    func getNightlyRecharge(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let nightlyRechargeData = try await api.getNightlyRecharge(identifier: device.deviceId, fromDate: start, toDate: end).value
                Task { @MainActor in
                    if !nightlyRechargeData.isEmpty {
                        let nightlyRechargeJson = try encoder.encode(nightlyRechargeData)
                        self.activityRecordingData.data = String(data: nightlyRechargeJson, encoding: .utf8)!
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No nightly recharge data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load nightly recharge data, \(err)")
            }
        }
    }
    
    func getCalories(start: Date, end: Date, caloriesType: CaloriesType) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let caloriesData = try await api.getCalories(
                    identifier: device.deviceId,
                    fromDate: start,
                    toDate: end,
                    caloriesType: caloriesType
                ).value
                
                Task { @MainActor in
                    if !caloriesData.isEmpty {
                        let caloriesJson = try encoder.encode(caloriesData)
                        self.activityRecordingData.data = String(data: caloriesJson, encoding: .utf8) ?? ""
                        self.activityRecordingData.loadingState = .success
                    } else {
                        self.activityRecordingData.loadingState = .failed(
                            error: "No calorie data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))"
                        )
                    }
                }
            } catch let err {
                Task { @MainActor in
                    self.activityRecordingData.loadingState = .failed(error: "Failed to load calorie data, \(err)")
                }
            }
        }
    }

    func getActivitySamplesData(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let activitySamplesData = try await api.getActivitySampleData(identifier: device.deviceId, fromDate: start, toDate: end).value
                Task {@MainActor in
                    if (!activitySamplesData.isEmpty) {
                        let activitySamplesJson = try encoder.encode(activitySamplesData)
                        self.activityRecordingData.data = String(data: activitySamplesJson, encoding: String.Encoding.utf8)!
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No activity samples data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load activity samples data, \(err)")
            }
        }
    }

    func getUserDeviceSettings() async -> PolarUserDeviceSettings.PolarUserDeviceSettingsResult? {
        var settings: PolarUserDeviceSettings.PolarUserDeviceSettingsResult? = nil
        if case .connected(let device) = deviceConnectionState {
            do {
                 settings = try await api.getPolarUserDeviceSettings(identifier: device.deviceId).value
            } catch let err {
                NSLog("Failed to get device user location, \(err)")
            }
        }

        return settings
    }
        
    func setUserDeviceSettings(userDeviceSettings: PolarUserDeviceSettings) async {

        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.setPolarUserDeviceSettings(device.deviceId, polarUserDeviceSettings: userDeviceSettings).value
                Task { @MainActor in
                    self.userDeviceSettings.deviceUserLocation = userDeviceSettings.deviceLocation
                }
            } catch let err {
                NSLog("Failed to set device user location, \(err)")
            }
        }
    }

    func deleteStoredUserData(untilDate: Date, dataType: PolarStoredDataType.StoredDataType) async {

          if case .connected(let device) = deviceConnectionState {
              do {
                  try await api.deleteStoredDeviceData(device.deviceId, dataType: dataType, until: untilDate).value
              } catch let err {
                  NSLog("Failed to delete user data, \(err)")
                  Task { @MainActor in
                      self.somethingFailed(text: "Failed to delete user data: \(err)")
                  }
              }
          }
      }
    
    func deleteDeviceDataFolders(fromDate: Date, toDate: Date) async {
          if case .connected(let device) = deviceConnectionState {
              do {
                  try await api.deleteDeviceDateFolders(device.deviceId, fromDate: fromDate, toDate: toDate).value
              } catch let err {
                  NSLog("Failed to delete date folders: \(err)")
                  Task { @MainActor in
                      self.somethingFailed(text: "Failed to delete data folders: \(err)")
                  }
              }
          }
      }

    func getSkinTemperature(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let skinTemperatureData = try await api.getSkinTemperature(identifier: device.deviceId, fromDate: start, toDate: end).value
                Task { @MainActor in
                    if !skinTemperatureData.isEmpty {
                        let skinTemperatureJson = try encoder.encode(skinTemperatureData)
                        self.activityRecordingData.data = String(data: skinTemperatureJson, encoding: .utf8)!
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No skin temperature data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load skin temperature data, \(err)")
            }
        }
    }
       
    func getSleepRecordingState() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let enabled = try await api.getSleepRecordingState(identifier: device.deviceId).value
                Task {@MainActor in
                    self.sleepRecordingFeature.sleepRecordingEnabledAvailable = true
                    self.sleepRecordingFeature.sleepRecordingEnabled = enabled
                }
            } catch let err {
                NSLog("Failed to get device sleep recording state, \(err)")
            }
        }
    }
    
    func getActiveTimeData(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let calendar = Calendar(identifier: .gregorian)
                let endOfDayOfEnd =
                calendar.date(byAdding: .day, value: 1, to: end)!
                    .addingTimeInterval(TimeInterval(-Double.leastNonzeroMagnitude))
                let activeTimeData = try await api.getActiveTime(identifier: device.deviceId, fromDate: start, toDate: endOfDayOfEnd).value
                Task {@MainActor in
                    if (!activeTimeData.isEmpty) {
                        let activeTimeJson = try encoder.encode(activeTimeData)
                        self.activityRecordingData.data = String(data: activeTimeJson, encoding: .utf8)!
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No active time data data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load active time data, \(err)")
            }
        }
    }

    func observeSleepRecordingState() {
        if case .connected(let device) = deviceConnectionState {
            api.observeSleepRecordingState(identifier: device.deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe { e in
                    switch e {
                    case .next( let enableds ):
                        if let enabled = enableds.last {
                            self.sleepRecordingFeature.sleepRecordingEnabled = enabled
                            self.sleepRecordingFeature.sleepRecordingEnabledAvailable = true
                        }
                    case .error(let err):
                        NSLog("Error in observing sleep recording state: \(err)")
                    case .completed:
                        NSLog("observeSleepRecordingSettings completed")
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func sleepRecordingStop() {
        if case .connected(let device) = deviceConnectionState {
            api.stopSleepRecording(identifier: device.deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe { e in
                    switch e {
                    case .error(let err):
                        NSLog("Error in stopping sleep recording: \(err)")
                    case .completed:
                        NSLog("sleepRecordingStop completed")
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func listTrainingSessions() async {
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.trainingSessionEntries.entries.removeAll()
                self.offlineRecordingEntries.isFetching = true
            }
            NSLog("Start training session listing")
            api.getTrainingSessionReferences(identifier: device.deviceId, fromDate: nil, toDate: nil)
                .observe(on: MainScheduler.instance)
                .do(
                    onDispose: {
                        self.trainingSessionEntries.isFetching = false
                    })
                .subscribe{ e in
                    switch e {
                    case .next(let entry):
                        self.trainingSessionEntries.entries.append(entry)
                    case .error(let err):
                        NSLog("Training session listing error: \(err)")
                    case .completed:
                        NSLog("Training session listing completed")
                    }
                }.disposed(by: disposeBag)
        }
    }
    
    func getTrainingSession(trainingSessionReference: PolarTrainingSessionReference) async {
        if case .connected(let device) = deviceConnectionState {
            Task { @MainActor in
                self.trainingSessionData.loadState = TrainingSessionDataLoadingState.inProgress
            }

            do {
                NSLog("Start training session \(trainingSessionReference.path) fetch")
                let readStartTime = Date()

                let trainingSession: PolarTrainingSession
                trainingSession = try await api.getTrainingSession(
                    identifier: device.deviceId,
                    trainingSessionReference: trainingSessionReference
                ).value

                let sessionSummaryData = try trainingSession.sessionSummary.jsonUTF8Data()
                let sessionSummaryString = String(data: sessionSummaryData, encoding: .utf8) ?? "{}"

                var exerciseJsonObjects: [String] = []
                for exercise in trainingSession.exercises {
                    let summaryString: String
                    if let summaryData = try exercise.exerciseSummary?.jsonUTF8Data(),
                       let str = String(data: summaryData, encoding: .utf8) {
                        summaryString = str
                    } else {
                        summaryString = "{}"
                    }

                    let routeString: String = {
                        if let route = exercise.route,
                           let data = try? route.jsonUTF8Data(),
                           let str = String(data: data, encoding: .utf8) {
                            return str
                        } else if let routeAdv = exercise.routeAdvanced,
                                  let data = try? routeAdv.jsonUTF8Data(),
                                  let str = String(data: data, encoding: .utf8) {
                            return str
                        } else {
                            return "{}"
                        }
                    }()

                    let samplesString: String = {
                        if let samples = exercise.samples,
                           let data = try? samples.jsonUTF8Data(),
                           let str = String(data: data, encoding: .utf8) {
                            return str
                        } else if let samplesAdv = exercise.samplesAdvanced,
                                  let data = try? samplesAdv.jsonUTF8Data(),
                                  let str = String(data: data, encoding: .utf8) {
                            return str
                        } else {
                            return "{}"
                        }
                    }()

                    let exerciseJson = """
                    {
                      "exerciseSummary": \(summaryString),
                      "route": \(routeString),
                      "samples": \(samplesString)
                    }
                    """
                    exerciseJsonObjects.append(exerciseJson)
                }

                let exercisesArrayString = "[\(exerciseJsonObjects.joined(separator: ","))]"

                let jsonString = """
                {
                  "sessionSummary": \(sessionSummaryString),
                  "exercises": \(exercisesArrayString)
                }
                """

                Task { @MainActor in
                    self.trainingSessionData.data = jsonString
                    self.trainingSessionData.loadState = TrainingSessionDataLoadingState.success
                }
            } catch let err {
                NSLog("Training session read failed: \(err)")
                Task { @MainActor in
                    self.trainingSessionData.loadState = TrainingSessionDataLoadingState.failed(
                        error: "Training session read failed: \(err)"
                    )
                }
            }
        }
    }

    func waitForConnection() {
        let deviceId = deviceConnectionState.get().deviceId

        api.waitForConnection(deviceId)
            .observe(on: MainScheduler.instance)
            .subscribe(
                onCompleted: { [weak self] in
                    NSLog("Device connected.")
                    self?.deviceConnected = true
                },
                onError: { error in
                    NSLog("Failed to wait device connection: \(error)")
                }
            )
            .disposed(by: disposeBag)
    }
    
    func setUserDeviceLocation(location: Int) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await withCheckedThrowingContinuation { continuation in
                    _ = api.setUserDeviceLocation(device.deviceId, location: location)
                        .subscribe(
                            onCompleted: { continuation.resume() },
                            onError: { continuation.resume(throwing: $0) }
                        )
                }
            } catch {
                NSLog("Failed to set user device location: \(error.localizedDescription)")
            }
        }
    }

    func setUsbConnectionMode(enabled: Bool) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await withCheckedThrowingContinuation { continuation in
                    _ = api.setUsbConnectionMode(device.deviceId, enabled: enabled)
                        .subscribe(
                            onCompleted: { continuation.resume() },
                            onError: { continuation.resume(throwing: $0) }
                        )
                }
            } catch {
                NSLog("Failed to set USB connection mode: \(error.localizedDescription)")
            }
        }
    }

    func setAutomaticTrainingDetectionSettings(
          mode: Bool,
          sensitivity: Int,
          minimumDuration: Int
    ) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await withCheckedThrowingContinuation { continuation in
                    _ = api.setAutomaticTrainingDetectionSettings(
                        device.deviceId,
                        mode: mode,
                        sensitivity: sensitivity,
                        minimumDuration: minimumDuration
                    )
                    .subscribe(
                        onCompleted: { continuation.resume() },
                        onError: { continuation.resume(throwing: $0) }
                    )
                }
            } catch {
                NSLog("Failed to set automatic training detection settings: \(error.localizedDescription)")
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
            result = "" // PPG is handled in a separate func, ppgDataHeaderString
        case .ppi:
            result = "TIMESTAMP PPI(ms) HR ERROR_ESTIMATE BLOCKER_BIT SKIN_CONTACT_SUPPORT SKIN_CONTACT_STATUS\n"
        case .gyro:
            result =  "TIMESTAMP X(deg/sec) Y(deg/sec) Z(deg/sec)\n"
        case .magnetometer:
            result =  "TIMESTAMP X(Gauss) Y(Gauss) Z(Gauss)\n"
        case .hr:
            result = "HR PPG_QUALITY CORRECTED_HR CONTACT_SUPPORTED CONTACT_STATUS RR_AVAILABLE RR(ms)\n"
        case .temperature:
            result = "TIMESTAMP TEMPERATURE(Celcius)\n"
        case .pressure:
            result = "TIMESTAMP PRESSURE(mBar)\n"
        case .skinTemperature:
            result = "TIMESTAMP SKIN TEMPERATURE(Celcius)\n"
        }
        return result
    }
    
    private func ppgDataHeaderString(_ data: PolarPpgData) -> String {
        var result = ""
        switch data.type {
        case .ppg1:
            result =  "TIMESTAMP SPORT_ID\n"
        case .ppg2:
            result =  "TIMESTAMP PPG0 PPG1 Status\n"
        case .ppg3:
            result =  "TIMESTAMP PPG0 PPG1 PPG2\n"
        case .ppg3_ambient1:
            result =  "TIMESTAMP PPG0 PPG1 PPG2 AMBIENT\n"
        case .ppg17:
            result =  "TIMESTAMP PPG0 PPG1 PPG2 PPG3 PPG4 PPG5 PPG6 PPG7 PPG8 PPG9 PPG10 PPG11 PPG12 PPG13 PPG14 PPG15 STATUS\n"
        case .ppg21:
            result =  "TIMESTAMP GREEN0 GREEN1 GREEN2 GREEN3 GREEN4 GREEN5 GREEN6 GREEN7 RED0 RED1 RED2 RED3 RED4 RED5 IR0 IR1 IR2 IR3 IR4 IR5 STATUS\n"
        default:
            result = "PPG data type not supported"
        }
        return result
    }

    private func dataToString<T>(_ data: T) -> String {
        var result = ""
        switch data {
        case let polarAccData as PolarAccData:
            result += polarAccData.map{ "\($0.timeStamp) \($0.x) \($0.y) \($0.z)" }.joined(separator: "\n")
        case let polarEcgData as PolarEcgData:
            result +=  polarEcgData.map{ "\($0.timeStamp) \($0.voltage)" }.joined(separator: "\n")
        case let polarGyroData as PolarGyroData:
            result +=  polarGyroData.map{ "\($0.timeStamp) \($0.x) \($0.y) \($0.z)" }.joined(separator: "\n")
        case let polarMagnetometerData as PolarMagnetometerData:
            result +=  polarMagnetometerData.map{ "\($0.timeStamp) \($0.x) \($0.y) \($0.z)" }.joined(separator: "\n")
        case let polarPpgData as PolarPpgData:
            if polarPpgData.type == PpgDataType.ppg1 {
                result += polarPpgData.samples.map{ "\($0.timeStamp) \($0.channelSamples[0])" }.joined(separator: "\n")
            }
            if polarPpgData.type == PpgDataType.ppg3 {
                result += polarPpgData.samples.map{ "\($0.timeStamp) \($0.channelSamples[0]) \($0.channelSamples[1]) \($0.channelSamples[2])" }.joined(separator: "\n")
            }
            if polarPpgData.type == PpgDataType.ppg3_ambient1 {
                result += polarPpgData.samples.map{ "\($0.timeStamp) \($0.channelSamples[0]) \($0.channelSamples[1]) \($0.channelSamples[2]) \($0.channelSamples[3])" }.joined(separator: "\n")
            }
            if polarPpgData.type == PpgDataType.ppg21 {
                result += polarPpgData.samples.map{ "\($0.timeStamp) \($0.channelSamples[0]) \($0.channelSamples[1]) \($0.channelSamples[2]) \($0.channelSamples[3]), \($0.channelSamples[4]) \($0.channelSamples[5]) \($0.channelSamples[6]) \($0.channelSamples[7]) \($0.channelSamples[8]) \($0.channelSamples[9]) \($0.channelSamples[10]) \($0.channelSamples[11]) \($0.channelSamples[12]) \($0.channelSamples[13]) \($0.channelSamples[14]) \($0.channelSamples[15]) \($0.channelSamples[16]) \($0.channelSamples[17]) \($0.channelSamples[18]) \($0.channelSamples[19])" }.joined(separator: "\n")
            }
            if polarPpgData.type == PpgDataType.ppg2 {
                result += polarPpgData.samples.map{ "\($0.timeStamp) \($0.channelSamples[0]) \($0.channelSamples[1]) \($0.channelSamples[2])" }.joined(separator: "\n")
            }
        case let polarPpiData as PolarPpiData:
            result += polarPpiData.samples.map{ "\($0.timeStamp) \($0.ppInMs) \($0.hr) \($0.ppErrorEstimate) \($0.blockerBit) \($0.skinContactSupported) \($0.skinContactStatus)" }.joined(separator: "\n")
        case let polarHrData as PolarHrData:
            result += polarHrData.map{ "\($0.hr) \($0.ppgQuality) \($0.correctedHr) \($0.contactStatusSupported) \($0.contactStatus) \($0.rrAvailable) \($0.rrsMs.map { String($0) }.joined(separator: " "))" }.joined(separator: "\n")
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
        var firstRow: Data = "".data(using: .utf8)!
        if (type != PolarDeviceDataType.ppg) {
            firstRow = dataHeaderString(type).data(using: .utf8)!
        }

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

    func setBleMultiConnectionMode(enabled: Bool) async {

        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.setMultiBLEConnectionMode(identifier: deviceId!, enable: enabled).value
                Task { @MainActor in
                    self.multiBleFeature.isEnabled = enabled
                }
            } catch let err {
                NSLog("Failed to set BLE multi connection mode, \(err)")
            }
        }
    }

    func multiBLEModeToggle() {
        if case .connected(let device) = deviceConnectionState {
            if self.multiBleFeature.isEnabled {
                api.setMultiBLEConnectionMode(identifier: deviceId!, enable: false)
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            NSLog("Multi BLE mode is disabled")
                            Task { @MainActor in
                                self.multiBleFeature.isEnabled = false
                            }
                        case .error(let err):
                            self.somethingFailed(text: "Multi BLE mode disable failed: \(err)")
                        }
                    }.disposed(by: disposeBag)
            } else {
                api.setMultiBLEConnectionMode(identifier: deviceId!, enable: true)
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            NSLog("Multi BLE mode is enabled")
                            Task { @MainActor in
                                self.multiBleFeature.isEnabled = true
                            }
                        case .error(let err):
                            self.somethingFailed(text: "Multi BLE mode enable failed: \(err)")
                        }
                    }.disposed(by: disposeBag)
            }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
            Task { @MainActor in
                self.multiBleFeature.isEnabled = false
            }
        }
    }

    func getMultiBleModeStatus() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                NSLog("Get multi BLE mode status")
                let isMultiBleModeEnabled: Bool = try await api.getMultiBLEConnectionMode(identifier: device.deviceId).value
                NSLog("Multi BLE mode currently enabled: \(isMultiBleModeEnabled)")
                Task { @MainActor in
                    self.multiBleFeature.isEnabled = isMultiBleModeEnabled
                    self.multiBleFeature.isSupported = true
                }
            } catch let err {
                Task { @MainActor in
                    let errorMessage = "\(err)"
                    if errorMessage.contains("gattAttributeError") && errorMessage.contains("errorCode: 3") {
                        NSLog("Multi BLE mode not supported by device")
                        self.multiBleFeature.isEnabled = false
                        self.multiBleFeature.isSupported = false
                    }
                }
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
        case .skinTemperature:
            return "SKIN TEMP"
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

// MARK: - PolarBleApiPowerStateObserver
extension PolarBleDeviceManager : PolarBleApiPowerStateObserver {
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
            if polarDeviceInfo.deviceId == self.deviceConnectionState.get().deviceId {
                self.deviceConnectionState = .connecting(polarDeviceInfo)
            }
        }
    }
    
    func deviceConnected(_ device: PolarDeviceInfo) {
        NSLog("DEVICE CONNECTED: \(device)")
        Task { @MainActor in
            self.disconnectedDevicesPairingErrors.removeValue(forKey: device.deviceId)
            self.updateStateWhenDeviceConnected(device: device)
        }
    }
    
    func deviceDisconnected(_ device: PolarDeviceInfo, pairingError: Bool) {
        NSLog("DISCONNECTED: \(device)")
        Task { @MainActor in
            self.updateStateWhenDeviceDisconnected(withId: device.deviceId, pairingError: pairingError)
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
    
    func batteryChargingStatusReceived(_ identifier: String, chargingStatus: BleBasClient.ChargeState) {
        NSLog("battery charging status updated: \(chargingStatus)")
        Task { @MainActor in
            self.batteryStatusFeature.chargeState = chargingStatus
        }
    }
    
    func batteryPowerSourcesStateReceived(_ identifier: String, powerSourcesState: BleBasClient.PowerSourcesState) {
        NSLog("battery power sources status updated: \(powerSourcesState)")
        Task { @MainActor in
            self.batteryStatusFeature.powerSourcesState = powerSourcesState
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
            Task { @MainActor in
                self.onlineStreamingFeature.isSupported = true
            }

            api.getAvailableHRServiceDataTypes(identifier: identifier)
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
    
        case .feature_polar_led_animation:
            Task { @MainActor in
                self.ledAnimationFeature.isSupported = true
            }
            Task {
                await getSdkModeStatus()
            }
            break
        case .feature_polar_firmware_update:
            Task { @MainActor in
                self.firmwareUpdateFeature.isSupported = true
                self.checkFirmwareUpdateFeature.isSupported = true
            }
        case .feature_polar_activity_data:
            // Not implemented
            print("Not implemented")
        case .feature_polar_features_configuration_service:
                // Not implemented
                print("Not implemented")
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

// MARK: - Log Management
private var logFileURL: URL?
private var logFileHandle: FileHandle?

private func getAppLogsFile() -> URL? {
    let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    let logFileURL = documentsDirectory.appendingPathComponent("PSDCAppLogs.txt")

    if !FileManager.default.fileExists(atPath: logFileURL.path) {
        let logContent = "iOS PSDC app logs:\n"
        do {
            try logContent.write(to: logFileURL, atomically: true, encoding: .utf8)
            NSLog("Log file created at: %@", logFileURL.absoluteString)
        } catch {
            NSLog("Error writing log file: %@", error.localizedDescription)
            return nil
        }
    } else {
        NSLog("Log file already exists at: %@", logFileURL.absoluteString)
    }
    
    return logFileURL
}

private func openLogFile(logFileURL: URL) -> FileHandle? {
    guard !logFileURL.absoluteString.isEmpty else {
        NSLog("Error: Log file URL is nil or empty.")
        return nil
    }
    
    do {
        let fileHandle = try FileHandle(forWritingTo: logFileURL)
        NSLog("Successfully opened log file handle: %@", logFileURL.absoluteString)
        return fileHandle
    } catch {
        NSLog("Error opening log file for writing: %@", error.localizedDescription)
        do {
            let fileHandle = try FileHandle(forWritingTo: logFileURL)
            NSLog("Created a new log file at: %@", logFileURL.absoluteString)
            return fileHandle
        } catch {
            NSLog("Error opening or creating log file: %@", error.localizedDescription)
            return nil
        }
    }
}

private func logToFile(_ message: String) {
    if let logFileHandle = logFileHandle {
        if let data = (message + "\n").data(using: .utf8) {
            logFileHandle.seekToEndOfFile()
            logFileHandle.write(data)
            logFileHandle.synchronizeFile()
        } else {
            NSLog("Error: Could not convert message to data.")
        }
    } else {
        if let logFileURL = logFileURL {
            logFileHandle = openLogFile(logFileURL: logFileURL)
            if logFileHandle != nil {
                logToFile(message)
            } else {
                NSLog("Error: Failed to open log file.")
            }
        }
    }
}

// MARK: - PolarBleApiLogger
extension PolarBleSdkManager : PolarBleApiLogger {
    func message(_ str: String) {
        let timestamp = Date.now
        NSLog("\(timestamp) Polar SDK log:  \(str) [SdkMgr-\(deviceId ?? "no-device")]")
        logToFile("\(timestamp) Polar SDK log:  \(str) [SdkMgr-\(deviceId ?? "no-device")]")
    }
}

// Mark: - iOS local notifications
extension PolarBleSdkManager {
    private func showFWUNotification(id: String, title: String, body: String) {
        Task { @MainActor in
            do {
                if try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert]) == true {
                    let content = UNMutableNotificationContent()
                    content.title = title
                    content.body = body
                    try await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
                }
            } catch let err {
                // Ignore
                BleLogger.trace("\(err)")
            }
        }
    }
}
