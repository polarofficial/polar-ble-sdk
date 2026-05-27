import Foundation
import CoreBluetooth

public class BleBasClient: BleGattClientBase, @unchecked Sendable {
    public static let BATTERY_SERVICE = CBUUID(string: "180F")
    private static let BATTERY_LEVEL_CHARACTERISTIC = CBUUID(string: "2A19")
    public static let BATTERY_STATUS_CHARACTERISTIC = CBUUID(string: "2BED")
    private static let UNDEFINED_BATTERY_PERCENTAGE = -1

    var cachedBatteryPercentage = AtomicInteger(initialValue: UNDEFINED_BATTERY_PERCENTAGE)

    var cachedChargeState = ChargeState.unknown
    var cachedPowerSourcesState = PowerSourcesState(batteryPresent: .unknown, wiredExternalPowerConnected: .unknown, wirelessExternalPowerConnected: .unknown)

    private let batteryStreams = StreamContinuationList<Int>()
    private let chargeStreams = StreamContinuationList<ChargeState>()
    private let powerSourceStreams = StreamContinuationList<PowerSourcesState>()

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

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BleBasClient.BATTERY_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: BleBasClient.BATTERY_LEVEL_CHARACTERISTIC)
        addCharacteristicRead(BleBasClient.BATTERY_LEVEL_CHARACTERISTIC)
        automaticEnableNotificationsOnConnect(chr: BleBasClient.BATTERY_STATUS_CHARACTERISTIC)
        addCharacteristicRead(BleBasClient.BATTERY_STATUS_CHARACTERISTIC)
    }

    override public func disconnected() {
        super.disconnected()
        cachedBatteryPercentage.set(BleBasClient.UNDEFINED_BATTERY_PERCENTAGE)
        batteryStreams.finish(throwing: BleGattException.gattDisconnected)
        cachedChargeState = .unknown
        chargeStreams.finish(throwing: BleGattException.gattDisconnected)
        powerSourceStreams.finish(throwing: BleGattException.gattDisconnected)
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        var trace = "BleBasClient process data. chr: \(chr.uuidString)"
        if err == 0 {
            if chr == BleBasClient.BATTERY_LEVEL_CHARACTERISTIC {
                var level: UInt8 = 0
                (data as NSData).getBytes(&level, length: MemoryLayout<UInt8>.size)
                trace.append(" battery percentage: \(level)")
                BleLogger.trace(trace)
                guard isValidBatteryPercentage(Int(level)) else { return }
                cachedBatteryPercentage.set(Int(level))
                batteryStreams.yield(Int(level))
            } else if chr == BleBasClient.BATTERY_STATUS_CHARACTERISTIC {
                cachedChargeState = parseChargeState(from: data)
                trace.append(" charge state: \(cachedChargeState)")
                cachedPowerSourcesState = parsePowerSourcesState(from: data)
                trace.append(" power sources state: \(cachedPowerSourcesState)")
                BleLogger.trace(trace)
                chargeStreams.yield(cachedChargeState)
                powerSourceStreams.yield(cachedPowerSourcesState)
            }
        } else {
            trace.append(" err: \(err)")
            BleLogger.error(trace)
        }
    }

    private func parseChargeState(from data: Data) -> ChargeState {
        let dataHex = data.map { String(format: "%02X", $0) }.joined(separator: " ")
        BleLogger.trace("Parsing charge state from data: \(dataHex)")
        guard data.count > 0 else { return .unknown }
        let chargeStateValue = (data[1] & 0x60) >> 5
        switch chargeStateValue {
        case 1: return .charging
        case 2: return .dischargingActive
        case 3: return .dischargingInactive
        default: return .unknown
        }
    }

    private func parsePowerSourcesState(from data: Data) -> PowerSourcesState {
        guard data.count > 0 else {
            return PowerSourcesState(batteryPresent: .unknown, wiredExternalPowerConnected: .unknown, wirelessExternalPowerConnected: .unknown)
        }
        let batteryPresent: BatteryPresentState = (data[1] & 0x01) == 1 ? .present : .notPresent
        let wiredValue = (data[1] & 0x06) >> 1
        let wired: PowerSourceState = wiredValue == 0 ? .notConnected : (wiredValue == 1 ? .connected : (wiredValue == 3 ? .reservedForFutureUse : .unknown))
        let wirelessValue = (data[1] & 0x18) >> 3
        let wireless: PowerSourceState = wirelessValue == 0 ? .notConnected : (wirelessValue == 1 ? .connected : (wirelessValue == 3 ? .reservedForFutureUse : .unknown))
        return PowerSourcesState(batteryPresent: batteryPresent, wiredExternalPowerConnected: wired, wirelessExternalPowerConnected: wireless)
    }

    public func readLevel() throws {
        try self.gattServiceTransmitter?.readValue(self, serviceUuid: BleBasClient.BATTERY_SERVICE, characteristicUuid: BleBasClient.BATTERY_LEVEL_CHARACTERISTIC)
    }

    /// AsyncThrowingStream for monitoring battery status updates.
    public func monitorBatteryStatus(_ checkConnection: Bool) -> AsyncThrowingStream<Int, Error> {
        let stream = batteryStreams.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
        // prepend cached value if valid
        let cached = cachedBatteryPercentage.get()
        if isValidBatteryPercentage(cached) {
            return AsyncThrowingStream { continuation in
                continuation.yield(cached)
                Task {
                    do {
                        for try await value in stream {
                            continuation.yield(value)
                        }
                        continuation.finish()
                    } catch {
                        continuation.finish(throwing: error)
                    }
                }
            }
        }
        return stream
    }

    /// AsyncThrowingStream for monitoring charging status updates.
    public func monitorChargingStatus(_ checkConnection: Bool) -> AsyncThrowingStream<ChargeState, Error> {
        let stream = chargeStreams.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
        let cached = cachedChargeState
        return AsyncThrowingStream { continuation in
            continuation.yield(cached)
            Task {
                do {
                    for try await value in stream { continuation.yield(value) }
                    continuation.finish()
                } catch { continuation.finish(throwing: error) }
            }
        }
    }

    /// AsyncThrowingStream for monitoring power sources status updates.
    public func monitorPowerSourcesState(_ checkConnection: Bool) -> AsyncThrowingStream<PowerSourcesState, Error> {
        let stream = powerSourceStreams.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
        let cached = cachedPowerSourcesState
        return AsyncThrowingStream { continuation in
            continuation.yield(cached)
            Task {
                do {
                    for try await value in stream { continuation.yield(value) }
                    continuation.finish()
                } catch { continuation.finish(throwing: error) }
            }
        }
    }

    private func isValidBatteryPercentage(_ batteryPercentage: Int) -> Bool {
        return (0...100).contains(batteryPercentage)
    }

    public func getBatteryLevel() -> Int {
        return cachedBatteryPercentage.get()
    }

    public func getChargeState() -> ChargeState {
        return cachedChargeState
    }
}
