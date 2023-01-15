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
    weak var cccWriteObserver: PolarBleApiCCCWriteObserver?
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
    
    required public init(_ queue: DispatchQueue, features: Int) {
        var clientList: [(_ gattServiceTransmitter: BleAttributeTransportProtocol) -> BleGattClientBase] = []
        if ((features & Features.hr.rawValue) != 0) {
            clientList.append(BleHrClient.init)
        }
        if ((features & Features.deviceInfo.rawValue) != 0) {
            clientList.append(BleDisClient.init)
        }
        if ((features & Features.batteryStatus.rawValue) != 0) {
            clientList.append(BleBasClient.init)
        }
        if ((features & Features.polarSensorStreaming.rawValue) != 0) {
            clientList.append(BlePmdClient.init)
        }
        if ((features & Features.polarFileTransfer.rawValue) != 0) {
            clientList.append(BlePsFtpClient.init)
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
            self.observer?.deviceDisconnected(info)
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
    
    fileprivate func sessionPmdClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePmdClient.PMD_SERVICE)
        let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as! BlePmdClient
        if client.isCharacteristicNotificationEnabled(BlePmdClient.PMD_CP) &&
            client.isCharacteristicNotificationEnabled(BlePmdClient.PMD_DATA) {
            // client ready
            return session
        }
        throw PolarErrors.notificationNotEnabled
    }
    
    fileprivate func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        let session = try sessionServiceReady(identifier, service: BlePsFtpClient.PSFTP_SERVICE)
        let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as! BlePsFtpClient
        if client.isCharacteristicNotificationEnabled(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC) {
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
        throw PolarErrors.invalidArgument
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
    
    // hook clients based on services available
    fileprivate func setupDevice(_ session: BleDeviceSession) {
        session.cccWriteCallback = self
        let deviceId = session.advertisementContent.polarDeviceIdUntouched.count != 0 ?
        session.advertisementContent.polarDeviceIdUntouched :
        session.address.uuidString
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
                                    var featureSet = Set<DeviceStreamingFeature>()
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
                    case BlePsFtpClient.PSFTP_SERVICE:
                        return (client as! BlePsFtpClient).waitPsFtpReady(true)
                            .observe(on: self.scheduler)
                            .do(onCompleted: {
                                switch (BlePolarDeviceCapabilitiesUtility.fileSystemType(session.advertisementContent.polarDeviceType)) {
                                case .h10FileSystem,
                                        .sagRfc2FileSystem:
                                    self.deviceFeaturesObserver?.ftpFeatureReady(deviceId)
                                    break
                                default:
                                    break
                                }
                            })
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
                    let rrsMs = value.rrs.map({ (rr) -> Int in
                        return Int(round((Float(rr) / 1024.0) * 1000.0))
                    })
                    self.deviceHrObserver?.hrValueReceived(
                        deviceId, data: (hr: UInt8(value.hr), rrs: value.rrs, rrsMs: rrsMs, contact: value.sensorContact, contactSupported: value.sensorContactSupported))
                case .error(let error):
                    self.logMessage("\(error)")
                }
            }
    }
}

extension PolarBleApiImpl: BleCCCWriteProtocol {
    func cccWrite(_ address: UUID, characteristic: CBUUID) {
        cccWriteObserver?.cccWrite(address, characteristic: characteristic)
    }
}

extension PolarBleApiImpl: BleLoggerProtocol {
    func logMessage(_ message: String) {
        logger?.message(message)
    }
}

extension PolarBleApiImpl: PolarBleApi {
    
    func cleanup() {
        _ = listener.removeAllSessions(
            Set(CollectionOfOne(BleDeviceSession.DeviceSessionState.sessionClosed)))
    }
    
    func polarFilter(_ enable: Bool) {
        listener.scanPreFilter = enable ? deviceFilter : nil
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
    
    func isFeatureReady(_ identifier: String, feature: Features) -> Bool {
        switch feature {
        case .polarFileTransfer:
            do {
                _ = try sessionFtpClientReady(identifier)
                return true;
            } catch _ {
                // do nothing
            }
        case .polarSensorStreaming:
            do {
                _ = try sessionPmdClientReady(identifier)
                return true
            } catch _ {
                // do nothing
            }
        default:
            break
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
    
    func startRecording(_ identifier: String, exerciseId: String, interval: RecordingInterval = RecordingInterval.interval_1s, sampleType: SampleType) -> Completable {
        do {
            guard exerciseId.count > 0 && exerciseId.count < 64 else {
                throw PolarErrors.invalidArgument
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
    
    func requestStreamSettings(_ identifier: String, feature: DeviceStreamingFeature) -> Single<PolarSensorSetting> {
        switch feature {
        case .ecg:
            return querySettings(identifier, type: .ecg)
        case .acc:
            return querySettings(identifier, type: .acc)
        case .ppg:
            return querySettings(identifier, type: .ppg)
        case .magnetometer:
            return querySettings(identifier, type: .mgn)
        case .gyro:
            return querySettings(identifier, type: .gyro)
        case .ppi:
            return Single.error(PolarErrors.operationNotSupported)
        }
    }
    
    func requestFullStreamSettings(_ identifier: String, feature: DeviceStreamingFeature) -> Single<PolarSensorSetting> {
        switch feature {
        case .ecg:
            return queryFullSettings(identifier, type: .ecg)
        case .acc:
            return queryFullSettings(identifier, type: .acc)
        case .ppg:
            return queryFullSettings(identifier, type: .ppg)
        case .magnetometer:
            return queryFullSettings(identifier, type: .mgn)
        case .gyro:
            return queryFullSettings(identifier, type: .gyro)
        case .ppi:
            return Single.error(PolarErrors.operationNotSupported)
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
                    $0.mapToPolarData()
                }
        }
    }
    
    func startOhrPPIStreaming(_ identifier: String) -> Observable<PolarPpiData> {
        return startStreaming(identifier, type: .ppi, settings: PolarSensorSetting()) { (client) -> Observable<PolarPpiData> in
            return client.observePpi().map {
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
                .map({ (path) -> (path: String, date: Date, entryId: String) in
                    let components = path.split(separator: "/")
                    let dateFormatter = DateFormatter()
                    dateFormatter.dateFormat = "yyyyMMddHHmmss"
                    if let date = dateFormatter.date(from: String(components[2] + components[4])) {
                        return (path,date: date, entryId: String(components[2] + components[4]))
                    }
                    throw PolarErrors.dateTimeFormatFailed()
                })
                .catch({ (err) -> Observable<PolarExerciseEntry> in
                    return Observable.error(PolarErrors.deviceError(description: "\(err)"))
                })
            } else if fsType == .h10FileSystem {
                return fetchRecursive("/", client: client, condition: { (entry) -> Bool in
                    return entry.hasSuffix("/") || entry == "SAMPLES.BPB"
                })
                .map({ (path) -> (path: String, date: Date, entryId: String) in
                    let components = path.split(separator: "/")
                    return (path,date: Date(), entryId: String(components[0]))
                })
                .catch({ (err) -> Observable<PolarExerciseEntry> in
                    return Observable.error(PolarErrors.deviceError(description: "\(err)"))
                })
            }
            throw PolarErrors.operationNotSupported
        } catch let err {
            return Observable.error(err)
        }
    }
    
    private func querySettings(_ identifier: String, type: PmdMeasurementType) -> Single<PolarSensorSetting> {
        do {
            let session = try sessionPmdClientReady(identifier)
            let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as! BlePmdClient
            return client.querySettings(type)
                .map { (setting) -> PolarSensorSetting in
                    return PolarSensorSetting(setting.settings)
                }
                .catch { (err) -> Single<PolarSensorSetting> in
                    return Single.error(PolarErrors.deviceError(description: "\(err)"))
                }
        } catch let err {
            return Single.error(err)
        }
    }
    
    private func queryFullSettings(_ identifier: String, type: PmdMeasurementType) -> Single<PolarSensorSetting> {
        do {
            let session = try sessionPmdClientReady(identifier)
            let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as! BlePmdClient
            return client.queryFullSettings(type)
                .map { (setting) -> PolarSensorSetting in
                    return PolarSensorSetting(setting.settings)
                }
                .catch { (err) -> Single<PolarSensorSetting> in
                    return Single.error(PolarErrors.deviceError(description: "\(err)"))
                }
        } catch let err {
            return Single.error(err)
        }
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
    
    func fetchRecursive(_ path: String, client: BlePsFtpClient, condition: @escaping (_ p: String) -> Bool) -> Observable<String> {
        do {
            var operation = Protocol_PbPFtpOperation()
            operation.command = Protocol_PbPFtpOperation.Command.get
            operation.path = path
            let request = try operation.serializedData()
            
            return client.request(request)
                .asObservable()
                .observe(on: MainScheduler.instance)
                .flatMap { (data) -> Observable<String> in
                    do {
                        let dir = try Protocol_PbPFtpDirectory(serializedData: data as Data)
                        let entries = dir.entries
                            .compactMap({ (entry) -> String? in
                                if condition(entry.name) {
                                    return path + entry.name
                                }
                                
                                return nil
                            })
                        if entries.count != 0 {
                            return Observable<String>.from(entries)
                                .flatMap({ (path) -> Observable<String> in
                                    if path.hasSuffix("/") {
                                        return self.fetchRecursive(path, client: client, condition: condition)
                                    } else {
                                        return Observable.just(path)
                                    }
                                })
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
            let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as! BlePmdClient
            return client.startSdkMode()
        } catch let err {
            return Completable.error(err)
        }
    }
    
    func disableSDKMode(_ identifier: String) -> Completable {
        do {
            let session = try sessionPmdClientReady(identifier)
            let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as! BlePmdClient
            return client.stopSdkMode()
        } catch let err {
            return Completable.error(err)
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
    func mapToPolarData() -> PolarOhrData {
        var polarSamples: [(timeStamp:UInt64, channelSamples: [Int32])] = []
        for sample in self.samples {
            polarSamples.append((timeStamp: sample.timeStamp, channelSamples: [sample.ppgDataSamples[0], sample.ppgDataSamples[1], sample.ppgDataSamples[2], sample.ambientSample ] ))
        }
        return PolarOhrData(timeStamp: self.timeStamp, type: OhrDataType.ppg3_ambient1, samples: polarSamples)
    }
}
