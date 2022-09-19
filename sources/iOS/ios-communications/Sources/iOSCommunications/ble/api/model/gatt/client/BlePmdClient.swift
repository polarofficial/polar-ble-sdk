
import CoreBluetooth
import Foundation
import RxSwift

public struct Pmd {
    public struct PmdSetting {
        public enum PmdSettingType: UInt8, CaseIterable {
            case sampleRate = 0
            case resolution
            case range
            case rangeMilliUnit
            case channels
            case factor
            case unknown = 0xff
        }
        
        static let mapTypeToFieldSize = [PmdSettingType.sampleRate : 2,
                                         PmdSettingType.resolution : 2,
                                         PmdSettingType.range : 2,
                                         PmdSettingType.rangeMilliUnit : 4,
                                         PmdSettingType.channels : 1,
                                         PmdSettingType.factor : 4]
        
        public var settings = [PmdSettingType : Set<UInt32>]()
        public var selected = [PmdSettingType : UInt32]()
        
        public init(_ selected: [PmdSettingType : UInt32]){
            self.selected = selected
        }
        
        public init(_ data: Data){
            self.settings = Pmd.PmdSetting.parsePmdSettingsData(data)
            self.selected = settings.reduce(into: [:]) { (result, arg1) in
                let (key, value) = arg1
                result[Pmd.PmdSetting.PmdSettingType(rawValue: UInt8(key.rawValue)) ?? Pmd.PmdSetting.PmdSettingType.unknown]=value.max()!
            }
        }
        
        static func parsePmdSettingsData(_ data: Data) -> [PmdSettingType : Set<UInt32>] {
            var offset = 0
            var settings = [PmdSettingType : Set<UInt32>]()
            while (offset+2) < data.count {
                let type = PmdSettingType(rawValue: data[offset]) ?? .unknown
                offset += 1
                let count = Int(data[offset])
                offset += 1
                let advanceStep = mapTypeToFieldSize[type] ?? data.count
                settings[type] = Set(stride(from: offset, to: offset + (count*advanceStep), by: advanceStep).map { (start) -> UInt32 in
                    let value = data.subdata(in: start..<start.advanced(by: advanceStep))
                    offset += advanceStep
                    return BlePmdClient.arrayToUInt(value, offset: 0, size: advanceStep)
                })
            }
            return settings
        }
        
        mutating func updatePmdSettingsFromStartResponse(_ data: Data) {
            let settingsFromStartResponse = Pmd.PmdSetting.parsePmdSettingsData(data)
            if let factor = settingsFromStartResponse[PmdSettingType.factor] {
                selected[PmdSettingType.factor] = factor.first!
            }
        }
        
        public func serialize() -> Data {
            return selected.reduce(into: NSMutableData()) { (result, entry) in
                if entry.key != .factor {
                    result.append([UInt8(entry.key.rawValue)], length: 1)
                    result.append([0x01], length: 1)
                    let fieldSize = UInt32(Pmd.PmdSetting.mapTypeToFieldSize[entry.key] ?? 0)
                    for i in 0..<fieldSize {
                        result.append([UInt8((entry.value >> (i*8)) & 0xff)], length: 1)
                    }
                }
            } as Data
        }
    }
    
    public enum BlePmdError: Error {
        case controlPointRequestFailed(errorCode: Int, description:String)
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
        public let sdkModeSupported: Bool
        
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
            sdkModeSupported = (data[2] & 0x02) != 0
        }
    }
    
    public enum PmdResponseCode: Int {
        case success = 0
        case errorInvalidOpCode = 1
        case errorInvalidMeasurementType = 2
        case errorNotSupported = 3
        case errorInvalidLength = 4
        case errorInvalidParameter = 5
        case errorAlreadyInState = 6
        case errorInvalidResolution = 7
        case errorInvalidSampleRate = 8
        case errorInvalidRange = 9
        case errorInvalidMTU = 10
        case errorInvalidNumberOfChannels = 11
        case errorInvalidState = 12
        case errorDeviceInCharger = 13
        case unknown_error = 0xffff
        
        var description : String {
            switch self {
            case .success: return "Success"
            case .errorInvalidOpCode: return "Invalid op code"
            case .errorInvalidMeasurementType: return "Invalid measurement type"
            case .errorNotSupported: return "Not supported"
            case .errorInvalidLength: return "Invalid length"
            case .errorInvalidParameter: return "Invalid parameter"
            case .errorAlreadyInState: return "Already in state"
            case .errorInvalidResolution: return "Invalid Resolution"
            case .errorInvalidSampleRate: return "Invalid Sample rate"
            case .errorInvalidRange: return "Invalid Range"
            case .errorInvalidMTU: return "Invalid MTU"
            case .errorInvalidNumberOfChannels: return "Invalid Number of channels"
            case .errorInvalidState: return "Invalid state"
            case .errorDeviceInCharger: return "Device in charger"
            case .unknown_error: return "unknown error"
            }
        }
    }
    
    public enum PmdMeasurementType: UInt8 {
        case ecg       = 0
        case ppg       = 1
        case acc       = 2
        case ppi       = 3
        case bioz      = 4
        case gyro      = 5
        case mgn       = 6
        case barometer = 7
        case ambient   = 8
        case sdkMode   = 9
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
            type = PmdMeasurementType(rawValue: data[2]) ?? PmdMeasurementType.unknown_type
            errorCode = PmdResponseCode(rawValue: Int(data[3])) ?? PmdResponseCode.unknown_error
            if data.count > 4 {
                more = data[4] != 0
                parameters.append(data.subdata(in: 5..<data.count))
            } else {
                more = false
            }
        }
    }
    
    public static func buildFromDeltaFrame3Axis(_ data: Data, resolution: UInt8, factor: Float, timeStamp: UInt64) ->
    (timeStamp: UInt64,samples: [(x: Float,y: Float,z: Float)]) {
        let samples = parseDeltaFramesToSamples(data, channels: 3, resolution: resolution)
        return (timeStamp, samples.map { (sample) -> (x: Float,y: Float,z: Float) in
            assert(sample.count == 3)
            let channel0 = (Float(sample[0]) * factor)
            let channel1 = (Float(sample[1]) * factor)
            let channel2 = (Float(sample[2]) * factor)
            return (channel0,channel1,channel2)
        })
    }
    
    public static func buildFromDeltaFrame3Axis(_ data: Data, resolution: UInt8, timeStamp: UInt64) -> (timeStamp: UInt64,samples: [(x: Int32,y: Int32,z: Int32)]) {
        let samples = parseDeltaFramesToSamples(data, channels: 3, resolution: resolution)
        return (timeStamp, samples.map { (sample) -> (x: Int32,y: Int32,z: Int32) in
            assert(sample.count == 3)
            return (sample[0],sample[1],sample[2])
        })
    }
    
    
    public static func buildFromDeltaFramePPG(_ data: Data, resolution: UInt8, channels: UInt8, timeStamp: UInt64) -> (timeStamp: UInt64, channels: UInt8, samples: [[Int32]]) {
        let samples = parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        return (timeStamp, channels, samples)
    }
    
    /// ACC
    public static func buildAccSamples(_ frameType: UInt8, data: Data, timeStamp: UInt64) -> (timeStamp: UInt64, samples: [(x: Int32,y: Int32,z: Int32)]) {
        let sampleSize = Int(frameType+1)
        let sampleChunk = Int(sampleSize * 3)
        return (timeStamp, stride(from: 0, to: data.count, by: sampleChunk).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: sampleChunk))
        }.map { (sample) -> (x: Int32,y: Int32,z: Int32) in
            var offset = 0
            let x = BlePmdClient.arrayToInt(sample, offset: offset, size: sampleSize)
            offset += sampleSize
            let y = BlePmdClient.arrayToInt(sample, offset: offset, size: sampleSize)
            offset += sampleSize
            let z = BlePmdClient.arrayToInt(sample, offset: offset, size: sampleSize)
            return (x,y,z)
        })
    }
    
    /// ECG
    public static func buildEcgSamples(_ type: UInt8, data: Data, timeStamp: UInt64) -> (timeStamp: UInt64, samples: [Int32]) {
        let sampleSize = type == 0 ? 3 : 2
        return (timeStamp,stride(from: 0, to: data.count, by: sampleSize).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: sampleSize))
        }.map { (content) -> Int32 in
            return BlePmdClient.arrayToInt(content, offset: 0, size: sampleSize)
        })
    }
    
    /// BIOZ
    public static func buildBiozSamples(_ data: Data, timeStamp: UInt64) -> (timeStamp: UInt64, samples: [Int32]) {
        return (timeStamp, stride(from: 0, to: data.count, by: 3).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: 3))
        }.map { (content) in
            return BlePmdClient.arrayToInt(content, offset: 0, size: 3)
        })
    }
    
    /// PPG
    public static func buildPpgSamples(_ data: Data, timeStamp: UInt64) -> (timeStamp: UInt64, channels: UInt8, samples: [[Int32]]) {
        let sampleSize = 3
        let sampleChunk = sampleSize * 4
        return (timeStamp, 4,  stride(from: 0, to: data.count, by: sampleChunk).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: sampleChunk))
        }.map { (value) -> [Int32] in
            var offset = 0
            let ppg0 = BlePmdClient.arrayToInt(value, offset: offset, size: sampleSize)
            offset += sampleSize
            let ppg1 = BlePmdClient.arrayToInt(value, offset: offset, size: sampleSize)
            offset += sampleSize
            let ppg2 = BlePmdClient.arrayToInt(value, offset: offset, size: sampleSize)
            offset += sampleSize
            let ambient = BlePmdClient.arrayToInt(value, offset: offset, size: sampleSize)
            offset += sampleSize
            return ([ppg0,ppg1,ppg2,ambient])
        })
    }
    
    /// PPI
    public static func buildPpiSamples(_ data: Data, timeStamp: UInt64) -> (timeStamp: UInt64, samples: [(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)]) {
        let PPI_SAMPLE_CHUNK = 6
        return (timeStamp, stride(from: 0, to: data.count, by: PPI_SAMPLE_CHUNK).map { (start) -> Data in
            return data.subdata(in: start..<start.advanced(by: PPI_SAMPLE_CHUNK))
        }.map { (data) -> (hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int) in
            let hr = Int(data[0])
            let ppInMs = UInt16(UInt16(data[2]) << 8 | UInt16(data[1]))
            let ppErrorEstimate = UInt16(UInt16(data[4]) << 8 | UInt16(data[3]))
            let blockerBit = Int(data[5]) & 0x01
            let skinContactStatus = (Int(data[5]) & 0x02) >> 1
            let skinContactSupported = (Int(data[5]) & 0x04) >> 2
            return ((hr: hr, ppInMs: ppInMs, ppErrorEstimate: ppErrorEstimate, blockerBit: blockerBit, skinContactStatus: skinContactStatus, skinContactSupported: skinContactSupported))
        })
    }
    
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
            return Array(stride(from: 0, to: 8, by: 1).map { (index) -> Bool in
                return (byte & (0x01 << index)) != 0
            })
        }
        
        let mask = Int32.max << Int32(bitWidth-1)
        let channelBitsLength = bitWidth*channels
        return Array(stride(from: 0, to: totalBitLength, by: UInt32.Stride(channelBitsLength)).map { (start) -> [Int32] in
            return Array(stride(from: start, to: UInt32(start+UInt32(channelBitsLength)), by: UInt32.Stride(bitWidth)).map { (subStart) -> Int32 in
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
        var offset = Int(channels*UInt8(ceil(Double(resolution)/8.0)))
        var samples = [[Int32]]()
        samples.append(contentsOf: [refSamples])
        while offset < data.count {
            let deltaSize = UInt32(data[offset])
            offset += 1
            let sampleCount = UInt32(data[offset])
            offset += 1
            let bitLength = (sampleCount*deltaSize*UInt32(channels))
            let length = Int(ceil(Double(bitLength)/8.0))
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
    public static let PMD_CP      = CBUUID(string: "FB005C81-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PMD_MTU     = CBUUID(string: "FB005C82-02E7-F387-1CAD-8ACD2D8DF0C8")
    
    private struct PmdControlPointCommands {
        static let GET_MEASUREMENT_SETTINGS: UInt8 = 0x01
        static let REQUEST_MEASUREMENT_START: UInt8 = 0x02
        static let STOP_MEASUREMENT: UInt8 = 0x03
        static let GET_SDK_MODE_SETTINGS: UInt8 = 0x04
    }
    
    let pmdCpInputQueue = AtomicList<Data>()
    var features: Data?
    
    private var observersAcc = AtomicList<RxObserver<(timeStamp: UInt64,samples: [(x: Int32,y: Int32,z: Int32)])>>()
    private var observersGyro = AtomicList<RxObserver<(timeStamp: UInt64,samples: [(x: Float,y: Float,z: Float)])>>()
    private var observersMagnetometer = AtomicList<RxObserver<(timeStamp: UInt64,samples: [(x: Float,y: Float,z: Float)])>>()
    private var observersEcg = AtomicList<RxObserver<(timeStamp: UInt64,samples: [Int32])>>()
    private var observersPpg = AtomicList<RxObserver<(timeStamp: UInt64, channels: UInt8, samples: [[Int32]])>>()
    private var observersPpi = AtomicList<RxObserver<(timeStamp: UInt64,samples: [(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)])>>()
    private var observersBioz = AtomicList<RxObserver<(timeStamp: UInt64,samples: [Int32])>>()
    private var observersFeature = AtomicList<RxObserverSingle<Pmd.PmdFeature>>()
    private var storedSettings  = AtomicType<[Pmd.PmdMeasurementType : Pmd.PmdSetting]>(initialValue: [Pmd.PmdMeasurementType : Pmd.PmdSetting]())
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BlePmdClient.PMD_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(BlePmdClient.PMD_CP)
        automaticEnableNotificationsOnConnect(chr: BlePmdClient.PMD_CP)
        automaticEnableNotificationsOnConnect(chr: BlePmdClient.PMD_MTU)
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        clearStreamObservers(error: BleGattException.gattDisconnected)
        RxUtils.postErrorOnSingleAndClearList(observersFeature, error: BleGattException.gattDisconnected)
        pmdCpInputQueue.removeAll()
        features = nil
    }
    
    fileprivate func fetchSetting(_ type: Pmd.PmdMeasurementType, setting: Pmd.PmdSetting.PmdSettingType) -> UInt32? {
        var ret: UInt32?
        storedSettings.accessItem({ (settings) in
            ret = settings[type]?.selected[setting]
        })
        return ret
    }
    
    fileprivate func fetchFactor(_ type: Pmd.PmdMeasurementType) -> Float {
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
    
    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int ) {
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
                    } else if frameType == 128 {
                        guard let resolution = fetchSetting(Pmd.PmdMeasurementType.ppg, setting: .resolution) else {
                            BleLogger.error("PPG resolution setting missing")
                            return
                        }
                        guard let channels = fetchSetting(Pmd.PmdMeasurementType.ppg, setting: .channels) else {
                            BleLogger.error("PPG channels setting missing")
                            return
                        }
                        
                        RxUtils.emitNext(observersPpg) { (emitter) in
                            let ppgSamples = Pmd.buildFromDeltaFramePPG(samples, resolution: UInt8(resolution), channels:
                                                                            UInt8(channels), timeStamp: timeStamp)
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
                    } else if frameType == 128 {
                        // delta framed
                        let factor = fetchFactor(Pmd.PmdMeasurementType.acc)
                        guard let resolution = fetchSetting(Pmd.PmdMeasurementType.acc, setting: .resolution) else {
                            BleLogger.error("ACC resolution setting missing")
                            return
                        }
                        RxUtils.emitNext(observersAcc) { (emitter) in
                            let accSamples = Pmd.buildFromDeltaFrame3Axis(samples, resolution:
                                                                            UInt8(resolution), factor: factor * 1000.0, timeStamp: timeStamp)
                            emitter.obs.onNext((accSamples.timeStamp,accSamples.samples.map { (arg0) -> (x:Int32, y: Int32, z: Int32) in
                                let (x, y, z) = arg0
                                return (Int32(x),Int32(y),Int32(z))
                            }))
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
                case Pmd.PmdMeasurementType.mgn.rawValue:
                    if frameType == 128 {
                        let factor = fetchFactor(Pmd.PmdMeasurementType.mgn)
                        guard let resolution = fetchSetting(Pmd.PmdMeasurementType.mgn, setting: .resolution) else {
                            BleLogger.error("Unknown MGN resolution setting missing")
                            return
                        }
                        RxUtils.emitNext(observersMagnetometer) { (emitter) in
                            let samples = Pmd.buildFromDeltaFrame3Axis(samples, resolution:
                                                                        UInt8(resolution), factor: factor, timeStamp: timeStamp)
                            emitter.obs.onNext(samples)
                        }
                    } else {
                        BleLogger.error("Unknown MAGNETOMETER frame received")
                    }
                case Pmd.PmdMeasurementType.gyro.rawValue:
                    if frameType == 128 {
                        let factor = fetchFactor(Pmd.PmdMeasurementType.gyro)
                        guard let resolution = fetchSetting(Pmd.PmdMeasurementType.gyro, setting: .resolution) else {
                            BleLogger.error("GYRO resolution setting missing")
                            return
                        }
                        RxUtils.emitNext(observersGyro) { (emitter) in
                            let samples = Pmd.buildFromDeltaFrame3Axis(samples, resolution:
                                                                        UInt8(resolution), factor: factor, timeStamp: timeStamp)
                            emitter.obs.onNext(samples)
                        }
                    } else {
                        BleLogger.error("Unknown GYRO frame received")
                    }
                default:
                    BleLogger.trace("Non handled stream type received")
                }
            } else {
                BleLogger.error("PMD MTU attribute error: \(err)")
            }
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
    
    public func observeAcc() -> Observable<(timeStamp: UInt64,samples: [(x: Int32,y: Int32,z: Int32)])> {
        return RxUtils.monitor(observersAcc, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observeGyro() -> Observable<(timeStamp: UInt64,samples: [(x: Float,y: Float,z: Float)])> {
        return RxUtils.monitor(observersGyro, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observeMagnetometer() -> Observable<(timeStamp: UInt64,samples: [(x: Float,y: Float,z: Float)])> {
        return RxUtils.monitor(observersMagnetometer, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observeEcg() -> Observable<(timeStamp: UInt64,samples: [Int32])> {
        return RxUtils.monitor(observersEcg, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observePpg() -> Observable<(timeStamp: UInt64, channels: UInt8, samples: [[Int32]])> {
        return RxUtils.monitor(observersPpg, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observePpi() -> Observable<(timeStamp: UInt64,samples: [(hr: Int, ppInMs: UInt16, ppErrorEstimate: UInt16, blockerBit: Int, skinContactStatus: Int, skinContactSupported: Int)])> {
        return RxUtils.monitor(observersPpi, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func observeBioz() -> Observable<(timeStamp: UInt64,samples: [Int32])> {
        return RxUtils.monitor(observersBioz, transport: gattServiceTransmitter, checkConnection: true)
    }
    
    public func startMeasurement(_ type: Pmd.PmdMeasurementType, settings: Pmd.PmdSetting) -> Completable {
        storedSettings.accessItem { (settingsStored) in
            settingsStored[type] = settings
        }
        return Completable.create { observer in
            do {
                let serialized = settings.serialize()
                var packet = Data([PmdControlPointCommands.REQUEST_MEASUREMENT_START, type.rawValue])
                packet.append(serialized)
                let cpResponse = try self.sendControlPointCommand(packet as Data)
                if cpResponse.errorCode == .success {
                    self.storedSettings.accessItem { (settingsStored) in
                        settingsStored[type]?.updatePmdSettingsFromStartResponse(cpResponse.parameters as Data)
                    }
                    observer(.completed)
                } else {
                    observer(.error(Pmd.BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create {
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    public func stopMeasurement(_ type: Pmd.PmdMeasurementType) -> Completable {
        return Completable.create{ observer in
            do {
                let packet = Data([PmdControlPointCommands.STOP_MEASUREMENT ,type.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    observer(.completed)
                } else {
                    observer(.error(Pmd.BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
                }
            } catch let err {
                observer(.error(err))
            }
            return Disposables.create{}
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    public func readFeature(_ checkConnection: Bool) -> Single<Pmd.PmdFeature> {
        return Single.create { observer in
            let object = RxObserverSingle<Pmd.PmdFeature>(obs: observer)
            
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
                self.observersFeature.remove(
                    { (item) -> Bool in
                        return item === object
                    }
                )
            }
        }
    }
    
    public override func clientReady(_ checkConnection: Bool) -> Completable {
        return waitNotificationEnabled(BlePmdClient.PMD_CP, checkConnection: checkConnection)
            .andThen(waitNotificationEnabled(BlePmdClient.PMD_MTU, checkConnection: checkConnection))
    }
    
    public func querySettings(_ setting: Pmd.PmdMeasurementType) -> Single<Pmd.PmdSetting> {
        return Single.create{ [unowned self] observer in
            do {
                let packet = Data([PmdControlPointCommands.GET_MEASUREMENT_SETTINGS, setting.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    let query = Pmd.PmdSetting(cpResponse.parameters as Data)
                    observer(.success(query))
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
    
    public func queryFullSettings(_ setting: Pmd.PmdMeasurementType) -> Single<Pmd.PmdSetting> {
        return Single.create{ [unowned self] observer in
            do {
                let packet = Data([PmdControlPointCommands.GET_SDK_MODE_SETTINGS, setting.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success {
                    let query = Pmd.PmdSetting(cpResponse.parameters as Data)
                    observer(.success(query))
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
                let packet = Data([PmdControlPointCommands.REQUEST_MEASUREMENT_START, Pmd.PmdMeasurementType.sdkMode.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success || cpResponse.errorCode == .errorAlreadyInState {
                    observer(.completed)
                } else {
                    observer(.error(Pmd.BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
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
                let packet = Data([PmdControlPointCommands.STOP_MEASUREMENT, Pmd.PmdMeasurementType.sdkMode.rawValue])
                let cpResponse = try self.sendControlPointCommand(packet)
                if cpResponse.errorCode == .success || cpResponse.errorCode == .errorAlreadyInState{
                    observer(.completed)
                } else {
                    observer(.error(Pmd.BlePmdError.controlPointRequestFailed(errorCode: cpResponse.errorCode.rawValue, description: cpResponse.errorCode.description)))
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
    
    private func sendControlPointCommand(_ data: Data) throws -> Pmd.PmdControlPointResponse {
        guard let transport = self.gattServiceTransmitter else {
            throw BleGattException.gattTransportNotAvailable
        }
        try transport.transmitMessage(self, serviceUuid: BlePmdClient.PMD_SERVICE, characteristicUuid: BlePmdClient.PMD_CP, packet: data, withResponse: true)
        let response = try self.pmdCpInputQueue.poll(60)
        let resp = Pmd.PmdControlPointResponse(response)
        var more = resp.more
        while (more) {
            let parameters = try self.pmdCpInputQueue.poll(60)
            more = parameters[0] != 0
            resp.parameters.append(parameters.subdata(in: 1..<parameters.count))
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
        RxUtils.postErrorAndClearList(observersBioz, error: error)
    }
}
