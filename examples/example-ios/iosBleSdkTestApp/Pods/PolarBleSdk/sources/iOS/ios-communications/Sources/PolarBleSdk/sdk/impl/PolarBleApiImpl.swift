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
    weak var sdkModeFeatureObserver: PolarBleApiSdkModeFeatureObserver?
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
    
    private func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
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
                        self.deviceFeaturesObserver?.hrFeatureReady(deviceId)
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
                                    self.deviceFeaturesObserver?.streamingFeaturesReady(deviceId, streamingFeatures: featureSet)
                                    
                                    if value.contains(PmdMeasurementType.sdkMode) {
                                        self.sdkModeFeatureObserver?.sdkModeFeatureAvailable(deviceId)
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
        case .ppi, .hr:
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
        case .ppi, .hr:
            return Single.error(PolarErrors.operationNotSupported)
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
    
    func getOfflineRecord(_ identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret?) -> RxSwift.Single<PolarOfflineRecordingData> {
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
    
    func startOhrStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarOhrData> {
        return startStreaming(identifier, type: .ppg, settings: settings) { (client) -> Observable<PolarOhrData> in
            return client.observePpg()
                .map {
                    $0.mapToPolarOhrData()
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
    
    func startOhrPPIStreaming(_ identifier: String) -> Observable<PolarPpiData> {
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
                    return [(hr: UInt8($0.hr), rrsMs: $0.rrsMs, rrAvailable: $0.rrPresent, contactStatus: $0.sensorContact, contactStatusSupported: $0.sensorContactSupported)]
                }
        } catch let err {
            return Observable.error(err)
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
        return PolarGyroData(timeStamp: self.timeStamp, samples: polarSamples)
    }
}

private extension AccData {
    func mapToPolarData() -> PolarAccData {
        var polarSamples: [(timeStamp: UInt64, x: Int32, y: Int32, z: Int32)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z))
        }
        return PolarAccData(timeStamp: self.timeStamp, samples: polarSamples)
    }
}

private extension MagData {
    func mapToPolarData() -> PolarMagnetometerData {
        var polarSamples: [(timeStamp: UInt64, x: Float, y: Float, z: Float)] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, x: sample.x, y: sample.y, z: sample.z))
        }
        return PolarMagnetometerData(timeStamp: self.timeStamp, samples: polarSamples)
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
        var polarSamples: [(hr: UInt8, rrsMs: [Int], rrAvailable: Bool, contactStatus: Bool, contactStatusSupported: Bool)] = []
        for sample in self.samples {
            polarSamples.append((hr: sample.hr, rrsMs:[], rrAvailable: false, contactStatus: false, contactStatusSupported: false))
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
        return PolarEcgData(timeStamp: self.timeStamp, samples: polarSamples)
    }
}

private extension PpgData {
    func mapToPolarOhrData() -> PolarOhrData {
        var polarSamples: [(timeStamp:UInt64, channelSamples: [Int32])] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, channelSamples: [sample.ppgDataSamples[0], sample.ppgDataSamples[1], sample.ppgDataSamples[2], sample.ambientSample ] ))
        }
        return PolarOhrData(timeStamp: self.timeStamp, type: OhrDataType.ppg3_ambient1, samples: polarSamples)
    }
    
    func mapToPolarData() -> PolarPpgData {
        var polarSamples: [(timeStamp:UInt64, channelSamples: [Int32])] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, channelSamples: [sample.ppgDataSamples[0], sample.ppgDataSamples[1], sample.ppgDataSamples[2], sample.ambientSample ] ))
        }
        return PolarPpgData(type: PpgDataType.ppg3_ambient1, samples: polarSamples)
    }
}
