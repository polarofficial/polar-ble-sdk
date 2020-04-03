
import Foundation
import CoreBluetooth
import RxSwift

public class BleDisClient: BleGattClientBase {
    
    public static let DIS_SERVICE              = CBUUID(string: "180a")
    public static let MODEL_NUMBER_STRING      = CBUUID(string: "2a24")
    public static let MANUFACTURER_NAME_STRING = CBUUID(string: "2a29")
    public static let HARDWARE_REVISION_STRING = CBUUID(string: "2a27")
    public static let FIRMWARE_REVISION_STRING = CBUUID(string: "2a26")
    public static let SOFTWARE_REVISION_STRING = CBUUID(string: "2a28")
    public static let SERIAL_NUMBER_STRING     = CBUUID(string: "2a25")
    public static let SYSTEM_ID                = CBUUID(string: "2a23")
    public static let IEEE_11073_20601         = CBUUID(string: "2a2a")
    public static let PNP_ID                   = CBUUID(string: "2a50")
    
    var disInformation=[CBUUID : String]()
    var observers = AtomicList<RxObserver<(CBUUID ,String)>>()
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BleDisClient.DIS_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(BleDisClient.MODEL_NUMBER_STRING)
        addCharacteristicRead(BleDisClient.MANUFACTURER_NAME_STRING)
        addCharacteristicRead(BleDisClient.HARDWARE_REVISION_STRING)
        addCharacteristicRead(BleDisClient.FIRMWARE_REVISION_STRING)
        addCharacteristicRead(BleDisClient.SOFTWARE_REVISION_STRING)
        addCharacteristicRead(BleDisClient.SERIAL_NUMBER_STRING)
        addCharacteristicRead(BleDisClient.SYSTEM_ID)
        addCharacteristicRead(BleDisClient.IEEE_11073_20601)
        addCharacteristicRead(BleDisClient.PNP_ID)
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        disInformation.removeAll()
        RxUtils.postErrorAndClearList(observers,error: BleGattException.gattDisconnected)
    }
    
    override public func processServiceData(_ chr: CBUUID , data: Data , err: Int ){
        if( err == 0 ){
            let stringValue = NSString(data: data, encoding: String.Encoding.ascii.rawValue) as String?
            if stringValue != nil {
                disInformation[chr] = stringValue
            } else {
                disInformation[chr] = ""
            }
            RxUtils.emitNext(observers) { (observer) in
                let disList = self.disInformation
                observer.obs.onNext((chr,stringValue ?? ""))
                if self.hasAllAvailableReadableCharacteristics(disList as [CBUUID : AnyObject]) {
                    observer.obs.onCompleted()
                }
            }
        }
    }
    
    /// read dis info, all available dis infos are returned
    ///
    /// - Parameter checkConnection: check initial connection
    /// - Returns: Observable stream
    public func readDisInfo(_ checkConnection: Bool) -> Observable<(CBUUID,String)> {
        var object: RxObserver<(CBUUID ,String)>!
        return Observable.create{ observer in
            object = RxObserver<(CBUUID ,String)>.init(obs: observer)
            if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                self.observers.append(object)
                let disList = self.disInformation
                if disList.count != 0 {
                    for item in disList {
                        object.obs.onNext((item.key,item.value))
                    }
                    if self.hasAllAvailableReadableCharacteristics(disList as [CBUUID : AnyObject]) {
                        object.obs.onCompleted()
                    }
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
