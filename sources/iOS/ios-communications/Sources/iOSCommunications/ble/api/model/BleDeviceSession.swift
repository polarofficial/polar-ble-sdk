import Foundation
import CoreBluetooth
public enum BleDisconnectReason: Equatable {
    case insufficientEncryption
    case encryptionTimedOut
    case pairingInformationRemoved
    case uuidNotAllowed
    case unknown
}

/// Device-control command that a pending expected disconnect is following.
public enum BleDeviceCommand: Equatable {
    case restart
    case factoryReset
    case warehouseSleep
    case hibernate
    case turnOff
}

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
    public private(set) var hasEstablishedConnection = false
    public private(set) var securityRecoveryAttempts = 0
    public private(set) var securityRecoveryExhausted = false

    // Device-control command (restart, factory reset, warehouse sleep, hibernate, turn off)
    // whose success path was already reached; the disconnect that follows is expected.
    public private(set) var pendingDeviceCommand: BleDeviceCommand? = nil
    private var deviceCommandDisconnectDeadline: Date? = nil

    /// Call right after a device-control command's success path to mark the disconnect that
    /// is expected to follow as non-actionable. A still-valid pending mark is kept as-is rather
    /// than overwritten, since once accepted the device is already on its way out and a second
    /// command issued moments later is unlikely to be the real cause.
    public func markExpectedDeviceCommandDisconnect(_ command: BleDeviceCommand, validFor: TimeInterval = 60) {
        guard expectedDeviceCommandIfStillValid() == nil else { return }
        pendingDeviceCommand = command
        deviceCommandDisconnectDeadline = Date().addingTimeInterval(validFor)
    }

    public func expectedDeviceCommandIfStillValid() -> BleDeviceCommand? {
        guard let command = pendingDeviceCommand, let deadline = deviceCommandDisconnectDeadline, Date() <= deadline else {
            return nil
        }
        return command
    }

    public func clearExpectedDeviceCommandDisconnect() {
        pendingDeviceCommand = nil
        deviceCommandDisconnectDeadline = nil
    }

    public func markConnectionEstablished() {
        hasEstablishedConnection = true
        securityRecoveryAttempts = 0
        securityRecoveryExhausted = false
        clearExpectedDeviceCommandDisconnect()
    }

    @discardableResult
    public func recordSecurityRecoveryAttempt(maxAttempts: Int = 3) -> Bool {
        securityRecoveryAttempts += 1
        securityRecoveryExhausted = securityRecoveryAttempts >= maxAttempts
        return securityRecoveryExhausted
    }

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

    public func retryServiceDiscovery() {
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
        // A forgotten bond can surface either as an explicit peer-removed
        // error or as an ATT request that can no longer be encrypted.
        switch self.error?.bleDisconnectReason {
        case .pairingInformationRemoved:
            return true
        case .insufficientEncryption:
            return hasEstablishedConnection || securityRecoveryExhausted
        default:
            return false
        }
    }
}
