import Foundation
import CoreBluetooth
import Combine


public protocol SDKCBCentralManagerDelegate: CBCentralManagerDelegate {
   func centralManager(_ central: CBCentralManager, connectionTimeoutDidOccurFor peripheral: CBPeripheral, error: Error?)
}

public class SDKCBCentralManager: CBCentralManager {
    fileprivate var queue: DispatchQueue?
    public init(delegate: SDKCBCentralManagerDelegate, queue: dispatch_queue_t?, options: [String: Any]? = nil) {
        self.queue = queue
        super.init(delegate: delegate, queue: queue, options: options)
    }

    public required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    public override func connect(_ peripheral: CBPeripheral, options: [String : Any]? = nil) {
        super.connect(peripheral, options: options)
        queue?.asyncAfter(deadline: .now() + 3) {
            if peripheral.state == .connecting  {
                super.cancelPeripheralConnection(peripheral)
                (self.delegate as? SDKCBCentralManagerDelegate)?.centralManager(self, connectionTimeoutDidOccurFor: peripheral, error: CBError(_nsError: NSError(domain: CBError.errorDomain, code: CBError.connectionTimeout.rawValue, userInfo: nil)))
            }
        }
    }
}


public class CBDeviceListenerImpl: NSObject, SDKCBCentralManagerDelegate {
    
    private let SESSION_TEAR_DOWN_TIMEOUT_MS = 1000
    
    fileprivate let restoreIdentifier: String?
    fileprivate lazy var manager: SDKCBCentralManager = {
        var options: [String: Any]? = nil
        if let restoreId = restoreIdentifier {
            options = [CBCentralManagerOptionRestoreIdentifierKey: restoreId]
        }
        return SDKCBCentralManager(delegate: self, queue: queueBle, options: options)
    }()
    
    fileprivate let sessions = AtomicList<CBDeviceSessionImpl>()
    fileprivate var queue: DispatchQueue
    fileprivate var queueBle: DispatchQueue
    fileprivate let connectionStateSubject = PassthroughSubject<(session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState), Never>()
    fileprivate let bleStateSubject = CurrentValueSubject<BleState, Never>(.unknown)
    fileprivate lazy var scanner = CBScanner(manager, queue: queue, sessions: sessions)
    fileprivate let factory: BleGattClientFactory
    private var readRSSITimer: Timer!
    private var cancellables = Set<AnyCancellable>()
    public var automaticH10Mapping = false
    public var automaticReconnection = true
    public var scanPreFilter: ((_ content: BleAdvertisementContent) -> Bool)?
    public var servicesToScanFor: [CBUUID]? {
        didSet {
            scanner.setServices(servicesToScanFor)
        }
    }
    public weak var deviceSessionStateObserver: BleDeviceSessionStateObserver?
    public weak var powerStateObserver: BlePowerStateObserver? {
        didSet {
            powerStateObserver?.powerStateChanged(bleStateSubject.value)
        }
    }
    
    public init(_ queue: DispatchQueue, clients: [(_ transport: BleAttributeTransportProtocol) -> BleGattClientBase], identifier: Int, restoreIdentifier: String? = nil) {
        self.queue = queue
        self.factory = BleGattClientFactory(clients)
        self.queueBle = DispatchQueue(label: "CBDeviceListenerImplQueue\(identifier)", attributes: [])
        self.restoreIdentifier = restoreIdentifier
        super.init()
    }
    
    fileprivate func session(_ peripheral: CBPeripheral) -> CBDeviceSessionImpl? {
        return sessions.fetch( { (item: CBDeviceSessionImpl) -> Bool in
            return (item.peripheral.identifier == peripheral.identifier)
        })
    }
    
    fileprivate func updateSessionState(_ session: CBDeviceSessionImpl, state: BleDeviceSession.DeviceSessionState, error: Error? = nil) {
        session.updateSessionState(state, error: error)
        connectionStateSubject.send((session: session, state: state))
        deviceSessionStateObserver?.stateChanged(session)
        if scanner.scanningNeeded() {
            scanner.enableScan()
        } else {
            scanner.disableScan()
        }
    }
    
    @available(iOS 10.0, *)
    fileprivate func btState2String(_ state: CBManagerState) -> String {
        switch state {
        case .unknown:
            return "Unknown"
        case .resetting:
            return "Resetting"
        case .unsupported:
            return "Unsupported"
        case .unauthorized:
            return "Unauthorized"
        case .poweredOff:
            return "PoweredOff"
        case .poweredOn:
            return "PoweredOn"
        @unknown default:
            return "Unknown"
        }
    }
    
    // cb central manager callbacks
    public func centralManagerDidUpdateState(_ central: CBCentralManager){
        queue.async(execute: {
            if #available(iOS 10.0, *) {
                BleLogger.trace("state update to: ", self.btState2String(central.state))
            }
            switch central.state {
            case .unknown:
                break
            case .resetting:
                fallthrough
            case .unsupported:
                fallthrough
            case .unauthorized:
                fallthrough
            case .poweredOff:
                self.scanner.powerOff()
                let sessionList = self.sessions.list()
                for session in sessionList {
                    if session.state == .sessionOpening ||
                        session.state == .sessionOpen ||
                        session.state == .sessionClosing {
                        self.handleDisconnected(session, error: nil)
                        session.reset()
                    }
                }
                if central.state == .resetting {
                    // clear device list
                    self.sessions.removeAll()
                }
                break
            case .poweredOn:
                // Handle peripherals restored via willRestoreState.
                // Only restore sessions that were actively open or opening when the app
                // was suspended — i.e. previousState was .sessionOpen or .sessionOpening.
                // Sessions whose previousState is .sessionClosed or .sessionClosing were
                // intentionally disconnected by the user before the app was killed and
                // must NOT be auto-reconnected on restart.
                for session in self.sessions.list() {
                    guard session.previousState == .sessionOpen ||
                          session.previousState == .sessionOpening else {
                        BleLogger.trace("Skipping restore for session \(session.advertisementContent.name): previousState was \(session.previousState.description())")
                        continue
                    }
                    if session.peripheral.state == .connected && session.state == .sessionClosed {
                        session.connected()
                        self.updateSessionState(session, state: .sessionOpen)
                    } else if session.peripheral.state == .connecting && session.state == .sessionClosed {
                        self.updateSessionState(session, state: .sessionOpening)
                    }
                }
                self.scanner.powerOn()
            @unknown default:
                break
            }
            let newBleState = BleState(rawValue: self.manager.state.rawValue) ?? BleState.unknown
            self.bleStateSubject.send(newBleState)
            self.powerStateObserver?.powerStateChanged(newBleState)
        })
    }
    
    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        if self.session(peripheral) == nil, let filter = self.scanPreFilter {
            let advContent = BleAdvertisementContent()
            
            if peripheral.name != nil && advertisementData[CBAdvertisementDataLocalNameKey] == nil {
                var advData = [String : AnyObject]()
                advData[CBAdvertisementDataLocalNameKey] = peripheral.name as AnyObject?
                advContent.processAdvertisementData(Int32(RSSI.intValue), advertisementData: advData)
            } else {
                advContent.processAdvertisementData(Int32(RSSI.intValue), advertisementData: advertisementData as [String : AnyObject])
            }
            
            if !filter(advContent) {
                return
            }
        }
        handleDeviceDiscovered(central, didDiscover: peripheral, advertisementData: advertisementData, rssi: RSSI)
    }
    
    private func handleDeviceDiscovered(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        var session = self.session(peripheral)
        if automaticH10Mapping &&
            peripheral.name?.contains("H10") ?? false ||
            peripheral.name?.contains("H9") ?? false {
            let items = peripheral.name?.split(separator: " ").map(String.init)
            let deviceId = items?.last
            if let old = self.sessions.fetch({ (impl) -> Bool in
                return impl.advertisementContent.polarDeviceIdUntouched == deviceId
            }) {
                if old != session {
                    BleLogger.trace("found old h10 or H9, mapped to new")
                    old.assignNewPeripheral(peripheral)
                    sessions.remove { (impl) -> Bool in
                        return impl == session
                    }
                    session = old
                }
            }
        }
        
        if session == nil {
            self.sessions.append(CBDeviceSessionImpl(peripheral: peripheral, central: self.manager, scanner: self, factory: self.factory, queueBle: self.queueBle, queue: self.queue))
            BleLogger.trace("new peripheral discovered: ", peripheral.description)
        } else {
            BleLogger.trace("peripheral with session discovered: ", peripheral.description)
        }
        
        guard let sess = self.session(peripheral) else {
            BleLogger.error("out of memory")
            return
        }
        
        queue.async(execute: {
            sess.advertisementContent.processAdvertisementData(RSSI.int32Value, advertisementData: advertisementData)
            
            self.scanner.scanSubject.send(sess)
            
            if sess.state == .sessionOpenPark {
                if sess.isConnectable() {
                    self.updateSessionState(sess, state: .sessionOpening)
                    self.manager.connect(sess.peripheral, options: nil)
                } else {
                    BleLogger.trace("connection attempt deferred due to reason device is non connectable")
                }
            }
        })
    }
    
    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral){
        BleLogger.trace("didConnect: ", peripheral.description)
        queue.async(
            execute: {
                if let device = self.session(peripheral) {
                    device.connected()
                    self.updateSessionState(device,state: BleDeviceSession.DeviceSessionState.sessionOpen)
                    self.startReadRSSI()
                } else {
                    BleLogger.error("didConnect: Unknown peripheral received")
                }
            }
        )
    }
    
    public func centralManager(_ central: CBCentralManager, connectionTimeoutDidOccurFor peripheral: CBPeripheral, error: (any Error)?) {
        BleLogger.trace("connectionTimeoutDidOccurFor: ", peripheral.description, "error: ", error?.localizedDescription ?? "error reason unknown")
        queue.async(execute: {
            if let device = self.session(peripheral) {
                self.handleDisconnected(device, error: error)
            } else {
                BleLogger.error("connectionTimeoutDidOccurFor: Unknown peripheral received")
            }
        }
        )
    }
    
    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?){
        BleLogger.trace("didFailToConnect: ", peripheral.description, "error: ", error?.localizedDescription ?? "error reason unknown")
        queue.async(execute: {
            if let device = self.session(peripheral) {
                self.handleDisconnected(device, error: error)
            } else {
                BleLogger.error("didFailToConnect: Unknown peripheral received")
            }
        }
        )
    }
    
    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?){
        BleLogger.trace("didDisconnectPeripheral: ", peripheral.description)
        queue.async(execute: {
            if let device = self.session(peripheral) {
                self.handleDisconnected(device, error: error)
                self.stopReadRSSI()
                device.reset()
            } else {
                BleLogger.error("didDisconnectPeripheral: Unknown peripheral received")
            }
        })
    }
    
    func startReadRSSI() {
        if self.readRSSITimer == nil {
            self.readRSSITimer = Timer.scheduledTimer(timeInterval: 1.0, target: self, selector: #selector(self.readRSSIForConnectedSession), userInfo: nil, repeats: true)
        }
    }

    @objc func readRSSIForConnectedSession() {
        if let session = self.sessions.list().first(where: { $0.state == .sessionOpen }) {
            session.peripheral.readRSSI()
        }
    }

    func stopReadRSSI() {
        if (self.readRSSITimer != nil) {
            self.readRSSITimer.invalidate()
            self.readRSSITimer = nil
        }
    }

    fileprivate func handleDisconnected(_ session: CBDeviceSessionImpl, error: Error?) {
        
        let canTryReconnect = !(error?.indicatesBLEPairingProblem ?? false)
        
        if automaticReconnection && canTryReconnect {
            switch (session.state) {
            case .sessionOpen where session.connectionType == .directConnection && self.blePowered():
                updateSessionState(session, state: .sessionOpening)
                manager.connect((session).peripheral, options: nil)
            case .sessionOpen: fallthrough
            case .sessionOpening:
                updateSessionState(session, state: .sessionOpenPark)
            case .sessionClosing:
                updateSessionState(session, state: .sessionClosed)
            default:
                break
            }
        } else {
            updateSessionState(session, state: .sessionClosed, error: error)
        }
    }
    
    public func centralManager(_ central: CBCentralManager, connectionEventDidOccur event: CBConnectionEvent, for peripheral: CBPeripheral) {
        // handle if needed
    }
    
    public func centralManager(_ central: CBCentralManager, willRestoreState dict: [String : Any]) {
        BleLogger.trace("willRestoreState called")
        guard let peripherals = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] else {
            BleLogger.trace("willRestoreState: no peripherals to restore")
            return
        }
        for peripheral in peripherals {
            BleLogger.trace("willRestoreState: restoring peripheral \(peripheral.identifier) (state: \(peripheral.state.rawValue))")
            if session(peripheral) == nil {
                let restoredSession = CBDeviceSessionImpl(
                    peripheral: peripheral,
                    central: self.manager,
                    scanner: self,
                    factory: self.factory,
                    queueBle: self.queueBle,
                    queue: self.queue
                )
                // Set delegate so we don't miss callbacks, but do NOT call
                // connected() or trigger state transitions yet — the central
                // manager is not powered on at this point.
                peripheral.delegate = restoredSession
                sessions.append(restoredSession)
            }
        }
    }
}

extension CBDeviceListenerImpl: CBScanningProtocol {
    public func stopScanning(){
        queue.async(execute: {
            self.scanner.stopScan()
        })
    }
    
    public func continueScanning(){
        queue.async(execute: {
            self.scanner.startScan()
        })
    }
}

extension CBDeviceListenerImpl: BleDeviceListener {
    
    public func blePowered() -> Bool{
        return manager.state == .poweredOn
    }
    
    public func monitorBleState() -> AnyPublisher<BleState, Error> {
        return bleStateSubject
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    public func search(_ uuids: [CBUUID]?, identifiers: [UUID]?, fetchKnownDevices: Bool) -> AnyPublisher<BleDeviceSession, Error> {
        return monitorBleState()
            .filter { $0 == .poweredOn }
            .prefix(1)
            .flatMap { _ -> AnyPublisher<BleDeviceSession, Error> in
                self.scanner.addClient()
                
                var foundPeripherals = [CBPeripheral]()
                
                if uuids != nil {
                    foundPeripherals = self.manager.retrieveConnectedPeripherals(withServices: uuids!)
                        .filter {
                            return identifiers?.contains($0.identifier) ?? true
                        }
                } else if identifiers != nil {
                    foundPeripherals = self.manager.retrievePeripherals(withIdentifiers: (identifiers)!)
                }
                
                for device in foundPeripherals {
                    if self.session(device) == nil {
                        var advData = [String : Any]()
                        if device.name != nil {
                            advData[CBAdvertisementDataLocalNameKey] = device.name as Any?
                        }
                        if uuids != nil {
                            advData[CBAdvertisementDataServiceUUIDsKey] = uuids as Any?
                        }
                        self.centralManager(self.manager, didDiscover: device, advertisementData: advData, rssi: -20)
                    }
                }
                if fetchKnownDevices {
                    self.sessions.items.forEach { (sess) in
                        BleLogger.trace("search returning session \(sess.peripheral)")
                        self.scanner.scanSubject.send(sess)
                    }
                }
                
                return self.scanner.scanSubject
                    .setFailureType(to: Error.self)
                    .handleEvents(receiveCancel: {
                        self.scanner.removeClient()
                    })
                    .eraseToAnyPublisher()
            }
            .eraseToAnyPublisher()
    }
    
    public func openSessionDirect(_ session: BleDeviceSession){
        if self.blePowered() {
            switch session.state {
            case .sessionClosed where !session.isConnectable():
                updateSessionState(session as! CBDeviceSessionImpl, state: .sessionOpenPark)
            case .sessionClosed:
                updateSessionState(session as! CBDeviceSessionImpl, state: .sessionOpening)
                manager.connect((session as! CBDeviceSessionImpl).peripheral, options: nil)
            case .sessionClosing:
                updateSessionState(session as! CBDeviceSessionImpl, state: .sessionOpenPark)
            default:
                break
            }
        } else if session.state == .sessionClosed {
            updateSessionState(session as! CBDeviceSessionImpl, state: .sessionOpenPark)
        }
    }
    
    public func monitorDeviceSessionState() -> AnyPublisher<(session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState), Error> {
        return connectionStateSubject
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }
    
    public func closeSessionDirect(_ session: BleDeviceSession){
        switch (session.state) {
        case .sessionOpening: fallthrough
        case .sessionOpen:
            // Transition to .sessionClosing BEFORE starting teardown so that
            // didDisconnectPeripheral (which may fire during or before the sink
            // callback) sees .sessionClosing in handleDisconnected and correctly
            // moves to .sessionClosed instead of .sessionOpenPark.
            updateSessionState(session as! CBDeviceSessionImpl, state: .sessionClosing)
            completeSessionClose(session)
        case .sessionOpenPark where session.previousState == .sessionClosing:
            updateSessionState(session as! CBDeviceSessionImpl, state: .sessionClosing)
        case .sessionOpenPark:
            updateSessionState(session as! CBDeviceSessionImpl, state: .sessionClosed)
        default: break
        }
    }
    
    public func removeAllSessions(_ inState: Set<BleDeviceSession.DeviceSessionState>) -> Int{
        return sessions.removeIf({ (_ session: CBDeviceSessionImpl) -> Bool in
            return inState.contains(session.state)
        })
    }
    
    public func removeAllSessions() -> Int{
        return removeAllSessions(Set(arrayLiteral: .sessionClosed,.sessionOpenPark))
    }
    
    public func allSessions() -> [BleDeviceSession]{
        return sessions.list()
    }
    
    private func completeSessionClose(_ session: BleDeviceSession) {
        let tearDownPublishers = session.gattClients.map { client -> AnyPublisher<Never, Error> in
            return client.tearDown()
        }
        
        Publishers.MergeMany(tearDownPublishers)
            .timeout(.milliseconds(SESSION_TEAR_DOWN_TIMEOUT_MS), scheduler: DispatchQueue.main)
            .catch { error -> Empty<Never, Error> in
                BleLogger.trace("Catched error while closing the session \(session.advertisementContent.name). Error \(error)")
                return Empty()
            }
            .sink(receiveCompletion: { [weak self] _ in
                guard let self = self else { return }
                // State was already set to .sessionClosing in closeSessionDirect.
                // Just cancel the peripheral connection — didDisconnectPeripheral
                // will fire and handleDisconnected will move it to .sessionClosed.
                self.manager.cancelPeripheralConnection((session as! CBDeviceSessionImpl).peripheral)
                BleLogger.trace("Completed session close tear down for session \(session.advertisementContent.name)")
            }, receiveValue: { _ in })
            .store(in: &cancellables)
    }
}
