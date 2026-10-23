
import CoreBluetooth
import Foundation

public class BleSonovaClient: BleGattClientBase {
    public static let SPORT_ID_SERVICE = CBUUID(string: "D4731F3A-A37D-4218-AED6-A68F5030078C")
    public static let SPORT_ID_CHARACTERISTIC = CBUUID(string: "ebbcd029-50b1-43f8-be3d-633886446b4e")

    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BleSonovaClient.SPORT_ID_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
    }

    override public func disconnected() {
        super.disconnected()
        let disconnectBytes: [UInt8] = [0]
        do {
            guard let transmitter = self.gattServiceTransmitter else {
                throw BleGattException.gattServiceNotFound
            }
            try transmitter.transmitMessage(self,
                                            serviceUuid: BleSonovaClient.SPORT_ID_SERVICE,
                                            characteristicUuid: BleSonovaClient.SPORT_ID_CHARACTERISTIC,
                                            packet: Data(disconnectBytes),
                                            withResponse: true)
        } catch BleGattException.gattServiceNotFound {
            BleLogger.error("Gatt service not found.")
        } catch {
            BleLogger.error("Error transmitting disconnect sport id: \(error)")
        }
    }

    override public func serviceDataWritten(_ characteristic: CBUUID, err status: Int) {
        guard status != 0 else {
            BleLogger.error("Error writing to characteristic: \(characteristic), status: \(status)")
            return
        }
    }

    public func writeSportId(sportId: Int) throws {
        guard (0...200).contains(sportId) else {
            throw BleGattException.gattDataError(description: "Sport id must be between 0 and 200, was: \(sportId)")
        }
        let sportIdBytes: [UInt8] = [UInt8(sportId)]
        do {
            guard let transmitter = self.gattServiceTransmitter else {
                throw BleGattException.gattServiceNotFound
            }
            try transmitter.transmitMessage(self,
                                            serviceUuid: BleSonovaClient.SPORT_ID_SERVICE,
                                            characteristicUuid: BleSonovaClient.SPORT_ID_CHARACTERISTIC,
                                            packet: Data(sportIdBytes),
                                            withResponse: true)
        } catch BleGattException.gattServiceNotFound {
            BleLogger.error("Gatt service not found.")
        } catch {
            BleLogger.error("Error transmitting sport id: \(error)")
        }
    }
}
