
import CoreBluetooth
import Foundation
import RxSwift

public struct Pmd {
    
    static func parseDeltaFrameRefSamples(_ data: Data, channels: UInt8, resolution: UInt8)  -> [Int32] {
        let resolutionInBytes = Int(ceil(Double(resolution)/8.0))
        return Array(stride(from: 0, to: (resolutionInBytes*Int(channels)), by: resolutionInBytes).map { (start) -> Int32 in
            let value = data.subdata(in: start..<start.advanced(by: resolutionInBytes))
            return BlePmdClient.arrayToInt(value, offset: 0, size: resolutionInBytes)
        })
    }
    
    static func parseDeltaFrame(_ data: Data, channels: UInt32, bitWidth: UInt32, totalBitLength: UInt32) -> [[Int32]]{
        // convert array to bits
        let dataInBits = data.flatMap { (byte) -> [Bool] in
            return Array(stride(from: 0, to: 8, by: 1)
                .map { (index) -> Bool in
                    return (byte & (0x01 << index)) != 0
                }
            )
        }
        
        let mask = Int32.max << Int32(bitWidth-1)
        let channelBitsLength = bitWidth*channels
        return Array(stride(from: 0, to: totalBitLength, by: UInt32.Stride(channelBitsLength)).map { (start) -> [Int32] in
            return Array(stride(from: start, to: UInt32(start+UInt32(channelBitsLength)), by: UInt32.Stride(bitWidth))
                .map { (subStart) -> Int32 in
                    let deltaSampleList: ArraySlice<Bool> = dataInBits[Int(subStart)..<Int(subStart+UInt32(bitWidth))]
                    var deltaSample: Int32 = 0
                    var i=0
                    deltaSampleList.forEach { (bitValue) in
                        let bit = Int32(bitValue ? 1 : 0)
                        deltaSample |= (bit << i)
                        i += 1
                    }
                    
                    if((deltaSample & mask) != 0) {
                        deltaSample |= mask;
                    }
                    return deltaSample
                })
        })
    }
    
    static func parseDeltaFramesToSamples(_ data: Data, channels: UInt8, resolution: UInt8) -> [[Int32]] {
        let refSamples = parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        var offset = Int(channels * UInt8(ceil(Double(resolution)/8.0)))
        var samples = [[Int32]]()
        samples.append(contentsOf: [refSamples])
        while offset < data.count {
            let deltaSize = UInt32(data[offset])
            offset += 1
            let sampleCount = UInt32(data[offset])
            offset += 1
            let bitLength = (sampleCount * deltaSize * UInt32(channels))
            let length = Int(ceil(Double(bitLength) / 8.0))
            let frame = data.subdata(in: offset..<(offset+length))
            let deltas = parseDeltaFrame(frame, channels: UInt32(channels), bitWidth: deltaSize, totalBitLength: bitLength)
            offset += length
            deltas.forEach { (delta) in
                var nextSamples = [Int32]()
                let last = samples.last!
                for i in 0..<channels {
                    let sample = last[Int(i)] + delta[Int(i)]
                    nextSamples.append(sample)
                }
                samples.append(nextSamples)
            }
        }
        return samples
    }
}

/// client declaration
public class BlePmdClient: BleGattClientBase {
    
    public static let PMD_SERVICE = CBUUID(string: "FB005C80-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PMD_CP = CBUUID(string: "FB005C81-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PMD_DATA = CBUUID(string: "FB005C82-02E7-F387-1CAD-8ACD2D8DF0C8")
    
    let pmdCpResponseQueue = AtomicList<Data>()
    var features: Data?
    
    private var observersAcc = AtomicList<RxObserver<AccData>>()
    private var observersGyro = AtomicList<RxObserver<GyrData>>()
    private var observersMagnetometer = AtomicList<RxObserver<MagData>>()
    private var observersEcg = AtomicList<RxObserver<EcgData>>()
    private var observersPpg = AtomicList<RxObserver<PpgData>>()
    private var observersPpi = AtomicList<RxObserver<PpiData>>()
    private var observersFeature = AtomicList<RxObserverSingle<Set<PmdMeasurementType>>>()
    private var storedSettings  = AtomicType<[PmdMeasurementType : PmdSetting]>(initialValue: [PmdMeasurementType : PmdSetting]())
    private var previousTimeStamp = AtomicType<[PmdMeasurementType : UInt64]>(initialValue: [PmdMeasurementType : UInt64]())
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BlePmdClient.PMD_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(BlePmdClient.PMD_CP)
        automaticEnableNotificationsOnConnect(chr: BlePmdClient.PMD_CP)
        automaticEnableNotificationsOnConnect(chr: BlePmdClient.PMD_DATA)
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        clearStreamObservers(error: BleGattException.gattDisconnected)
        RxUtils.postErrorOnSingleAndClearList(observersFeature, error: BleGattException.gattDisconnected)
        pmdCpResponseQueue.removeAll()
        features = nil
        previousTimeStamp.set([:])
    }
    
    fileprivate func fetchSetting(_ type: PmdMeasurementType, setting: PmdSetting.PmdSettingType) -> UInt32? {
        var ret: UInt32?
        storedSettings.accessItem({ (settings) in
            ret = settings[type]?.selected[setting]
        })
        return ret
    }
    
    fileprivate func getFactor(_ type: PmdMeasurementType) -> Float {
        var factor: UInt32?
        storedSettings.accessItem({ (settings) in
            factor = settings[type]?.selected[.factor]
        })
        if let value = factor {
            return Float(bitPattern: value)
        }
        BleLogger.error("factor not stored to settings")
        return 1.0
    }
    
    private func getSampleRate(_ type: PmdMeasurementType) -> UInt {
        return UInt(fetchSetting(type, setting: PmdSetting.PmdSettingType.sampleRate) ?? 0)
    }
    
    private func getPreviousFrameTimeStamp(_ type: PmdMeasurementType) -> UInt64 {
        var timeStamp: UInt64?
        previousTimeStamp.accessItem({ (timeStamps) in
            timeStamp = timeStamps[type]
        })
        return timeStamp ?? 0
    }
    
    private func setPreviousFrameTimeStamp(_ type: PmdMeasurementType, _ timeStamp: UInt64 ) {
        previousTimeStamp.accessItem { (timeStamps) in
            timeStamps[type] = timeStamp
        }
    }
    
    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int ) {
        if chr.isEqual(BlePmdClient.PMD_CP) {
            if err == 0 {
                if data[0] == 0x0f {
                    features = data
                    RxUtils.emitNext(observersFeature) { (observer) in
                        observer.obs(.success(PmdMeasurementType.fromByteArray(data)))
                    }
                } else {
                    processCpCommand(data)
                }
            } else {
                BleLogger.error("PMD CP attribute error: \(err)")
            }
        } else if chr.isEqual(BlePmdClient.PMD_DATA) {
            if err == 0 {
                processPmdData(data)
            } else {
                BleLogger.error("PMD MTU attribute error: \(err)")
            }
        }
    }
    private func processCpCommand(_ data: Data) {
        BleLogger.trace_hex("PMD command: ", data: data)
        
        guard !data.isEmpty else {
            BleLogger.error("PMD CP received empty data")
            return
        }
        
        if(data[0] != PmdControlPointResponse.CONTROL_POINT_RESPONSE_CODE) {
            switch (data[0]) {
            case PmdControlPointCommandServiceToClient.ONLINE_MEASUREMENT_STOPPED:
                for dataType in data.dropFirst() {
                    let errorDescription = "Stop command from device"
                    switch PmdMeasurementType.fromId(id: dataType) {
                    case .ecg:
                        RxUtils.postErrorAndClearList(observersEcg, error: BlePmdError.bleOnlineStreamClosed(description: errorDescription ))
                    case .ppg:
                        RxUtils.postErrorAndClearList(observersPpg, error: BlePmdError.bleOnlineStreamClosed(description: errorDescription))
                    case .acc:
                        RxUtils.postErrorAndClearList(observersAcc, error: BlePmdError.bleOnlineStreamClosed(description: errorDescription))
                    case .ppi:
                        RxUtils.postErrorAndClearList(observersPpi, error: BlePmdError.bleOnlineStreamClosed(description: errorDescription))
                    case .gyro:
                        RxUtils.postErrorAndClearList(observersGyro, error: BlePmdError.bleOnlineStreamClosed(description: errorDescription))
                    case .mgn:
                        RxUtils.postErrorAndClearList(observersMagnetometer, error: BlePmdError.bleOnlineStreamClosed(description: errorDescription))
                    default:
                        BleLogger.error("PMD CP, not supported PmdMeasurementType for Measurement stop. Measurement type value \(dataType) ")
                    }
                }
                break
            default:
                BleLogger.error("PMD CP, not supported CP command from server. Command \(data[0]) ")
                break
            }
            
        } else {
            self.pmdCpResponseQueue.push(data)
        }
    }
    
    private func processPmdData(_ data: Data) {
        BleLogger.trace_hex("PMD Data: ", data: data)
        
        let frame: PmdDataFrame
        do {
            frame = try PmdDataFrame(data: data, getPreviousFrameTimeStamp, getFactor, getSampleRate)
        } catch {
            print("Couldn't parse the data frame. Reason: \(error)")
            return
        }
        setPreviousFrameTimeStamp(frame.measurementType, frame.timeStamp)
        
        switch (frame.measurementType ) {
        case PmdMeasurementType.ecg:
            RxUtils.emitNext(observersEcg) { (emitter) in
                do {
                    let parsedData = try EcgData.parseDataFromDataFrame(frame: frame)
                    emitter.obs.onNext(parsedData)
                } catch let error {
                    emitter.obs.onError(error)
                }
            }
            
        case PmdMeasurementType.ppg:
            RxUtils.emitNext(observersPpg) { (emitter) in
                do {
                    let parsedData = try PpgData.parseDataFromDataFrame(frame: frame)
                    emitter.obs.onNext(parsedData)
                } catch let error {
                    emitter.obs.onError(error)
                }
            }
        case PmdMeasurementType.acc:
            RxUtils.emitNext(observersAcc) { (emitter) in
                do {
                    let parsedData = try AccData.parseDataFromDataFrame(frame: frame)
                    emitter.obs.onNext(parsedData)
                } catch let error {
                    emitter.obs.onError(error)
                }
            }
        case PmdMeasurementType.ppi:
            RxUtils.emitNext(observersPpi) { (emitter) in
                do {
                    let parsedData = try PpiData.parseDataFromDataFrame(frame: frame)
                    emitter.obs.onNext(parsedData)
                } catch let error {
                    emitter.obs.onError(error)
                }
            }
        case PmdMeasurementType.mgn:
            RxUtils.emitNext(observersMagnetometer) { (emitter) in
                do {
                    let parsedData = try MagData.parseDataFromDataFrame(frame: frame)
                    emitter.obs.onNext(parsedData)
                } catch let error {
                    emitter.obs.onError(error)
                }
            }
        case PmdMeasurementType.gyro:
            RxUtils.emitNext(observersGyro) { (emitter) in
                do {
                    let parsedData = try GyrData.parseDataFromDataFrame(frame: frame)
                    emitter.obs.onNext(parsedData)
                } catch let error {
                    emitter.obs.onError(error)
                }
            }
        default:
            BleLogger.trace("Non handled stream type received")
        }
    }
    
    static func arrayToUInt(_ data: Data, offset: Int, size: Int) -> UInt32 {
        let result = data.subdata(in: offset..<(offset+size)) as NSData
        var value: UInt32 = 0
        assert(size <= 4)
        memcpy(&value,result.bytes,size)
        return value
    }
    
    static func arrayToInt(_ data: Data, offset: Int, size: Int) -> Int32 {
        let result = data.subdata(in: offset..<(offset+size)) as NSData
        var value: Int32 = 0
        assert(size <= 4)
        memcpy(&value,result.bytes,size)
        let mask = (Int32.max << ((size*8)-1))
        if (value & mask) != 0 {
            value |= mask
        }
        return value
    }
    
    public func observeAcc() -> Observable<AccData> {
        return RxUtils.monitor(observersAcc, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observeGyro() -> Observable<GyrData> {
        return RxUtils.monitor(observersGyro, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observeMagnetometer() -> Observable<MagData> {
        return RxUtils.monitor(observersMagnetometer, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observeEcg() -> Observable<(EcgData)> {
        return RxUtils.monitor(observersEcg, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observePpg() -> Observable<PpgData> {
        return RxUtils.monitor(observersPpg, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observePpi() -> Observable<PpiData> {
        return RxUtils.monitor(observersPpi, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func startMeasurement(_ type: PmdMeasurementType, settings: PmdSetting, _ recordingType: PmdRecordingType = PmdRecordingType.online, _ secret: PmdSecret? = nil) -> Completable {
        storedSettings.accessItem { (settingsStored) in
            settingsStored[type] = settings
        }
        return Completable.create { observer in
            do {
                let measurementType = type.rawValue
                let requestByte = recordingType.asBitField() | measurementType
                let settingsBytes = settings.serialize()
                let secretBytes = secret?.serializeToPmdSettings() ?? Data()
                var packet = Data([PmdControlPointCommandClientToService.REQUEST_MEASUREMENT_START, requestByte])
                packet.append(settingsBytes)
                packet.append(secretBytes)
                BleLogger.trace( "start measurement. Measurement type: \(type) Recording type: \(recordingType) Secret provided: \(secret != nil) ")
                
                let cpResponse = try self.sendControlPointCommand(packet as Data)
                if cpResponse.errorCode == .success {
                    self.storedSettings.accessItem { (settingsStored) in
                        settingsStored[type]?.updatePmdSettingsFromStartResponse(cpResponse.parameters as Data)
                    }
                    observer(.completed)
                } else {
                    observer(.error(BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create {
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    func stopMeasurement(_ type: PmdMeasurementType) -> Completable {
        return Completable.create{ observer in
            do {
                let packet = Data([PmdControlPointCommandClientToService.STOP_MEASUREMENT ,type.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    observer(.completed)
                } else {
                    observer(.error(BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
                }
                self.setPreviousFrameTimeStamp(type, 0)
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create{}
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    public func readFeature(_ checkConnection: Bool) -> Single<Set<PmdMeasurementType>> {
        return Single.create { observer in
            let object = RxObserverSingle<Set<PmdMeasurementType>>(obs: observer)
            
            if let f = self.features {
                // available
                observer(.success(PmdMeasurementType.fromByteArray(f)))
            } else {
                if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                    self.observersFeature.append(object)
                } else {
                    observer(.failure(BleGattException.gattDisconnected))
                }
            }
            return Disposables.create {
                self.observersFeature.remove(
                    { (item) -> Bool in
                        return item === object
                    }
                )
            }
        }
    }
    
    func readMeasurementStatus() -> Single<[(PmdMeasurementType, PmdActiveMeasurement)]> {
        return Single.create{ [unowned self] observer in
            do {
                let cpResponse = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.GET_MEASUREMENT_STATUS]))
                if cpResponse.errorCode == .success {
                    var measurementStatus = [(PmdMeasurementType, PmdActiveMeasurement)]()
                    for parameter in cpResponse.parameters as Data {
                        switch(PmdMeasurementType.fromId(id: parameter)) {
                        case .ecg:
                            measurementStatus.append((PmdMeasurementType.ecg, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        case .ppg:
                            measurementStatus.append((PmdMeasurementType.ppg, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        case .acc:
                            measurementStatus.append((PmdMeasurementType.acc, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        case .ppi:
                            measurementStatus.append((PmdMeasurementType.ppi, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        case .gyro:
                            measurementStatus.append((PmdMeasurementType.gyro, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        case .mgn:
                            measurementStatus.append((PmdMeasurementType.mgn, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        case .location:
                            measurementStatus.append((PmdMeasurementType.location, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        case .pressure:
                            measurementStatus.append((PmdMeasurementType.pressure, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        case .temperature:
                            measurementStatus.append((PmdMeasurementType.temperature, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        case .offline_hr:
                            measurementStatus.append((PmdMeasurementType.offline_hr, PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)))
                            break
                        default:
                            break
                        }
                    }
                    observer(.success(measurementStatus))
                    
                } else {
                    observer(.failure(BleGattException.gattAttributeError(errorCode: cpResponse.errorCode.rawValue, errorDescription: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.failure(err))
            }
            return Disposables.create{
                // do nothing
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    public override func clientReady(_ checkConnection: Bool) -> Completable {
        return waitNotificationEnabled(BlePmdClient.PMD_CP, checkConnection: checkConnection)
            .andThen(waitNotificationEnabled(BlePmdClient.PMD_DATA, checkConnection: checkConnection))
    }
    
    func querySettings(_ type: PmdMeasurementType, _ recordingType: PmdRecordingType) -> Single<PmdSetting> {
        return Single.create{ [unowned self] observer in
            do {
                let measurementType = type.rawValue
                let requestByte = recordingType.asBitField() | measurementType
                let packet = Data([PmdControlPointCommandClientToService.GET_MEASUREMENT_SETTINGS, requestByte])
                
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    let settings = PmdSetting(cpResponse.parameters as Data)
                    observer(.success(settings))
                } else {
                    observer(.failure(BleGattException.gattAttributeError(errorCode: cpResponse.errorCode.rawValue, errorDescription: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.failure(err))
            }
            return Disposables.create{
                // do nothing
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    func queryFullSettings(_ type: PmdMeasurementType,_ recordingType: PmdRecordingType ) -> Single<PmdSetting> {
        return Single.create{ [unowned self] observer in
            do {
                let measurementType = type.rawValue
                let requestByte = recordingType.asBitField() | measurementType
                let packet = Data([PmdControlPointCommandClientToService.GET_SDK_MODE_SETTINGS, requestByte])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    let settings = PmdSetting(cpResponse.parameters as Data)
                    observer(.success(settings))
                } else {
                    observer(.failure(BleGattException.gattAttributeError(errorCode: cpResponse.errorCode.rawValue, errorDescription: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.failure(err))
            }
            return Disposables.create{
                // do nothing
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    public func startSdkMode() -> Completable {
        return Completable.create{ observer in
            do {
                let packet = Data([PmdControlPointCommandClientToService.REQUEST_MEASUREMENT_START, PmdMeasurementType.sdkMode.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success || cpResponse.errorCode == .errorAlreadyInState {
                    observer(.completed)
                } else {
                    observer(.error(BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create{}
        }
        .andThen( { () -> Completable in
            return Completable.create { observer in
                self.clearStreamObservers(error: BleGattException.gattOperationModeChange(description: "SDK mode enabled"))
                observer(.completed)
                return Disposables.create()
            }
        }())
        .subscribe(on: baseSerialDispatchQueue)
    }
    
    public func stopSdkMode() -> Completable {
        return Completable.create{ observer in
            do {
                let packet = Data([PmdControlPointCommandClientToService.STOP_MEASUREMENT, PmdMeasurementType.sdkMode.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success || cpResponse.errorCode == .errorAlreadyInState{
                    observer(.completed)
                } else {
                    observer(.error(BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create{}
        }
        .andThen( { () -> Completable in
            return Completable.create { observer in
                self.clearStreamObservers(error: BleGattException.gattOperationModeChange(description: "SDK mode disabled"))
                observer(.completed)
                return Disposables.create()
            }
        }())
        .subscribe(on: baseSerialDispatchQueue)
    }
    
    func isSdkModeEnabled() -> Single<PmdSdkMode> {
        return Single.create{ [unowned self] observer in
            do {
                let packet = Data([PmdControlPointCommandClientToService.GET_SDK_MODE_STATUS])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    let byteArray = [UInt8](cpResponse.parameters as Data)
                    if let responseParameter = byteArray.first {
                        let sdkMode = PmdSdkMode.fromResponse(sdkModeByte: responseParameter)
                        observer(.success(sdkMode))
                    } else {
                        observer(.failure(BleGattException.gattDataError(description: "Couldn't get the SDK mode status. Response parameter is missing")))
                    }
                } else {
                    observer(.failure(BleGattException.gattAttributeError(errorCode: cpResponse.errorCode.rawValue, errorDescription: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.failure(err))
            }
            return Disposables.create{
                // do nothing
            }
        }
        .subscribe(on: baseSerialDispatchQueue)
    }
    
    func setOfflineRecordingTrigger(offlineRecordingTrigger: PmdOfflineTrigger, secret: PmdSecret?) -> Completable {
        let setOfflineTriggerModeCompletable = setOfflineRecordingTriggerMode(triggerMode: offlineRecordingTrigger.triggerMode)
        let setOfflineTriggerSettingsCompletable: Completable
        if (offlineRecordingTrigger.triggerMode != PmdOfflineRecTriggerMode.disabled) {
            
            setOfflineTriggerSettingsCompletable = getOfflineRecordingTriggerStatus()
                .flatMapCompletable { pmdOfflineTriggers -> Completable in
                    let allSettingCommands = pmdOfflineTriggers.triggers
                        .map { availableMeasurementType in
                            if let trigger = offlineRecordingTrigger.triggers[availableMeasurementType.key] {
                                BleLogger.trace("Enable trigger \(availableMeasurementType.key)")
                                return self.setOfflineRecordingTriggerSetting(triggerStatus: PmdOfflineRecTriggerStatus.enabled, type: availableMeasurementType.key, setting: trigger.setting, secret: secret)
                                
                            } else {
                                BleLogger.trace("Disable trigger \(availableMeasurementType.key)")
                                return self.setOfflineRecordingTriggerSetting(triggerStatus: PmdOfflineRecTriggerStatus.disabled, type: availableMeasurementType.key)
                            }
                        }
                    return Completable.concat(allSettingCommands)
                }
        } else {
            setOfflineTriggerSettingsCompletable = Completable.empty()
        }
        
        return setOfflineTriggerModeCompletable.concat(setOfflineTriggerSettingsCompletable)
    }
    
    private func setOfflineRecordingTriggerMode(triggerMode: PmdOfflineRecTriggerMode) -> Completable {
        return Completable.create{ observer in
            do {
                let packet = Data([PmdControlPointCommandClientToService.SET_OFFLINE_RECORDING_TRIGGER_MODE, triggerMode.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    observer(.completed)
                } else {
                    observer(.error(BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create{}
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    func getOfflineRecordingTriggerStatus() -> Single<PmdOfflineTrigger> {
        return Single.create{ [unowned self] observer in
            do {
                let packet = Data([PmdControlPointCommandClientToService.GET_OFFLINE_RECORDING_TRIGGER_STATUS])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    let data = (cpResponse.parameters as Data)
                    if !data.isEmpty {
                        let triggerStatus = try PmdOfflineTrigger.fromResponse(data: data)
                        observer(.success(triggerStatus))
                    } else {
                        observer(.failure(BleGattException.gattDataError(description: "Couldn't get the Offline recording trigger status. Response parameter is missing")))
                    }
                } else {
                    observer(.failure(BleGattException.gattAttributeError(errorCode: cpResponse.errorCode.rawValue, errorDescription: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.failure(err))
            }
            return Disposables.create{
                // do nothing
            }
        }
        .subscribe(on: baseSerialDispatchQueue)
    }
    
    private func setOfflineRecordingTriggerSetting(triggerStatus: PmdOfflineRecTriggerStatus, type: PmdMeasurementType, setting: PmdSetting? = nil, secret: PmdSecret? = nil) -> Completable {
        
        return Completable.create{ observer in
            do {
                if type.isDataType(){
                    let triggerStatusByte: UInt8 = triggerStatus.rawValue
                    let measurementTypeByte: UInt8 = type.rawValue
                    
                    let settingsBytes: Data
                    
                    if (triggerStatus == PmdOfflineRecTriggerStatus.enabled) {
                        let settingBytes = setting?.serialize() ?? Data()
                        let securityBytes = secret?.serializeToPmdSettings() ?? Data()
                        let length:UInt8 = UInt8((settingBytes + securityBytes).count)
                        settingsBytes = Data([length]) + settingBytes + securityBytes
                    } else {
                        settingsBytes = Data()
                    }
                    
                    let parameters = Data([triggerStatusByte, measurementTypeByte]) + settingsBytes
                    let packet = Data([PmdControlPointCommandClientToService.SET_OFFLINE_RECORDING_TRIGGER_SETTINGS]) + parameters
                    let cpResponse = try self.sendControlPointCommand(packet)
                    if cpResponse.errorCode == .success {
                        observer(.completed)
                    } else {
                        observer(.error(BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
                    }
                    
                } else {
                    observer(.error(BlePmdError.controlPointRequestFailed(errorCode: PmdResponseCode.errorInvalidMeasurementType.rawValue, description: "Invalid PmdMeasurementType: \(type)")))
                    
                }
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create{}
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    private func sendControlPointCommand(_ data: Data) throws -> PmdControlPointResponse {
        guard let transport = self.gattServiceTransmitter else {
            throw BleGattException.gattTransportNotAvailable
        }
        try transport.transmitMessage(self, serviceUuid: BlePmdClient.PMD_SERVICE, characteristicUuid: BlePmdClient.PMD_CP, packet: data, withResponse: true)
        let response = try self.pmdCpResponseQueue.poll(60)
        let resp = PmdControlPointResponse(response)
        var more = resp.more
        while (more) {
            let parameters = try self.pmdCpResponseQueue.poll(60)
            let moreResponse = PmdControlPointResponse(parameters)
            more = moreResponse.more
            resp.parameters.append(Data(moreResponse.parameters))
        }
        return resp
    }
    
    private func clearStreamObservers(error : Error) {
        RxUtils.postErrorAndClearList(observersAcc, error: error)
        RxUtils.postErrorAndClearList(observersGyro, error: error)
        RxUtils.postErrorAndClearList(observersMagnetometer, error: error)
        RxUtils.postErrorAndClearList(observersEcg, error: error)
        RxUtils.postErrorAndClearList(observersPpg, error: error)
        RxUtils.postErrorAndClearList(observersPpi, error: error)
    }
}
