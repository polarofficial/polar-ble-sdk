
import Foundation
import CoreBluetooth
import RxSwift

public class BleH7SettingsClient: BleGattClientBase{
    
    public static let H7_SETTINGS_SERVICE = CBUUID(string: "6217FF49-AC7B-547E-EECF-016A06970BA9")
    let H7_SETTINGS_CHARACTERISTIC        = CBUUID(string: "6217FF4A-B07D-5DEB-261E-2586752D942E")

    let inputQueue = AtomicList<[Data: Int]>()
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BleH7SettingsClient.H7_SETTINGS_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(H7_SETTINGS_CHARACTERISTIC)
    }
    
    public enum H7SettingsMessage: Int{
        case h7ConfigureBroadcast = 1
        case h7Configure5khz = 2
        case h7RequestCurrentSettings = 3
        
        public var description : String {
            switch self {
                case .h7ConfigureBroadcast:      return "H7_CONFIGURE_BROADCAST"
                case .h7Configure5khz:           return "H7_CONFIGURE_5KHZ"
                case .h7RequestCurrentSettings: return "H7_REQUEST_CURRENT_SETTINGS"
            }
        }
    }
    
    public class H7SettingsResponse{
        public let broadcastValue: UInt8
        public let khzValue: UInt8
        
        init(data: Data) {
            self.khzValue = (data[0] & 0x02) >> 1
            self.broadcastValue = (data[0] & 0x01)
        }
        
        init(broadcastValue: UInt8,khzValue: UInt8) {
            self.broadcastValue = broadcastValue
            self.khzValue = khzValue
        }
        
        public func description() -> String {
            return "BC value: \(broadcastValue) khz value: \(khzValue)"
        }
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        inputQueue.removeAll()
    }
    
    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int ){
        if(chr.isEqual(H7_SETTINGS_CHARACTERISTIC)){
            inputQueue.push([data : err])
        }
    }
    
    private func readSettingsValue() throws -> [Data : Int]{
        if let transport = gattServiceTransmitter {
            try transport.readValue(self, serviceUuid: BleH7SettingsClient.H7_SETTINGS_SERVICE, characteristicUuid: H7_SETTINGS_CHARACTERISTIC)
            return try inputQueue.poll(30)
        }
        throw BleGattException.gattTransportNotAvailable
    }
    
    // apis
    public func sendSettingsCommand(_ command: H7SettingsMessage, parameter: UInt8) -> Observable<H7SettingsResponse> {
        let has = self.isServiceDiscovered()
        return Observable.create{ observer in
            if self.gattServiceTransmitter?.isConnected() ?? false {
                if has {
                    self.inputQueue.removeAll()
                    var khzValue: UInt8 = 0
                    var broadcastValue: UInt8 = 0
                    do {
                        var packet = try self.readSettingsValue()
                        if packet.first?.1 == 0 {
                            let bytes = packet.first!.0
                            khzValue = (bytes[0] & 0x02) >> 1
                            broadcastValue = (bytes[0] & 0x01)
                            switch command {
                                case .h7ConfigureBroadcast: fallthrough
                                case .h7Configure5khz:
                                    var values = [UInt8](repeating: 0, count: 1)
                                    if( command == .h7ConfigureBroadcast ) {
                                        values[0] = ((khzValue << 1) | parameter)
                                    } else {
                                        values[0] = ((parameter << 1) | broadcastValue)
                                    }
                                    do{
                                        try self.gattServiceTransmitter?.transmitMessage(self, serviceUuid: BleH7SettingsClient.H7_SETTINGS_SERVICE, characteristicUuid: self.H7_SETTINGS_CHARACTERISTIC, packet: Data(values), withResponse: true) 
                                        packet = try self.readSettingsValue()
                                        observer.onNext(H7SettingsResponse.init(data: (packet.first?.0)!))
                                        observer.onCompleted()
                                    } catch let error {
                                        observer.onError(error)
                                    }
                                    break
                                case .h7RequestCurrentSettings:
                                    observer.onNext(H7SettingsResponse.init(broadcastValue: broadcastValue, khzValue: khzValue))
                                    observer.onCompleted()
                                    break
                            }
                        } else {
                            observer.onError(BleGattException.gattAttributeError(errorCode: packet.first?.1 ?? -1))
                        }
                    } catch let error {
                        observer.onError(error)
                    }
                } else {
                    observer.onError(BleGattException.gattServiceNotFound)
                }
            } else {
                observer.onError(BleGattException.gattDisconnected)
            }
            return Disposables.create {
                
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
}
