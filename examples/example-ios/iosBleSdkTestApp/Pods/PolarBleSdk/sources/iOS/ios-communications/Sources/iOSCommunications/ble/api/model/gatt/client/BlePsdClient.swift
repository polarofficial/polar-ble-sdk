
import Foundation
import CoreBluetooth
import RxSwift

// all psd data types + observer declarations
public struct Psd {
    public class PPData {
        public let rc: Int
        public let hr: Int
        public var ppInMs: UInt16 = 0
        public var ppErrorEstimate: UInt16 = 0
        public let blockerBit: Int
        public let skinContactStatus: Int
        public let skinContactSupported: Int
        
        init(_ data: Data){
            rc = Int(data[0])
            hr = Int(data[1])
            let sub1 = data.subdata(in: 2..<data.count)
            let sub2 = data.subdata(in: 4..<data.count)
            memcpy(&ppInMs,(sub1 as NSData).bytes, 2)
            memcpy(&ppErrorEstimate, (sub2 as NSData).bytes, 2)
            blockerBit = Int(data[4]) & 0x01
            skinContactStatus = (Int(data[4]) & 0x02) >> 1
            skinContactSupported = (Int(data[4]) & 0x04) >> 2
        }
    }
    
    
    public class PsdResponse {
        
        public let responseCode: UInt8
        public let opCode: UInt8
        public let status: UInt8
        
        public init(_ data: Data) {
            if( data.count > 2 ) {
                responseCode = data[0]
                opCode = data[1]
                status = data[2]
            } else {
                responseCode = 0
                status = 0
                opCode = 0
            }
        }
    }
    
    public class PsdFeature {
        public let ecgSupported: Bool
        public let accSupported: Bool
        public let ohrSupported: Bool
        public let ppSupported: Bool
        
        public init(_ data: Data) {
            ecgSupported = (data[0] & 0x01) == 1
            ohrSupported = ((data[0] & 0x02) >> 1) == 1
            accSupported = ((data[0] & 0x04) >> 2) == 1
            ppSupported = ((data[0] & 0x08) != 0)
        }
    }
}

public class BlePsdClient: BleGattClientBase {
    
    public enum PsdMessage: Int{
        case psdUnknown = 0
        case psdStartOhrPpStream = 7
        case psdStopOhrPpStream = 8
    }
    
    public static let PSD_SERVICE = CBUUID(string: "FB005C20-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_FEATURE = CBUUID(string: "FB005C21-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_PP      = CBUUID(string: "FB005C26-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_CP      = CBUUID(string: "FB005C22-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_OHR     = CBUUID(string: "FB005C24-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_ECG     = CBUUID(string: "FB005C23-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_ACC     = CBUUID(string: "FB005C25-02E7-F387-1CAD-8ACD2D8DF0C8")
    
    var observers = AtomicList<RxObserver<Psd.PPData>>()
    var observersFeature = AtomicList<RxObserverSingle<Psd.PsdFeature>>()
    // operation locks
    let psdCpInputQueue = AtomicList<[Data: Int]>()
    let features = AtomicType<Data>(initialValue: Data())
    var psdEnabled: AtomicInteger!
    fileprivate let PSD_PP_DATA_SIZE = 7
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BlePsdClient.PSD_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: BlePsdClient.PSD_CP)
        automaticEnableNotificationsOnConnect(chr: BlePsdClient.PSD_PP)
        addCharacteristicRead(BlePsdClient.PSD_FEATURE)
        psdEnabled = getNotificationCharacteristicState(BlePsdClient.PSD_CP)
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        RxUtils.postErrorAndClearList(observers, error: BleGattException.gattDisconnected)
        RxUtils.postErrorOnSingleAndClearList(observersFeature, error: BleGattException.gattDisconnected)
        psdCpInputQueue.removeAll()
        features.set(Data())
    }
    
    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int ){
        if( chr.isEqual(BlePsdClient.PSD_PP) ) {
            stride(from: 0, to: data.count, by: PSD_PP_DATA_SIZE).map { (start) -> Data in
                let end = (start.advanced(by: PSD_PP_DATA_SIZE) > data.count) ? data.count-start : PSD_PP_DATA_SIZE
                return data.subdata(in: start..<start.advanced(by: end))
            }.forEach { (content) in
                RxUtils.emitNext(observers) { (observer) in
                    observer.obs.onNext(Psd.PPData(content))
                }
            }
        } else if(chr.isEqual(BlePsdClient.PSD_CP)){
            psdCpInputQueue.push( [data: err] )
        } else if(chr.isEqual(BlePsdClient.PSD_FEATURE)) {
            features.set(data)
            RxUtils.emitNext(observersFeature) { (observer) in
                observer.obs(.success(Psd.PsdFeature.init(data)))
            }
        }
    }
    
    fileprivate func sendPsdCommandAndProcessResponse(_ observer: (RxSwift.SingleEvent<Psd.PsdResponse>) -> (),
                                                      packet: Data) {
        do{
            if let transport = self.gattServiceTransmitter {
                try transport.transmitMessage(self, serviceUuid: BlePsdClient.PSD_SERVICE, characteristicUuid: BlePsdClient.PSD_CP, packet: packet, withResponse: true)
                let packet = try psdCpInputQueue.poll(60)
                if packet.first?.1 == 0 {
                    let response = Psd.PsdResponse.init((packet.first?.0)!)
                    observer(.success(response))
                } else {
                    observer(.failure(BleGattException.gattCharacteristicError))
                }
            } else {
                observer(.failure(BleGattException.gattTransportNotAvailable))
            }
        } catch let error {
            observer(.failure(error))
        }
    }
    
    /// send psd controlpoint command
    ///
    /// - Parameters:
    ///   - command: psd command to send
    ///   - value: optional parameters value, check spec if psd command requires parameters or not
    /// - Returns: Observable stream, complete produced after next
    //                              , next psd
    public func sendControlpointCommand(_ command: PsdMessage, value: [UInt8])  -> Single<Psd.PsdResponse> {       
        return Single.create{ observer in
            if self.psdEnabled.get() == self.ATT_NOTIFY_OR_INDICATE_ON {
                switch command {
                case .psdStartOhrPpStream: fallthrough
                case .psdStopOhrPpStream:
                    let packet = [UInt8](repeating:UInt8(command.rawValue), count:1)
                    self.sendPsdCommandAndProcessResponse(observer, packet: NSData.init(bytes: packet, length: 1) as Data)
                default:
                    observer(.failure(BleGattException.gattOperationNotSupported))
                }
            } else {
                observer(.failure(BleGattException.gattCharacteristicNotifyNotEnabled))
            }
            return Disposables.create {
                // do nothing
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    /// start psd PP stream monitor
    ///
    /// - Parameter checkConnection: initial check from transport wheather we have connection or not
    /// - Returns: Observable stream of ecgData PPData
    public func observePsdPPNotifications(_ checkConnection: Bool) -> Observable<Psd.PPData> {
        return RxUtils.monitor(observers, transport: self.gattServiceTransmitter, checkConnection: checkConnection)
    }
    
    public func readFeature(_ checkConnection: Bool) -> Single<Psd.PsdFeature> {
        var object: RxObserverSingle<Psd.PsdFeature>!
        return Single.create{ observer in
            object = RxObserverSingle<Psd.PsdFeature>.init(obs: observer)
            if self.features.get().count != 0 {
                // available
                observer(.success(Psd.PsdFeature.init(self.features.get())))
            } else {
                if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                    self.observersFeature.append(object)
                } else {
                    observer(.failure(BleGattException.gattDisconnected))
                }
            }
            return Disposables.create {
                self.observersFeature.remove({ (item) -> Bool in
                    return item === object
                })
            }
        }.subscribe(on: baseConcurrentDispatchQueue)
    }
    
    public override func clientReady(_ checkConnection: Bool) -> Completable {
        // TODO add later check for pp and ecg
        return waitNotificationEnabled(BlePsdClient.PSD_CP, checkConnection: checkConnection)
    }
}
