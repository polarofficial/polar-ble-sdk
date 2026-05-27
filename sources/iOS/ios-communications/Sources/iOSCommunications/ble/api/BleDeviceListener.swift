import Foundation
import CoreBluetooth
import Combine

/// BleState mapping from CB state
public enum BleState: Int {
    case unknown
    case resetting
    case unsupported
    case unauthorized
    case poweredOff
    case poweredOn
}

/// Ble central api
public protocol BleDeviceListener {

    /// helper to ask ble power state
    func blePowered() -> Bool

    /// power state observer
    var powerStateObserver: BlePowerStateObserver? { get set }

    /// enable or disable automatic reconnection
    var automaticReconnection: Bool { get set }

    /// enable or disable scan uuid filter
    var servicesToScanFor: [CBUUID]? { get set }

    /// enable or disable scan pre filter
    var scanPreFilter: ((_ content: BleAdvertisementContent) -> Bool)? { get set }

    /// enable or disable automatic H10 mapping
    var automaticH10Mapping: Bool { get set }

    /// Start scanning ble devices.
    ///
    /// - Parameters:
    ///   - uuids: optional list of service UUIDs to filter by
    ///   - identifiers: optional list of device identifiers
    ///   - fetchKnownDevices: if true, also emit already known sessions
    /// - Returns: AnyPublisher emitting device advertisements
    func search(_ uuids: [CBUUID]?, identifiers: [UUID]?, fetchKnownDevices: Bool) -> AnyPublisher<BleDeviceSession, Error>

    /// Start connection request for device.
    func openSessionDirect(_ session: BleDeviceSession)

    /// Start disconnection request for device.
    func closeSessionDirect(_ session: BleDeviceSession)

    /// request to clear all cached sessions
    @discardableResult
    func removeAllSessions(_ inState: Set<BleDeviceSession.DeviceSessionState>) -> Int
    @discardableResult
    func removeAllSessions() -> Int

    /// return all known sessions
    func allSessions() -> [BleDeviceSession]

    /// Monitor BLE power state changes. Immediately emits the current state upon subscription.
    func monitorBleState() -> AnyPublisher<BleState, Error>

    /// Monitor device session state changes for all sessions.
    func monitorDeviceSessionState() -> AnyPublisher<(session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState), Error>
}

public protocol BleDeviceSessionStateObserver: AnyObject {
    func stateChanged(_ session: BleDeviceSession)
}

public protocol BlePowerStateObserver: AnyObject {
    func powerStateChanged(_ state: BleState)
}

public extension BleDeviceListener {
    func search(_ uuids: [CBUUID]? = nil, identifiers: [UUID]? = nil, fetchKnownDevices: Bool = false) -> AnyPublisher<BleDeviceSession, Error> {
        return search(uuids, identifiers: identifiers, fetchKnownDevices: fetchKnownDevices)
    }
}

/// Extension to provide distinct filtering on any Publisher whose Output is Hashable.
public extension Publisher where Output: Hashable {
    func distinct() -> AnyPublisher<Output, Failure> {
        var seen = Set<Output>()
        return self
            .filter { element in
                objc_sync_enter(self as AnyObject)
                defer { objc_sync_exit(self as AnyObject) }
                guard !seen.contains(element) else { return false }
                seen.insert(element)
                return true
            }
            .eraseToAnyPublisher()
    }
}
