import Foundation
import CoreBluetooth

public class BleHrClient: BleGattClientBase, @unchecked Sendable {
    public static let HR_SERVICE = CBUUID(string: "180D")
    private static let BODY_SENSOR_LOCATION = CBUUID(string: "2a38")
    static let HR_MEASUREMENT = CBUUID(string: "2a37")

    /// A tuple representing a Heart Rate (HR) notification received over BLE.
    public typealias BleHrNotification = (hr: Int, sensorContact: Bool, sensorContactSupported: Bool, energy: Int, rrs: [Int], rrsMs: [Int], rrPresent: Bool)

    private let hrStreams = StreamContinuationList<BleHrNotification>()

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BleHrClient.HR_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(BleHrClient.BODY_SENSOR_LOCATION)
        automaticEnableNotificationsOnConnect(chr: BleHrClient.HR_MEASUREMENT, disableOnDisconnect: true)
    }

    override public func disconnected() {
        super.disconnected()
        hrStreams.finish(throwing: BleGattException.gattDisconnected)
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if chr.isEqual(BleHrClient.HR_MEASUREMENT) && err == 0 {
            // HR measurement requires at least 2 bytes: flags + one HR byte
            guard data.count >= 2 else {
                BleLogger.error("HR measurement data too short: \(data.count) bytes")
                return
            }
            var offset = 0
            let hrFormat = data[0] & 0x01
            let sensorContact = ((data[0] & 0x06) >> 1) == 0x03
            let contactSupported = (data[0] & 0x04) != 0
            let energyExpended = (data[0] & 0x08) >> 3
            let rrPresent = (data[0] & 0x10) >> 4
            // 16-bit HR format needs a third byte
            guard hrFormat == 0 || data.count >= 3 else {
                BleLogger.error("HR measurement data too short for 16-bit HR format: \(data.count) bytes")
                return
            }
            let hrValue = hrFormat == 1 ? (Int(data[1]) + (Int(data[2]) << 8)) : Int(data[1])
            offset = Int(hrFormat) + 2
            var energy = 0
            if energyExpended == 1 {
                guard offset + 2 <= data.count else {
                    BleLogger.error("HR measurement data too short for energy expended field: \(data.count) bytes")
                    return
                }
                energy = Int(data[offset]) + (Int(data[offset + 1]) << 8)
                offset += 2
            }
            var rrs = [Int]()
            var rrsMs = [Int]()
            if rrPresent == 1 {
                let len = data.count
                while offset + 1 < len {
                    let rrValueRaw = Int(data[offset]) | (Int(data[offset + 1]) << 8)
                    offset += 2
                    rrs.append(rrValueRaw)
                    rrsMs.append(BleHrClient.mapRr1024ToRrMs(rrsRaw: rrValueRaw))
                }
            }
            hrStreams.yield((hr: hrValue, sensorContact: sensorContact, sensorContactSupported: contactSupported,
                             energy: energy, rrs: rrs, rrsMs: rrsMs, rrPresent: rrPresent == 1))
        }
    }

    private static func mapRr1024ToRrMs(rrsRaw: Int) -> Int {
        return Int(round((Float(rrsRaw) / 1024.0) * 1000.0))
    }

    /// AsyncThrowingStream for observing heart rate data from BLE HR Service.
    ///
    /// - Parameter checkConnection: if connection is checked on start of observation
    /// - Returns: AsyncThrowingStream of heart rate data
    public func observeHrNotifications(_ checkConnection: Bool) -> AsyncThrowingStream<BleHrNotification, Error> {
        return hrStreams.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
    }

    /// Stop observing heart rate data from BLE HR Service.
    public func stopObserveHrNotifications(checkConnection: Bool) {
        BleLogger.trace("Stop observing HR")
        hrStreams.finish()
    }
}
