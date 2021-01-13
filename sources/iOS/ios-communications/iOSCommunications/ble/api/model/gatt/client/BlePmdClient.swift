
import CoreBluetooth
import Foundation
import RxSwift

public struct Pmd {
    public struct PmdSetting {
        public enum PmdSettingType: UInt8, CaseIterable {
            case sampleRate = 0
            case resolution = 1
            case range = 2
            case unknown = 0xff
        }
        
        public var settings  = [PmdSettingType : Set<UInt16>]()
        
        public init(_ settings: [PmdSettingType : Set<UInt16>]){
            self.settings = settings
        }
        
        public init(_ data: Data){
            var offset = 0
            while (offset < data.count){
                let type = PmdSettingType.init(rawValue: data[offset]) ?? .unknown
                offset += 1
                let count = Int(data[offset])
                offset += 1
                settings[type] = Set(stride(from: offset, to: offset + (count*2), by: 2).map { (start) -> UInt16 in
                    let value = data.subdata(in: start..<start.advanced(by: 2))
                    offset += 2
                    return UInt16(value[0]) | UInt16(value[1]) << 8
                })
            }
        }
        
        func serialize() -> Data {
            return settings.reduce(into: NSMutableData()) { (result, entry) in
                result.append([UInt8(entry.key.rawValue)], length: 1)
                result.append([UInt8(entry.value.count)], length: 1)
                for item in entry.value {
                    result.append([UInt8(item),UInt8(item >> 8)], length: 2)
                }
            } as Data
        }

        func selectedSampleSize() -> Int {
            let resolution = settings[.resolution]?.max() ?? 8
            return Int(ceil(Double(resolution)/8.0))
        }
    }

    public enum BlePmdError: Error {
        case controlPointRequestFailed(errorCode: Int)
    }
    
    public struct PmdFeature {
        public let ecgSupported: Bool
        public let ppgSupported: Bool
        public let accSupported: Bool
        public let ppiSupported: Bool
        public let bioZSupported: Bool
        public let gyroSupported: Bool
        public let magnetometerSupported: Bool
        public let barometerSupported: Bool
        public let ambientSupported: Bool
        
        public init(_ data: Data) {
            ecgSupported = (data[1] & 0x01) != 0
            ppgSupported = (data[1] & 0x02) != 0
            accSupported = (data[1] & 0x04) != 0
            ppiSupported = (data[1] & 0x08) != 0
            bioZSupported = (data[1] & 0x10) != 0
            gyroSupported = (data[1] & 0x20) != 0
            magnetometerSupported = (data[1] & 0x40) != 0
            barometerSupported = (data[1] & 0x80) != 0
            ambientSupported = (data[2] & 0x01) != 0
        }
    }
    
    public enum PmdResponseCode: Int {
        case success = 0
        case errorInvalidOpCode = 1
        case errorInvalidMeasurementType = 2
        case errorNotSupported = 3
        case errorInvalidLength = 4
        case errorInvalidParameter = 5
        case errorInvalidState = 6
        case unknown_error = 0xffff
    }
    
    public enum PmdMeasurementType: UInt8{
        case ecg       = 0
        case ppg       = 1
        case acc       = 2
        case ppi       = 3
        case bioz      = 4
        case gyro      = 5
        case mgn       = 6
        case barometer = 7
        case ambient   = 8
        case unknown_type = 0xff
    }
    
    public struct PmdControlPointResponse {
        public let response: UInt8
        public let opCode: UInt8
        public let type: PmdMeasurementType
        public let errorCode: PmdResponseCode
        public let more: Bool
        public let parameters = NSMutableData()
        public init(_ data: Data) {
            response = data[0]
            opCode = data[1]
            type = PmdMeasurementType.init(rawValue: data[2]) ?? PmdMeasurementType.unknown_type
            errorCode = PmdResponseCode.init(rawValue: Int(data[3])) ?? PmdResponseCode.unknown_error
            if data.count > 4 {
                more = data[4] != 0
                parameters.append(data.subdata(in: 5..<data.count))
            } else {
                more = false
            }
        }
    }
    
    /// ACC
    public static func buildAccSamples(_ frameType: UInt8, data: Data, timeStamp: UInt64) -> (UInt64,[(x: Int32,y: Int32,z: Int32)]) {
        let sampleSize = Int(frameType+1)
        let sampleChunk = Int(sampleSize * 3)
        var samples = [(Int32,Int32,Int32)]()
        stride(from: 0, to: data.count, by: sampleChunk).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: sampleChunk))
            }.forEach { (sample) in
                var offset = 0
                let x = BlePmdClient.arrayToInt(sample, offset: offset, size: sampleSize)
                offset += sampleSize
                let y = BlePmdClient.arrayToInt(sample, offset: offset, size: sampleSize)
                offset += sampleSize
                let z = BlePmdClient.arrayToInt(sample, offset: offset, size: sampleSize)
                samples.append((x,y,z))
        }
        return (timeStamp,samples)
    }
    
    /// ECG
    static func buildEcgSamples(_ type: UInt8, data: Data, timeStamp: UInt64) -> (UInt64,[Int32]) {
        let sampleSize = type == 0 ? 3 : 2
        var samples = [Int32]()
        stride(from: 0, to: data.count, by: sampleSize).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: sampleSize))
            }.forEach { (content) in
                let sample = BlePmdClient.arrayToInt(content, offset: 0, size: sampleSize)
                samples.append(sample)
        }
        return (timeStamp,samples)
    }
    
    /// BIOZ
    static func buildBiozSamples(_ data: Data, timeStamp: UInt64) -> (UInt64,[Int32]) {
        var samples = [Int32]()
        stride(from: 0, to: data.count, by: 3).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: 3))
            }.forEach { (content) in
                let sample = BlePmdClient.arrayToInt(content, offset: 0, size: 3)
                samples.append(sample)
        }
        return (timeStamp,samples)
    }
    
    /// PPG
    static func buildPpgSamples(_ data: Data, timeStamp: UInt64) -> (UInt64,[(ppg0: Int32,ppg1: Int32,ppg2: Int32,ambient: Int32)]) {
        let sampleSize = 3
        let sampleChunk = sampleSize * 4
        var samples = [(Int32,Int32,Int32,Int32)]()
        stride(from: 0, to: data.count, by: sampleChunk).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: sampleChunk))
            }.forEach { (value) in
                var offset = 0
                let ppg0 = BlePmdClient.arrayToInt(value, offset: offset, size: sampleSize)
                offset += sampleSize
                let ppg1 = BlePmdClient.arrayToInt(value, offset: offset, size: sampleSize)
                offset += sampleSize
                let ppg2 = BlePmdClient.arrayToInt(value, offset: offset, size: sampleSize)
                offset += sampleSize
                let ambient = BlePmdClient.arrayToInt(value, offset: offset, size: sampleSize)
                offset += sampleSize
                samples.append((ppg0,ppg1,ppg2,ambient))
        }
        return (timeStamp,samples)
    }
    
    /// PPI
    static func buildPpiSamples(_ data: Data, timeStamp: UInt64) -> (UInt64,[(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)]) {
        var samples = [(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)]()
        let PPI_SAMPLE_CHUNK = 6
        stride(from: 0, to: data.count, by: PPI_SAMPLE_CHUNK).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: PPI_SAMPLE_CHUNK))
            }.forEach { (data) in
                let hr = Int(data[0])
                let ppInMs = UInt16(UInt16(data[2]) << 8 | UInt16(data[1]))
                let ppErrorEstimate = UInt16(UInt16(data[4]) << 8 | UInt16(data[3]))
                let blockerBit = Int(data[5]) & 0x01
                let skinContactStatus = (Int(data[5]) & 0x02) >> 1
                let skinContactSupported = (Int(data[5]) & 0x04) >> 2
                samples.append((hr: hr, ppInMs: ppInMs, ppErrorEstimate: ppErrorEstimate, blockerBit: blockerBit, skinContactStatus: skinContactStatus, skinContactSupported: skinContactSupported))
        }
        return (timeStamp,samples)
    }
}

/// client declaration
public class BlePmdClient: BleGattClientBase {

    public static let PMD_SERVICE = CBUUID(string: "FB005C80-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PMD_CP      = CBUUID(string: "FB005C81-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PMD_MTU     = CBUUID(string: "FB005C82-02E7-F387-1CAD-8ACD2D8DF0C8")

    let pmdCpInputQueue = AtomicList<Data>()
    var features: Data?
    var pmdCpEnabled: AtomicInteger!
    var observersAcc = AtomicList<RxObserver<(timeStamp: UInt64,samples: [(x: Int32,y: Int32,z: Int32)])>>()
    var observersEcg = AtomicList<RxObserver<(timeStamp: UInt64,samples: [Int32])>>()
    var observersPpg = AtomicList<RxObserver<(timeStamp: UInt64,samples: [(ppg0: Int32,ppg1: Int32,ppg2: Int32,ambient: Int32)])>>()
    var observersPpi = AtomicList<RxObserver<(timeStamp: UInt64,samples: [(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)])>>()
    var observersBioz = AtomicList<RxObserver<(timeStamp: UInt64,samples: [Int32])>>()
    var observersFeature = AtomicList<RxObserverSingle<Pmd.PmdFeature>>()
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BlePmdClient.PMD_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(BlePmdClient.PMD_CP)
        addCharacteristicNotification(BlePmdClient.PMD_CP)
        addCharacteristicNotification(BlePmdClient.PMD_MTU)
        pmdCpEnabled = notificationAtomicInteger(BlePmdClient.PMD_CP)
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        RxUtils.postErrorAndClearList(observersAcc, error: BleGattException.gattDisconnected)
        RxUtils.postErrorAndClearList(observersEcg, error: BleGattException.gattDisconnected)
        RxUtils.postErrorAndClearList(observersPpg, error: BleGattException.gattDisconnected)
        RxUtils.postErrorAndClearList(observersPpi, error: BleGattException.gattDisconnected)
        RxUtils.postErrorAndClearList(observersBioz, error: BleGattException.gattDisconnected)
        RxUtils.postErrorOnSingleAndClearList(observersFeature, error: BleGattException.gattDisconnected)
        pmdCpInputQueue.removeAll()
        features = nil
    }
    
    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int ){
        if chr.isEqual(BlePmdClient.PMD_CP) {
            if err == 0 {
                if data[0] == 0x0f {
                    features = data
                    RxUtils.emitNext(observersFeature) { (observer) in
                        observer.obs(.success(Pmd.PmdFeature(data)))
                    }
                } else {
                    self.pmdCpInputQueue.push(data)
                }
            } else {
                BleLogger.error("PMD CP attribute error: \(err)")
            }
        } else if chr.isEqual(BlePmdClient.PMD_MTU) {
            if err == 0 {
                let timeBytes = data.subdata(in: 1..<9) as NSData
                var timeStamp: UInt64 = 0
                memcpy(&timeStamp,timeBytes.bytes,8)
                let frameType = data[9]
                let samples = data.subdata(in: 10..<data.count)
                switch (data[0] ) {
                case Pmd.PmdMeasurementType.ecg.rawValue:
                    if frameType <= 2 {
                        RxUtils.emitNext(observersEcg) { (emitter) in
                            let ecgSamples = Pmd.buildEcgSamples(frameType, data: samples, timeStamp: timeStamp)
                            emitter.obs.onNext(ecgSamples)
                        }
                    } else {
                        BleLogger.error("Unknown ECG frame received")
                    }
                case Pmd.PmdMeasurementType.ppg.rawValue:
                    if frameType == 0 {
                        RxUtils.emitNext(observersPpg) { (emitter) in
                            let ppgSamples = Pmd.buildPpgSamples(samples, timeStamp: timeStamp)
                            emitter.obs.onNext(ppgSamples)
                        }
                    } else {
                        BleLogger.error("Unknown PPG frame received")
                    }
                case Pmd.PmdMeasurementType.acc.rawValue:
                    if frameType <= 2 {
                        RxUtils.emitNext(observersAcc) { (emitter) in
                            let accSamples = Pmd.buildAccSamples(frameType, data: samples, timeStamp: timeStamp)
                            emitter.obs.onNext(accSamples)
                        }
                    } else {
                        BleLogger.error("Unknown ACC frame received")
                    }
                case Pmd.PmdMeasurementType.ppi.rawValue:
                    if frameType == 0 {
                        RxUtils.emitNext(observersPpi) { (emitter) in
                            let ppiSamples = Pmd.buildPpiSamples(samples, timeStamp: timeStamp)
                            emitter.obs.onNext(ppiSamples)
                        }
                    } else {
                        BleLogger.error("Unknown PPI frame received")
                    }
                case Pmd.PmdMeasurementType.bioz.rawValue:
                    if frameType == 0 {
                        RxUtils.emitNext(observersBioz) { (emitter) in
                            let biozSamples = Pmd.buildBiozSamples(samples, timeStamp: timeStamp)
                            emitter.obs.onNext(biozSamples)
                        }
                    } else {
                        BleLogger.error("Unknown BIOZ frame received")
                    }
                default:
                    BleLogger.trace("Non handled stream type received")
                }
            } else {
                BleLogger.error("PMD MTU attribute error: \(err)")
            }
        }
    }
    
    static func arrayToInt(_ data: Data, offset: Int, size: Int) -> Int32 {
        let result = data.subdata(in: offset..<(offset+size)) as NSData
        var value: Int32 = 0
        memcpy(&value,result.bytes,size)
        switch size {
        case 3:
            if (value & 0x00800000) != 0 {
                value |= (Int32(0xFF) << 24)
            }
        case 2:
            if (value & 0x00008000) != 0 {
                value |= (Int32(0xFFFF) << 16)
            }
        default:
            if (value & 0x00000080) != 0 {
                value |= (Int32(0xFFFFFF) << 8)
            }
        }
        return value
    }
    
    public func observeAcc() -> Observable<(timeStamp: UInt64,samples: [(x: Int32,y: Int32,z: Int32)])> {
        return RxUtils.monitor(observersAcc, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observeEcg() -> Observable<(timeStamp: UInt64,samples: [Int32])> {
        return RxUtils.monitor(observersEcg, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observePpg() -> Observable<(timeStamp: UInt64,samples: [(ppg0: Int32,ppg1: Int32,ppg2: Int32,ambient: Int32)])> {
        return RxUtils.monitor(observersPpg, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observePpi() -> Observable<(timeStamp: UInt64,samples: [(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)])> {
        return RxUtils.monitor(observersPpi, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observeBioz() -> Observable<(timeStamp: UInt64,samples: [Int32])> {
        return RxUtils.monitor(observersBioz, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func startMeasurement(_ type: Pmd.PmdMeasurementType, settings: Pmd.PmdSetting) -> Completable {
        return Completable.create{ observer in
            do {
                let serialized = settings.serialize()
                var packet = Data([0x02,type.rawValue])
                packet.append(serialized)
                let cpResponse = try self.sendControlPointCommand(packet as Data)
                if cpResponse.errorCode == .success {
                    observer(.completed)
                } else {
                    observer(.error(Pmd.BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue)))
                }
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create{
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }

    public func stopMeasurement(_ type: Pmd.PmdMeasurementType) -> Completable {
        return Completable.create{ observer in
            do {
                let packet = Data([0x03,type.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    observer(.completed)
                } else {
                    observer(.error(Pmd.BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue)))
                }
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create{
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    public func readFeature(_ checkConnection: Bool) -> Single<Pmd.PmdFeature> {
        var object: RxObserverSingle<Pmd.PmdFeature>!
        return Single.create{ observer in
            object = RxObserverSingle<Pmd.PmdFeature>.init(obs: observer)
            if let f = self.features {
                // available
                observer(.success(Pmd.PmdFeature(f)))
            } else {
                if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                    self.observersFeature.append(object)
                } else {
                    observer(.failure(BleGattException.gattDisconnected))
                }
            }
            return Disposables.create {
                self.observersFeature.remove({ (item) -> Bool in
                    return item === object
                })
            }
        }
    }
    
    public override func clientReady(_ checkConnection: Bool) -> Completable {
        return waitNotificationEnabled(BlePmdClient.PMD_CP, checkConnection: checkConnection).andThen(waitNotificationEnabled(BlePmdClient.PMD_MTU, checkConnection: checkConnection))
    }
    
    public func querySettings(_ setting: Pmd.PmdMeasurementType) -> Single<Pmd.PmdSetting> {
        return Single.create{ observer in
            do {
                let packet = Data([0x01,setting.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    let query = Pmd.PmdSetting(cpResponse.parameters as Data)
                    observer(.success(query))
                } else {
                    observer(.failure(BleGattException.gattAttributeError(errorCode: cpResponse.errorCode.rawValue)))
                }
            } catch let err {
                observer(.failure(err))
            }
            return Disposables.create{
                // do nothing
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    private func sendControlPointCommand(_ data: Data) throws -> Pmd.PmdControlPointResponse {
        guard let transport = self.gattServiceTransmitter else {
            throw BleGattException.gattTransportNotAvailable
        }
        try transport.transmitMessage(self, serviceUuid: BlePmdClient.PMD_SERVICE, characteristicUuid: BlePmdClient.PMD_CP, packet: data, withResponse: true)
        let response = try self.pmdCpInputQueue.poll(60)
        let resp = Pmd.PmdControlPointResponse(response)
        var more = resp.more
        while(more){
            let parameters = try self.pmdCpInputQueue.poll(60)
            more = parameters[0] != 0
            resp.parameters.append(parameters.subdata(in: 1..<parameters.count))
        }
        return resp
    }
}
