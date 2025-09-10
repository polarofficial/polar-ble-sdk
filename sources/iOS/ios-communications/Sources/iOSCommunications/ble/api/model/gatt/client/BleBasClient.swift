
import Foundation
import CoreBluetooth
import RxSwift

public class BleBasClient: BleGattClientBase {
    public static let BATTERY_SERVICE = CBUUID(string: "180F")
    private static let BATTERY_LEVEL_CHARACTERISTIC = CBUUID(string: "2A19")
    public static let BATTERY_STATUS_CHARACTERISTIC = CBUUID(string: "2BED")
    private static let UNDEFINED_BATTERY_PERCENTAGE = -1
    
    var cachedBatteryPercentage = AtomicInteger(initialValue: UNDEFINED_BATTERY_PERCENTAGE)
    var observers = AtomicList<RxObserver<Int>>()

    var cachedChargeState = ChargeState.unknown
    var chargeStateObservers = AtomicList<RxObserver<ChargeState>>()
    
    var cachedPowerSourcesState = PowerSourcesState(batteryPresent: .unknown, wiredExternalPowerConnected: .unknown, wirelessExternalPowerConnected: .unknown)
    var powerSourceStateObservers = AtomicList<RxObserver<PowerSourcesState>>()
    
    public enum ChargeState {
        case unknown
        case charging
        case dischargingActive
        case dischargingInactive
    }
    
    public enum PowerSourceState {
        case notConnected
        case connected
        case unknown
        case reservedForFutureUse
    }
    
    public enum BatteryPresentState {
        case notPresent
        case present
        case unknown
    }
    
    public struct PowerSourcesState {
        public let batteryPresent: BatteryPresentState
        public let wiredExternalPowerConnected: PowerSourceState
        public let wirelessExternalPowerConnected: PowerSourceState
        public init(batteryPresent: BatteryPresentState, wiredExternalPowerConnected: PowerSourceState, wirelessExternalPowerConnected: PowerSourceState) {
            self.batteryPresent = batteryPresent
            self.wiredExternalPowerConnected = wiredExternalPowerConnected
            self.wirelessExternalPowerConnected = wirelessExternalPowerConnected
        }
    }

    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BleBasClient.BATTERY_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: BleBasClient.BATTERY_LEVEL_CHARACTERISTIC)
        addCharacteristicRead(BleBasClient.BATTERY_LEVEL_CHARACTERISTIC)

        automaticEnableNotificationsOnConnect(chr: BleBasClient.BATTERY_STATUS_CHARACTERISTIC)
        addCharacteristicRead(BleBasClient.BATTERY_STATUS_CHARACTERISTIC)
    }

    override public func disconnected() {
        super.disconnected()
        cachedBatteryPercentage.set(BleBasClient.UNDEFINED_BATTERY_PERCENTAGE)
        RxUtils.postErrorAndClearList(observers, error: BleGattException.gattDisconnected)

        cachedChargeState = .unknown
        RxUtils.postErrorAndClearList(chargeStateObservers, error: BleGattException.gattDisconnected)
    }

    override public func processServiceData(_ chr: CBUUID , data: Data , err: Int ){
        var trace = "BleBasClient process data. chr: \(chr.uuidString)"
        if err == 0 {
            if chr == BleBasClient.BATTERY_LEVEL_CHARACTERISTIC {
                var level: UInt8 = 0
                (data as NSData).getBytes(&level, length: MemoryLayout<UInt8>.size)
                trace.append(" battery percentage: \(level)")
                BleLogger.trace(trace)
                cachedBatteryPercentage.set(Int(level))
                RxUtils.emitNext(observers) { observer in observer.obs.onNext(Int(level)) }
            } else if chr == BleBasClient.BATTERY_STATUS_CHARACTERISTIC {
                cachedChargeState = parseChargeState(from: data)
                trace.append(" charge state: \(cachedChargeState)")
                cachedPowerSourcesState = parsePowerSourcesState(from: data)
                trace.append(" power sources state: \(cachedPowerSourcesState)")
                BleLogger.trace(trace)
                RxUtils.emitNext(chargeStateObservers) { observer in observer.obs.onNext(cachedChargeState) }
                RxUtils.emitNext(powerSourceStateObservers) { observer in observer.obs.onNext(cachedPowerSourcesState) }
            }
        } else {
            trace.append(" err: \(err)")
            BleLogger.error(trace)
        }
    }

    private func parseChargeState(from data: Data) -> ChargeState {
        let dataHex = data.map { String(format: "%02X", $0) }.joined(separator: " ")
        BleLogger.trace("Parsing charge state from data: \(dataHex)")
        guard data.count > 0 else {
            BleLogger.error("Charge state: data is empty?")
            BleLogger.trace("Charge state: Unknown")
            return .unknown
        }
        let chargeStateValue = (data[1] & 0x60) >> 5

        switch chargeStateValue {
        case 1:
            BleLogger.trace("Charge state: Charging")
            return .charging
        case 2:
            BleLogger.trace("Charge state: Discharging Active")
            return .dischargingActive
        case 3:
            BleLogger.trace("Charge state: Discharging Inactive")
            return .dischargingInactive
        default:
            BleLogger.trace("Charge state: Unknown (value: \(chargeStateValue))")
            return .unknown
        }
    }
    
    private func parsePowerSourcesState(from data: Data) -> PowerSourcesState {
        let dataHex = data.map { String(format: "%02X", $0) }.joined(separator: " ")
        BleLogger.trace("Parsing power sources state from data: \(dataHex)")
        guard data.count > 0 else {
            BleLogger.error("Power sources state: data is empty?")
            BleLogger.trace("Power sources state: Unknown")
            return PowerSourcesState(batteryPresent: .unknown, wiredExternalPowerConnected: .unknown, wirelessExternalPowerConnected: .unknown)
        }
        
        let batteryPresentValue = (data[1] & 0x01)
        var batteryPresent: BatteryPresentState = .unknown
        switch batteryPresentValue {
        case 0:
            BleLogger.trace("Power sources state: No Battery Present")
            batteryPresent = .notPresent
        case 1:
            BleLogger.trace("Power sources state: Battery Present")
            batteryPresent = .present
        default:
            batteryPresent = .unknown
            BleLogger.trace("Power sources state: Battery present state: unknown")
        }
        
        let wiredExternalPowerConnectedValue = (data[1] & 0x06) >> 1
        var wiredExternalPowerConnected: PowerSourceState = .unknown
        switch wiredExternalPowerConnectedValue {
        case 0:
            BleLogger.trace("Power sources state: No wired external power source connected")
            wiredExternalPowerConnected = .notConnected
        case 1:
            BleLogger.trace("Power sources state: Wired external power source connected")
            wiredExternalPowerConnected = .connected
        case 3:
            BleLogger.trace("Power sources state: Wired external power source state: RFU")
            wiredExternalPowerConnected = .reservedForFutureUse
        default:
            wiredExternalPowerConnected = .unknown
            BleLogger.trace("Power sources state: Wired external power source state: unknown")
        }
        
        let wirelessExternalPowerConnectedValue = (data[1] & 0x18) >> 3
        var wirelessExternalPowerConnected: PowerSourceState = .unknown
        switch wirelessExternalPowerConnectedValue {
        case 0:
            BleLogger.trace("Power sources state: No wireless external power source connected")
            wirelessExternalPowerConnected = .notConnected
        case 1:
            BleLogger.trace("Power sources state: Wireless external power source connected")
            wirelessExternalPowerConnected = .connected
        case 3:
            BleLogger.trace("Power sources state: Wireless external power source state: RFU")
            wirelessExternalPowerConnected = .reservedForFutureUse
        default:
            wirelessExternalPowerConnected = .unknown
            BleLogger.trace("Power sources state: Wireless external power source state: unknown")
        }
        
        return PowerSourcesState(batteryPresent: batteryPresent,
                                 wiredExternalPowerConnected: wiredExternalPowerConnected,
                                 wirelessExternalPowerConnected: wirelessExternalPowerConnected)
        
    }

    // apis
    public func readLevel() throws {
        try self.gattServiceTransmitter?.readValue(self, serviceUuid: BleBasClient.BATTERY_SERVICE, characteristicUuid: BleBasClient.BATTERY_LEVEL_CHARACTERISTIC)
    }
    
    /// Get observable for monitoring battery status updates on connected device. If battery level is already cached then the cached value is emitted immidiately.
    ///
    /// - Parameter checkConnection: If `true`, verifies the initial connection before starting monitoring. If the device is not connected, error is emitted immediately.
    ///   If `false`, monitoring starts without checking the connection, and errors are only emitted if the device disconnects after subscription.
    /// - Returns: Observable stream of battery status.
    ///   - `onNext`, on every battery status update received from connected device. The value is the device battery level as a percentage from 0% to 100%.
    ///   - `onError`, if `checkConnection` is `true` and the device is not initially connected, the error is emitted immediately.
    ///     If `checkConnection` is `false`, error is only emitted if the BLE connection is lost after subscribing.
    public func monitorBatteryStatus(_ checkConnection: Bool) -> Observable<Int> {
        return RxUtils.monitor(observers, transport: gattServiceTransmitter, checkConnection: checkConnection)
            .startWith(self.cachedBatteryPercentage.get())
            .filter { value in self.isValidBatteryPercentage(value) }
    }

    /// Get observable for monitoring charging status updates on connected device. If charging status is already cached then the cached value is emitted immediately.
    ///
    /// - Parameter checkConnection: If `true`, verifies the initial connection before starting monitoring. If the device is not connected, error is emitted immediately.
    ///   If `false`, monitoring starts without checking the connection, and errors are only emitted if the device disconnects after subscription.
    /// - Returns: Observable stream of battery status.
    ///   - `onNext`, on every charging status received from connected device.
    ///   - `onError`, if `checkConnection` is `true` and the device is not initially connected, the error is emitted immediately.
    ///     If `checkConnection` is `false`, error is only emitted if the BLE connection is lost after subscribing.
    public func monitorChargingStatus(_ checkConnection: Bool) -> Observable<ChargeState> {
        return RxUtils.monitor(chargeStateObservers, transport: gattServiceTransmitter, checkConnection: checkConnection)
            .startWith(self.cachedChargeState)
    }

    private func isValidBatteryPercentage(_ batteryPercentage: Int) -> Bool {
        let batteryPercentageRange = 0...100
        return batteryPercentageRange.contains(batteryPercentage)
    }
    
    /// Get observable for monitoring power sources status updates on connected device. If power source status is already cached then the cached value is emitted immediately.
    ///
    /// - Parameter checkConnection: If `true`, verifies the initial connection before starting monitoring. If the device is not connected, error is emitted immediately.
    ///   If `false`, monitoring starts without checking the connection, and errors are only emitted if the device disconnects after subscription.
    /// - Returns: Observable stream of power sources status.
    ///   - `onNext`, on every power sources status received from connected device.
    ///   - `onError`, if `checkConnection` is `true` and the device is not initially connected, the error is emitted immediately.
    ///     If `checkConnection` is `false`, error is only emitted if the BLE connection is lost after subscribing.
    public func monitorPowerSourcesState(_ checkConnection: Bool) -> Observable<PowerSourcesState> {
        return RxUtils.monitor(powerSourceStateObservers, transport: gattServiceTransmitter, checkConnection: checkConnection)
            .startWith(self.cachedPowerSourcesState)
    }
}
