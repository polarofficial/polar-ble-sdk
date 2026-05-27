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
    }
    let mockDeviceSession: MockBleDeviceSession
    
    override var serviceClientUtils: PolarServiceClientUtils {
        return MockPolarServiceClientUtils(listener: MockCBDeviceListenerImpl(), session: mockDeviceSession)
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
    var mockMedianRssi: Int32 = -70
    var mockIsConnectable: Bool = true
    var mockContainsService: Bool = true

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
                return sess.advertisementContent.medianRssi >= rssi &&
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

// MARK: - MockBlePmdClient

class MockBlePmdClient: BlePmdClient {
    var querySettingsCalls: [(type: PmdMeasurementType, recordingType: PmdRecordingType)] = []
    var querySettingsReturnValue: Result<PmdSetting, Error>?

    var queryFullSettingsCalls: [(type: PmdMeasurementType, recordingType: PmdRecordingType)] = []
    var queryFullSettingsReturnValue: Result<PmdSetting, Error>?

    var readFeatureCalls: [Bool] = []
    var readFeatureReturnValue: Result<Set<PmdMeasurementType>, Error>?

    var readMeasurementStatusCalls: Int = 0
    var readMeasurementStatusReturnValue: Result<[(PmdMeasurementType, PmdActiveMeasurement)], Error>?

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
}

// MARK: - BleDeviceSession backed by MockBlePmdClient

class MockPmdBleDeviceSession: BleDeviceSession {
    let mockPmdClient: MockBlePmdClient
    private let pmdClientBase: BleGattClientBase

    init(mockPmdClient: MockBlePmdClient) {
        self.mockPmdClient = mockPmdClient
        self.pmdClientBase = unsafeBitCast(mockPmdClient, to: BleGattClientBase.self)
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
        queryCalls.append((id: id, parameters: parameters))
        if !queryReturnValues.isEmpty {
            let result = queryReturnValues.removeFirst()
            switch result {
            case .success(let data): return NSData(data: data)
            case .failure(let error): throw error
            }
        }
        switch queryReturnValue ?? .success(Data()) {
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
