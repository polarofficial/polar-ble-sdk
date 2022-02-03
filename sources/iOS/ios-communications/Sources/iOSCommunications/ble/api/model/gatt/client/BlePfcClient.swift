
import Foundation
import CoreBluetooth
import RxSwift

public struct Pfc {
    public struct PfcFeature {
        public let broadcastSupported: Bool
        public let khzSupported: Bool
        public let multiConnectionSupported: Bool
        public let antPlusSupported: Bool
        
        init(_ data: Data) {
            broadcastSupported        = (data[0] & 0x01) == 0x01
            khzSupported              = (data[0] & 0x02) == 0x02
            multiConnectionSupported  = (data[0] & 0x80) == 0x80
            antPlusSupported          = (data[1] & 0x01) == 0x01
        }
    }
    
    public struct PfcResponse {
        public enum PfcResponseCodes: Int {
            case unknownErrorCode = 0
            case success = 1
            case errorNotSupported = 2
            case errorInvalidParameter = 3
            case errorOperationFailed = 4
            case errorNotAllowed = 5
        }
        
        public let responseCode: UInt8
        public let opCode: UInt8
        public let status: PfcResponseCodes
        public let payload: Data
        
        public init() {
            self.responseCode = 0
            self.opCode = 0
            self.status = Pfc.PfcResponse.PfcResponseCodes.success
            self.payload = Data()
        }
        
        public init(data: Data){
            responseCode = data[0]
            opCode = data[1]
            status = Pfc.PfcResponse.PfcResponseCodes.init(rawValue: Int(data[2])) ??
                Pfc.PfcResponse.PfcResponseCodes.unknownErrorCode
            if data.count > 3 {
                payload = data.subdata(in: 3..<data.count)
            } else {
                payload = Data()
            }
        }
        
        public func description() -> String {
            return "Response code: " + String.init(format: "%02x", self.responseCode) +
                " op code: " + String.init(format: "%02x", self.opCode) +
                " status: "  + String.init(format:"%02x", self.status.rawValue)
        }
    }
}

/// pfc = polar features configuration service.
/// Usage:
///     - first readFeature to get list of features currently supported by device
public class BlePfcClient: BleGattClientBase{
    
    public static let PFC_SERVICE = CBUUID(string: "6217FF4B-FB31-1140-AD5A-A45545D7ECF3")
    let PFC_FEATURE               = CBUUID(string: "6217FF4C-C8EC-B1FB-1380-3AD986708E2D")
    public static let PFC_CP      = CBUUID(string: "6217FF4D-91BB-91D0-7E2A-7CD3BDA8A1F3")
    
    var pfcEnabled: AtomicInteger!
    let pfcInputQueue       = AtomicList<[Data: Int]>()
    var pfcFeatureData      = AtomicType<Data>(initialValue: Data())
    let pfcFeatureObservers = AtomicList<RxObserverSingle<Pfc.PfcFeature>>()
    
    public enum PfcMessage: Int{
        /// configure hr broadcast setting, single byte parameter: 0 = off, 1 = on
        case pfcConfigureBroadcast = 1
        /// request current broadcast setting, payload as single byte with 0/1
        case pfcRequestBroadcastSetting = 2
        /// configure 5khz setting, single byte parameter: 0 = off, 1 = on
        case pfcConfigure5khz = 3
        /// request current 5khz setting, payload as single byte with 0/1
        case pfcRequest5khzSetting = 4
        /// configure adaptive tx power level (only in H10)
        case pfcConfigureAdaptiveTxPowerLevel = 5
        ///
        case pfcRequestAdaptiveTxPowerLevelSetting = 6
        ///
        case pfcConfigureBleMode = 7
        /// configure/enable multi connection = dual connection support in the device
        case pfcConfigureMultiConnection = 8
        /// request current multi connection setting
        case pfcRequestMultiConnectionSetting = 9
        ///
        case pfcConfigureAntPlusSetting = 10
        ///
        case pfcRequestAntPlusSetting = 11
        
        public var description : String {
            switch self {
            case .pfcConfigureBroadcast:                     return "PFC_CONFIGURE_BROADCAST"
            case .pfcRequestBroadcastSetting:               return "PFC_REQUEST_BROADCAST_SETTING"
            case .pfcConfigure5khz:                          return "PFC_CONFIGURE_5KHZ"
            case .pfcRequest5khzSetting:                    return "PFC_REQUEST_5KHZ_SETTING"
            case .pfcConfigureAdaptiveTxPowerLevel:       return "PFC_CONFIGURE_ADAPTIVE_TX_POWER_LEVEL"
            case .pfcRequestAdaptiveTxPowerLevelSetting: return "PFC_REQUEST_ADAPTIVE_TX_POWER_LEVEL_SETTING"
            case .pfcConfigureBleMode: return "PFC_CONFIGURE_BLE_MODE"
            case .pfcConfigureMultiConnection:              return "PFC_CONFIGURE_MULTI_CONNECTION"
            case .pfcRequestMultiConnectionSetting:        return "PFC_REQUEST_MULTI_CONNECTION_SETTING"
            case .pfcConfigureAntPlusSetting:              return "PFC_CONFIGURE_ANT_PLUS_SETTING"
            case .pfcRequestAntPlusSetting:                return "PFC_REQUEST_ANT_PLUS_SETTING"
            }
        }
    }
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BlePfcClient.PFC_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: BlePfcClient.PFC_CP)
        addCharacteristicRead(PFC_FEATURE)
        pfcEnabled = getNotificationCharacteristicState(BlePfcClient.PFC_CP)
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        pfcInputQueue.removeAll()
        pfcFeatureData.set(Data())
        RxUtils.postErrorOnSingleAndClearList(pfcFeatureObservers, error: BleGattException.gattDisconnected)
    }
    
    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int ){
        if(chr.isEqual(BlePfcClient.PFC_CP)){
            pfcInputQueue.push( [data: err] )
        }else if(chr.isEqual(PFC_FEATURE)){
            if(err == 0){
                pfcFeatureData.set(data)
                RxUtils.emitNext(pfcFeatureObservers) { (observer) in
                    observer.obs(.success(Pfc.PfcFeature(data)))
                }
            } else {
                RxUtils.postErrorOnSingleAndClearList(pfcFeatureObservers, error: BleGattException.gattAttributeError(errorCode: err))
            }
        }
    }
    
    fileprivate func sendPfcCommandAndProcessResponse(_ observer: (RxSwift.SingleEvent<Pfc.PfcResponse>) -> (), packet: Data) {
        do{
            if let transport = self.gattServiceTransmitter {
                try transport.transmitMessage(self, serviceUuid: BlePfcClient.PFC_SERVICE, characteristicUuid: BlePfcClient.PFC_CP, packet: packet, withResponse: true)
                do{
                    let packet = try pfcInputQueue.poll(30)
                    if packet.first?.1 == 0 {
                        observer(.success(Pfc.PfcResponse(data: packet.first?.0 ?? Data())))
                    } else {
                        observer(.failure(BleGattException.gattCharacteristicError))
                    }
                } catch let error {
                    observer(.failure(error))
                }
            } else {
                observer(.failure(BleGattException.gattTransportNotAvailable))
            }
        } catch let error {
            observer(.failure(error))
        }
    }
    
    public func sendControlPointCommand(_ command: PfcMessage, value: UInt8) -> Single<Pfc.PfcResponse> {
        return sendControlPointCommand(command, value: [value])
    }
    
    /// send a single controlpoint command
    ///
    /// - Parameters:
    ///   - command: @see PfcMessage for command id
    ///   - value: optional parameters if any, check spec for details
    /// - Returns: Single stream: success @see pfc.PfcResponse, error
    public func sendControlPointCommand(_ command: PfcMessage, value: [UInt8]) -> Single<Pfc.PfcResponse> {
        return Single.create{ observer in
            if self.pfcEnabled.get() == self.ATT_NOTIFY_OR_INDICATE_ON {
                switch command {
                case .pfcRequestBroadcastSetting: fallthrough
                case .pfcRequest5khzSetting: fallthrough
                case .pfcRequestMultiConnectionSetting: fallthrough
                case .pfcRequestAdaptiveTxPowerLevelSetting: fallthrough
                case .pfcRequestAntPlusSetting:
                    self.sendPfcCommandAndProcessResponse(observer, packet: Data([UInt8(command.rawValue)]))
                case .pfcConfigure5khz: fallthrough
                case .pfcConfigureBroadcast: fallthrough
                case .pfcConfigureMultiConnection: fallthrough
                case .pfcConfigureBleMode: fallthrough
                case .pfcConfigureAdaptiveTxPowerLevel: fallthrough
                case .pfcConfigureAntPlusSetting:
                    var packet = Data()
                    packet.append(UInt8(command.rawValue))
                    packet.append(contentsOf: value)
                    self.sendPfcCommandAndProcessResponse(observer, packet: packet)
                }
            } else {
                observer(.failure(BleGattException.gattCharacteristicNotifyNotEnabled))
            }
            return Disposables.create {
            }
        }.subscribe(on: baseSerialDispatchQueue)
    }
    
    ///
    ///
    /// - Parameter checkConnection:
    /// - Returns: 
    public func readFeature(_ checkConnection: Bool) -> Single<Pfc.PfcFeature> {
        var object: RxObserverSingle<Pfc.PfcFeature>!
        return Single.create{ observer in
            object = RxObserverSingle<Pfc.PfcFeature>.init(obs: observer)
            if self.pfcFeatureData.get().count != 0 {
                observer(.success(Pfc.PfcFeature(self.pfcFeatureData.get())))
            } else {
                if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                    self.pfcFeatureObservers.append(object)
                } else {
                    observer(.failure(BleGattException.gattDisconnected))
                }
            }
            return Disposables.create {
                self.pfcFeatureObservers.remove({ (item) -> Bool in
                    return item === object
                })
            }
        }.subscribe(on: baseConcurrentDispatchQueue)
    }
    
    public override func clientReady(_ checkConnection: Bool) -> Completable {
        return waitNotificationEnabled(BlePfcClient.PFC_CP, checkConnection: checkConnection)
    }
}
