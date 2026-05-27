/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import Combine
import os

#if os(iOS)
import UIKit
#endif

/// Default implementation
@objc class PolarBleApiImpl: NSObject,
                             BleDeviceSessionStateObserver,
                             BlePowerStateObserver {
    
    weak var deviceHrObserver: PolarBleApiDeviceHrObserver?
    weak var deviceFeaturesObserver: PolarBleApiDeviceFeaturesObserver?
    weak var deviceInfoObserver: PolarBleApiDeviceInfoObserver?
    weak var powerStateObserver: PolarBleApiPowerStateObserver? {
        didSet {
            if listener.blePowered() {
                powerStateObserver?.blePowerOn()
            }
        }
    }
    var deviceSessionState: BleDeviceSession.DeviceSessionState?

    weak var observer: PolarBleApiObserver?
    
    var isBlePowered: Bool {
        get {
            return listener.blePowered()
        }
    }
    weak var logger: PolarBleApiLogger?
    var automaticReconnection: Bool {
        get {
            return listener.automaticReconnection
        }
        set {
            listener.automaticReconnection = newValue
        }
    }
    
    let listener: CBDeviceListenerImpl
    let queue: DispatchQueue
    let scheduler: DispatchQueue
    var connectSubscriptions = [String: Task<Void, Never>]()
    var featureCheckSubscriptions = [String: AnyCancellable]()
    private var hrMulticasts = [String: MulticastAsyncStream<BleHrClient.BleHrNotification>]()
    /// Tracks which SDK features are currently ready, keyed by device identifier.
    /// Populated as features become ready during device setup; cleared on disconnect.
    private var readyFeaturesMap = [String: Set<PolarBleSdkFeature>]()
    private let readyFeaturesLock = NSLock()
    var serviceList = [CBUUID.init(string: "180D")]
    let features:Set<PolarBleSdkFeature>
    let dateFormatter = ISO8601DateFormatter()
    let PMDFilePath = "/PMDFILES.TXT"
    public private(set) var serviceClientUtils: PolarServiceClientUtils
    var fileUtils: PolarFileUtils
    
    required public init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>, restoreIdentifier: String? = nil) {
        let resolvedFeatures = features.isEmpty ? Set(PolarBleSdkFeature.allCases) : features
        var clientList: [(_ gattServiceTransmitter: BleAttributeTransportProtocol) -> BleGattClientBase] = []
        self.features = resolvedFeatures
        
        // BleHrClient
        if(resolvedFeatures.contains(PolarBleSdkFeature.feature_hr) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_online_streaming) ) {
            clientList.append(BleHrClient.init)
        }
        
        // BleDisClient
        if(resolvedFeatures.contains(PolarBleSdkFeature.feature_device_info)) {
            clientList.append(BleDisClient.init)
        }
        
        // BleBasClient
        if(resolvedFeatures.contains(PolarBleSdkFeature.feature_battery_info)) {
            clientList.append(BleBasClient.init)
        }
        
        // BlePmdClient
        if(resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_online_streaming) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_offline_recording) ||
              resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_sdk_mode) ||
              resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_led_animation)) {
            clientList.append(BlePmdClient.init)
        }
        
        // BlePsFtpClient
        if(resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_offline_recording) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_h10_exercise_recording) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_device_time_setup) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_activity_data) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_training_data) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_sleep_data) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_device_control) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_file_transfer) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_temperature_data) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_firmware_update) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_led_animation) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_spo2_test_data) ||
           resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_watch_faces_configuration)) {
            clientList.append(BlePsFtpClient.init)
            //TODO, why this is needed?
            serviceList.append(CBUUID.init(string: "FEEE"))
        }
        
        // BleHtsClient
        if (resolvedFeatures.contains(PolarBleSdkFeature.feature_hts)) {
            clientList.append(BleHtsClient.init)
        }
        
        // BlePsPFCClient
        if (resolvedFeatures.contains(PolarBleSdkFeature.feature_polar_features_configuration_service)) {
            clientList.append(BlePfcClient.init)
        }
        
        self.queue = queue
        self.listener = CBDeviceListenerImpl(queue, clients: clientList, identifier: 0, restoreIdentifier: restoreIdentifier)
        self.listener.automaticH10Mapping = true
        self.scheduler = queue
        self.serviceClientUtils = PolarServiceClientUtils(listener: listener)
        self.fileUtils = PolarFileUtils(listener: listener, serviceClientUtils: serviceClientUtils)
        super.init()
        self.listener.scanPreFilter = deviceFilter
        self.listener.deviceSessionStateObserver = self
        self.listener.powerStateObserver = self
        BleLogger.setLogLevel(BleLogger.LOG_LEVEL_ALL)
        BleLogger.setLogger(self)
        
#if os(iOS)
        NotificationCenter.default.addObserver(self, selector: #selector(foreground), name: UIApplication.willEnterForegroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(background), name: UIApplication.didEnterBackgroundNotification, object: nil)
#endif
        BlePolarDeviceCapabilitiesUtility.initialize()
    }
    
    deinit {
#if os(iOS)
        NotificationCenter.default.removeObserver(self, name: UIApplication.willEnterForegroundNotification, object: nil)
        NotificationCenter.default.removeObserver(self, name: UIApplication.didEnterBackgroundNotification, object: nil)
#endif
    }
    
    // from BlePowerStateObserver
    func powerStateChanged(_ state: BleState) {
        switch state {
        case .poweredOn:
            self.powerStateObserver?.blePowerOn()
        case .resetting: fallthrough
        case .poweredOff:
            self.powerStateObserver?.blePowerOff()
        case .unknown: fallthrough
        case .unsupported: fallthrough
        case .unauthorized:
            break
        }
    }
    
    // from BleDeviceSessionStateObserver
    func stateChanged(_ session: BleDeviceSession) {
        deviceSessionState = session.state
        let hasSAGRFCFileSystem = (BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) == BlePolarDeviceCapabilitiesUtility.FileSystemType.polarFileSystemV2)
        let info = PolarDeviceInfo(
            session.advertisementContent.polarDeviceIdUntouched.count != 0 ? session.advertisementContent.polarDeviceIdUntouched : session.address.uuidString,
            session.address, Int(session.advertisementContent.rssiFilter.rssi),session.advertisementContent.name,true, hasSAGRFCFileSystem)
        switch session.state {
        case .sessionOpen:
            self.observer?.deviceConnected(info)
            self.setupDevice(session)
        case .sessionOpenPark where session.previousState == .sessionOpen: fallthrough
        case .sessionClosed where session.previousState == .sessionOpen: fallthrough
        case .sessionClosed where session.previousState == .sessionClosing:
            let dis1 = readyFeaturesLock.withLock {
                readyFeaturesMap.removeValue(forKey: info.deviceId)
                return featureCheckSubscriptions.removeValue(forKey: info.deviceId)
            }
            dis1?.cancel()
            let hrMulticast1 = readyFeaturesLock.withLock { hrMulticasts.removeValue(forKey: info.deviceId) }
            hrMulticast1?.finish(throwing: BleGattException.gattDisconnected)
            self.observer?.deviceDisconnected(info, pairingError: session.error?.indicatesBLEPairingProblem ?? false)
        case .sessionOpenPark where session.previousState == .sessionOpening:
            let dis2 = readyFeaturesLock.withLock {
                readyFeaturesMap.removeValue(forKey: info.deviceId)
                return featureCheckSubscriptions.removeValue(forKey: info.deviceId)
            }
            dis2?.cancel()
            let hrMulticast2 = readyFeaturesLock.withLock { hrMulticasts.removeValue(forKey: info.deviceId) }
            hrMulticast2?.finish(throwing: BleGattException.gattDisconnected)
            self.observer?.deviceDisconnected(info, pairingError: false)
        case .sessionClosed where session.disconnectedDueRemovedPairing:
            let dis3 = readyFeaturesLock.withLock {
                readyFeaturesMap.removeValue(forKey: info.deviceId)
                return featureCheckSubscriptions.removeValue(forKey: info.deviceId)
            }
            dis3?.cancel()
            let hrMulticast3 = readyFeaturesLock.withLock { hrMulticasts.removeValue(forKey: info.deviceId) }
            hrMulticast3?.finish(throwing: BleGattException.gattDisconnected)
            self.observer?.deviceDisconnected(info, pairingError: session.error?.indicatesBLEPairingProblem ?? false)
        case .sessionOpening:
            self.observer?.deviceConnecting(info)
        case .sessionClosed: fallthrough
        case .sessionOpenPark: fallthrough
        case .sessionClosing:
            break
        }
    }
    
    @objc private func foreground() {
        logMessage("foreground")
        listener.servicesToScanFor = nil
    }
    
    @objc private func background() {
        logMessage("background")
        listener.servicesToScanFor = serviceList
    }
    
    private enum FeatureState {
        case notAvailable
        case notReady
        case ready
    }
    
    fileprivate func deviceFilter(_ content: BleAdvertisementContent) -> Bool {
        return content.polarDeviceId.count != 0 && content.polarDeviceType != "mobile"
    }

    private func hasHrClient(_ session: BleDeviceSession) -> Bool {
        return session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient != nil
    }

    private func hasDisClient(_ session: BleDeviceSession) -> Bool {
        return session.fetchGattClient(BleDisClient.DIS_SERVICE) as? BleDisClient != nil
    }

    private func hasBasClient(_ session: BleDeviceSession) -> Bool {
        return session.fetchGattClient(BleBasClient.BATTERY_SERVICE) as? BleBasClient != nil
    }

    private func hasPmdClient(_ session: BleDeviceSession) -> Bool {
        return session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient != nil
    }

    private func hasPsFtpClient(_ session: BleDeviceSession) -> Bool {
        return session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient != nil
    }

    private func hasPfcClient(_ session: BleDeviceSession) -> Bool {
        return session.fetchGattClient(BlePfcClient.PFC_SERVICE) as? BlePfcClient != nil
    }

    private func hasHtsClient(_ session: BleDeviceSession) -> Bool {
        return session.fetchGattClient(HealthThermometer.HTS_SERVICE) as? BleHtsClient != nil
    }

    private func isHeartRateFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BleHrClient.HR_SERVICE) && hasHrClient(session)
    }

    private func isHeartRateFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        guard let hrClient = session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        let state: FeatureState = hrClient.isCharacteristicNotificationEnabled(BleHrClient.HR_MEASUREMENT) ? .ready : .notReady
        return Just(state).eraseToAnyPublisher()
    }

    private func isDeviceInfoFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BleDisClient.DIS_SERVICE) && hasDisClient(session)
    }

    private func isDeviceInfoFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        Just(hasDisClient(session) ? .ready : .notAvailable).eraseToAnyPublisher()
    }

    private func isBatteryInfoFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BleBasClient.BATTERY_SERVICE) && hasBasClient(session)
    }

    private func isBatteryInfoFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        Just(hasBasClient(session) ? .ready : .notAvailable).eraseToAnyPublisher()
    }

    private func isOnlineStreamingFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        let hrAvailable = discoveredServices.contains(BleHrClient.HR_SERVICE) && hasHrClient(session)
        let pmdAvailable = discoveredServices.contains(BlePmdClient.PMD_SERVICE) && hasPmdClient(session)
        return hrAvailable || pmdAvailable
    }

    private func isPmdReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        guard let pmdClient = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        guard PolarServiceClientUtils.pmdNotificationsEnabled(session) else {
            return Just(.notReady).eraseToAnyPublisher()
        }
        return Future<FeatureState, Never> { promise in
            Task {
                let features = (try? await pmdClient.readFeature(true)) ?? Set<PmdMeasurementType>()
                promise(.success(features.contains { $0.isDataType() } ? .ready : .notAvailable))
            }
        }.eraseToAnyPublisher()
    }

    private func isAnyPmdMeasurementTypeReady(_ session: BleDeviceSession, types: [PmdMeasurementType]) -> AnyPublisher<FeatureState, Never> {
        guard let pmdClient = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        guard PolarServiceClientUtils.pmdNotificationsEnabled(session) else {
            return Just(.notReady).eraseToAnyPublisher()
        }
        return Future<FeatureState, Never> { promise in
            Task {
                let features = (try? await pmdClient.readFeature(true)) ?? Set<PmdMeasurementType>()
                promise(.success(features.contains(where: { types.contains($0) }) ? .ready : .notAvailable))
            }
        }.eraseToAnyPublisher()
    }

    private func isOnlineStreamingFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isHeartRateFeatureReady(session).zip(isPmdReady(session))
            .map { hr, pmd -> FeatureState in
                if hr == .ready || pmd == .ready { return .ready }
                if hr == .notAvailable && pmd == .notAvailable { return .notAvailable }
                return .notReady
            }
            .eraseToAnyPublisher()
    }

    private func isFtpReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        guard session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient != nil else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        let state: FeatureState = PolarServiceClientUtils.psFtpNotificationsEnabled(session) ? .ready : .notReady
        return Just(state).eraseToAnyPublisher()
    }

    private func isOfflineRecordingFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePmdClient.PMD_SERVICE) &&
               discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) &&
               hasPmdClient(session) &&
               hasPsFtpClient(session)
    }

    private func isOfflineRecordingFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isAnyPmdMeasurementTypeReady(session, types: [.offline_recording, .offline_hr]).zip(isFtpReady(session))
            .map { pmd, ftp -> FeatureState in
                if ftp == .notAvailable || pmd == .notAvailable { return .notAvailable }
                if ftp == .ready && pmd == .ready { return .ready }
                return .notReady
            }
            .eraseToAnyPublisher()
    }

    private func isH10ExerciseFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) &&
               hasPsFtpClient(session) &&
               BlePolarDeviceCapabilitiesUtility.isRecordingSupported(session.advertisementContent.polarDeviceType)
    }

    private func isH10ExerciseFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        guard BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) == .h10FileSystem else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        let state: FeatureState = PolarServiceClientUtils.psFtpNotificationsEnabled(session) ? .ready : .notReady
        return Just(state).eraseToAnyPublisher()
    }

    private func isOfflineExerciseV2FeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        guard hasPsFtpClient(session) else { return Just(.notAvailable).eraseToAnyPublisher() }
        guard PolarServiceClientUtils.psFtpNotificationsEnabled(session) else { return Just(.notReady).eraseToAnyPublisher() }
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        return Future<FeatureState, Never> { promise in
            Task {
                var operation = Protocol_PbPFtpOperation()
                operation.command = .get
                operation.path = "/DEVICE.BPB"
                do {
                    let request = try operation.serializedData()
                    let response = try await client.request(request)
                    let deviceInfo = try Data_PbDeviceInfo(serializedBytes: Data(response))
                    promise(.success(deviceInfo.capabilities.contains("dm_exercise") ? .ready : .notAvailable))
                } catch {
                    promise(.success(.notAvailable))
                }
            }
        }.eraseToAnyPublisher()
    }

    private func isPolarDeviceTimeFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) && hasPsFtpClient(session)
    }

    private func isPolarDeviceTimeFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        let state: FeatureState = PolarServiceClientUtils.psFtpNotificationsEnabled(session) ? .ready : .notReady
        return Just(state).eraseToAnyPublisher()
    }

    private func isSdkModeFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePmdClient.PMD_SERVICE) && hasPmdClient(session)
    }

    private func isSdkModeFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isAnyPmdMeasurementTypeReady(session, types: [.sdkMode])
    }

    private func isLedAnimationFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePmdClient.PMD_SERVICE) &&
               discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) &&
               hasPmdClient(session) &&
               hasPsFtpClient(session)
    }

    private func isLedAnimationFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isAnyPmdMeasurementTypeReady(session, types: [.sdkMode]).zip(isFtpReady(session))
        .map { pmd, ftp -> FeatureState in
            if ftp == .notAvailable || pmd == .notAvailable { return .notAvailable }
            if ftp == .ready && pmd == .ready { return .ready }
            return .notReady
        }
        .eraseToAnyPublisher()
    }

    private func isPolarActivityDataFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) &&
               hasPsFtpClient(session) &&
               BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(session.advertisementContent.polarDeviceType)
    }

    private func isPolarActivityDataFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        let deviceModel = PolarAdvDataUtility.extractDeviceModelFromName(session.advertisementContent.name)
        guard BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceModel) else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        return isFtpReady(session)
    }

    private func isPolarTrainingDataFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) && hasPsFtpClient(session)
    }

    private func isPolarTrainingDataFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isFtpReady(session)
    }

    private func isPolarSleepFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) &&
               hasPsFtpClient(session) &&
               BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(session.advertisementContent.polarDeviceType)
    }

    private func isPolarSleepFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isFtpReady(session)
    }

    private func isPolarDeviceControlFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) && hasPsFtpClient(session)
    }

    private func isPolarDeviceControlFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isFtpReady(session)
    }

    private func isPolarFileTransferFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) && hasPsFtpClient(session)
    }

    private func isPolarFileTransferFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isFtpReady(session)
    }

    private func isHtsFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(HealthThermometer.HTS_SERVICE) && hasHtsClient(session)
    }

    private func isHtsFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        guard let htsClient = session.fetchGattClient(HealthThermometer.HTS_SERVICE) as? BleHtsClient else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        let state: FeatureState = htsClient.isCharacteristicNotificationEnabled(HealthThermometer.TEMPERATURE_MEASUREMENT) ? .ready : .notReady
        return Just(state).eraseToAnyPublisher()
    }

    private func isPolarTemperatureDataFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) &&
               hasPsFtpClient(session) &&
               BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(session.advertisementContent.polarDeviceType)
    }

    private func isPolarTemperatureDataFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isFtpReady(session)
    }

    private func isPolarFirmwareUpdateFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) &&
               hasPsFtpClient(session) &&
               BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported(session.advertisementContent.polarDeviceType)
    }

    private func isPolarFirmwareUpdateFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isFtpReady(session)
    }

    private func isFeatureConfigurationServiceFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePfcClient.PFC_SERVICE) && hasPfcClient(session)
    }

    private func isFeatureConfigurationServiceFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        guard let pfcClient = session.fetchGattClient(BlePfcClient.PFC_SERVICE) as? BlePfcClient else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        let state: FeatureState = pfcClient.isServiceDiscovered() ? .ready : .notReady
        return Just(state).eraseToAnyPublisher()
    }

    private func isPolarSpo2TestDataFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) && hasPsFtpClient(session)
    }

    private func isPolarSpo2TestDataFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        isFtpReady(session)
    }

    private func isWatchFacesConfigurationFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Bool {
        guard !BlePolarDeviceCapabilitiesUtility.isDeviceSensor(session.advertisementContent.polarDeviceType) else {
            return false
        }
        return discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) && hasPsFtpClient(session)
    }

    private func isWatchFacesConfigurationFeatureReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        return isFtpReady(session)
    }

    private func isFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID], feature: PolarBleSdkFeature) -> Bool {
        switch feature {
        case .feature_hr:
            return isHeartRateFeatureAvailable(session, discoveredServices)
        case .feature_device_info:
            return isDeviceInfoFeatureAvailable(session, discoveredServices)
        case .feature_battery_info:
            return isBatteryInfoFeatureAvailable(session, discoveredServices)
        case .feature_polar_online_streaming:
            return isOnlineStreamingFeatureAvailable(session, discoveredServices)
        case .feature_polar_offline_recording:
            return isOfflineRecordingFeatureAvailable(session, discoveredServices)
        case .feature_polar_offline_exercise_v2:
            return isOfflineExerciseV2FeatureAvailable(session, discoveredServices)
        case .feature_polar_h10_exercise_recording:
            return isH10ExerciseFeatureAvailable(session, discoveredServices)
        case .feature_polar_device_time_setup:
            return isPolarDeviceTimeFeatureAvailable(session, discoveredServices)
        case .feature_polar_sdk_mode:
            return isSdkModeFeatureAvailable(session, discoveredServices)
        case .feature_polar_led_animation:
            return isLedAnimationFeatureAvailable(session, discoveredServices)
        case .feature_polar_activity_data:
            return isPolarActivityDataFeatureAvailable(session, discoveredServices)
        case .feature_polar_training_data:
            return isPolarTrainingDataFeatureAvailable(session, discoveredServices)
        case .feature_polar_sleep_data:
            return isPolarSleepFeatureAvailable(session, discoveredServices)
        case .feature_polar_device_control:
            return isPolarDeviceControlFeatureAvailable(session, discoveredServices)
        case .feature_polar_firmware_update:
            return isPolarFirmwareUpdateFeatureAvailable(session, discoveredServices)
        case .feature_polar_features_configuration_service:
            return isFeatureConfigurationServiceFeatureAvailable(session, discoveredServices)
        case .feature_polar_file_transfer:
            return isPolarFileTransferFeatureAvailable(session, discoveredServices)
        case .feature_hts:
            return isHtsFeatureAvailable(session, discoveredServices)
        case .feature_polar_temperature_data:
            return isPolarTemperatureDataFeatureAvailable(session, discoveredServices)
        case .feature_polar_spo2_test_data:
            return isPolarSpo2TestDataFeatureAvailable(session, discoveredServices)
        case .feature_polar_watch_faces_configuration:
            return isWatchFacesConfigurationFeatureAvailable(session, discoveredServices)
        }
    }

    private func isFeatureReady(_ session: BleDeviceSession, feature: PolarBleSdkFeature) -> AnyPublisher<FeatureState, Never> {
        switch feature {
        case .feature_hr:
            return isHeartRateFeatureReady(session)
        case .feature_device_info:
            return isDeviceInfoFeatureReady(session)
        case .feature_battery_info:
            return isBatteryInfoFeatureReady(session)
        case .feature_polar_online_streaming:
            return isOnlineStreamingFeatureReady(session)
        case .feature_polar_offline_recording:
            return isOfflineRecordingFeatureReady(session)
        case .feature_polar_offline_exercise_v2:
            return isOfflineExerciseV2FeatureReady(session)
        case .feature_polar_h10_exercise_recording:
            return isH10ExerciseFeatureReady(session)
        case .feature_polar_device_time_setup:
            return isPolarDeviceTimeFeatureReady(session)
        case .feature_polar_sdk_mode:
            return isSdkModeFeatureReady(session)
        case .feature_polar_led_animation:
            return isLedAnimationFeatureReady(session)
        case .feature_polar_activity_data:
            return isPolarActivityDataFeatureReady(session)
        case .feature_polar_training_data:
            return isPolarTrainingDataFeatureReady(session)
        case .feature_polar_sleep_data:
            return isPolarSleepFeatureReady(session)
        case .feature_polar_device_control:
            return isPolarDeviceControlFeatureReady(session)
        case .feature_polar_firmware_update:
            return isPolarFirmwareUpdateFeatureReady(session)
        case .feature_polar_features_configuration_service:
            return isFeatureConfigurationServiceFeatureReady(session)
        case .feature_polar_file_transfer:
            return isPolarFileTransferFeatureReady(session)
        case .feature_hts:
            return isHtsFeatureReady(session)
        case .feature_polar_temperature_data:
            return isPolarTemperatureDataFeatureReady(session)
        case .feature_polar_spo2_test_data:
            return isPolarSpo2TestDataFeatureReady(session)
        case .feature_polar_watch_faces_configuration:
            return isWatchFacesConfigurationFeatureReady(session)
        }
    }

    private struct FeaturesStates {
        // Features that are not available, due e.g. missing service/client
        let notAvailable: Set<PolarBleSdkFeature>
        // Features that are ready
        let ready: Set<PolarBleSdkFeature>
        // Features not yet ready
        let notReady: Set<PolarBleSdkFeature>
    }

    private func checkFeaturesReady(session: BleDeviceSession, featuresToCheck: [PolarBleSdkFeature]) -> AnyPublisher<FeaturesStates, Never> {
        if featuresToCheck.isEmpty {
            return Just(FeaturesStates(notAvailable: [], ready: [], notReady: [])).eraseToAnyPublisher()
        }
        let publishers = featuresToCheck.map { feature in
            isFeatureReady(session, feature: feature)
                .map { state in (feature, state) }
                .eraseToAnyPublisher()
        }
        return Publishers.MergeMany(publishers)
            .collect()
            .map { results -> FeaturesStates in
                var notAvailable = Set<PolarBleSdkFeature>()
                var ready = Set<PolarBleSdkFeature>()
                var notReady = Set<PolarBleSdkFeature>()
                for (feature, state) in results {
                    switch state {
                    case .notAvailable: notAvailable.insert(feature)
                    case .ready: ready.insert(feature)
                    case .notReady: notReady.insert(feature)
                    }
                }
                return FeaturesStates(notAvailable: notAvailable, ready: ready, notReady: notReady)
            }
            .eraseToAnyPublisher()
    }

    private func isOfflineExerciseV2FeatureAvailable(
        _ session: BleDeviceSession,
        _ discoveredServices: [CBUUID]
    ) -> Bool {
        guard discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) else {
            return false
        }
        do {
            let ftpSession = try serviceClientUtils.sessionFtpClientReady(
                session.advertisementContent.polarDeviceIdUntouched
            )
            guard ftpSession.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) is BlePsFtpClient else {
                return false
            }
            return true
        } catch {
            return false
        }
    }

    // hook clients based on services available
    fileprivate func setupDevice(_ session: BleDeviceSession) {
        let deviceId = session.advertisementContent.polarDeviceIdUntouched.count != 0 ?
            session.advertisementContent.polarDeviceIdUntouched :
            session.address.uuidString

        // Collect all discovered services, then kick off feature readiness checking
        Task { [weak self] in
            guard let self = self else { return }
            var discoveredServices: [CBUUID] = []
            do {
                for try await uuid in session.monitorServicesDiscovered(true) {
                    discoveredServices.append(uuid)
                }
            } catch {
                self.logMessage("Error collecting services: \(error)")
                return
            }
            let requestedFeatures = self.features.isEmpty ? PolarBleSdkFeature.allCases : Array(self.features)
            self.makeFeaturesReadyCallbackWhenReady(session: session, discoveredServices: discoveredServices, requestedFeatures: requestedFeatures)
        }

        // Setup individual service clients as they are discovered
        Task { [weak self] in
            guard let self = self else { return }
            do {
                for try await uuid in session.monitorServicesDiscovered(true) {
                    guard let client = session.fetchGattClient(uuid) else { continue }
                    switch uuid {
                    case BleHrClient.HR_SERVICE:
                        self.startHrObserver(client as! BleHrClient, deviceId: deviceId)
                    case BleBasClient.BATTERY_SERVICE:
                        let batteryClient = client as! BleBasClient
                        Task { [weak self] in
                            guard let self = self else { return }
                            for try await level in batteryClient.monitorBatteryStatus(true) {
                                self.deviceInfoObserver?.batteryLevelReceived(deviceId, batteryLevel: UInt(level))
                            }
                        }
                        Task { [weak self] in
                            guard let self = self else { return }
                            for try await status in batteryClient.monitorChargingStatus(true) {
                                self.deviceInfoObserver?.batteryChargingStatusReceived(deviceId, chargingStatus: status)
                            }
                        }
                        Task { [weak self] in
                            guard let self = self else { return }
                            for try await state in batteryClient.monitorPowerSourcesState(true) {
                                self.deviceInfoObserver?.batteryPowerSourcesStateReceived(deviceId, powerSourcesState: state)
                            }
                        }
                    case BleDisClient.DIS_SERVICE:
                        let disClient = client as! BleDisClient
                        Task { [weak self] in
                            guard let self = self else { return }
                            for try await (cbuuid, value) in disClient.readDisInfo(true) {
                                self.deviceInfoObserver?.disInformationReceived(deviceId, uuid: cbuuid, value: value)
                            }
                        }
                        Task { [weak self] in
                            guard let self = self else { return }
                            for try await (cbuuid, value) in disClient.readDisInfoWithKeysAsStrings(true) {
                                self.deviceInfoObserver?.disInformationReceivedWithKeysAsStrings(deviceId, key: cbuuid, value: value)
                            }
                        }
                    case BlePmdClient.PMD_SERVICE:
                        Task {
                            try? await (client as! BlePmdClient).clientReady(true).awaitCompletion()
                        }
                    default:
                        break
                    }
                }
                self.logMessage("device setup completed")
            } catch {
                self.logMessage("device setup error: \(error)")
            }
        }
    }

    private func makeFeaturesReadyCallbackWhenReady(session: BleDeviceSession, discoveredServices: [CBUUID], requestedFeatures: [PolarBleSdkFeature]) {
        let deviceId = session.advertisementContent.polarDeviceIdUntouched.count != 0 ?
            session.advertisementContent.polarDeviceIdUntouched :
            session.address.uuidString

        let availableFeaturesSet = Set(requestedFeatures.filter { feature in
            isFeatureAvailable(session, discoveredServices, feature: feature)
        })

        if availableFeaturesSet.isEmpty {
            self.deviceFeaturesObserver?.bleSdkFeaturesReadiness(deviceId, ready: [], unavailable: requestedFeatures)
            return
        }

        var featuresToCheck = availableFeaturesSet
        var readyFeatures = Set<PolarBleSdkFeature>()
        var polledUnavailableFeatures = Set<PolarBleSdkFeature>()
        var allReadyCallbackSent = false
        let deadline = Date().addingTimeInterval(10.0)

        let cancellable = Timer.publish(every: 0.25, on: .main, in: .default)
            .autoconnect()
            .prepend(Date())
            .setFailureType(to: Never.self)
            .prefix(while: { [weak self] _ in
                self != nil && !featuresToCheck.isEmpty && Date() < deadline
                    && session.state == BleDeviceSession.DeviceSessionState.sessionOpen
            })
            .flatMap(maxPublishers: .max(1)) { [weak self] _ -> AnyPublisher<FeaturesStates, Never> in
                guard let self else { return Empty().eraseToAnyPublisher() }
                return self.checkFeaturesReady(session: session, featuresToCheck: Array(featuresToCheck))
            }
            .receive(on: DispatchQueue.main)
            .handleEvents(receiveCompletion: { [weak self] _ in
                guard let self else { return }
                if !allReadyCallbackSent {
                    let orderedReadyFeatures = requestedFeatures.filter { readyFeatures.contains($0) }
                    let orderedUnavailableFeatures = requestedFeatures.filter { !availableFeaturesSet.contains($0) || polledUnavailableFeatures.contains($0) }
                    BleLogger.trace("Timeout/disconnect, calling bleSdkFeaturesReadiness with ready: \(orderedReadyFeatures), unavailable: \(orderedUnavailableFeatures)")
                    self.deviceFeaturesObserver?.bleSdkFeaturesReadiness(deviceId, ready: orderedReadyFeatures, unavailable: orderedUnavailableFeatures)
                }
                _ = self.readyFeaturesLock.withLock { self.featureCheckSubscriptions.removeValue(forKey: deviceId) }
            })
            .sink { [weak self] states in
                guard let self else { return }
                let newlyReadyFeatures = states.ready
                let notAvailableFeatures = states.notAvailable

                for feature in newlyReadyFeatures where !readyFeatures.contains(feature) {
                    self.deviceFeaturesObserver?.bleSdkFeatureReady(deviceId, feature: feature)
                    readyFeatures.insert(feature)
                    _ = self.readyFeaturesLock.withLock {
                        self.readyFeaturesMap[deviceId, default: []].insert(feature)
                    }
                }

                featuresToCheck.subtract(newlyReadyFeatures)
                featuresToCheck.subtract(notAvailableFeatures)
                polledUnavailableFeatures.formUnion(notAvailableFeatures)

                if !newlyReadyFeatures.isEmpty || !notAvailableFeatures.isEmpty {
                    BleLogger.trace("Features ready: \(newlyReadyFeatures), not available: \(notAvailableFeatures), still checking: \(featuresToCheck)")
                }

                if !allReadyCallbackSent && featuresToCheck.isEmpty {
                    BleLogger.trace("All features ready, calling bleSdkFeaturesReadiness")
                    let orderedReadyFeatures = requestedFeatures.filter { availableFeaturesSet.contains($0) && !polledUnavailableFeatures.contains($0) }
                    let orderedUnavailableFeatures = requestedFeatures.filter { !availableFeaturesSet.contains($0) || polledUnavailableFeatures.contains($0) }
                    self.deviceFeaturesObserver?.bleSdkFeaturesReadiness(deviceId, ready: orderedReadyFeatures, unavailable: orderedUnavailableFeatures)
                    allReadyCallbackSent = true
                }
            }
        readyFeaturesLock.withLock { featureCheckSubscriptions[deviceId] = cancellable }
    }

    private func startHrObserver(_ client: BleHrClient, deviceId: String) {
        Task { [weak self] in
            do {
                for try await value in client.observeHrNotifications(true) {
                    self?.deviceHrObserver?.hrValueReceived(
                        deviceId, data: (hr: UInt8(value.hr), rrs: value.rrs, rrsMs: value.rrsMs,
                                         contact: value.sensorContact, contactSupported: value.sensorContactSupported))
                }
            } catch {
                self?.logMessage("HR observer error: \(error)")
            }
        }
    }
}

extension PolarBleApiImpl: BleLoggerProtocol {
    func logMessage(_ message: String, privacy: OSLogPrivacy) {
        logMessage(message)
    }

    func logMessage(_ message: String) {
        logger?.message(message)
    }
}

extension PolarBleApiImpl: PolarBleApi  {
    
    func cleanup() {
        _ = listener.removeAllSessions(
            Set(CollectionOfOne(BleDeviceSession.DeviceSessionState.sessionClosed)))
    }
    
    func polarFilter(_ enable: Bool) {
        listener.scanPreFilter = enable ? deviceFilter : nil
    }
    
    func searchForDevice() -> AsyncThrowingStream<PolarDeviceInfo, Error> {
        searchForDevice(withRequiredDeviceNamePrefix: nil)
    }

    func searchForDevice(withRequiredDeviceNamePrefix requiredDeviceNamePrefix: String? = "Polar") -> AsyncThrowingStream<PolarDeviceInfo, Error> {
        AsyncThrowingStream { continuation in
            var seenDeviceIds = Set<String>()
            let task = Task {
                do {
                    try await listener.search(serviceList, identifiers: nil, fetchKnownDevices: true)
                        .asyncForEach { sess in
                            let name = sess.advertisementContent.name
                            guard requiredDeviceNamePrefix == nil || requiredDeviceNamePrefix == "" || name.containsCaseInsensitive(requiredDeviceNamePrefix!) else { return }
                            let deviceId = sess.advertisementContent.polarDeviceIdUntouched
                            guard !seenDeviceIds.contains(deviceId) else { return }
                            seenDeviceIds.insert(deviceId)
                            let hasSAGRFCFileSystem = BlePolarDeviceCapabilitiesUtility.fileSystemType(
                                sess.advertisementContent.polarDeviceType) == .polarFileSystemV2
                            continuation.yield((deviceId,
                                address: sess.address,
                                rssi: Int(sess.advertisementContent.medianRssi),
                                name: name,
                                connectable: sess.advertisementContent.isConnectable,
                                hasSAGRFCFileSystem: hasSAGRFCFileSystem))
                        }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
    
    func searchForDevice(withNameContaining nameContaining: String? = "Polar") -> AsyncThrowingStream<PolarDeviceInfo, Error> {
        AsyncThrowingStream { continuation in
            var seenDeviceIds = Set<String>()
            let task = Task {
                do {
                    try await listener.search(serviceList, identifiers: nil, fetchKnownDevices: true)
                        .asyncForEach { sess in
                            let name = sess.advertisementContent.name
                            guard nameContaining == nil || nameContaining == "" || name.containsCaseInsensitive(nameContaining!) else { return }
                            let deviceId = sess.advertisementContent.polarDeviceIdUntouched
                            guard !seenDeviceIds.contains(deviceId) else { return }
                            seenDeviceIds.insert(deviceId)
                            let hasSAGRFCFileSystem = BlePolarDeviceCapabilitiesUtility.fileSystemType(
                                sess.advertisementContent.polarDeviceType) == .polarFileSystemV2
                            continuation.yield((deviceId,
                                address: sess.address,
                                rssi: Int(sess.advertisementContent.medianRssi),
                                name: name,
                                connectable: sess.advertisementContent.isConnectable,
                                hasSAGRFCFileSystem: hasSAGRFCFileSystem))
                        }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func startAutoConnectToDevice(_ rssi: Int, service: CBUUID?, polarDeviceType: String?) async throws {
        let publisher = listener.search(serviceList, identifiers: nil, fetchKnownDevices: true)
            .filter { (sess: BleDeviceSession) -> Bool in
                return sess.advertisementContent.medianRssi >= rssi &&
                sess.isConnectable() &&
                (polarDeviceType == nil || polarDeviceType == sess.advertisementContent.polarDeviceType) &&
                (service == nil || sess.advertisementContent.containsService(service!))
            }
            .prefix(1)
        try await publisher.asyncForEach { session in
            self.logMessage("auto connect search complete")
#if os(watchOS)
            session.connectionType = .directConnection
#endif
            self.listener.openSessionDirect(session)
        }
    }
    
    func connectToDevice(_ identifier: String) throws {
        var session = try serviceClientUtils.fetchSession(identifier)
        if session == nil ||
            session?.state == BleDeviceSession.DeviceSessionState.sessionClosed ||
            session?.state == BleDeviceSession.DeviceSessionState.sessionClosing {
            connectSubscriptions[identifier]?.cancel()
            session = nil
        }
        if session != nil {
#if os(watchOS)
            session!.connectionType = .directConnection
#endif
            self.listener.openSessionDirect(session!)
        } else {
            connectSubscriptions[identifier] = Task {
                do {
                    try await self.listener.search(self.serviceList, identifiers: nil, fetchKnownDevices: true)
                        .receive(on: self.scheduler)
                        .filter { (sess: BleDeviceSession) -> Bool in
                            identifier.contains("-") ? sess.address.uuidString == identifier : sess.advertisementContent.polarDeviceIdUntouched == identifier
                        }
                        .prefix(1)
                        .asyncForEach { value in
#if os(watchOS)
                            value.connectionType = .directConnection
#endif
                            self.listener.openSessionDirect(value)
                        }
                    self.logMessage("connect search complete")
                } catch {
                    self.logMessage("\(error)")
                }
            }
        }
    }
    
    func disconnectFromDevice(_ identifier: String) throws {
        if let session = try serviceClientUtils.fetchSession(identifier) {
            if (session.state == BleDeviceSession.DeviceSessionState.sessionOpen ||
                session.state == BleDeviceSession.DeviceSessionState.sessionOpening ||
                session.state == BleDeviceSession.DeviceSessionState.sessionOpenPark){
                listener.closeSessionDirect(session)
            }
        }
        connectSubscriptions.removeValue(forKey: identifier)?.cancel()
    }
    
    func isFeatureReady(_ identifier: String, feature: PolarBleSdkFeature) -> Bool {
        return readyFeaturesLock.withLock { readyFeaturesMap[identifier]?.contains(feature) ?? false }
    }
    
    func setLocalTime(_ identifier: String, time: Date, zone: TimeZone) async throws {
        let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        guard let pbLocalDateTime = PolarTimeUtils.dateToPbPftpSetLocalTime(time: time, zone: zone),
              let pbSystemDateTime = PolarTimeUtils.dateToPbPftpSetSystemTime(time: time) else {
            throw PolarErrors.dateTimeFormatFailed()
        }
        self.logMessage("set local time to \(time) and timeZone \(zone) in device \(identifier)")
        let paramsSetLocalTime = try pbLocalDateTime.serializedData()
        let paramsSetSystemTime = try pbSystemDateTime.serializedData()
        switch BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) {
        case .unknownFileSystem: break
        case .h10FileSystem:
            _ = try await client.query(Protocol_PbPFtpQuery.setLocalTime.rawValue, parameters: paramsSetLocalTime as NSData)
        case .polarFileSystemV2:
            async let t1 = client.query(Protocol_PbPFtpQuery.setLocalTime.rawValue, parameters: paramsSetLocalTime as NSData)
            async let t2 = client.query(Protocol_PbPFtpQuery.setSystemTime.rawValue, parameters: paramsSetSystemTime as NSData)
            _ = try await (t1, t2)
        }
    }
    
    func getLocalTime(_ identifier: String) async throws -> Date {
        let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        self.logMessage("get local time from device \(identifier)")
        switch BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) {
        case .h10FileSystem, .unknownFileSystem:
            throw PolarErrors.operationNotSupported
        case .polarFileSystemV2:
            let data = try await client.query(Protocol_PbPFtpQuery.getLocalTime.rawValue, parameters: nil)
            let result = try Protocol_PbPFtpSetLocalTimeParams(serializedBytes: data as Data)
            return try PolarTimeUtils.dateFromPbPftpLocalDateTime(result)
        }
    }
    
    func getLocalTimeWithZone(_ identifier: String) async throws -> (Date, TimeZone) {
        let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        self.logMessage("get local time and timezone from device \(identifier)")
        switch BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) {
        case .h10FileSystem, .unknownFileSystem:
            throw PolarErrors.operationNotSupported
        case .polarFileSystemV2:
            let data = try await client.query(Protocol_PbPFtpQuery.getLocalTime.rawValue, parameters: nil)
            let result = try Protocol_PbPFtpSetLocalTimeParams(serializedBytes: data as Data)
            let date = try PolarTimeUtils.dateFromPbPftpLocalDateTime(result)
            let offsetMinutes = Int(result.tzOffset)
            guard let tz = TimeZone(secondsFromGMT: offsetMinutes * 60) else {
                throw PolarErrors.messageDecodeFailed
            }
            return (date, tz)
        }
    }
    
    func getDiskSpace(_ identifier: String) async throws -> PolarDiskSpaceData {
        let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        let data = try await client.query(Protocol_PbPFtpQuery.getDiskSpace.rawValue, parameters: nil)
        let proto = try Protocol_PbPFtpDiskSpaceResult(serializedBytes: data as Data)
        return PolarDiskSpaceData.fromProto(proto: proto)
    }
    
    func startRecording(_ identifier: String, exerciseId: String, interval: RecordingInterval = RecordingInterval.interval_1s, sampleType: SampleType) async throws {
        guard exerciseId.count > 0 && exerciseId.count < 64 else { throw PolarErrors.invalidArgument() }
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        guard BlePolarDeviceCapabilitiesUtility.isRecordingSupported(session.advertisementContent.polarDeviceType) else { throw PolarErrors.operationNotSupported }
        var duration = PbDuration()
        duration.seconds = UInt32(interval.rawValue)
        var params = Protocol_PbPFtpRequestStartRecordingParams()
        params.recordingInterval = duration
        params.sampleDataIdentifier = exerciseId
        params.sampleType = sampleType == .hr ? PbSampleType.sampleTypeHeartRate : PbSampleType.sampleTypeRrInterval
        let queryParams = try params.serializedData()
        _ = try await client.query(Protocol_PbPFtpQuery.requestStartRecording.rawValue, parameters: queryParams as NSData)
    }

    
    func stopRecording(_ identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        guard BlePolarDeviceCapabilitiesUtility.isRecordingSupported(session.advertisementContent.polarDeviceType) else { throw PolarErrors.operationNotSupported }
        _ = try await client.query(Protocol_PbPFtpQuery.requestStopRecording.rawValue, parameters: nil)
    }

    
    func requestRecordingStatus(_ identifier: String) async throws -> PolarRecordingStatus {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        guard BlePolarDeviceCapabilitiesUtility.isRecordingSupported(session.advertisementContent.polarDeviceType) else { throw PolarErrors.operationNotSupported }
        let data = try await client.query(Protocol_PbPFtpQuery.requestRecordingStatus.rawValue, parameters: nil)
        let result = try Protocol_PbRequestRecordingStatusResult(serializedBytes: data as Data)
        return (ongoing: result.recordingOn, entryId: result.hasSampleDataIdentifier ? result.sampleDataIdentifier : "")
    }

    
    func removeExercise(_ identifier: String, entry: PolarExerciseEntry) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        var operation = Protocol_PbPFtpOperation()
        switch BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) {
        case .polarFileSystemV2:
            throw PolarErrors.polarBleSdkInternalException(description: "Other than H10 sensor is not supported by removeExercise API method. For other than H10 sensor use API deleteTrainingSession API method instead.")
        case .h10FileSystem:
            operation.command = .remove
            operation.path = entry.path
            let request = try operation.serializedData()
            _ = try await client.request(request)
        default:
            throw PolarErrors.operationNotSupported
        }
    }

    
    func startListenForPolarHrBroadcasts(_ identifiers: Set<String>?) -> AsyncThrowingStream<PolarHrBroadcastData, Error> {
        BleLogger.trace("Start Hr broadcast listener. Filtering: \(identifiers != nil)")
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    try await listener.search(serviceList, identifiers: nil)
                        .asyncForEach { session in
                            let hasSAGRFCFileSystem = BlePolarDeviceCapabilitiesUtility.fileSystemType(
                                session.advertisementContent.polarDeviceType) == .polarFileSystemV2
                            guard (identifiers == nil || identifiers!.contains(session.advertisementContent.polarDeviceIdUntouched)) &&
                                    session.advertisementContent.polarHrAdvertisementData.isPresent &&
                                    session.advertisementContent.polarHrAdvertisementData.isHrDataUpdated else { return }
                            continuation.yield((
                                deviceInfo: (session.advertisementContent.polarDeviceIdUntouched,
                                             address: session.address,
                                             rssi: Int(session.advertisementContent.rssiFilter.rssi),
                                             name: session.advertisementContent.name,
                                             connectable: session.advertisementContent.isConnectable,
                                             hasSAGRFCFileSystem: hasSAGRFCFileSystem),
                                hr: session.advertisementContent.polarHrAdvertisementData.hrValueForDisplay,
                                batteryStatus: session.advertisementContent.polarHrAdvertisementData.batteryStatus))
                        }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
    
    func requestStreamSettings(_ identifier: String, feature: PolarDeviceDataType) async throws -> PolarSensorSetting {
        BleLogger.trace("Request online stream settings. Feature: \(feature) Device: \(identifier)")
        switch feature {
        case .ecg: return try await querySettings(identifier, type: .ecg, recordingType: .online)
        case .acc: return try await querySettings(identifier, type: .acc, recordingType: .online)
        case .ppg: return try await querySettings(identifier, type: .ppg, recordingType: .online)
        case .magnetometer: return try await querySettings(identifier, type: .mgn, recordingType: .online)
        case .gyro: return try await querySettings(identifier, type: .gyro, recordingType: .online)
        case .temperature: return try await querySettings(identifier, type: .temperature, recordingType: .online)
        case .skinTemperature: return try await querySettings(identifier, type: .skinTemperature, recordingType: .online)
        case .pressure: return try await querySettings(identifier, type: .pressure, recordingType: .online)
        case .ppi, .hr: throw PolarErrors.operationNotSupported
        }
    }

    
    func requestFullStreamSettings(_ identifier: String, feature: PolarDeviceDataType) async throws -> PolarSensorSetting {
        BleLogger.trace("Request full online stream settings. Feature: \(feature) Device: \(identifier)")
        switch feature {
        case .ecg: return try await queryFullSettings(identifier, type: .ecg, recordingType: .online)
        case .acc: return try await queryFullSettings(identifier, type: .acc, recordingType: .online)
        case .ppg: return try await queryFullSettings(identifier, type: .ppg, recordingType: .online)
        case .magnetometer: return try await queryFullSettings(identifier, type: .mgn, recordingType: .online)
        case .gyro: return try await queryFullSettings(identifier, type: .gyro, recordingType: .online)
        case .ppi, .hr, .temperature, .pressure, .skinTemperature: throw PolarErrors.operationNotSupported
        }
    }

    
    func requestOfflineRecordingSettings(_ identifier: String, feature: PolarDeviceDataType) async throws -> PolarSensorSetting {
        BleLogger.trace("Request offline stream settings. Feature: \(feature) Device: \(identifier)")
        switch feature {
        case .ecg: return try await querySettings(identifier, type: .ecg, recordingType: .offline)
        case .acc: return try await querySettings(identifier, type: .acc, recordingType: .offline)
        case .ppg: return try await querySettings(identifier, type: .ppg, recordingType: .offline)
        case .magnetometer: return try await querySettings(identifier, type: .mgn, recordingType: .offline)
        case .gyro: return try await querySettings(identifier, type: .gyro, recordingType: .offline)
        case .ppi, .hr: throw PolarErrors.operationNotSupported
        case .temperature: return try await querySettings(identifier, type: .temperature, recordingType: .offline)
        case .skinTemperature: return try await querySettings(identifier, type: .skinTemperature, recordingType: .offline)
        case .pressure: return try await querySettings(identifier, type: .pressure, recordingType: .offline)
        }
    }

    
    func requestFullOfflineRecordingSettings(_ identifier: String, feature: PolarDeviceDataType) async throws -> PolarSensorSetting {
        BleLogger.trace("Request full offline stream settings. Feature: \(feature) Device: \(identifier)")
        switch feature {
        case .ecg: return try await queryFullSettings(identifier, type: .ecg, recordingType: .offline)
        case .acc: return try await queryFullSettings(identifier, type: .acc, recordingType: .offline)
        case .ppg: return try await queryFullSettings(identifier, type: .ppg, recordingType: .offline)
        case .magnetometer: return try await queryFullSettings(identifier, type: .mgn, recordingType: .offline)
        case .gyro: return try await queryFullSettings(identifier, type: .gyro, recordingType: .offline)
        case .ppi, .hr, .pressure: throw PolarErrors.operationNotSupported
        case .temperature: return try await queryFullSettings(identifier, type: .temperature, recordingType: .offline)
        case .skinTemperature: return try await queryFullSettings(identifier, type: .skinTemperature, recordingType: .offline)
        }
    }

    
    func getAvailableOfflineRecordingDataTypes(_ identifier: String) async throws -> Set<PolarDeviceDataType> {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        let pmdFeature = try await client.readFeature(true)
        var deviceData: Set<PolarDeviceDataType> = Set()
        if pmdFeature.contains(.ecg) { deviceData.insert(.ecg) }
        if pmdFeature.contains(.acc) { deviceData.insert(.acc) }
        if pmdFeature.contains(.ppg) { deviceData.insert(.ppg) }
        if pmdFeature.contains(.ppi) { deviceData.insert(.ppi) }
        if pmdFeature.contains(.gyro) { deviceData.insert(.gyro) }
        if pmdFeature.contains(.mgn) { deviceData.insert(.magnetometer) }
        if pmdFeature.contains(.offline_hr) { deviceData.insert(.hr) }
        if pmdFeature.contains(.temperature) { deviceData.insert(.temperature) }
        if pmdFeature.contains(.skinTemperature) { deviceData.insert(.skinTemperature) }
        return deviceData
    }

    
    func getOfflineRecordingStatus(_ identifier: String) async throws -> [PolarDeviceDataType: Bool] {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Get offline recording status. Device: \(identifier)")
        let status = try await client.readMeasurementStatus()
        var activeOfflineRecordings = [PolarDeviceDataType: Bool]()
        for element in status {
            let polarFeature = try PolarDataUtils.mapToPolarFeature(from: element.0)
            activeOfflineRecordings[polarFeature] = (element.1 == .offline_measurement_active || element.1 == .online_offline_measurement_active)
        }
        return activeOfflineRecordings
    }

    
    func listOfflineRecordings(_ identifier: String) -> AsyncThrowingStream<PolarOfflineRecordingEntry, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    // Try fast listing (PMDFiles.txt) first, fall back to recursive
                    let pmdData = try await self.deviceSupportsFasterOfflineRecordListing(identifier: identifier)
                    if !pmdData.isEmpty {
                        let entries = try PolarOfflineRecordingUtils.listOfflineRecordingsV2(fileData: Data(pmdData))
                        for entry in entries { continuation.yield(entry) }
                    } else {
                        let entries = try await self.fetchRecursive("/U/0/", client: client, condition: { entry in
                            entry.matches("^([0-9]{8})(\\/)") ||
                            entry.matches("^([0-9]{6})(\\/)") ||
                            entry == "R/" ||
                            entry.contains(".REC")
                        })
                        for entry in entries {
                            let components = entry.name.split(separator: "/")
                            let dateFormatter = DateFormatter()
                            dateFormatter.calendar = .init(identifier: .iso8601)
                            dateFormatter.locale = Locale(identifier: "en_US_POSIX")
                            dateFormatter.dateFormat = "yyyyMMddHHmmss"
                            guard components.count >= 6,
                                  entry.size > 0,
                                  let date = dateFormatter.date(from: String(components[2] + components[4])) else { continue }
                            guard let pmdMeasurementType = try? OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: String(components[5])),
                                  let type = try? PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType) else { continue }
                            continuation.yield(PolarOfflineRecordingEntry(path: entry.name, size: UInt(entry.size), date: date, type: type))
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }


    private func deviceSupportsFasterOfflineRecordListing(identifier: String) async throws -> [UInt8] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = PMDFilePath
        do {
            let request = try operation.serializedData()
            let data = try await client.request(request)
            return data.isEmpty ? [] : [UInt8](data)
        } catch {
            return []
        }
    }

    
    private func loadFileorEmpty(path: String, client: BlePsFtpClient) async throws -> [UInt8] {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        let requestData = try operation.serializedData()
        do {
            let data = try await client.request(requestData)
            return [UInt8](data)
        } catch {
            return []
        }
    }


    func getOfflineRecord(
        _ identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ) async throws -> PolarOfflineRecordingData {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
            throw PolarErrors.operationNotSupported
        }

        let count = try await getSubRecordingCount(identifier: identifier, entry: entry)
        let indices = count > 0 ? Array(0..<count) : [0]

        var polarAccData: PolarOfflineRecordingData?
        var polarGyroData: PolarOfflineRecordingData?
        var polarMagData: PolarOfflineRecordingData?
        var polarPpgData: PolarOfflineRecordingData?
        var polarPpiData: PolarOfflineRecordingData?
        var polarHrData: PolarOfflineRecordingData?
        var polarTemperatureData: PolarOfflineRecordingData?
        var polarSkinTemperatureData: PolarOfflineRecordingData?
        var polarEmptyData: PolarOfflineRecordingData?
        let lastTimestamp: UInt64 = 0

        for subRecordingIndex in indices {
            let subRecordingPath: String
            if entry.path.range(of: ".*\\.REC$", options: .regularExpression) != nil && count > 0 {
                subRecordingPath = entry.path.replacingOccurrences(of: "\\d(?=\\.REC$)", with: "\(subRecordingIndex)", options: .regularExpression)
            } else {
                subRecordingPath = entry.path
            }
            var operation = Protocol_PbPFtpOperation()
            operation.command = .get
            operation.path = subRecordingPath.isEmpty ? entry.path : subRecordingPath
            let request = try operation.serializedData()
            BleLogger.trace("Offline record get. Device: \(identifier) Path: \(subRecordingPath) Secret used: \(secret != nil)")

            let dataResult = try await client.request(request)
            do {
                let pmdSecret = try secret.map { try PolarDataUtils.mapToPmdSecret(from: $0) }
                let offlineRecordingData = try OfflineRecordingData<Any>.parseDataFromOfflineFile(
                    fileData: dataResult as Data,
                    type: PolarDataUtils.mapToPmdClientMeasurementType(from: entry.type),
                    secret: pmdSecret,
                    lastTimestamp: lastTimestamp
                )
                let settings: PolarSensorSetting = offlineRecordingData.recordingSettings?.mapToPolarSettings() ?? PolarSensorSetting()
                switch offlineRecordingData.data {
                case let accData as AccData:
                    polarAccData = processAccData(accData, polarAccData, offlineRecordingData, settings)
                case let gyroData as GyrData:
                    polarGyroData = processGyroData(gyroData, polarGyroData, offlineRecordingData, settings)
                case let magData as MagData:
                    polarMagData = processMagData(magData, polarMagData, offlineRecordingData, settings)
                case let ppgData as PpgData:
                    polarPpgData = processPpgData(ppgData, polarPpgData, offlineRecordingData, settings)
                case let ppiData as PpiData:
                    polarPpiData = processPpiData(ppiData, polarPpiData, offlineRecordingData)
                case let hrData as OfflineHrData:
                    polarHrData = processHrData(hrData, polarHrData, offlineRecordingData)
                case let temperatureData as TemperatureData:
                    polarTemperatureData = processTemperatureData(temperatureData, polarTemperatureData, offlineRecordingData)
                case let skinTemperatureData as SkinTemperatureData:
                    polarSkinTemperatureData = processSkinTemperatureData(skinTemperatureData, polarSkinTemperatureData, offlineRecordingData)
                case _ as EmptyData:
                    polarEmptyData = processEmptyData(offlineRecordingData)
                default:
                    throw PolarErrors.polarOfflineRecordingError(description: "GetOfflineRecording failed. Data type is not supported.")
                }
            } catch {
                throw PolarErrors.polarOfflineRecordingError(description: "Failed to parse data: \(error)")
            }
        }

        for dataObject in [polarAccData, polarGyroData, polarMagData, polarPpgData, polarPpiData, polarHrData, polarTemperatureData, polarSkinTemperatureData, polarEmptyData] {
            if let data = dataObject { return data }
        }
        throw PolarErrors.polarOfflineRecordingError(description: "Invalid data")
    }

    public func getOfflineRecordWithProgress(
        _ identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ) -> AsyncThrowingStream<PolarOfflineRecordingResult, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(
                        session.advertisementContent.polarDeviceType) else {
                        continuation.finish(throwing: PolarErrors.operationNotSupported)
                        return
                    }

                    let totalBytes = Int64(entry.size)
                    let lock = NSLock()

                    continuation.yield(.progress(PolarOfflineRecordingProgress(
                        bytesDownloaded: 0,
                        totalBytes: totalBytes,
                        progressPercent: 0
                    )))

                    class ProgressCallbackImpl: BlePsFtpProgressCallback {
                        let totalBytes: Int64
                        let continuation: AsyncThrowingStream<PolarOfflineRecordingResult, Error>.Continuation
                        let lock: NSLock
                        var accumulatedBytes: Int64 = 0

                        init(
                            totalBytes: Int64,
                            continuation: AsyncThrowingStream<PolarOfflineRecordingResult, Error>.Continuation,
                            lock: NSLock
                        ) {
                            self.totalBytes = totalBytes
                            self.continuation = continuation
                            self.lock = lock
                        }

                        func onProgressUpdate(bytesReceived: Int) {
                            lock.lock()
                            accumulatedBytes += Int64(bytesReceived)
                            let currentBytes = accumulatedBytes
                            lock.unlock()
                            let percent = totalBytes > 0 ? Int((currentBytes * 100) / totalBytes) : 0
                            let clampedPercent = min(max(percent, 0), 100)
                            continuation.yield(.progress(PolarOfflineRecordingProgress(
                                bytesDownloaded: currentBytes,
                                totalBytes: totalBytes,
                                progressPercent: clampedPercent
                            )))
                        }
                    }

                    let progressCallback = ProgressCallbackImpl(
                        totalBytes: totalBytes,
                        continuation: continuation,
                        lock: lock
                    )
                    client.progressCallback = progressCallback

                    do {
                        let data = try await self.getOfflineRecord(identifier, entry: entry, secret: secret)
                        client.progressCallback = nil
                        continuation.yield(.progress(PolarOfflineRecordingProgress(
                            bytesDownloaded: totalBytes,
                            totalBytes: totalBytes,
                            progressPercent: 100
                        )))
                        continuation.yield(.complete(data))
                        continuation.finish()
                    } catch {
                        client.progressCallback = nil
                        continuation.finish(throwing: error)
                    }
                } catch {
                    continuation.finish(throwing: self.handleError(error))
                }
            }
        }
    }

    func getSubRecordingCount(identifier: String, entry: PolarOfflineRecordingEntry) async throws -> Int {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        let directoryPath = entry.path.components(separatedBy: "/").dropLast().joined(separator: "/") + "/"
        let fileType = try mapDeviceDataTypeToOfflineRecordingFileName(type: entry.type)
        operation.path = directoryPath
        do {
            let data = try await client.request(try operation.serializedData())
            let directory = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            return directory.entries.filter { $0.name.hasPrefix(fileType) }.count
        } catch {
            if case let BlePsFtpException.responseError(code) = error, code == 103 { return 0 }
            throw handleError(error)
        }
    }

    
    func getSubRecordings(identifier: String, entry: PolarOfflineRecordingEntry) async throws -> [String] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        let directoryPath = entry.path.components(separatedBy: "/").dropLast().joined(separator: "/") + "/"
        let type = entry.path.components(separatedBy: "/").last?.replacingOccurrences(of: "[0-9]+.REC", with: "", options: .regularExpression).replacingOccurrences(of: " ", with: "")
        operation.path = directoryPath
        var parentDir = ""
        if let lastSlashIndex = entry.path.dropLast().lastIndex(of: "/") {
            parentDir = String(entry.path[...lastSlashIndex])
        }
        do {
            let data = try await client.request(try operation.serializedData())
            let directory = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            return directory.entries.compactMap { e in e.name.contains(type ?? "") ? parentDir + e.name : nil }
        } catch {
            if case let BlePsFtpException.responseError(code) = error, code == 103 { return [entry.path] }
            throw handleError(error)
        }
    }


    func listSplitOfflineRecordings(_ identifier: String) -> AsyncThrowingStream<PolarOfflineRecordingEntry, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                        continuation.finish(throwing: PolarErrors.operationNotSupported)
                        return
                    }
                    BleLogger.trace("Start offline recording listing in device: \(identifier)")
                    let entries = try await self.fetchRecursive("/U/0/", client: client, condition: { entry in
                        entry.matches("^([0-9]{8})(\\/)") ||
                        entry.matches("^([0-9]{6})(\\/)") ||
                        entry == "R/" ||
                        entry.contains(".REC")
                    })
                    for entry in entries {
                        let components = entry.name.split(separator: "/")
                        let dateFormatter = DateFormatter()
                        dateFormatter.calendar = .init(identifier: .iso8601)
                        dateFormatter.locale = Locale(identifier: "en_US_POSIX")
                        dateFormatter.dateFormat = "yyyyMMddHHmmss"
                        guard components.count >= 6,
                              let date = dateFormatter.date(from: String(components[2] + components[4])) else { continue }
                        guard let pmdMeasurementType = try? OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: String(components[5])),
                              let type = try? PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType) else { continue }
                        continuation.yield(PolarOfflineRecordingEntry(path: entry.name, size: UInt(entry.size), date: date, type: type))
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    
    func getSplitOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret?) async throws -> PolarOfflineRecordingData {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else { throw PolarErrors.operationNotSupported }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = entry.path
        let request = try operation.serializedData()
        BleLogger.trace("Offline record get. Device: \(identifier) Path: \(entry.path) Secret used: \(secret != nil)")
        let data = try await client.request(request)
        var pmdSecret: PmdSecret? = nil
        if let s = secret { pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s) }
        let type: PmdMeasurementType = PolarDataUtils.mapToPmdClientMeasurementType(from: entry.type)
        let offlineRecData = try OfflineRecordingData<Any>.parseDataFromOfflineFile(fileData: data as Data, type: type, secret: pmdSecret)
        let settings = offlineRecData.recordingSettings?.mapToPolarSettings() ?? PolarSensorSetting()
        switch offlineRecData.data {
        case is AccData: return .accOfflineRecordingData((offlineRecData.data as! AccData).mapToPolarData(), startTime: offlineRecData.startTime, settings: settings)
        case is GyrData: return .gyroOfflineRecordingData((offlineRecData.data as! GyrData).mapToPolarData(), startTime: offlineRecData.startTime, settings: settings)
        case is MagData: return .magOfflineRecordingData((offlineRecData.data as! MagData).mapToPolarData(), startTime: offlineRecData.startTime, settings: settings)
        case is PpgData: return .ppgOfflineRecordingData((offlineRecData.data as! PpgData).mapToPolarData(), startTime: offlineRecData.startTime, settings: settings)
        case is PpiData: return .ppiOfflineRecordingData((offlineRecData.data as! PpiData).mapToPolarData(), startTime: offlineRecData.startTime)
        case is OfflineHrData: return .hrOfflineRecordingData((offlineRecData.data as! OfflineHrData).mapToPolarData(), startTime: offlineRecData.startTime)
        case is TemperatureData: return .temperatureOfflineRecordingData((offlineRecData.data as! TemperatureData).mapToPolarData(), startTime: offlineRecData.startTime)
        case is SkinTemperatureData: return .skinTemperatureOfflineRecordingData((offlineRecData.data as! SkinTemperatureData).mapToPolarData(), startTime: offlineRecData.startTime)
        case is EmptyData: return .emptyData(startTime: offlineRecData.startTime)
        default: throw PolarErrors.polarOfflineRecordingError(description: "GetOfflineRecording failed. Data type is not supported.")
        }
    }

    
    func removeOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry) async throws {
        BleLogger.trace("Remove offline record. Device: \(identifier) Path: \(entry.path)")
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) is BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else { throw PolarErrors.operationNotSupported }
        let subrecords = try await getSubRecordings(identifier: identifier, entry: entry)
        try await fileUtils.removeMultipleFiles(identifier: identifier, filePaths: subrecords)
        let indices = entry.path.findIndices(lookable: "/")
        var indexCount = 1
        var currentDir = String(entry.path[...indices[indices.count - indexCount]])
        while currentDir != "/U/0/" {
            try await fileUtils.deleteDataDirectory(identifier: identifier, directoryPath: currentDir)
            indexCount += 1
            currentDir = String(entry.path[...indices[indices.count - indexCount]])
        }
    }

    
    func removeOfflineRecords(_ identifier: String, entry: PolarOfflineRecordingEntry) async throws -> Bool {
        BleLogger.trace("Remove offline record. Device: \(identifier) Path: \(entry.path)")
        do {
            try await removeOfflineRecord(identifier, entry: entry)
            return true
        } catch {
            return false
        }
    }


    func mapDeviceDataTypeToOfflineRecordingFileName(type: PolarDeviceDataType) throws -> String {
         switch type {
             case .acc: return "ACC"
             case .gyro: return "GYRO"
             case .magnetometer  : return "MAG"
             case .ppg: return "PPG"
             case .ppi: return "PPI"
             case .hr: return "HR"
             case .temperature: return "TEMP"
             case .skinTemperature: return "SKINTEMP"
             default: throw BleGattException.gattDataError(description: "Unknown pmd measurement type: \(type)")
         }
    }

    func startOfflineRecording(_ identifier: String, feature: PolarDeviceDataType, settings: PolarSensorSetting?, secret: PolarRecordingSecret?) async throws {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        var pmdSecret: PmdSecret? = nil
        if let s = secret { pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s) }
        try await client.startMeasurement(
            PolarDataUtils.mapToPmdClientMeasurementType(from: feature),
            settings: (settings ?? PolarSensorSetting()).map2PmdSetting(),
            .offline, pmdSecret)
    }

    
    func stopOfflineRecording(_ identifier: String, feature: PolarDeviceDataType) async throws {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Stop offline recording. Feature: \(feature) Device \(identifier)")
        let measurementType = PolarDataUtils.mapToPmdClientMeasurementType(from: feature)
        try await client.stopMeasurement(measurementType)
        // Poll until measurement is no longer active (up to 5 seconds)
        for _ in 0..<10 {
            try await Task.sleep(nanoseconds: 500_000_000)
            let statuses = try await client.readMeasurementStatus()
            if let status = statuses.first(where: { $0.0 == measurementType }) {
                if status.1 == .no_measurement_active { return }
            } else {
                return
            }
        }
    }

    
    func setOfflineRecordingTrigger(_ identifier: String, trigger: PolarOfflineRecordingTrigger, secret: PolarRecordingSecret?) async throws {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Setup offline recording trigger. Device: \(identifier) Secret used: \(secret != nil)")
        let pmdOfflineTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        var pmdSecret: PmdSecret? = nil
        if let s = secret { pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s) }
        try await client.setOfflineRecordingTrigger(offlineRecordingTrigger: pmdOfflineTrigger, secret: pmdSecret)
    }

    
    func getOfflineRecordingTriggerSetup(_ identifier: String) async throws -> PolarOfflineRecordingTrigger {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Get offline recording trigger setup. Device: \(identifier)")
        let trigger = try await client.getOfflineRecordingTriggerStatus()
        return try PolarDataUtils.mapToPolarOfflineTrigger(from: trigger)
    }

    
    func getAvailableOnlineStreamDataTypes(_ identifier: String) async throws -> Set<PolarDeviceDataType> {
        var deviceData: Set<PolarDeviceDataType> = Set()
        if let hrSession = try? serviceClientUtils.sessionHrClientReady(identifier),
           hrSession.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient != nil {
            deviceData.insert(.hr)
        }
        let pmdSession = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let pmdClient = pmdSession.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return deviceData }
        let pmdFeature = try await pmdClient.readFeature(true)
        if pmdFeature.contains(.ecg) { deviceData.insert(.ecg) }
        if pmdFeature.contains(.acc) { deviceData.insert(.acc) }
        if pmdFeature.contains(.ppg) { deviceData.insert(.ppg) }
        if pmdFeature.contains(.ppi) { deviceData.insert(.ppi) }
        if pmdFeature.contains(.gyro) { deviceData.insert(.gyro) }
        if pmdFeature.contains(.mgn) { deviceData.insert(.magnetometer) }
        if pmdFeature.contains(.temperature) { deviceData.insert(.temperature) }
        if pmdFeature.contains(.pressure) { deviceData.insert(.pressure) }
        if pmdFeature.contains(.skinTemperature) { deviceData.insert(.skinTemperature) }
        return deviceData
    }

    
    func getAvailableHRServiceDataTypes(identifier: String) async throws -> Set<PolarDeviceDataType> {
        let session = try serviceClientUtils.sessionServiceReady(identifier, service: BleHrClient.HR_SERVICE)
        var deviceData: Set<PolarDeviceDataType> = Set()
        if let bleHrClient = session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient, bleHrClient.isServiceDiscovered() {
            deviceData.insert(.hr)
        }
        return deviceData
    }

    
    func startEcgStreaming(_ identifier: String, settings: PolarSensorSetting) -> AsyncThrowingStream<PolarEcgData, Error> {
        return startStreaming(identifier, type: .ecg, settings: settings) { client in client.observeEcg().map { $0.mapToPolarData() } }
    }

    
    func startAccStreaming(_ identifier: String, settings: PolarSensorSetting) -> AsyncThrowingStream<PolarAccData, Error> {
        return startStreaming(identifier, type: .acc, settings: settings) { client in client.observeAcc().map { $0.mapToPolarData() } }
    }

    func startGyroStreaming(_ identifier: String, settings: PolarSensorSetting) -> AsyncThrowingStream<PolarGyroData, Error> {
        return startStreaming(identifier, type: .gyro, settings: settings) { client in client.observeGyro().map { $0.mapToPolarData() } }
    }

    
    func startMagnetometerStreaming(_ identifier: String, settings: PolarSensorSetting) -> AsyncThrowingStream<PolarMagnetometerData, Error> {
        return startStreaming(identifier, type: .mgn, settings: settings) { client in client.observeMagnetometer().map { $0.mapToPolarData() } }
    }


    
    func startPpgStreaming(_ identifier: String, settings: PolarSensorSetting) -> AsyncThrowingStream<PolarPpgData, Error> {
        return startStreaming(identifier, type: .ppg, settings: settings) { client in client.observePpg().map { $0.mapToPolarData() } }
    }

    
    func startPpiStreaming(_ identifier: String) -> AsyncThrowingStream<PolarPpiData, Error> {
        return startStreaming(identifier, type: .ppi, settings: PolarSensorSetting()) { client in client.observePpi().map { $0.mapToPolarData() } }
    }

    
    func startHrStreaming(_ identifier: String) -> AsyncThrowingStream<PolarHrData, Error> {
        // Create or reuse a multicast for this device so the BLE upstream is shared.
        let multicast: MulticastAsyncStream<BleHrClient.BleHrNotification> = readyFeaturesLock.withLock {
            if let existing = hrMulticasts[identifier] { return existing }
            let newMulticast = MulticastAsyncStream<BleHrClient.BleHrNotification> { [weak self] in
                AsyncThrowingStream { continuation in
                    guard let self else { continuation.finish(throwing: BleGattException.gattDisconnected); return }
                    let task = Task {
                        do {
                            let session = try self.serviceClientUtils.sessionServiceReady(identifier, service: BleHrClient.HR_SERVICE)
                            guard let bleHrClient = session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient else {
                                continuation.finish(throwing: PolarErrors.serviceNotFound); return
                            }
                            for try await n in bleHrClient.observeHrNotifications(true) { continuation.yield(n) }
                            continuation.finish()
                        } catch { continuation.finish(throwing: error) }
                    }
                    continuation.onTermination = { _ in task.cancel() }
                }
            }
            hrMulticasts[identifier] = newMulticast
            return newMulticast
        }

        // Pass the HR gatt client's transport for the connection check.
        let transport: (any BleAttributeTransportProtocol)? = (try? serviceClientUtils.sessionServiceReady(identifier, service: BleHrClient.HR_SERVICE))
            .flatMap { ($0.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient)?.gattServiceTransmitter }
        let consumer = multicast.makeStream(transport: transport, checkConnection: true)
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    for try await notification in consumer {
                        let hrData: PolarHrData = [(hr: UInt8(notification.hr), ppgQuality: 0, correctedHr: 0, rrsMs: notification.rrsMs, rrAvailable: notification.rrPresent, contactStatus: notification.sensorContact, contactStatusSupported: notification.sensorContactSupported)]
                        continuation.yield(hrData)
                    }
                    continuation.finish()
                } catch { continuation.finish(throwing: self.handleError(error)) }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }


    func startTemperatureStreaming(_ identifier: String, settings: PolarSensorSetting) -> AsyncThrowingStream<PolarTemperatureData, Error> {
        return startStreaming(identifier, type: .temperature, settings: settings) { client in client.observeTemperature().map { $0.mapToPolarData() } }
    }


    func startPressureStreaming(_ identifier: String, settings: PolarSensorSetting) -> AsyncThrowingStream<PolarPressureData, Error> {
        return startStreaming(identifier, type: .pressure, settings: settings) { client in client.observePressure().map { $0.mapToPolarData() } }
    }

    
    func startSkinTemperatureStreaming(_ identifier: String, settings: PolarSensorSetting) -> AsyncThrowingStream<PolarTemperatureData, Error> {
        return startStreaming(identifier, type: .skinTemperature, settings: settings) { client in client.observeSkinTemperature().map { $0.mapToPolarData() } }
    }


    func fetchExercise(_ identifier: String, entry: PolarExerciseEntry) async throws -> PolarExerciseData {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.operationNotSupported }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = entry.path
        let request = try operation.serializedData()
        let data = try await client.request(request)
        let samples = try Data_PbExerciseSamples(serializedBytes: data as Data)
        var exSamples = [UInt32]()
        if samples.hasRrSamples && samples.rrSamples.rrIntervals.count != 0 {
            for rrInterval in samples.rrSamples.rrIntervals { exSamples.append(rrInterval) }
        } else {
            for hrSample in samples.heartRateSamples { exSamples.append(hrSample) }
        }
        return (samples.recordingInterval.seconds, samples: exSamples)
    }

    
    func listExercises(_ identifier: String) -> AsyncThrowingStream<PolarExerciseEntry, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
                    let fsType = BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType)
                    let entries: [(name: String, size: UInt64)]
                    let condition: (String) -> Bool
                    if fsType == .polarFileSystemV2 {
                        condition = { e in e.matches("^([0-9]{8})(\\/)") || e.matches("^([0-9]{6})(\\/)") || e == "E/" || e == "SAMPLES.BPB" || e == "00/" }
                        entries = try await self.fetchRecursive("/U/0/", client: client, condition: condition)
                    } else if fsType == .h10FileSystem {
                        condition = { e in e.hasSuffix("/") || e == "SAMPLES.BPB" }
                        entries = try await self.fetchRecursive("/", client: client, condition: condition)
                    } else {
                        continuation.finish(throwing: PolarErrors.operationNotSupported)
                        return
                    }
                    let dateFormatter = DateFormatter()
                    dateFormatter.calendar = .init(identifier: .iso8601)
                    dateFormatter.locale = Locale(identifier: "en_US_POSIX")
                    dateFormatter.dateFormat = "yyyyMMddHHmmss"
                    for entry in entries {
                        let components = entry.name.split(separator: "/")
                        if fsType == .polarFileSystemV2, components.count >= 5 {
                            if let date = dateFormatter.date(from: String(components[2] + components[4])) {
                                continuation.yield((entry.name, date: date, entryId: String(components[2] + components[4])))
                            }
                        } else if fsType == .h10FileSystem, !components.isEmpty {
                            continuation.yield((entry.name, date: Date(), entryId: String(components[0])))
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: PolarErrors.deviceError(description: "\(error)"))
                }
            }
        }
    }


    @available(*, deprecated, renamed: "listExercises")
    func fetchStoredExerciseList(_ identifier: String) -> AsyncThrowingStream<PolarExerciseEntry, Error> {
        return listExercises(identifier)
    }


    func setLedConfig(_ identifier: String, ledConfig: LedConfig) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var builder = Protocol_PbPFtpOperation()
        builder.command = .put
        builder.path = LedConfig.LED_CONFIG_FILENAME
        let proto = try builder.serializedData()
        let sdkModeLedByte: UInt8 = ledConfig.sdkModeLedEnabled ? LedConfig.LED_ANIMATION_ENABLE_BYTE : LedConfig.LED_ANIMATION_DISABLE_BYTE
        let ppiModeLedByte: UInt8 = ledConfig.ppiModeLedEnabled ? LedConfig.LED_ANIMATION_ENABLE_BYTE : LedConfig.LED_ANIMATION_DISABLE_BYTE
        let data = Data([sdkModeLedByte, ppiModeLedByte])
        let inputStream = InputStream(data: data)
        for try await _ in client.write(proto as NSData, data: inputStream) {}
    }


    func doFactoryReset(_ identifier: String, preservePairingInformation: Bool) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var builder = Protocol_PbPFtpFactoryResetParams()
        builder.sleep = false
        builder.otaFwupdate = preservePairingInformation
        BleLogger.trace("Send do factory reset to device: \(identifier)")
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: try builder.serializedData() as NSData)
    }


    func doFactoryReset(_ identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var builder = Protocol_PbPFtpFactoryResetParams()
        builder.sleep = false
        BleLogger.trace("Send do factory reset to device: \(identifier)")
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: try builder.serializedData() as NSData)
    }


    func doRestart(_ identifier: String, preservePairingInformation: Bool) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var builder = Protocol_PbPFtpFactoryResetParams()
        builder.sleep = false
        builder.doFactoryDefaults = false
        builder.otaFwupdate = preservePairingInformation
        BleLogger.trace("Send do restart to device: \(identifier)")
        do {
            try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: try builder.serializedData() as NSData)
        } catch let err as BleGattException {
            if case .gattDisconnected = err { BleLogger.trace("doRestart() gattDisconnected") } else { throw err }
        }
    }


    func doRestart(_ identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var builder = Protocol_PbPFtpFactoryResetParams()
        builder.sleep = false
        builder.doFactoryDefaults = false
        BleLogger.trace("Send do restart to device: \(identifier)")
        do {
            try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: try builder.serializedData() as NSData)
        } catch let err as BleGattException {
            if case .gattDisconnected = err { BleLogger.trace("doRestart() gattDisconnected") } else { throw err }
        }
    }


    func getSDLogConfiguration(_ identifier: String) async throws -> SDLogConfig {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else { throw PolarErrors.operationNotSupported }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = SERVICE_DATALOG_CONFIG_FILEPATH
        let request = try operation.serializedData()
        BleLogger.trace("Sensor datalog get. Device: \(identifier) Path: \(operation.path)")
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, parameters: nil)
        let data = try await client.request(request)
        let sensorDataLog = try Data_PbSensorDataLog(serializedBytes: data as Data)
        let logConfig = SDLogConfig.fromProto(proto: sensorDataLog)
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, parameters: nil)
        return logConfig
    }


    func setSDLogConfiguration(_ identifier: String, logConfiguration: SDLogConfig) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let sdLogConfigProto = try SDLogConfig.toProto(sdLogConfig: logConfiguration).serializedData()
        var operation = Protocol_PbPFtpOperation()
        operation.command = .put
        operation.path = SERVICE_DATALOG_CONFIG_FILEPATH
        let proto = try operation.serializedData()
        BleLogger.trace("Sensor datalog set. Device: \(identifier) Path: \(operation.path)")
        let inputStream = InputStream(data: Data(sdLogConfigProto))
        for try await _ in client.write(proto as NSData, data: inputStream) {}
    }

    
    func doFirstTimeUse(_ identifier: String, ftuConfig: PolarFirstTimeUseConfig) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.deviceError(description: "Failed to fetch GATT client.") }
        // Set local time
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.timeZone = TimeZone.current
        guard let date = isoFormatter.date(from: ftuConfig.deviceTime) else { throw PolarErrors.deviceError(description: "Invalid deviceTime format: \(ftuConfig.deviceTime)") }
        try await sendInitializationAndStartSyncNotifications(identifier: identifier)
        try await setLocalTime(identifier, time: date, zone: TimeZone.current)
        // Write user ID
        var userIdOperation = Protocol_PbPFtpOperation()
        userIdOperation.command = .put
        userIdOperation.path = UserIdentifierType.USER_IDENTIFIER_FILENAME
        let userIdentifier = UserIdentifierType.create()
        let userIdProto = try userIdentifier.toProto().serializedData()
        let userIdHeader = try userIdOperation.serializedData()
        for try await _ in client.write(userIdHeader as NSData, data: InputStream(data: userIdProto)) {}
        BleLogger.trace("User data written to device: \(identifier)")
        // Write FTU config
        let ftuConfigProto = try ftuConfig.toProto()?.serializedData() ?? { throw PolarErrors.deviceError(description: "Serialization of FTU Config failed.") }()
        var physDataOp = Protocol_PbPFtpOperation()
        physDataOp.command = .put
        physDataOp.path = PolarFirstTimeUseConfig.FTU_CONFIG_FILEPATH
        let physDataHeader = try physDataOp.serializedData()
        for try await _ in client.write(physDataHeader as NSData, data: InputStream(data: ftuConfigProto)) {}
        BleLogger.trace("User physical data written to device: \(identifier)")
        // Send initialization and stop sync (acknowledge FTU completion)
        try await sendTerminateAndStopSyncNotifications(identifier: identifier)
        BleLogger.trace("Successfully completed First Time Use writes to device: \(identifier)")
    }

    
    func isFtuDone(_ identifier: String) async throws -> Bool {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        guard .polarFileSystemV2 == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else { throw PolarErrors.operationNotSupported }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = UserIdentifierType.USER_IDENTIFIER_FILENAME
        let request = try operation.serializedData()
        logMessage("Check if FTU has been done to device \(identifier)")
        let data = try await client.request(request)
        return try Data_PbUserIdentifier(serializedBytes: data as Data).hasMasterIdentifier
    }

    
    func getUserPhysicalConfiguration(_ identifier: String) async throws -> PolarPhysicalConfiguration? {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.deviceError(description: "Failed to fetch GATT client.") }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = PolarFirstTimeUseConfig.FTU_CONFIG_FILEPATH
        let requestData = try operation.serializedData()
        do {
            let nsData = try await client.request(requestData)
            let pbUserPhysData = try Data_PbUserPhysData(serializedBytes: nsData as Data)
            return pbUserPhysData.toPolarPhysicalConfiguration()
        } catch {
            if case let BlePsFtpException.responseError(errorCode) = error,
               errorCode == Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue {
                BleLogger.trace("Phys data file does not exist on device \(identifier)")
                return nil
            }
            throw error
        }
    }

    
    func checkFirmwareUpdate(_ identifier: String) -> AsyncThrowingStream<CheckFirmwareUpdateStatus, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.yield(.checkFwUpdateFailed(details: "No BlePsFtpClient available"))
                        continuation.finish()
                        return
                    }
                    guard let deviceInfo = await PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: client, deviceId: identifier) else {
                        continuation.yield(.checkFwUpdateFailed(details: "Failed to read device firmware info"))
                        continuation.finish()
                        return
                    }
                    guard let firmwareUpdateRequest = try? FirmwareUpdateRequest(
                        clientId: "polar-sensor-data-collector-ios",
                        uuid: PolarDeviceUuid.fromDeviceId(identifier),
                        firmwareVersion: deviceInfo.deviceFwVersion,
                        hardwareCode: deviceInfo.deviceHardwareCode) else {
                        continuation.yield(.checkFwUpdateFailed(details: "Failed to create FirmwareUpdateRequest"))
                        continuation.finish()
                        return
                    }
                    let fwApi = FirmwareUpdateApi()
                    let result = try await withCheckedThrowingContinuation { (cont: CheckedContinuation<FirmwareUpdateResponse?, Error>) in
                        fwApi.checkFirmwareUpdate(firmwareUpdateRequest: firmwareUpdateRequest) { response in
                            switch response {
                            case .success(let result): cont.resume(returning: result)
                            case .failure: cont.resume(returning: nil)
                            }
                        }
                    }
                    if let result = result, let url = result.fileUrl, !url.isEmpty {
                        continuation.yield(.checkFwUpdateAvailable(version: result.version ?? ""))
                    } else {
                        continuation.yield(.checkFwUpdateNotAvailable(details: "No new firmware available"))
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
    
    func updateFirmware(_ identifier: String) -> AsyncThrowingStream<FirmwareUpdateStatus, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    for try await status in self.updateFirmwareAsync(identifier) {
                        continuation.yield(status)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }


    
    func updateFirmware(_ identifier: String, fromFirmwareURL: URL) -> AsyncThrowingStream<FirmwareUpdateStatus, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    for try await status in self.updateFirmwareAsync(identifier, firmwareURL: fromFirmwareURL) {
                        continuation.yield(status)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }


        
    private func updateFirmwareAsync(_ identifier: String, firmwareURL: URL? = nil) -> AsyncThrowingStream<FirmwareUpdateStatus, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                let session = try? self.serviceClientUtils.sessionFtpClientReady(identifier)
                guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    continuation.yield(.fwUpdateFailed(details: "No BlePsFtpClient available"))
                    continuation.finish()
                    return
                }
                guard let filesystemType = session?.advertisementContent.polarDeviceType else {
                    continuation.yield(.fwUpdateFailed(details: "Could not get file system type"))
                    continuation.finish()
                    return
                }
                let hasH10FileSystem = (BlePolarDeviceCapabilitiesUtility.fileSystemType(filesystemType) == .h10FileSystem)
                let backupManager = PolarBackupManager(client: client)
                var backupList: [PolarBackupManager.BackupFileData] = []
                let automaticReconnection = self.automaticReconnection
                var firmwareVersionInfo: String = ""
                defer { self.automaticReconnection = automaticReconnection }
                do {
                    // Resolve Firmware URL
                    let (availableVersionInfo, url, _): (String?, String?, FirmwareUpdateStatus)
                    if let firmwareURL = firmwareURL {
                        (availableVersionInfo, url, _) = (firmwareURL.lastPathComponent, firmwareURL.absoluteString, FirmwareUpdateStatus.preparingDeviceForFwUpdate(details: "Preparing for firmware update"))
                    } else {
                        (availableVersionInfo, url, _) = try await self.checkFirmwareUrlAvailabilityAsync(identifier)
                    }
                    firmwareVersionInfo = availableVersionInfo ?? "new version"
                    guard let url = url else {
                        BleLogger.trace("Did not receive url for firmware package, can not update")
                        continuation.yield(.fwUpdateNotAvailable(details: "Firmware update not available"))
                        continuation.finish()
                        return
                    }
                    // Fetch firmware package
                    continuation.yield(.fetchingFwUpdatePackage(details: "Fetching firmware package to \(firmwareVersionInfo)"))
                    let firmwareFiles = try await self.getFirmwareUpdatePackageAsync(firmwareUrl: url)
                    guard firmwareFiles.count > 0 else {
                        BleLogger.error("No firmware files available, can not update")
                        continuation.yield(.fwUpdateNotAvailable(details: "Can not update, firmware files were not available"))
                        continuation.finish()
                        return
                    }
                    // Prepare device: backup. H10 file system cannot be backed up.
                    if !hasH10FileSystem {
                        continuation.yield(.preparingDeviceForFwUpdate(details: "Backing up"))
                        backupList = try await backupManager.backupDevice()
                    }
                    // Prepare device: factory reset
                    continuation.yield(.preparingDeviceForFwUpdate(details: "Performing factory reset"))
                    do {
                        try await self.doFactoryReset(identifier, preservePairingInformation: true)
                    } catch let error as BleGattException {
                        if case .gattDisconnected = error { BleLogger.trace("doFactoryReset(preservePairingInformation) gattDisconnected") }
                        else { throw self.handleError(error) }
                    }
                    let disconnectedDueRemovedPairing = session?.disconnectedDueRemovedPairing
                    if disconnectedDueRemovedPairing ?? false { try self.connectToDevice(identifier) }
                    // Wait for reconnection after factory reset
                    continuation.yield(.preparingDeviceForFwUpdate(details: "Reconnecting after factory reset"))
                    try await self.waitDeviceSessionWithPftpToOpen(identifier: identifier, timeoutSeconds: 6*60, waitForDeviceDownSeconds: 10)
                    // Speed up for file transfer by sending sync signal
                    try await self.sendInitializationAndStartSyncNotifications(identifier: identifier)
                    // Write FW files
                    continuation.yield(.writingFwUpdatePackage(details: "Writing firmware files \(firmwareVersionInfo)"))
                    for try await status in self.writeFirmwareFilesToDeviceAsync(identifier, firmwareFiles: firmwareFiles) {
                        continuation.yield(status)
                    }
                    continuation.yield(.finalizingFwUpdate(details: "Waiting for device to update to \(firmwareVersionInfo)"))
                    try await self.waitDeviceSessionWithPftpToOpen(identifier: identifier, timeoutSeconds: 6*60, waitForDeviceDownSeconds: 10)
                    if !hasH10FileSystem {
                        try await self.sendInitializationAndStartSyncNotifications(identifier: identifier)
                        continuation.yield(.finalizingFwUpdate(details: "Restoring backup to device"))
                        try await backupManager.restoreBackup(backupFiles: backupList)
                        backupList = []
                        try await self.sendTerminateSessionNotification(identifier: identifier)
                    }
                    continuation.yield(.finalizingFwUpdate(details: "Setting device time"))
                    try await self.setLocalTime(identifier, time: Date(), zone: TimeZone.current)
                    if BlePolarDeviceCapabilitiesUtility.isDeviceSensor(session!.advertisementContent.polarDeviceType) {
                        continuation.yield(.finalizingFwUpdate(details: "Stopping sync"))
                        try await self.sendStopSyncNotification(identifier: identifier)
                    } else {
                        continuation.yield(.finalizingFwUpdate(details: "Restarting device"))
                        try await self.doRestart(identifier, preservePairingInformation: true)
                        continuation.yield(.finalizingFwUpdate(details: "Reconnecting after restart"))
                        try await self.waitDeviceSessionWithPftpToOpen(identifier: identifier, timeoutSeconds: 6*60, waitForDeviceDownSeconds: 10)
                    }
                    continuation.yield(.fwUpdateCompletedSuccessfully(details: "Firmware update to \(firmwareVersionInfo) completed successfully"))
                    continuation.finish()
                } catch let error {
                    BleLogger.error("Error during updateFirmware() to \(firmwareVersionInfo), error: \(error)")
                    if !hasH10FileSystem {
                        continuation.yield(.fwUpdateFailed(details: "Error: \(error)"))
                        continuation.finish(throwing: self.handleError(error))
                    } else {
                        continuation.yield(.fwUpdateCompletedSuccessfully(details: "Firmware update to \(firmwareVersionInfo) completed successfully"))
                        continuation.finish()
                    }
                }
            }
        }
    }

    // Returns (availableVersion, firmwareURL, FirmwareUpdateStatus)
    private func checkFirmwareUrlAvailabilityAsync(_ identifier: String) async throws -> (String?, String?, FirmwareUpdateStatus) {
        guard let session = try? serviceClientUtils.sessionFtpClientReady(identifier) else {
            return (nil, nil, .fwUpdateFailed(details: "No BleDeviceSession available"))
        }
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            return (nil, nil, .fwUpdateFailed(details: "No BlePsFtpClient available"))
        }
        guard let deviceInfo = await PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: client, deviceId: identifier) else {
            return (nil, nil, .fwUpdateFailed(details: "Failed to read device firmware info"))
        }
        guard let firmwareUpdateRequest = try? FirmwareUpdateRequest(
            clientId: "polar-sensor-data-collector-ios",
            uuid: PolarDeviceUuid.fromDeviceId(identifier),
            firmwareVersion: deviceInfo.deviceFwVersion,
            hardwareCode: deviceInfo.deviceHardwareCode) else {
            return (nil, nil, .fwUpdateFailed(details: "Failed to create FirmwareUpdateRequest"))
        }
        return await withCheckedContinuation { cont in
            let fwApi = FirmwareUpdateApi()
            fwApi.checkFirmwareUpdate(firmwareUpdateRequest: firmwareUpdateRequest) { response in
                switch response {
                case .success(let result):
                    cont.resume(returning: (result.version, result.fileUrl, .preparingDeviceForFwUpdate(details: "Preparing for firmware update")))
                case .failure(let error):
                    cont.resume(returning: (nil, nil, .fwUpdateFailed(details: error.localizedDescription)))
                }
            }
        }
    }

    private func getFirmwareUpdatePackageAsync(firmwareUrl: String) async throws -> [(String, Data)] {
        guard let firmwarePackage = try await FirmwareUpdateApi().getFirmwareUpdatePackage(url: firmwareUrl) else {
            BleLogger.error("Firmware package download fetch failed")
            return []
        }
        BleLogger.trace("Firmware package downloaded, zipped size: \(firmwarePackage.count) bytes")
        guard let unzippedFirmwarePackage = PolarFirmwareUpdateUtils.unzipFirmwarePackage(zippedData: firmwarePackage) else {
            BleLogger.error("Failed to unzip firmware package")
            return []
        }
        BleLogger.trace("Firmware package unzipped, total size: \(unzippedFirmwarePackage.reduce(0) { $0 + $1.value.count }) bytes")
        let sorted = unzippedFirmwarePackage
            .filter { (filename, _) -> Bool in
                if filename == "readme.txt" { BleLogger.trace("Skipping file \(filename)"); return false }
                return true
            }
            .sorted { PolarFirmwareUpdateUtils.FwFileComparator.compare($0.key, $1.key) == .orderedAscending }
        return Array(sorted)
    }

    private func writeFirmwareFilesToDeviceAsync(_ identifier: String, firmwareFiles: [(String, Data)], minPercentageIncrement: Int = 0) -> AsyncThrowingStream<FirmwareUpdateStatus, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    for firmwareFile in firmwareFiles {
                        var lastBytesWritten: Int = 0
                        let firmwareFilePath = "/\(firmwareFile.0)"
                        let firmwareFileBytes = firmwareFile.1
                        for try await bytesWritten in self.writeFirmwareToDeviceAsync(identifier: identifier, firmwareFilePath: firmwareFilePath, firmwareBytes: firmwareFileBytes) {
                            let bw = Int(bytesWritten)
                            let delta = bw - lastBytesWritten
                            let deltaPercentage = delta * 100 / firmwareFileBytes.count
                            let updateDownstream = lastBytesWritten == 0 || bytesWritten >= firmwareFileBytes.count || deltaPercentage >= minPercentageIncrement
                            if updateDownstream {
                                lastBytesWritten = bw
                                let percentage = bw * 100 / firmwareFileBytes.count
                                continuation.yield(.writingFwUpdatePackage(
                                    details: "Writing firmware update file \(firmwareFile.0), (\(percentage)%) bytes written: \(bw)/\(firmwareFileBytes.count)"
                                ))
                            }
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
    
    func getSteps(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarStepsData] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarStepsData]()
        for date in datesList {
            let result = try await PolarActivityUtils.readStepsFromDayDirectory(client: client, date: date)
            results.append(PolarStepsData(date: date, steps: result))
        }
        return results
    }


    func getDistance(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarDistanceData] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarDistanceData]()
        for date in datesList {
            let result = try await PolarActivityUtils.readDistanceFromDayDirectory(client: client, date: date)
            results.append(PolarDistanceData(date: date, distanceMeters: result))
        }
        return results
    }


    func get247HrSamples(identifier: String, fromDate: Date, toDate: Date) async throws -> [Polar247HrSamplesData] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        return try await PolarAutomaticSamplesUtils.read247HrSamples(client: client, fromDate: fromDate, toDate: toDate)
    }

    
    func get247PPiSamples(identifier: String, fromDate: Date, toDate: Date) async throws -> [Polar247PPiSamplesData] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        return try await PolarAutomaticSamplesUtils.read247PPiSamples(client: client, fromDate: fromDate, toDate: toDate)
    }


    func getNightlyRecharge(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarNightlyRechargeData] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarNightlyRechargeData]()
        for date in datesList {
            if let result = await PolarNightlyRechargeUtils.readNightlyRechargeData(client: client, date: date) {
                results.append(result)
            }
        }
        return results
    }

    
    func getCalories(identifier: String, fromDate: Date, toDate: Date, caloriesType: CaloriesType) async throws -> [PolarCaloriesData] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarCaloriesData]()
        for date in datesList {
            let result = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: client, date: date, caloriesType: caloriesType)
            results.append(PolarCaloriesData(date: date, calories: result))
        }
        return results
    }


    func getActivitySampleData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarActivityDayData] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarActivityDayData]()
        for date in datesList {
            let result = try await PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: client, date: date)
            results.append(result)
        }
        return results
    }

    
    func getDailySummaryData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarDailySummary] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarDailySummary]()
        for date in datesList {
            if let result = try await PolarActivityUtils.readDailySummaryDataFromDayDirectory(client: client, date: date) {
                results.append(result)
            }
        }
        return results
    }

    
    public func startExercise(identifier: String, profile: PolarExerciseSession.SportProfile) async throws {
        BleLogger.trace("Start exercise pressed for \(identifier) with profile=\(profile)")
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var sportId = PbSportIdentifier()
        sportId.value = UInt64(profile.rawValue)
        var params = Protocol_PbPFtpStartExerciseParams()
        params.sportIdentifier = sportId
        let payloadData = try params.serializedData()
        _ = try await client.query(Protocol_PbPFtpQuery.startExercise.rawValue, parameters: payloadData as NSData)
        BleLogger.trace("Start exercise succeeded for \(identifier)")
    }


    public func pauseExercise(identifier: String) async throws {
        BleLogger.trace("Pause exercise pressed for \(identifier)")
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        _ = try await client.query(Protocol_PbPFtpQuery.pauseExercise.rawValue, parameters: nil)
        BleLogger.trace("Pause exercise succeeded for \(identifier)")
    }


    public func resumeExercise(identifier: String) async throws {
        BleLogger.trace("Resume exercise pressed for \(identifier)")
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        _ = try await client.query(Protocol_PbPFtpQuery.resumeExercise.rawValue, parameters: nil)
        BleLogger.trace("Resume exercise succeeded for \(identifier)")
    }


    public func stopExercise(identifier: String) async throws {
        BleLogger.trace("Stop exercise pressed for \(identifier)")
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var params = Protocol_PbPFtpStopExerciseParams()
        params.save = true
        let payloadData = try params.serializedData()
        _ = try await client.query(Protocol_PbPFtpQuery.stopExercise.rawValue, parameters: payloadData as NSData)
        BleLogger.trace("Stop exercise succeeded for \(identifier)")
    }


    public func getExerciseStatus(identifier: String) async throws -> PolarExerciseSession.ExerciseInfo {
        BleLogger.trace("Get exercise status pressed for \(identifier)")
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let data = try await client.query(Protocol_PbPFtpQuery.getExerciseStatus.rawValue, parameters: nil)
        let info = try PolarExerciseSession.ExerciseInfo.parse(from: data as Data)
        BleLogger.trace("Get exercise status succeeded for \(identifier): \(info)")
        return info
    }

    
    func observeExerciseStatus(identifier: String) -> AsyncThrowingStream<PolarExerciseSession.ExerciseInfo, Error> {
        BleLogger.trace("Start observing exercise status notifications for \(identifier)")
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    for try await notification in client.waitNotification() {
                        if notification.id == Protocol_PbPFtpDevToHostNotification.exerciseStatus.rawValue {
                            do {
                                let info = try PolarExerciseSession.ExerciseInfo.parse(from: notification.parameters as Data)
                                BleLogger.trace("Exercise status changed: \(info)")
                                continuation.yield(info)
                            } catch {
                                BleLogger.error("Failed to parse exercise status notification: \(error)")
                            }
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: PolarErrors.polarBleSdkInternalException(description: "Exercise status observation failed for \(identifier): \(error.localizedDescription)"))
                }
            }
        }
    }


    @available(*, deprecated, message: "Use setWarehouseSleep(_ identifier: String) instead")
    func setWarehouseSleep(_ identifier: String, enableWarehouseSleep: Bool?) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var builder = Protocol_PbPFtpFactoryResetParams()
        builder.sleep = enableWarehouseSleep ?? false
        builder.otaFwupdate = true
        BleLogger.trace("Setting warehouse sleep, device: \(identifier).")
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: try builder.serializedData() as NSData)
    }


    func setWarehouseSleep(_ identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var builder = Protocol_PbPFtpFactoryResetParams()
        builder.sleep = true
        builder.doFactoryDefaults = true
        BleLogger.trace("Setting warehouse sleep to true, device: \(identifier).")
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: try builder.serializedData() as NSData)
    }

    
    func turnDeviceOff(_ identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var builder = Protocol_PbPFtpFactoryResetParams()
        builder.sleep = true
        builder.doFactoryDefaults = false
        BleLogger.trace("Turn off device \(identifier).")
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: try builder.serializedData() as NSData)
    }


    func getActiveTime(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarActiveTimeData] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let calendar = Calendar.current
        var datesList = [Date]()
        var currentDate = fromDate
        while currentDate <= toDate {
            datesList.append(currentDate)
            if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = nextDate } else { break }
        }
        var results = [PolarActiveTimeData]()
        for date in datesList {
            let activeTime = try await PolarActivityUtils.readActiveTimeFromDayDirectory(client: client, date: date)
            results.append(PolarActiveTimeData(date: date, timeNonWear: activeTime.timeNonWear, timeSleep: activeTime.timeSleep, timeSedentary: activeTime.timeSedentary, timeLightActivity: activeTime.timeLightActivity, timeContinuousModerateActivity: activeTime.timeContinuousModerateActivity, timeIntermittentModerateActivity: activeTime.timeIntermittentModerateActivity, timeContinuousVigorousActivity: activeTime.timeContinuousVigorousActivity, timeIntermittentVigorousActivity: activeTime.timeIntermittentVigorousActivity))
        }
        return results
    }

    
    func setPolarUserDeviceSettings(_ identifier: String, polarUserDeviceSettings: PolarUserDeviceSettings) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let settingsPath = BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) == .polarFileSystemV2 ? DEVICE_SETTINGS_FILE_PATH : SENSOR_SETTINGS_FILE_PATH
        let userDeviceSettingsData = try PolarUserDeviceSettings.toProto(userDeviceSettings: polarUserDeviceSettings).serializedData()
        var operation = Protocol_PbPFtpOperation()
        operation.command = .put
        operation.path = settingsPath
        let proto = try operation.serializedData()
        BleLogger.trace("Polar user device settings set. Device: \(identifier) Path: \(settingsPath)")
        let inputStream = InputStream(data: userDeviceSettingsData)
        for try await _ in client.write(proto as NSData, data: inputStream) {}
    }


    func getPolarUserDeviceSettings(identifier: String) async throws -> PolarUserDeviceSettings.PolarUserDeviceSettingsResult {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let settingsPath = BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) == .polarFileSystemV2 ? DEVICE_SETTINGS_FILE_PATH : SENSOR_SETTINGS_FILE_PATH
        return try await PolarUserDeviceSettingsUtils.getUserDeviceSettings(client: client, deviceSettingsPath: settingsPath)
    }


    func deleteStoredDeviceData(_ identifier: String, dataType: PolarStoredDataType.StoredDataType, until: Date?) async throws {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd"
        let entryPattern = dataType.rawValue
        var condition: (_ p: String) -> Bool
        var folderPath: String = "/U/0"
        switch dataType {
        case .AUTO_SAMPLE:
            folderPath = "/U/0/AUTOS"
            condition = { e in e.contains("^(\\d{8})(/)") || e == "\(entryPattern)/" || e.contains(".BPB") }
        case .SDLOGS:
            folderPath = "/SDLOGS"
            condition = { e in e.contains("^(\\d{8})(/)") || e == "\(entryPattern)/" || e.contains(".SLG") || e.contains(".TXT") }
        case .ACTIVITY, .DAILY_SUMMARY, .NIGHTLY_RECOVERY, .SLEEP, .SKIN_CONTACT_CHANGES, .SKINTEMP, .SLEEP_SCORE:
            folderPath = "/U/0/"
            condition = { e in
                (e.matches("^([0-9]{8})(\\/)") || e == "\(formatter.string(from: until!))" + "/" || e == "\(entryPattern)/" || e.contains(".BPB")) && !e.contains("USERID.BPB") && !e.contains("HIST")
            }
        case .UNDEFINED: return
        }
        var deletedFiles = [String]()
        for try await file in fileUtils.listFiles(identifier: identifier, folderPath: folderPath, condition: condition) {
            switch dataType {
            case .AUTO_SAMPLE:
                if try await fileUtils.checkAutoSampleFile(identifier: identifier, filePath: file, until: until!) {
                    _ = try await fileUtils.removeSingleFile(identifier: identifier, filePath: file)
                    deletedFiles.append(file)
                }
            case .SDLOGS:
                _ = try await fileUtils.removeSingleFile(identifier: identifier, filePath: file)
                deletedFiles.append(file)
            case .ACTIVITY, .DAILY_SUMMARY, .NIGHTLY_RECOVERY, .SLEEP, .SKIN_CONTACT_CHANGES, .SKINTEMP, .SLEEP_SCORE:
                if formatter.string(from: until!) >= String(file.split(separator: "/")[2]) {
                    _ = try await fileUtils.removeSingleFile(identifier: identifier, filePath: file)
                    deletedFiles.append(file)
                }
            case .UNDEFINED: break
            }
        }
        // Delete empty parent directories
        for path in deletedFiles {
            let indices = path.findIndices(lookable: "/")
            var indexCount = 1
            var currentDir = String(path[...indices[indices.count - indexCount]])
            while currentDir != "/U/0/" {
                try await fileUtils.deleteDataDirectory(identifier: identifier, directoryPath: currentDir)
                indexCount += 1
                if indexCount > indices.count { break }
                currentDir = String(path[...indices[indices.count - indexCount]])
            }
        }
    }

    
    func deleteDeviceDateFolders(_ identifier: String, fromDate: Date?, toDate: Date?) async throws {
        let path = "/U/0/"
        let calendar = Calendar.current
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd"
        dateFormatter.timeZone = TimeZone.current
        guard let to = toDate, let from = fromDate else { throw PolarErrors.dateTimeFormatFailed(description: "Invalid from and/or to date") }
        var validDates = Set<Date>()
        var currentDate = try from.localDate()
        let toLocalDate = try to.localDate()
        while currentDate <= toLocalDate {
            validDates.insert(currentDate)
            if let next = calendar.date(byAdding: .day, value: 1, to: currentDate) { currentDate = next } else { break }
        }
        let condition: (_ p: String) -> Bool = { entry in
            if entry.hasSuffix("/") {
                if let d = dateFormatter.date(from: String(entry.dropLast())) { return validDates.contains(d) }
            }
            return false
        }
        for try await folder in fileUtils.listFiles(identifier: identifier, folderPath: path, condition: condition, recurseDeep: false) {
            try await fileUtils.deleteDataDirectory(identifier: identifier, directoryPath: folder)
        }
    }


    func deleteTelemetryData(_ identifier: String) async throws {
        let condition: (_ p: String) -> Bool = { e in e.contains("^([A-Za-z]{3}[0-9]{1,3})") && e.contains("TRC") && e.contains(".BIN") }
        for try await file in fileUtils.listFiles(identifier: identifier, folderPath: "/", condition: condition) {
            _ = try await fileUtils.removeSingleFile(identifier: identifier, filePath: file)
            BleLogger.trace("Successfully deleted telemetry data \(file) from device \(identifier).")
        }
    }

    
    func getTrainingSessionReferences(
        identifier: String,
        fromDate: Date? = nil,
        toDate: Date? = nil
    ) async throws -> [PolarTrainingSessionReference] {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        return try await PolarTrainingSessionUtils.getTrainingSessionReferences(
            client: client,
            fromDate: fromDate,
            toDate: toDate
        )
    }

    func getTrainingSession(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ) async throws -> PolarTrainingSession {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        return try await PolarTrainingSessionUtils.readTrainingSession(
            client: client,
            reference: trainingSessionReference
        )
    }
    
    func getTrainingSessionWithProgress(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference,
        progressHandler: @escaping (PolarTrainingSessionProgress) -> Void
    ) async throws -> PolarTrainingSession {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        return try await PolarTrainingSessionUtils.readTrainingSessionWithProgress(
            client: client,
            reference: trainingSessionReference,
            progressHandler: progressHandler
        )
    }
    
    func deleteTrainingSession(identifier: String, reference: PolarTrainingSessionReference) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        BleLogger.trace("Delete training session with path '\(reference.path)' ")
        try await PolarTrainingSessionUtils.deleteTrainingSession(client: client, reference: reference)
    }


    func waitForConnection(_ identifier: String) async throws {
        while true {
            if let session = try? serviceClientUtils.fetchSession(identifier), session.state == .sessionOpen { return }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
    }

    
    func setUserDeviceLocation(_ identifier: String, location: Int) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var currentSettings = try await getUserDeviceSettingsProto(client: client)
        guard let deviceLocation = PbDeviceLocation(rawValue: location) else { throw PolarErrors.invalidArgument(description: "Invalid device location: \(location)") }
        currentSettings.generalSettings.deviceLocation = deviceLocation
        try await setUserDeviceSettingsProto(client: client, polarUserDeviceSettings: currentSettings)
    }


    func setUsbConnectionMode(_ identifier: String, enabled: Bool) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        let settingsPath = BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) == .polarFileSystemV2 ? DEVICE_SETTINGS_FILE_PATH : SENSOR_SETTINGS_FILE_PATH
        var currentSettings = try await getUserDeviceSettingsProto(client: client, settingsPath: settingsPath)
        var usbSettings = Data_PbUsbConnectionSettings()
        usbSettings.mode = enabled ? .on : .off
        currentSettings.usbConnectionSettings = usbSettings
        try await setUserDeviceSettingsProto(client: client, polarUserDeviceSettings: currentSettings, settingsPath: settingsPath)
    }


    func setAutomaticTrainingDetectionSettings(_ identifier: String, mode: Bool, sensitivity: Int, minimumDuration: Int) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var currentSettings = try await getUserDeviceSettingsProto(client: client)
        var atdSettings = Data_PbAutomaticTrainingDetectionSettings()
        atdSettings.state = mode ? .on : .off
        atdSettings.sensitivity = UInt32(sensitivity)
        atdSettings.minimumTrainingDurationSeconds = UInt32(minimumDuration)
        currentSettings.automaticMeasurementSettings.automaticTrainingDetectionSettings = atdSettings
        try await setUserDeviceSettingsProto(client: client, polarUserDeviceSettings: currentSettings)
    }

    
    func setDaylightSavingTime(_ identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { throw PolarErrors.serviceNotFound }
        var currentSettings = try await getUserDeviceSettingsProto(client: client)
        guard let nextDSTTransition = TimeZone.current.nextDaylightSavingTimeTransition(after: Date()) else { throw PolarErrors.polarBleSdkInternalException(description: "Could not get next daylight saving time transition for time zone \(TimeZone.current).") }
        let nextDSTOffset = TimeZone.current.daylightSavingTimeOffset(for: nextDSTTransition.addingTimeInterval(24*60*60)) - TimeZone.current.daylightSavingTimeOffset(for: nextDSTTransition.addingTimeInterval(-(24*60*60)))
        currentSettings.daylightSaving.nextDaylightSavingTime = PolarTimeUtils.dateToPbSystemDateTime(date: nextDSTTransition)
        currentSettings.daylightSaving.offset = Int32(nextDSTOffset)
        try await setUserDeviceSettingsProto(client: client, polarUserDeviceSettings: currentSettings)
    }


    public func setTelemetryEnabled(_ identifier: String, enabled: Bool) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        var currentProto = try await getUserDeviceSettingsProto(client: client)
        currentProto.telemetrySettings.telemetryEnabled = enabled
        try await setUserDeviceSettingsProto(client: client, polarUserDeviceSettings: currentProto)
        BleLogger.trace("Telemetry enabled=\(enabled) written for \(identifier)")
    }

    
    func setMultiBLEConnectionMode(identifier: String, enable: Bool) async throws {
        let session = try serviceClientUtils.sessionPfcClientReady(identifier)
        guard let client = session.fetchGattClient(BlePfcClient.PFC_SERVICE) as? BlePfcClient else { throw PolarErrors.serviceNotFound }
        let enableValue: UInt8 = enable ? 1 : 0
        let response = try await client.sendControlPointCommand(BlePfcClient.PfcMessage.pfcConfigureMultiConnection, value: enableValue)
        if response.status != .success { throw PolarErrors.operationNotSupported }
    }

    
    func getMultiBLEConnectionMode(identifier: String) async throws -> Bool {
        let session = try await serviceClientUtils.waitPfcClientReady(identifier)
        guard let client = session.fetchGattClient(BlePfcClient.PFC_SERVICE) as? BlePfcClient else { throw PolarErrors.serviceNotFound }
        let response = try await client.sendControlPointCommand(BlePfcClient.PfcMessage.pfcRequestMultiConnectionSetting, value: UInt8(0))
        if response.payload.isEmpty { return false }
        return response.payload[0] == 1
    }

    
    func sendInitializationAndStartSyncNotifications(identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        _ = try await client.query(Protocol_PbPFtpQuery.requestSynchronization.rawValue, parameters: nil)
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, parameters: nil)
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.startSync.rawValue, parameters: nil)
    }


    func sendTerminateAndStopSyncNotifications(identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        var params = Protocol_PbPFtpStopSyncParams()
        params.completed = true
        let parameters = try params.serializedData() as NSData
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.stopSync.rawValue, parameters: parameters)
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, parameters: nil)
    }

    
    func sendTerminateSessionNotification(identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, parameters: nil)
    }

    
    func sendStopSyncNotification(identifier: String) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        var params = Protocol_PbPFtpStopSyncParams()
        params.completed = true
        let parameters = try params.serializedData() as NSData
        try await client.sendNotification(Protocol_PbPFtpHostToDevNotification.stopSync.rawValue, parameters: parameters)
    }

    
    private func getDeviceSettingsPath(_ session: BleDeviceSession) -> String {
        let fsType = BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType)
        switch fsType {
        case .h10FileSystem:
            return SENSOR_SETTINGS_FILE_PATH
        default:
            return DEVICE_SETTINGS_FILE_PATH
        }
    }

    private func getUserDeviceSettingsProto(client: BlePsFtpClient, settingsPath: String = DEVICE_SETTINGS_FILE_PATH) async throws -> Data_PbUserDeviceSettings {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = settingsPath
        let request = try operation.serializedData()
        let responseData = try await client.request(request)
        return try Data_PbUserDeviceSettings(serializedBytes: Data(responseData))
    }


    private func setUserDeviceSettingsProto(client: BlePsFtpClient, polarUserDeviceSettings: Data_PbUserDeviceSettings, settingsPath: String = DEVICE_SETTINGS_FILE_PATH) async throws {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .put
        operation.path = settingsPath
        let proto = try operation.serializedData()
        let settingsData = try polarUserDeviceSettings.serializedData()
        let inputStream = InputStream(data: settingsData)
        for try await _ in client.write(proto as NSData, data: inputStream) {}
    }

    
    public func setAutomaticOHRMeasurementEnabled(_ identifier: String, enabled: Bool) async throws {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        var updated = try await getUserDeviceSettingsProto(client: client)
        var autosSettings = Data_PbAutomaticMeasurementSettings()
        autosSettings.state = enabled ? .alwaysOn : .off
        if !enabled {
            autosSettings.clearTimedSettings()
            autosSettings.clearIntelligentTimedSettings()
        }
        updated.automaticMeasurementSettings.automaticOhrMeasurement = autosSettings
        try await setUserDeviceSettingsProto(client: client, polarUserDeviceSettings: updated)
        BleLogger.trace("AUTOS files enabled=\(enabled) written for \(identifier)")
    }


    // Removed: old Single<> version - use the async throws version above
    private func _unused_getUserDeviceSettingsProto_placeholder() -> Never { fatalError() }

    
    func checkIfDirectoryIsEmpty(directoryPath: String, client: BlePsFtpClient) async throws -> Bool {
        return try await fileUtils.checkIfDirectoryIsEmpty(directoryPath: directoryPath, client: client)
    }

    
    func removeSingleFile(identifier: String, filePath: String) async throws -> NSData {
        return try await fileUtils.removeSingleFile(identifier: identifier, filePath: filePath)
    }

    
    private func dateFromStringWOTime(dateFrom: String) -> Date {
        
        let year = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 0)..<dateFrom.index(dateFrom.endIndex, offsetBy: -4)]))
        let month = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex , offsetBy: 4)..<dateFrom.index(dateFrom.endIndex, offsetBy: -2)]))
        let day = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 6)..<dateFrom.index(dateFrom.endIndex, offsetBy: 0)]))
        
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(abbreviation: "UTC")!
        
        var datecomponents = DateComponents()
        datecomponents.year = year
        datecomponents.month = month
        datecomponents.day = day
        datecomponents.hour = 0
        datecomponents.minute = 0
        datecomponents.second = 0
        
        return calendar.date(from: datecomponents)!
    }

    private func writeFirmwareToDeviceAsync(identifier: String, firmwareFilePath: String, firmwareBytes: Data) -> AsyncThrowingStream<UInt, Error> {
        return AsyncThrowingStream { continuation in
            Task<Void, Never> {
                do {
                    guard let session = try? self.serviceClientUtils.sessionFtpClientReady(identifier) else {
                        continuation.finish(throwing: PolarErrors.deviceNotConnected)
                        return
                    }
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    BleLogger.trace("Initialize session")
                    _ = try await client.query(Protocol_PbPFtpQuery.prepareFirmwareUpdate.rawValue, parameters: nil)
                    BleLogger.trace("Start \(firmwareFilePath) write")
                    var builder = Protocol_PbPFtpOperation()
                    builder.command = .put
                    builder.path = firmwareFilePath
                    let proto = try builder.serializedData()
                    for try await bytesWritten in client.write(proto as NSData, data: InputStream(data: firmwareBytes)) {
                        BleLogger.trace("Writing firmware update file, bytes written: \(bytesWritten)/\(firmwareBytes.count)")
                        continuation.yield(bytesWritten)
                    }
                    if firmwareFilePath.contains("SYSUPDAT.IMG") {
                        BleLogger.trace("Firmware file is SYSUPDAT.IMG, waiting for reboot")
                    }
                    continuation.finish()
                } catch {
                    if let pftpError = Protocol_PbPFtpError(rawValue: (error as NSError)._code) {
                        if pftpError == .batteryTooLow {
                            continuation.finish(throwing: PolarErrors.deviceError(description: "Battery too low to perform firmware update"))
                        } else if pftpError == .rebooting && firmwareFilePath.contains("SYSUPDAT.IMG") {
                            BleLogger.trace("PFTP firmware file write success - device is rebooting")
                            continuation.finish()
                        } else {
                            BleLogger.error("PFTP error during firmware write: \(error.localizedDescription)")
                            continuation.finish(throwing: self.handleError(error))
                        }
                    } else {
                        BleLogger.error("PFTP error during firmware write: \(error.localizedDescription)")
                        continuation.finish(throwing: self.handleError(error))
                    }
                }
            }
        }
    }

    func waitDeviceSessionWithPftpToOpen(identifier: String, timeoutSeconds: Int, waitForDeviceDownSeconds: Int = 0) async throws {
        if waitForDeviceDownSeconds > 0 {
            try await Task.sleep(nanoseconds: UInt64(waitForDeviceDownSeconds) * 1_000_000_000)
        }
        let deadline = Date().addingTimeInterval(Double(timeoutSeconds))
        while Date() < deadline {
            if let session = try? serviceClientUtils.sessionFtpClientReady(identifier), session.state == .sessionOpen { return }
            try await Task.sleep(nanoseconds: 500_000_000)
        }
        throw PolarErrors.polarBleSdkInternalException(description: "Timeout waiting for device \(identifier) to be ready")
    }


    private func processAccData(
        _ accData: AccData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>,
        _ settings: PolarSensorSetting
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .accOfflineRecordingData(existingData, startTime, existingSettings):
            let newSamples = existingData + accData.samples.map { sample in
                (timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z)
            }
            return .accOfflineRecordingData(
                newSamples,
                startTime: startTime,
                settings: existingSettings
            )
        default:
            return .accOfflineRecordingData(
                accData.mapToPolarData(),
                startTime: offlineRecordingData.startTime,
                settings: settings
            )
        }
    }

    private func processGyroData(
        _ gyroData: GyrData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>,
        _ settings: PolarSensorSetting
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .gyroOfflineRecordingData(existingData, startTime, existingSettings):
            let newSamples: PolarGyroData = existingData + gyroData.samples.map { (timeStamp: $0.timeStamp, x: $0.x, y: $0.y, z: $0.z) }
            return .gyroOfflineRecordingData(
                newSamples,
                startTime: startTime,
                settings: existingSettings
            )
        default:
            return .gyroOfflineRecordingData(
                gyroData.mapToPolarData(),
                startTime: offlineRecordingData.startTime,
                settings: settings
            )
        }
    }

    private func processMagData(
        _ magData: MagData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>,
        _ settings: PolarSensorSetting
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .magOfflineRecordingData(existingData, startTime, existingSettings):
            let newSamples: PolarMagnetometerData = existingData + magData.samples.map { (timeStamp: $0.timeStamp, x: $0.x, y: $0.y, z: $0.z) }
            return .magOfflineRecordingData(
                newSamples,
                startTime: startTime,
                settings: existingSettings
            )
        default:
            return .magOfflineRecordingData(
                magData.mapToPolarData(),
                startTime: offlineRecordingData.startTime,
                settings: settings
            )
        }
    }

    private func processPpgData(
        _ ppgData: PpgData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>,
        _ settings: PolarSensorSetting
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .ppgOfflineRecordingData(existingData, startTime, existingSettings):
            let newSamples = existingData.samples + ppgData.mapToPolarData().samples
            return .ppgOfflineRecordingData(
                (type: existingData.type, samples: newSamples),
                startTime: startTime,
                settings: existingSettings
            )
        default:
            return .ppgOfflineRecordingData(
                ppgData.mapToPolarData(),
                startTime: offlineRecordingData.startTime,
                settings: settings
            )
        }
    }

    private func processPpiData(
        _ ppiData: PpiData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .ppiOfflineRecordingData(existingData, startTime):
            let newSamples = existingData.samples + ppiData.samples.map {
                (
                    timestamp: $0.timeStamp,
                    hr: $0.hr,
                    ppInMs: $0.ppInMs,
                    ppErrorEstimate: $0.ppErrorEstimate,
                    blockerBit: $0.blockerBit,
                    skinContactStatus: $0.skinContactStatus,
                    skinContactSupported: $0.skinContactSupported
                )
            }
            return .ppiOfflineRecordingData(
                (timeStamp: UInt64(startTime.timeIntervalSince1970), samples: newSamples),
                startTime: startTime
            )
        default:
            return .ppiOfflineRecordingData(
                (timeStamp: UInt64(offlineRecordingData.startTime.timeIntervalSince1970), samples: ppiData.samples.map {
                    (
                        timeStamp: $0.timeStamp,
                        hr: $0.hr,
                        ppInMs: $0.ppInMs,
                        ppErrorEstimate: $0.ppErrorEstimate,
                        blockerBit: $0.blockerBit,
                        skinContactStatus: $0.skinContactStatus,
                        skinContactSupported: $0.skinContactSupported
                    )
                }),
                startTime: offlineRecordingData.startTime
            )
        }
    }

    private func processHrData(
        _ hrData: OfflineHrData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .hrOfflineRecordingData(existingData, startTime):
            let newSamples = existingData + hrData.samples.map {
                (
                    hr: $0.hr,
                    ppgQuality: $0.ppgQuality,
                    correctedHr: $0.correctedHr,
                    rrsMs: [],
                    rrAvailable: false,
                    contactStatus: false,
                    contactStatusSupported: false
                )
            }
            return .hrOfflineRecordingData(
                newSamples,
                startTime: startTime
            )
        default:
            return .hrOfflineRecordingData(
                hrData.samples.map {
                    (
                        hr: $0.hr,
                        ppgQuality: $0.ppgQuality,
                        correctedHr: $0.correctedHr,
                        rrsMs: [],
                        rrAvailable: false,
                        contactStatus: false,
                        contactStatusSupported: false
                    )
                },
                startTime: offlineRecordingData.startTime
            )
        }
    }

    private func processTemperatureData(
        _ temperatureData: TemperatureData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .temperatureOfflineRecordingData(existingData, startTime):
            let newSamples = existingData.samples + temperatureData.samples.map {
                (
                    timeStamp: $0.timeStamp,
                    temperature: $0.temperature
                )
            }
            let updatedData: PolarTemperatureData = (
                timeStamp: newSamples.last?.timeStamp ?? existingData.timeStamp,
                samples: newSamples
            )
            return .temperatureOfflineRecordingData(
                updatedData,
                startTime: startTime
            )
        default:
            return .temperatureOfflineRecordingData(
                temperatureData.mapToPolarData(),
                startTime: offlineRecordingData.startTime
            )
        }
    }
    
    private func processSkinTemperatureData(
        _ skinTemperatureData: SkinTemperatureData,
        _ existingData: PolarOfflineRecordingData?,
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
        switch existingData {
        case let .skinTemperatureOfflineRecordingData(existingData, startTime):
            let newSamples = existingData.samples + skinTemperatureData.samples.map {
                (
                    timeStamp: $0.timeStamp,
                    temperature: $0.skinTemperature
                )
            }
            let updatedData: PolarTemperatureData = (
                timeStamp: newSamples.last?.timeStamp ?? existingData.timeStamp,
                samples: newSamples
            )
            return .skinTemperatureOfflineRecordingData(
                updatedData,
                startTime: startTime
            )
        default:
            return .skinTemperatureOfflineRecordingData(
                skinTemperatureData.mapToPolarData(),
                startTime: offlineRecordingData.startTime
            )
        }
    }

    private func processEmptyData(
        _ offlineRecordingData: OfflineRecordingData<Any>
    ) -> PolarOfflineRecordingData {
            return .emptyData(startTime:offlineRecordingData.startTime)
    }

    private func querySettings(_ identifier: String, type: PmdMeasurementType, recordingType: PmdRecordingType) async throws -> PolarSensorSetting {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        do {
            let setting = try await client.querySettings(type, recordingType)
            return setting.mapToPolarSettings()
        } catch {
            throw PolarErrors.deviceError(description: "\(error)")
        }
    }

    
    private func queryFullSettings(_ identifier: String, type: PmdMeasurementType, recordingType: PmdRecordingType) async throws -> PolarSensorSetting {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        do {
            let setting = try await client.queryFullSettings(type, recordingType)
            return setting.mapToPolarSettings()
        } catch {
            throw PolarErrors.deviceError(description: "\(error)")
        }
    }

    
    fileprivate func startStreaming<T, S: AsyncSequence>(_ identifier: String, type: PmdMeasurementType, settings: PolarSensorSetting, observer: @escaping (_ client: BlePmdClient) -> S) -> AsyncThrowingStream<T, Error> where S.Element == T {
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let session = try self.serviceClientUtils.sessionPmdClientReady(identifier)
                    let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as! BlePmdClient
                    try await client.startMeasurement(type, settings: settings.map2PmdSetting())
                    defer {
                        Task { try? await client.stopMeasurement(type) }
                    }
                    for try await value in observer(client) { continuation.yield(value) }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: PolarErrors.deviceError(description: "\(error)"))
                }
            }
            continuation.onTermination = { _ in task.cancel() }  // ← link cancellation
        }
    }
    
    private func fetchDirectoryEntries(_ path: String, client: BlePsFtpClient, condition: @escaping (_ p: String) -> Bool) async throws -> [(name: String, size: UInt64)] {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        let request = try operation.serializedData()
        let data = try await client.request(request)
        let dir = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
        return dir.entries.compactMap { entry -> (name: String, size: UInt64)? in
            condition(entry.name) ? (name: path + entry.name, size: entry.size) : nil
        }
    }

    
    private func fetchRecursive(_ path: String, client: BlePsFtpClient, condition: @escaping (_ p: String) -> Bool) async throws -> [(name: String, size: UInt64)] {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        let request = try operation.serializedData()
        do {
            let data = try await client.request(request)
            let dir = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            var results: [(name: String, size: UInt64)] = []
            for entry in dir.entries {
                if condition(entry.name) {
                    let fullPath = path + entry.name
                    if fullPath.hasSuffix("/") {
                        let sub = try await fetchRecursive(fullPath, client: client, condition: condition)
                        results.append(contentsOf: sub)
                    } else {
                        results.append((name: fullPath, size: entry.size))
                    }
                }
            }
            return results
        } catch {
            if case let BlePsFtpException.responseError(code) = error, code == 103 {
                BleLogger.trace("Directory not found at path: \(path). Returning empty list.")
                return []
            }
            throw PolarErrors.deviceError(description: "\(error)")
        }
    }

    
    func enableSDKMode(_ identifier: String) async throws {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        try await client.startSdkMode()
    }

    
    func disableSDKMode(_ identifier: String) async throws {
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        try await client.stopSdkMode()
    }

    
    func isSDKModeEnabled(_ identifier: String) async throws -> Bool {
        let session = try await serviceClientUtils.waitPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { throw PolarErrors.serviceNotFound }
        let mode = try await client.isSdkModeEnabled()
        return mode != PmdSdkMode.disabled
    }


    func getBatteryLevel(identifier: String) throws -> Int {
        do {
            let session = try serviceClientUtils.sessionServiceReady(identifier, service: BleBasClient.BATTERY_SERVICE)
            let client = session.fetchGattClient(BleBasClient.BATTERY_SERVICE) as! BleBasClient
            return client.getBatteryLevel()
        } catch let err {
            throw handleError(err)
        }
    }
    
    func getChargerState(identifier: String) throws -> BleBasClient.ChargeState {

        do {
            let session = try serviceClientUtils.sessionServiceReady(identifier, service: BleBasClient.BATTERY_SERVICE)
            let client = session.fetchGattClient(BleBasClient.BATTERY_SERVICE) as! BleBasClient
            return client.getChargeState()
        } catch let err {
            throw handleError(err)
        }
    }

    func getRSSIValue(_ identifier: String) throws -> Int {
        return try self.serviceClientUtils.getRSSIValue(identifier)
    }

    func checkIfDeviceDisconnectedDueRemovedPairing(_ identifier: String) throws -> Bool {
        do {
            return try serviceClientUtils.checkIfDeviceDisconnectedDueRemovedPairing(identifier: identifier)
        } catch let err {
            throw PolarErrors.deviceError(description: "Failed to check if BLE was disconnected due to removed pairing for device \(identifier). Error: \(err.localizedDescription)")
        }
    }

    /// Low level APIs. Intended for Polar internal use only!
    ///
    func readFile(identifier: String, filePath: String) async throws -> Data? {
        return try await fileUtils.readFile(identifier: identifier, filePath: filePath)
    }

    
    func writeFile(identifier: String, filePath: String, fileData: Data) async throws {
        try await fileUtils.writeFile(identifier: identifier, filePath: filePath, fileData: fileData)
    }

    
    func deleteFileOrDirectory(identifier: String, filePath: String) async throws {
        try await fileUtils.deleteFile(identifier: identifier, filePath: filePath)
    }


    func getFileList(identifier: String, directoryPath: String, recurseDeep: Bool) async throws -> [String] {
        return try await fileUtils.listFiles(identifier: identifier, directoryPath: directoryPath, recurseDeep: recurseDeep)
    }


    private func handleError(_ error: Error) -> Error {
        let nsError = error as NSError

        if let mapped = Protocol_PbPFtpError(rawValue: nsError.code) {
            return NSError(
                domain: nsError.domain,
                code: nsError.code,
                userInfo: [NSLocalizedDescriptionKey: "\(mapped) (\(nsError.localizedDescription))"]
            )
        }

        return error
    }

}

extension String {
    func matches(_ regex: String) -> Bool {
        if let regex = try? NSRegularExpression(pattern: regex, options: .caseInsensitive) {
            return regex.matches(in: self, options: [], range: NSRange(location: 0, length: self.count)).count == 1
        }
        return false
    }
    
    func contains(_ regex: String) -> Bool {
        if let regex = try? NSRegularExpression(pattern: regex, options: []) {
            return (regex.firstMatch(in: self, options: [], range: NSRange(location: 0, length: self.utf16.count)) != nil)
        }
        return false
    }

    func containsCaseInsensitive(_ regex: String) -> Bool {
        if let regex = try? NSRegularExpression(pattern: regex, options: [.caseInsensitive]) {
            return (regex.firstMatch(in: self, options: [], range: NSRange(location: 0, length: self.utf16.count)) != nil)
        }
        return false
    }
}



private extension GyrData {
    func mapToPolarData() -> PolarGyroData {
        var polarSamples: [(timeStamp: UInt64, x: Float, y: Float, z: Float)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z))
        }
        return PolarGyroData(polarSamples)
    }
}

private extension AccData {
    func mapToPolarData() -> PolarAccData {
        var polarSamples: [(timeStamp: UInt64, x: Int32, y: Int32, z: Int32)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z))
        }
        return PolarAccData(polarSamples)
    }
}

private extension MagData {
    func mapToPolarData() -> PolarMagnetometerData {
        var polarSamples: [(timeStamp: UInt64, x: Float, y: Float, z: Float)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z))
        }
        return PolarMagnetometerData(polarSamples)
    }
}

private extension PpiData {
    func mapToPolarData() -> PolarPpiData {
        var polarSamples: [(timeStamp: UInt64, hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, hr: sample.hr, ppInMs: sample.ppInMs, ppErrorEstimate: sample.ppErrorEstimate, blockerBit: sample.blockerBit, skinContactStatus: sample.skinContactStatus, skinContactSupported: sample.skinContactSupported))
        }
        return PolarPpiData(timeStamp: 0, samples: polarSamples)
    }
}

private extension OfflineHrData {
    func mapToPolarData() -> PolarHrData {
        var polarSamples: [(hr: UInt8, ppgQuality: UInt8, correctedHr: UInt8, rrsMs: [Int], rrAvailable: Bool, contactStatus: Bool, contactStatusSupported: Bool)] = []
        for sample in self.samples {
            let data = [(hr: sample.hr, ppgQuality: sample.ppgQuality, correctedHr: sample.correctedHr, rrsMs: [], rrAvailable: false, contactStatus: false, contactStatusSupported: false)]
            polarSamples.append((hr: sample.hr, ppgQuality: sample.ppgQuality, correctedHr: sample.correctedHr, rrsMs: [], rrAvailable: false, contactStatus: false, contactStatusSupported: false))
        }
        return polarSamples
    }
}

private extension EcgData {
    func mapToPolarData() -> PolarEcgData {
        var polarSamples: [(timeStamp: UInt64, voltage: Int32)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, voltage: sample.microVolts ))
        }
        return PolarEcgData(polarSamples)
    }
}

private extension PpgData {
    func mapToPolarData() -> PolarPpgData {
        var polarSamples: [(timeStamp:UInt64, channelSamples: [Int32], statusBits: [Int8]?) ] = []
        var dataType: PpgDataType! = .unknown

        for sample in self.samples {
            if (sample.frameType == PmdDataFrameType.type_0) {
                let ppgData = sample as! PpgDataFrameType0
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: [ppgData.ppgDataSamples[0], ppgData.ppgDataSamples[1], ppgData.ppgDataSamples[2], ppgData.ambientSample ], statusBits: nil ))
                dataType = PpgDataType.ppg3_ambient1
            }  else if (sample.frameType == PmdDataFrameType.type_6) {
                let ppgData = sample as! PpgDataFrameType6
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: [ppgData.sportId], statusBits: nil))
                dataType = PpgDataType.ppg1
            } else if (sample.frameType == PmdDataFrameType.type_7) {
                let ppgData = sample as! PpgDataFrameType7
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: ppgData.ppgDataSamples, statusBits: nil))
                dataType = PpgDataType.ppg17
            } else if (sample.frameType == PmdDataFrameType.type_10) {
                let ppgData = sample as! PpgDataFrameType10
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: ppgData.greenSamples + ppgData.redSamples + ppgData.irSamples, statusBits: ppgData.statusBits))
                dataType = PpgDataType.ppg21
            } else if (sample.frameType == PmdDataFrameType.type_9) {
                let ppgData = sample as! PpgDataFrameType9
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: ppgData.ppgDataSamples, statusBits: nil))
                dataType = PpgDataType.ppg3
            } else if (sample.frameType == PmdDataFrameType.type_13) {
                let ppgData = sample as! PpgDataFrameType13
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: [ppgData.ppgDataSamples[0], ppgData.ppgDataSamples[1]], statusBits: ppgData.statusBits ))
                dataType = PpgDataType.ppg2
            }
        }
        return PolarPpgData(type: dataType, samples: polarSamples)
    }
}

private extension TemperatureData {
    func mapToPolarData() -> PolarTemperatureData {
        var polarSamples: [(timeStamp: UInt64, temperature: Float)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, temperature: sample.temperature ))
        }
        return PolarTemperatureData(timeStamp: samples[0].timeStamp, samples: polarSamples)
    }
}

private extension PressureData {
    func mapToPolarData() -> PolarPressureData {
        var polarSamples: [(timeStamp: UInt64, pressure: Float)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, pressure: sample.pressure ))
        }
        return PolarPressureData(timeStamp: samples[0].timeStamp, samples: polarSamples)
    }
}

private extension SkinTemperatureData {
    func mapToPolarData() -> PolarTemperatureData {
        var polarSamples: [(timeStamp: UInt64, temperature: Float)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, temperature: sample.skinTemperature ))
        }
        return PolarTemperatureData(timeStamp: samples[0].timeStamp, samples: polarSamples)
    }
}

private extension Date {
    
    var onlyDate: Date? {
        get {
            let calender = Calendar.current
            var dateComponents = calender.dateComponents([.year, .month, .day], from: self)
            dateComponents.timeZone = NSTimeZone.system
            return calender.date(from: dateComponents)
        }
    }
}


// This extension keeps the wrong TimeZone information (UTC) in the localDate object although the date and time are returned for the CURRENT time zone.
extension Date {
    func localDate() throws -> Date {
        let nowUTC = Date()
        let timeZoneOffset = Double(TimeZone.current.secondsFromGMT(for: nowUTC))
        guard let localDate = Calendar.current.date(byAdding: .second, value: Int(timeZoneOffset), to: self) else {
            throw PolarErrors.invalidArgument(description: "Null date value found.")
        }
        return localDate
    }
}

private extension String {
    func findIndices(lookable: Character) -> [String.Index] {
        var indices: [String.Index] = []
        var index = 0
        for letter in self {
            if letter == lookable {
                indices.append(String.Index(encodedOffset: index))
            }
            index += 1
        }
        return indices
    }
}


