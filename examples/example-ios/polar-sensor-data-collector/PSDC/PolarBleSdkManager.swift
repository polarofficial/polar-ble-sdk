/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import Combine
import CoreBluetooth
import UserNotifications

/// PolarBleSdkManager demonstrates how to user PolarBleSDK API to access data and control devices
/// PSDC Uses separate managers per device connection to support multiple simultaneous connections
///
/// See PolarBleDeviceManager for scanning connectable devices
///
@MainActor
class PolarBleSdkManager : ObservableObject {
    
    // NOTICE this example requests all available features. To initialize
    // only selected SDK features, list the features in features array, e.g.:
    // features: [.feature_hr,.feature_polar_device_control]
    private var api: PolarBleApi
    
    var connectedDevices: [PolarDeviceInfo] = []
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
    
    @Published var offlineExerciseV2Feature = OfflineExerciseV2Feature()

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
    
    @Published var genericApiFileList: [String] = []
    @Published var genericApiFileData: Data = Data()
    @Published var lastHrBroadcastData: PolarHrBroadcastData? = nil
    @Published var hrBroadcastUpdateCount: Int = 0

    private var exerciseRefreshTimer: DispatchSourceTimer?
    private var broadcastTask: Task<Void, Never>?
    private var autoConnectTask: Task<Void, Never>?
    private var onlineStreamingTasks: [PolarDeviceDataType: Task<Void, Never>] = [:]
    private var exerciseNotificationTask: Task<Void, Never>?
    private var sleepObserverTask: Task<Void, Never>?
    
    @Published var isObservingDeviceToHostNotifications: Bool = false
    private var deviceToHostNotificationTask: Task<Void, Never>?
    @Published var deviceToHostNotificationInfo: (String, PolarDeviceToHostNotification, Data, Any?)?
    
    @Published var batteryChargeLevel = -1
    @Published var deviceChargeStatus = BleBasClient.ChargeState.unknown
    @Published var rssi: Any = "N/A"
    @Published var didDisconnect = false
    
    @Published var offlineExerciseV2Supported: Bool = false
    @Published var offlineExerciseV2Entries: [PolarExerciseEntry] = []
    @Published var offlineExerciseV2Status: Bool = false
    
    @Published var fileTransferFeature = FileTransferFeature()
    @Published var activityDataFeature = ActivityDataFeature()
    @Published var watchFaceFeature = WatchFaceFeature()

    private var h10ExerciseEntry: PolarExerciseEntry?
    
    private var searchDevicesTask: Task<Void, Never>? = nil
    
    private let encoder = JSONEncoder()
    private let dateFormatter = DateFormatter()

    @Published var elapsedTimeToast: String? = nil
    var logFileHandle: FileHandle? = nil
    
    /// Initialiser used by PolarBleDeviceManager — shares the single api instance so that
    /// peripherals discovered during scanning are in the same session map as connectToDevice.
    init(api: PolarBleApi) {
        self.api = api
        self.isBluetoothOn = false
        self.userDeviceSettings = UserDeviceSettingsFeature(deviceUserLocation: PolarUserDeviceSettings.DeviceLocation.UNDEFINED)
        if let logFileURL = getAppLogsFile() {
            logFileHandle = openLogFile(logFileURL: logFileURL)
        }
        self.api.polarFilter(true)
        self.api.observer = self
        self.api.deviceFeaturesObserver = self
        self.api.powerStateObserver = self
        self.api.deviceInfoObserver = self
        self.api.logger = self
    }

    /// Standalone initialiser kept for SwiftUI Previews and legacy call sites.
    init(restoreIdentifier: String? = nil) {
        self.api = PolarBleApiDefaultImpl.polarImplementation(
            DispatchQueue.main,
            features: [],
            restoreIdentifier: restoreIdentifier ?? "com.polar.PolarSensorDataCollector-iOS.preview"
        )
        self.isBluetoothOn = false
        self.userDeviceSettings = UserDeviceSettingsFeature(deviceUserLocation: PolarUserDeviceSettings.DeviceLocation.UNDEFINED)
        if let logFileURL = getAppLogsFile() {
            logFileHandle = openLogFile(logFileURL: logFileURL)
        }
        self.api.polarFilter(true)
        self.api.observer = self
        self.api.deviceFeaturesObserver = self
        self.api.powerStateObserver = self
        self.api.deviceInfoObserver = self
        self.api.logger = self
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
                if (connectedDevice.deviceId == deviceId) {
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
        connectedDevices.insert(device, at:0)
        self.deviceConnectionState = DeviceConnectionState.connected(device)
        self.updateDisplayedConnectedDevices(with: device)
    }
    
    
    func disconnectFromDevice(device : PolarDeviceInfo) {
        do {
            guard connectedDevices.contains(where: { $0.deviceId == device.deviceId }) else {
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
        
        if let index = connectedDevices.firstIndex(where: { $0.deviceId == deviceId }) {
            connectedDevices.remove(at: index)
            disconnectedDevicesPairingErrors[deviceId] = pairingError
        }
        
        if pairingError && generalMessage == nil {
            Task { @MainActor in
                self.generalMessage = Message(text: "Pairing error for \(deviceId). Remove previous Bluetooth pairing from phone and from sensor/watch to enable pairing again, restart app, and retry connecting.")
            }
        }
        self.generalMessage = nil
        
        let wasPresent = connectedDevices.contains(where: { $0.deviceId == deviceId })
        connectedDevices.removeAll(where: { $0.deviceId == deviceId })  // ← removeAll instead of firstIndex+remove

        if wasPresent {
            disconnectedDevicesPairingErrors[deviceId] = pairingError
        }
        updateDisplayedConnectedDevices()
    }
    
    private func updateDisplayedConnectedDevices(with device: PolarDeviceInfo? = nil) {
        if (connectedDevices.isEmpty) {
            connectedDevicesText = "No device connected"
            deviceConnectionState = DeviceConnectionState.noDevice(nullPolarDeviceInfo)
        } else {
            let selectedDevice = (connectedDevices.first(where: { $0.deviceId == device?.deviceId }) ?? connectedDevices.first!)
            connectedDevicesText = "Connected: \(selectedDevice.name)"
            deviceConnectionState = DeviceConnectionState.connected(selectedDevice)
        }
        
        self.switchableDevices = self.connectedDevices.compactMap { device in
            return device.deviceId != self.deviceId ? device : nil
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
        autoConnectTask?.cancel()
        autoConnectTask = Task {
            do {
                try await api.startAutoConnectToDevice(-55, service: nil, polarDeviceType: nil)
                NSLog("auto connect search complete")
            } catch {
                NSLog("auto connect failed: \(error)")
            }
        }
    }
}

extension PolarBleSdkManager {
    
    func getOnlineStreamSettings(feature: PolarBleSdk.PolarDeviceDataType) {
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("Online stream settings request failed. Device is not connected \(deviceConnectionState)")
            return
        }
        NSLog("Online stream settings fetch for \(feature)")
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let settings = try await api.requestStreamSettings(device.deviceId, feature: feature)
                NSLog("Online stream settings fetch completed for \(feature)")
                var receivedSettings: [TypeSetting] = []
                for setting in settings.settings {
                    var values: [Int] = []
                    for settingsValue in setting.value { values.append(Int(settingsValue)) }
                    receivedSettings.append(TypeSetting(type: setting.key, values: values))
                }
                onlineStreamSettings = RecordingSettings(feature: feature, settings: receivedSettings)
            } catch {
                somethingFailed(text: "Online stream settings request failed: \(error)")
                onlineStreamSettings = nil
            }
        }
    }
    
    func getOfflineRecordingSettings(feature: PolarBleSdk.PolarDeviceDataType) {
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("Offline recording settings request failed. Device is not connected \(deviceConnectionState)")
            return
        }
        NSLog("Offline recording settings fetch for \(feature)")
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let settings = try await api.requestOfflineRecordingSettings(device.deviceId, feature: feature)
                NSLog("Offline recording settings fetch completed for \(feature)")
                var receivedSettings: [TypeSetting] = []
                for setting in settings.settings {
                    var values: [Int] = []
                    for settingsValue in setting.value { values.append(Int(settingsValue)) }
                    receivedSettings.append(TypeSetting(type: setting.key, values: values))
                }
                offlineRecordingSettings = RecordingSettings(feature: feature, settings: receivedSettings)
            } catch {
                somethingFailed(text: "Offline recording settings request failed: \(error)")
                onlineStreamSettings = nil
            }
        }
    }
    
    func offlineRecordingSettings(for feature: PolarDeviceDataType) -> RecordingSettings? {
        return offlineRecordingSettingsMap[feature]
    }
    
    func getOfflineRecordingTriggerSetup() async throws -> PolarOfflineRecordingTrigger {
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("Offline recording trigger setup request failed. Device is not connected \(deviceConnectionState)")
            throw PolarErrors.deviceNotConnected
        }
        NSLog("Offline recording trigger setup fetch for \(device.deviceId)")
        let triggerSetup = try await api.getOfflineRecordingTriggerSetup(device.deviceId)
        NSLog("Offline recording trigger setup fetch completed for \(device.deviceId)")
        return triggerSetup
    }
    
    func selectSportProfile(_ sport: PolarExerciseSession.SportProfile) {
        exerciseState.selectedSport = sport
    }

    func refreshExerciseStatus() async {
        guard case .connected(let device) = deviceConnectionState else { return }
        await MainActor.run { self.exerciseState.isRefreshing = true }
        do {
            let info = try await api.getExerciseStatus(identifier: device.deviceId)
            await MainActor.run {
                self.exerciseState.apply(status: info.status, sport: info.sportProfile, startTime: info.startTime)
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
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await api.startExercise(identifier: device.deviceId, profile: exerciseState.selectedSport)
                let info = try await api.getExerciseStatus(identifier: device.deviceId)
                exerciseState.apply(status: info.status, sport: info.sportProfile, startTime: info.startTime)
                NSLog("Start exercise succeeded for \(device.deviceId)")
            } catch {
                NSLog("Start exercise failed for \(device.deviceId): \(error)")
            }
        }
    }

    func pauseExercise() {
        guard case .connected(let device) = deviceConnectionState else { return }
        NSLog("Pause exercise pressed for \(device.deviceId)")
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await api.pauseExercise(identifier: device.deviceId)
                let info = try await api.getExerciseStatus(identifier: device.deviceId)
                exerciseState.apply(status: info.status, sport: info.sportProfile, startTime: info.startTime)
                NSLog("Pause exercise succeeded for \(device.deviceId)")
            } catch {
                NSLog("Pause exercise failed for \(device.deviceId): \(error)")
            }
        }
    }

    func resumeExercise() {
        guard case .connected(let device) = deviceConnectionState else { return }
        NSLog("Resume exercise pressed for \(device.deviceId)")
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await api.resumeExercise(identifier: device.deviceId)
                let info = try await api.getExerciseStatus(identifier: device.deviceId)
                exerciseState.apply(status: info.status, sport: info.sportProfile, startTime: info.startTime)
                NSLog("Resume exercise succeeded for \(device.deviceId)")
            } catch {
                NSLog("Resume exercise failed for \(device.deviceId): \(error)")
            }
        }
    }

    func stopExercise() {
        guard case .connected(let device) = deviceConnectionState else { return }
        NSLog("Stop exercise pressed for \(device.deviceId)")
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await api.stopExercise(identifier: device.deviceId)
                let info = try await api.getExerciseStatus(identifier: device.deviceId)
                exerciseState.apply(status: info.status, sport: info.sportProfile, startTime: info.startTime)
                NSLog("Stop exercise succeeded for \(device.deviceId)")
            } catch {
                NSLog("Stop exercise failed for \(device.deviceId): \(error)")
            }
        }
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
    ) async throws {
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("Setting offline recording trigger request failed. Device is not connected \(deviceConnectionState)")
            throw PolarErrors.deviceNotConnected
        }
        let identifier = device.deviceId
        NSLog("Setting offline recording trigger for \(identifier)")
        try await api.setOfflineRecordingTrigger(identifier, trigger: trigger, secret: secret)
        NSLog("Offline recording trigger set successfully for \(identifier)")
    }

    func getOfflineRecordingTriggerSettings(feature: PolarDeviceDataType) async throws {
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("Device is not connected. Cannot fetch offline recording settings.")
            throw PolarErrors.deviceNotConnected
        }
        NSLog("Offline recording trigger setup fetch for \(device.deviceId)")
        do {
            let settings = try await api.requestOfflineRecordingSettings(device.deviceId, feature: feature)
            BleLogger.trace("Offline recording settings fetched for feature: \(feature)")
            let recordingSettings = convertToRecordingSettings(feature: feature, sensorSettings: settings)
            offlineRecordingSettingsMap[feature] = recordingSettings
        } catch {
            BleLogger.error("Failed to fetch offline recording settings for feature \(feature): \(error)")
            throw error
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
        onlineStreamingTasks[feature]?.cancel()
        onlineStreamingTasks.removeValue(forKey: feature)
        
        if feature == .hr {
            HrDataHolder.shared.clear()
        }
        if feature == .acc {
            AccDataHolder.shared.clear()
        }
    }
    
    func listOfflineRecordings() async {
        guard case .connected(let device) = deviceConnectionState else { return }
        await MainActor.run {
            offlineRecordingEntries.entries.removeAll()
            offlineRecordingEntries.isFetching = true
        }
        NSLog("Start offline recording listing")
        do {
            for try await entry in api.listOfflineRecordings(device.deviceId) {
                await MainActor.run { offlineRecordingEntries.entries.append(entry) }
            }
            NSLog("Offline recording listing completed")
        } catch {
            NSLog("Offline recording listing error: \(error)")
        }
        await MainActor.run { offlineRecordingEntries.isFetching = false }
    }
    
    func getOfflineRecordingStatus() async {
        guard case .connected(let device) = deviceConnectionState else { return }
        NSLog("getOfflineRecordingStatus")
        do {
            let offlineRecStatus = try await api.getOfflineRecordingStatus(device.deviceId)
            NSLog("Enabled offline rec features \(offlineRecStatus)")
            await MainActor.run { offlineRecordingFeature.isRecording = offlineRecStatus }
        } catch {
            NSLog("Failed to get status of offline recording \(error)")
        }
    }
    
    func removeOfflineRecording(offlineRecordingEntry: PolarOfflineRecordingEntry) {
        guard case .connected(let device) = deviceConnectionState else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
            return
        }
        NSLog("start offline recording removal")
        Task {
            do {
                try await api.removeOfflineRecord(device.deviceId, entry: offlineRecordingEntry)
                NSLog("offline recording removal completed")
                await MainActor.run { offlineRecordingEntries.entries.removeAll{$0 == offlineRecordingEntry} }
            } catch {
                NSLog("offline recording remove failed: \(error)")
            }
        }
    }
    
    func getOfflineRecording(offlineRecordingEntry: PolarOfflineRecordingEntry) async {
        if case .connected(let device) = deviceConnectionState {
            await MainActor.run {
                self.offlineRecordingData.loadState = OfflineRecordingDataLoadingState.inProgress
                self.offlineRecordingData.progress = PolarOfflineRecordingProgress(
                    bytesDownloaded: 0,
                    totalBytes: Int64(offlineRecordingEntry.size),
                    progressPercent: 0
                )
            }
            
            do {
                NSLog("start offline recording \(offlineRecordingEntry.path) fetch")
                let readStartTime = Date()

                for try await result in api.getOfflineRecordWithProgress(device.deviceId, entry: offlineRecordingEntry, secret: nil) {
                    switch result {
                    case .progress(let progress):
                        await MainActor.run {
                            self.offlineRecordingData.progress = PolarOfflineRecordingProgress(
                                bytesDownloaded: progress.bytesDownloaded,
                                totalBytes: progress.totalBytes,
                                progressPercent: progress.progressPercent
                            )
                        }
                        
                    case .complete(let offlineRecording):
                        let elapsedTime = Date().timeIntervalSince(readStartTime)
                        
                        switch offlineRecording {
                        case .accOfflineRecordingData(let data, let startTime, let settings):
                            NSLog("ACC data received")
                            await MainActor.run {
                                self.offlineRecordingData.startTime = startTime
                                self.offlineRecordingData.usedSettings = settings
                                self.offlineRecordingData.data = dataHeaderString(.acc) + dataToString(data)
                                self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                                self.offlineRecordingData.downLoadTime = elapsedTime
                            }
                        case .gyroOfflineRecordingData(let data, startTime: let startTime, settings: let settings):
                            NSLog("GYR data received")
                            await MainActor.run {
                                self.offlineRecordingData.startTime = startTime
                                self.offlineRecordingData.usedSettings = settings
                                self.offlineRecordingData.data = dataHeaderString(.gyro) + dataToString(data)
                                self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                                self.offlineRecordingData.downLoadTime = elapsedTime
                            }
                        case .magOfflineRecordingData(let data, startTime: let startTime, settings: let settings):
                            NSLog("MAG data received")
                            await MainActor.run {
                                self.offlineRecordingData.startTime = startTime
                                self.offlineRecordingData.usedSettings = settings
                                self.offlineRecordingData.data = dataHeaderString(.magnetometer) + dataToString(data)
                                self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                                self.offlineRecordingData.downLoadTime = elapsedTime
                            }
                        case .ppgOfflineRecordingData(let data, startTime: let startTime, settings: let settings):
                            NSLog("PPG data received")
                            await MainActor.run {
                                self.offlineRecordingData.startTime = startTime
                                self.offlineRecordingData.usedSettings = settings
                                self.offlineRecordingData.data = ppgDataHeaderString(data) + dataToString(data)
                                self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                                self.offlineRecordingData.downLoadTime = elapsedTime
                            }
                        case .ppiOfflineRecordingData(let data, startTime: let startTime):
                            NSLog("PPI data received")
                            await MainActor.run {
                                self.offlineRecordingData.startTime = startTime
                                self.offlineRecordingData.usedSettings = nil
                                self.offlineRecordingData.data = dataHeaderString(.ppi) + dataToString(data)
                                self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                                self.offlineRecordingData.downLoadTime = elapsedTime
                            }
                        case .hrOfflineRecordingData(let data, startTime: let startTime):
                            NSLog("HR data received")
                            await MainActor.run {
                                self.offlineRecordingData.startTime = startTime
                                self.offlineRecordingData.usedSettings = nil
                                self.offlineRecordingData.data = dataHeaderString(.hr) + dataToString(data)
                                self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                                self.offlineRecordingData.downLoadTime = elapsedTime
                            }
                        case .temperatureOfflineRecordingData(let data, startTime: let startTime):
                            NSLog("Temperature data received")
                            await MainActor.run {
                                self.offlineRecordingData.startTime = startTime
                                self.offlineRecordingData.usedSettings = nil
                                self.offlineRecordingData.data = dataHeaderString(.temperature) + dataToString(data)
                                self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                                self.offlineRecordingData.downLoadTime = elapsedTime
                            }
                        case .skinTemperatureOfflineRecordingData(let data, startTime: let startTime):
                            NSLog("Skin temperature data received")
                            await MainActor.run {
                                self.offlineRecordingData.startTime = startTime
                                self.offlineRecordingData.usedSettings = nil
                                self.offlineRecordingData.data = dataHeaderString(.skinTemperature) + dataToString(data)
                                self.offlineRecordingData.dataSize = offlineRecordingEntry.size
                                self.offlineRecordingData.downLoadTime = elapsedTime
                            }
                        case .emptyData(startTime: let startTime):
                            await MainActor.run {
                                self.offlineRecordingData.startTime = startTime
                            }
                        }
                        
                        await MainActor.run {
                            self.offlineRecordingData.loadState = OfflineRecordingDataLoadingState.success
                        }
                    }
                }
            } catch let err {
                NSLog("offline recording read failed: \(err)")
                await MainActor.run {
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
                let settings = try PolarSensorSetting(polarSensorSettings)
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    do {
                        try await api.startOfflineRecording(device.deviceId, feature: feature, settings: settings, secret: nil)
                        offlineRecordingFeature.isRecording[feature] = true
                        NSLog("offline recording \(feature) successfully started")
                    } catch {
                        NSLog("failed to start offline recording \(feature). Reason: \(error)")
                    }
                }
            } catch {
                somethingFailed(text: "Settings validation failed for datatype \(feature.displayName).")
            }
        } else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
        }
    }
    
    func offlineRecordingStop(feature: PolarDeviceDataType) {
        guard case .connected(let device) = deviceConnectionState else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
            return
        }
        NSLog("Request offline recording \(feature) stop")
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await api.stopOfflineRecording(device.deviceId, feature: feature)
                offlineRecordingFeature.isRecording[feature] = false
                NSLog("offline recording \(feature) successfully stopped")
            } catch {
                NSLog("failed to stop offline recording \(feature). Reason: \(error)")
                offlineRecordingFeature.isRecording[feature] = false
            }
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
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("Device is not connected \(deviceConnectionState)")
            return
        }
        Task { @MainActor in
            self.onlineStreamingFeature.isStreaming[.ecg] = OnlineStreamingState.inProgress
        }
        let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .ecg)
        onlineStreamingTasks[.ecg] = Task {
            defer {
                if let fileHandle = logFile?.fileHandle {
                    self.closeOnlineStreamLogFile(fileHandle)
                }
                Task { @MainActor in
                    self.onlineStreamingFeature.isStreaming[.ecg] = OnlineStreamingState.success(url: logFile?.url)
                }
            }
            do {
                for try await data in api.startEcgStreaming(device.deviceId, settings: settings) {
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
                }
                NSLog("ECG stream completed")
            } catch {
                NSLog("ECG stream failed: \(error)")
                if let fileHandle = logFile?.fileHandle {
                    self.writeErrorOnlineStreamLogFile(fileHandle, error)
                }
            }
        }
    }
    
    func accStreamStart(settings: PolarBleSdk.PolarSensorSetting) {
        if case .connected(let device) = deviceConnectionState {
            
            Task { @MainActor in
                self.onlineStreamingFeature.isStreaming[.acc] = OnlineStreamingState.inProgress
            }
            
            let logFile: (url: URL, fileHandle: FileHandle)? = openOnlineStreamLogFile(type: .acc)
            
            NSLog("ACC stream start: \(device.deviceId)")
            onlineStreamingTasks[.acc] = Task {
                defer {
                    if let fileHandle = logFile?.fileHandle { self.closeOnlineStreamLogFile(fileHandle) }
                    Task { @MainActor in self.onlineStreamingFeature.isStreaming[.acc] = OnlineStreamingState.success(url: logFile?.url) }
                }
                do {
                    for try await data in api.startAccStreaming(device.deviceId, settings: settings) {
                        if let fileHandle = logFile?.fileHandle { self.writeOnlineStreamLogFile(fileHandle, data) }
                        for item in data { NSLog("ACC    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)") }
                        Task { @MainActor in
                            for sample in data {
                                self.accRecordingData.x = data.last!.x
                                self.accRecordingData.y = data.last!.y
                                self.accRecordingData.z = data.last!.z
                                self.accRecordingData.timestamp = data.last!.timeStamp
                                AccDataHolder.shared.updateAcc(x: sample.x, y: sample.y, z: sample.z)
                            }
                        }
                    }
                    NSLog("ACC stream completed")
                } catch {
                    NSLog("ACC stream failed: \(error)")
                    if let fileHandle = logFile?.fileHandle { self.writeErrorOnlineStreamLogFile(fileHandle, error) }
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
            
            onlineStreamingTasks[.magnetometer] = Task {
                defer {
                    if let fileHandle = logFile?.fileHandle { self.closeOnlineStreamLogFile(fileHandle) }
                    Task { @MainActor in self.onlineStreamingFeature.isStreaming[.magnetometer] = OnlineStreamingState.success(url: logFile?.url) }
                }
                do {
                    for try await data in api.startMagnetometerStreaming(device.deviceId, settings: settings) {
                        if let fileHandle = logFile?.fileHandle { self.writeOnlineStreamLogFile(fileHandle, data) }
                        for item in data { NSLog("MAG    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)") }
                        Task { @MainActor in
                            self.magnetometerRecordingData.x = data.last!.x
                            self.magnetometerRecordingData.y = data.last!.y
                            self.magnetometerRecordingData.z = data.last!.z
                            self.magnetometerRecordingData.timestamp = data.last!.timeStamp
                        }
                    }
                    NSLog("MAG stream completed")
                } catch {
                    NSLog("MAG stream failed: \(error)")
                    if let fileHandle = logFile?.fileHandle { self.writeErrorOnlineStreamLogFile(fileHandle, error) }
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
            
            onlineStreamingTasks[.gyro] = Task {
                defer {
                    if let fileHandle = logFile?.fileHandle { self.closeOnlineStreamLogFile(fileHandle) }
                    Task { @MainActor in self.onlineStreamingFeature.isStreaming[.gyro] = OnlineStreamingState.success(url: logFile?.url) }
                }
                do {
                    for try await data in api.startGyroStreaming(device.deviceId, settings: settings) {
                        if let fileHandle = logFile?.fileHandle { self.writeOnlineStreamLogFile(fileHandle, data) }
                        for item in data { NSLog("GYR    x: \(item.x) y: \(item.y) z: \(item.z) timeStamp: \(item.timeStamp)") }
                        Task { @MainActor in
                            self.gyroRecordingData.x = data.last!.x
                            self.gyroRecordingData.y = data.last!.y
                            self.gyroRecordingData.z = data.last!.z
                            self.gyroRecordingData.timestamp = data.last!.timeStamp
                        }
                    }
                    NSLog("GYR stream completed")
                } catch {
                    NSLog("GYR stream failed: \(error)")
                    if let fileHandle = logFile?.fileHandle { self.writeErrorOnlineStreamLogFile(fileHandle, error) }
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
            
            onlineStreamingTasks[.ppg] = Task {
                defer {
                    if let fileHandle = logFile?.fileHandle { self.closeOnlineStreamLogFile(fileHandle) }
                    Task { @MainActor in self.onlineStreamingFeature.isStreaming[.ppg] = OnlineStreamingState.success(url: logFile?.url) }
                }
                do {
                    for try await data in api.startPpgStreaming(device.deviceId, settings: settings) { do {
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
                                NSLog("PPG  ppg0: \(item.channelSamples[0]) ppg1: \(item.channelSamples[1]) status: \(String(describing: item.statusBits)) timeStamp: \(item.timeStamp)")
                            }
                            Task { @MainActor in
                                self.ppgRecordingData.ppg0 = data.samples[0].channelSamples[0]
                                self.ppgRecordingData.ppg1 = data.samples[0].channelSamples[1]
                                self.ppgRecordingData.status = data.samples[0].statusBits
                            }
                        }
                    } }
                    NSLog("PPG stream completed")
                } catch {
                    NSLog("PPG stream failed: \(error)")
                    if let fileHandle = logFile?.fileHandle { self.writeErrorOnlineStreamLogFile(fileHandle, error) }
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
            
            onlineStreamingTasks[.ppi] = Task {
                defer {
                    if let fileHandle = logFile?.fileHandle { self.closeOnlineStreamLogFile(fileHandle) }
                    Task { @MainActor in self.onlineStreamingFeature.isStreaming[.ppi] = OnlineStreamingState.success(url: logFile?.url) }
                }
                do {
                    for try await data in api.startPpiStreaming(device.deviceId) {
                        if let fileHandle = logFile?.fileHandle { self.writeOnlineStreamLogFile(fileHandle, data) }
                        Task { @MainActor in
                            for item in data.samples {
                                NSLog("PPI    TimeStamp: \(item.timeStamp)    PeakToPeak(ms): \(item.ppInMs) sample.blockerBit: \(item.blockerBit)  errorEstimate: \(item.ppErrorEstimate)")
                                self.ppiRecordingData.ppInMs = item.ppInMs
                                self.ppiRecordingData.blockerBit = item.blockerBit
                                self.ppiRecordingData.ppErrorEstimate = item.ppErrorEstimate
                                self.ppiRecordingData.timeStamp = item.timeStamp
                            }
                        }
                    }
                    NSLog("PPI stream completed")
                } catch {
                    NSLog("PPI stream failed: \(error)")
                    if let fileHandle = logFile?.fileHandle { self.writeErrorOnlineStreamLogFile(fileHandle, error) }
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
            
            onlineStreamingTasks[.hr] = Task {
                defer {
                    if let fileHandle = logFile?.fileHandle { self.closeOnlineStreamLogFile(fileHandle) }
                    Task { @MainActor in self.onlineStreamingFeature.isStreaming[.hr] = OnlineStreamingState.success(url: logFile?.url) }
                }
                do {
                    for try await data in api.startHrStreaming(device.deviceId) {
                        if let fileHandle = logFile?.fileHandle { self.writeOnlineStreamLogFile(fileHandle, data) }
                        NSLog("HR    BPM: \(data[0].hr) rrs: \(data[0].rrsMs) rrAvailable: \(data[0].rrAvailable) contact status: \(data[0].contactStatus) contact supported: \(data[0].contactStatusSupported)")
                        Task { @MainActor in
                            self.hrRecordingData.hr = data[0].hr
                            self.hrRecordingData.rrs = data[0].rrsMs
                            self.hrRecordingData.rrAvailable = data[0].rrAvailable
                            self.hrRecordingData.contactStatus = data[0].contactStatus
                            self.hrRecordingData.contactStatusSupported = data[0].contactStatusSupported
                            HrDataHolder.shared.updateHr(Int(data[0].hr))
                        }
                    }
                    NSLog("Hr stream completed")
                } catch {
                    NSLog("Hr stream failed: \(error)")
                    if let fileHandle = logFile?.fileHandle { self.writeErrorOnlineStreamLogFile(fileHandle, error) }
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
            
            onlineStreamingTasks[.temperature] = Task {
                defer {
                    if let fileHandle = logFile?.fileHandle { self.closeOnlineStreamLogFile(fileHandle) }
                    Task { @MainActor in self.onlineStreamingFeature.isStreaming[.temperature] = OnlineStreamingState.success(url: logFile?.url) }
                }
                do {
                    for try await data in api.startTemperatureStreaming(device.deviceId, settings: settings) {
                        if let fileHandle = logFile?.fileHandle { self.writeOnlineStreamLogFile(fileHandle, data) }
                        Task { @MainActor in
                            for item in data.samples {
                                NSLog("TEMP    temp: \(item.temperature) timestamp: \(item.timeStamp)")
                                self.temperatureRecordingData.temperature = item.temperature
                                self.temperatureRecordingData.timestamp = item.timeStamp
                            }
                        }
                    }
                    NSLog("TEMP stream completed")
                } catch {
                    NSLog("TEMP stream failed: \(error)")
                    if let fileHandle = logFile?.fileHandle { self.writeErrorOnlineStreamLogFile(fileHandle, error) }
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
            
            onlineStreamingTasks[.pressure] = Task {
                defer {
                    if let fileHandle = logFile?.fileHandle { self.closeOnlineStreamLogFile(fileHandle) }
                    Task { @MainActor in self.onlineStreamingFeature.isStreaming[.pressure] = OnlineStreamingState.success(url: logFile?.url) }
                }
                do {
                    for try await data in api.startPressureStreaming(device.deviceId, settings: settings) {
                        if let fileHandle = logFile?.fileHandle { self.writeOnlineStreamLogFile(fileHandle, data) }
                        Task { @MainActor in
                            for item in data.samples {
                                NSLog("PRE    pressure: \(item.pressure) timestamp: \(item.timeStamp)")
                                self.pressureRecordingData.pressure = item.pressure
                                self.pressureRecordingData.timestamp = item.timeStamp
                            }
                        }
                    }
                    NSLog("PRE stream completed")
                } catch {
                    NSLog("PRE stream failed: \(error)")
                    if let fileHandle = logFile?.fileHandle { self.writeErrorOnlineStreamLogFile(fileHandle, error) }
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
            
            onlineStreamingTasks[.skinTemperature] = Task {
                defer {
                    if let fileHandle = logFile?.fileHandle { self.closeOnlineStreamLogFile(fileHandle) }
                    Task { @MainActor in self.onlineStreamingFeature.isStreaming[.skinTemperature] = OnlineStreamingState.success(url: logFile?.url) }
                }
                do {
                    for try await data in api.startSkinTemperatureStreaming(device.deviceId, settings: settings) {
                        if let fileHandle = logFile?.fileHandle { self.writeOnlineStreamLogFile(fileHandle, data) }
                        Task { @MainActor in
                            for item in data.samples {
                                NSLog("SKIN TEMP    temp: \(item.temperature) timestamp: \(item.timeStamp)")
                                self.skinTemperatureRecordingData.temperature = item.temperature
                                self.skinTemperatureRecordingData.timestamp = item.timeStamp
                            }
                        }
                    }
                    NSLog("SKIN TEMP stream completed")
                } catch {
                    NSLog("SKIN TEMP stream failed: \(error)")
                    if let fileHandle = logFile?.fileHandle { self.writeErrorOnlineStreamLogFile(fileHandle, error) }
                }
            }
        } else {
            NSLog("Device is not connected \(deviceConnectionState)")
        }
    }
    
    func sdkModeToggle() {
        guard sdkModeFeature.isSupported else { return }
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("Device is not connected \(deviceConnectionState)")
            Task { @MainActor in self.sdkModeFeature.isEnabled = false }
            return
        }
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                if sdkModeFeature.isEnabled {
                    try await api.disableSDKMode(device.deviceId)
                    NSLog("SDK mode disabled")
                    sdkModeFeature.isEnabled = false
                } else {
                    try await api.enableSDKMode(device.deviceId)
                    NSLog("SDK mode enabled")
                    sdkModeFeature.isEnabled = true
                }
            } catch {
                somethingFailed(text: "SDK mode toggle failed: \(error)")
            }
        }
    }
    
    func getSdkModeStatus() async {
        if case .connected(let device) = deviceConnectionState,
           self.sdkModeFeature.isSupported == true,
           self.firmwareUpdateFeature.inProgress == false {
            do {
                NSLog("get SDK mode status")
                let isSdkModeEnabled: Bool = try await api.isSDKModeEnabled(device.deviceId)
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
    
    func getOfflineExerciseV2Status() async {
        guard case .connected(let device) = deviceConnectionState else { return }

        let isReady = api.isFeatureReady(device.deviceId, feature: .feature_polar_offline_exercise_v2)
        offlineExerciseV2Feature.isSupported = isReady
    }

    func listH10Exercises() {
        guard case .connected(let device) = deviceConnectionState else { return }
        h10ExerciseEntry = nil
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                for try await entry in api.listExercises(device.deviceId) {
                    NSLog("entry: \(entry.date.description) path: \(entry.path) id: \(entry.entryId)")
                    h10ExerciseEntry = entry
                }
                NSLog("list exercises completed")
            } catch {
                NSLog("failed to list exercises: \(error)")
            }
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
                
                let data:PolarExerciseData = try await api.fetchExercise(device.deviceId, entry: e)
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
        guard case .connected(let device) = deviceConnectionState else { return }
        guard let entry = h10ExerciseEntry else {
            somethingFailed(text: "No exercise to read, please list the exercises first")
            return
        }
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await api.removeExercise(device.deviceId, entry: entry)
                h10ExerciseEntry = nil
                NSLog("remove completed")
            } catch {
                NSLog("failed to remove exercise: \(error)")
            }
        }
    }
    
    func deleteTrainingSession(reference: PolarTrainingSessionReference) {
        guard case .connected(let device) = deviceConnectionState else {
            somethingFailed(text: "Device is not connected \(deviceConnectionState)")
            return
        }
        NSLog("start training session removal")
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await api.deleteTrainingSession(identifier: device.deviceId, reference: reference)
                NSLog("Training session deleted successfully")
                trainingSessionEntries.entries.removeAll{$0 == reference}
            } catch {
                NSLog("Failed to delete training session: \(error)")
            }
        }
    }

    func h10RecordingToggle() {
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("Device is not connected \(deviceConnectionState)")
            Task { @MainActor in self.h10RecordingFeature.isEnabled = false }
            return
        }
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                if h10RecordingFeature.isEnabled {
                    try await api.stopRecording(device.deviceId)
                    NSLog("recording stopped")
                    h10RecordingFeature.isEnabled = false
                } else {
                    try await api.startRecording(device.deviceId, exerciseId: "TEST_APP_ID", interval: .interval_1s, sampleType: .rr)
                    NSLog("recording started")
                    h10RecordingFeature.isEnabled = true
                }
            } catch {
                somethingFailed(text: "recording toggle failed: \(error)")
            }
        }
    }
    
    func getH10RecordingStatus() {
        guard case .connected(let device) = deviceConnectionState, h10RecordingFeature.isSupported else { return }
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let pair = try await api.requestRecordingStatus(device.deviceId)
                var recordingStatus = "Recording on: \(pair.ongoing)."
                if pair.ongoing {
                    recordingStatus.append(" Recording started with id: \(pair.entryId)")
                    h10RecordingFeature.isEnabled = true
                } else {
                    h10RecordingFeature.isEnabled = false
                }
                NSLog(recordingStatus)
            } catch {
                somethingFailed(text: "H10 recording status request failed: \(error)")
            }
        }
    }
    
    func setTime() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let time = Date()
                let timeZone = TimeZone.current
                
                try await api.setLocalTime(device.deviceId, time: time, zone: timeZone)
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
                let (date, tz): (Date, TimeZone) = try await api.getLocalTimeWithZone(device.deviceId)
                Task { @MainActor in
                    let formatter = DateFormatter()
                    formatter.dateStyle = .short
                    formatter.timeStyle = .medium
                    formatter.timeZone = tz
                    
                    self.generalMessage = Message(
                        text: "\(formatter.string(from: date)) (\(tz.identifier)) read from the device \(device.deviceId)"
                    )
                }
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "time get failed: \(err)")
                }
            }
        } else {
            Task { @MainActor in
                self.somethingFailed(text: "time get failed. No device connected")
            }
        }
    }
    
    func getDiskSpace() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let diskSpace: PolarDiskSpaceData = try await api.getDiskSpace(device.deviceId)
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
                let _: Void = try await api.setLedConfig(device.deviceId, ledConfig: ledConfig)
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
    
    func doRestart() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let _: Void = try await api.doRestart(device.deviceId)
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

    func getWatchFaceConfig() async -> Result<PolarWatchFaceConfig, Error> {
        if case .connected(let device) = deviceConnectionState {
            do {
                let config = try await api.getWatchFaceConfig(device.deviceId)
                return .success(config)
            } catch let err {
                return .failure(err)
            }
        } else {
            return .failure(NSError(domain: "PolarBleSdkManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "No device connected"]))
        }
    }

    func setWatchFaceConfig(config: PolarWatchFaceConfig) async -> Result<Void, Error> {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.setWatchFaceConfig(device.deviceId, config: config)
                return .success(())
            } catch let err {
                return .failure(err)
            }
        } else {
            return .failure(NSError(domain: "PolarBleSdkManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "No device connected"]))
        }
    }

    func doFactoryReset() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let _: Void = try await api.doFactoryReset(device.deviceId)
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
                try await api.doFirstTimeUse(device.deviceId, ftuConfig: ftuConfig)
                
                Task { @MainActor in
                    self.generalMessage = Message(text: "Physical data sent to device: \(device.deviceId)")
                }
                
            } catch let err {
                Task { @MainActor in
                    self.somethingFailed(text: "doPhysicalConfig() error: \(err)")
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
                let ftuDone = try await api.isFtuDone(device.deviceId)
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
                guard let physInfo = try await api.getUserPhysicalConfiguration(device.deviceId) else {
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
                let _: Void = try await api.setWarehouseSleep(device.deviceId)
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
                let _: Void = try await api.turnDeviceOff(device.deviceId)
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
                    try await self.api.setSDLogConfiguration(device.deviceId, logConfiguration: logConfig)
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
                do {
                    for try await status in api.checkFirmwareUpdate(device.deviceId) {
                        await MainActor.run { [weak self] in
                            guard let self else { return }
                            switch status {
                            case .checkFwUpdateAvailable(let version):
                                checkFirmwareUpdateFeature.firmwareVersionAvailable = version
                                checkFirmwareUpdateFeature.status = "checkFwUpdateAvailable: \(version)"
                            case .checkFwUpdateFailed(let details):
                                checkFirmwareUpdateFeature.status = "checkFwUpdateFailed: \(details)"
                            case .checkFwUpdateNotAvailable(let details):
                                checkFirmwareUpdateFeature.status = "checkFwUpdateNotAvailable: \(details)"
                            }
                            checkFirmwareUpdateFeature.inProgress = false
                        }
                    }
                } catch {
                    await MainActor.run { [weak self] in
                        guard let self else { return }
                        checkFirmwareUpdateFeature.status = "Error: \(error.localizedDescription)"
                        checkFirmwareUpdateFeature.inProgress = false
                    }
                }
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
            let badgeId = "FWU-\(deviceId ?? "")"
            let stream = firmwareURL != nil ? api.updateFirmware(device.deviceId, fromFirmwareURL: firmwareURL!) : api.updateFirmware(device.deviceId)
            do {
                for try await status in stream {
                    await MainActor.run { [weak self] in
                        guard let self else { return }
                        switch status {
                        case .fetchingFwUpdatePackage(let details):
                            firmwareUpdateFeature.status = "fetchingFwUpdatePackage: \(details)"
                            showUNUserNotification(id: badgeId, title: "Fetching firmware", body: details)
                            updatingDevices.append(device)
                        case .preparingDeviceForFwUpdate(let details):
                            firmwareUpdateFeature.status = "preparingDeviceForFwUpdate: \(details)"
                            showUNUserNotification(id: badgeId, title: "Preparing device for update", body: details)
                        case .writingFwUpdatePackage(let details):
                            firmwareUpdateFeature.status = "writingFwUpdatePackage: \(details)"
                            showUNUserNotification(id: badgeId, title: "Writing firmware to device", body: details)
                        case .finalizingFwUpdate(let details):
                            firmwareUpdateFeature.status = "finalizingFwUpdate: \(details)"
                            showUNUserNotification(id: badgeId, title: "Finalizing update", body: details)
                        case .fwUpdateCompletedSuccessfully(let details):
                            firmwareUpdateFeature.status = "fwUpdateCompletedSuccessfully: \(details)"
                            showUNUserNotification(id: badgeId, title: "Firmware update complete", body: details)
                            firmwareUpdateFeature.inProgress = false
                        case .fwUpdateNotAvailable(let details):
                            firmwareUpdateFeature.status = "fwUpdateNotAvailable: \(details)"
                            showUNUserNotification(id: badgeId, title: "Firmware update not available", body: details)
                            firmwareUpdateFeature.inProgress = false
                        case .fwUpdateFailed(let details):
                            firmwareUpdateFeature.status = "fwUpdateFailed: \(details)"
                            showUNUserNotification(id: badgeId, title: "Firmware update failed", body: details)
                            firmwareUpdateFeature.inProgress = false
                        }
                    }
                }
                updatingDevices.removeAll { $0.deviceId == device.deviceId }
                await checkFirmwareUpdate()
            } catch {
                await MainActor.run { [weak self] in
                    guard let self else { return }
                    firmwareUpdateFeature.status = "Error: \(error)"
                    firmwareUpdateFeature.inProgress = false
                    updatingDevices.removeAll { $0.deviceId == device.deviceId }
                    connectToDevice(withId: device.deviceId)
                }
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
        guard case .connected(let device) = deviceConnectionState else { return }
        do {
            let config = try await api.getSDLogConfiguration(device.deviceId)
            await MainActor.run {
                sdLogConfig = SDLogConfig(
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
            }
        } catch {
            print("Failed to load sensor datalog settings, \(error)")
        }
    }

    func getSleep(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let sleepData = try await api.getSleep(identifier: device.deviceId, fromDate: start, toDate: end)
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
                let stepsData = try await api.getSteps(identifier: device.deviceId, fromDate: start, toDate: end)
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
                let hrSamplesData = try await api.get247HrSamples(identifier: device.deviceId, fromDate: start, toDate: end)
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
                let ppiSamplesData = try await api.get247PPiSamples(identifier: device.deviceId, fromDate: start, toDate: end)
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
                let nightlyRechargeData = try await api.getNightlyRecharge(identifier: device.deviceId, fromDate: start, toDate: end)
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
                )
                
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
                dateFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let activitySamplesData = try await api.getActivitySampleData(identifier: device.deviceId, fromDate: start, toDate: end)
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

    func getDailySummaryData(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let dailySummaryData = try await api.getDailySummaryData(identifier: device.deviceId, fromDate: start, toDate: end)
                Task {@MainActor in
                    if (!dailySummaryData.isEmpty) {
                        let dailySummaryDataJson = try encoder.encode(dailySummaryData)
                        self.activityRecordingData.data = String(data: dailySummaryDataJson, encoding: String.Encoding.utf8)!
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No daily summary data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load daily summary data, \(err)")
            }
        }
    }

    func getSpo2Test(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                let spo2DateTimeFormatter = DateFormatter()
                spo2DateTimeFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
                spo2DateTimeFormatter.timeZone = TimeZone(identifier: "UTC")
                encoder.dateEncodingStrategy = .millisecondsSince1970
                let spo2Data = try await api.getSpo2TestData(identifier: device.deviceId, fromDate: start, toDate: end)
                Task { @MainActor in
                    if !spo2Data.isEmpty {
                        let spo2Json = try encoder.encode(spo2Data)
                        if let jsonArray = try JSONSerialization.jsonObject(with: spo2Json, options: []) as? [[String: Any]] {
                            let transformed = jsonArray.map { record -> [String: Any] in
                                var entry = record
                                if let dateMs = record["date"] as? Double {
                                    let date = Date(timeIntervalSince1970: dateMs / 1000.0)
                                    entry["testTime"] = spo2DateTimeFormatter.string(from: date)
                                    entry.removeValue(forKey: "date")
                                }
                                return entry
                            }
                            let transformedJson = try JSONSerialization.data(withJSONObject: transformed, options: [])
                            self.activityRecordingData.data = String(data: transformedJson, encoding: .utf8)!
                        } else {
                            self.activityRecordingData.data = String(data: spo2Json, encoding: .utf8)!
                        }
                        self.activityRecordingData.activityType = PolarActivityDataType.SPO2_TEST
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.success
                    } else {
                        self.activityRecordingData.activityType = PolarActivityDataType.SPO2_TEST
                        dateFormatter.dateFormat = "yyyy-MM-dd"
                        self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "No SpO2 test data found for dates \(dateFormatter.string(from: start)) - \(dateFormatter.string(from: end))")
                    }
                }
            } catch let err {
                Task { @MainActor in
                    self.activityRecordingData.activityType = PolarActivityDataType.SPO2_TEST
                    self.activityRecordingData.loadingState = ActivityRecordingDataLoadingState.failed(error: "Failed to load SpO2 test data, \(err)")
                }
            }
        }
    }

    func getUserDeviceSettings() async -> PolarUserDeviceSettings.PolarUserDeviceSettingsResult? {
        var settings: PolarUserDeviceSettings.PolarUserDeviceSettingsResult? = nil
        if case .connected(let device) = deviceConnectionState {
            do {
                 settings = try await api.getPolarUserDeviceSettings(identifier: device.deviceId)
            } catch let err {
                NSLog("Failed to get device user location, \(err)")
            }
        }

        return settings
    }
        
    func setUserDeviceSettings(userDeviceSettings: PolarUserDeviceSettings) async {

        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.setPolarUserDeviceSettings(device.deviceId, polarUserDeviceSettings: userDeviceSettings)
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
                  try await api.deleteStoredDeviceData(device.deviceId, dataType: dataType, until: untilDate)
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
                  try await api.deleteDeviceDateFolders(device.deviceId, fromDate: fromDate, toDate: toDate)
              } catch let err {
                  NSLog("Failed to delete date folders: \(err)")
                  Task { @MainActor in
                      self.somethingFailed(text: "Failed to delete data folders: \(err)")
                  }
              }
          }
    }

    func deleteTelemetryData() async {
        let badgeId = "Settings-\(deviceId ?? "")"
        if case .connected(let device) = deviceConnectionState {
          do {
              try await api.deleteTelemetryData(device.deviceId)
              Task { @MainActor in
                  self.generalMessage = Message(text: "Telemetry data deleted")
              }
          } catch let err {
              NSLog("Failed to delete telemetry data: \(err)")
              Task { @MainActor in
                  self.somethingFailed(text: "Telemetry data deletion failed for device: \(err)")
              }
          }
        }
    }

    func getSkinTemperature(start: Date, end: Date) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
                encoder.dateEncodingStrategy = .formatted(dateFormatter)
                let skinTemperatureData = try await api.getSkinTemperature(identifier: device.deviceId, fromDate: start, toDate: end)
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
                let enabled = try await api.getSleepRecordingState(identifier: device.deviceId)
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
                let activeTimeData = try await api.getActiveTime(identifier: device.deviceId, fromDate: start, toDate: endOfDayOfEnd)
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
        guard case .connected(let device) = deviceConnectionState else { return }
        sleepObserverTask?.cancel()
        sleepObserverTask = Task { [weak self] in
            guard let self else { return }
            do {
                for try await enableds in api.observeSleepRecordingState(identifier: device.deviceId) {
                    if let enabled = enableds.last {
                        await MainActor.run {
                            self.sleepRecordingFeature.sleepRecordingEnabled = enabled
                            self.sleepRecordingFeature.sleepRecordingEnabledAvailable = true
                        }
                    }
                }
                NSLog("observeSleepRecordingSettings completed")
            } catch {
                NSLog("Error in observing sleep recording state: \(error)")
            }
        }
    }
    
    func sleepRecordingStop() {
        guard case .connected(let device) = deviceConnectionState else { return }
        Task {
            do {
                try await api.stopSleepRecording(identifier: device.deviceId)
                NSLog("sleepRecordingStop completed")
            } catch {
                NSLog("Error in stopping sleep recording: \(error)")
            }
        }
    }
    
    func listTrainingSessions() async {
        guard case .connected(let device) = deviceConnectionState else { return }
        await MainActor.run {
            trainingSessionEntries.entries.removeAll()
            trainingSessionEntries.isFetching = true
        }
        NSLog("Start training session listing")
        do {
            let refs1 = try await api.getTrainingSessionReferences(identifier: device.deviceId, fromDate: nil, toDate: nil)
            for entry in refs1 {
                await MainActor.run { trainingSessionEntries.entries.append(entry) }
            }
            NSLog("Training session listing completed")
        } catch {
            NSLog("Training session listing error: \(error)")
        }
        await MainActor.run { trainingSessionEntries.isFetching = false }
    }
    
    func listTrainingSessions(start: Date, end: Date) async {
        guard case .connected(let device) = deviceConnectionState else { return }
        await MainActor.run {
            trainingSessionEntries.entries.removeAll()
            trainingSessionEntries.isFetching = true
        }
        NSLog("Start training session listing from start date: \(start) to end date: \(end)")
        do {
            let refs2 = try await api.getTrainingSessionReferences(identifier: device.deviceId, fromDate: start, toDate: end)
            for entry in refs2 {
                await MainActor.run { trainingSessionEntries.entries.append(entry) }
            }
            NSLog("Training session listing completed")
        } catch {
            NSLog("Training session listing error: \(error)")
        }
        await MainActor.run { trainingSessionEntries.isFetching = false }
    }
    
    func getTrainingSession(trainingSessionReference: PolarTrainingSessionReference) async {
        if case .connected(let device) = deviceConnectionState {
            await MainActor.run {
                self.trainingSessionData.progress = PolarTrainingSessionProgress(
                    totalBytes: trainingSessionReference.fileSize,
                    completedBytes: 0,
                    progressPercent: 0
                )
                self.trainingSessionData.loadState = TrainingSessionDataLoadingState.inProgress
            }

            do {
                NSLog("Start training session \(trainingSessionReference.path) fetch")
                let readStartTime = Date()

                let trainingSession = try await api.getTrainingSessionWithProgress(
                    identifier: device.deviceId,
                    trainingSessionReference: trainingSessionReference,
                    progressHandler: { [weak self] progress in
                        NSLog("Training session progress: \(progress.completedBytes)/\(progress.totalBytes) (\(progress.progressPercent)%)")
                        Task { @MainActor [weak self] in
                            self?.trainingSessionData.progress = PolarTrainingSessionProgress(
                                totalBytes: progress.totalBytes,
                                completedBytes: progress.completedBytes,
                                progressPercent: progress.progressPercent
                            )
                        }
                    }
                )
                do { let trainingSession = trainingSession; if true {
                        let elapsedTime = Date().timeIntervalSince(readStartTime)
                        NSLog("Training session received")
                        
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
                        
                        await MainActor.run {
                            self.trainingSessionData.data = jsonString
                            self.trainingSessionData.loadState = TrainingSessionDataLoadingState.success
                        }
                    } }
            } catch let err {
                NSLog("training session read failed: \(err)")
                await MainActor.run {
                    self.trainingSessionData.loadState = TrainingSessionDataLoadingState.failed(error: "training session read failed: \(err)")
                }
            }
        }
    }

    func waitForConnection() {
        let deviceId = deviceConnectionState.get().deviceId
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await api.waitForConnection(deviceId)
                NSLog("Device connected.")
                deviceConnected = true
            } catch {
                NSLog("Failed to wait device connection: \(error)")
            }
        }
    }
    
    func setUserDeviceLocation(location: Int) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.setUserDeviceLocation(device.deviceId, location: location)
            } catch {
                NSLog("Failed to set user device location: \(error.localizedDescription)")
            }
        }
    }

    func setUsbConnectionMode(enabled: Bool) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.setUsbConnectionMode(device.deviceId, enabled: enabled)
            } catch {
                NSLog("Failed to set USB connection mode: \(error.localizedDescription)")
            }
        }
    }
    
    func setTelemetryEnabled(enabled: Bool) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.setTelemetryEnabled(device.deviceId, enabled: enabled)
            } catch {
                NSLog("Failed to set telemetry enabled: \(error.localizedDescription)")
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
                try await api.setAutomaticTrainingDetectionSettings(
                    device.deviceId,
                    mode: mode,
                    sensitivity: sensitivity,
                    minimumDuration: minimumDuration
                )
            } catch {
                NSLog("Failed to set automatic training detection settings: \(error.localizedDescription)")
            }
        }
    }
    
    func setAutomaticOHRMeasurementEnabled(enabled: Bool) async {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.setAutomaticOHRMeasurementEnabled(device.deviceId, enabled: enabled)
            } catch {
                NSLog("Failed to set automatic OHR measurement state: \(error.localizedDescription)")
            }
        }
    }

    func setDaylightSavingTime() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                try await api.setDaylightSavingTime(device.deviceId)
            } catch {
                NSLog("Failed to set daylight saving time: \(error.localizedDescription)")
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
            result =  "TIMESTAMP PPG0 PPG1 STATUS0 STATUS1\n"
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
                result += polarPpgData.samples.map { sample -> String in
                    var line = "\(sample.timeStamp)"
                    if sample.channelSamples.count >= 1 {
                        line += " \(sample.channelSamples[0])"
                    } else {
                        BleLogger.trace("PPG1 sample incomplete - channels: \(sample.channelSamples.count)")
                    }
                    return line
                }.joined(separator: "\n")
            }
            if polarPpgData.type == PpgDataType.ppg3 {
                result += polarPpgData.samples.map { sample -> String in
                    var line = "\(sample.timeStamp)"
                    for i in 0..<min(3, sample.channelSamples.count) {
                        line += " \(sample.channelSamples[i])"
                    }
                    if sample.channelSamples.count < 3 {
                        BleLogger.trace("PPG3 sample incomplete - channels: \(sample.channelSamples.count)")
                    }
                    return line
                }.joined(separator: "\n")
            }
            if polarPpgData.type == PpgDataType.ppg3_ambient1 {
                result += polarPpgData.samples.map { sample -> String in
                    var line = "\(sample.timeStamp)"
                    for i in 0..<min(4, sample.channelSamples.count) {
                        line += " \(sample.channelSamples[i])"
                    }
                    if sample.channelSamples.count < 4 {
                        BleLogger.trace("PPG3_ambient1 sample incomplete - channels: \(sample.channelSamples.count)")
                    }
                    return line
                }.joined(separator: "\n")
            }
            if polarPpgData.type == PpgDataType.ppg21 {
                result += polarPpgData.samples.map { sample -> String in
                    var line = "\(sample.timeStamp)"
                    // Add available channel samples (up to 20)
                    for i in 0..<min(20, sample.channelSamples.count) {
                        line += " \(sample.channelSamples[i])"
                    }
                    // Add available status bits (up to 20)
                    if let statusBits = sample.statusBits {
                        for i in 0..<min(20, statusBits.count) {
                            line += " \(statusBits[i])"
                        }
                    }
                    if sample.channelSamples.count < 20 || sample.statusBits == nil || sample.statusBits!.count < 20 {
                        BleLogger.trace("PPG21 sample incomplete - channels: \(sample.channelSamples.count), statusBits: \(sample.statusBits?.count ?? 0)")
                    }
                    return line
                }.joined(separator: "\n")
            }
            if polarPpgData.type == PpgDataType.ppg2 {
                result += polarPpgData.samples.map { sample -> String in
                    var line = "\(sample.timeStamp)"
                    // Add available channel samples (up to 2)
                    for i in 0..<min(2, sample.channelSamples.count) {
                        line += " \(sample.channelSamples[i])"
                    }
                    // Add available status bits (up to 2)
                    if let statusBits = sample.statusBits {
                        for i in 0..<min(2, statusBits.count) {
                            line += " \(statusBits[i])"
                        }
                    }
                    if sample.channelSamples.count < 2 || sample.statusBits == nil || sample.statusBits!.count < 2 {
                        BleLogger.trace("PPG2 sample incomplete - channels: \(sample.channelSamples.count), statusBits: \(sample.statusBits?.count ?? 0)")
                    }
                    return line
                }.joined(separator: "\n")
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
                try await api.setMultiBLEConnectionMode(identifier: deviceId!, enable: enabled)
                Task { @MainActor in
                    self.multiBleFeature.isEnabled = enabled
                }
            } catch let err {
                NSLog("Failed to set BLE multi connection mode, \(err)")
            }
        }
    }

    func multiBLEModeToggle() {
        guard case .connected = deviceConnectionState, let devId = deviceId else {
            NSLog("Device is not connected \(deviceConnectionState)")
            Task { @MainActor in self.multiBleFeature.isEnabled = false }
            return
        }
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let enable = !multiBleFeature.isEnabled
                try await api.setMultiBLEConnectionMode(identifier: devId, enable: enable)
                multiBleFeature.isEnabled = enable
                NSLog("Multi BLE mode is \(enable ? "enabled" : "disabled")")
            } catch {
                somethingFailed(text: "Multi BLE mode toggle failed: \(error)")
            }
        }
    }

    func getMultiBleModeStatus() async {
        if case .connected(let device) = deviceConnectionState {
            do {
                NSLog("Get multi BLE mode status")
                let isMultiBleModeEnabled: Bool = try await api.getMultiBLEConnectionMode(identifier: device.deviceId)
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
    
    func startListenForPolarHrBroadcasts(_ deviceIds: Set<String>?) {
        broadcastTask?.cancel()
        broadcastTask = Task { [weak self] in
            guard let self else { return }
            do {
                for try await hrData in api.startListenForPolarHrBroadcasts(deviceIds) {
                    NSLog("HR broadcast received, device: \(hrData.deviceInfo.deviceId), HR: \(hrData.hr)")
                    await MainActor.run {
                        self.lastHrBroadcastData = hrData
                        self.hrBroadcastUpdateCount += 1
                    }
                }
            } catch {
                NSLog("HR broadcast error: \(error)")
            }
        }
    }
    
    func stopListenForPolarHrBroadcasts() {
        broadcastTask?.cancel()
        broadcastTask = nil
    }
    
    func startObservingExerciseNotifications() {
        guard case .connected(let device) = deviceConnectionState else { return }
        exerciseNotificationTask?.cancel()
        exerciseState.isObservingNotifications = true
        exerciseState.notificationEvent = nil
        exerciseNotificationTask = Task { [weak self] in
            guard let self else { return }
            do {
                for try await info in api.observeExerciseStatus(identifier: device.deviceId) {
                    NSLog("Exercise notification received: \(info.status)")
                    await MainActor.run {
                        self.exerciseState.apply(status: info.status, sport: info.sportProfile, startTime: info.startTime)
                        self.exerciseState.applyNotificationEvent(status: info.status, sport: info.sportProfile)
                    }
                }
            } catch {
                NSLog("Exercise notification observation failed: \(error)")
                await MainActor.run {
                    self.exerciseState.isObservingNotifications = false
                    self.exerciseState.notificationEvent = "Error:  \(error.localizedDescription)"
                }
            }
        }
    }

    func stopObservingExerciseNotifications() {
        exerciseNotificationTask?.cancel()
        exerciseNotificationTask = nil
        exerciseState.isObservingNotifications = false
    }

    func toggleExerciseNotificationObservation() {
        if exerciseState.isObservingNotifications {
            stopObservingExerciseNotifications()
        } else {
            startObservingExerciseNotifications()
        }
    }
    
    func startObservingDeviceToHostNotifications() {
        guard case .connected(let device) = deviceConnectionState else { return }
        let deviceId = device.deviceId
        let badgeId = "D2H-\(deviceId)"
        let title = "Device \(deviceId)"
        deviceToHostNotificationTask?.cancel()
        isObservingDeviceToHostNotifications = true
        deviceToHostNotificationTask = Task { [weak self] in
            guard let self else { return }
            do {
                for try await notificationData in api.observeDeviceToHostNotifications(identifier: deviceId) {
                    let paramDescription = notificationData.parsedParameters != nil ? String(describing: notificationData.parsedParameters!) : "none"
                    let details = "\(notificationData.notificationType) with \(notificationData.parameters.count) bytes, param: \(paramDescription)"
                    BleLogger.trace("PSDC: Device to host notification received: \(details)")
                    await MainActor.run { self.showUNUserNotification(id: badgeId, title: title, body: details) }
                }
                BleLogger.trace("PSDC: Device to host notification observing completed")
                await MainActor.run { self.isObservingDeviceToHostNotifications = false }
            } catch {
                let details = "Device to host notification receiving from device \(device.deviceId) failed with error \(error)"
                BleLogger.trace("PSDC: \(details)")
                await MainActor.run {
                    self.showUNUserNotification(id: badgeId, title: title, body: details)
                    self.isObservingDeviceToHostNotifications = false
                }
            }
        }
    }

    func stopObservingDeviceToHostNotifications() {
        deviceToHostNotificationTask?.cancel()
        deviceToHostNotificationTask = nil
        isObservingDeviceToHostNotifications = false
    }
        
    func toggleDeviceToHostNotificationObservation() {
        if isObservingDeviceToHostNotifications {
            stopObservingDeviceToHostNotifications()
        } else {
            startObservingDeviceToHostNotifications()
        }
    }
    
    func getBatteryChargeLevel() throws {
        if case .connected(let device) = deviceConnectionState {
            self.batteryChargeLevel = try api.getBatteryLevel(identifier: device.deviceId)
        }
    }

    func getChargeStatus() throws {
        if case .connected(let device) = deviceConnectionState {
            self.deviceChargeStatus = try api.getChargerState(identifier: device.deviceId)
        }
    }

    // PolarBleSdkManager methods for generic low level API.
    func listFiles(directoryPath: String, recurseDeep: Bool = false) async throws {
        if case .connected(let device) = deviceConnectionState {
            let fileList = try await api.getFileList(identifier: device.deviceId, directoryPath: directoryPath, recurseDeep: recurseDeep)
            Task { @MainActor in
                if (fileList.isEmpty) {
                    NSLog("No files found for path \(directoryPath)")
                } else {
                    genericApiFileList.append(contentsOf: fileList)
                }
            }
        }
    }

    func readFile(filePath: String) async throws {
        if case .connected(let device) = deviceConnectionState {
            let fileData = try await api.readFile(identifier: device.deviceId, filePath: filePath)
            Task { @MainActor in
                if (fileData == nil) {
                    NSLog("No file data found for path \(filePath)")
                } else {
                    genericApiFileData = fileData ?? Data()
                }
            }
        }
    }

    func writeFile(filePath: String, fileData: Data) async throws {
        if case .connected(let device) = deviceConnectionState {
            try await api.writeFile(identifier: device.deviceId, filePath: filePath, fileData: fileData)
        }
    }

    func deleteFile(filePath: String) async throws {
        if case .connected(let device) = deviceConnectionState {
            try await api.deleteFileOrDirectory(identifier: device.deviceId, filePath: filePath)
        }
    }

    func getRSSIValue() {
        if case .connected(let device) = deviceConnectionState {
            do {
                rssi = try api.getRSSIValue(device.deviceId)
            } catch {
                NSLog("Failed to get RSSI value: \(error)")
            }
        }
    }

    func checkIfDeviceDisconnectedDueToRemovedPairing() {
        if case .connected(let device) = deviceConnectionState {
            do {
                didDisconnect = try api.checkIfDeviceDisconnectedDueRemovedPairing(device.deviceId)
            } catch {
                NSLog("Failed to get check if device did disconnect due to removed pairing: \(error)")
            }
        }
    }

    func startOfflineExerciseV2() async throws -> OfflineExerciseStartResult {
        guard case .connected(let device) = deviceConnectionState else {
            throw NSError(domain: "Device not connected", code: -1)
        }
        offlineExerciseV2Status = false
        do {
            let result = try await api.startOfflineExerciseV2(identifier: device.deviceId)
            offlineExerciseV2Status = (result.result == .success)
            return result
        } catch {
            NSLog("Start Offline Exercise V2 failed: \(error)")
            throw error
        }
    }

    func stopOfflineExerciseV2() async throws {
        guard case .connected(let device) = deviceConnectionState else {
            throw NSError(domain: "Device not connected", code: -1)
        }
        do {
            try await api.stopOfflineExerciseV2(identifier: device.deviceId)
            offlineExerciseV2Status = false
        } catch {
            NSLog("Stop Offline Exercise V2 failed: \(error)")
            throw error
        }
    }

    func listOfflineExercisesV2() {
        guard case .connected(let device) = deviceConnectionState else { return }
        genericApiFileList.removeAll()
        offlineExerciseV2Entries.removeAll()
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                for try await entry in api.listOfflineExercisesV2(identifier: device.deviceId, directoryPath: "/") {
                    genericApiFileList.append(entry.path)
                    offlineExerciseV2Entries.append(entry)
                }
            } catch {
                if isSystemBusy(error: error) {
                    NSLog("listOfflineExercisesV2(), device busy (exercise running)")
                } else {
                    NSLog("listOfflineExercisesV2 failed: \(error)")
                }
            }
        }
    }

    func readOfflineExerciseV2() async -> PolarExerciseData? {

        guard case .connected(let device) = deviceConnectionState else {
            NSLog("readOfflineExerciseV2 failed, device not connected")
            return nil
        }

        guard let entryPath = genericApiFileList.first else {
            NSLog("readOfflineExerciseV2 failed, no entry found")
            return nil
        }

        let entry = PolarExerciseEntry(
            path: entryPath,
            date: Date(),
            entryId: URL(fileURLWithPath: entryPath).lastPathComponent
        )

        do {

            let data = try await api
                .fetchOfflineExerciseV2(
                    identifier: device.deviceId,
                    entry: entry
                )

            return data

        } catch {
            NSLog("readOfflineExerciseV2 failed: \(error)")
            return nil
        }
    }

    func getOfflineExerciseStatusV2() {
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("getOfflineExerciseStatusV2 failed – device not connected")
            return
        }
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let isRunning = try await api.getOfflineExerciseStatusV2(identifier: device.deviceId)
                offlineExerciseV2Status = isRunning
            } catch {
                if isSystemBusy(error: error) {
                    offlineExerciseV2Status = true
                } else {
                    NSLog("getOfflineExerciseStatusV2 failed: \(error)")
                }
            }
        }
    }

    func removeOfflineExerciseV2() async throws {
        guard case .connected(let device) = deviceConnectionState else {
            throw NSError(domain: "Device not connected", code: -1)
        }
        guard let entryPath = genericApiFileList.first else {
            throw NSError(domain: "No exercise entry selected", code: -2)
        }
        let entry = PolarExerciseEntry(
            path: entryPath,
            date: Date(),
            entryId: URL(fileURLWithPath: entryPath).lastPathComponent
        )
        offlineExerciseV2Status = false
        do {
            try await api.removeOfflineExerciseV2(identifier: device.deviceId, entry: entry)
            genericApiFileList.removeAll()
            offlineExerciseV2Entries.removeAll()
        } catch {
            if isSystemBusy(error: error) {
                offlineExerciseV2Status = true
            } else {
                NSLog("removeOfflineExerciseV2 failed: \(error)")
            }
            throw error
        }
    }

    func checkOfflineExerciseV2Support() {
        guard case .connected(let device) = deviceConnectionState else {
            NSLog("checkOfflineExerciseV2Support, no device connected, skipping V2 exercise capability check")
            offlineExerciseV2Supported = false
            return
        }
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let supported = try await api.isOfflineExerciseV2Supported(identifier: device.deviceId)
                NSLog("checkOfflineExerciseV2Support, capability result received: \(supported)")
                offlineExerciseV2Supported = supported
            } catch {
                NSLog("V2 exercise capability check failed: \(error)")
                offlineExerciseV2Supported = false
            }
        }
    }

    func fetchAndExportOfflineExerciseV2() async throws -> URL {

        guard let entryPath = offlineExerciseV2Entries.first?.path,
              case .connected(let device) = deviceConnectionState else {
            throw NSError(domain: "NoDeviceOrEntry", code: -1)
        }

        let entry = PolarExerciseEntry(
            path: entryPath,
            date: Date(),
            entryId: URL(fileURLWithPath: entryPath).lastPathComponent
        )

        let data = try await api
            .fetchOfflineExerciseV2(identifier: device.deviceId, entry: entry)

        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("Polar_offline_exercise_export.txt")

        let text = "Samples:\(data.samples)"

        try text.write(to: tempURL, atomically: true, encoding: .utf8)

        return tempURL
    }


    private func isSystemBusy(error: Error) -> Bool {
        let msg = error.localizedDescription.uppercased()
        return msg.contains("202") || msg.contains("SYSTEM_BUSY")
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

// MARK: - Log Management
extension PolarBleSdkManager {
    private var logFileURL: URL? {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?
            .appendingPathComponent("PSDCAppLogs.txt")
    }

    func getAppLogsFile() -> URL? {
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

    func openLogFile(logFileURL: URL) -> FileHandle? {
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
            NSLog("Error opening or creating log file: %@", error.localizedDescription)
            return nil
        }
    }

    func logToFile(_ message: String) {
        if let logFileHandle = logFileHandle {
            if let data = (message + "\n").data(using: .utf8) {
                logFileHandle.seekToEndOfFile()
                logFileHandle.write(data)
                logFileHandle.synchronizeFile()
            } else {
                NSLog("Error: Could not convert message to data.")
            }
        } else {
            if let url = logFileURL {
                logFileHandle = openLogFile(logFileURL: url)
                if logFileHandle != nil {
                    logToFile(message)
                } else {
                    NSLog("Error: Failed to open log file.")
                }
            }
        }
    }
}
// MARK: - PolarBleApiPowerStateObserver (PolarBleSdkManager)
extension PolarBleSdkManager : PolarBleApiPowerStateObserver {
    nonisolated func blePowerOn() {
        NSLog("BLE ON")
        Task { @MainActor in
            self.isBluetoothOn = true
        }
    }

    nonisolated func blePowerOff() {
        NSLog("BLE OFF")
        Task { @MainActor in
            self.isBluetoothOn = false
        }
    }
}

// MARK: - PolarBleApiPowerStateObserver (PolarBleDeviceManager)
extension PolarBleDeviceManager : PolarBleApiPowerStateObserver {
    nonisolated func blePowerOn() {
        NSLog("BLE ON")
        Task { @MainActor in
            isBluetoothOn = true
        }
    }
    
    nonisolated func blePowerOff() {
        NSLog("BLE OFF")
        Task { @MainActor in
            isBluetoothOn = false
        }
    }
}

// MARK: - PolarBleApiObserver
extension PolarBleSdkManager : PolarBleApiObserver {
    nonisolated func deviceConnecting(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DEVICE CONNECTING: \(polarDeviceInfo)")
        Task { @MainActor in
            if polarDeviceInfo.deviceId == self.deviceConnectionState.get().deviceId {
                self.deviceConnectionState = .connecting(polarDeviceInfo)
            }
        }
    }
    
    nonisolated func deviceConnected(_ device: PolarDeviceInfo) {
        NSLog("DEVICE CONNECTED: \(device)")
        Task { @MainActor in
            self.disconnectedDevicesPairingErrors.removeValue(forKey: device.deviceId)
            self.updateStateWhenDeviceConnected(device: device)
            self.checkOfflineExerciseV2Support()
        }
    }
    
    nonisolated func deviceDisconnected(_ device: PolarDeviceInfo, pairingError: Bool) {
        NSLog("DISCONNECTED: \(device)")
        Task { @MainActor in
            self.updateStateWhenDeviceDisconnected(withId: device.deviceId, pairingError: pairingError)
        }
    }
}

// MARK: - PolarBleApiDeviceInfoObserver
extension PolarBleSdkManager : PolarBleApiDeviceInfoObserver {
    nonisolated func disInformationReceivedWithKeysAsStrings(_ identifier: String, key: String, value: String) {
        // Not implemented
    }
    
    nonisolated func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        NSLog("battery level updated: \(batteryLevel)")
        Task { @MainActor in
            self.batteryStatusFeature.batteryLevel = batteryLevel
        }
    }
    
    nonisolated func batteryChargingStatusReceived(_ identifier: String, chargingStatus: BleBasClient.ChargeState) {
        NSLog("battery charging status updated: \(chargingStatus)")
        Task { @MainActor in
            self.batteryStatusFeature.chargeState = chargingStatus
        }
    }
    
    nonisolated func batteryPowerSourcesStateReceived(_ identifier: String, powerSourcesState: BleBasClient.PowerSourcesState) {
        NSLog("battery power sources status updated: \(powerSourcesState)")
        Task { @MainActor in
            self.batteryStatusFeature.powerSourcesState = powerSourcesState
        }
    }
    
    nonisolated func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
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

    nonisolated func bleSdkFeaturesReadiness(_ identifier: String, ready: [PolarBleSdkFeature], unavailable: [PolarBleSdkFeature]) {
        
        // Initialize SDK feature dependent PSDC features:
        
        if ready.contains(.feature_hr) {
            Task { @MainActor in
                self.onlineStreamingFeature.isSupported = true
            }

            Task { @MainActor [weak self] in
                guard let self else { return }
                do {
                    let availableOnlineDataTypes = try await api.getAvailableHRServiceDataTypes(identifier: identifier)
                    for dataType in availableOnlineDataTypes {
                        onlineStreamingFeature.availableOnlineDataTypes[dataType] = true
                    }
                } catch {
                    BleLogger.trace("Failed to get available online streaming data types: \(error)")
                }
            }
        }
        
        if ready.contains(.feature_battery_info) {
            Task { @MainActor in
                self.batteryStatusFeature.isSupported = true
            }
        }
        
        if ready.contains(.feature_device_info)  {
            Task { @MainActor in
                self.deviceInfoFeature.isSupported = true
            }
        }
        
        if ready.contains(.feature_polar_h10_exercise_recording) {
            Task { @MainActor in
                self.h10RecordingFeature.isSupported = true
            }
            Task { @MainActor in
                self.getH10RecordingStatus()
            }
        }
        
        if ready.contains(.feature_polar_device_time_setup) ||
            ready.contains(.feature_polar_device_control) {
            Task { @MainActor in
                self.deviceTimeSetupFeature.isSupported = true
            }
        }
        
        if ready.contains(.feature_polar_sdk_mode) {
            Task { @MainActor in
                self.sdkModeFeature.isSupported = true
            }
            Task { @MainActor in
                await self.getSdkModeStatus()
            }
        }
        
        if ready.contains(.feature_polar_online_streaming) {
            Task { @MainActor in
                self.onlineStreamingFeature.isSupported = true
            }
            
            Task { @MainActor [weak self] in
                guard let self else { return }
                do {
                    let availableOnlineDataTypes = try await api.getAvailableOnlineStreamDataTypes(identifier)
                    for dataType in availableOnlineDataTypes {
                        onlineStreamingFeature.availableOnlineDataTypes[dataType] = true
                    }
                } catch {
                    BleLogger.trace("Failed to get available online streaming data types: \(error)")
                }
            }
        }
        
        if ready.contains(.feature_polar_offline_recording) {
            Task { @MainActor in
                self.offlineRecordingFeature.isSupported = true
            }
            Task { @MainActor in
                await self.getOfflineRecordingStatus()
            }
        }

        if ready.contains(.feature_polar_offline_exercise_v2) {
            Task { @MainActor in
                self.offlineExerciseV2Feature.isSupported = true
            }
            Task { @MainActor in
                await self.getOfflineExerciseV2Status()
            }
        }

        if ready.contains(.feature_polar_led_animation) {
            Task { @MainActor in
                self.ledAnimationFeature.isSupported = true
            }
            Task { @MainActor in
                await self.getSdkModeStatus()
            }
        }
    
        if ready.contains(.feature_polar_firmware_update) ||
            ready.contains(.feature_polar_device_control) {
            Task { @MainActor in
                self.firmwareUpdateFeature.isSupported = true
                self.checkFirmwareUpdateFeature.isSupported = true
                if checkFirmwareUpdateNeeded() {
                    Task {
                        await checkFirmwareUpdate()
                    }
                }
            }
        }
    
        if ready.contains(.feature_polar_training_data) {
            Task { @MainActor in
                self.trainingSessionEntries.isSupported = true
            }
            NSLog("Training data feature is ready")
        }
        
        if ready.contains(.feature_polar_sleep_data) {
            Task { @MainActor in
                self.sleepRecordingFeature.isSupported = true
            }
            Task { @MainActor in
                await self.getSleepRecordingState()
            }
        }
    
        if ready.contains(.feature_polar_activity_data) {
            Task { @MainActor in
                self.activityDataFeature.isSupported = true
            }
        }
    
        if ready.contains(.feature_polar_features_configuration_service) {
            // Not implemented. Could enable multi BLE mode control.
            print("Not implemented")
        }
        
        if ready.contains(.feature_polar_file_transfer) {
            Task { @MainActor in
                self.fileTransferFeature.isSupported = true
            }
        }

        if ready.contains(.feature_polar_watch_faces_configuration) {
            Task { @MainActor in
                self.watchFaceFeature.isSupported = true
            }
        }
    }
    
    nonisolated func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdk.PolarBleSdkFeature) {
        NSLog("Feature is ready: \(feature)")
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
    private func showUNUserNotification(id: String, title: String, body: String) {
        Task { @MainActor in
            do {
                if try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert,.badge, .sound]) == true {
                    let content = UNMutableNotificationContent()
                    content.title = title
                    content.body = body
                    try await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
                } else {
                    BleLogger.trace("PSDC: Permission not granted for notifications")
                }
            } catch let err {
                // Ignore
                BleLogger.trace("\(err)")
            }
        }
    }
}

