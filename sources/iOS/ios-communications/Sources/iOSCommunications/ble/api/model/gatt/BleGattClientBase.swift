
import Foundation
import CoreBluetooth
import RxSwift

public class BleGattClientBase: Hashable {
    public func hash(into hasher: inout Hasher) {
        hasher.combine(ObjectIdentifier(self).hashValue)
    }
    
    static func address<T: AnyObject>(o: T) -> Int {
        return unsafeBitCast(o, to: Int.self)
    }
    
    var baseSerialDispatchQueue: SerialDispatchQueueScheduler!
    let baseConcurrentDispatchQueue = ConcurrentDispatchQueueScheduler.init(queue: DispatchQueue(label: "BaseConcurrentDispatchQueue", attributes: DispatchQueue.Attributes.concurrent))
    
    weak var gattServiceTransmitter: BleAttributeTransportProtocol?
    var mtuSize = 20
    
    private struct notificationChr {
        let uuid: CBUUID
        var state: AtomicInteger = AtomicInteger.init(initialValue: -1)
        let disableOnDisconnect: Bool
    }
    
    private var notificationCharacteristics = [notificationChr]()
    private var characteristics = Set<CBUUID>()
    private let availableCharacteristics = AtomicList<CBUUID>()
    private var characteristicsRead = Set<CBUUID>()
    private let availableReadableCharacteristics = AtomicList<CBUUID>()
    private let serviceUuid: CBUUID
    private let serviceDiscovered = AtomicBoolean(initialValue: false)
    private var disposeBag = DisposeBag()
    
    let ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN = -1
    let ATT_NOTIFY_OR_INDICATE_ON = 0
    let ATT_NOTIFY_OR_INDICATE_OFF = 1
    let ATT_NOTIFY_OR_INDICATE_ERROR = 2
    
    private var notificationWaitObservers = AtomicList<NotificationWaitObserver>()
    private var serviceWaitObservers = AtomicList<RxObserverCompletable>()
    
    private class NotificationWaitObserver {
        let obs: RxSwift.PrimitiveSequenceType.CompletableObserver
        let uuid: CBUUID
        init(_ obs: @escaping RxSwift.PrimitiveSequenceType.CompletableObserver, uuid: CBUUID){
            self.obs = obs
            self.uuid = uuid
        }
    }
    
    init(serviceUuid: CBUUID, gattServiceTransmitter: BleAttributeTransportProtocol){
        self.gattServiceTransmitter = gattServiceTransmitter
        self.serviceUuid = serviceUuid
        self.baseSerialDispatchQueue = SerialDispatchQueueScheduler.init(internalSerialQueueName: "BaseDispatchQueue" + serviceUuid.uuidString + "obj\( NSString(format: "%p", BleGattClientBase.address(o: self)) )")
    }
    
    func addCharacteristicRead( _ chr: CBUUID ) {
        characteristics.insert(chr)
        characteristicsRead.insert(chr)
    }
    
    func addCharacteristic( _ chr: CBUUID ) {
        characteristics.insert(chr)
    }
    
    /// Characteristic notification/indication is automatic enabled on connection establishment
    ///
    /// - Parameters:
    ///     - chr: characteristic which notification is set on
    ///     - disableOnDisconnect: true if enabled notification shall be disabled on disconnection.
    func automaticEnableNotificationsOnConnect(chr: CBUUID, disableOnDisconnect: Bool = false) {
        BleLogger.trace("Automatically enable characteristic notification \(chr.uuidString) on connect. Disable on disconnect: \(disableOnDisconnect)")
        addCharacteristicNotification(chr, disableOnDisconnect:disableOnDisconnect)
    }
    
    /// Enable characteristic notification/indication
    ///
    /// - Parameters:
    ///     - chr: characteristic which notification is set on
    ///     - disableOnDisconnect: true if enabled notification shall be disabled on disconnection.
    func enableCharacteristicNotification(chr: CBUUID, disableOnDisconnect: Bool = false) -> Completable {
        return Completable.create{ [weak self] observer in
            guard let self = self else {
                observer(.completed)
                return Disposables.create {}
            }
            if(!self.containsNotifyCharacteristic(chr)) {
                BleLogger.trace("GATT Base request notification enable for chr: \(chr)")
                self.addCharacteristicNotification(chr, disableOnDisconnect:disableOnDisconnect)
                self.writeCharacteristicNotification(chr: chr, enable: true)
                    .subscribe(onCompleted: {
                        observer(.completed)
                    }, onError: { Error in
                        observer(.error(Error))
                    })
                    .disposed(by: self.disposeBag)
            }
            return Disposables.create {}
        }
    }
    
    /// Disable characteristic notification/indication
    ///
    /// - Parameters:
    ///     - chr: characteristic which notification is set on
    func disableCharacteristicNotification(chr: CBUUID) -> Completable {
        return Completable.create{ [weak self] observer in
            guard let self = self else {
                observer(.completed)
                return Disposables.create {}
            }
            
            if(self.containsNotifyCharacteristic(chr)) {
                BleLogger.trace("GATT Base request notification disable for chr: \(chr)")
                self.writeCharacteristicNotification(chr: chr, enable: false)
                    .subscribe(onCompleted: {
                        observer(.completed)
                    }, onError: { Error in
                        observer(.error(Error))
                    })
                    .disposed(by: self.disposeBag)
            }
            return Disposables.create {}
        }
    }
    
    func containsCharacteristic( _ chr: CBUUID ) -> Bool {
        return characteristics.contains(chr)
    }
    
    func containsNotifyCharacteristic( _ chr: CBUUID ) -> Bool {
        return notificationCharacteristics.contains {
            return $0.uuid.isEqual(chr)
        }
    }
    
    func containsReadCharacteristic( _ chr: CBUUID ) -> Bool {
        return characteristicsRead.contains(chr)
    }
    
    func getNotificationCharacteristicState(_ chr: CBUUID) -> AtomicInteger? {
        return notificationCharacteristics.first {
            return $0.uuid.isEqual(chr)
        }?.state ?? nil
    }
    
    // from protocol
    public func disconnected() {
        serviceDiscovered.set(false)
        mtuSize = 20
        availableCharacteristics.removeAll()
        availableReadableCharacteristics.removeAll()
        notificationCharacteristics.forEach { (pair) in
            pair.state.set(ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN)
        }
        RxUtils.emitNext(notificationWaitObservers) { (observer) in
            observer.obs(.error(BleGattException.gattDisconnected))
        }
        notificationWaitObservers.removeAll()
        RxUtils.postErrorOnCompletableAndClearList(serviceWaitObservers, error: BleGattException.gattDisconnected)
        disposeBag = DisposeBag() // Assigning new object disposes the current disposables
    }
    
    public func processServiceData(_ chr: CBUUID , data: Data , err: Int ) {
        assert(false, "processServiceData not overridden by parent class")
    }
    
    /// Call when notify descriptor is written on Gatt service
    ///
    /// - Parameters:
    ///   - chr: charcteristic written
    ///   - enabled: true if notification was enabled
    ///   - err: error on notification setting change attempt
    public func notifyDescriptorWritten(_ chr: CBUUID, enabled: Bool, err: Int) {
        BleLogger.trace("GATT Base notifyDescriptorWritten for chr: \(chr) enabled: \(enabled) err \(err)")
        
        if let notifChr = notificationCharacteristics.first(where: { return $0.uuid.isEqual(chr)}) {
            if err == 0 && enabled {
                notifChr.state.set(ATT_NOTIFY_OR_INDICATE_ON)
            } else if err == 0 && !enabled {
                notifChr.state.set(ATT_NOTIFY_OR_INDICATE_OFF)
            } else {
                notifChr.state.set(ATT_NOTIFY_OR_INDICATE_ERROR)
            }
        }
        
        let list = notificationWaitObservers.list().filter { (observer) -> Bool in
            return observer.uuid.isEqual(chr)
        }
        
        for object in list {
            if err == 0 {
                object.obs(.completed)
            } else {
                object.obs(.error(BleGattException.gattCharacteristicNotifyError(errorCode: err, errorDescription: "notify description write failed" )))
            }
        }
    }
    
    public func serviceDataWritten(_ chr: CBUUID, err: Int){
        // implement if needed
    }
    
    public func processCharacteristicDiscovered(_ characteristic: CBUUID, properties: UInt) {
        if !availableCharacteristics.list().contains(characteristic) {
            availableCharacteristics.append(characteristic)
        }
        if !availableReadableCharacteristics.list().contains(characteristic) &&
            (properties & CBCharacteristicProperties.read.rawValue) != 0 {
            availableReadableCharacteristics.append(characteristic)
        }
    }
    
    public func clientReady(_ checkConnection: Bool) -> Completable {
        // override in client if required
        return Completable.empty()
    }
    
    public func setServiceDiscovered(_ value: Bool) {
        serviceDiscovered.set(value)
    }
    
    public func isServiceDiscovered() -> Bool {
        return serviceDiscovered.get()
    }
    
    public func serviceBelongsToClient(_ uuid: CBUUID) -> Bool {
        return serviceUuid.isEqual(uuid)
    }
    
    public func setMtu(_ mtuSize: Int ){
        self.mtuSize = mtuSize
    }
    
    func hasAllAvailableCharacteristics(_ list: [CBUUID : AnyObject]) -> Bool {
        return Set(availableCharacteristics.list()).isSubset(of: Set(list.keys))
    }
    
    func hasAllAvailableReadableCharacteristics(_ list: [CBUUID : AnyObject]) -> Bool {
        return Set(availableReadableCharacteristics.list()).isSubset(of: Set(list.keys))
    }
    
    public func isCharacteristicNotificationEnabled(_ uuid: CBUUID) -> Bool {
        if let integer = getNotificationCharacteristicState(uuid) {
            return integer.get() == 0
        }
        return false
    }
    
    public func waitDiscovered(checkConnection: Bool) -> Completable {
        var subscriber: RxObserverCompletable!
        return Completable.create{ observer in
            subscriber = RxObserverCompletable.init(obs: observer)
            if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                if self.serviceDiscovered.get() == true {
                    observer(.completed)
                } else {
                    self.serviceWaitObservers.append(subscriber)
                }
            } else {
                observer(.error(BleGattException.gattDisconnected))
            }
            return Disposables.create {
                self.serviceWaitObservers.remove({ (object: RxObserverCompletable) in
                    return object === subscriber
                })
            }
        }
    }
    
    /// Waits notification to be enabled
    ///
    /// - Parameters:
    ///   - chr: characteristic uuid to check
    ///   - checkConnection: check initial connection from transport
    /// - Returns: Observable, complete produced when notification is enabled or is already enabled. Error produced if attribute operation fails, or device disconnects
    public func waitNotificationEnabled(_ chr: CBUUID, checkConnection: Bool) -> Completable {
        return waitNotification(chr, checkConnection, toBeEnabled: true )
    }
    
    /// Tear down the client before connection closed
    ///
    /// - Returns: Observable, complete produced when tear down is finished.
    public func tearDown() -> Completable {
        // notifications to be disabled before connection close
        if let notification = notificationCharacteristics.first(where: { return $0.state.get() == ATT_NOTIFY_OR_INDICATE_ON && $0.disableOnDisconnect}) {
            return self.disableCharacteristicNotification(chr: notification.uuid)
        } else {
            return Completable.empty()
        }
    }
    
    private func writeCharacteristicNotification(chr: CBUUID, enable: Bool) -> Completable {
        return Completable.create{ observer in
            // set status to unknown state prior to notification write
            if let notifChr = self.notificationCharacteristics.first(where: {return $0.uuid.isEqual(chr)}) {
                notifChr.state.set(self.ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN)
            }
            
            do {
                try self.gattServiceTransmitter?.setCharacteristicNotify(self, serviceUuid: self.serviceUuid, characteristicUuid: chr, notify: enable)
                observer(.completed)
                return Disposables.create {}
            } catch let err {
                observer(.error(err))
                return Disposables.create {}
            }
        }
        .andThen(self.waitNotification(chr, true, toBeEnabled: enable))
    }
    
    private func addCharacteristicNotification(_ chr: CBUUID, disableOnDisconnect:Bool = false) {
        characteristics.insert(chr)
        if(!containsNotifyCharacteristic(chr)){
            let chrNotification = notificationChr(uuid: chr, state: AtomicInteger.init(initialValue: ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN), disableOnDisconnect: disableOnDisconnect)
            notificationCharacteristics.append(chrNotification)
        }
    }
    
    func removeCharacteristicNotification( _ chr: CBUUID ) {
        BleLogger.trace("Remove notification characteristic for \(chr.uuidString)")
        notificationCharacteristics.removeAll {
            $0.uuid.isEqual(chr)
        }
        characteristics.remove(chr);
    }
    
    private func waitNotification(_ chr: CBUUID, _ checkConnection: Bool, toBeEnabled: Bool ) -> Completable {
        return Completable.create { observer in
            let integer = self.getNotificationCharacteristicState(chr)
            let subscriber = NotificationWaitObserver(observer, uuid: chr)
            if integer != nil {
                if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                    if integer?.get() != self.ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN {
                        if (toBeEnabled && integer?.get() == self.ATT_NOTIFY_OR_INDICATE_ON)
                            || (!toBeEnabled && integer?.get() == self.ATT_NOTIFY_OR_INDICATE_OFF) {
                            observer(.completed)
                        } else if toBeEnabled && integer?.get() == self.ATT_NOTIFY_OR_INDICATE_OFF {
                            observer(.error(BleGattException.gattCharacteristicNotifyNotEnabled))
                        } else if !toBeEnabled && integer?.get() == self.ATT_NOTIFY_OR_INDICATE_ON {
                            observer(.error(BleGattException.gattCharacteristicNotifyNotDisabled))
                        } else {
                            observer(.error(BleGattException.gattCharacteristicNotifyError(errorCode: -1, errorDescription: "notify description failed. Waiting for enable: \(toBeEnabled). Error code is not known report as -1" )))
                        }
                    } else {
                        self.notificationWaitObservers.append(subscriber)
                    }
                } else {
                    observer(.error(BleGattException.gattDisconnected))
                }
            } else {
                observer(.error(BleGattException.gattCharacteristicNotFound))
            }
            return Disposables.create {
                self.notificationWaitObservers.remove({ (observer: NotificationWaitObserver) -> Bool in
                    return subscriber === observer
                })
            }
        }
    }
}

public func == (lhs: BleGattClientBase, rhs: BleGattClientBase) -> Bool{
    return lhs === rhs
}
