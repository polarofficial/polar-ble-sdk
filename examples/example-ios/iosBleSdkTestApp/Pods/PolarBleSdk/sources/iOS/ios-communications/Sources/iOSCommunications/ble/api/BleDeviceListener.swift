
import RxSwift
import Foundation
import CoreBluetooth

/// BleState mapping from CB state
public enum BleState : Int {
    case unknown
    case resetting
    case unsupported
    case unauthorized
    case poweredOff
    case poweredOn
}

/// Ble central api
public protocol BleDeviceListener{
    
    /// helper to ask ble power state
    ///
    /// - Returns: true ble powered
    func blePowered() -> Bool
    
    /// ble power state
    ///
    /// - Returns: Observable ble power state
    @available(*, deprecated, message: "use powerStateObserver instead")
    func monitorBleState() -> Observable<BleState>
    
    /// power state observer
    var powerStateObserver: BlePowerStateObserver? {get set}
    
    /// enable or disable automatic reconnection
    var automaticReconnection: Bool {get set}

    /// enable or disable scan uuid filter
    var servicesToScanFor: [CBUUID]? {get set}
    
    /// enable or disable scan pre filter
    var scanPreFilter: ((_ content: BleAdvertisementContent) -> Bool)? {get set}
    
    /// enable or disable automatic H10 mapping
    var automaticH10Mapping: Bool {get set}
    
    /// Start scanning ble devices
    ///
    /// - Parameters:
    ///   - uuids: optional list of services, to look for from corebluetooth
    ///   - identifiers: optional list of device identifiers to look for from corebluetooth
    ///   - preFilter: pre filter before memory allocation, for performance reason
    /// - Returns: Observable stream of device advertisements
    func search(_ uuids: [CBUUID]?, identifiers: [UUID]?, fetchKnownDevices: Bool) -> Observable<BleDeviceSession>
    
    /// Start connection request for device, callbacks are informed to monitorDeviceSessionState or
    /// deviceSessionStateObserver
    ///
    /// - Parameter session: session instance
    /// - Returns:
    func openSessionDirect(_ session: BleDeviceSession) 
    
    /// all session state changes, deprecated
    ///
    /// - Returns: Observable stream
    func monitorDeviceSessionState() -> Observable<(session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState)>

    /// all session state changes
    ///
    /// - Returns: Observable stream
    @available(*, deprecated, message: "use monitorDeviceSessionState instead")
    var deviceSessionStateObserver: BleDeviceSessionStateObserver? {get set}
    
    /// Start disconnection request for device, callbacks are informed to monitorDeviceSessionState or
    /// deviceSessionStateObserver
    ///
    /// - Parameter session: device to be disconnected
    /// - Returns:
    func closeSessionDirect(_ session: BleDeviceSession)
    
    /// request to clear all cached sessions
    ///
    /// - Parameter inState: set of states allowed to be removed default Closed | Park
    /// - Returns: count of session removed successfully
    @discardableResult
    func removeAllSessions(_ inState: Set<BleDeviceSession.DeviceSessionState>) -> Int
    @discardableResult
    func removeAllSessions() -> Int
    
    /// return all known sessions
    ///
    /// - Returns: list of sessions
    func allSessions() -> [BleDeviceSession]
}

public protocol BleDeviceSessionStateObserver: AnyObject {
    func stateChanged(_ session: BleDeviceSession)
}

public protocol BlePowerStateObserver: AnyObject {
    func powerStateChanged(_ state: BleState)
}

public extension BleDeviceListener {
    func search(_ uuids: [CBUUID]? = nil, identifiers: [UUID]?=nil, fetchKnownDevices: Bool = false)  -> Observable<BleDeviceSession> {
        return search(uuids, identifiers: identifiers, fetchKnownDevices: fetchKnownDevices)
    }
}

/// extension to provide distinct 
public extension Observable where Element: Hashable {
    func distinct() -> Observable<Element> {
        var set = Set<Element>()
        return concatMap { element -> Observable<Element> in
            objc_sync_enter(self)
            defer {
                objc_sync_exit(self)
            }
            if set.contains(element) {
                return RxSwift.Observable<Element>.empty()
            } else {
                set.insert(element)
                return RxSwift.Observable<Element>.just(element)
            }
        }.do(onDispose:  {
            set = Set<Element>()
        })
    }
}
