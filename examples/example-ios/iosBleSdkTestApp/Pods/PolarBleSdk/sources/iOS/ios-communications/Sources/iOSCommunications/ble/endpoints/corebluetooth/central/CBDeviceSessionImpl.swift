
import Foundation
import CoreBluetooth
import RxSwift

class CBDeviceSessionImpl: BleDeviceSession, CBPeripheralDelegate, BleAttributeTransportProtocol {
    private(set) var peripheral: CBPeripheral
    private let central: CBCentralManager
    private let scanner: CBScanningProtocol
    private var serviceMonitors = AtomicList<RxObserver<CBUUID>>()
    private var serviceCount = AtomicInteger.init(initialValue: 0)
    private var attNotifyQueue = [CBCharacteristic]()
    private let queueBle: DispatchQueue
    private let queue: DispatchQueue
    
    // non nil if there is pending write to be finished. Write can be completed by disposing the disposable.
    fileprivate var pendingPeripheralWrite:Disposable? = nil
    
    private static let WRITE_FREEZE_SAFEGUARD_TIMEOUT_MS = 20
    
    init(peripheral: CBPeripheral,
         central: CBCentralManager,
         scanner: CBScanningProtocol,
         factory: BleGattClientFactory,
         queueBle: DispatchQueue,
         queue: DispatchQueue) {
        self.peripheral = peripheral
        self.scanner = scanner
        self.central = central
        self.queueBle = queueBle
        self.queue = queue
        super.init(peripheral.identifier)
        self.peripheral.delegate = self
        self.gattClients = factory.loadClients(self)
        // fill advertisement data with name stored in peripheral
        if peripheral.name != nil {
            var advData = [String : AnyObject]()
            advData[CBAdvertisementDataLocalNameKey] = peripheral.name as AnyObject?
            advertisementContent.processAdvertisementData(-100, advertisementData: advData)
        }
    }
    
    func assignNewPeripheral(_ peripheral: CBPeripheral){
        self.peripheral = peripheral
        self.peripheral.delegate = self
    }
    
    deinit {
        self.peripheral.delegate = nil
    }
    
    func updateSessionState(_ newState: BleDeviceSession.DeviceSessionState){
        BleLogger.trace("default session state update from: ", state.description() , " to: ", newState.description())
        previousState = state
        state = newState
    }
    
    fileprivate func fetchService(_ serviceUuid: CBUUID) -> CBService? {
        return peripheral.services?.filter({ (service) -> Bool in
            return service.uuid.isEqual(serviceUuid)
        }).first
    }
    
    fileprivate func fetchCharacteristic(_ service: CBService, characteristicUuid: CBUUID) -> CBCharacteristic? {
        return service.characteristics?.filter({ (chr) -> Bool in
            return chr.uuid.isEqual(characteristicUuid)
        }).first
    }
    
    override func isConnectable() -> Bool {
        var cbconnected = false
        if let services = advertisementContent.advData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] {
            let peripherals = self.central.retrieveConnectedPeripherals(withServices: services)
            cbconnected = peripherals.contains(peripheral)
        }
        return cbconnected || peripheral.state == .connected || advertisementContent.isConnectable
    }
    
    func connected() {
        if peripheral.services == nil || peripheral.services?.count == 0 {
            peripheral.delegate = self
            peripheral.discoverServices(nil)
        } else {
            BleLogger.trace("Using cached services")
            self.peripheral(peripheral, didDiscoverServices: nil)
        }
    }
    
    func reset() {
        for client in gattClients {
            client.disconnected()
        }
        serviceCount.set(0)
        if isBleQueue() {
            attNotifyQueue.removeAll()
        } else {
            queueBle.sync {
                attNotifyQueue.removeAll()
            }
        }
        RxUtils.postErrorAndClearList(serviceMonitors, error: BleGattException.gattDisconnected)
    }
    
    override func monitorServicesDiscovered(_ checkConnection: Bool) -> Observable<CBUUID> {
        var object: RxObserver<CBUUID>!
        return Observable.create{ observer in
            object = RxObserver<CBUUID>.init(obs: observer)
            if self.isConnected() {
                if self.peripheral.services != nil &&
                    self.peripheral.services?.count != 0 &&
                    
                    self.serviceCount.get() >= (self.peripheral.services?.count)! {
                    for service in (self.peripheral.services)! {
                        observer.onNext(service.uuid)
                    }
                    observer.onCompleted()
                } else {
                    if self.serviceCount.get() > 0 && self.peripheral.services != nil {
                        for service in (self.peripheral.services)! {
                            if service.characteristics != nil && service.characteristics?.count != 0 {
                                observer.onNext(service.uuid)
                            }
                        }
                    }
                    self.serviceMonitors.append(object)
                }
            } else {
                observer.onError(BleGattException.gattDisconnected)
            }
            return Disposables.create {
                self.serviceMonitors.remove({ (item) -> Bool in
                    return item === object
                })
            }
        }
    }
    
    // from BleGattTransmitter
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws {
        if isBleQueue() {
            try doTransmitMessage(parent, serviceUuid: serviceUuid, characteristicUuid: characteristicUuid, packet: packet, withResponse: withResponse)
        } else {
            try queueBle.sync {
                try doTransmitMessage(parent, serviceUuid: serviceUuid, characteristicUuid: characteristicUuid, packet: packet, withResponse: withResponse)
            }
        }
    }
    
    // from BleGattTransmitter
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws {
        
        if peripheral.state == CBPeripheralState.connected {
            if let service = fetchService(serviceUuid) {
                if let characteristic = fetchCharacteristic(service, characteristicUuid: characteristicUuid) {
                    attNotifyQueue.append(characteristic)
                    if attNotifyQueue.count == 1 {
                        self.sendNextAttNotify(false, enableNotify: notify)
                    }
                    return
                }
                throw BleGattException.gattCharacteristicNotFound
            }
            throw BleGattException.gattServiceNotFound
        }
        throw BleGattException.gattDisconnected
    }
    
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID , characteristicUuid: CBUUID ) throws{
        if isBleQueue() {
            try doReadValue(parent, serviceUuid:  serviceUuid, characteristicUuid: characteristicUuid)
        } else {
            try queueBle.sync {
                try doReadValue(parent, serviceUuid:  serviceUuid, characteristicUuid: characteristicUuid)
            }
        }
    }
    
    func doTransmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID , characteristicUuid: CBUUID , packet: Data, withResponse: Bool) throws{
        if( peripheral.state == CBPeripheralState.connected ) {
            if let service = fetchService(serviceUuid) {
                if let characteristic = fetchCharacteristic(service, characteristicUuid: characteristicUuid) {
                    if( withResponse && (characteristic.properties.rawValue) & CBCharacteristicProperties.write.rawValue != 0 ) {
                        peripheral.writeValue(packet, for: characteristic, type: CBCharacteristicWriteType.withResponse)
                    } else if( characteristic.properties.rawValue & CBCharacteristicProperties.writeWithoutResponse.rawValue != 0){
                        if(peripheral.canSendWriteWithoutResponse) {
                            peripheral.writeValue(packet, for: characteristic, type:CBCharacteristicWriteType.withoutResponse);
                            parent.serviceDataWritten(characteristicUuid, err: 0)
                        } else {
                            // the delay is used to safe guard the scenario peripheralIsReady() is never called
                            pendingPeripheralWrite = Completable.empty()
                                .delay(RxTimeInterval.milliseconds(CBDeviceSessionImpl.WRITE_FREEZE_SAFEGUARD_TIMEOUT_MS), scheduler: SerialDispatchQueueScheduler(internalSerialQueueName: ""))
                                .do( onDispose: {
                                    self.peripheral.writeValue(packet, for: characteristic, type: CBCharacteristicWriteType.withoutResponse)
                                    parent.serviceDataWritten(characteristicUuid, err: 0)
                                }).subscribe(
                                    onCompleted: {},
                                    onError: { error in
                                        BleLogger.trace("write failed: \(error)")
                                    })
                        }
                    } else {
                        throw BleGattException.gattCharacteristicError
                    }
                    return
                }
                throw BleGattException.gattCharacteristicNotFound
            }
            throw BleGattException.gattServiceNotFound
        }
        throw BleGattException.gattDisconnected
    }
    
    func doReadValue(_ parent: BleGattClientBase, serviceUuid: CBUUID , characteristicUuid: CBUUID ) throws{
        if( peripheral.state == CBPeripheralState.connected ) {
            if let service = fetchService(serviceUuid) {
                if let characteristic = fetchCharacteristic(service, characteristicUuid: characteristicUuid) {
                    peripheral.readValue(for: characteristic)
                    return
                }
                throw BleGattException.gattCharacteristicNotFound
            }
            throw BleGattException.gattServiceNotFound
        }
        throw BleGattException.gattDisconnected
    }
    
    fileprivate func isBleQueue() -> Bool {
        return String.init(cString: __dispatch_queue_get_label(nil)) == queueBle.label
    }
    
    func sendNextAttNotify(_ remove: Bool, enableNotify enabled: Bool = true) {
        if(remove){
            attNotifyQueue.removeFirst()
        }
        if let chr = attNotifyQueue.first {
            BleLogger.trace("send next att notify: \(chr.description)")
            peripheral.setNotifyValue(enabled, for: chr)
        }
    }
    
    func isConnected() -> Bool {
        return self.peripheral.state == CBPeripheralState.connected
    }
    
    func attributeOperationStarted(){
        queue.async {
            self.scanner.stopScanning()
        }
    }
    
    func attributeOperationFinished(){
        queue.async {
            self.scanner.continueScanning()
        }
    }
    
    // from delegate
    func peripheralDidUpdateName(_ peripheral: CBPeripheral) {
        // no need
    }
    
    func peripheral(_ peripheral: CBPeripheral, didModifyServices invalidatedServices: [CBService]) {
        // reset all gatt clients and redo service discovery
        BleLogger.trace("didModifyServices")
        serviceCount.set(0)
        self.peripheral.discoverServices(nil)
    }
    
    func peripheral(_ peripheral: CBPeripheral, didReadRSSI RSSI: NSNumber, error: Error?) {
        // implement if needed
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        BleLogger.trace_if_error("didDiscoverServices: ", error: error)
        if error == nil {
            // BIG NOTE peripheral.maximumWriteValueLengthForType(CBCharacteristicWriteType.WithResponse) returns incorrect mtu!
            let mtu = peripheral.maximumWriteValueLength(for: CBCharacteristicWriteType.withoutResponse)
            BleLogger.trace("MTU SIZE(WithoutResponse): \(mtu)")
            if mtu > 0 {
                for client in gattClients {
                    client.setMtu(mtu)
                }
            }
            if let services = peripheral.services {
                for service in services {
                    BleLogger.trace("service discovered: ",service.uuid.description)
                    fetchGattClient(service.uuid)?.setServiceDiscovered(true)
                    peripheral.discoverCharacteristics(nil, for: service)
                }
            } else {
                BleLogger.error("No services present")
                RxUtils.postErrorAndClearList(serviceMonitors, error: BleGattException.gattServicesNotFound)
            }
        } else {
            RxUtils.postErrorAndClearList(serviceMonitors, error: error!)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverIncludedServicesFor service: CBService, error: Error?) {
        // implement if needed
        BleLogger.trace("didDiscoverIncludedServicesForService")
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        BleLogger.trace_if_error("didDiscoverCharacteristicsForService: ", error: error)
        if error == nil {
            ++serviceCount
            if let client = fetchGattClient(service.uuid) {
                if let chrs = service.characteristics {
                    for chr in chrs {
                        if client.containsCharacteristic(chr.uuid) {
                            client.processCharacteristicDiscovered(chr.uuid, properties: chr.properties.rawValue)
                            if client.containsNotifyCharacteristic(chr.uuid) {
                                attNotifyQueue.append(chr)
                                if attNotifyQueue.count == 1 {
                                    self.sendNextAttNotify(false)
                                }
                            }
                            if client.containsReadCharacteristic(chr.uuid) {
                                peripheral.readValue(for: chr)
                            }
                        }
                    }
                } else {
                    BleLogger.error("Service has no characteristics")
                }
            }
            RxUtils.emitNext(serviceMonitors) { (observer) in
                observer.obs.onNext(service.uuid)
                if serviceCount.get() >= (peripheral.services?.count)! {
                    observer.obs.onCompleted()
                }
            }
        } else {
            RxUtils.postErrorAndClearList(serviceMonitors, error: error!)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        BleLogger.trace_if_error("didUpdateValueFor \(characteristic.uuid): ", error: error)
        if let serviceUuid = characteristic.service?.uuid, let client = fetchGattClient(serviceUuid) {
            if client.containsCharacteristic(characteristic.uuid) {
                let errorCode = (error as NSError?)?.code ?? 0
                let data = characteristic.value ?? Data()
                client.processServiceData(characteristic.uuid, data: data, err: errorCode)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        BleLogger.trace_if_error("didWriteValueForCharacteristic: ", error: error)
        if let serviceUuid = characteristic.service?.uuid, let client = fetchGattClient(serviceUuid) {
            if client.containsCharacteristic(characteristic.uuid) {
                let errorCode = (error as NSError?)?.code ?? 0
                client.serviceDataWritten(characteristic.uuid, err: errorCode)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        BleLogger.trace_if_error("didUpdateNotificationStateForCharacteristic \(characteristic.uuid.uuidString): ", error: error)
        let errorCode = (error as NSError?)?.code ?? 0
        if errorCode == CBError.Code.uuidNotAllowed.rawValue {
            BleLogger.trace("Special handling needed for security re-establish")
            attNotifyQueue.removeAll()
            serviceCount.set(0)
            peripheral.discoverServices(nil)
            return
        }
        if let serviceUuid = characteristic.service?.uuid, let client = fetchGattClient(serviceUuid) {
            if client.containsCharacteristic(characteristic.uuid) {
                let isNotifying = errorCode == 0 ? characteristic.isNotifying : false
                client.notifyDescriptorWritten(characteristic.uuid, enabled: isNotifying, err: errorCode)
            }
        }
        sendNextAttNotify(true)
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverDescriptorsFor characteristic: CBCharacteristic, error: Error?) {
        // implement if needed
        BleLogger.trace("didDiscoverDescriptorsForCharacteristic")
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor descriptor: CBDescriptor, error: Error?) {
        // implement if needed
        BleLogger.trace("didUpdateValueForDescriptor")
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor descriptor: CBDescriptor, error: Error?) {
        // implement if needed
        BleLogger.trace("didWriteValueForDescriptor")
    }
    
    func peripheralIsReady(toSendWriteWithoutResponse peripheral: CBPeripheral) {
        BleLogger.trace("peripheralIsReadyToSendWriteWithoutResponse")
        pendingPeripheralWrite?.dispose()
        pendingPeripheralWrite = nil
    }
}

func == (lhs: CBDeviceSessionImpl, rhs: CBDeviceSessionImpl) -> Bool {
    return lhs === rhs
}
