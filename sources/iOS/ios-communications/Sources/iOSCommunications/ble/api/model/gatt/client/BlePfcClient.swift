import Foundation
import CoreBluetooth
import Combine

public struct Pfc {
    public struct PfcFeature {
        public let broadcastSupported: Bool
        public let khzSupported: Bool
        public let multiConnectionSupported: Bool
        public let antPlusSupported: Bool
        public let securityModeSupported: Bool

        init(_ data: Data) {
            broadcastSupported       = (data[0] & 0x01) == 0x01
            khzSupported             = (data[0] & 0x02) == 0x02
            multiConnectionSupported = (data[0] & 0x80) == 0x80
            antPlusSupported         = (data[1] & 0x01) == 0x01
            securityModeSupported    = (data[1] & 0x02) == 0x02
        }
    }

    public struct PfcResponse {
        public enum PfcResponseCodes: Int {
            case unknownErrorCode = 0, success = 1, errorNotSupported = 2,
                 errorInvalidParameter = 3, errorOperationFailed = 4, errorNotAllowed = 5
        }
        public let responseCode: UInt8
        public let opCode: UInt8
        public let status: PfcResponseCodes
        public let payload: Data

        public init() {
            responseCode = 0; opCode = 0; status = .success; payload = Data()
        }
        public init(data: Data) {
            responseCode = data[0]
            opCode = data[1]
            status = PfcResponseCodes(rawValue: Int(data[2])) ?? .unknownErrorCode
            payload = data.count > 3 ? data.subdata(in: 3..<data.count) : Data()
        }
        public func description() -> String {
            return "Response code: \(String(format: "%02x", responseCode)) op code: \(String(format: "%02x", opCode)) status: \(String(format: "%02x", status.rawValue))"
        }
    }
}

/// pfc = polar features configuration service.
public class BlePfcClient: BleGattClientBase, @unchecked Sendable {

    public static let PFC_SERVICE = CBUUID(string: "6217FF4B-FB31-1140-AD5A-A45545D7ECF3")
    let PFC_FEATURE               = CBUUID(string: "6217FF4C-C8EC-B1FB-1380-3AD986708E2D")
    public static let PFC_CP      = CBUUID(string: "6217FF4D-91BB-91D0-7E2A-7CD3BDA8A1F3")

    var pfcEnabled: AtomicInteger!
    let pfcInputQueue    = AtomicList<[Data: Int]>()
    var pfcFeatureData   = AtomicType<Data>(initialValue: Data())

    private let featureWaiters = StreamContinuationList<Pfc.PfcFeature>()

    public enum PfcMessage: Int, Sendable {
        case pfcConfigureBroadcast = 1, pfcRequestBroadcastSetting = 2,
             pfcConfigure5khz = 3, pfcRequest5khzSetting = 4,
             pfcConfigureAdaptiveTxPowerLevel = 5, pfcRequestAdaptiveTxPowerLevelSetting = 6,
             pfcConfigureBleMode = 7, pfcConfigureMultiConnection = 8, pfcRequestMultiConnectionSetting = 9,
             pfcConfigureAntPlusSetting = 10, pfcRequestAntPlusSetting = 11,
             pfcRequestSecurityMode = 12, pfcConfigureSensorInitiatedSecurityMode = 14,
             pfcRequestSensorInitiatedSecurityMode = 15

        public var description: String { return "PFC_\(rawValue)" }
    }

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BlePfcClient.PFC_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: BlePfcClient.PFC_CP)
        addCharacteristicRead(PFC_FEATURE)
        pfcEnabled = getNotificationCharacteristicState(BlePfcClient.PFC_CP)
    }

    override public func disconnected() {
        super.disconnected()
        pfcInputQueue.removeAll()
        pfcFeatureData.set(Data())
        featureWaiters.finish(throwing: BleGattException.gattDisconnected)
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if chr.isEqual(BlePfcClient.PFC_CP) {
            pfcInputQueue.push([data: err])
        } else if chr.isEqual(PFC_FEATURE) {
            if err == 0 {
                pfcFeatureData.set(data)
                let feature = Pfc.PfcFeature(data)
                featureWaiters.yield(feature)
                featureWaiters.finish()
            } else {
                featureWaiters.finish(throwing: BleGattException.gattAttributeError(errorCode: err))
            }
        }
    }

    private func sendPfcCommandAndProcessResponse(packet: Data) throws -> Pfc.PfcResponse {
        guard let transport = gattServiceTransmitter else { throw BleGattException.gattTransportNotAvailable }
        try transport.transmitMessage(self, serviceUuid: BlePfcClient.PFC_SERVICE, characteristicUuid: BlePfcClient.PFC_CP, packet: packet, withResponse: true)
        let responsePacket = try pfcInputQueue.poll(30)
        guard responsePacket.first?.1 == 0 else { throw BleGattException.gattCharacteristicError }
        return Pfc.PfcResponse(data: responsePacket.first?.0 ?? Data())
    }

    public func sendControlPointCommand(_ command: PfcMessage, value: UInt8) async throws -> Pfc.PfcResponse {
        return try await sendControlPointCommand(command, value: [value])
    }

    /// Send a single control point command.
    public func sendControlPointCommand(_ command: PfcMessage, value: [UInt8]) async throws -> Pfc.PfcResponse {
        return try await withCheckedThrowingContinuation { continuation in
            baseSerialDispatchQueue.async {
                guard self.pfcEnabled.get() == self.ATT_NOTIFY_OR_INDICATE_ON else {
                    continuation.resume(throwing: BleGattException.gattCharacteristicNotifyNotEnabled)
                    return
                }
                do {
                    let response: Pfc.PfcResponse
                    switch command {
                    case .pfcRequestBroadcastSetting, .pfcRequest5khzSetting, .pfcRequestMultiConnectionSetting,
                         .pfcRequestAdaptiveTxPowerLevelSetting, .pfcRequestAntPlusSetting,
                         .pfcRequestSecurityMode, .pfcRequestSensorInitiatedSecurityMode:
                        response = try self.sendPfcCommandAndProcessResponse(packet: Data([UInt8(command.rawValue)]))
                    default:
                        var packet = Data([UInt8(command.rawValue)])
                        packet.append(contentsOf: value)
                        response = try self.sendPfcCommandAndProcessResponse(packet: packet)
                    }
                    continuation.resume(returning: response)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    /// Read PFC feature.
    public func readFeature(_ checkConnection: Bool) async throws -> Pfc.PfcFeature {
        let cached = pfcFeatureData.get()
        if cached.count != 0 {
            return Pfc.PfcFeature(cached)
        }
        let stream = featureWaiters.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
        for try await feature in stream {
            return feature
        }
        throw BleGattException.gattDisconnected
    }

    public override func clientReady(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        waitNotificationEnabled(BlePfcClient.PFC_CP, checkConnection: checkConnection)
    }
}
