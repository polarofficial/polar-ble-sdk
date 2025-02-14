/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import RxSwift

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
            } else {
                powerStateObserver?.blePowerOff()
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
    let scheduler: SerialDispatchQueueScheduler
    var connectSubscriptions = [String : Disposable]()
    var serviceList = [CBUUID.init(string: "180D")]
    let features:Set<PolarBleSdkFeature>
    let dateFormatter = ISO8601DateFormatter()
    
    required public init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>) {
        var clientList: [(_ gattServiceTransmitter: BleAttributeTransportProtocol) -> BleGattClientBase] = []
        self.features = features
        
        // BleHrClient
        if(features.contains(PolarBleSdkFeature.feature_hr) ||
           features.contains(PolarBleSdkFeature.feature_polar_online_streaming) ) {
            clientList.append(BleHrClient.init)
        }
        
        // BleDisClient
        if(features.contains(PolarBleSdkFeature.feature_device_info)) {
            clientList.append(BleDisClient.init)
        }
        
        // BleBasClient
        if(features.contains(PolarBleSdkFeature.feature_battery_info)) {
            clientList.append(BleBasClient.init)
        }
        
        // BlePmdClient
        if(features.contains(PolarBleSdkFeature.feature_polar_online_streaming) ||
           features.contains(PolarBleSdkFeature.feature_polar_offline_recording) ||
           features.contains(PolarBleSdkFeature.feature_polar_sdk_mode)) {
            clientList.append(BlePmdClient.init)
        }
        
        // BlePsFtpClient
        if(features.contains(PolarBleSdkFeature.feature_polar_offline_recording) ||
           features.contains(PolarBleSdkFeature.feature_polar_h10_exercise_recording) ||
           features.contains(PolarBleSdkFeature.feature_polar_device_time_setup)) {
            clientList.append(BlePsFtpClient.init)
            //TODO, why this is needed?
            serviceList.append(CBUUID.init(string: "FEEE"))
        }
        
        self.queue = queue
        self.listener = CBDeviceListenerImpl(queue, clients: clientList, identifier: 0)
        self.listener.automaticH10Mapping = true
        self.scheduler = SerialDispatchQueueScheduler(queue: queue, internalSerialQueueName: "BleApiScheduler")
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
        let info = PolarDeviceInfo(
            session.advertisementContent.polarDeviceIdUntouched.count != 0 ? session.advertisementContent.polarDeviceIdUntouched : session.address.uuidString,
            session.address, Int(session.advertisementContent.rssiFilter.rssi),session.advertisementContent.name,true)
        switch session.state {
        case .sessionOpen:
            self.observer?.deviceConnected(info)
            self.setupDevice(session)
        case .sessionOpenPark where session.previousState == .sessionOpen: fallthrough
        case .sessionClosed where session.previousState == .sessionOpen: fallthrough
        case .sessionClosed where session.previousState == .sessionClosing:
            self.observer?.deviceDisconnected(info, pairingError: false)
        case .sessionOpenPark where session.previousState == .sessionOpening:
            self.observer?.deviceDisconnected(info, pairingError: true)
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
    
    fileprivate func deviceFilter(_ content: BleAdvertisementContent) -> Bool {
        return content.polarDeviceId.count != 0 && content.polarDeviceType != "mobile"
    }
    
    private func sessionPmdClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePmdClient.PMD_SERVICE)
        let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as! BlePmdClient
        if client.isCharacteristicNotificationEnabled(BlePmdClient.PMD_CP) &&
            client.isCharacteristicNotificationEnabled(BlePmdClient.PMD_DATA) {
            return session
        }
        throw PolarErrors.notificationNotEnabled
    }
    
    internal func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePsFtpClient.PSFTP_SERVICE)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        if client.isCharacteristicNotificationEnabled(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC) {
            return session
        }
        throw PolarErrors.notificationNotEnabled
    }
    
    private func sessionHrClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BleHrClient.HR_SERVICE)
        if let client = session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient,
           client.isCharacteristicNotificationEnabled(BleHrClient.HR_MEASUREMENT) {
            return session
        }
        throw PolarErrors.notificationNotEnabled
    }
    
    fileprivate func sessionServiceReady(_ identifier: String, service: CBUUID) throws -> BleDeviceSession {
        if let session = try fetchSession(identifier) {
            if session.state == BleDeviceSession.DeviceSessionState.sessionOpen {
                if let client = session.fetchGattClient(service){
                    if client.isServiceDiscovered() {
                        return session
                    }
                    throw PolarErrors.notificationNotEnabled
                }
                throw PolarErrors.serviceNotFound
            }
            throw PolarErrors.deviceNotConnected
        }
        throw PolarErrors.deviceNotFound
    }
    
    fileprivate func fetchSession(_ identifier: String) throws -> BleDeviceSession? {
        if identifier.matches("^([0-9a-fA-F]{8})(-[0-9a-fA-F]{4}){3}-([0-9a-fA-F]{12})") {
            return sessionByDeviceAddress(identifier)
        } else if identifier.matches("([0-9a-fA-F]){6,8}") {
            return sessionByDeviceId(identifier)
        }
        throw PolarErrors.invalidArgument()
    }
    
    fileprivate func sessionByDeviceAddress(_ identifier: String) -> BleDeviceSession? {
        return listener.allSessions().filter { (sess: BleDeviceSession) -> Bool in
            return sess.address.uuidString == identifier
        }.first
    }
    
    fileprivate func sessionByDeviceId(_ identifier: String) -> BleDeviceSession? {
        return listener.allSessions().filter { (sess: BleDeviceSession) -> Bool in
            return sess.advertisementContent.polarDeviceIdUntouched == identifier
        }.first
    }
    
    private func isHeartRateFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BleHrClient.HR_SERVICE)) {
            guard let client = session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient else {
                return Single.just(false)
            }
            return client.clientReady(true)
                .andThen(Single.just(true))
        } else {
            return Single.just(false)
        }
    }
    
    private func isDeviceInfoFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BleDisClient.DIS_SERVICE)) {
            guard let client = session.fetchGattClient(BleDisClient.DIS_SERVICE) as? BleDisClient else {
                return Single.just(false)
            }
            return client.clientReady(true)
                .andThen(Single.just(true))
        } else {
            return Single.just(false)
        }
    }
    
    private func isBatteryInfoFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BleBasClient.BATTERY_SERVICE)) {
            guard let client = session.fetchGattClient(BleBasClient.BATTERY_SERVICE) as? BleBasClient else {
                return Single.just(false)
            }
            return client.clientReady(true)
                .andThen(Single.just(true))
        } else {
            return Single.just(false)
        }
    }
    
    private func isH10ExerciseFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) && BlePolarDeviceCapabilitiesUtility.isRecordingSupported(session.advertisementContent.polarDeviceType)) {
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.just(false)
            }
            return client.clientReady(true)
                .andThen(Single.just(true))
        } else {
            return Single.just(false)
        }
    }
    
    private func isSdkModeFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BlePmdClient.PMD_SERVICE)) {
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
                return Single.just(false)
            }
            return client.clientReady(true)
                .andThen(
                    client.readFeature(true)
                        .map { (pmdFeatures) -> Bool in
                            if(pmdFeatures.contains(PmdMeasurementType.sdkMode)) {
                                return true
                            } else {
                                return false
                            }
                        }
                )
        } else {
            return Single.just(false)
        }
    }

    private func isLedAnimationFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BlePmdClient.PMD_SERVICE) && discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE)) {

            guard let pmdClient = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
                return Single.just(false)
            }
            guard let psftpClient = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.just(false)
            }

            let isPmdClientAvailable = pmdClient.clientReady(true)
                .andThen(
                    pmdClient.readFeature(true)
                        .map { (pmdFeatures) -> Bool in
                            if (pmdFeatures.contains(PmdMeasurementType.sdkMode)) {
                                return true
                            } else {
                                return false
                            }
                        }
                )

            let isFtpClientAvailable = psftpClient.clientReady(true)
                .andThen(Single.just(true))

            return Observable.combineLatest(isPmdClientAvailable.asObservable(), isFtpClientAvailable.asObservable()) { isPmdClientAvailable, isFtpClientAvailable -> Bool in
                return (isPmdClientAvailable && isFtpClientAvailable)
            }.asSingle()
        } else {
            return Single.just(false)
        }
    }

    private func isPolarFirmwareUpdateFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) && BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported(session.advertisementContent.polarDeviceType)) {
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.just(false)
            }
            return client.clientReady(true)
                .andThen(Single.just(true))
        } else {
            return Single.just(false)
        }
    }

    private func isPolarDeviceTimeFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE)) {
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.just(false)
            }
            return client.clientReady(true)
                .andThen(Single.just(true))
        } else {
            return Single.just(false)
        }
    }
    
    private func isOnlineStreamingAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        let isHrClientAvailable:Single<Bool>
        if (discoveredServices.contains(BleHrClient.HR_SERVICE)) {
            if let client = session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient {
                
                //TODO test this
                isHrClientAvailable = client.clientReady(true)
                    .andThen(Single.just(true))
                    .catchAndReturn(false)
            } else {
                isHrClientAvailable = Single.just(false)
            }
        } else {
            isHrClientAvailable = Single.just(false)
        }
        
        let isPmdClientAvailable:Single<Bool>
        if (discoveredServices.contains(BlePmdClient.PMD_SERVICE)) {
            if let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient {
                isPmdClientAvailable = client.clientReady(true)
                    .andThen(
                        client.readFeature(true)
                            .map { (pmdFeatures) -> Bool in
                                var anyDataTypes = false
                                for feature in pmdFeatures {
                                    if (feature.isDataType()) {
                                        anyDataTypes = true
                                        break
                                    }
                                }
                                return anyDataTypes
                            }
                    )
            } else {
                isPmdClientAvailable = Single.just(false)
            }
        } else {
            isPmdClientAvailable = Single.just(false)
        }
        
        return Observable.combineLatest(isHrClientAvailable.asObservable(), isPmdClientAvailable.asObservable()) { isHrClientAvailable, isPmdClientAvailable -> Bool in
            return (isHrClientAvailable || isPmdClientAvailable)
        }.asSingle()
    }
    
    
    private func isOfflineRecordingAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BlePmdClient.PMD_SERVICE) && discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE)) {
            
            guard let pmdClient = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
                return Single.just(false)
            }
            guard let psftpClient = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.just(false)
            }
            
            let isPmdClientAvailable = pmdClient.clientReady(true)
                .andThen(
                    pmdClient.readFeature(true)
                        .map { (pmdFeatures) -> Bool in
                            if(pmdFeatures.contains(PmdMeasurementType.offline_recording)) {
                                return true
                            } else {
                                return false
                            }
                        }
                )
            
            let isFtpClientAvailable = psftpClient.clientReady(true)
                .andThen(Single.just(true))
            
            return Observable.combineLatest(isPmdClientAvailable.asObservable(), isFtpClientAvailable.asObservable()) { isPmdClientAvailable, isFtpClientAvailable -> Bool in
                return (isPmdClientAvailable && isFtpClientAvailable)
            }.asSingle()
        } else {
            return Single.just(false)
        }
    }

    private func isPolarActivityDataFeatureAvailable(_ session: BleDeviceSession, _ discoveredServices: [CBUUID]) -> Single<Bool> {
        if (discoveredServices.contains(BlePsFtpClient.PSFTP_SERVICE) && BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(session.advertisementContent.polarDeviceType)) {
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.just(false)
            }
            return client.clientReady(true)
                .andThen(Single.just(true))
        } else {
            return Single.just(false)
        }
    }

    private func makeFeatureCallbackIfNeeded(session: BleDeviceSession, discoveredServices: [CBUUID], featurePolarOfflineRecording: PolarBleSdkFeature) -> Completable {
        let isFeatureAvailable: Single<Bool>
        switch(featurePolarOfflineRecording) {
        case .feature_hr:
            isFeatureAvailable = isHeartRateFeatureAvailable(session, discoveredServices)
        case .feature_device_info:
            isFeatureAvailable = isDeviceInfoFeatureAvailable(session, discoveredServices)
        case .feature_battery_info:
            isFeatureAvailable = isBatteryInfoFeatureAvailable(session, discoveredServices)
        case .feature_polar_online_streaming:
            isFeatureAvailable = isOnlineStreamingAvailable(session, discoveredServices)
        case .feature_polar_offline_recording:
            isFeatureAvailable = isOfflineRecordingAvailable(session, discoveredServices)
        case .feature_polar_h10_exercise_recording:
            isFeatureAvailable = isH10ExerciseFeatureAvailable(session, discoveredServices)
        case .feature_polar_device_time_setup:
            isFeatureAvailable = isPolarDeviceTimeFeatureAvailable(session, discoveredServices)
        case .feature_polar_sdk_mode:
            isFeatureAvailable = isSdkModeFeatureAvailable(session, discoveredServices)
        case .feature_polar_led_animation:
            isFeatureAvailable = isLedAnimationFeatureAvailable(session, discoveredServices)
        case .feature_polar_activity_data:
            isFeatureAvailable = isPolarActivityDataFeatureAvailable(session, discoveredServices)
        case .feature_polar_firmware_update:
            isFeatureAvailable = isPolarFirmwareUpdateFeatureAvailable(session, discoveredServices)
        }
        
        return isFeatureAvailable.flatMapCompletable { (isReady: Bool) -> Completable in
            if (isReady) {
                return Completable.create {[weak self] observer in
                    guard let self = self else {
                        observer(.completed)
                        return Disposables.create {}
                    }
                    let deviceId = session.advertisementContent.polarDeviceIdUntouched.count != 0 ?
                    session.advertisementContent.polarDeviceIdUntouched :
                    session.address.uuidString
                    
                    self.deviceFeaturesObserver?.bleSdkFeatureReady(deviceId, feature: featurePolarOfflineRecording)
                    
                    observer(.completed)
                    return Disposables.create {}
                }
            } else {
                return Completable.empty()
            }
        }
    }
    
    // hook clients based on services available
    fileprivate func setupDevice(_ session: BleDeviceSession) {
        let deviceId = session.advertisementContent.polarDeviceIdUntouched.count != 0 ?
        session.advertisementContent.polarDeviceIdUntouched :
        session.address.uuidString
        
        _ = session.monitorServicesDiscovered(true)
            .toArray()
            .asObservable()
            .flatMap { (uuid: [CBUUID]) -> Completable in
                var availableFeaturesList: [Completable] = []
                
                for feature in PolarBleSdkFeature.allCases {
                    if(self.features.contains(feature)) {
                        availableFeaturesList.append(self.makeFeatureCallbackIfNeeded(session: session, discoveredServices: uuid, featurePolarOfflineRecording: feature))
                    }
                }
                return Completable.concat(availableFeaturesList)
            }.subscribe { e in
                switch e {
                case .error(let error):
                    self.logMessage("\(error)")
                case .completed:
                    self.logMessage("device setup completed")
                }
            }
        
        _ = session.monitorServicesDiscovered(true)
            .observe(on: scheduler)
            .flatMap { (uuid: CBUUID) -> Observable<Any> in
                if let client = session.fetchGattClient(uuid) {
                    switch uuid {
                    case BleHrClient.HR_SERVICE:
                        self.deviceFeaturesObserver?.bleSdkFeatureReady(deviceId, feature: PolarBleSdkFeature.feature_hr)
                        let hrClient = client as! BleHrClient
                        self.startHrObserver(hrClient, deviceId: deviceId)
                    case BleBasClient.BATTERY_SERVICE:
                        return (client as! BleBasClient).monitorBatteryStatus(true)
                            .observe(on: self.scheduler)
                            .do(onNext: { (level: Int) in
                                self.deviceInfoObserver?.batteryLevelReceived(
                                    deviceId, batteryLevel: UInt(level))
                            })
                            .map { (_) -> Any in
                                return Any.self
                            }
                    case BleDisClient.DIS_SERVICE:
                        return (client as! BleDisClient).readDisInfo(true)
                            .observe(on: self.scheduler)
                            .do(onNext: { (arg0) in
                                self.deviceInfoObserver?.disInformationReceived(deviceId, uuid: arg0.0, value: arg0.1)
                            })
                            .flatMap { (arg0) -> Observable<(String, String)> in
                                return (client as! BleDisClient).readDisInfoWithKeysAsStrings(true)
                                    .do(onNext: { (arg0) in
                                        self.deviceInfoObserver?.disInformationReceivedWithKeysAsStrings(deviceId, key: arg0.0, value: arg0.1)
                                    }
                            )}
                            .map { (_) -> Any in
                                return Any.self
                            }
                    case BlePmdClient.PMD_SERVICE:
                        let pmdClient = (client as! BlePmdClient)
                        return pmdClient.clientReady(true)
                            .andThen(pmdClient.readFeature(true)
                                .observe(on: self.scheduler)
                                .do(onSuccess: { (value: Set<PmdMeasurementType>) in
                                    var featureSet = Set<PolarDeviceDataType>()
                                    if value.contains(PmdMeasurementType.ecg) {
                                        featureSet.insert(.ecg)
                                    }
                                    if value.contains(PmdMeasurementType.acc) {
                                        featureSet.insert(.acc)
                                    }
                                    if value.contains(PmdMeasurementType.ppg) {
                                        featureSet.insert(.ppg)
                                    }
                                    if value.contains(PmdMeasurementType.ppi) {
                                        featureSet.insert(.ppi)
                                    }
                                    if value.contains(PmdMeasurementType.gyro) {
                                        featureSet.insert(.gyro)
                                    }
                                    if value.contains(PmdMeasurementType.mgn) {
                                        featureSet.insert(.magnetometer)
                                    }
                                    if value.contains(PmdMeasurementType.temperature) {
                                        featureSet.insert(.temperature)
                                    }
                                    if value.contains(PmdMeasurementType.pressure) {
                                        featureSet.insert(.pressure)
                                    }
                                    if value.contains(PmdMeasurementType.skinTemperature) {
                                        featureSet.insert(.skinTemperature)
                                    }
                                    
                                    self.deviceFeaturesObserver?.bleSdkFeatureReady(deviceId, feature: PolarBleSdkFeature.feature_polar_online_streaming)

                                    if value.contains(PmdMeasurementType.sdkMode) {
                                        self.deviceFeaturesObserver?.bleSdkFeatureReady(deviceId, feature: PolarBleSdkFeature.feature_polar_sdk_mode)
                                    }
                                }))
                            .asObservable()
                            .map { (_) -> Any in
                                return Any.self
                            }
                    default:
                        break
                    }
                }
                return Observable<Any>.empty()
            }.subscribe { e in
                switch e {
                case .next(_):
                    break
                case .error(let error):
                    self.logMessage("\(error)")
                case .completed:
                    self.logMessage("device setup completed")
                }
            }
    }
    
    private func startHrObserver(_ client: BleHrClient, deviceId: String) {
        _ = client.observeHrNotifications(true)
            .observe(on: self.scheduler)
            .subscribe{ e in
                switch e {
                case .completed:
                    break
                case .next(let value):
                    self.deviceHrObserver?.hrValueReceived(
                        deviceId, data: (hr: UInt8(value.hr), rrs: value.rrs, rrsMs: value.rrsMs, contact: value.sensorContact, contactSupported: value.sensorContactSupported))
                case .error(let error):
                    self.logMessage("\(error)")
                }
            }
    }
}

extension PolarBleApiImpl: BleLoggerProtocol {
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
    
    func searchForDevice() -> Observable<PolarDeviceInfo> {
        return listener.search(nil, identifiers: nil)
            .distinct()
            .map({ value -> PolarDeviceInfo in
                return (value.advertisementContent.polarDeviceIdUntouched,
                        address: value.address,
                        rssi: Int(value.advertisementContent.medianRssi),
                        name: value.advertisementContent.name,
                        connectable: value.advertisementContent.isConnectable)
            })
    }
    
    func startAutoConnectToDevice(_ rssi: Int, service: CBUUID?, polarDeviceType: String?) -> Completable {
        return listener.search(serviceList, identifiers: nil)
            .filter { (sess: BleDeviceSession) -> Bool in
                return sess.advertisementContent.medianRssi >= rssi &&
                sess.isConnectable() &&
                (polarDeviceType == nil || polarDeviceType == sess.advertisementContent.polarDeviceType) &&
                (service == nil || sess.advertisementContent.containsService(service!))
            }
            .take(1)
            .do(onNext: { (session) in
                self.logMessage("auto connect search complete")
#if os(watchOS)
                session.connectionType = .directConnection
#endif
                self.listener.openSessionDirect(session)
            })
            .asSingle()
            .asCompletable()
    }
    
    func connectToDevice(_ identifier: String) throws {
        let session = try fetchSession(identifier)
        if  session == nil || session?.state == BleDeviceSession.DeviceSessionState.sessionClosed {
            if let sub = connectSubscriptions[identifier] {
                sub.dispose()
            }
            if session != nil {
#if os(watchOS)
                session!.connectionType = .directConnection
#endif
                self.listener.openSessionDirect(session!)
            } else {
                connectSubscriptions[identifier] = listener.search(serviceList, identifiers: nil)
                    .observe(on: scheduler)
                    .filter { (sess: BleDeviceSession) -> Bool in
                        return identifier.contains("-") ? sess.address.uuidString == identifier : sess.advertisementContent.polarDeviceIdUntouched == identifier
                    }
                    .take(1)
                    .subscribe{ e in
                        switch e {
                        case .completed:
                            self.logMessage("connect search complete")
                        case .error(let error):
                            self.logMessage("\(error)")
                        case .next(let value):
#if os(watchOS)
                            value.connectionType = .directConnection
#endif
                            self.listener.openSessionDirect(value)
                        }
                    }
            }
        }
    }
    
    func disconnectFromDevice(_ identifier: String) throws {
        if let session = try fetchSession(identifier) {
            if (session.state == BleDeviceSession.DeviceSessionState.sessionOpen ||
                session.state == BleDeviceSession.DeviceSessionState.sessionOpening ||
                session.state == BleDeviceSession.DeviceSessionState.sessionOpenPark){
                listener.closeSessionDirect(session)
            }
        }
        connectSubscriptions.removeValue(forKey: identifier)?.dispose()
    }
    
    func isFeatureReady(_ identifier: String, feature: PolarBleSdkFeature) -> Bool {
        switch feature {
            
        case .feature_hr:
            do {
                _ = try sessionHrClientReady(identifier)
                return true
            } catch _ {
                // do nothing
            }
        case .feature_device_info:
            do {
                _ = try sessionServiceReady(identifier, service: BleDisClient.DIS_SERVICE)
                return true
            } catch _ {
                // do nothing
            }
        case .feature_battery_info:
            do {
                _ = try sessionServiceReady(identifier, service: BleBasClient.BATTERY_SERVICE)
                return true
            } catch _ {
                // do nothing
            }
        case .feature_polar_online_streaming:
            do {
                _ = try sessionHrClientReady(identifier)
                _ = try sessionPmdClientReady(identifier)
                return true
            } catch _ {
                // do nothing
            }
        case .feature_polar_offline_recording:
            do {
                _ = try sessionFtpClientReady(identifier)
                _ = try sessionPmdClientReady(identifier)
                return true
            } catch _ {
                // do nothing
            }
        case .feature_polar_device_time_setup:
            do {
                _ = try sessionFtpClientReady(identifier)
                return true
            } catch _ {
                // do nothing
            }
        case .feature_polar_h10_exercise_recording:
            do {
                let session = try sessionFtpClientReady(identifier)
                guard session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) is BlePsFtpClient else {
                    return false
                }
                if .h10FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType( session.advertisementContent.polarDeviceType) {
                    return true
                } else {
                    return false
                }
            } catch _ {
                // do nothing
            }
        case .feature_polar_sdk_mode:
            do {
                _ = try sessionPmdClientReady(identifier)
                return true
            } catch _ {
                // do nothing
            }
        case .feature_polar_led_animation:
            do {
                _ = try sessionFtpClientReady(identifier)
                return true
            } catch _ {
                // do nothing
            }
        case .feature_polar_firmware_update:
            do {
                _ = try sessionFtpClientReady(identifier)
                return true
            } catch _ {
                // do nothing
            }
        case .feature_polar_activity_data:
            do {
                _ = try sessionFtpClientReady(identifier)
                return true
            } catch _ {
                // do nothing
            }
        }
        
        return false
    }
    
    func setLocalTime(_ identifier: String, time: Date, zone: TimeZone) -> Completable {
        do {
            let session = try sessionFtpClientReady(identifier)
            
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }
            
            guard let pbLocalDateTime = PolarTimeUtils.dateToPbPftpSetLocalTime(time: time, zone: zone),
                  let pbSystemDateTime = PolarTimeUtils.dateToPbPftpSetSystemTime(time: time) else {
                return Completable.error(PolarErrors.dateTimeFormatFailed())
            }
            
            self.logMessage("set local time to \(time) and timeZone \(zone) in device \(identifier)")
            
            let paramsSetLocalTime = try pbLocalDateTime.serializedData()
            let paramsSetSystemTime = try pbSystemDateTime.serializedData()
            
            switch BlePolarDeviceCapabilitiesUtility.fileSystemType( session.advertisementContent.polarDeviceType) {
            case .unknownFileSystem:
                return Completable.empty()
            case .h10FileSystem:
                return client.query(Protocol_PbPFtpQuery.setLocalTime.rawValue, parameters: paramsSetLocalTime as NSData).catch { error in
                    return Single.error(PolarErrors.deviceError(description: "\(error)"))
                }
                .asCompletable()
            case .sagRfc2FileSystem:
                return  Single.zip(
                    client.query(Protocol_PbPFtpQuery.setLocalTime.rawValue, parameters: paramsSetLocalTime as NSData),
                    client.query(Protocol_PbPFtpQuery.setSystemTime.rawValue, parameters: paramsSetSystemTime as NSData))
                .catch { error in
                    return Single.error(PolarErrors.deviceError(description: "\(error)"))
                }
                .asCompletable()
            }
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func getLocalTime(_ identifier: String) -> RxSwift.Single<Date> {
        do {
            let session = try sessionFtpClientReady(identifier)
            
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }
            self.logMessage("get local time from device \(identifier)")
            switch BlePolarDeviceCapabilitiesUtility.fileSystemType( session.advertisementContent.polarDeviceType) {
            case .h10FileSystem ,
                    .unknownFileSystem:
                return Single.error(PolarErrors.operationNotSupported)
            case .sagRfc2FileSystem:
                return client.query(Protocol_PbPFtpQuery.getLocalTime.rawValue, parameters: nil)
                    .map { data in
                        let result = try Protocol_PbPFtpSetLocalTimeParams(serializedData: data as Data)
                        let date = try PolarTimeUtils.dateFromPbPftpLocalDateTime(result)
                        return date
                    }
            }
        } catch let err {
            return Single.error(err)
        }
    }
    
    func getDiskSpace(_ identifier: String) -> Single<PolarDiskSpaceData> {
        do {
            let session = try sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }
            return client.query(Protocol_PbPFtpQuery.getDiskSpace.rawValue, parameters: nil)
                .map { data in
                    let proto = try Protocol_PbPFtpDiskSpaceResult(serializedData: data as Data)
                    return PolarDiskSpaceData.fromProto(proto: proto)
                }.catch { error in
                    return Single<PolarDiskSpaceData>.error(error)
                }
        } catch let error {
            return Single<PolarDiskSpaceData>.error(error)
        }
    }
    
    func startRecording(_ identifier: String, exerciseId: String, interval: RecordingInterval = RecordingInterval.interval_1s, sampleType: SampleType) -> Completable {
        do {
            guard exerciseId.count > 0 && exerciseId.count < 64 else {
                throw PolarErrors.invalidArgument()
            }
            let session = try sessionFtpClientReady(identifier)
            let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
            if BlePolarDeviceCapabilitiesUtility.isRecordingSupported(session.advertisementContent.polarDeviceType) {
                var duration = PbDuration()
                duration.seconds = UInt32(interval.rawValue)
                var params = Protocol_PbPFtpRequestStartRecordingParams()
                params.recordingInterval = duration
                params.sampleDataIdentifier = exerciseId
                params.sampleType = sampleType == .hr ? PbSampleType.sampleTypeHeartRate : PbSampleType.sampleTypeRrInterval
                let queryParams = try params.serializedData()
                return client.query(Protocol_PbPFtpQuery.requestStartRecording.rawValue, parameters: queryParams as NSData)
                    .catch { (err) -> Single<NSData> in
                        return Single.error(err)
                    }
                    .asCompletable()
                
                
            }
            throw PolarErrors.operationNotSupported
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func stopRecording(_ identifier: String) -> Completable {
        do {
            let session = try sessionFtpClientReady(identifier)
            let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
            if BlePolarDeviceCapabilitiesUtility.isRecordingSupported(
                session.advertisementContent.polarDeviceType)
            {
                return client.query(Protocol_PbPFtpQuery.requestStopRecording.rawValue, parameters: nil)
                    .catch { (err) -> Single<NSData> in
                        return Single.error(PolarErrors.deviceError(description: "\(err)"))
                    }
                    .asCompletable()
            }
            throw PolarErrors.operationNotSupported
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func requestRecordingStatus(_ identifier: String) -> Single<PolarRecordingStatus> {
        do {
            let session = try sessionFtpClientReady(identifier)
            let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
            if BlePolarDeviceCapabilitiesUtility.isRecordingSupported( session.advertisementContent.polarDeviceType) {
                return client.query(Protocol_PbPFtpQuery.requestRecordingStatus.rawValue, parameters: nil)
                    .map { (data) -> PolarRecordingStatus in
                        let result = try Protocol_PbRequestRecordingStatusResult(serializedData: data as Data)
                        return (ongoing: result.recordingOn, entryId: result.hasSampleDataIdentifier ? result.sampleDataIdentifier : "")
                    }
                    .catch { (err) -> Single<PolarRecordingStatus> in
                        return Single.error(PolarErrors.deviceError(description: "\(err)"))
                    }
            }
            throw PolarErrors.operationNotSupported
        } catch let err {
            return Single.error(err)
        }
    }
    
    func removeExercise(_ identifier: String, entry: PolarExerciseEntry) -> Completable {
        do {
            let session = try sessionFtpClientReady(identifier)
            let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
            let components = entry.path.split(separator: "/")
            var operation = Protocol_PbPFtpOperation()
            switch BlePolarDeviceCapabilitiesUtility.fileSystemType( session.advertisementContent.polarDeviceType) {
            case .sagRfc2FileSystem:
                operation.command = .get
                operation.path = "/U/0/" + components[2] + "/E/"
                let request = try operation.serializedData()
                return client.request(request)
                    .flatMap { (content) -> Single<NSData> in
                        do {
                            let dir = try Protocol_PbPFtpDirectory(serializedData: content as Data)
                            var removeOperation = Protocol_PbPFtpOperation()
                            removeOperation.command = .remove
                            if dir.entries.count <= 1 {
                                removeOperation.path = "/U/0/" + components[2] + "/"
                            } else {
                                removeOperation.path = "/U/0/" + components[2] + "/E/" + components[4] + "/"
                            }
                            let request = try removeOperation.serializedData()
                            return client.request(request)
                        } catch {
                            return Single.error(PolarErrors.messageDecodeFailed)
                        }
                    }
                    .catch { (err) -> Single<NSData> in
                        return Single.error(PolarErrors.deviceError(description: "\(err)"))
                    }
                    .asCompletable()
            case .h10FileSystem:
                operation.command = .remove
                operation.path = entry.path
                let request = try operation.serializedData()
                
                return client.request(request)
                    .observe(on: self.scheduler)
                    .catch { (err) -> Single<NSData> in
                        return Single.error(PolarErrors.deviceError(description: "\(err)"))
                    }
                    .asCompletable()
            default:
                throw PolarErrors.operationNotSupported
            }
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func startListenForPolarHrBroadcasts(_ identifiers: Set<String>?) -> Observable<PolarHrBroadcastData> {
        BleLogger.trace( "Start Hr broadcast listener. Filtering: \(identifiers != nil)")
        return listener.search(serviceList, identifiers: nil)
            .filter({ (session) -> Bool in
                return (identifiers == nil || identifiers!.contains(session.advertisementContent.polarDeviceIdUntouched)) &&
                session.advertisementContent.polarHrAdvertisementData.isPresent &&
                session.advertisementContent.polarHrAdvertisementData.isHrDataUpdated
            })
            .map({ (value) -> PolarHrBroadcastData in
                return (deviceInfo: (value.advertisementContent.polarDeviceIdUntouched,
                                     address: value.address,
                                     rssi: Int(value.advertisementContent.rssiFilter.rssi), name: value.advertisementContent.name, connectable: value.advertisementContent.isConnectable), hr:  value.advertisementContent.polarHrAdvertisementData.hrValueForDisplay,
                        batteryStatus: value.advertisementContent.polarHrAdvertisementData.batteryStatus)
            })
    }
    
    func requestStreamSettings(_ identifier: String, feature: PolarDeviceDataType) -> Single<PolarSensorSetting> {
        BleLogger.trace("Request online stream settings. Feature: \(feature) Device: \(identifier)")
        
        switch feature {
        case .ecg:
            return querySettings(identifier, type: .ecg, recordingType: PmdRecordingType.online)
        case .acc:
            return querySettings(identifier, type: .acc, recordingType: PmdRecordingType.online)
        case .ppg:
            return querySettings(identifier, type: .ppg, recordingType: PmdRecordingType.online)
        case .magnetometer:
            return querySettings(identifier, type: .mgn, recordingType: PmdRecordingType.online)
        case .gyro:
            return querySettings(identifier, type: .gyro, recordingType: PmdRecordingType.online)
        case .temperature:
            return querySettings(identifier, type: .temperature, recordingType: PmdRecordingType.online)
        case .skinTemperature:
            return querySettings(identifier, type: .skinTemperature, recordingType: PmdRecordingType.online)
        case .pressure:
            return querySettings(identifier, type: .pressure, recordingType: PmdRecordingType.online)
        case .ppi, .hr:
            return Single.error(PolarErrors.operationNotSupported)
        }
    }
    
    func requestFullStreamSettings(_ identifier: String, feature: PolarDeviceDataType) -> Single<PolarSensorSetting> {
        BleLogger.trace("Request full online stream settings. Feature: \(feature) Device: \(identifier)")
        
        switch feature {
        case .ecg:
            return queryFullSettings(identifier, type: .ecg, recordingType: PmdRecordingType.online)
        case .acc:
            return queryFullSettings(identifier, type: .acc, recordingType: PmdRecordingType.online)
        case .ppg:
            return queryFullSettings(identifier, type: .ppg, recordingType: PmdRecordingType.online)
        case .magnetometer:
            return queryFullSettings(identifier, type: .mgn, recordingType: PmdRecordingType.online)
        case .gyro:
            return queryFullSettings(identifier, type: .gyro, recordingType: PmdRecordingType.online)
        case .ppi, .hr, .temperature, .pressure, .skinTemperature:
            return Single.error(PolarErrors.operationNotSupported)
        }
    }
    
    func requestOfflineRecordingSettings(_ identifier: String, feature: PolarDeviceDataType) -> RxSwift.Single<PolarSensorSetting> {
        
        BleLogger.trace("Request offline stream settings. Feature: \(feature) Device: \(identifier)")
        switch feature {
        case .ecg:
            return querySettings(identifier, type: .ecg, recordingType: PmdRecordingType.offline)
        case .acc:
            return querySettings(identifier, type: .acc, recordingType: PmdRecordingType.offline)
        case .ppg:
            return querySettings(identifier, type: .ppg, recordingType: PmdRecordingType.offline)
        case .magnetometer:
            return querySettings(identifier, type: .mgn, recordingType: PmdRecordingType.offline)
        case .gyro:
            return querySettings(identifier, type: .gyro, recordingType: PmdRecordingType.offline)
        case .ppi, .hr:
            return Single.error(PolarErrors.operationNotSupported)
        case .temperature:
            return querySettings(identifier, type: .temperature, recordingType: PmdRecordingType.offline)
        case .skinTemperature:
            return querySettings(identifier, type: .skinTemperature, recordingType: PmdRecordingType.offline)
        case .pressure:
            return querySettings(identifier, type: .pressure, recordingType: PmdRecordingType.offline)
        }
    }
    
    func requestFullOfflineRecordingSettings(_ identifier: String, feature: PolarDeviceDataType) -> RxSwift.Single<PolarSensorSetting> {
        BleLogger.trace("Request full offline stream settings. Feature: \(feature) Device: \(identifier)")
        
        switch feature {
        case .ecg:
            return queryFullSettings(identifier, type: .ecg, recordingType: PmdRecordingType.offline)
        case .acc:
            return queryFullSettings(identifier, type: .acc, recordingType: PmdRecordingType.offline)
        case .ppg:
            return queryFullSettings(identifier, type: .ppg, recordingType: PmdRecordingType.offline)
        case .magnetometer:
            return queryFullSettings(identifier, type: .mgn, recordingType: PmdRecordingType.offline)
        case .gyro:
            return queryFullSettings(identifier, type: .gyro, recordingType: PmdRecordingType.offline)
        case .ppi, .hr, .pressure:
            return Single.error(PolarErrors.operationNotSupported)
        case .temperature:
            return queryFullSettings(identifier, type: .temperature, recordingType: PmdRecordingType.offline)
        case .skinTemperature:
            return queryFullSettings(identifier, type: .skinTemperature, recordingType: PmdRecordingType.offline)
        }
    }
    
    func getAvailableOfflineRecordingDataTypes(_ identifier: String) -> Single<Set<PolarDeviceDataType>> {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Single.error(PolarErrors.serviceNotFound) }
            return client.readFeature(true)
                .map { pmdFeature -> Set<PolarDeviceDataType> in
                    var deviceData: Set<PolarDeviceDataType> = Set()
                    if (pmdFeature.contains(PmdMeasurementType.ecg)) {
                        deviceData.insert(PolarDeviceDataType.ecg)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.acc)) {
                        deviceData.insert(PolarDeviceDataType.acc)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.ppg)) {
                        deviceData.insert(PolarDeviceDataType.ppg)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.ppi)) {
                        deviceData.insert(PolarDeviceDataType.ppi)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.gyro)) {
                        deviceData.insert(PolarDeviceDataType.gyro)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.mgn)) {
                        deviceData.insert(PolarDeviceDataType.magnetometer)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.offline_hr)) {
                        deviceData.insert(PolarDeviceDataType.hr)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.temperature)) {
                        deviceData.insert(PolarDeviceDataType.temperature)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.skinTemperature)) {
                        deviceData.insert(PolarDeviceDataType.skinTemperature)
                    }
                    return deviceData
                }
        } catch let err {
            return Single.error(err)
        }
    }
    
    func getOfflineRecordingStatus(_ identifier: String) -> Single<[PolarDeviceDataType:Bool]> {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Single.error(PolarErrors.serviceNotFound) }
            
            BleLogger.trace( "Get offline recording status. Device: \(identifier)")
            return client.readMeasurementStatus()
                .map { status -> [PolarDeviceDataType:Bool] in
                    
                    var activeOfflineRecordings = [PolarDeviceDataType:Bool]()
                    try status.forEach {  element in
                        
                        let polarFeature = try PolarDataUtils.mapToPolarFeature(from: element.0)
                        if (element.1 == PmdActiveMeasurement.offline_measurement_active ||
                            element.1 == PmdActiveMeasurement.online_offline_measurement_active
                        ) {
                            activeOfflineRecordings[polarFeature] = true
                        } else {
                            activeOfflineRecordings[polarFeature] = false
                            //activeOfflineRecordings.append(polarFeature, false)
                        }
                    }
                    
                    return activeOfflineRecordings
                }
        } catch let err {
            return Single.error(err)
        }
    }
    
    func listOfflineRecordings(_ identifier: String) -> Observable<PolarOfflineRecordingEntry> {
        do {
            let session = try sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            guard .sagRfc2FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                throw PolarErrors.operationNotSupported
            }

            BleLogger.trace("Start offline recording listing in device: \(identifier)")

            return fetchRecursive("/U/0/", client: client, condition: { entry in
                entry.matches("^([0-9]{8})(\\/)") ||
                entry.matches("^([0-9]{6})(\\/)") ||
                entry == "R/" ||
                entry.contains(".REC")
            })
            .flatMap { entry -> Observable<PolarOfflineRecordingEntry> in
                let components = entry.name.split(separator: "/")
                let dateFormatter = DateFormatter()
                dateFormatter.locale = Locale(identifier: "en_US_POSIX")

                if components[2].count == 8 && components[4].count == 6 {
                    dateFormatter.dateFormat = "yyyyMMddHHmmss"
                } else {
                    dateFormatter.dateFormat = "yyyyMMddHHmm"
                }

                guard let date = dateFormatter.date(from: String(components[2] + components[4])) else {
                    return Observable.error(PolarErrors.dateTimeFormatFailed(description: "Listing offline recording failed. Couldn't parse create data from date \(components[2]) and time \(components[4])"))
                }

                guard let pmdMeasurementType = try? OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: String(components[5])) else {
                    return Observable.error(PolarErrors.polarBleSdkInternalException(description: "Listing offline recording failed. Couldn't parse the pmd type from \(components[5])"))
                }

                guard let type = try? PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType) else {
                    return Observable.error(PolarErrors.polarBleSdkInternalException(description: "Listing offline recording failed. Couldn't parse the polar type from pmd type: \(pmdMeasurementType)"))
                }

                let polarEntry = PolarOfflineRecordingEntry(
                    path: entry.name,
                    size: UInt(entry.size),
                    date: date,
                    type: type
                )
                BleLogger.trace("Adding entry: \(polarEntry)")
                return Observable.just(polarEntry)
            }
            .groupBy { entry in
                entry.path.replacingOccurrences(of: "\\d+\\.REC$", with: ".REC", options: .regularExpression)
            }
            .flatMap { groupedEntries -> Observable<PolarOfflineRecordingEntry> in
                return groupedEntries
                    .reduce([]) { (accumulator, entry) -> [PolarOfflineRecordingEntry] in
                        var updatedAccumulator = accumulator
                        updatedAccumulator.append(entry)
                        return updatedAccumulator
                    }
                    .flatMap { entriesList -> Observable<PolarOfflineRecordingEntry> in
                        guard let firstEntry = entriesList.first else {
                            return Observable.empty()
                        }

                        var totalSize = 0
                        entriesList.forEach { entry in
                            totalSize += Int(entry.size)
                        }

                        let modifiedEntry = PolarOfflineRecordingEntry(
                            path: firstEntry.path,
                            size: UInt(totalSize),
                            date: firstEntry.date,
                            type: firstEntry.type
                        )
                        BleLogger.trace("Merging entries: \(entriesList) into: \(modifiedEntry)")
                        return Observable.just(modifiedEntry)
                    }
            }
        } catch {
            return Observable.error(error)
        }
    }
    
   func getOfflineRecord(
          _ identifier: String,
          entry: PolarOfflineRecordingEntry,
          secret: PolarRecordingSecret?
      ) -> Single<PolarOfflineRecordingData> {
          do {
              let session = try sessionFtpClientReady(identifier)
              guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                  throw PolarErrors.serviceNotFound
              }
              guard .sagRfc2FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                  throw PolarErrors.operationNotSupported
              }

              let subRecordingCountObservable = getSubRecordingCount(identifier: identifier, entry: entry).asObservable()

              return Single.create { single in
                  var polarAccData: PolarOfflineRecordingData?
                  var polarGyroData: PolarOfflineRecordingData?
                  var polarMagData: PolarOfflineRecordingData?
                  var polarPpgData: PolarOfflineRecordingData?
                  var polarPpiData: PolarOfflineRecordingData?
                  var polarHrData: PolarOfflineRecordingData?
                  var polarTemperatureData: PolarOfflineRecordingData?
                  var polarSkinTemperatureData: PolarOfflineRecordingData?

                  let lastTimestamp: UInt64 = 0

                  let processingObservable = subRecordingCountObservable
                      .flatMap { count -> Observable<PolarOfflineRecordingData> in
                          return Observable.range(start: 0, count: count)
                              .flatMap { subRecordingIndex -> Observable<PolarOfflineRecordingData> in
                                  Observable.create { observer in
                                      let subRecordingPath: String
                                      if entry.path.range(of: ".*\\.REC$", options: .regularExpression) != nil && count > 0 {
                                          subRecordingPath = entry.path.replacingOccurrences(of: "\\d(?=\\.REC$)", with: "\(subRecordingIndex)", options: .regularExpression)
                                      } else {
                                          subRecordingPath = entry.path
                                      }

                                      do {
                                          var operation = Protocol_PbPFtpOperation()
                                          operation.command = .get
                                          operation.path = subRecordingPath.isEmpty ? entry.path : subRecordingPath
                                          let request = try operation.serializedData()

                                          BleLogger.trace("Offline record get. Device: \(identifier) Path: \(subRecordingPath) Secret used: \(secret != nil)")

                                          let notificationResult = client.sendNotification(
                                              Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue,
                                              parameters: nil
                                          )

                                          let requestResult = notificationResult
                                              .andThen(Single.deferred { client.request(request) })
                                              .map { dataResult -> OfflineRecordingData<Any> in
                                                  do {
                                                      let pmdSecret = try secret.map { try PolarDataUtils.mapToPmdSecret(from: $0) }
                                                      return try OfflineRecordingData<Any>.parseDataFromOfflineFile(
                                                          fileData: dataResult as Data,
                                                          type: PolarDataUtils.mapToPmdClientMeasurementType(from: entry.type),
                                                          secret: pmdSecret,
                                                          lastTimestamp: lastTimestamp
                                                      )
                                                  } catch {
                                                      throw PolarErrors.polarOfflineRecordingError(description: "Failed to parse data")
                                                  }
                                              }

                                          _ = requestResult.subscribe(
                                              onSuccess: { offlineRecordingData in
                                                  let settings: PolarSensorSetting = offlineRecordingData.recordingSettings?.mapToPolarSettings() ?? PolarSensorSetting()

                                                  switch offlineRecordingData.data {
                                                  case let accData as AccData:
                                                      polarAccData = self.processAccData(accData, polarAccData, offlineRecordingData, settings)
                                                      observer.onNext(polarAccData!)
                                                  case let gyroData as GyrData:
                                                      polarGyroData = self.processGyroData(gyroData, polarGyroData, offlineRecordingData, settings)
                                                      observer.onNext(polarGyroData!)
                                                  case let magData as MagData:
                                                      polarMagData = self.processMagData(magData, polarMagData, offlineRecordingData, settings)
                                                      observer.onNext(polarMagData!)
                                                  case let ppgData as PpgData:
                                                      polarPpgData = self.processPpgData(ppgData, polarPpgData, offlineRecordingData, settings)
                                                      observer.onNext(polarPpgData!)
                                                  case let ppiData as PpiData:
                                                      polarPpiData = self.processPpiData(ppiData, polarPpiData, offlineRecordingData)
                                                      observer.onNext(polarPpiData!)
                                                  case let hrData as OfflineHrData:
                                                      polarHrData = self.processHrData(hrData, polarHrData, offlineRecordingData)
                                                      observer.onNext(polarHrData!)
                                                  case let temperatureData as TemperatureData:
                                                      polarTemperatureData = self.processTemperatureData(temperatureData, polarTemperatureData, offlineRecordingData)
                                                      observer.onNext(polarTemperatureData!)
                                                  case let skinTemperatureData as SkinTemperatureData:
                                                      polarSkinTemperatureData = self.processSkinTemperatureData(skinTemperatureData, polarSkinTemperatureData, offlineRecordingData)
                                                      observer.onNext(polarTemperatureData!)
                                                  default:
                                                      observer.onError(PolarErrors.polarOfflineRecordingError(description: "GetOfflineRecording failed. Data type is not supported."))
                                                      return
                                                  }
                                                  observer.onCompleted()
                                              },
                                              onFailure: { error in
                                                  observer.onError(error)
                                              }
                                          )
                                      } catch {
                                          observer.onError(error)
                                      }

                                      return Disposables.create { }
                                  }
                              }
                      }
                      .ignoreElements()
                      .asCompletable()

                  _ = processingObservable.andThen(Single.deferred {
                      let data = polarAccData ?? polarGyroData ?? polarMagData ?? polarPpgData ?? polarPpiData ?? polarHrData ?? polarTemperatureData
                      if let validData = data {
                          return Single.just(validData)
                      } else {
                          return Single.error(PolarErrors.polarOfflineRecordingError(description: "Invalid data"))
                      }
                  })
                  .subscribe(
                      onSuccess: { data in
                          single(.success(data))
                      },
                      onFailure: { error in
                          single(.failure(error))
                      }
                  )

                  return Disposables.create { }
              }
          } catch {
              return Single.error(error)
          }
    }

    func getSubRecordingCount(identifier: String, entry: PolarOfflineRecordingEntry) -> Single<Int> {
        return Single.create { single in
            do {
                let session = try self.sessionFtpClientReady(identifier)
                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    single(.failure(PolarErrors.serviceNotFound))
                    return Disposables.create()
                }
                
                var operation = Protocol_PbPFtpOperation()
                operation.command = Protocol_PbPFtpOperation.Command.get
                let directoryPath = entry.path.components(separatedBy: "/").dropLast().joined(separator: "/") + "/"
                operation.path = directoryPath
                
                _ = client.request(try operation.serializedData())
                    .subscribe(
                        onSuccess: { content in
                            do {
                                let directory = try Protocol_PbPFtpDirectory(serializedData: content as Data)
                                single(.success(directory.entries.count))
                            } catch {
                                single(.failure(error))
                            }
                        },
                        onFailure: { error in
                            single(.failure(error))
                        }
                    )
            } catch {
                single(.failure(error))
            }
            return Disposables.create()
        }
    }
    
    func getSubRecordings(identifier: String, entry: PolarOfflineRecordingEntry) -> Single<Array<String>> {
        return Single.create { single in
            do {
                let session = try self.sessionFtpClientReady(identifier)
                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    single(.failure(PolarErrors.serviceNotFound))
                    return Disposables.create()
                }
                
                var operation = Protocol_PbPFtpOperation()
                operation.command = Protocol_PbPFtpOperation.Command.get
                let directoryPath = entry.path.components(separatedBy: "/").dropLast().joined(separator: "/") + "/"
                operation.path = directoryPath
                
                _ = client.request(try operation.serializedData())
                    .subscribe(
                        onSuccess: { content in
                            do {
                                let directory = try Protocol_PbPFtpDirectory(serializedData: content as Data)
                                var records = [String]()
                                for entry in directory.entries {
                                    records.append(entry.name)
                                }
                                single(.success(records))
                            } catch {
                                single(.failure(error))
                            }
                        },
                        onFailure: { error in
                            single(.failure(error))
                        }
                    )
            } catch {
                single(.failure(error))
            }
            return Disposables.create()
        }
    }

    func listSplitOfflineRecordings(_ identifier: String) -> Observable<PolarOfflineRecordingEntry> {
        do {
            let session = try sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Observable.error(PolarErrors.serviceNotFound)
            }
            guard .sagRfc2FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                return Observable.error(PolarErrors.operationNotSupported)
            }
            BleLogger.trace("Start offline recording listing in device: \(identifier)")
            return fetchRecursive("/U/0/", client: client, condition: { (entry) -> Bool in
                return entry.matches("^([0-9]{8})(\\/)") ||
                entry.matches("^([0-9]{6})(\\/)") ||
                entry == "R/" ||
                entry.contains(".REC")
            })
            .map { (entry) -> PolarOfflineRecordingEntry in
                let components = entry.name.split(separator: "/")
                let dateFormatter = DateFormatter()
                dateFormatter.locale = Locale(identifier: "en_US_POSIX")
                dateFormatter.dateFormat = "yyyyMMddHHmmss"

                guard let date = dateFormatter.date(from: String(components[2] + components[4])) else {
                    throw PolarErrors.dateTimeFormatFailed(description: "Listing offline recording failed. Couldn't parse create data from date \(components[2]) and time \(components[4])")
                }
                guard let pmdMeasurementType = try? OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName:  String(components[5])) else {
                    throw PolarErrors.polarBleSdkInternalException(description: "Listing offline recording failed. Couldn't parse the pmd type from \(components[5])")
                }
                guard let type = try? PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType) else {
                    throw PolarErrors.polarBleSdkInternalException(description: "Listing offline recording failed. Couldn't parse the polar type from pmd type: \(pmdMeasurementType)")
                }

                return PolarOfflineRecordingEntry(
                    path: entry.name,
                    size: UInt(entry.size),
                    date: date,
                    type: type)
            }
            .catch({ (err) -> Observable<PolarOfflineRecordingEntry> in
                return Observable.error(PolarErrors.deviceError(description: "\(err)"))
            })

        } catch let err {
            return Observable.error(err)
        }
    }
    
    func getSplitOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret?) -> RxSwift.Single<PolarOfflineRecordingData> {
        do {
            let session = try sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }
            guard .sagRfc2FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                return Single.error(PolarErrors.operationNotSupported)
            }

            var operation = Protocol_PbPFtpOperation()
            operation.command =  Protocol_PbPFtpOperation.Command.get
            operation.path = entry.path
            let request = try operation.serializedData()

            BleLogger.trace("Offline record get. Device: $identifier Path: \(entry.path) Secret used: \(secret != nil)")
            return client.sendNotification(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, parameters: nil)
                .andThen(client.request(request))
                .map { data -> OfflineRecordingData<Any> in
                    var pmdSecret: PmdSecret? = nil
                    if let s = secret {
                        pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s)
                    }

                    let type:PmdMeasurementType = PolarDataUtils.mapToPmdClientMeasurementType(from: entry.type)
                    return try OfflineRecordingData<Any>.parseDataFromOfflineFile(fileData: data as Data, type:type, secret: pmdSecret)
                }
                .map { offlineRecData -> PolarOfflineRecordingData in
                    let settings = offlineRecData.recordingSettings?.mapToPolarSettings() ?? PolarSensorSetting()
                    switch(offlineRecData.data) {
                    case is AccData:
                        return PolarOfflineRecordingData.accOfflineRecordingData(
                            (offlineRecData.data as! AccData).mapToPolarData(),
                            startTime: offlineRecData.startTime,
                            settings: settings)
                    case is GyrData:
                        return PolarOfflineRecordingData.gyroOfflineRecordingData(
                            (offlineRecData.data as! GyrData).mapToPolarData(),
                            startTime: offlineRecData.startTime,
                            settings: settings)
                    case is MagData:
                        return PolarOfflineRecordingData.magOfflineRecordingData(
                            (offlineRecData.data as! MagData).mapToPolarData(),
                            startTime: offlineRecData.startTime,
                            settings: settings)
                    case is PpgData:
                        return PolarOfflineRecordingData.ppgOfflineRecordingData(
                            (offlineRecData.data as! PpgData).mapToPolarData(),
                            startTime: offlineRecData.startTime,
                            settings: settings)
                    case is PpiData:
                        return PolarOfflineRecordingData.ppiOfflineRecordingData(
                            (offlineRecData.data as! PpiData).mapToPolarData(),
                            startTime: offlineRecData.startTime)
                    case is OfflineHrData:
                        return PolarOfflineRecordingData.hrOfflineRecordingData(
                            (offlineRecData.data as! OfflineHrData).mapToPolarData(),
                            startTime: offlineRecData.startTime)
                    case is TemperatureData:
                        return PolarOfflineRecordingData.temperatureOfflineRecordingData(
                            (offlineRecData.data as! TemperatureData).mapToPolarData(),
                            startTime: offlineRecData.startTime)
                    default:
                        throw PolarErrors.polarOfflineRecordingError(description: "GetOfflineRecording failed. Data type is not supported.")
                    }
                }
                .flatMap { polarOfflineData -> Single<PolarOfflineRecordingData> in
                    return client.sendNotification(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, parameters: nil)
                        .andThen(Single.just(polarOfflineData))
                }
        } catch let err {
            return Single.error(err)
        }
    }

    func removeOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry) -> Completable {
        BleLogger.trace("Remove offline record. Device: \(identifier) Path: \(entry.path)")
        do {
            let session = try sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }
            guard .sagRfc2FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                return Completable.error(PolarErrors.operationNotSupported)
            }
            
            return removeOfflineFilesRecursively(client, entry.path, deleteIfMatchesRegex: "/\\d{8}/")
            
        } catch let err {
            return Completable.error(err)
        }
    }
    
    private func removeOfflineFilesRecursively(_ client: BlePsFtpClient, _ deletePath: String, deleteIfMatchesRegex: String? = nil) -> Completable {
        do {
            if(deleteIfMatchesRegex != nil) {
                guard deletePath.contains(deleteIfMatchesRegex!) else {
                    return Completable.error(PolarErrors.polarOfflineRecordingError(description:  "Not valid offline recording path to delete \(deletePath)"))
                }
            }
            
            var parentDir: String = ""
            
            if (deletePath.last == "/") {
                if let lastSlashIndex = deletePath.dropLast().lastIndex(of: "/") {
                    parentDir = String(deletePath[...lastSlashIndex])
                }
            } else {
                if let lastSlashIndex = deletePath.lastIndex(of: "/") {
                    parentDir = String(deletePath[...lastSlashIndex])
                }
            }
            
            var operation = Protocol_PbPFtpOperation()
            operation.command = .get
            operation.path = parentDir
            let request = try operation.serializedData()
            
            return client.request(request)
                .flatMapCompletable { content -> Completable in
                    do {
                        let parentDirEntries = try Protocol_PbPFtpDirectory(serializedData: content as Data)
                        let isParentDirValid: Bool
                        if let regex = deleteIfMatchesRegex {
                            isParentDirValid = parentDir.contains(regex)
                        } else {
                            isParentDirValid = true
                        }
                        if parentDirEntries.entries.count <= 1 && isParentDirValid {
                            // It is safe to remove the parent dir
                            return self.removeOfflineFilesRecursively(client, parentDir, deleteIfMatchesRegex: deleteIfMatchesRegex)
                        } else {
                            BleLogger.trace(" Remove offline recording from the path \(deletePath)")
                            var removeOperation = Protocol_PbPFtpOperation()
                            removeOperation.command = .remove
                            removeOperation.path = deletePath
                            let request = try removeOperation.serializedData()
                            return client.request(request).asCompletable()
                        }
                    } catch {
                        return Completable.error(PolarErrors.messageDecodeFailed)
                    }
                }
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func removeOfflineRecords(_ identifier: String, entry: PolarOfflineRecordingEntry) -> Single<Bool> {
        
        BleLogger.trace("Remove offline record. Device: \(identifier) Path: \(entry.path)")
        do {
            let session = try sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }
            guard .sagRfc2FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                return Single.error(PolarErrors.operationNotSupported)
            }
            
            return removeOfflineSubrecordings(identifier, entry: entry)
        } catch let err {
            return Single.error(err)
        }
    }
        
    private func removeOfflineSubrecordings(_ identifier: String, entry: PolarOfflineRecordingEntry) -> Single<Bool> {
        BleLogger.trace("Completely remove offline record with all its subrecordings. Device: \(identifier). Path: \(entry.path).")
        do {
            let session = try sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }
            guard .sagRfc2FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                return Single.error(PolarErrors.operationNotSupported)
            }
            
            return self.getSubRecordings(identifier: identifier, entry: entry).flatMap { subrecords -> Single<Bool> in
                return Single.create { singleEmitter in
                    var disposed = false
                    defer {
                        if !disposed {
                            singleEmitter(.success(true))
                        }
                    }
                    var idx = 0
                    for record in subrecords {
                        idx+=1
                        var deviceType = ""
                        do {
                            do {
                                deviceType = try self.mapDeviceDataTypeToOfflineRecordingFileName(type: entry.type)
                            } catch let err {
                                BleLogger.error("Error in finding device type for subrecord \(record).")
                            }
                            
                            let matches = record.matches(deviceType)
                            if (matches) {
                                let indices = entry.path.findIndices(lookable: "/")
                                let pathPrefix =  String(entry.path[...indices[indices.count-1]])
                                let deletePath = pathPrefix + record
                                self.removeSingleFile(identifier: identifier, filePath: deletePath).subscribe (
                                    onSuccess: {_ in
                                        if idx == subrecords.count {
                                            self.removeOfflineFilesRecursively(client, entry.path, deleteIfMatchesRegex: "/\\d{8}/").subscribe(
                                                onCompleted: {
                                                    return singleEmitter(.success(true))
                                                }
                                            )
                                        }
                                    },
                                    onError: { error in
                                        singleEmitter(.failure(error))
                                    }
                                )
                            }
                        }
                        
                    }
                    return Disposables.create()
                }
            }
        } catch let err {
            return Single.error(err)
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

    func startOfflineRecording(_ identifier: String, feature: PolarDeviceDataType, settings: PolarSensorSetting?, secret: PolarRecordingSecret?) -> RxSwift.Completable {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Completable.error(PolarErrors.serviceNotFound) }
            
            var pmdSecret: PmdSecret? = nil
            if let s = secret {
                pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s)
            }
            
            return client.startMeasurement(
                PolarDataUtils.mapToPmdClientMeasurementType(from:feature), settings: (settings ?? PolarSensorSetting()) .map2PmdSetting(), PmdRecordingType.offline, pmdSecret)
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func stopOfflineRecording(_ identifier: String, feature: PolarDeviceDataType) -> Completable {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Completable.error(PolarErrors.serviceNotFound) }
            BleLogger.trace("Stop offline recording. Feature: \(feature) Device \(identifier)")
            return client.stopMeasurement( PolarDataUtils.mapToPmdClientMeasurementType(from:feature))
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func setOfflineRecordingTrigger(_ identifier: String, trigger: PolarOfflineRecordingTrigger, secret: PolarRecordingSecret?) -> Completable {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Completable.error(PolarErrors.serviceNotFound) }
            
            BleLogger.trace("Setup offline recording trigger. Trigger mode: \(trigger.triggerMode) Trigger features: \(trigger.triggerFeatures.map{ "\($0)" }.joined(separator: ",")) Device: \(identifier) Secret used: \(secret != nil)")
            
            let pmdOfflineTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
            var pmdSecret: PmdSecret? = nil
            if let s = secret {
                pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s)
            }
            
            return client.setOfflineRecordingTrigger(offlineRecordingTrigger: pmdOfflineTrigger, secret: pmdSecret)
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func getOfflineRecordingTriggerSetup(_ identifier: String) -> Single<PolarOfflineRecordingTrigger> {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Single.error(PolarErrors.serviceNotFound) }
            BleLogger.trace("Get offline recording trigger setup. Device: \(identifier)")
            return client.getOfflineRecordingTriggerStatus()
                .map { try PolarDataUtils.mapToPolarOfflineTrigger(from: $0) }
        } catch let err {
            return Single.error(err)
        }
    }
    
    func getAvailableOnlineStreamDataTypes(_ identifier: String) -> Single<Set<PolarDeviceDataType>> {
        do {
            let session = try sessionPmdClientReady(identifier)
            
            // TODO, properly check the situation pmd client is not available but hr client is
            guard let pmdClient = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Single.error(PolarErrors.serviceNotFound) }
            
            let bleHrClient = session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient
            
            return pmdClient.readFeature(true)
                .map { pmdFeature -> Set<PolarDeviceDataType> in
                    var deviceData: Set<PolarDeviceDataType> = Set()
                    
                    if (bleHrClient != nil ) {
                        deviceData.insert(PolarDeviceDataType.hr)
                    }
                    
                    if (pmdFeature.contains(PmdMeasurementType.ecg)) {
                        deviceData.insert(PolarDeviceDataType.ecg)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.acc)) {
                        deviceData.insert(PolarDeviceDataType.acc)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.ppg)) {
                        deviceData.insert(PolarDeviceDataType.ppg)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.ppi)) {
                        deviceData.insert(PolarDeviceDataType.ppi)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.gyro)) {
                        deviceData.insert(PolarDeviceDataType.gyro)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.mgn)) {
                        deviceData.insert(PolarDeviceDataType.magnetometer)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.offline_hr)) {
                        deviceData.insert(PolarDeviceDataType.hr)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.temperature)) {
                        deviceData.insert(PolarDeviceDataType.temperature)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.pressure)) {
                        deviceData.insert(PolarDeviceDataType.pressure)
                    }
                    if (pmdFeature.contains(PmdMeasurementType.skinTemperature)) {
                        deviceData.insert(PolarDeviceDataType.skinTemperature)
                    }
                    return deviceData
                }
        } catch let err {
            return Single.error(err)
        }
    }
    
    func startEcgStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarEcgData> {
        return startStreaming(identifier, type: .ecg, settings: settings) { (client) -> Observable<PolarEcgData> in
            return client.observeEcg()
                .map{
                    $0.mapToPolarData()
                }
        }
    }
    
    func startAccStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarAccData> {
        return startStreaming(identifier, type: .acc, settings: settings) { (client) -> Observable<PolarAccData> in
            return client.observeAcc()
                .map{
                    $0.mapToPolarData()
                }
        }
    }
    
    func startGyroStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarGyroData> {
        return startStreaming(identifier, type: .gyro, settings: settings) { (client) -> Observable<PolarGyroData> in
            return client.observeGyro()
                .map {
                    $0.mapToPolarData()
                }
        }
    }
    
    func startMagnetometerStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarMagnetometerData> {
        return startStreaming(identifier, type: .mgn, settings: settings) { (client) -> Observable<PolarMagnetometerData> in
            return client.observeMagnetometer().map {
                $0.mapToPolarData()
            }
        }
    }

    
    func startPpgStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarPpgData> {
        return startStreaming(identifier, type: .ppg, settings: settings) { (client) -> Observable<PolarPpgData> in
            return client.observePpg()
                .map {
                    $0.mapToPolarData()
                }
        }
    }
    
    func startPpiStreaming(_ identifier: String) -> Observable<PolarPpiData> {
        return startStreaming(identifier, type: .ppi, settings: PolarSensorSetting()) { (client) -> Observable<PolarPpiData> in
            return client.observePpi()
                .map {
                    $0.mapToPolarData()
                }
        }
    }
    
    func startHrStreaming(_ identifier: String) -> Observable<PolarHrData> {
        do {
            let session = try sessionServiceReady(identifier, service: BleHrClient.HR_SERVICE)
            guard let bleHrClient = session.fetchGattClient(BleHrClient.HR_SERVICE) as? BleHrClient else {
                return Observable.error(PolarErrors.serviceNotFound)
            }
            return bleHrClient.observeHrNotifications(true)
                .map {
                    // Online HR streaming does not provide corrected HR. Thus, ppgQuality and correctedHr are set to zero.
                    return [(hr: UInt8($0.hr), 0, 0, rrsMs: $0.rrsMs, rrAvailable: $0.rrPresent, contactStatus: $0.sensorContact, contactStatusSupported: $0.sensorContactSupported)]
                }
        } catch let err {
            return Observable.error(err)
        }
    }

    func startTemperatureStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarTemperatureData> {
        return startStreaming(identifier, type: .temperature, settings: settings) { (client) -> Observable<PolarTemperatureData> in
            return client.observeTemperature()
                .map {
                    $0.mapToPolarData()
                }
        }
    }

    func startPressureStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarPressureData> {
        return startStreaming(identifier, type: .pressure, settings: settings) { (client) -> Observable<PolarPressureData> in
            return client.observePressure()
                .map {
                    $0.mapToPolarData()
                }
        }
    }
    
    func startSkinTemperatureStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarTemperatureData> {
        return startStreaming(identifier, type: .skinTemperature, settings: settings) { (client) -> Observable<PolarTemperatureData> in
            return client.observeSkinTemperature()
                .map {
                    $0.mapToPolarData()
                }
        }
    }

    func fetchExercise(_ identifier: String, entry: PolarExerciseEntry) -> Single<PolarExerciseData> {
        do {
            let session = try sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.operationNotSupported)
            }
            
            let fsType = BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType)
            
            let beforeFetch = (fsType == .h10FileSystem) ?
            client.sendNotification(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, parameters: nil)
                .andThen(client.sendNotification(Protocol_PbPFtpHostToDevNotification.startSync.rawValue, parameters: nil))
            : Completable.empty()
            
            let afterFetch = (fsType == .h10FileSystem) ?
            client.sendNotification(Protocol_PbPFtpHostToDevNotification.stopSync.rawValue, parameters: nil)
                .andThen(client.sendNotification(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, parameters: nil))
            : Completable.empty()
            
            var operation = Protocol_PbPFtpOperation()
            operation.command =  Protocol_PbPFtpOperation.Command.get
            operation.path = entry.path
            let request = try operation.serializedData()
            return beforeFetch
                .andThen(client.request(request))
                .map { (data) -> PolarExerciseData in
                    let samples = try Data_PbExerciseSamples(serializedData: data as Data)
                    var exSamples = [UInt32]()
                    if samples.hasRrSamples && samples.rrSamples.rrIntervals.count != 0 {
                        for rrInterval in samples.rrSamples.rrIntervals {
                            exSamples.append(rrInterval)
                        }
                    } else {
                        for hrSample in samples.heartRateSamples {
                            exSamples.append(hrSample)
                        }
                    }
                    return (samples.recordingInterval.seconds, samples: exSamples)
                }
                .catch(
                    { (err) -> Single<PolarExerciseData> in
                        return Single.error(PolarErrors.deviceError(description: "\(err)"))
                    }
                )
                .do(
                    onDispose: {
                        _ = afterFetch
                            .subscribe()
                    }
                )
        } catch let err {
            return Single.error(err)
        }
    }
    
    func fetchStoredExerciseList(_ identifier: String) -> Observable<PolarExerciseEntry> {
        do {
            let session = try sessionFtpClientReady(identifier)
            let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
            let fsType = BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType)
            if fsType == .sagRfc2FileSystem {
                return fetchRecursive("/U/0/", client: client, condition: { (entry) -> Bool in
                    return entry.matches("^([0-9]{8})(\\/)") ||
                    entry.matches("^([0-9]{6})(\\/)") ||
                    entry == "E/" ||
                    entry == "SAMPLES.BPB" ||
                    entry == "00/"
                })
                .map { (entry) -> (path: String, date: Date, entryId: String) in
                    let components = entry.name.split(separator: "/")
                    let dateFormatter = DateFormatter()
                    dateFormatter.locale = Locale(identifier: "en_US_POSIX")
                    dateFormatter.dateFormat = "yyyyMMddHHmmss"
                    if let date = dateFormatter.date(from: String(components[2] + components[4])) {
                        return (entry.name, date: date, entryId: String(components[2] + components[4]))
                    }
                    throw PolarErrors.dateTimeFormatFailed()
                }
                .catch({ (err) -> Observable<PolarExerciseEntry> in
                    return Observable.error(PolarErrors.deviceError(description: "\(err)"))
                })
            } else if fsType == .h10FileSystem {
                return fetchRecursive("/", client: client, condition: { (entry) -> Bool in
                    return entry.hasSuffix("/") || entry == "SAMPLES.BPB"
                })
                .map { (entry) -> (path: String, date: Date, entryId: String) in
                    let components = entry.name.split(separator: "/")
                    return (entry.name, date: Date(), entryId: String(components[0]))
                }
                .catch( { (err) -> Observable<PolarExerciseEntry> in
                    return Observable.error(PolarErrors.deviceError(description: "\(err)"))
                })
            }
            throw PolarErrors.operationNotSupported
        } catch let err {
            return Observable.error(err)
        }
    }

    func setLedConfig(_ identifier: String, ledConfig: LedConfig) -> Completable {
        return Completable.create { completable in
            do {
                let session = try self.sessionFtpClientReady(identifier)
                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    completable(.error(PolarErrors.serviceNotFound))
                    return Disposables.create()
                }

                var builder = Protocol_PbPFtpOperation()
                builder.command = Protocol_PbPFtpOperation.Command.put
                builder.path = LedConfig.LED_CONFIG_FILENAME
                let proto = try builder.serializedData()
                let sdkModeLedByte: UInt8 = ledConfig.sdkModeLedEnabled ? LedConfig.LED_ANIMATION_ENABLE_BYTE : LedConfig.LED_ANIMATION_DISABLE_BYTE
                let ppiModeLedByte: UInt8 = ledConfig.ppiModeLedEnabled ? LedConfig.LED_ANIMATION_ENABLE_BYTE : LedConfig.LED_ANIMATION_DISABLE_BYTE
                let data = Data([sdkModeLedByte, ppiModeLedByte])
                let inputStream = InputStream(data: data)

                client.write(proto as NSData, data: inputStream)
                    .subscribe(
                        onError: { error in
                            completable(.error(error))
                        }, onCompleted: {
                            completable(.completed)
                        })

                completable(.completed)
            } catch let err {
                completable(.error(err))
            }

            return Disposables.create()
        }
    }

    func doFactoryReset(_ identifier: String, preservePairingInformation: Bool) -> Completable {
        do {
            let session = try sessionFtpClientReady(identifier)

            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }

            var builder = Protocol_PbPFtpFactoryResetParams()
            builder.sleep = false
            builder.otaFwupdate = preservePairingInformation
            BleLogger.trace("Send do factory reset to device: \(identifier)")
            return try client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: builder.serializedData() as NSData)
        } catch let err {
            return Completable.error(err)
        }
    }

    func doRestart(_ identifier: String, preservePairingInformation: Bool) -> Completable {
            do {
                let session = try sessionFtpClientReady(identifier)

                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    return Completable.error(PolarErrors.serviceNotFound)
                }

                var builder = Protocol_PbPFtpFactoryResetParams()
                builder.sleep = false
                builder.doFactoryDefaults = false
                builder.otaFwupdate = preservePairingInformation
                BleLogger.trace("Send do restart to device: \(identifier)")
                return try client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: builder.serializedData() as NSData)
                } catch let err {
                    return Completable.error(err)
                }
        }
    
    func getSDLogConfiguration(_ identifier: String) -> RxSwift.Single<SDLogConfig> {
          do {
              let session = try sessionFtpClientReady(identifier)
              guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                  return Single.error(PolarErrors.serviceNotFound)
              }
              guard .sagRfc2FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                  return Single.error(PolarErrors.operationNotSupported)
              }

              var operation = Protocol_PbPFtpOperation()
              operation.command =  Protocol_PbPFtpOperation.Command.get
              operation.path = SERVICE_DATALOG_CONFIG_FILEPATH
              let request = try operation.serializedData()

              BleLogger.trace("Sensor datalog get. Device: \(identifier) Path: \(operation.path)")
              return client.sendNotification(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, parameters: nil)
                  .andThen(client.request(request))
                  .map { data -> SDLogConfig in
                      let sensorDataLog = try Data_PbSensorDataLog(serializedData: data as Data)
                      return SDLogConfig.fromProto(proto: sensorDataLog)
                  }
                  .flatMap { logConfig -> Single<SDLogConfig> in
                      return client.sendNotification(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, parameters: nil)
                          .andThen(Single.just(logConfig))
                  }
          } catch let err {
              return Single.error(err)
          }
    }

    func setSDLogConfiguration(_ identifier: String, logConfiguration: SDLogConfig) -> Completable {
        return Completable.create { completable in
            do {
                let session = try self.sessionFtpClientReady(identifier)
                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    completable(.error(PolarErrors.serviceNotFound))
                    return Disposables.create()
                }

                let sdLogConfigProto = try SDLogConfig.toProto(sdLogConfig: logConfiguration).serializedData()

                var operation = Protocol_PbPFtpOperation()
                operation.command = Protocol_PbPFtpOperation.Command.put
                operation.path = SERVICE_DATALOG_CONFIG_FILEPATH
                let proto = try operation.serializedData()

                let data = Data(sdLogConfigProto)
                let inputStream = InputStream(data: data)

                BleLogger.trace("Sensor datalog set. Device: \(identifier) Path: \(operation.path)")

                client.write(proto as NSData, data: inputStream)
                    .subscribe(
                        onError: { error in
                          completable(.error(error))
                      }, onCompleted: {
                          completable(.completed)
                      })
                completable(.completed)
            } catch let err {
                completable(.error(err))
            }

            return Disposables.create()
        }
    }

    func doFirstTimeUse(_ identifier: String, ftuConfig: PolarFirstTimeUseConfig) -> Completable {
        return Completable.create { completable in
            do {
                let session = try self.sessionFtpClientReady(identifier)
                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    completable(.error(PolarErrors.deviceError(description: "Failed to fetch GATT client.")))
                    return Disposables.create()
                }

                let ftuConfigProto = try ftuConfig.toProto()?.serializedData() ?? {
                    throw PolarErrors.deviceError(description: "Serialization of FTU Config failed.")
                }()

                var ftuOperation = Protocol_PbPFtpOperation()
                ftuOperation.command = Protocol_PbPFtpOperation.Command.put
                ftuOperation.path = PolarFirstTimeUseConfig.FTU_CONFIG_FILEPATH
                let ftuProto = try ftuOperation.serializedData()

                let ftuData = Data(ftuConfigProto)
                let ftuInputStream = InputStream(data: ftuData)

                client.write(ftuProto as NSData, data: ftuInputStream)
                    .subscribe(
                        onError: { error in
                            BleLogger.error("Failed to write FTU configuration to device: \(identifier) - \(error.localizedDescription)")
                            completable(.error(error))
                        },
                        onCompleted: {
                            do {
                                var userIdOperation = Protocol_PbPFtpOperation()
                                userIdOperation.command = Protocol_PbPFtpOperation.Command.put
                                userIdOperation.path = UserIdentifierType.USER_IDENTIFIER_FILENAME

                                let userIdentifier = UserIdentifierType.create()
                                let userIdProto = try userIdentifier.toProto().serializedData()

                                let userIdData = Data(userIdProto)
                                let userIdInputStream = InputStream(data: userIdData)

                                client.write(try userIdOperation.serializedData() as NSData, data: userIdInputStream)
                                    .subscribe(
                                        onError: { error in
                                            BleLogger.error("Failed to write User ID to device: \(identifier) - \(error.localizedDescription)")
                                            completable(.error(error))
                                        },
                                        onCompleted: {
                                            let setTimeCompletable = Completable.create { setTimeCompletable in
                                                do {
                                                    let isoFormatter = ISO8601DateFormatter()
                                                    isoFormatter.timeZone = TimeZone.current

                                                    guard let date = isoFormatter.date(from: ftuConfig.deviceTime) else {
                                                        setTimeCompletable(.error(PolarErrors.deviceError(description: "Invalid deviceTime format: \(ftuConfig.deviceTime)")))
                                                        return Disposables.create()
                                                    }

                                                    self.setLocalTime(identifier, time: date, zone: TimeZone.current)
                                                        .subscribe(
                                                            onCompleted: {
                                                                setTimeCompletable(.completed)
                                                            },
                                                            onError: { error in
                                                                setTimeCompletable(.error(error))
                                                            }
                                                        )
                                                } catch let error {
                                                    BleLogger.error("Error setting time for device: \(identifier) - \(error.localizedDescription)")
                                                    setTimeCompletable(.error(error))
                                                }
                                                return Disposables.create()
                                            }

                                            setTimeCompletable
                                                .subscribe(
                                                    onCompleted: {
                                                        self.sendTerminateAndStopSyncNotifications(client: client)
                                                        completable(.completed)
                                                    },
                                                    onError: { error in
                                                        completable(.error(error))
                                                    }
                                                )
                                        }
                                    )
                            } catch let error {
                                BleLogger.error("Error processing User ID for device: \(identifier) - \(error.localizedDescription)")
                                completable(.error(error))
                            }
                        }
                    )

            } catch let error {
                BleLogger.error("Error processing FTU configuration for device: \(identifier) - \(error.localizedDescription)")
                completable(.error(error))
            }

            return Disposables.create()
        }
    }
    
    func isFtuDone(_ identifier: String) -> Single<Bool> {
   
        do {
            let session = try sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }
            guard .sagRfc2FileSystem == BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType) else {
                return Single.error(PolarErrors.operationNotSupported)
            }
            
            var operation = Protocol_PbPFtpOperation()
            operation.command =  Protocol_PbPFtpOperation.Command.get
            operation.path = UserIdentifierType.USER_IDENTIFIER_FILENAME
            let request = try operation.serializedData()

            self.logMessage("Check if FTU has been done to device \(identifier)")
            return client.sendNotification(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, parameters: nil)
                .andThen(client.request(request))
                .map { data -> Bool in
                    return try Data_PbUserIdentifier(serializedData: data as Data).hasMasterIdentifier
                }
        } catch let err {
            return Single.error(err)
        }
    }
    
    func checkFirmwareUpdate(_ identifier: String) -> Observable<CheckFirmwareUpdateStatus> {
        let fwApi = FirmwareUpdateApi()
        
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Observable.just(CheckFirmwareUpdateStatus.checkFwUpdateFailed(details: "No BlePsFtpClient available"))
            }
            self.sendInitializationAndStartSyncNotifications(client: client)
            
            guard let deviceInfo = PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: client, deviceId: identifier) else {
                return Observable.just(CheckFirmwareUpdateStatus.checkFwUpdateFailed(details: "Failed to read device firmware info"))
            }
            
            let firmwareUpdateRequest: FirmwareUpdateRequest
            do {
                firmwareUpdateRequest = try FirmwareUpdateRequest(
                    clientId: "polar-sensor-data-collector-ios",
                    uuid: PolarDeviceUuid.fromDeviceId(identifier),
                    firmwareVersion: deviceInfo.deviceFwVersion,
                    hardwareCode: deviceInfo.deviceHardwareCode
                )
            } catch {
                return Observable.just(CheckFirmwareUpdateStatus.checkFwUpdateFailed(details: "Failed to create FirmwareUpdateRequest: \(error.localizedDescription)"))
            }
            
            return Observable.create { observer in
                fwApi.checkFirmwareUpdate(firmwareUpdateRequest: firmwareUpdateRequest) { result in
                    switch result {
                    case .success(let apiResponse):
                        guard let statusCode = apiResponse.statusCode else {
                            BleLogger.error("No status code received")
                            observer.onNext(CheckFirmwareUpdateStatus.checkFwUpdateFailed(details: "No status code received"))
                            observer.onCompleted()
                            return
                        }
                        
                        if statusCode == 204 {
                            BleLogger.trace("Firmware update not available, status code 204")
                            observer.onNext(CheckFirmwareUpdateStatus.checkFwUpdateNotAvailable(details: "Firmware update not available"))
                            observer.onCompleted()
                            return
                        }
                        
                        if statusCode == 400 {
                            BleLogger.error("Bad request, status code 400")
                            observer.onNext(CheckFirmwareUpdateStatus.checkFwUpdateFailed(details: "Bad request to firmware API"))
                            observer.onCompleted()
                            return
                        }
                        
                        guard statusCode == 200 else {
                            BleLogger.error("Unexpected status code: \(statusCode)")
                            observer.onNext(CheckFirmwareUpdateStatus.checkFwUpdateFailed(details: "Unexpected status code: \(statusCode)"))
                            observer.onCompleted()
                            return
                        }
                        
                        let deviceFwVersion = deviceInfo.deviceFwVersion
                        if !PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(currentVersion: deviceFwVersion, availableVersion: apiResponse.version!) {
                            BleLogger.trace("No firmware update available, device firmware version \(deviceFwVersion)")
                            observer.onNext(CheckFirmwareUpdateStatus.checkFwUpdateNotAvailable(details: "No firmware update available, device firmware version \(deviceFwVersion)"))
                            observer.onCompleted()
                            return
                        }
                        
                        BleLogger.trace("Firmware update available, latest firmware version: \(apiResponse.version!), device firmware version \(deviceFwVersion)")
                        observer.onNext(CheckFirmwareUpdateStatus.checkFwUpdateAvailable(version: apiResponse.version!))
                        observer.onCompleted()
                        
                    case .failure(let error):
                        BleLogger.error("Error checking firmware update: \(error)")
                        observer.onNext(CheckFirmwareUpdateStatus.checkFwUpdateFailed(details: "Error checking firmware update: \(error.localizedDescription)"))
                        observer.onCompleted()
                    }
                }
                return Disposables.create()
            }
        } catch {
            BleLogger.error("Error during firmware update check: \(error)")
            return Observable.just(CheckFirmwareUpdateStatus.checkFwUpdateFailed(details: "Error: \(error.localizedDescription)"))
        }
    }

    func updateFirmware(_ identifier: String) -> Observable<FirmwareUpdateStatus> {
        let fwApi = FirmwareUpdateApi()

        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Observable.just(FirmwareUpdateStatus.fwUpdateFailed(details: "No BlePsFtpClient available"))
            }
            self.sendInitializationAndStartSyncNotifications(client: client)

            guard let deviceInfo = PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: client, deviceId: identifier) else {
                return Observable.just(FirmwareUpdateStatus.fwUpdateFailed(details: "Failed to read device firmware info"))
            }

            let firmwareUpdateRequest: FirmwareUpdateRequest
            do {
                firmwareUpdateRequest = try FirmwareUpdateRequest(
                    clientId: "polar-sensor-data-collector-ios",
                    uuid: PolarDeviceUuid.fromDeviceId(identifier),
                    firmwareVersion: deviceInfo.deviceFwVersion,
                    hardwareCode: deviceInfo.deviceHardwareCode
                )
            } catch {
                return Observable.just(FirmwareUpdateStatus.fwUpdateFailed(details: "Failed to create FirmwareUpdateRequest: \(error.localizedDescription)"))
            }

            return Observable.create { observer in
                fwApi.checkFirmwareUpdate(firmwareUpdateRequest: firmwareUpdateRequest) { result in
                    switch result {
                    case .success(let apiResponse):
                        guard let statusCode = apiResponse.statusCode else {
                            BleLogger.error("No status code received")
                            observer.onNext(FirmwareUpdateStatus.fwUpdateFailed(details: "No status code received"))
                            observer.onCompleted()
                            return
                        }

                        if statusCode == 204 {
                            BleLogger.trace("Firmware update not available, status code 204")
                            observer.onNext(FirmwareUpdateStatus.fwUpdateNotAvailable(details: "Firmware update not available"))
                            observer.onCompleted()
                            return
                        }

                        if statusCode == 400 {
                            BleLogger.error("Bad request, status code 400")
                            observer.onNext(FirmwareUpdateStatus.fwUpdateFailed(details: "Bad request to firmware API"))
                            observer.onCompleted()
                            return
                        }

                        guard statusCode == 200 else {
                            BleLogger.error("Unexpected status code: \(statusCode)")
                            observer.onNext(FirmwareUpdateStatus.fwUpdateFailed(details: "Unexpected status code: \(statusCode)"))
                            observer.onCompleted()
                            return
                        }

                        let deviceFwVersion = deviceInfo.deviceFwVersion
                        if !PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(currentVersion: deviceFwVersion, availableVersion: apiResponse.version!) {
                            BleLogger.trace("No firmware update available, device firmware version \(deviceFwVersion)")
                            observer.onNext(FirmwareUpdateStatus.fwUpdateNotAvailable(details: "No firmware update available, device firmware version \(deviceFwVersion)"))
                            observer.onCompleted()
                            return
                        }

                        BleLogger.trace("Firmware update available, latest firmware version: \(apiResponse.version!), device firmware version \(deviceFwVersion)")

                        let factoryResetMaxWaitTimeSeconds: TimeInterval = 6 * 60
                        let rebootMaxWaitTimeSeconds: TimeInterval = 3 * 60

                        var backupContent: [PolarBackupManager.BackupFileData]?
                        let backupManager = PolarBackupManager(client: client)

                        observer.onNext(FirmwareUpdateStatus.fetchingFwUpdatePackage(details: "Fetching firmware update package..."))

                        backupManager.backupDevice()
                            .asObservable()
                            .flatMap { content -> Observable<FirmwareUpdateStatus> in
                                backupContent = content
                                BleLogger.trace("Device backup completed with \(content.count) files")
                                observer.onNext(FirmwareUpdateStatus.fetchingFwUpdatePackage(details: "Backup completed, downloading firmware..."))

                                return fwApi.getFirmwareUpdatePackage(url: apiResponse.fileUrl!)
                                    .flatMap { firmwarePackage -> Observable<FirmwareUpdateStatus> in
                                        guard let firmwarePackage = firmwarePackage else {
                                            BleLogger.error("Firmware package download fetch failed")
                                            observer.onNext(FirmwareUpdateStatus.fwUpdateFailed(details: "Firmware package fetch failed"))
                                            observer.onCompleted()
                                            return Observable.empty()
                                        }

                                        let contentLength = firmwarePackage.count
                                        BleLogger.trace("Firmware package downloaded, zipped size: \(contentLength) bytes")
                                        observer.onNext(FirmwareUpdateStatus.writingFwUpdatePackage(details: "Writing firmware package..."))

                                        guard let unzippedFirmwarePackage = PolarFirmwareUpdateUtils.unzipFirmwarePackage(zippedData: firmwarePackage) else {
                                            BleLogger.error("Failed to unzip firmware package")
                                            observer.onNext(FirmwareUpdateStatus.fwUpdateFailed(details: "Failed to unzip firmware package"))
                                            observer.onCompleted()
                                            return Observable.empty()
                                        }

                                        let unzippedContentLength = unzippedFirmwarePackage.reduce(0) { $0 + $1.value.count }
                                        BleLogger.trace("Firmware package unzipped, total size: \(unzippedContentLength) bytes")

                                        let sortedFirmwarePackage = unzippedFirmwarePackage.sorted { (file1, file2) -> Bool in
                                            return PolarFirmwareUpdateUtils.FwFileComparator.compare(file1.key, file2.key) == .orderedAscending
                                        }

                                        return Observable.from(sortedFirmwarePackage)
                                            .concatMap { fileEntry -> Observable<FirmwareUpdateStatus> in
                                                let fileName = fileEntry.key
                                                let firmwareBytes = fileEntry.value
                                                let filePath = "/\(fileName)"
                                                
                                                // Polar H10 FW package has this file
                                                if fileName.lowercased() == "readme.txt" {
                                                    BleLogger.trace("Skipping file \(fileName)")
                                                    return Observable.just(FirmwareUpdateStatus.writingFwUpdatePackage(details: "Skipping file \(fileName)"))
                                                }

                                                return self.writeFirmwareToDevice(deviceId: identifier, firmwareFilePath: filePath, firmwareBytes: firmwareBytes)
                                                    .map { bytesWritten -> FirmwareUpdateStatus in
                                                        BleLogger.trace("Writing firmware update file \(fileName), bytes written: \(bytesWritten)/\(firmwareBytes.count) bytes")
                                                        return FirmwareUpdateStatus.writingFwUpdatePackage(details: "Writing firmware update file \(fileName), bytes written: \(bytesWritten)/\(firmwareBytes.count) bytes")
                                                    }
                                            }
                                            .concat(Observable.just(FirmwareUpdateStatus.finalizingFwUpdate(details: "Finalizing firmware update...")))
                                    }
                            }
                            .flatMap { status -> Observable<FirmwareUpdateStatus> in
                                if case FirmwareUpdateStatus.finalizingFwUpdate = status {
                                    return Observable.create { observer in
                                        BleLogger.trace("Firmware update is in finalizing stage")

                                        BleLogger.trace("Device rebooting")
                                        self.waitDeviceSessionToOpen(deviceId: identifier, timeoutSeconds: Int(factoryResetMaxWaitTimeSeconds), waitForDeviceDownSeconds: 10)
                                            .do(onSubscribe: {
                                                BleLogger.trace("Waiting for device session to open after reboot with timeout: \(rebootMaxWaitTimeSeconds) seconds")
                                            })
                                            .andThen(Single.just(status))
                                            .flatMap { _ in
                                                return Single.create { singleObserver in
                                                    do {
                                                        let session = try self.sessionFtpClientReady(identifier)
                                                        singleObserver(.success(session))
                                                    } catch {
                                                        BleLogger.trace("Error: \(error). Waiting for FTP client readiness.")
                                                        singleObserver(.failure(error))
                                                    }
                                                    return Disposables.create()
                                                }
                                                .flatMap { _ in
                                                    BleLogger.trace("Performing factory reset while preserving pairing information")
                                                    return self.doFactoryReset(identifier, preservePairingInformation: true)
                                                        .do(onSubscribe: {
                                                            BleLogger.trace("Factory reset initiated")
                                                        })
                                                        .andThen(Single.just(()))
                                                }
                                            }
                                            .flatMap { _ in
                                                BleLogger.trace("Waiting for device session to open after factory reset with timeout: \(factoryResetMaxWaitTimeSeconds) seconds")
                                                return self.waitDeviceSessionToOpen(deviceId: identifier, timeoutSeconds: Int(factoryResetMaxWaitTimeSeconds), waitForDeviceDownSeconds: 10)
                                                    .do(onSubscribe: {
                                                        BleLogger.trace("Waiting for device session to open post factory reset")
                                                    })
                                                    .andThen(Single.just(()))
                                            }
                                            .flatMap { _ in
                                                if let backupContent = backupContent {
                                                    BleLogger.trace("Restoring backup after firmware update")
                                                    return backupManager.restoreBackup(backupFiles: backupContent)
                                                        .do(onSubscribe: {
                                                            BleLogger.trace("Backup restoration initiated")
                                                        })
                                                        .andThen(Single.just(FirmwareUpdateStatus.fwUpdateCompletedSuccessfully(details: apiResponse.version!)))
                                                } else {
                                                    BleLogger.trace("No backup available to restore, proceeding with completion")
                                                    return Single.just(FirmwareUpdateStatus.fwUpdateCompletedSuccessfully(details: apiResponse.version!))
                                                }
                                            }
                                            .asObservable()
                                            .subscribe(
                                                onNext: { status in
                                                    BleLogger.trace("Emitting next status: \(status)")
                                                    observer.onNext(status)
                                                },
                                                onError: { error in
                                                    BleLogger.trace("Error during device reboot or factory reset: \(error)")
                                                    observer.onError(error)
                                                },
                                                onCompleted: {
                                                    BleLogger.trace("Device reboot and factory reset completed successfully")
                                                    observer.onCompleted()
                                                }
                                            )
                                        return Disposables.create()
                                    }
                                } else {
                                    return Observable.just(status)
                                }
                            }
                            .subscribe(
                                onNext: { status in
                                    observer.onNext(status)
                                },
                                onError: { error in
                                    BleLogger.error("Error during firmware update: \(error)")
                                    observer.onNext(FirmwareUpdateStatus.fwUpdateFailed(details: "Error during firmware update: \(error.localizedDescription)"))
                                    observer.onError(error)
                                },
                                onCompleted: {
                                    _ = self.setLocalTime(identifier, time: Date(), zone: TimeZone.current)
                                        .subscribe(
                                            onCompleted: {
                                               BleLogger.trace("Local time set successfully")
                                            },
                                            onError: { error in
                                               BleLogger.error("Error setting local time: \(error)")
                                            },
                                            onDisposed: {
                                               self.sendTerminateAndStopSyncNotifications(client: client)
                                               observer.onCompleted()
                                           }
                                       )
                                }
                            )
                    case .failure(let error):
                        BleLogger.error("Error checking firmware update: \(error)")
                        observer.onNext(FirmwareUpdateStatus.fwUpdateFailed(details: "Error checking firmware update: \(error.localizedDescription)"))
                        observer.onCompleted()
                    }
                }
                return Disposables.create()
            }
        } catch {
            BleLogger.error("Error during firmware update: \(error)")
            return Observable.just(FirmwareUpdateStatus.fwUpdateFailed(details: "Error: \(error.localizedDescription)"))
        }
    }

    func getSteps(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarStepsData]> {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            var stepsDataList = [(Date, Int)]()

            let calendar = Calendar.current

            var datesList = [Date]()
            var currentDate = fromDate

            while currentDate <= toDate {
                datesList.append(currentDate)
                if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) {
                    currentDate = nextDate
                } else {
                    break
                }
            }

            return Observable.from(datesList)
                .flatMap { date -> Single<(Date, Int)> in
                    return PolarActivityUtils.readStepsFromDayDirectory(client: client, date: date)
                        .map { steps -> (Date, Int) in
                            return (date, steps)
                        }
                }
                .toArray()
                .map { pairs -> [PolarStepsData] in
                    pairs.forEach { pair in
                        stepsDataList.append(pair)
                    }
                    return stepsDataList.map { PolarStepsData(date: $0.0, steps: $0.1) }
                }
        } catch {
            return Single.error(error)
        }
    }

    func getDistance(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarDistanceData]> {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            var distanceDataList = [(Date, Float)]()

            let calendar = Calendar.current

            var datesList = [Date]()
            var currentDate = fromDate

            while currentDate <= toDate {
                datesList.append(currentDate)
                if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) {
                    currentDate = nextDate
                } else {
                    break
                }
            }

            return Observable.from(datesList)
                .flatMap { date -> Single<(Date, Float)> in
                    return PolarActivityUtils.readDistanceFromDayDirectory(client: client, date: date)
                        .map { distance -> (Date, Float) in
                            return (date, distance)
                        }
                }
                .toArray()
                .map { pairs -> [PolarDistanceData] in
                    pairs.forEach { pair in
                        distanceDataList.append(pair)
                    }
                    return distanceDataList.map { PolarDistanceData(date: $0.0, distanceMeters: $0.1) }
                }
        } catch {
            return Single.error(error)
        }
    }

    func get247HrSamples(identifier: String, fromDate: Date, toDate: Date) -> Single<[Polar247HrSamplesData]> {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            return PolarAutomaticSamplesUtils.read247HrSamples(client: client, fromDate: fromDate, toDate: toDate)
        } catch {
            return Single.error(error)
        }
    }
    
    func getNightlyRecharge(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarNightlyRechargeData]> {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            var nightlyRechargeDataList = [PolarNightlyRechargeData]()

            let calendar = Calendar.current
            var currentDate = fromDate

            var datesList = [Date]()

            while currentDate <= toDate {
                datesList.append(currentDate)
                currentDate = calendar.date(byAdding: .day, value: 1, to: currentDate)!
            }

            return Observable.from(datesList)
                .flatMap { date in
                    PolarNightlyRechargeUtils.readNightlyRechargeData(client: client, date: date)
                        .asObservable()
                        .do(onNext: { nightlyRechargeData in
                            nightlyRechargeDataList.append(nightlyRechargeData)
                        })
                }
                .toArray()
                .flatMap { _ in
                    Single.just(nightlyRechargeDataList)
                }
        } catch {
            return Single.error(error)
        }
    }
    
    func getCalories(identifier: String, fromDate: Date, toDate: Date, caloriesType: CaloriesType) -> RxSwift.Single<[PolarCaloriesData]> {
       do {
           let session = try self.sessionFtpClientReady(identifier)
           guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
               return Single.error(PolarErrors.serviceNotFound)
           }

           let calendar = Calendar.current
           var datesList = [Date]()
           var currentDate = fromDate

           while currentDate <= toDate {
               datesList.append(currentDate)
               if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) {
                   currentDate = nextDate
               } else {
                   break
               }
           }

           return Observable.from(datesList)
               .flatMap { date -> Single<(Date, Int)> in
                   return PolarActivityUtils.readCaloriesFromDayDirectory(client: client, date: date, caloriesType: caloriesType)
                       .map { calories -> (Date, Int) in
                           return (date, calories)
                       }
               }
               .toArray()
               .map { pairs -> [PolarCaloriesData] in
                   return pairs.map { (date, calories) in
                       PolarCaloriesData(date: date, calories: calories)
                   }
               }
       } catch {
           return Single.error(error)
       }
   }

    func getSkinTemperature(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarSkinTemperatureData.PolarSkinTemperatureResult]> {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            var skinTemperatureDataList = [PolarSkinTemperatureData.PolarSkinTemperatureResult]()

            let calendar = Calendar.current
            var currentDate = fromDate

            var datesList = [Date]()

            while currentDate <= toDate {
                datesList.append(currentDate)
                currentDate = calendar.date(byAdding: .day, value: 1, to: currentDate)!
            }

            return Observable.from(datesList)
                .flatMap { date in
                    PolarSkinTemperatureUtils.readSkinTemperatureData(client: client, date: date)
                        .asObservable()
                        .do(onNext: { skinTemp in
                            skinTemperatureDataList.append(skinTemp)
                        })
                }
                .toArray()
                .flatMap { _ in
                    Single.just(skinTemperatureDataList)
                }
        } catch {
            return Single.error(error)
        }
    }

    @available(*, deprecated, message: "Use setWarehouseSleep(_ identifier: String) instead")
    func setWarehouseSleep(_ identifier: String, enableWarehouseSleep: Bool?) -> Completable {
        do {
            let session = try sessionFtpClientReady(identifier)

            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }

            var builder = Protocol_PbPFtpFactoryResetParams()
            builder.sleep = ((enableWarehouseSleep != nil) ? enableWarehouseSleep : false)!
            builder.otaFwupdate = true
            BleLogger.trace("Setting warehouse sleep to: \(String(describing: enableWarehouseSleep)), and do a required factory reset to the device: \(identifier).")
            return try client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: builder.serializedData() as NSData)
            } catch let err {
                return Completable.error(err)
            }
    }

    func setWarehouseSleep(_ identifier: String) -> Completable {
        do {
            let session = try sessionFtpClientReady(identifier)

            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Completable.error(PolarErrors.serviceNotFound)
            }

            var builder = Protocol_PbPFtpFactoryResetParams()
            builder.sleep = true
            builder.otaFwupdate = true
            BleLogger.trace("Setting warehouse sleep to true, and do a required factory reset to the device: \(identifier).")
            return try client.sendNotification(Protocol_PbPFtpHostToDevNotification.reset.rawValue, parameters: builder.serializedData() as NSData)
            } catch let err {
                return Completable.error(err)
            }
    }

    func getActiveTime(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarActiveTimeData]> {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            let calendar = Calendar.current
            var datesList = [Date]()
            var currentDate = fromDate

            while currentDate <= toDate {
                datesList.append(currentDate)
                if let nextDate = calendar.date(byAdding: .day, value: 1, to: currentDate) {
                    currentDate = nextDate
                } else {
                    break
                }
            }

            return Observable.from(datesList)
                .flatMap { date -> Single<(Date, PolarActiveTimeData)> in
                    return PolarActivityUtils.readActiveTimeFromDayDirectory(client: client, date: date)
                        .map { activeTime -> (Date, PolarActiveTimeData) in
                            return (date, activeTime)
                        }
                }
                .toArray()
                .map { pairs -> [PolarActiveTimeData] in
                    return pairs.map { (date, activeTimeData) in
                        PolarActiveTimeData(
                            date: date,
                            timeNonWear: activeTimeData.timeNonWear,
                            timeSleep: activeTimeData.timeSleep,
                            timeSedentary: activeTimeData.timeSedentary,
                            timeLightActivity: activeTimeData.timeLightActivity,
                            timeContinuousModerateActivity: activeTimeData.timeContinuousModerateActivity,
                            timeIntermittentModerateActivity: activeTimeData.timeIntermittentModerateActivity,
                            timeContinuousVigorousActivity: activeTimeData.timeContinuousVigorousActivity,
                            timeIntermittentVigorousActivity: activeTimeData.timeIntermittentVigorousActivity
                        )
                    }
                }
        } catch {
            return Single.error(error)
        }
    }
    
    func setPolarUserDeviceSettings(_ identifier: String, polarUserDeviceSettings: PolarUserDeviceSettings?) -> Completable {
        return Completable.create { completable in
            do {
                let session = try self.sessionFtpClientReady(identifier)
                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                    completable(.error(PolarErrors.serviceNotFound))
                    return Disposables.create()
                }
                let userDeviceSettingsData = try PolarUserDeviceSettings.toProto(deviceUserLocation: polarUserDeviceSettings!.deviceLocation).serializedData()

                var operation = Protocol_PbPFtpOperation()
                operation.command = Protocol_PbPFtpOperation.Command.put
                operation.path = DEVICE_SETTINGS_FILE_PATH
                let proto = try operation.serializedData()

                let data = Data(userDeviceSettingsData)
                let inputStream = InputStream(data: data)

                BleLogger.trace("Polar user device settings set. Device: \(identifier) Path: \(operation.path)")

                client.write(proto as NSData, data: inputStream)
                    .subscribe(
                        onError: { error in
                          completable(.error(error))
                      }, onCompleted: {
                          completable(.completed)
                      })
                completable(.completed)
            } catch let err {
                
                completable(.error(err))
            }

            return Disposables.create()
        }
    }

    func getPolarUserDeviceSettings(identifier: String) -> Single<PolarUserDeviceSettings.PolarUserDeviceSettingsResult> {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }

            return PolarUserDeviceSettingsUtils.getUserDeviceSettings(client: client)
                .map { settings -> PolarUserDeviceSettings.PolarUserDeviceSettingsResult in
                    return settings
                }
        } catch {
            return Single.error(error)
        }
    }

    func deleteStoredDeviceData(_ identifier: String, dataType: PolarStoredDataType.StoredDataType, until: Date?) -> Completable {
        
        var dataDeletionStats = DataDeletionStats()
        let entryPattern = dataType.rawValue
        
        switch dataType {
        case .AUTO_SAMPLE:
            return deleteListedFilesByType(identifier: identifier, dataDeletionStats: dataDeletionStats, entryPath: "/U/0/AUTOS", until: until!, dataType, condition:{ (entry) -> Bool in
                entry.contains("^(\\d{8})(/)") ||
                entry == "\(entryPattern)/" ||
                entry.contains(".BPB")
            })
        case .SDLOGS:
            return deleteListedFilesByType(identifier: identifier, dataDeletionStats: dataDeletionStats, entryPath: "/SDLOGS", until: until!, dataType, condition:{ (entry) -> Bool in
                entry.contains("^(\\d{8})(/)") ||
                entry == "\(entryPattern)/" ||
                entry.contains(".SLG") ||
                entry.contains(".TXT")
            })
        case .ACTIVITY:
            fallthrough
        case .DAILY_SUMMARY:
            fallthrough
        case .NIGHTLY_RECOVERY:
            fallthrough
        case .SLEEP:
            fallthrough
        case .SKIN_CONTACT_CHANGES:
            fallthrough
        case .SKINTEMP:
            fallthrough
        case .SLEEP_SCORE:
            if (until != nil) {
                return deleteListedFilesByType(identifier: identifier, dataDeletionStats: dataDeletionStats, entryPath: "/U/0/", until: until!, dataType, condition:{ (entry) -> Bool in
                             return entry.matches("^([0-9]{8})(\\/)") ||
                                 entry == "\(entryPattern)/" ||
                                 entry.contains(".BPB") &&
                                 !entry.contains("USERID.BPB") &&
                                 !entry.contains("HIST")
                         })
            } else {
                return Completable.error(PolarErrors.dateTimeFormatFailed(description: "Value for until date cannot be empty."))
            }
        case .UNDEFINED:
            return Completable.empty()
        }
    }

    private func deleteListedFilesByType(identifier: String, dataDeletionStats: DataDeletionStats, entryPath: String, until: Date, _ dataType: PolarStoredDataType.StoredDataType, condition: @escaping (_ p: String) -> Bool) -> Completable {
      
        listFiles(identifier: identifier, dataDeletionStats: dataDeletionStats, folderPath: entryPath, condition: condition)
            .map{ [self] item -> () in
                dataDeletionStats.fileDeletionMap[item] = false
            }
            .asObservable()
            .subscribe(
                onError: { error in
                    BleLogger.error("Failed to delete files.")
                    },
                onCompleted: { [self] in
                    switch dataType {
                    case .AUTO_SAMPLE:
                        BleLogger.trace("Starting to delete files from /U/0/AUTOS/ folder.")
                        checkAutoSampleFiles(identifier: identifier, dataDeletionStats: dataDeletionStats).subscribe(
                            onCompleted: {
                                deleteAutoSampleFiles(identifier: identifier, dataDeletionStats: dataDeletionStats)
                            }
                        )
                    case .SDLOGS:
                        BleLogger.trace("Starting to delete files from SDLOGS folder.")
                        deleteSdLogFiles(identifier: identifier, dataDeletionStats: dataDeletionStats)
                    case .ACTIVITY:
                        fallthrough
                    case .DAILY_SUMMARY:
                        fallthrough
                    case .NIGHTLY_RECOVERY:
                        fallthrough
                    case .SLEEP:
                        fallthrough
                    case .SKIN_CONTACT_CHANGES:
                        fallthrough
                    case .SKINTEMP:
                        fallthrough
                    case .SLEEP_SCORE:
                        BleLogger.trace("Starting to delete files from /U/0 directory, file type: " + dataType.rawValue)
                        deleteDataDirectories(identifier: identifier, until: until, dataDeletionStats: dataDeletionStats)
                    case .UNDEFINED:
                        BleLogger.trace("User selected \(dataType.rawValue). Do nothing.")
                    }
                }
            )
        
        
        while(!dataDeletionStats.deleteOpCompleted.get()){
            Thread.sleep(forTimeInterval: 0.5);
        }
        
        return Completable.empty()
    }
  
    private func listFiles(identifier: String, dataDeletionStats: DataDeletionStats, folderPath: String = "/", condition: @escaping (_ p: String) -> Bool) -> Observable<String> {
        
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Observable.error(PolarErrors.serviceNotFound)
            }
            
            var path = folderPath
            
            if (path.first != "/") {
                path.insert("/", at: path.startIndex)
            }
            if (path.last != "/") {
                path.insert("/", at: path.endIndex)
            }
       
            return fetchRecursive(path, client: client, condition: condition)
            .map { (entry) -> String in
                dataDeletionStats.fileDeletionMap[entry.name] = false
                return (entry.name)
            }

        } catch {
            return Observable.error(PolarErrors.deviceError(description: "Error in listing files from \(folderPath) path."))
        }
    }

    private func checkAutoSampleFiles(identifier: String, dataDeletionStats: DataDeletionStats) -> Observable<NSData> {
        
        return Observable.range(start: 0, count: dataDeletionStats.fileDeletionMap.count)
            .flatMap() { [self] index -> Observable<NSData> in
                let filePath = Array(dataDeletionStats.fileDeletionMap)[index]
               return getFile(identifier: identifier, filePath: filePath.key)
                .map { file -> NSData in
                    let calendar = Calendar.current
                    let dateFormatter = DateFormatter()
                    dateFormatter.dateFormat = "yyyyMMdd"
                    dateFormatter.timeZone = TimeZone(abbreviation: "UTC")

                    let fileData = try Data_PbAutomaticSampleSessions(serializedData: file as Data)
                    let proto = AutomaticSamples.fromProto(proto: fileData)
                    let dateCompareResult = calendar.compare(self.dateFromStringWOTime(dateFrom: dateFormatter.string(from: proto.day!)), to: self.dateFromStringWOTime(dateFrom: dateFormatter.string(from: Date())), toGranularity: .day)

                    switch dateCompareResult {
                    case .orderedSame:
                        dataDeletionStats.fileDeletionMap.removeValue(forKey: filePath.key)
                    case .orderedAscending:
                        break
                    case .orderedDescending:
                        break
                    }

                    return file as NSData
                }
            }
    }

    private func deleteAutoSampleFiles(identifier: String, dataDeletionStats: DataDeletionStats) {

        Observable.from(dataDeletionStats.fileDeletionMap)
            .flatMap() { file, item -> PrimitiveSequence<SingleTrait, ()> in
                dataDeletionStats.amountOfHandedDeletions+=1
                return self.removeSingleFile(identifier: identifier, filePath: file)
                    .map { _ -> () in
                        dataDeletionStats.fileDeletionMap[file] = true
                    }
            }.subscribe(
                onError: { error in
                    BleLogger.error("Error in deleting auto samples file from device \(identifier). Error: \(error)")
                },
                onCompleted: {
                    if (dataDeletionStats.amountOfHandedDeletions == dataDeletionStats.fileDeletionMap.count) {
                        dataDeletionStats.deleteOpCompleted.set(true)
                    }
                }
            )
    }

    private func deleteSdLogFiles(identifier: String, dataDeletionStats: DataDeletionStats) {
        
        Observable.from(dataDeletionStats.fileDeletionMap)
            .flatMap() { file, item -> PrimitiveSequence<SingleTrait, ()> in
                dataDeletionStats.amountOfHandedDeletions+=1
                return self.removeSingleFile(identifier: identifier, filePath: file)
                    .map { _ -> () in
                        dataDeletionStats.fileDeletionMap[file] = true
                    }
            }
            .subscribe(
                onNext: {print("")},
                onError: { error in
                    BleLogger.error("Error in deleting log file from device \(identifier). Error: \(error)")
                },
                onCompleted: {
                    if (dataDeletionStats.amountOfHandedDeletions == dataDeletionStats.fileDeletionMap.count) {
                        dataDeletionStats.deleteOpCompleted.set(true)
                    }
                }
            )
    }
    
    private func deleteDataDirectories(identifier: String, until: Date, dataDeletionStats: DataDeletionStats) {
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd"
        dateFormatter.timeZone = TimeZone(abbreviation: "UTC")
        
        var directorySet = [String : Bool]()

        for (file, _) in dataDeletionStats.fileDeletionMap {
            let fileUriParts = file.split(separator: "/")
            let fileDate = dateFromStringWOTime(dateFrom: String(fileUriParts[2]))
            // Remove time from until date.
            let until = dateFromStringWOTime(dateFrom: dateFormatter.string(from: until))
            if !(fileDate < until) {
                dataDeletionStats.fileDeletionMap.removeValue(forKey: file)
            }
        }

        Observable.from(dataDeletionStats.fileDeletionMap)
            .flatMap() { fileUri, item -> PrimitiveSequence<SingleTrait, ()> in
                dataDeletionStats.amountOfHandedDeletions+=1
                let parentFolderPath = "/" + fileUri.split(separator: "/").dropLast().joined(separator: "/")
                return self.removeSingleFile(identifier: identifier, filePath: parentFolderPath)
                    .map { _ -> () in
                        BleLogger.trace("Deleted directory \(parentFolderPath)")
                    }
            }.subscribe(
                onError: { error in
                    BleLogger.error("Error while deleting a data directory, error: \(error)")
                },
                onCompleted: {
                    self.findFilesFromDayDirectories(identifier: identifier, dataDeletionStats: dataDeletionStats)
                }
            )
    }

    private func findFilesFromDayDirectories(identifier: String, dataDeletionStats: DataDeletionStats) {
        
        var remainingFiles = [String : Bool]()
        
        if (!dataDeletionStats.fileDeletionMap.isEmpty) {
            for (file, _) in dataDeletionStats.fileDeletionMap {
                let fileUriParts = file.split(separator: "/")
                let fileUri = "/" + fileUriParts.dropLast().dropLast().joined(separator: "/")

                listFiles(identifier: identifier, dataDeletionStats: dataDeletionStats, folderPath: fileUri, condition: { (entry) -> Bool in
                    return entry.matches("^([0-9]{8})(\\/)") ||
                    entry.matches("^([A-Z-0-9]{1,6}[0-9]{0,10000})(/)") ||
                    entry.contains(".BPB") ||
                    entry.contains(".REC")
                })
                .map{ item -> () in
                    remainingFiles[item] = false
                }
                .asObservable()
                .subscribe(
                    onError: { error in
                        // If day directory is totally empty BlePsFtpUtility.processRfc76MessageFrame throws error as there is no payload from device.
                        if ("frameHasNoPayload".compare("\(error)") == .orderedSame) {
                            self.deleteDayDirectories(identifier: identifier, dataDeletionStats: dataDeletionStats)
                        } else {
                            BleLogger.error("An error occurred while trying read from path \(fileUri), error: \(error)")
                        }
                    },
                    onCompleted: {
                        if (remainingFiles.isEmpty) {
                            self.deleteDayDirectories(identifier: identifier, dataDeletionStats: dataDeletionStats)
                        } else {
                            dataDeletionStats.deleteOpCompleted.set(true)
                        }
                    }
                )
            }
        } else {
            dataDeletionStats.deleteOpCompleted.set(true)
        }
    }
    
    private func deleteDayDirectories(identifier: String, dataDeletionStats: DataDeletionStats) {
        
        var directorySet = [String : Bool]()
        
        for (file, _) in dataDeletionStats.fileDeletionMap {
            let fileUri = "/" + file.split(separator: "/").dropLast().dropLast().joined(separator: "/")
            directorySet[fileUri] = false
        }

        Observable.from(directorySet)
            .flatMap() { fileUri, item -> PrimitiveSequence<SingleTrait, ()> in
                return self.removeSingleFile(identifier: identifier, filePath: fileUri)
                    .map { _ -> () in
                        BleLogger.trace("Deleted directory \(fileUri)")
                    }
            }.subscribe(
                onError: { error in
                    BleLogger.error("An error occurred while deleting a directory, error: \(error)")
                },
                onCompleted: {
                    if (dataDeletionStats.amountOfHandedDeletions == dataDeletionStats.fileDeletionMap.count) {
                        dataDeletionStats.deleteOpCompleted.set(true)
                    }
                }
            )
    }

    private func removeSingleFile(identifier: String, filePath: String) -> Single<NSData> {
        
        do{
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Single.error(PolarErrors.serviceNotFound)
            }
            var operation = Protocol_PbPFtpOperation()
            operation.command = Protocol_PbPFtpOperation.Command.remove
            operation.path = filePath
            let request = try operation.serializedData()
            return client.request(request)
        } catch {
            return Single.error(PolarErrors.deviceError(description: "Failed to remove file \(filePath) path."))
        }
    }
    
    private func getFile(identifier: String, filePath: String) -> Observable<NSData> {
        do {
            let session = try self.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                return Observable.error(PolarErrors.serviceNotFound)
            }
            
            var operation = Protocol_PbPFtpOperation()
            operation.command = Protocol_PbPFtpOperation.Command.get
            operation.path = filePath
            let request = try operation.serializedData()
            return client.request(request).asObservable()
        } catch let err {
            return Observable.error(PolarErrors.deviceError(description: "Failed to list files from \(filePath) path. Error \(err)"))
        }
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

    private func sendInitializationAndStartSyncNotifications(client: BlePsFtpClient) {
        _ = client.sendNotification(
            Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue,
            parameters: nil
        ).subscribe()
        client.sendNotification(
            Protocol_PbPFtpHostToDevNotification.startSync.rawValue,
            parameters: nil
        ).subscribe()
    }

    private func sendInitializationAndStartSyncNotificationsCompletable(client: BlePsFtpClient) -> Completable {
        return client.sendNotification(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, parameters: nil)
            .andThen(
                client.sendNotification(Protocol_PbPFtpHostToDevNotification.startSync.rawValue, parameters: nil)
            )
    }

    private func sendTerminateAndStopSyncNotifications(client: BlePsFtpClient) {
        var params = Protocol_PbPFtpStopSyncParams()
        params.completed = true
        do {
            let parameters = try params.serializedData() as NSData
            _ = client.sendNotification(
                Protocol_PbPFtpHostToDevNotification.stopSync.rawValue,
                parameters: parameters
            ).subscribe()
        } catch {
            BleLogger.error("Failed to serialize stop sync parameters: \(error)")
        }
        
        client.sendNotification(
            Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue,
            parameters: nil
        ).subscribe()
    }

    private func writeFirmwareToDevice(deviceId: String, firmwareFilePath: String, firmwareBytes: Data) -> Observable<UInt> {
        let factoryResetMaxWaitTimeSeconds: TimeInterval = 6 * 60
        BleLogger.trace("Write FW to device")
        return doFactoryReset(deviceId, preservePairingInformation: true)
            .andThen(waitDeviceSessionToOpen(deviceId: deviceId, timeoutSeconds: Int(factoryResetMaxWaitTimeSeconds), waitForDeviceDownSeconds: 10))
            .andThen(Observable.create { observer in
                do {
                    let session = try self.sessionFtpClientReady(deviceId)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        throw PolarErrors.serviceNotFound
                    }

                    BleLogger.trace("Initialize session")
                    return self.sendInitializationAndStartSyncNotificationsCompletable(client: client)
                        .andThen(client.query(Protocol_PbPFtpQuery.prepareFirmwareUpdate.rawValue, parameters: nil).asCompletable())
                        .andThen(Completable.create { completable in
                            do {
                                BleLogger.trace("Start \(firmwareFilePath) write")
                                var builder = Protocol_PbPFtpOperation()
                                builder.command = Protocol_PbPFtpOperation.Command.put
                                builder.path = firmwareFilePath
                                let proto = try builder.serializedData()

                                _ = client.write(proto as NSData, data: InputStream(data: firmwareBytes))
                                    .throttle(.seconds(5), scheduler: MainScheduler.instance)
                                    .do(onNext: { bytesWritten in
                                        BleLogger.trace("Writing firmware update file, bytes written: \(bytesWritten)/\(firmwareBytes.count)")
                                        observer.onNext(UInt(bytesWritten))
                                    })
                                    .ignoreElements()
                                    .asCompletable()
                                    .subscribe(onCompleted: {
                                        if firmwareFilePath.contains("SYSUPDAT.IMG") {
                                            BleLogger.trace("Firmware file is SYSUPDAT.IMG, waiting for reboot")
                                        }
                                        observer.onCompleted()
                                        completable(.completed)
                                    }, onError: { error in
                                        if let pftpError = Protocol_PbPFtpError(rawValue: error._code) {
                                            if pftpError == .batteryTooLow {
                                                observer.onError(PolarErrors.deviceError(description: "Battery too low to perform firmware update"))
                                            } else {
                                                BleLogger.error("PFTP error during firmware write: \(error.localizedDescription)")
                                                observer.onError(error)
                                            }
                                        } else {
                                            BleLogger.error("Error during firmware write: \(error.localizedDescription)")
                                            observer.onError(error)
                                        }
                                        completable(.error(error))
                                    })
                            } catch {
                                completable(.error(error))
                            }
                            return Disposables.create()
                        })
                        .subscribe(onCompleted: {
                            observer.onCompleted()
                        }, onError: { error in
                            observer.onError(error)
                        })
                } catch {
                    observer.onError(error)
                }
                return Disposables.create()
            })
    }


    private func waitDeviceSessionToOpen(deviceId: String, timeoutSeconds: Int, waitForDeviceDownSeconds: Int = 0) -> Completable {
        BleLogger.trace("Wait for device session to open, timeoutSeconds: \(timeoutSeconds), waitForDeviceDownSeconds: \(waitForDeviceDownSeconds)")
        let pollIntervalSeconds = 5

        return Completable.create { emitter in
            var disposable: Disposable?
            disposable = Observable<Int>.timer(RxTimeInterval.seconds(waitForDeviceDownSeconds), scheduler: MainScheduler.instance)
                .flatMap { _ in
                    Observable<Int>.interval(RxTimeInterval.seconds(pollIntervalSeconds), scheduler: MainScheduler.instance)
                        .take(until: Observable<Int>.timer(RxTimeInterval.seconds(timeoutSeconds), scheduler: MainScheduler.instance))
                }
                .timeout(RxTimeInterval.seconds(timeoutSeconds), scheduler: MainScheduler.instance)
                .subscribe(onNext: { _ in
                    if self.deviceSessionState == BleDeviceSession.DeviceSessionState.sessionOpen {
                        BleLogger.trace("Session opened, deviceId: \(deviceId)")
                        disposable?.dispose()
                        emitter(.completed)
                    } else {
                        BleLogger.trace("Waiting for device session to open, deviceId: \(deviceId), current state: \(String(describing: self.deviceSessionState))")
                    }
                }, onError: { error in
                    BleLogger.trace("Timeout reached while waiting for device session to open, deviceId: \(deviceId)")
                    emitter(.error(error))
                })
            return Disposables.create {
                disposable?.dispose()
            }
        }
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
            let newSamples = existingData.samples + ppgData.samples.map { (timeStamp: $0.timeStamp!, channelSamples: $0.ppgDataSamples) }
            return .ppgOfflineRecordingData(
                (samples: newSamples, type: existingData.type),
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
            return .temperatureOfflineRecordingData(
                updatedData,
                startTime: startTime
            )
        default:
            return .temperatureOfflineRecordingData(
                skinTemperatureData.mapToPolarData(),
                startTime: offlineRecordingData.startTime
            )
        }
    }


    private func querySettings(_ identifier: String, type: PmdMeasurementType, recordingType: PmdRecordingType) -> Single<PolarSensorSetting> {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Single.error(PolarErrors.serviceNotFound) }
            
            return client.querySettings(type, recordingType)
                .map { (setting) -> PolarSensorSetting in
                    return setting.mapToPolarSettings()
                }
                .catch { (err) -> Single<PolarSensorSetting> in
                    return Single.error(PolarErrors.deviceError(description: "\(err)"))
                }
        } catch let err {
            return Single.error(err)
        }
    }
    
    private func queryFullSettings(_ identifier: String, type: PmdMeasurementType, recordingType: PmdRecordingType) -> Single<PolarSensorSetting> {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Single.error(PolarErrors.serviceNotFound) }
            
            return client.queryFullSettings(type, recordingType)
                .map { (setting) -> PolarSensorSetting in
                    return setting.mapToPolarSettings()
                }
                .catch { (err) -> Single<PolarSensorSetting> in
                    return Single.error(PolarErrors.deviceError(description: "\(err)"))
                }
        } catch let err {
            return Single.error(err)
        }
    }
    
    fileprivate func startStreaming<T>(_ identifier: String,
                                       type: PmdMeasurementType,
                                       settings: PolarSensorSetting,
                                       observer: @escaping (_ client: BlePmdClient) -> Observable<T>) -> Observable<T> {
        do {
            let session = try sessionPmdClientReady(identifier)
            let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as! BlePmdClient
            return client.startMeasurement(type, settings: settings.map2PmdSetting())
                .andThen(observer(client)
                    .do(onDispose: {
                        _ = client.stopMeasurement(type)
                            .subscribe()
                    }))
                .catch { (err) -> Observable<T> in
                    return Observable.error(PolarErrors.deviceError(description: "\(err)"))
                }
        } catch let err {
            return Observable.error(err)
        }
    }
    
    private func fetchRecursive(_ path: String, client: BlePsFtpClient, condition: @escaping (_ p: String) -> Bool) -> Observable<(name: String, size:UInt64)> {
        do {
            var operation = Protocol_PbPFtpOperation()
            operation.command = Protocol_PbPFtpOperation.Command.get
            operation.path = path
            let request = try operation.serializedData()
            
            return client.request(request)
                .asObservable()
                .flatMap { (data) -> Observable<(name: String, size:UInt64)> in
                    do {
                        let dir = try Protocol_PbPFtpDirectory(serializedData: data as Data)
                        let entries = dir.entries
                            .compactMap { (entry) -> (name: String, size:UInt64)? in
                                if condition(entry.name) {
                                    return (name: path + entry.name, size: entry.size)
                                }
                                return nil
                            }
                        if entries.count != 0 {
                            return Observable<(String, UInt64)>.from(entries)
                                .flatMap { (entry) -> Observable<(name: String, size:UInt64)> in
                                    if entry.0.hasSuffix("/") {
                                        return self.fetchRecursive(entry.0, client: client, condition: condition)
                                    } else {
                                        return Observable.just(entry)
                                    }
                                }
                        }
                        return Observable.empty()
                    } catch let err {
                        return Observable.error(PolarErrors.deviceError(description: "\(err)"))
                    }
                }
        } catch let err {
            return Observable.error(err)
        }
    }
    
    func enableSDKMode(_ identifier: String) -> Completable {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Completable.error(PolarErrors.serviceNotFound) }
            
            return client.startSdkMode()
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func disableSDKMode(_ identifier: String) -> Completable {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Completable.error(PolarErrors.serviceNotFound) }
            return client.stopSdkMode()
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func isSDKModeEnabled(_ identifier: String) -> Single<Bool> {
        do {
            let session = try sessionPmdClientReady(identifier)
            guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else { return Single.error(PolarErrors.serviceNotFound) }
            return client.isSdkModeEnabled()
                .map { $0 != PmdSdkMode.disabled }
        } catch let err {
            return Single.error(err)
        }
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
}

extension PrimitiveSequence where Trait == SingleTrait {
    public func asCompletable() -> PrimitiveSequence<CompletableTrait, Never> {
        return self.asObservable()
            .flatMap { _ in Observable<Never>.empty() }
            .asCompletable()
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
        var polarSamples: [(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)] = []
        for sample in self.samples {
            polarSamples.append((hr: sample.hr, ppInMs: sample.ppInMs, ppErrorEstimate: sample.ppErrorEstimate, blockerBit: sample.blockerBit, skinContactStatus: sample.skinContactStatus, skinContactSupported: sample.skinContactSupported))
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
        var polarSamples: [(timeStamp:UInt64, channelSamples: [Int32])] = []
        var dataType: PpgDataType!

        for sample in self.samples {
            if (sample.frameType == PmdDataFrameType.type_0) {
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: [sample.ppgDataSamples[0], sample.ppgDataSamples[1], sample.ppgDataSamples[2], sample.ambientSample ] ))
                dataType = PpgDataType.ppg3_ambient1
            }  else if (sample.frameType == PmdDataFrameType.type_6) {
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: sample.ppgDataSamples))
                dataType = PpgDataType.ppg1
            } else if (sample.frameType == PmdDataFrameType.type_7) {
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: sample.ppgDataSamples))
                dataType = PpgDataType.ppg17
            } else if (sample.frameType == PmdDataFrameType.type_10) {
                var samples = sample.ppgDataSamples
                samples.append(sample.status)
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: samples))
                dataType = PpgDataType.ppg21
            } else if (sample.frameType == PmdDataFrameType.type_9) {
                polarSamples.append((timeStamp: sample.timeStamp!, channelSamples: sample.ppgDataSamples))
                dataType = PpgDataType.ppg3
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

private class DataDeletionStats {
    var fileDeletionMap = [String : Bool]()
    let deleteOpCompleted = AtomicBoolean(initialValue: false)
    var amountOfHandedDeletions = 0
    var deletedDirs = [String]()
}
