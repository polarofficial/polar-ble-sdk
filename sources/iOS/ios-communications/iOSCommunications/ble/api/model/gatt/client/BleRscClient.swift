
import Foundation
import CoreBluetooth
import RxSwift

public class BleRscClient: BleGattClientBase {
    
    public static let RSC_SERVICE = CBUUID(string: "1814")
    let RSC_FEATURE             = CBUUID(string: "2a54")
    let RSC_MEASUREMENT         = CBUUID(string: "2a53")
    
    var observers = AtomicList<RxObserver<BleRscNotification>>()
    
    public typealias BleRscNotification = (speed: Double, candence: UInt8, strideLength: Int, distance: Double, running: Bool, flags: UInt8)
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BleRscClient.RSC_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: RSC_MEASUREMENT)
        addCharacteristicRead(RSC_FEATURE)
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        RxUtils.postErrorAndClearList(observers, error: BleGattException.gattDisconnected)
    }
    
    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int ){
        if( err == 0 ) {
            if (chr.isEqual(RSC_MEASUREMENT)) {
                var index = 0
                let flags = data[0]
                index += 1
                let strideLenPresent     = (flags & 0x01) == 0x01
                let totalDistancePresent = (flags & 0x02) == 0x02
                let running              = (flags & 0x04) == 0x04
                let speedMask = UInt16(data[index]) | UInt16(UInt16(data[index + 1]) << 8)
                let speed = (Double(speedMask)/256.0)*3.6 // km/h
                index += 2
                let cadence = data[index]
                index += 1
                
                var strideLength = 0
                var totalDistance = 0.0
                
                if(strideLenPresent){
                    strideLength = (Int(UInt16(data[index]) | UInt16(UInt16(data[index + 1]) << 8)))
                    index += 2
                }
                if(totalDistancePresent){
                    var distance=0
                    memcpy(&distance, (data.subdata(in: index..<(index+4)) as NSData).bytes, 4)
                    totalDistance = Double(distance)*0.1
                }
                RxUtils.emitNext(observers) { (observer) in
                    observer.obs.onNext(BleRscNotification(speed: speed, candence: cadence, strideLength: strideLength, distance: totalDistance, running: running, flags: flags))
                }
            }else if(chr.isEqual(RSC_FEATURE)){
                // do nothing
            }
        }
    }
    
    // api
    public func observeRscNotifications(_ checkConnection: Bool) -> Observable<BleRscNotification> {
        return RxUtils.monitor(observers, transport: gattServiceTransmitter, checkConnection: checkConnection)
    }
    
    public override func clientReady(_ checkConnection: Bool) -> Completable {
        return waitNotificationEnabled(RSC_MEASUREMENT, checkConnection: checkConnection)
    }
}
