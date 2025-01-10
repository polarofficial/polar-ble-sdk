
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
    
    public static let SYSTEM_ID_HEX            = String("SYSTEM_ID_HEX")
    
    private var observers = AtomicList<RxObserver<(CBUUID, String)>>()
    private var observersStringKey = AtomicList<RxObserver<(String, String)>>()

    // disInformation and disInformationStringKey are both synchronized using disInformation.accessItem()
    private let disInformation = AtomicType<[CBUUID : String]>(initialValue: [CBUUID : String]())
    private var disInformationStringKey = [String : String]()
    
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
        disInformation.accessItem { disInformation in
            disInformation.removeAll()
            disInformationStringKey.removeAll()
        }
        RxUtils.postErrorAndClearList(observers, error: BleGattException.gattDisconnected)
        RxUtils.postErrorAndClearList(observersStringKey, error: BleGattException.gattDisconnected)
    }
    
    override public func processServiceData(_ chr: CBUUID , data: Data , err: Int ){
        if( err == 0 ){
            var asciiRepresentation = ""
            var hexRepresentation = ""
            if let stringValue = NSString(data: data, encoding: String.Encoding.ascii.rawValue) as String? {
                asciiRepresentation = stringValue
            }
            disInformation.accessItem { disInformation in
                disInformation[chr] = asciiRepresentation
                if (chr == BleDisClient.SYSTEM_ID) {
                    hexRepresentation = data.map { String(format: "%02X", $0) }.joined()
                    disInformationStringKey[chr.uuidString] = hexRepresentation
                } else {
                    disInformationStringKey[chr.uuidString] = asciiRepresentation
                }
            }
            RxUtils.emitNext(observers) { (observer) in
                observer.obs.onNext((chr, asciiRepresentation))
                disInformation.accessItem { disInformation in
                    let disList = disInformation
                    if self.hasAllAvailableReadableCharacteristics(disList as [CBUUID : AnyObject]) {
                        observer.obs.onCompleted()
                    }
                }
            }
            RxUtils.emitNext(observersStringKey) { observer in
                if (chr == BleDisClient.SYSTEM_ID) {
                    // Reorder hex bytes to be in correct format
                    hexRepresentation = data.map { String(format: "%02X", $0) }.joined()
                    let reorderedHex = stride(from: 0, to: hexRepresentation.count, by: 2).map {
                       String(hexRepresentation[hexRepresentation.index(hexRepresentation.startIndex, offsetBy: $0)..<hexRepresentation.index(hexRepresentation.startIndex, offsetBy: $0+2)])
                    }.reversed().joined()
                    observer.obs.onNext((BleDisClient.SYSTEM_ID_HEX, reorderedHex))
                } else {
                    observer.obs.onNext((chr.uuidString, asciiRepresentation))
                }
                disInformation.accessItem { disInformation in
                    if self.hasAllAvailableReadableCharacteristics(disInformation as [CBUUID : AnyObject]) {
                        observer.obs.onCompleted()
                    }
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
                self.disInformation.accessItem { disInformation in
                    let disList = disInformation
                    if disList.count != 0 {
                        for item in disList {
                            object.obs.onNext((item.key,item.value))
                        }
                        if self.hasAllAvailableReadableCharacteristics(disList as [CBUUID : AnyObject]) {
                            object.obs.onCompleted()
                        }
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

    public func readDisInfoWithKeysAsStrings(_ checkConnection: Bool) -> Observable<(String, String)> {
            var object: RxObserver<(String, String)>!
            return Observable.create { observer in
                object = RxObserver<(String, String)>.init(obs: observer)
                if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                    self.observersStringKey.append(object)
                    self.disInformation.accessItem { disInformation in
                        let disList = disInformation
                        if disList.count != 0 {
                            for item in disList {
                                object.obs.onNext((item.key.uuidString, item.value))
                            }
                            if self.hasAllAvailableReadableCharacteristics(disList as [CBUUID : AnyObject]) {
                                object.obs.onCompleted()
                            }
                        }
                    }
                } else {
                    observer.onError(BleGattException.gattDisconnected)
                }
                return Disposables.create {
                    self.observersStringKey.remove { (item) -> Bool in
                        return item === object
                    }
                }
            }
        }
}
