//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import XCTest
import Combine
import CoreBluetooth
@testable import PolarBleSdk

class PolarBleApiImplWithMockSession: PolarBleApiImpl {
    required init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>, restoreIdentifier: String? = nil) {
        fatalError("init(_:features:) has not been implemented")
    }
    
    init(mockDeviceSession: MockBleDeviceSession) {
        self.mockDeviceSession = mockDeviceSession
        super.init(DispatchQueue(label: "test"), features: [], restoreIdentifier: nil)
        // Ensure file-based APIs (via fileUtils) resolve sessions from the mock service utils.
        self.fileUtils = PolarFileUtils(listener: _serviceUtils.mockListener, serviceClientUtils: _serviceUtils)
    }
    let mockDeviceSession: MockBleDeviceSession
    
    private lazy var _serviceUtils: MockPolarServiceClientUtils = {
        MockPolarServiceClientUtils(listener: MockCBDeviceListenerImpl(), session: mockDeviceSession)
    }()
    
    override var serviceClientUtils: PolarServiceClientUtils {
        return _serviceUtils
    }
}

class PolarBleApiImplWithMockH10Session: PolarBleApiImpl {
    required init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>, restoreIdentifier: String? = nil) {
        fatalError("init(_:features:) has not been implemented")
    }
    
    init(mockDeviceSession: MockH10BleDeviceSession) {
        self.mockDeviceSession = mockDeviceSession
        super.init(DispatchQueue(label: "test"), features: [], restoreIdentifier: nil)
    }
    let mockDeviceSession: MockH10BleDeviceSession
    
    override var serviceClientUtils: PolarServiceClientUtils {
        return MockPolarH10ServiceClientUtils(listener: MockCBDeviceListenerImpl(), session: mockDeviceSession)
    }
}

class MockCBDeviceListenerImpl: CBDeviceListenerImpl {
    
    var listener: CBDeviceListenerImpl
    var clientList: [(_ gattServiceTransmitter: BleAttributeTransportProtocol) -> BleGattClientBase] = []
    var closeSessionDirectCalls: [BleDeviceSession] = []
    
    init() {
        clientList.append(BlePmdClient.init)
        listener = CBDeviceListenerImpl(DispatchQueue(label: "test"), clients: clientList, identifier: 0)
        super.init(DispatchQueue(label: "test"), clients: clientList, identifier: 0)
    }
    
}

class MockPolarServiceClientUtils: PolarServiceClientUtils {
    
    var mockListener: MockCBDeviceListenerImpl!
    var mockSession: MockBleDeviceSession
    init(listener: MockCBDeviceListenerImpl, session: MockBleDeviceSession) {
        self.mockSession = session
        self.mockListener = listener
        super.init(listener: listener)
    }
    
    required init(listener: CBDeviceListenerImpl) {
        fatalError("init(listener:) has not been implemented")
    }
    override func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        return mockSession
    }
}

class MockNoFtpClientBleDeviceSession: BleDeviceSession {
    init() {
        super.init(UUID(), advertisementContent: MockAdvertisementContent())
    }
    public override func fetchGattClient(_ serviceUuid: CBUUID) -> BleGattClientBase? {
        return nil
    }
}

class MockNoFtpClientPolarServiceClientUtils: PolarServiceClientUtils {
    var mockListener: MockCBDeviceListenerImpl!
    var mockSession: MockNoFtpClientBleDeviceSession
    init(listener: MockCBDeviceListenerImpl, session: MockNoFtpClientBleDeviceSession) {
        self.mockSession = session
        self.mockListener = listener
        super.init(listener: listener)
    }
    required init(listener: CBDeviceListenerImpl) {
        fatalError("init(listener:) has not been implemented")
    }
    override func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        return mockSession
    }
}

class MockPolarH10ServiceClientUtils: PolarServiceClientUtils {
    var mockListener: MockCBDeviceListenerImpl!
    var mockSession: MockH10BleDeviceSession
    
    init(listener: MockCBDeviceListenerImpl, session: MockH10BleDeviceSession) {
        self.mockSession = session
        self.mockListener = listener
        super.init(listener: listener)
    }
    
    required init(listener: CBDeviceListenerImpl) {
        fatalError("init(listener:) has not been implemented")
    }
    
    override func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        return mockSession
    }
}

class MockAdvertisementContent: BleAdvertisementContent {
    override var polarDeviceType: String {
        return "360"
    }
}

class MockH10AdvertisementContent: BleAdvertisementContent {
    override var polarDeviceType: String {
        return "h10"
    }
}

class MockH10BleDeviceSession: BleDeviceSession {
    init(mockFtpClient: MockBlePsFtpClient) {
        self.mockFtpClient = mockFtpClient
        self.ftpClient = unsafeBitCast(mockFtpClient, to: BleGattClientBase.self)
        super.init(UUID(), advertisementContent: MockH10AdvertisementContent())
    }
    let mockFtpClient: MockBlePsFtpClient
    private let ftpClient: BleGattClientBase
    public override func fetchGattClient(_ serviceUuid: CBUUID) -> BleGattClientBase? {
        if serviceUuid == BlePsFtpClient.PSFTP_SERVICE {
            return ftpClient
        }
        return nil
    }
}

class MockBleDeviceSession: BleDeviceSession {
    init(mockFtpClient: MockBlePsFtpClient) {
        self.mockFtpClient = mockFtpClient
        // Store as BlePsFtpClient (iOSCommunications type) so the upcast to
        // BleGattClientBase succeeds without a cross-module type identity conflict.
        self.ftpClient = unsafeBitCast(mockFtpClient, to: BleGattClientBase.self)
        super.init(UUID(), advertisementContent: MockAdvertisementContent())
    }
    let mockFtpClient: MockBlePsFtpClient
    private let ftpClient: BleGattClientBase
    public override func fetchGattClient(_ serviceUuid: CBUUID) -> BleGattClientBase? {
        if serviceUuid == BlePsFtpClient.PSFTP_SERVICE {
            return ftpClient
        }
        return nil
    }
}

class MockPolarGattServiceTransmitter: BleAttributeTransportProtocol {
    var mockConnectionStatus: Bool = true
    var setCharacteristicsNotifyCache: [(characteristicUuid: CBUUID, notify: Bool)] = []
    
    func isConnected() -> Bool {
        return mockConnectionStatus
    }
    
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID , characteristicUuid: CBUUID , packet: Data, withResponse: Bool) throws {
        // Do nothing
    }
    
    func characteristicWith(uuid: CBUUID) throws -> CBCharacteristic? {
        return nil
    }
    
    func characteristicNameWith(uuid: CBUUID) -> String? {
        return nil
    }
    
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID , characteristicUuid: CBUUID ) throws {
        // Do nothing
    }
    
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws {
        setCharacteristicsNotifyCache.append((characteristicUuid, notify))
        parent.notifyDescriptorWritten(characteristicUuid, enabled: notify, err: 0)
    }
    
    func attributeOperationStarted(){
        // Do nothing
    }
    
    func attributeOperationFinished(){
        // Do nothing
    }
}

// MARK: - Mock advertisement content for search tests

class MockSearchAdvertisementContent: BleAdvertisementContent {
    var mockName: String = ""
    var mockPolarDeviceType: String = ""
    var mockPolarDeviceIdUntouched: String = ""
    var mockMedianRssi: Int32 = -70 {
        didSet { rssiFilter.processRssiValueUpdated(mockMedianRssi) }
    }
    var mockIsConnectable: Bool = true
    var mockContainsService: Bool = true
    
    override init() {
        super.init()
        rssiFilter.processRssiValueUpdated(-70)
    }
    
    override var name: String { mockName }
    override var polarDeviceType: String { mockPolarDeviceType }
    override var polarDeviceIdUntouched: String { mockPolarDeviceIdUntouched }
    override var medianRssi: Int32 { mockMedianRssi }
    override var isConnectable: Bool { mockIsConnectable }
    
    override func containsService(_ service: CBUUID) -> Bool { mockContainsService }
}

// MARK: - BleDeviceSession that uses an injectable MockSearchAdvertisementContent

class MockSearchBleDeviceSession: BleDeviceSession {
    init(advertisementContent: MockSearchAdvertisementContent) {
        super.init(UUID(), advertisementContent: advertisementContent)
    }
    
    override func isConnectable() -> Bool { return advertisementContent.isConnectable }
}


// MARK: - Standalone mock for searchForDevice tests

class MockSearchBleApiImpl {
    let searchSubject = PassthroughSubject<BleDeviceSession, Error>()
    
    // Accept a MockBleDeviceSession to match the call-site signature in tests.
    init(mockDeviceSession: MockBleDeviceSession) {}
    
    func searchForDevice() -> AnyPublisher<PolarDeviceInfo, Error> {
        searchForDevice(withRequiredDeviceNamePrefix: nil)
    }
    
    func searchForDevice(withRequiredDeviceNamePrefix requiredDeviceNamePrefix: String? = "Polar") -> AnyPublisher<PolarDeviceInfo, Error> {
        var hasSAGRFCFileSystem: Bool = false
        return searchSubject
            .filter { sess -> Bool in
                let name = sess.advertisementContent.name
                hasSAGRFCFileSystem = (BlePolarDeviceCapabilitiesUtility.fileSystemType(
                    sess.advertisementContent.polarDeviceType) == .polarFileSystemV2)
                return requiredDeviceNamePrefix == nil || name.hasPrefix(requiredDeviceNamePrefix!)
            }
            .distinct()
            .map { value -> PolarDeviceInfo in
                return (value.advertisementContent.polarDeviceIdUntouched,
                        address: value.address,
                        rssi: Int(value.advertisementContent.medianRssi),
                        name: value.advertisementContent.name,
                        connectable: value.advertisementContent.isConnectable,
                        hasSAGRFCFileSystem: hasSAGRFCFileSystem)
            }
            .eraseToAnyPublisher()
    }
}

// MARK: - Standalone mock for startAutoConnectToDevice tests

class MockAutoConnectBleApiImpl {
    let searchSubject = PassthroughSubject<BleDeviceSession, Error>()
    var openedSessions: [BleDeviceSession] = []
    
    init(mockDeviceSession: MockBleDeviceSession) {}
    
    func startAutoConnectToDevice(_ rssi: Int, service: CBUUID?, polarDeviceType: String?) -> AnyPublisher<Never, Error> {
        return searchSubject
            .filter { sess -> Bool in
                return Int(sess.advertisementContent.medianRssi) >= rssi &&
                sess.isConnectable() &&
                (polarDeviceType == nil || polarDeviceType == sess.advertisementContent.polarDeviceType) &&
                (service == nil || sess.advertisementContent.containsService(service!))
            }
            .prefix(1)
            .handleEvents(receiveOutput: { [weak self] session in
                self?.openedSessions.append(session)
            })
            .ignoreOutput()
            .eraseToAnyPublisher()
    }
}

// MARK: - Standalone mock for startListenForPolarHrBroadcasts tests

class MockHrBroadcastBleApiImpl {
    /// A simple unbounded FIFO that bridges synchronous test `send` calls into an
    /// `AsyncThrowingStream`.  Values sent before the consumer iterates are buffered.
    private actor SessionChannel {
        private var buffer: [BleDeviceSession] = []
        private var waiters: [CheckedContinuation<BleDeviceSession?, Error>] = []
        private var finished: Bool = false
        private var finishError: Error? = nil
        
        func send(_ session: BleDeviceSession) {
            if let waiter = waiters.first {
                waiters.removeFirst()
                waiter.resume(returning: session)
            } else {
                buffer.append(session)
            }
        }
        
        func finish(throwing error: Error? = nil) {
            finished = true; finishError = error
            let pending = waiters; waiters.removeAll()
            if let e = error { pending.forEach { $0.resume(throwing: e) } }
            else             { pending.forEach { $0.resume(returning: nil) } }
        }
        
        func next() async throws -> BleDeviceSession? {
            if !buffer.isEmpty { return buffer.removeFirst() }
            if finished { if let e = finishError { throw e }; return nil }
            return try await withCheckedThrowingContinuation { waiters.append($0) }
        }
    }
    
    private let channel = SessionChannel()
    
    init(mockDeviceSession: MockBleDeviceSession) {}
    
    func send(_ session: BleDeviceSession) { Task { await channel.send(session) } }
    func sendCompletion(_ completion: Subscribers.Completion<Error> = .finished) {
        Task {
            switch completion {
            case .finished:       await channel.finish()
            case .failure(let e): await channel.finish(throwing: e)
            }
        }
    }
    
    /// Proxy so existing `hrBroadcastApi.searchSubject.send(...)` call sites keep compiling.
    var searchSubject: _MockSearchSubjectProxy { _MockSearchSubjectProxy(owner: self) }
    
    func startListenForPolarHrBroadcasts(_ identifiers: Set<String>?) -> AsyncThrowingStream<PolarHrBroadcastData, Error> {
        AsyncThrowingStream { continuation in
            Task { [channel] in
                do {
                    while let session = try await channel.next() {
                        let hasSAGRFCFileSystem = BlePolarDeviceCapabilitiesUtility.fileSystemType(
                            session.advertisementContent.polarDeviceType) == .polarFileSystemV2
                        guard (identifiers == nil || identifiers!.contains(session.advertisementContent.polarDeviceIdUntouched)) &&
                                session.advertisementContent.polarHrAdvertisementData.isPresent &&
                                session.advertisementContent.polarHrAdvertisementData.isHrDataUpdated else { continue }
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
        }
    }
}

struct _MockSearchSubjectProxy {
    let owner: MockHrBroadcastBleApiImpl
    func send(_ session: BleDeviceSession) { owner.send(session) }
    func send(completion: Subscribers.Completion<Error>) { owner.sendCompletion(completion) }
}

// MARK: - MockBlePfcClient

class MockBlePfcClient: BlePfcClient {
    var commandCalls: [(command: BlePfcClient.PfcMessage, value: [UInt8])] = []
    var commandReturnValue: Result<Pfc.PfcResponse, Error> = .success(Pfc.PfcResponse())
    
    override public func sendControlPointCommand(_ command: BlePfcClient.PfcMessage, value: [UInt8]) async throws -> Pfc.PfcResponse {
        commandCalls.append((command: command, value: value))
        switch commandReturnValue {
        case .success(let response): return response
        case .failure(let error): throw error
        }
    }
}

// MARK: - BleDeviceSession backed by MockBlePfcClient

class MockPfcBleDeviceSession: BleDeviceSession {
    let mockPfcClient: MockBlePfcClient
    private let pfcClientBase: BleGattClientBase
    
    init(mockPfcClient: MockBlePfcClient) {
        self.mockPfcClient = mockPfcClient
        pfcClientBase = unsafeBitCast(mockPfcClient, to: BleGattClientBase.self)
        super.init(UUID(), advertisementContent: MockAdvertisementContent())
    }
    
    override func fetchGattClient(_ serviceUuid: CBUUID) -> BleGattClientBase? {
        serviceUuid == BlePfcClient.PFC_SERVICE ? pfcClientBase : nil
    }
}

// MARK: - ServiceClientUtils stub for PFC tests

class MockPfcServiceClientUtils: PolarServiceClientUtils {
    var stubSession: BleDeviceSession?
    var stubError: Error?
    
    required init(listener: CBDeviceListenerImpl) {
        super.init(listener: listener)
    }
    
    override func sessionPfcClientReady(_ identifier: String) throws -> BleDeviceSession {
        if let error = stubError { throw error }
        return stubSession!
    }
    
    override func waitPfcClientReady(_ identifier: String) async throws -> BleDeviceSession {
        if let error = stubError { throw error }
        return stubSession!
    }
    
    override func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        throw PolarErrors.serviceNotFound
    }
}

// MARK: - PolarBleApiImpl subclass for PFC tests

class MockPfcBleApiImpl: PolarBleApiImpl {
    required init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>, restoreIdentifier: String? = nil) {
        fatalError("use init(mockPfcSession:)")
    }
    
    init(mockPfcSession: MockPfcBleDeviceSession) {
        pfcSession = mockPfcSession
        super.init(DispatchQueue(label: "test.pfc"), features: [], restoreIdentifier: nil)
    }
    
    let pfcSession: MockPfcBleDeviceSession
    
    private lazy var _pfcServiceUtils: MockPfcServiceClientUtils = {
        let utils = MockPfcServiceClientUtils(listener: MockCBDeviceListenerImpl())
        utils.stubSession = pfcSession
        return utils
    }()
    
    override var serviceClientUtils: PolarServiceClientUtils { _pfcServiceUtils }
    
    /// Exposes the mock utils so individual tests can modify `stubSession` or `stubError`.
    var pfcServiceUtils: MockPfcServiceClientUtils { _pfcServiceUtils }
}

// MARK: - MockBlePmdClient

class MockBlePmdClient: BlePmdClient, @unchecked Sendable {
    var querySettingsCalls: [(type: PmdMeasurementType, recordingType: PmdRecordingType)] = []
    var querySettingsReturnValue: Result<PmdSetting, Error>?
    
    var queryFullSettingsCalls: [(type: PmdMeasurementType, recordingType: PmdRecordingType)] = []
    var queryFullSettingsReturnValue: Result<PmdSetting, Error>?
    
    var readFeatureCalls: [Bool] = []
    var readFeatureReturnValue: Result<Set<PmdMeasurementType>, Error>?
    
    var readMeasurementStatusCalls: Int = 0
    var readMeasurementStatusReturnValue: Result<[(PmdMeasurementType, PmdActiveMeasurement)], Error>?
    
    var startMeasurementCalls: [(type: PmdMeasurementType, settings: PmdSetting, recordingType: PmdRecordingType)] = []
    var startMeasurementError: Error?
    
    var stopMeasurementCalls: [PmdMeasurementType] = []
    var stopMeasurementError: Error?
    
    var sdkModeStartCalls = 0
    var sdkModeStartError: Error?
    var sdkModeStopCalls = 0
    var sdkModeStopError: Error?
    var sdkModeEnabledReturnValue: Result<PmdSdkMode, Error> = .success(.enabled)
    
    var setOfflineRecordingTriggerCalls: [PmdOfflineTrigger] = []
    var setOfflineRecordingTriggerError: Error?
    var getOfflineRecordingTriggerStatusCalls = 0
    var getOfflineRecordingTriggerStatusReturnValue: Result<PmdOfflineTrigger, Error> = .success(PmdOfflineTrigger(triggerMode: .disabled, triggers: [:]))
    
    override func querySettings(_ type: PmdMeasurementType, _ recordingType: PmdRecordingType) async throws -> PmdSetting {
        querySettingsCalls.append((type, recordingType))
        switch querySettingsReturnValue ?? .failure(NSError(domain: "MockBlePmdClient", code: 0)) {
        case .success(let v): return v
        case .failure(let e): throw e
        }
    }
    
    override func queryFullSettings(_ type: PmdMeasurementType, _ recordingType: PmdRecordingType) async throws -> PmdSetting {
        queryFullSettingsCalls.append((type, recordingType))
        switch queryFullSettingsReturnValue ?? .failure(NSError(domain: "MockBlePmdClient.full", code: 0)) {
        case .success(let v): return v
        case .failure(let e): throw e
        }
    }
    
    override public func readFeature(_ checkConnection: Bool) async throws -> Set<PmdMeasurementType> {
        readFeatureCalls.append(checkConnection)
        switch readFeatureReturnValue ?? .failure(NSError(domain: "MockBlePmdClient.readFeature", code: 0)) {
        case .success(let v): return v
        case .failure(let e): throw e
        }
    }
    
    override func readMeasurementStatus() async throws -> [(PmdMeasurementType, PmdActiveMeasurement)] {
        readMeasurementStatusCalls += 1
        switch readMeasurementStatusReturnValue ?? .failure(NSError(domain: "MockBlePmdClient.readMeasurementStatus", code: 0)) {
        case .success(let v): return v
        case .failure(let e): throw e
        }
    }
    
    override public func startMeasurement(_ type: PmdMeasurementType, settings: PmdSetting, _ recordingType: PmdRecordingType = .online, _ secret: PmdSecret? = nil) async throws {
        startMeasurementCalls.append((type, settings, recordingType))
        if let error = startMeasurementError { throw error }
    }
    
    override func stopMeasurement(_ type: PmdMeasurementType) async throws {
        stopMeasurementCalls.append(type)
        if let error = stopMeasurementError { throw error }
    }
    
    override public func startSdkMode() async throws {
        sdkModeStartCalls += 1
        if let error = sdkModeStartError { throw error }
    }
    
    override public func stopSdkMode() async throws {
        sdkModeStopCalls += 1
        if let error = sdkModeStopError { throw error }
    }
    
    override func isSdkModeEnabled() async throws -> PmdSdkMode {
        switch sdkModeEnabledReturnValue {
        case .success(let mode): return mode
        case .failure(let error): throw error
        }
    }
    
    override func setOfflineRecordingTrigger(offlineRecordingTrigger: PmdOfflineTrigger, secret: PmdSecret?) async throws {
        setOfflineRecordingTriggerCalls.append(offlineRecordingTrigger)
        if let error = setOfflineRecordingTriggerError { throw error }
    }
    
    override func getOfflineRecordingTriggerStatus() async throws -> PmdOfflineTrigger {
        getOfflineRecordingTriggerStatusCalls += 1
        switch getOfflineRecordingTriggerStatusReturnValue {
        case .success(let trigger): return trigger
        case .failure(let error): throw error
        }
    }
}

// MARK: - BleDeviceSession backed by MockBlePmdClient

class MockPmdBleDeviceSession: BleDeviceSession {
    let mockPmdClient: MockBlePmdClient
    private let pmdClientBase: BleGattClientBase
    
    init(mockPmdClient: MockBlePmdClient) {
        self.mockPmdClient = mockPmdClient
        self.pmdClientBase = mockPmdClient
        super.init(UUID())
    }
    
    override func isConnectable() -> Bool { return true }
    
    override func fetchGattClient(_ serviceUuid: CBUUID) -> BleGattClientBase? {
        serviceUuid == BlePmdClient.PMD_SERVICE ? pmdClientBase : nil
    }
}

// MARK: - ServiceClientUtils stub for PMD tests

class MockPmdServiceClientUtils: PolarServiceClientUtils {
    var stubSession: MockPmdBleDeviceSession?
    var stubError: Error?
    
    required init(listener: CBDeviceListenerImpl) {
        super.init(listener: listener)
    }
    
    override func sessionPmdClientReady(_ identifier: String) throws -> BleDeviceSession {
        if let error = stubError { throw error }
        return stubSession!
    }
    
    override func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        throw PolarErrors.serviceNotFound
    }
    
    override func waitPmdClientReady(_ identifier: String) async throws -> BleDeviceSession {
        if let error = stubError { throw error }
        return stubSession!
    }
}

// MARK: - PolarBleApiImpl subclass for requestStreamSettings tests

class MockPmdBleApiImpl: PolarBleApiImpl {
    required init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>, restoreIdentifier: String? = nil) {
        fatalError("use init(mockPmdSession:)")
    }
    
    init(mockPmdSession: MockPmdBleDeviceSession) {
        self.mockPmdSession = mockPmdSession
        super.init(DispatchQueue(label: "test.pmd"), features: [], restoreIdentifier: nil)
    }
    
    let mockPmdSession: MockPmdBleDeviceSession
    
    private lazy var _pmdServiceUtils: MockPmdServiceClientUtils = {
        let utils = MockPmdServiceClientUtils(listener: MockCBDeviceListenerImpl())
        utils.stubSession = mockPmdSession
        return utils
    }()
    
    override var serviceClientUtils: PolarServiceClientUtils { _pmdServiceUtils }
    
    var pmdServiceUtils: MockPmdServiceClientUtils { _pmdServiceUtils }
}

class PolarBleApiImplWithNoFtpSession: PolarBleApiImpl {
    required init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>, restoreIdentifier: String? = nil) {
        fatalError("init(_:features:) has not been implemented")
    }
    
    init(mockDeviceSession: MockNoFtpClientBleDeviceSession) {
        self.mockDeviceSession = mockDeviceSession
        super.init(DispatchQueue(label: "test.noftp"), features: [], restoreIdentifier: nil)
        self.fileUtils = PolarFileUtils(listener: _serviceUtils.mockListener, serviceClientUtils: _serviceUtils)
    }
    
    let mockDeviceSession: MockNoFtpClientBleDeviceSession
    
    private lazy var _serviceUtils: MockNoFtpClientPolarServiceClientUtils = {
        MockNoFtpClientPolarServiceClientUtils(listener: MockCBDeviceListenerImpl(), session: mockDeviceSession)
    }()
    
    override var serviceClientUtils: PolarServiceClientUtils { _serviceUtils }
}

class MockMultiClientBleDeviceSession: BleDeviceSession {
    init(clients: [BleGattClientBase], advertisementContent: BleAdvertisementContent = MockAdvertisementContent()) {
        super.init(UUID(), advertisementContent: advertisementContent)
        self.gattClients = clients
        self.state = .sessionOpen
    }
    
    override func isConnectable() -> Bool { true }
    
    override func monitorServicesDiscovered(_ checkConnection: Bool) -> AsyncThrowingStream<CBUUID, Error> {
        AsyncThrowingStream { continuation in
            continuation.finish()
        }
    }
}

class MockDynamicServiceClientUtils: PolarServiceClientUtils {
    var ftpSession: BleDeviceSession?
    var ftpError: Error?
    var pmdSession: BleDeviceSession?
    var pmdError: Error?
    var pfcSession: BleDeviceSession?
    var pfcError: Error?
    var hrSession: BleDeviceSession?
    var hrError: Error?
    var mdsSession: BleDeviceSession?
    var mdsError: Error?
    var serviceSessionByUuid: [String: BleDeviceSession] = [:]
    var serviceErrorByUuid: [String: Error] = [:]
    var fetchSessionHandler: ((String) throws -> BleDeviceSession?)?
    var rssiHandler: ((String) throws -> Int)?
    var pairingHandler: ((String) throws -> Bool)?
    
    required init(listener: CBDeviceListenerImpl) {
        super.init(listener: listener)
    }
    
    override func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        if let error = ftpError { throw error }
        if let session = ftpSession { return session }
        throw PolarErrors.serviceNotFound
    }
    
    override func sessionPmdClientReady(_ identifier: String) throws -> BleDeviceSession {
        if let error = pmdError { throw error }
        if let session = pmdSession { return session }
        throw PolarErrors.serviceNotFound
    }
    
    override func waitPmdClientReady(_ identifier: String) async throws -> BleDeviceSession {
        if let error = pmdError { throw error }
        if let session = pmdSession { return session }
        throw PolarErrors.serviceNotFound
    }
    
    override func sessionPfcClientReady(_ identifier: String) throws -> BleDeviceSession {
        if let error = pfcError { throw error }
        if let session = pfcSession { return session }
        throw PolarErrors.serviceNotFound
    }
    
    override func waitPfcClientReady(_ identifier: String) async throws -> BleDeviceSession {
        if let error = pfcError { throw error }
        if let session = pfcSession { return session }
        throw PolarErrors.serviceNotFound
    }
    
    override func sessionHrClientReady(_ identifier: String) throws -> BleDeviceSession {
        if let error = hrError { throw error }
        if let session = hrSession { return session }
        throw PolarErrors.serviceNotFound
    }
    
    override func sessionMdsClientReady(_ identifier: String) throws -> BleDeviceSession {
        if let error = mdsError { throw error }
        if let session = mdsSession { return session }
        throw PolarErrors.serviceNotFound
    }
    
    override func sessionServiceReady(_ identifier: String, service: CBUUID) throws -> BleDeviceSession {
        if let error = serviceErrorByUuid[service.uuidString] { throw error }
        if let session = serviceSessionByUuid[service.uuidString] { return session }
        switch service {
        case BlePsFtpClient.PSFTP_SERVICE: return try sessionFtpClientReady(identifier)
        case BlePmdClient.PMD_SERVICE: return try sessionPmdClientReady(identifier)
        case BlePfcClient.PFC_SERVICE: return try sessionPfcClientReady(identifier)
        case BleHrClient.HR_SERVICE: return try sessionHrClientReady(identifier)
        case BleMdsClient.MDS_SERVICE: return try sessionMdsClientReady(identifier)
        default: throw PolarErrors.serviceNotFound
        }
    }
    
    override func fetchSession(_ identifier: String) throws -> BleDeviceSession? {
        if let handler = fetchSessionHandler { return try handler(identifier) }
        return ftpSession ?? pmdSession ?? pfcSession ?? hrSession ?? mdsSession
    }
    
    override func getRSSIValue(_ identifier: String) throws -> Int {
        if let handler = rssiHandler { return try handler(identifier) }
        return try super.getRSSIValue(identifier)
    }
    
    override func checkIfDeviceDisconnectedDueRemovedPairing(identifier: String) throws -> Bool {
        if let handler = pairingHandler { return try handler(identifier) }
        return try super.checkIfDeviceDisconnectedDueRemovedPairing(identifier: identifier)
    }
}

class MockDynamicBleApiImpl: PolarBleApiImpl {
    required init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>, restoreIdentifier: String? = nil) {
        fatalError("use init(serviceUtils:)")
    }
    
    init(serviceUtils: MockDynamicServiceClientUtils) {
        self.injectedServiceUtils = serviceUtils
        super.init(DispatchQueue(label: "test.dynamic"), features: [], restoreIdentifier: nil)
        self.fileUtils = PolarFileUtils(listener: serviceUtils.listener!, serviceClientUtils: serviceUtils)
    }
    
    let injectedServiceUtils: MockDynamicServiceClientUtils
    
    override var serviceClientUtils: PolarServiceClientUtils { injectedServiceUtils }
}

class MockPolarFileUtils: PolarFileUtils {
    var listedFiles: [String] = []
    var listFilesError: Error?
    var checkedAutoSampleFiles: Set<String> = []
    var autoSampleCheckResult = true
    var removeSingleFileCalls: [(identifier: String, filePath: String)] = []
    var deleteDataDirectoryCalls: [(identifier: String, directoryPath: String)] = []
    
    required init(listener: CBDeviceListenerImpl, serviceClientUtils: PolarServiceClientUtils) {
        super.init(listener: listener, serviceClientUtils: serviceClientUtils)
    }
    
    override func listFiles(identifier: String, folderPath: String = "/", condition: @escaping (_ p: String) -> Bool, recurseDeep: Bool = true) -> AsyncThrowingStream<String, Error> {
        let files = listedFiles.filter(condition)
        let error = listFilesError
        return AsyncThrowingStream { continuation in
            if let error {
                continuation.finish(throwing: error)
                return
            }
            for file in files { continuation.yield(file) }
            continuation.finish()
        }
    }
    
    override func checkAutoSampleFile(identifier: String, filePath: String, until: Date) async throws -> Bool {
        checkedAutoSampleFiles.insert(filePath)
        return autoSampleCheckResult
    }
    
    override func deleteDataDirectory(identifier: String, directoryPath: String) async throws {
        deleteDataDirectoryCalls.append((identifier, directoryPath))
    }
    
    override func removeSingleFile(identifier: String, filePath: String) async throws -> NSData {
        removeSingleFileCalls.append((identifier, filePath))
        return NSData(data: Data())
    }
}

class MockDisconnectPolarServiceClientUtils: PolarServiceClientUtils {
    var stubSession: BleDeviceSession?
    var shouldThrow = false
    
    required init(listener: CBDeviceListenerImpl) {
        super.init(listener: listener)
    }
    
    override func fetchSession(_ identifier: String) throws -> BleDeviceSession? {
        if shouldThrow { throw PolarErrors.invalidArgument() }
        return stubSession
    }
    
    override func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        throw PolarErrors.serviceNotFound
    }
}

// MARK: - PolarBleApiImpl subclass for disconnectFromDevice tests
// Standalone class (not a PolarBleApiImpl subclass) that reimplements
// disconnectFromDevice to avoid the extension-override restrictions on both
// PolarBleApiImpl.disconnectFromDevice and CBDeviceListenerImpl.closeSessionDirect.

class MockDisconnectBleApiImpl {
    init(mockDeviceSession: BleDeviceSession) {}
    
    private lazy var _disconnectServiceUtils: MockDisconnectPolarServiceClientUtils = {
        MockDisconnectPolarServiceClientUtils(listener: MockCBDeviceListenerImpl())
    }()
    
    var disconnectServiceUtils: MockDisconnectPolarServiceClientUtils { _disconnectServiceUtils }
    
    var connectSubscriptions: [String: AnyCancellable] = [:]
    var closeSessionDirectCalls: [BleDeviceSession] = []
    
    func disconnectFromDevice(_ identifier: String) throws {
        if let session = try _disconnectServiceUtils.fetchSession(identifier) {
            if session.state == .sessionOpen ||
                session.state == .sessionOpening ||
                session.state == .sessionOpenPark {
                closeSessionDirectCalls.append(session)
            }
        }
        connectSubscriptions.removeValue(forKey: identifier)?.cancel()
    }
    
    func listOfflineRecordings(_ identifier: String) -> AsyncThrowingStream<PolarOfflineRecordingEntry, Error> {
        AsyncThrowingStream { continuation in
            do {
                _ = try _disconnectServiceUtils.sessionFtpClientReady(identifier)
                continuation.finish(throwing: PolarErrors.serviceNotFound)
            } catch {
                continuation.finish(throwing: error)
            }
        }
    }
}

// MARK: - ServiceClientUtils that lets tests control fetchSession output

public class MockBlePsFtpClient: BlePsFtpClient {
    private let requestCallsLock = NSLock()
    private let queryCallsLock = NSLock()
    public var requestCalls: [Data] = []
    public var requestReturnValues: [Result<Data, Error>] = []
    public var requestReturnValueClosure: ((Data) async throws -> Data)?
    public var requestReturnValue: Result<Data, Error>?
    public var directoryContentReturnValue: Result<Data, Error>?
    
    public var queryCalls: [(id: Int, parameters: NSData?)] = []
    public var queryReturnValues: [Result<Data, Error>] = []
    public var queryReturnValue: Result<Data, Error>?
    
    public var writeCalls: [(header: NSData, data: InputStream)] = []
    public var writeReturnValues: [AsyncThrowingStream<UInt, Error>] = []
    public var writeReturnValue: AsyncThrowingStream<UInt, Error>?
    
    public var sendNotificationCalls: [(notification: Int, parameters: NSData?)] = []
    public var sendNotificationError: Error?
    
    public var receiveNotificationCalls: [(notification: Int, parameters: [Data], compressed: Bool)] = []
    
    override public func request(_ header: Data) async throws -> NSData {
        requestCallsLock.lock()
        requestCalls.append(header)
        requestCallsLock.unlock()
        if let closure = requestReturnValueClosure {
            return NSData(data: try await closure(header))
        }
        if !requestReturnValues.isEmpty {
            let result = requestReturnValues.removeFirst()
            switch result {
            case .success(let data): return NSData(data: data)
            case .failure(let error): throw error
            }
        }
        switch requestReturnValue ?? .success(Data()) {
        case .success(let data): return NSData(data: data)
        case .failure(let error): throw error
        }
    }
    
    override public func query(_ id: Int, parameters: NSData?) async throws -> NSData {
        queryCallsLock.lock()
        queryCalls.append((id: id, parameters: parameters))
        let result: Result<Data, Error>
        if !queryReturnValues.isEmpty {
            result = queryReturnValues.removeFirst()
        } else {
            result = queryReturnValue ?? .success(Data())
        }
        queryCallsLock.unlock()
        switch result {
        case .success(let data): return NSData(data: data)
        case .failure(let error): throw error
        }
    }
    
    public override func write(_ header: NSData, data: InputStream) -> AsyncThrowingStream<UInt, Error> {
        writeCalls.append((header, data))
        if !writeReturnValues.isEmpty { return writeReturnValues.removeFirst() }
        return writeReturnValue ?? AsyncThrowingStream { $0.finish() }
    }
    
    public override func sendNotification(_ id: Int, parameters: NSData?) async throws {
        sendNotificationCalls.append((id, parameters))
        if let error = sendNotificationError { throw error }
    }
    
    public override func waitNotification() -> AsyncThrowingStream<PsFtpNotification, Error> {
        let calls = receiveNotificationCalls
        return AsyncThrowingStream { continuation in
            Task {
                for (id, arrayOfData, compressed) in calls {
                    let notification = PsFtpNotification()
                    notification.id = Int32(id)
                    if id == Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue {
                        var event = Protocol_PbPftpDHRestApiEvent()
                        event.uncompressed = !compressed
                        event.event = arrayOfData
                        notification.parameters = NSMutableData(data: (try? event.serializedData()) ?? Data())
                    } else {
                        notification.parameters = NSMutableData(data: arrayOfData.last ?? Data())
                    }
                    continuation.yield(notification)
                }
                continuation.finish()
            }
        }
    }
}

// MARK: - MockBleMdsClient

public class MockBleMdsClient: BleMdsClient, @unchecked Sendable {
    public var readSupportedFeaturesReturnValue: Result<UInt32, Error> = .success(0x01)
    public var readDeviceIdentifierReturnValue: Result<String, Error> = .success("POLAR-TEST-ID")
    public var readDataUriReturnValue: Result<String, Error> = .success("https://nrf-chunks.memfault.com/api/v0/chunks/")
    public var readAuthorizationReturnValue: Result<String, Error> = .success("")
    
    public var enableNotificationCalled = false
    public var enableNotificationError: Error? = nil
    public var disableNotificationCalled = false
    public var disableNotificationError: Error? = nil
    
    /// Chunks to emit when observeMemfaultChunks is subscribed
    public var stubbedChunks: [Data] = []
    public var stubbedChunkError: Error?
    
    public override func readSupportedFeatures() async throws -> UInt32 {
        switch readSupportedFeaturesReturnValue {
        case .success(let v): return v
        case .failure(let e): throw e
        }
    }
    
    public override func readDeviceIdentifier() async throws -> String {
        switch readDeviceIdentifierReturnValue {
        case .success(let v): return v
        case .failure(let e): throw e
        }
    }
    
    public override func readDataUri() async throws -> String {
        switch readDataUriReturnValue {
        case .success(let v): return v
        case .failure(let e): throw e
        }
    }
    
    public override func readAuthorization() async throws -> String {
        switch readAuthorizationReturnValue {
        case .success(let v): return v
        case .failure(let e): throw e
        }
    }
    
    public override func enableDataExportNotification() async throws {
        enableNotificationCalled = true
        if let error = enableNotificationError { throw error }
    }
    
    public override func disableDataExportNotification() async throws {
        disableNotificationCalled = true
        if let error = disableNotificationError { throw error }
    }
    
    public override func observeMemfaultChunks(checkConnection: Bool) -> AsyncThrowingStream<Data, Error> {
        let chunks = stubbedChunks
        let error = stubbedChunkError
        return AsyncThrowingStream { continuation in
            Task {
                for chunk in chunks { continuation.yield(chunk) }
                if let e = error { continuation.finish(throwing: e) }
                else { continuation.finish() }
            }
        }
    }
}

// MARK: - BleDeviceSession backed by MockBleMdsClient

class MockMdsBleDeviceSession: BleDeviceSession {
    let mockMdsClient: MockBleMdsClient
    private let mdsClientBase: BleGattClientBase
    
    init(mockMdsClient: MockBleMdsClient) {
        self.mockMdsClient = mockMdsClient
        self.mdsClientBase = mockMdsClient
        super.init(UUID())
        state = .sessionOpen
    }
    
    override func isConnectable() -> Bool { return true }
    
    override func fetchGattClient(_ serviceUuid: CBUUID) -> BleGattClientBase? {
        serviceUuid == BleMdsClient.MDS_SERVICE ? mdsClientBase : nil
    }
}

// MARK: - ServiceClientUtils stub for MDS tests

class MockMdsServiceClientUtils: PolarServiceClientUtils {
    var stubSession: MockMdsBleDeviceSession?
    var stubError: Error?
    
    required init(listener: CBDeviceListenerImpl) {
        super.init(listener: listener)
    }
    
    override func sessionMdsClientReady(_ identifier: String) throws -> BleDeviceSession {
        if let error = stubError { throw error }
        return stubSession!
    }
    
    override func sessionFtpClientReady(_ identifier: String) throws -> BleDeviceSession {
        throw PolarErrors.serviceNotFound
    }
}

// MARK: - PolarBleApiImpl subclass for Memfault tests

class MockMdsBleApiImpl: PolarBleApiImpl {
    required init(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>, restoreIdentifier: String? = nil) {
        fatalError("use init(mockMdsSession:)")
    }
    
    init(mockMdsSession: MockMdsBleDeviceSession) {
        self.mockMdsSession = mockMdsSession
        super.init(DispatchQueue(label: "test.mds"), features: [], restoreIdentifier: nil)
    }
    
    let mockMdsSession: MockMdsBleDeviceSession
    
    private lazy var _mdsServiceUtils: MockMdsServiceClientUtils = {
        let utils = MockMdsServiceClientUtils(listener: MockCBDeviceListenerImpl())
        utils.stubSession = mockMdsSession
        return utils
    }()
    
    override var serviceClientUtils: PolarServiceClientUtils { _mdsServiceUtils }
}

class MockPolarBleLowLevelApi: PolarBleLowLevelApi {
    
    var createFolderCalls: [(identifier: String, folderPath: String)] = []
    var createFolderShouldThrow: Error?
    
    func readFile(identifier: String, filePath: String) async throws -> Data? {
        fatalError("Not implemented")
    }
    
    func writeFile(identifier: String, filePath: String, fileData: Data) async throws {
        fatalError("Not implemented")
    }
    
    func deleteFileOrDirectory(identifier: String, filePath: String) async throws {
        fatalError("Not implemented")
    }
    
    func getFileList(identifier: String, directoryPath: String, recurseDeep: Bool) async throws -> [String] {
        fatalError("Not implemented")
    }
    
    func createFolder(identifier: String, folderPath: String) async throws {
        createFolderCalls.append((identifier: identifier, folderPath: folderPath))
        if let error = createFolderShouldThrow {
            throw error
        }
    }
}
