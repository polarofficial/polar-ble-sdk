import Foundation
import CoreBluetooth

public class BleH7SettingsClient: BleGattClientBase, @unchecked Sendable {

    public static let H7_SETTINGS_SERVICE = CBUUID(string: "6217FF49-AC7B-547E-EECF-016A06970BA9")
    let H7_SETTINGS_CHARACTERISTIC        = CBUUID(string: "6217FF4A-B07D-5DEB-261E-2586752D942E")

    let inputQueue = AtomicList<[Data: Int]>()

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BleH7SettingsClient.H7_SETTINGS_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(H7_SETTINGS_CHARACTERISTIC)
    }

    public enum H7SettingsMessage: Int, Sendable {
        case h7ConfigureBroadcast = 1
        case h7Configure5khz = 2
        case h7RequestCurrentSettings = 3

        public var description: String {
            switch self {
            case .h7ConfigureBroadcast:      return "H7_CONFIGURE_BROADCAST"
            case .h7Configure5khz:           return "H7_CONFIGURE_5KHZ"
            case .h7RequestCurrentSettings:  return "H7_REQUEST_CURRENT_SETTINGS"
            }
        }
    }

    public class H7SettingsResponse {
        public let broadcastValue: UInt8
        public let khzValue: UInt8

        init(data: Data) {
            self.khzValue = (data[0] & 0x02) >> 1
            self.broadcastValue = (data[0] & 0x01)
        }

        init(broadcastValue: UInt8, khzValue: UInt8) {
            self.broadcastValue = broadcastValue
            self.khzValue = khzValue
        }

        public func description() -> String {
            return "BC value: \(broadcastValue) khz value: \(khzValue)"
        }
    }

    override public func disconnected() {
        super.disconnected()
        inputQueue.removeAll()
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if chr.isEqual(H7_SETTINGS_CHARACTERISTIC) {
            inputQueue.push([data: err])
        }
    }

    private func readSettingsValue() throws -> [Data: Int] {
        if let transport = gattServiceTransmitter {
            try transport.readValue(self, serviceUuid: BleH7SettingsClient.H7_SETTINGS_SERVICE, characteristicUuid: H7_SETTINGS_CHARACTERISTIC)
            return try inputQueue.poll(30)
        }
        throw BleGattException.gattTransportNotAvailable
    }

    /// Send a settings command to the H7 device.
    ///
    /// - Parameters:
    ///   - command: the H7 settings command
    ///   - parameter: command parameter byte
    /// - Returns: H7SettingsResponse
    public func sendSettingsCommand(_ command: H7SettingsMessage, parameter: UInt8) async throws -> H7SettingsResponse {
        return try await withCheckedThrowingContinuation { continuation in
            baseSerialDispatchQueue.async {
                do {
                    guard self.gattServiceTransmitter?.isConnected() ?? false else {
                        continuation.resume(throwing: BleGattException.gattDisconnected)
                        return
                    }
                    guard self.isServiceDiscovered() else {
                        continuation.resume(throwing: BleGattException.gattServiceNotFound)
                        return
                    }
                    self.inputQueue.removeAll()
                    let packet = try self.readSettingsValue()
                    guard packet.first?.1 == 0 else {
                        continuation.resume(throwing: BleGattException.gattAttributeError(errorCode: packet.first?.1 ?? -1))
                        return
                    }
                    let bytes = packet.first!.0
                    let khzValue = (bytes[0] & 0x02) >> 1
                    let broadcastValue = (bytes[0] & 0x01)
                    switch command {
                    case .h7ConfigureBroadcast, .h7Configure5khz:
                        var values = [UInt8](repeating: 0, count: 1)
                        if command == .h7ConfigureBroadcast {
                            values[0] = (khzValue << 1) | parameter
                        } else {
                            values[0] = (parameter << 1) | broadcastValue
                        }
                        try self.gattServiceTransmitter?.transmitMessage(self, serviceUuid: BleH7SettingsClient.H7_SETTINGS_SERVICE,
                                                                         characteristicUuid: self.H7_SETTINGS_CHARACTERISTIC,
                                                                         packet: Data(values), withResponse: true)
                        let response = try self.readSettingsValue()
                        continuation.resume(returning: H7SettingsResponse(data: (response.first?.0)!))
                    case .h7RequestCurrentSettings:
                        continuation.resume(returning: H7SettingsResponse(broadcastValue: broadcastValue, khzValue: khzValue))
                    }
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
