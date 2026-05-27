import CoreBluetooth
import Foundation
import Combine

public struct Pmd {

    static func parseDeltaFrameRefSamples(_ data: Data, channels: UInt8, resolution: UInt8) -> [Int32] {
        let resolutionInBytes = Int(ceil(Double(resolution) / 8.0))
        return Array(stride(from: 0, to: (resolutionInBytes * Int(channels)), by: resolutionInBytes).map { start -> Int32 in
            return BlePmdClient.arrayToInt(data.subdata(in: start..<start.advanced(by: resolutionInBytes)), offset: 0, size: resolutionInBytes)
        })
    }


    static func parseDeltaFrame(_ data: Data, channels: UInt32, bitWidth: UInt32, totalBitLength: UInt32) -> [[Int32]] {
        // Guard against bitWidth == 0 to prevent UInt32 underflow and Int32 overflow crash.
        guard bitWidth > 0 else {
            BleLogger.error("parseDeltaFrame() bitWidth is 0, skipping frame as data may be malformed")
            return []
        }
        let dataInBits = data.flatMap { byte -> [Bool] in
            return Array(stride(from: 0, to: 8, by: 1).map { (byte & (0x01 << $0)) != 0 })
        }
        let mask = Int32.max << Int32(bitWidth - 1)
        let channelBitsLength = bitWidth * channels
        return Array(stride(from: 0, to: totalBitLength, by: UInt32.Stride(channelBitsLength)).map { start -> [Int32] in
            return Array(stride(from: start, to: start + UInt32(channelBitsLength), by: UInt32.Stride(bitWidth)).map { subStart -> Int32 in
                let bits: ArraySlice<Bool> = dataInBits[Int(subStart)..<Int(subStart + UInt32(bitWidth))]
                var sample: Int32 = 0
                bits.enumerated().forEach { i, bit in sample |= (Int32(bit ? 1 : 0) << i) }
                if (sample & mask) != 0 { sample |= mask }
                return sample
            })
        })
    }

    static func parseDeltaFramesToSamples(_ data: Data, channels: UInt8, resolution: UInt8) -> [[Int32]] {
        let refSamples = parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        var offset = Int(channels * UInt8(ceil(Double(resolution) / 8.0)))
        var samples = [[Int32]]()
        samples.append(refSamples)
        while offset < data.count {
            guard offset + 2 <= data.count else {
                BleLogger.error("parseDeltaFramesToSamples() truncated header. offset=\(offset)")
                return samples
            }
            let deltaSize = UInt32(data[offset]); offset += 1
            let sampleCount = UInt32(data[offset]); offset += 1
            // Guard against deltaSize == 0 to prevent crash in parseDeltaFrame.
            guard deltaSize > 0 else {
                BleLogger.error("parseDeltaFramesToSamples() deltaSize is 0, skipping frame as data may be malformed")
                continue
            }
            let bitLength = sampleCount * deltaSize * UInt32(channels)
            let length = Int(ceil(Double(bitLength) / 8.0))
            guard offset + length <= data.count else {
                BleLogger.error("parseDeltaFramesToSamples() data too short")
                return samples
            }
            let frame = data.subdata(in: offset..<(offset + length))
            let deltas = parseDeltaFrame(frame, channels: UInt32(channels), bitWidth: deltaSize, totalBitLength: bitLength)
            offset += length
            for delta in deltas {
                var nextSamples = [Int32]()
                let last = samples.last!
                var overflow = false
                for i in 0..<Int(channels) {
                    let sum = last[i].addingReportingOverflow(delta[i])
                    if sum.overflow { overflow = true; break }
                    nextSamples.append(sum.partialValue)
                }
                if !overflow && nextSamples.count == Int(channels) { samples.append(nextSamples) }
            }
        }
        return samples
    }
}

open class BlePmdClient: BleGattClientBase, @unchecked Sendable {

    struct PmdTimestampData: Hashable {
        let measurementType: PmdMeasurementType
        let frameType: PmdDataFrameType
    }

    public static let PMD_SERVICE = CBUUID(string: "FB005C80-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PMD_CP      = CBUUID(string: "FB005C81-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PMD_DATA    = CBUUID(string: "FB005C82-02E7-F387-1CAD-8ACD2D8DF0C8")

    let pmdCpResponseQueue = AtomicList<Data>()
    var features: Data?

    // MARK: - Streams
    private let accStreams         = StreamContinuationList<AccData>()
    private let gyroStreams        = StreamContinuationList<GyrData>()
    private let magStreams         = StreamContinuationList<MagData>()
    private let ecgStreams         = StreamContinuationList<EcgData>()
    private let ppgStreams         = StreamContinuationList<PpgData>()
    private let ppiStreams         = StreamContinuationList<PpiData>()
    private let tempStreams        = StreamContinuationList<TemperatureData>()
    private let skinTempStreams    = StreamContinuationList<SkinTemperatureData>()
    private let pressureStreams    = StreamContinuationList<PressureData>()

    // Feature waiters
    private let featureWaiters = StreamContinuationList<Set<PmdMeasurementType>>()

    private var storedSettings  = AtomicType<[PmdMeasurementType: PmdSetting]>(initialValue: [:])
    private var previousTimeStamp = AtomicType<[PmdTimestampData: UInt64]>(initialValue: [:])

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BlePmdClient.PMD_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(BlePmdClient.PMD_CP)
        automaticEnableNotificationsOnConnect(chr: BlePmdClient.PMD_CP)
        automaticEnableNotificationsOnConnect(chr: BlePmdClient.PMD_DATA)
    }

    override public func disconnected() {
        super.disconnected()
        clearStreamObservers(error: BleGattException.gattDisconnected)
        featureWaiters.finish(throwing: BleGattException.gattDisconnected)
        pmdCpResponseQueue.removeAll()
        features = nil
        previousTimeStamp.set([:])
    }

    fileprivate func clearStreamObservers(error: Error) {
        accStreams.finish(throwing: error)
        gyroStreams.finish(throwing: error)
        magStreams.finish(throwing: error)
        ecgStreams.finish(throwing: error)
        ppgStreams.finish(throwing: error)
        ppiStreams.finish(throwing: error)
        tempStreams.finish(throwing: error)
        skinTempStreams.finish(throwing: error)
        pressureStreams.finish(throwing: error)
    }

    fileprivate func fetchSetting(_ type: PmdMeasurementType, setting: PmdSetting.PmdSettingType) -> UInt32? {
        var value: UInt32?
        storedSettings.accessItem { value = $0[type]?.selected[setting] }
        return value
    }

    fileprivate func getFactor(_ type: PmdMeasurementType) -> Float {
        var factor: UInt32?
        storedSettings.accessItem { factor = $0[type]?.selected[.factor] }
        if let v = factor { return Float(bitPattern: v) }
        BleLogger.error("Factor not stored to settings. Using 1.0f as default.")
        return 1.0
    }

    private func getSampleRate(_ type: PmdMeasurementType) -> UInt {
        return UInt(fetchSetting(type, setting: .sampleRate) ?? 0)
    }

    private func getPreviousFrameTimeStamp(_ type: PmdMeasurementType, _ frameType: PmdDataFrameType) -> UInt64 {
        var ts: UInt64?
        previousTimeStamp.accessItem { ts = $0[PmdTimestampData(measurementType: type, frameType: frameType)] }
        return ts ?? 0
    }

    private func setPreviousFrameTimeStamp(_ type: PmdMeasurementType, _ frameType: PmdDataFrameType, _ ts: UInt64) {
        previousTimeStamp.accessItem { $0[PmdTimestampData(measurementType: type, frameType: frameType)] = ts }
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if chr.isEqual(BlePmdClient.PMD_CP) {
            if err == 0 {
                if data[0] == 0x0f {
                    features = data
                    let types = PmdMeasurementType.fromByteArray(data)
                    featureWaiters.yield(types)
                    featureWaiters.finish()
                } else {
                    processCpCommand(data)
                }
            } else {
                BleLogger.error("PMD CP attribute error: \(err)")
            }
        } else if chr.isEqual(BlePmdClient.PMD_DATA) {
            if err == 0 { processPmdData(data) }
            else { BleLogger.error("PMD MTU attribute error: \(err)") }
        }
    }

    private func processCpCommand(_ data: Data) {
        BleLogger.trace_hex("PMD command: ", data: data)
        guard !data.isEmpty else { BleLogger.error("PMD CP received empty data"); return }
        if data[0] != PmdControlPointResponse.CONTROL_POINT_RESPONSE_CODE {
            switch data[0] {
            case PmdControlPointCommandServiceToClient.ONLINE_MEASUREMENT_STOPPED:
                for dataType in data.dropFirst() {
                    let err = BlePmdError.bleOnlineStreamClosed(description: "Stop command from device")
                    switch PmdMeasurementType.fromId(id: dataType) {
                    case .ecg:          ecgStreams.finish(throwing: err)
                    case .ppg:          ppgStreams.finish(throwing: err)
                    case .acc:          accStreams.finish(throwing: err)
                    case .ppi:          ppiStreams.finish(throwing: err)
                    case .gyro:         gyroStreams.finish(throwing: err)
                    case .mgn:          magStreams.finish(throwing: err)
                    case .temperature:  tempStreams.finish(throwing: err)
                    case .skinTemperature: skinTempStreams.finish(throwing: err)
                    case .pressure:     pressureStreams.finish(throwing: err)
                    default: BleLogger.error("PMD CP, not supported type for stop: \(dataType)")
                    }
                }
            default:
                BleLogger.error("PMD CP, not supported CP command: \(data[0])")
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
        setPreviousFrameTimeStamp(frame.measurementType, frame.frameType, frame.timeStamp)
        switch frame.measurementType {
        case .ecg:
            do { ecgStreams.yield(try EcgData.parseDataFromDataFrame(frame: frame)) } catch { ecgStreams.finish(throwing: error) }
        case .ppg:
            do { ppgStreams.yield(try PpgData.parseDataFromDataFrame(frame: frame)) } catch { ppgStreams.finish(throwing: error) }
        case .acc:
            do { accStreams.yield(try AccData.parseDataFromDataFrame(frame: frame)) } catch { accStreams.finish(throwing: error) }
        case .ppi:
            do { ppiStreams.yield(try PpiData.parseDataFromDataFrame(frame: frame)) } catch { ppiStreams.finish(throwing: error) }
        case .mgn:
            do { magStreams.yield(try MagData.parseDataFromDataFrame(frame: frame)) } catch { magStreams.finish(throwing: error) }
        case .gyro:
            do { gyroStreams.yield(try GyrData.parseDataFromDataFrame(frame: frame)) } catch { gyroStreams.finish(throwing: error) }
        case .temperature:
            do { tempStreams.yield(try TemperatureData.parseDataFromDataFrame(frame: frame)) } catch { tempStreams.finish(throwing: error) }
        case .skinTemperature:
            do { skinTempStreams.yield(try SkinTemperatureData.parseDataFromDataFrame(frame: frame)) } catch { skinTempStreams.finish(throwing: error) }
        case .pressure:
            do { pressureStreams.yield(try PressureData.parseDataFromDataFrame(frame: frame)) } catch { pressureStreams.finish(throwing: error) }
        default:
            BleLogger.trace("Non handled stream type received")
        }
    }

    static func arrayToUInt(_ data: Data, offset: Int, size: Int) -> UInt32 {
        var value: UInt32 = 0; assert(size <= 4)
        memcpy(&value, (data.subdata(in: offset..<(offset + size)) as NSData).bytes, size)
        return value
    }

    static func arrayToInt(_ data: Data, offset: Int, size: Int) -> Int32 {
        var value: Int32 = 0; assert(size <= 4)
        memcpy(&value, (data.subdata(in: offset..<(offset + size)) as NSData).bytes, size)
        let mask = (Int32.max << ((size * 8) - 1))
        if (value & mask) != 0 { value |= mask }
        return value
    }

    // MARK: - Public observe streams

    public func observeAcc() -> AsyncThrowingStream<AccData, Error> {
        return accStreams.makeStream(transport: gattServiceTransmitter, checkConnection: true)
    }
    public func observeGyro() -> AsyncThrowingStream<GyrData, Error> {
        return gyroStreams.makeStream(transport: gattServiceTransmitter, checkConnection: true)
    }
    public func observeMagnetometer() -> AsyncThrowingStream<MagData, Error> {
        return magStreams.makeStream(transport: gattServiceTransmitter, checkConnection: true)
    }
    public func observeEcg() -> AsyncThrowingStream<EcgData, Error> {
        return ecgStreams.makeStream(transport: gattServiceTransmitter, checkConnection: true)
    }
    public func observePpg() -> AsyncThrowingStream<PpgData, Error> {
        return ppgStreams.makeStream(transport: gattServiceTransmitter, checkConnection: true)
    }
    public func observePpi() -> AsyncThrowingStream<PpiData, Error> {
        return ppiStreams.makeStream(transport: gattServiceTransmitter, checkConnection: true)
    }
    public func observeTemperature() -> AsyncThrowingStream<TemperatureData, Error> {
        return tempStreams.makeStream(transport: gattServiceTransmitter, checkConnection: true)
    }
    public func observePressure() -> AsyncThrowingStream<PressureData, Error> {
        return pressureStreams.makeStream(transport: gattServiceTransmitter, checkConnection: true)
    }
    public func observeSkinTemperature() -> AsyncThrowingStream<SkinTemperatureData, Error> {
        return skinTempStreams.makeStream(transport: gattServiceTransmitter, checkConnection: true)
    }

    // MARK: - Control point commands

    func sendControlPointCommand(_ packet: Data) throws -> PmdControlPointResponse {
        pmdCpResponseQueue.removeAll()
        try gattServiceTransmitter?.transmitMessage(self, serviceUuid: BlePmdClient.PMD_SERVICE,
                                                    characteristicUuid: BlePmdClient.PMD_CP,
                                                    packet: packet, withResponse: true)
        let response = try pmdCpResponseQueue.poll(30)
        return PmdControlPointResponse(response)
    }

    public func startMeasurement(_ type: PmdMeasurementType, settings: PmdSetting, _ recordingType: PmdRecordingType = .online, _ secret: PmdSecret? = nil) async throws {
        storedSettings.accessItem { $0[type] = settings }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let requestByte = recordingType.asBitField() | type.rawValue
                    var packet = Data([PmdControlPointCommandClientToService.REQUEST_MEASUREMENT_START, requestByte])
                    packet.append(settings.serialize())
                    packet.append(secret?.serializeToPmdSettings() ?? Data())
                    BleLogger.trace("start measurement. type: \(type) recordingType: \(recordingType)")
                    let response = try self.sendControlPointCommand(packet)
                    if response.errorCode == .success {
                        self.storedSettings.accessItem { settingsStored in
                            do { try settingsStored[type]?.updatePmdSettingsFromStartResponse(response.parameters as Data) }
                            catch { BleLogger.error("Failed to update PMD settings: \(error)") }
                        }
                        continuation.resume()
                    } else {
                        continuation.resume(throwing: BlePmdError.controlPointRequestFailed(errorCode: response.errorCode.rawValue, description: response.errorCode.description))
                    }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    func stopMeasurement(_ type: PmdMeasurementType) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let response = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.STOP_MEASUREMENT, type.rawValue]))
                    self.setPreviousFrameTimeStamp(type, PmdDataFrameType.type_0, 0)
                    if response.errorCode == .success { continuation.resume() }
                    else { continuation.resume(throwing: BlePmdError.controlPointRequestFailed(errorCode: response.errorCode.rawValue, description: response.errorCode.description)) }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    public func readFeature(_ checkConnection: Bool) async throws -> Set<PmdMeasurementType> {
        if let f = features { return PmdMeasurementType.fromByteArray(f) }
        let stream = featureWaiters.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
        for try await types in stream {
            return types
        }
        throw BleGattException.gattDisconnected
    }

    func readMeasurementStatus() async throws -> [(PmdMeasurementType, PmdActiveMeasurement)] {
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[(PmdMeasurementType, PmdActiveMeasurement)], Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let response = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.GET_MEASUREMENT_STATUS]))
                    if response.errorCode == .success {
                        var result = [(PmdMeasurementType, PmdActiveMeasurement)]()
                        for parameter in response.parameters as Data {
                            let t = PmdMeasurementType.fromId(id: parameter)
                            let active = PmdActiveMeasurement.fromStatusResponse(responseByte: parameter)
                            if t != .unknown_type { result.append((t, active)) }
                        }
                        continuation.resume(returning: result)
                    } else {
                        continuation.resume(throwing: BleGattException.gattAttributeError(errorCode: response.errorCode.rawValue, errorDescription: response.errorCode.description))
                    }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    public override func clientReady(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        Publishers.Concatenate(
            prefix: waitNotificationEnabled(BlePmdClient.PMD_CP, checkConnection: checkConnection),
            suffix: waitNotificationEnabled(BlePmdClient.PMD_DATA, checkConnection: checkConnection)
        ).eraseToAnyPublisher()
    }

    func querySettings(_ type: PmdMeasurementType, _ recordingType: PmdRecordingType) async throws -> PmdSetting {
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<PmdSetting, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let requestByte = recordingType.asBitField() | type.rawValue
                    let response = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.GET_MEASUREMENT_SETTINGS, requestByte]))
                    if response.errorCode == .success {
                        do { continuation.resume(returning: try PmdSetting(response.parameters as Data)) }
                        catch { continuation.resume(throwing: error) }
                    } else {
                        continuation.resume(throwing: BleGattException.gattAttributeError(errorCode: response.errorCode.rawValue, errorDescription: response.errorCode.description))
                    }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    func queryFullSettings(_ type: PmdMeasurementType, _ recordingType: PmdRecordingType) async throws -> PmdSetting {
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<PmdSetting, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let requestByte = recordingType.asBitField() | type.rawValue
                    let response = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.GET_SDK_MODE_SETTINGS, requestByte]))
                    if response.errorCode == .success {
                        do { continuation.resume(returning: try PmdSetting(response.parameters as Data)) }
                        catch { continuation.resume(throwing: error) }
                    } else {
                        continuation.resume(throwing: BleGattException.gattAttributeError(errorCode: response.errorCode.rawValue, errorDescription: response.errorCode.description))
                    }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    public func startSdkMode() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let response = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.REQUEST_MEASUREMENT_START, PmdMeasurementType.sdkMode.rawValue]))
                    if response.errorCode == .success || response.errorCode == .errorAlreadyInState {
                        self.clearStreamObservers(error: BleGattException.gattOperationModeChange(description: "SDK mode enabled"))
                        continuation.resume()
                    } else {
                        continuation.resume(throwing: BlePmdError.controlPointRequestFailed(errorCode: response.errorCode.rawValue, description: response.errorCode.description))
                    }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    public func stopSdkMode() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let response = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.STOP_MEASUREMENT, PmdMeasurementType.sdkMode.rawValue]))
                    if response.errorCode == .success || response.errorCode == .errorAlreadyInState {
                        self.clearStreamObservers(error: BleGattException.gattOperationModeChange(description: "SDK mode disabled"))
                        continuation.resume()
                    } else {
                        continuation.resume(throwing: BlePmdError.controlPointRequestFailed(errorCode: response.errorCode.rawValue, description: response.errorCode.description))
                    }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    func isSdkModeEnabled() async throws -> PmdSdkMode {
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<PmdSdkMode, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let response = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.GET_SDK_MODE_STATUS]))
                    if response.errorCode == .success {
                        if let byte = (response.parameters as Data).first {
                            continuation.resume(returning: PmdSdkMode.fromResponse(sdkModeByte: byte))
                        } else {
                            continuation.resume(throwing: BleGattException.gattDataError(description: "SDK mode status response parameter is missing"))
                        }
                    } else {
                        continuation.resume(throwing: BleGattException.gattAttributeError(errorCode: response.errorCode.rawValue, errorDescription: response.errorCode.description))
                    }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    func setOfflineRecordingTrigger(offlineRecordingTrigger: PmdOfflineTrigger, secret: PmdSecret?) async throws {
        try await setOfflineRecordingTriggerMode(triggerMode: offlineRecordingTrigger.triggerMode)
        if offlineRecordingTrigger.triggerMode != .disabled {
            let currentTriggers = try await getOfflineRecordingTriggerStatus()
            for (measurementType, _) in currentTriggers.triggers {
                if let trigger = offlineRecordingTrigger.triggers[measurementType] {
                    BleLogger.trace("Enable trigger \(measurementType)")
                    try await setOfflineRecordingTriggerSetting(triggerStatus: .enabled, type: measurementType, setting: trigger.setting, secret: secret)
                } else {
                    BleLogger.trace("Disable trigger \(measurementType)")
                    try await setOfflineRecordingTriggerSetting(triggerStatus: .disabled, type: measurementType)
                }
            }
        }
    }

    private func setOfflineRecordingTriggerMode(triggerMode: PmdOfflineRecTriggerMode) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let response = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.SET_OFFLINE_RECORDING_TRIGGER_MODE, triggerMode.rawValue]))
                    if response.errorCode == .success { continuation.resume() }
                    else { continuation.resume(throwing: BlePmdError.controlPointRequestFailed(errorCode: response.errorCode.rawValue, description: response.errorCode.description)) }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    func getOfflineRecordingTriggerStatus() async throws -> PmdOfflineTrigger {
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<PmdOfflineTrigger, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    let response = try self.sendControlPointCommand(Data([PmdControlPointCommandClientToService.GET_OFFLINE_RECORDING_TRIGGER_STATUS]))
                    if response.errorCode == .success {
                        let data = response.parameters as Data
                        if !data.isEmpty {
                            do { continuation.resume(returning: try PmdOfflineTrigger.fromResponse(data: data)) }
                            catch { continuation.resume(throwing: error) }
                        } else {
                            continuation.resume(throwing: BleGattException.gattDataError(description: "Offline trigger status response parameter is missing"))
                        }
                    } else {
                        continuation.resume(throwing: BleGattException.gattAttributeError(errorCode: response.errorCode.rawValue, errorDescription: response.errorCode.description))
                    }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }

    private func setOfflineRecordingTriggerSetting(triggerStatus: PmdOfflineRecTriggerStatus, type: PmdMeasurementType, setting: PmdSetting? = nil, secret: PmdSecret? = nil) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            baseSerialDispatchQueue.async {
                do {
                    guard type.isDataType() else {
                        continuation.resume()
                        return
                    }
                    var packet = Data([PmdControlPointCommandClientToService.SET_OFFLINE_RECORDING_TRIGGER_SETTINGS,
                                       triggerStatus.rawValue, type.rawValue])
                    if triggerStatus == .enabled {
                        packet.append(setting?.serialize() ?? Data())
                        packet.append(secret?.serializeToPmdSettings() ?? Data())
                    }
                    let response = try self.sendControlPointCommand(packet)
                    if response.errorCode == .success { continuation.resume() }
                    else { continuation.resume(throwing: BlePmdError.controlPointRequestFailed(errorCode: response.errorCode.rawValue, description: response.errorCode.description)) }
                } catch { continuation.resume(throwing: error) }
            }
        }
    }
}
