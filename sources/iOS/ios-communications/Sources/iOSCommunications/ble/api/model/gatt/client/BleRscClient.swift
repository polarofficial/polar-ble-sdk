import Foundation
import CoreBluetooth
import Combine

public class BleRscClient: BleGattClientBase, @unchecked Sendable {

    public static let RSC_SERVICE = CBUUID(string: "1814")
    let RSC_FEATURE     = CBUUID(string: "2a54")
    let RSC_MEASUREMENT = CBUUID(string: "2a53")

    public typealias BleRscNotification = (speed: Double, candence: UInt8, strideLength: Int, distance: Double, running: Bool, flags: UInt8)

    private let rscStreams = StreamContinuationList<BleRscNotification>()

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BleRscClient.RSC_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: RSC_MEASUREMENT)
        addCharacteristicRead(RSC_FEATURE)
    }

    override public func disconnected() {
        super.disconnected()
        rscStreams.finish(throwing: BleGattException.gattDisconnected)
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if err == 0 {
            if chr.isEqual(RSC_MEASUREMENT) {
                // RSC measurement requires at least 4 bytes: 1 flags + 2 speed + 1 cadence
                guard data.count >= 4 else {
                    BleLogger.error("RSC measurement data too short: \(data.count) bytes")
                    return
                }
                var index = 0
                let flags = data[0]
                index += 1
                let strideLenPresent     = (flags & 0x01) == 0x01
                let totalDistancePresent = (flags & 0x02) == 0x02
                let running              = (flags & 0x04) == 0x04
                let speedMask = UInt16(data[index]) | UInt16(UInt16(data[index + 1]) << 8)
                let speed = (Double(speedMask) / 256.0) * 3.6
                index += 2
                let cadence = data[index]
                index += 1
                var strideLength = 0
                var totalDistance = 0.0
                if strideLenPresent {
                    guard index + 2 <= data.count else {
                        BleLogger.error("RSC measurement data too short for stride length field: \(data.count) bytes")
                        return
                    }
                    strideLength = Int(UInt16(data[index]) | UInt16(UInt16(data[index + 1]) << 8))
                    index += 2
                }
                if totalDistancePresent {
                    guard index + 4 <= data.count else {
                        BleLogger.error("RSC measurement data too short for total distance field: \(data.count) bytes")
                        return
                    }
                    var distance = 0
                    memcpy(&distance, (data.subdata(in: index..<(index + 4)) as NSData).bytes, 4)
                    totalDistance = Double(distance) * 0.1
                }
                rscStreams.yield(BleRscNotification(speed: speed, candence: cadence, strideLength: strideLength,
                                                    distance: totalDistance, running: running, flags: flags))
            }
        }
    }

    /// AsyncThrowingStream for observing RSC notifications.
    public func observeRscNotifications(_ checkConnection: Bool) -> AsyncThrowingStream<BleRscNotification, Error> {
        return rscStreams.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
    }

    public override func clientReady(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        waitNotificationEnabled(RSC_MEASUREMENT, checkConnection: checkConnection)
    }
}
