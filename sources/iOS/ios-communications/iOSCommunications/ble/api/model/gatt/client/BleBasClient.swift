
import Foundation
import CoreBluetooth
import RxSwift

public class BleBasClient: BleGattClientBase {
    
    public static let BATTERY_SERVICE = CBUUID(string: "180F")
    let               BATTERY_LEVEL   = CBUUID(string: "2A19")

    var batteryLevel = AtomicInteger(initialValue: -1)
    var observers    = AtomicList<RxObserver<Int>>()
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BleBasClient.BATTERY_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(BATTERY_LEVEL)
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        self.batteryLevel.set(-1)
        RxUtils.postErrorAndClearList(observers, error: BleGattException.gattDisconnected)
    }
    
    override public func processServiceData(_ chr: CBUUID , data: Data , err: Int ){
        if( err == 0 ){
            var level: Int8=0
            (data as NSData).getBytes(&level, length: MemoryLayout<UInt8>.size)
            batteryLevel.set(Int(level))
            RxUtils.emitNext(observers) { (observer) in
                observer.obs.onNext(Int(level))
            }
        }
    }
    
    // apis
    public func readLevel() throws {
        try self.gattServiceTransmitter?.readValue(self, serviceUuid: BleBasClient.BATTERY_SERVICE, characteristicUuid: self.BATTERY_LEVEL)
    }
    
    /// wait/monitor battery level update, either returns cached value, or waits initial read value
    ///
    /// - Parameter checkConnection: check initial connection
    /// - Returns: Observable stream, complete: non produced
    public func waitBatteryLevelUpdate(_ checkConnection: Bool) -> Observable<Int> {
        var object: RxObserver<Int>!
        return Observable.create{ observer in
            object = RxObserver<Int>.init(obs: observer)
            if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                self.observers.append(object)
                if self.batteryLevel.get() != -1 {
                    object.obs.onNext(Int(self.batteryLevel.get()))
                }
            } else {
                observer.onError(BleGattException.gattDisconnected)
            }
            return Disposables.create {
                self.observers.remove({ (item) -> Bool in
                    return item === object
                })
            }
        }
    }
}
