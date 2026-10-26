// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import UIKit
import PolarBleSdk
import os.log

/**
 * Monitors and logs battery levels for both the phone and connected Polar devices.
 *
 * Call ``updateDeviceBattery(_:level:)`` whenever the SDK delivers a battery level,
 * ``updateDeviceChargingState(_:chargingStatus:)`` when a charging state arrives, and
 * ``logEvent(deviceId:event:)`` at the start and end of each sync or cleanup operation.
 *
 * Log lines are emitted via `os.log` (subsystem `com.polar.bgsync`, category `POLARBAT`)
 * and also written to the iOS system log, using the following pipe-separated format
 * designed for easy grepping and CSV parsing for battery-vs-sync graphing:
 *
 * ```
 * POLARBAT|<ISO8601_UTC>|device=<ID>|event=<EVENT>|phone_bat=<N>%|phone_charging=<STATE>|device_bat=<N_or_UNKNOWN>%|device_charging=<STATUS>
 * ```
 *
 * **Events:** SYNC_STARTED, SYNC_COMPLETED, CLEANUP_STARTED, CLEANUP_COMPLETED
 *
 * **phone_charging:** CHARGING | FULL | UNPLUGGED | UNKNOWN  (UIDevice.batteryState)
 *
 * **device_charging:** CHARGING | DISCHARGING_ACTIVE | DISCHARGING_INACTIVE | UNKNOWN  (BleBasClient.ChargeState)
 *
 * ### Grepping examples (macOS Console or `log` CLI)
 * ```bash
 * # Stream from a connected device
 * xcrun devicectl device syslog stream --device <udid> | grep POLARBAT
 *
 * # Filter by event
 * log stream --predicate 'subsystem == "com.polar.bgsync" AND category == "POLARBAT"'
 * ```
 *
 * ### CSV parsing (Python)
 * ```python
 * lines = [l for l in log_text.splitlines() if "POLARBAT|" in l]
 * # Each line: label | timestamp | device | event | phone_bat | phone_charging | device_bat | device_charging
 * ```
 */
class BatteryLevelMonitor {

    struct BatterySnapshot {
        let phoneBatteryPercent: Int?
        let phoneChargingState: String
        let deviceBatteryPercent: UInt?
        let deviceChargingState: String

        var hasKnownDeviceCharging: Bool {
            !deviceChargingState.uppercased().isEmpty && deviceChargingState.uppercased() != "UNKNOWN"
        }
    }

    static let shared = BatteryLevelMonitor()

    private static let logger = Logger(subsystem: "com.polar.bgsync", category: "POLARBAT")

    /// Last known battery level per device ID (0-100). Nil = not yet received.
    private var deviceBatteryLevels: [String: UInt] = [:]

    /// Last known charging state string per device ID.
    private var deviceChargingStates: [String: String] = [:]

    /// Serial queue for thread-safe access to battery state dictionaries
    private let stateQueue = DispatchQueue(label: "com.polar.bgsync.battery-state", qos: .userInitiated)

    private let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private init() {
        // Enable battery monitoring so UIDevice can report level and state.
        UIDevice.current.isBatteryMonitoringEnabled = true
    }

    // MARK: - State updates

    /**
     * Updates the cached device battery level.
     * Call this from `batteryLevelReceived(_:batteryLevel:)` in the sync engine.
     * Thread-safe: serialized via `stateQueue`.
     */
    func updateDeviceBattery(_ deviceId: String, level: UInt) {
        stateQueue.async { [weak self] in
            self?.deviceBatteryLevels[deviceId] = level
        }
    }

    /**
     * Updates the cached device charging state.
     * Call this from `batteryChargingStatusReceived(_:chargingStatus:)` in the sync engine.
     * Thread-safe: serialized via `stateQueue`.
     */
    func updateDeviceChargingState(_ deviceId: String, chargingStatus: BleBasClient.ChargeState) {
        stateQueue.async { [weak self] in
            self?.deviceChargingStates[deviceId] = self?.chargeStateString(chargingStatus) ?? "UNKNOWN"
        }
    }

    /**
     * Removes cached battery state for a device (e.g. when a session ends).
     * Thread-safe: serialized via `stateQueue`.
     */
    func clearDevice(_ deviceId: String) {
        stateQueue.async { [weak self] in
            self?.deviceBatteryLevels.removeValue(forKey: deviceId)
            self?.deviceChargingStates.removeValue(forKey: deviceId)
        }
    }

    // MARK: - Logging

    /**
     * Logs a structured battery event line via `os.log` and returns the formatted line
     * so callers can also route it to a UI log or diagnostic callback.
     *
     * Thread-safe: state access is serialized via `stateQueue`.
     *
     * - Parameters:
     *   - deviceId: Polar device ID (e.g. "B5F8B5C3")
     *   - event:    One of: SYNC_STARTED, SYNC_COMPLETED, CLEANUP_STARTED, CLEANUP_COMPLETED
     * - Returns: the formatted POLARBAT line that was logged
     */
    @discardableResult
    func logEvent(deviceId: String, event: String) -> String {
        let timestamp = isoFormatter.string(from: Date())

        // Phone battery
        let phoneBatRaw = UIDevice.current.batteryLevel   // -1.0 if monitoring disabled
        let phoneBatStr = phoneBatRaw >= 0 ? String(format: "%.0f", phoneBatRaw * 100) : "UNKNOWN"
        let phoneCharging = phoneChargingStateString()

        // Device battery (last received value from SDK) — read from state queue synchronously
        var deviceBatStr = "UNKNOWN"
        var deviceCharging = "UNKNOWN"
        stateQueue.sync { [weak self] in
            if let deviceBat = self?.deviceBatteryLevels[deviceId] {
                deviceBatStr = "\(deviceBat)"
            }
            deviceCharging = self?.deviceChargingStates[deviceId] ?? "UNKNOWN"
        }

        let line = "POLARBAT|\(timestamp)" +
            "|device=\(deviceId)" +
            "|event=\(event)" +
            "|phone_bat=\(phoneBatStr)%" +
            "|phone_charging=\(phoneCharging)" +
            "|device_bat=\(deviceBatStr)%" +
            "|device_charging=\(deviceCharging)"

        Self.logger.info("\(line, privacy: .public)")
        return line
    }

    /// Returns the latest battery snapshot for a device using the monitor cache.
    /// Device charging state is considered usable only when non-UNKNOWN.
    func currentSnapshot(deviceId: String) -> BatterySnapshot {
        let phoneBatRaw = UIDevice.current.batteryLevel
        let phoneBatteryPercent = phoneBatRaw >= 0 ? Int(phoneBatRaw * 100) : nil
        let phoneCharging = phoneChargingStateString()

        var deviceBatteryPercent: UInt?
        var deviceCharging = "UNKNOWN"
        stateQueue.sync { [weak self] in
            deviceBatteryPercent = self?.deviceBatteryLevels[deviceId]
            deviceCharging = self?.deviceChargingStates[deviceId] ?? "UNKNOWN"
        }

        return BatterySnapshot(
            phoneBatteryPercent: phoneBatteryPercent,
            phoneChargingState: phoneCharging,
            deviceBatteryPercent: deviceBatteryPercent,
            deviceChargingState: deviceCharging
        )
    }

    /// Builds PLOG BAT params from the monitor cache.
    /// When `requireKnownDeviceCharging` is true, returns nil until a real device charging
    /// state is available to avoid emitting misleading `device_charging=UNKNOWN` entries.
    func plogBatteryParams(deviceId: String, requireKnownDeviceCharging: Bool = true) -> String? {
        let snap = currentSnapshot(deviceId: deviceId)
        guard let phoneBatteryPercent = snap.phoneBatteryPercent else { return nil }
        guard let deviceBatteryPercent = snap.deviceBatteryPercent else { return nil }
        if requireKnownDeviceCharging && !snap.hasKnownDeviceCharging {
            return nil
        }

        return "phone=\(phoneBatteryPercent) phone_charging=\(snap.phoneChargingState) device=\(deviceBatteryPercent) device_charging=\(snap.deviceChargingState)"
    }

    // MARK: - Private helpers

    private func phoneChargingStateString() -> String {
        switch UIDevice.current.batteryState {
        case .charging:     return "CHARGING"
        case .full:         return "FULL"
        case .unplugged:    return "UNPLUGGED"
        case .unknown:      return "UNKNOWN"
        @unknown default:   return "UNKNOWN"
        }
    }

    private func chargeStateString(_ state: BleBasClient.ChargeState) -> String {
        switch state {
        case .charging:             return "CHARGING"
        case .dischargingActive:    return "DISCHARGING_ACTIVE"
        case .dischargingInactive:  return "DISCHARGING_INACTIVE"
        case .unknown:              return "UNKNOWN"
        }
    }
}
