
import Foundation
import CoreBluetooth
import RxSwift

public class BleBasClient: BleGattClientBase {
    public static let BATTERY_SERVICE = CBUUID(string: "180F")
    private static let BATTERY_LEVEL_CHARACTERISTIC = CBUUID(string: "2A19")
    private static let UNDEFINED_BATTERY_PERCENTAGE = -1
    
    var cachedBatteryPercentage = AtomicInteger(initialValue: UNDEFINED_BATTERY_PERCENTAGE)
    var observers = AtomicList<RxObserver<Int>>()
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BleBasClient.BATTERY_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: BleBasClient.BATTERY_LEVEL_CHARACTERISTIC)
        addCharacteristicRead(BleBasClient.BATTERY_LEVEL_CHARACTERISTIC)
    }
    
    override public func disconnected() {
        super.disconnected()
        cachedBatteryPercentage.set(BleBasClient.UNDEFINED_BATTERY_PERCENTAGE)
        RxUtils.postErrorAndClearList(observers, error: BleGattException.gattDisconnected)
    }
    
    override public func processServiceData(_ chr: CBUUID , data: Data , err: Int ){
        var trace = "BleBasClient process data. chr: \(chr.uuidString)"
        if( err == 0 ){
            var level: UInt8=0
            (data as NSData).getBytes(&level, length: MemoryLayout<UInt8>.size)
            trace.append(" battery percentage: \(level)")
            BleLogger.trace(trace)
            cachedBatteryPercentage.set(Int(level))
            RxUtils.emitNext(observers) { (observer) in observer.obs.onNext(Int(level))}
        } else {
            trace.append(" err: \(err)")
            BleLogger.error(trace)
        }
    }
    
    // apis
    public func readLevel() throws {
        try self.gattServiceTransmitter?.readValue(self, serviceUuid: BleBasClient.BATTERY_SERVICE, characteristicUuid: BleBasClient.BATTERY_LEVEL_CHARACTERISTIC)
    }
    
    /// Get observable for monitoring battery status updates on connected device. If battery level is already cached then the cached value is emitted immidiately.
    ///
    /// - Parameter checkConnection: check initial connection
    /// - Returns: Observable stream of battery status.
    /// onNext, on every battery status update received from connected device. The value is the device battery level as a percentage from 0% to 100%
    /// onError, if client is not initially connected or ble disconnect's
    public func monitorBatteryStatus(_ checkConnection: Bool) -> Observable<Int> {
        return RxUtils.monitor(observers, transport: gattServiceTransmitter, checkConnection: checkConnection)
            .startWith(self.cachedBatteryPercentage.get())
            .filter { value in self.isValidBatteryPercentage(value)};
    }
    
    private func isValidBatteryPercentage(_ batteryPercentage:Int) -> Bool {
        let batteryPercentageRange = 0...100
        return batteryPercentageRange.contains(batteryPercentage)
    }
}
