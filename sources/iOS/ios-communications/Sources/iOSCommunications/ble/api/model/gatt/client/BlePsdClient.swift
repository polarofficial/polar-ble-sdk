import Foundation
import CoreBluetooth
import Combine

public struct Psd {
    public class PPData {
        public let rc: Int
        public let hr: Int
        public var ppInMs: UInt16 = 0
        public var ppErrorEstimate: UInt16 = 0
        public let blockerBit: Int
        public let skinContactStatus: Int
        public let skinContactSupported: Int

        init(_ data: Data) {
            rc = Int(data[0])
            hr = Int(data[1])
            let sub1 = data.subdata(in: 2..<data.count)
            let sub2 = data.subdata(in: 4..<data.count)
            memcpy(&ppInMs, (sub1 as NSData).bytes, 2)
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
            if data.count > 2 {
                responseCode = data[0]; opCode = data[1]; status = data[2]
            } else {
                responseCode = 0; status = 0; opCode = 0
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
            ppSupported  = (data[0] & 0x08) != 0
        }
    }
}

public class BlePsdClient: BleGattClientBase, @unchecked Sendable {

    public enum PsdMessage: Int, Sendable {
        case psdUnknown = 0, psdStartOhrPpStream = 7, psdStopOhrPpStream = 8
    }

    public static let PSD_SERVICE = CBUUID(string: "FB005C20-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_FEATURE = CBUUID(string: "FB005C21-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_PP      = CBUUID(string: "FB005C26-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_CP      = CBUUID(string: "FB005C22-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_OHR     = CBUUID(string: "FB005C24-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_ECG     = CBUUID(string: "FB005C23-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSD_ACC     = CBUUID(string: "FB005C25-02E7-F387-1CAD-8ACD2D8DF0C8")

    private let ppStreams = StreamContinuationList<Psd.PPData>()
    let psdCpInputQueue = AtomicList<[Data: Int]>()
    let features = AtomicType<Data>(initialValue: Data())
    var psdEnabled: AtomicInteger!
    fileprivate let PSD_PP_DATA_SIZE = 7

    private let featureWaiters = StreamContinuationList<Psd.PsdFeature>()

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BlePsdClient.PSD_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: BlePsdClient.PSD_CP)
        automaticEnableNotificationsOnConnect(chr: BlePsdClient.PSD_PP)
        addCharacteristicRead(BlePsdClient.PSD_FEATURE)
        psdEnabled = getNotificationCharacteristicState(BlePsdClient.PSD_CP)
    }

    override public func disconnected() {
        super.disconnected()
        ppStreams.finish(throwing: BleGattException.gattDisconnected)
        psdCpInputQueue.removeAll()
        features.set(Data())
        featureWaiters.finish(throwing: BleGattException.gattDisconnected)
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if chr.isEqual(BlePsdClient.PSD_PP) {
            stride(from: 0, to: data.count, by: PSD_PP_DATA_SIZE).forEach { start in
                let end = min(start + PSD_PP_DATA_SIZE, data.count)
                ppStreams.yield(Psd.PPData(data.subdata(in: start..<end)))
            }
        } else if chr.isEqual(BlePsdClient.PSD_CP) {
            psdCpInputQueue.push([data: err])
        } else if chr.isEqual(BlePsdClient.PSD_FEATURE) {
            features.set(data)
            let feature = Psd.PsdFeature(data)
            featureWaiters.yield(feature)
            featureWaiters.finish()
        }
    }

    private func sendPsdCommandAndProcessResponse(packet: Data) throws -> Psd.PsdResponse {
        guard let transport = gattServiceTransmitter else { throw BleGattException.gattTransportNotAvailable }
        try transport.transmitMessage(self, serviceUuid: BlePsdClient.PSD_SERVICE, characteristicUuid: BlePsdClient.PSD_CP, packet: packet, withResponse: true)
        let responsePacket = try psdCpInputQueue.poll(60)
        guard responsePacket.first?.1 == 0 else { throw BleGattException.gattCharacteristicError }
        return Psd.PsdResponse(responsePacket.first!.0)
    }

    /// Send PSD control point command.
    public func sendControlpointCommand(_ command: PsdMessage, value: [UInt8]) async throws -> Psd.PsdResponse {
        return try await withCheckedThrowingContinuation { continuation in
            baseSerialDispatchQueue.async {
                guard self.psdEnabled.get() == self.ATT_NOTIFY_OR_INDICATE_ON else {
                    continuation.resume(throwing: BleGattException.gattCharacteristicNotifyNotEnabled)
                    return
                }
                do {
                    switch command {
                    case .psdStartOhrPpStream, .psdStopOhrPpStream:
                        let packet = Data([UInt8(command.rawValue)])
                        let response = try self.sendPsdCommandAndProcessResponse(packet: packet)
                        continuation.resume(returning: response)
                    default:
                        continuation.resume(throwing: BleGattException.gattOperationNotSupported)
                    }
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    /// AsyncThrowingStream for observing PSD PP notifications.
    public func observePsdPPNotifications(_ checkConnection: Bool) -> AsyncThrowingStream<Psd.PPData, Error> {
        return ppStreams.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
    }

    /// Read PSD feature.
    public func readFeature(_ checkConnection: Bool) async throws -> Psd.PsdFeature {
        let cached = features.get()
        if cached.count != 0 {
            return Psd.PsdFeature(cached)
        }
        let stream = featureWaiters.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
        for try await feature in stream {
            return feature
        }
        throw BleGattException.gattDisconnected
    }

    public override func clientReady(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        waitNotificationEnabled(BlePsdClient.PSD_CP, checkConnection: checkConnection)
    }
}
