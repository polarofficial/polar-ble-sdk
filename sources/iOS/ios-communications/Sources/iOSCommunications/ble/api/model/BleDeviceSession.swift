import Foundation
import CoreBluetooth

@objc open class BleDeviceSession: NSObject {

    public enum DeviceSessionState {
        case sessionClosed, sessionOpening, sessionOpenPark, sessionOpen, sessionClosing

        public func description() -> String {
            switch self {
            case .sessionClosed:  return "sessionClosed"
            case .sessionOpening: return "sessionOpening"
            case .sessionOpenPark: return "sessionOpenPark"
            case .sessionOpen:    return "sessionOpen"
            case .sessionClosing: return "sessionClosing"
            }
        }
    }

    // Put initial value that is well below actual BLE sensitivity.
    public var rssi: Int = -120

    public enum ConnectionType {
        /// connection attempt is directly requested after disconnection
        case directConnection
        /// connection attempt is requested after first advertisement head
        case connectFromAdvertisementHead
    }

    public let address: UUID
    public let advertisementContent: BleAdvertisementContent
    public var state = DeviceSessionState.sessionClosed
    public var error: Error? = nil
    public var previousState = DeviceSessionState.sessionClosed

    /// by default connect only from adv head
    public var connectionType = ConnectionType.connectFromAdvertisementHead
    var gattClients = [BleGattClientBase]()

    public init(_ addr: UUID, advertisementContent: BleAdvertisementContent? = nil) {
        self.advertisementContent = advertisementContent ?? BleAdvertisementContent()
        self.address = addr
    }

    /// helper to return BleGattClientBase instance based on service uuid
    public func fetchGattClient(_ serviceUuid: CBUUID) -> BleGattClientBase? {
        return gattClients.first(where: { $0.serviceBelongsToClient(serviceUuid) })
    }

    public func isConnectable() -> Bool {
        fatalError("not implemented")
    }

    /// Monitor services discovered on the device.
    ///
    /// - Parameter checkConnection: check current connection
    /// - Returns: AsyncThrowingStream emitting discovered service UUIDs, completing when all services are ready, or throwing on error
    public func monitorServicesDiscovered(_ checkConnection: Bool) -> AsyncThrowingStream<CBUUID, Error> {
        fatalError("not implemented")
    }

    public var disconnectedDueRemovedPairing: Bool {
        return self.error?.indicatesBLEPairingProblem ?? false
    }
}
