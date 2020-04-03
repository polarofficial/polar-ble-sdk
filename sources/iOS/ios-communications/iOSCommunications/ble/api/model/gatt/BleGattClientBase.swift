
import Foundation
import CoreBluetooth
import RxSwift

public class BleGattClientBase: Hashable {
    public func hash(into hasher: inout Hasher) {
         hasher.combine(ObjectIdentifier(self).hashValue)
    }
    var characteristics = Set<CBUUID>()
    var characteristicsRead = Set<CBUUID>()
    var characteristicsNotification = [[CBUUID : AtomicInteger]]()
    let availableCharacteristics = AtomicList<CBUUID>()
    let availableReadableCharacteristics = AtomicList<CBUUID>()
    let serviceUuid: CBUUID
    weak var gattServiceTransmitter: BleAttributeTransportProtocol?
    let serviceDiscovered = AtomicBoolean(initialValue: false)
    var mtuSize = 20
    public let ATT_NOTIFY_OR_INDICATE_OFF = 0xff
    
    public var baseSerialDispatchQueue: SerialDispatchQueueScheduler!
    public let baseConcurrentDispatchQueue = ConcurrentDispatchQueueScheduler.init(queue: DispatchQueue(label: "BaseConcurrentDispatchQueue", attributes: DispatchQueue.Attributes.concurrent))

    static func address<T: AnyObject>(o: T) -> Int {
        return unsafeBitCast(o, to: Int.self)
    }
    
    class NotificationWaitObserver {
        let obs: RxSwift.PrimitiveSequenceType.CompletableObserver
        let uuid: CBUUID
        init(_ obs: @escaping RxSwift.PrimitiveSequenceType.CompletableObserver, uuid: CBUUID){
            self.obs = obs
            self.uuid = uuid
        }
    }
    
    var notificationWaitObservers = AtomicList<NotificationWaitObserver>()
    var serviceWaitObservers = AtomicList<RxObserverCompletable>()
    
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
    
    func addCharacteristicNotification( _ chr: CBUUID ) {
        characteristics.insert(chr)
        if(!containsNotifyCharacteristic(chr)){
            characteristicsNotification.append([chr : AtomicInteger.init(initialValue: -1)])
        }
    }
    
    func containsCharacteristic( _ chr: CBUUID ) -> Bool {
        return characteristics.contains(chr)
    }
    
    public func containsAvailableCharacteristic( _ chr: CBUUID ) -> Bool {
        return availableCharacteristics.fetch({ (uuid) -> Bool in
            return uuid.isEqual(chr)
        }) != nil
    }
    
    func containsNotifyCharacteristic( _ chr: CBUUID ) -> Bool {
        return characteristicsNotification.contains { (pair) -> Bool in
            return pair.first?.key.isEqual(chr) ?? false
        }
    }
    
    func containsReadCharacteristic( _ chr: CBUUID ) -> Bool {
        return characteristicsRead.contains(chr)
    }
    
    func notificationAtomicInteger(_ chr: CBUUID) -> AtomicInteger? {
        return characteristicsNotification.first(where: { (pair) -> Bool in
            return pair.first?.key.isEqual(chr) ?? false
        })?.first?.value ?? nil
    }
    
    // from protocol
    public func disconnected(){
        serviceDiscovered.set(false)
        mtuSize = 20
        availableCharacteristics.removeAll()
        availableReadableCharacteristics.removeAll()
        characteristicsNotification.forEach { (pair) in
            pair.first?.value.set(-1)
        }
        RxUtils.emitNext(notificationWaitObservers) { (observer) in
            observer.obs(.error(BleGattException.gattDisconnected))
        }
        notificationWaitObservers.removeAll()
        RxUtils.postErrorOnCompletableAndClearList(serviceWaitObservers, error: BleGattException.gattDisconnected)
    }
    
    public func processServiceData(_ chr: CBUUID , data: Data , err: Int ){
        assert(false, "processServiceData not overridden by parent class")
    }
    
    public func setCharacteristicNotify(_ chr: CBUUID, enabled: Bool, err: Int){
        if let pair = characteristicsNotification.first(where: { (p) -> Bool in
            return p.first?.key.isEqual(chr) ?? false
        }) {
            if err == 0 {
                if enabled {
                    pair.first?.value.set(err)
                } else {
                    pair.first?.value.set(ATT_NOTIFY_OR_INDICATE_OFF)
                }
            } else {
                pair.first?.value.set(err)
            }
        }
        let list = notificationWaitObservers.list().filter { (observer) -> Bool in
            return observer.uuid.isEqual(chr)
        }
        for object in list {
            if enabled {
                object.obs(.completed)
            } else {
                object.obs(.error(BleGattException.gattCharacteristicNotifyNotEnabled))
            }
        }
    }
    
    public func serviceDataWritten(_ chr: CBUUID, err: Int){
        // implement if needed
    }
    
    public func processCharacteristicDiscovered(_ characteristic: CBUUID, properties: UInt){
        if !availableCharacteristics.list().contains(characteristic) {
            availableCharacteristics.append(characteristic)
        }
        if !availableReadableCharacteristics.list().contains(characteristic) &&
           (properties & CBCharacteristicProperties.read.rawValue) != 0 {
            availableReadableCharacteristics.append(characteristic)
        }
    }
    
    public func clientReady(_ checkConnection: Bool) -> Completable {
        // client should override this to implement ready callback
        return Completable.empty()
    }
    
    public func setServiceDiscovered(_ value: Bool, serviceUuid: CBUUID){
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
        if let integer = notificationAtomicInteger(uuid) {
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
    
    /// waits notification to be enabled
    ///
    /// - Parameters:
    ///   - chr: characteristic uuid to check
    ///   - checkConnection: check intial connection from transport
    /// - Returns: Observable, complete produced when notification is enabled or is allready enabled
    //                         error produced if attribute operation fails, or device disconnects
    public func waitNotificationEnabled(_ chr: CBUUID, checkConnection: Bool) -> Completable {
        let integer = notificationAtomicInteger(chr)
        var subscriber: NotificationWaitObserver!
        return Completable.create{ observer in
            subscriber = NotificationWaitObserver(observer, uuid: chr)
            if integer != nil {
                if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                    if integer?.get() != -1 {
                        if integer?.get() == 0 {
                            observer(.completed)
                        } else if integer?.get() == self.ATT_NOTIFY_OR_INDICATE_OFF {
                            observer(.error(BleGattException.gattCharacteristicNotifyNotEnabled))
                        } else {
                            observer(.error(BleGattException.gattAttributeError(errorCode: (integer?.get())!)))
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
