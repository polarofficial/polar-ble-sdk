/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

/// Polar Ble API connection observer.
public protocol PolarBleApiObserver: AnyObject {
    
    /// Callback when connection attempt is started to device
    ///
    /// - Requires SDK feature(s): None (core API callback).
    ///
    /// - Parameter identifier: Polar device info
    ///
    func deviceConnecting(_ identifier: PolarDeviceInfo)
    
    /// Device connection has been established.
    ///
    /// - Requires SDK feature(s): None (core API callback).
    ///
    /// - Parameter identifier: Polar device info
    ///
    func deviceConnected(_ identifier: PolarDeviceInfo)
    
    /// Connection lost to device with a typed diagnostic and recovery recommendation.
    ///
    /// - Requires SDK feature(s): None (core API callback).
    /// - Parameter identifier: Polar device info
    /// - Parameter info: Disconnect reason and recommended recovery action. The
    ///   action describes the next recovery category; it does not guarantee that
    ///   recovery will succeed.
    ///
    /// For `.retryOperation`, the application should not immediately replay an
    /// operation against the old connection. The SDK attempts to restore the BLE
    /// session and its normal connection setup. The application may retry its
    /// failed SDK request only after `deviceConnected` is received again. The SDK
    /// does not retain and replay arbitrary application operations for the app.
    /// Reads, streaming setup, and notification subscriptions can normally be
    /// requested again. Applications must not blindly repeat a non-idempotent
    /// write unless they know that the device did not apply it before the
    /// disconnect.
    ///
    /// For `.retryConnection`, the SDK attempts to retry the connection and
    /// security establishment. The application should keep the device nearby and
    /// powered on, and wait for `deviceConnected` before retrying its operation.
    /// If the SDK cannot restore the session, the application should present a
    /// connection/security failure and may offer another manual connection
    /// attempt. A timeout alone is not proof that the pairing record was removed.
    ///
    /// For `.removePairingAndPairAgain`, the pairing information is no longer
    /// usable. The application should stop retrying, instruct the user to remove
    /// or forget the device in iOS Bluetooth settings, remove the corresponding
    /// pairing from the sensor/watch when the device requires it, put the device
    /// into pairing mode, and connect again. The application should not repeatedly
    /// reconnect until this action has been completed.
    ///
    /// For `.none`, no pairing-specific recovery is indicated. The application
    /// should use its normal disconnected-device handling and may try a new
    /// connection when appropriate.
    ///
    /// The SDK may emit this callback before automatic recovery is complete.
    /// Applications should use `deviceConnected` as the signal that a new
    /// connection is ready; they should not assume that this callback alone means
    /// an operation can be retried immediately.
    ///
    func deviceDisconnected(_ identifier: PolarDeviceInfo, info: PolarBleDisconnectInfo)

    /// Connection lost to device.
    /// If PolarBleApi#disconnectFromPolarDevice is not called, a new connection attempt is dispatched automatically.
    ///
    /// - Requires SDK feature(s): None (core API callback).
    ///
    /// - Parameter identifier: Polar device info
    /// -  Parameter pairingError: If true, it indicates that the disconnection was caused by a pairing error.
    /// In this case, try removing the pairing from the system settings.
    ///
    @available(*, deprecated, message: "Use deviceDisconnected(_:info:) to receive the specific disconnect reason and recovery action.")
    func deviceDisconnected(_ identifier: PolarDeviceInfo, pairingError: Bool)
}

public enum PolarBleDisconnectReason: Equatable {
    /// The connection was lost without a classified pairing/security condition.
    case connectionLost
    /// A protected ATT operation lacked link encryption. During an initial
    /// connection attempt, the SDK keeps this condition retryable so pairing or
    /// security negotiation can complete. After a session that had previously
    /// connected successfully, the same condition is treated as a stale or
    /// unusable pairing: automatic reconnection stops and the application should
    /// remove the pairing from iOS and the device when required, pair again, and
    /// reconnect before retrying the operation.
    case insufficientEncryption
    /// The BLE encryption handshake timed out. Reconnect/security establishment
    /// should be retried before asking the user to re-pair.
    case encryptionTimedOut
    /// CoreBluetooth reported that the peer pairing information was removed or
    /// can no longer be used. User-driven pairing recovery is required.
    case pairingInformationRemoved
    /// iOS started pairing after an encrypted ATT failure, but the peripheral
    /// rejected the pairing negotiation. This does not prove that pairing
    /// information was removed.
    case pairingNegotiationFailed
    /// CoreBluetooth rejected a UUID during service/security handling. Service
    /// discovery or the affected operation should be retried; this is not proof
    /// that pairing must be removed.
    case uuidNotAllowed
    /// An error did not match a known SDK classification.
    case unknown
    /// Disconnect is the expected outcome of a device-control command (restart, factory reset,
    /// warehouse sleep, hibernate, turn off) that already reached its success path.
    case deviceCommand
}

/// The specific device-control command a `.deviceCommand` disconnect followed. Only set when
/// `PolarBleDisconnectInfo.reason` is `.deviceCommand`.
public enum PolarBleDeviceCommand: Equatable {
    case restart
    case factoryReset
    case warehouseSleep
    case hibernate
    case turnOff
}

public enum PolarBleRecoveryAction: Equatable {
    /// No pairing-specific action is required. Use normal disconnect handling.
    case none
    /// Restore the BLE session first. After `deviceConnected`, retry the failed
    /// idempotent SDK operation or setup. Do not blindly replay non-idempotent
    /// writes because the device may have applied them before disconnecting.
    case retryOperation
    /// Retry connection and security establishment first. After `deviceConnected`,
    /// rediscovery and SDK initialization are complete, retry the interrupted
    /// operation. Escalate only after the SDK retry policy is exhausted.
    case retryConnection
    /// Do not continue automatic retries. Remove/forget the pairing from the
    /// phone and, where required, from the device, pair again, and reconnect.
    case removePairingAndPairAgain
    /// Stop the current attempt, put the peripheral into its explicit pairing
    /// mode, and start a new connection attempt. Do not tell the user that an
    /// existing phone pairing is invalid unless the SDK reports
    /// `pairingInformationRemoved`.
    case retryPairing
}

public struct PolarBleDisconnectInfo: Equatable {
    /// The SDK-defined condition that caused or best explains the disconnect.
    public let reason: PolarBleDisconnectReason
    /// The next recovery category expected from the application or SDK.
    public let recoveryAction: PolarBleRecoveryAction
    /// The command that caused the disconnect, set only when `reason` is `.deviceCommand`.
    public let deviceCommand: PolarBleDeviceCommand?

    public init(reason: PolarBleDisconnectReason, recoveryAction: PolarBleRecoveryAction, deviceCommand: PolarBleDeviceCommand? = nil) {
        self.reason = reason
        self.recoveryAction = recoveryAction
        self.deviceCommand = deviceCommand
    }
}

public extension PolarBleApiObserver {
    func deviceDisconnected(_ identifier: PolarDeviceInfo, info: PolarBleDisconnectInfo) {}
    func deviceDisconnected(_ identifier: PolarDeviceInfo, pairingError: Bool) {}
}

/// Bluetooth state observer.
public protocol PolarBleApiPowerStateObserver: AnyObject {
    
    /// Ble powered on event.
    ///
    /// - Requires SDK feature(s): None (core API callback).
    ///
    func blePowerOn()
    
    /// Ble powered off event, no further actions are needed from the application.
    ///
    /// - Requires SDK feature(s): None (core API callback).
    ///
    func blePowerOff()
}

/// Device info observer.
public protocol PolarBleApiDeviceInfoObserver: AnyObject {
    
    /// Battery level received from device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_battery_info`
    ///
    /// - Parameter identifier: Polar device id
    /// - Parameter batteryLevel: battery level in precentage 0-100%
    ///
    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt)

    /// Battery charging status received from device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_battery_info`
    ///
    /// - Parameter identifier: Polar device id
    /// - Parameter chargingStatus: Battery charging status
    ///
    func batteryChargingStatusReceived(_ identifier: String, chargingStatus: BleBasClient.ChargeState)

    /// Battery power source status received from device
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_battery_info`
    ///
    /// - Parameter identifier: Polar device id
    /// - Parameter powerSourcesState: Includes presence of battery, and power sources -- wired and wireless -- states
    ///
    func batteryPowerSourcesStateReceived(_ identifier: String, powerSourcesState: BleBasClient.PowerSourcesState)
    
    /// Received a Device Information Service (DIS) characteristic value. Called once per available DIS characteristic.
    /// Characteristics include model number, manufacturer name, firmware/hardware/software revision, serial number, system ID, etc.
    ///
    /// Note: for `SYSTEM_ID` (UUID `2a23`) the value is a raw ASCII representation that may not be human-readable.
    /// Use `disInformationReceivedWithKeysAsStrings` to receive a properly byte-reordered hex string for System ID.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_device_info`
    ///
    /// - Parameter identifier: Polar device id
    /// - Parameter uuid: CBUUID identifying the DIS characteristic (e.g. `2a26` = firmware revision, `2a24` = model number)
    /// - Parameter value: ASCII string representation of the characteristic data
    ///
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String)

    /// Received a Device Information Service (DIS) characteristic value with string keys. Called once per available DIS characteristic.
    /// Similar to `disInformationReceived` but uses string keys instead of `CBUUID`. Prefer this variant over
    /// `disInformationReceived` when displaying or storing DIS data, as `SYSTEM_ID` is returned as a
    /// properly byte-reordered hex string (key `"SYSTEM_ID_HEX"`) rather than raw ASCII bytes.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_device_info`
    ///
    /// - Parameter identifier: Polar device id
    /// - Parameter key: UUID string of the DIS characteristic (e.g. `"2A26"` = firmware revision), or `"SYSTEM_ID_HEX"` for the system ID
    /// - Parameter value: ASCII string value for most characteristics; byte-reordered hex string for `SYSTEM_ID_HEX`
    ///
    func disInformationReceivedWithKeysAsStrings(_ identifier: String, key: String, value: String)
}

public extension PolarBleApiDeviceInfoObserver {
    func batteryPowerSourcesStateReceived(_ identifier: String, powerSourcesState: BleBasClient.PowerSourcesState) {}
}

/// Heart rate observer
public protocol PolarBleApiDeviceHrObserver: AnyObject {
    
    /// HR notification received. Notice when using OH1 and PPI stream is started this callback will produce 0 hr.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_hr`, `PolarBleSdkFeature.feature_polar_online_streaming`
    ///
    /// - Parameter identifier: Polar device id
    /// - Parameter data: Heart rate measurement tuple containing:
    ///   - hr: Heart rate value in BPM
    ///   - rrs: RR interval values in 1/1024 second units
    ///   - rrsMs: RR interval values in milliseconds
    ///   - contact: `true` if the sensor has contact with a measurable surface (e.g. skin)
    ///   - contactSupported: `true` if the sensor supports contact status detection
    ///
    func hrValueReceived(_ identifier: String, data: (hr: UInt8, rrs: [Int], rrsMs: [Int], contact: Bool, contactSupported: Bool))
}

/// Data client observer
public protocol PolarBleApiDeviceFeaturesObserver: AnyObject {

    /// The feature is available in this device and it is ready to use.
    /// Called only for the features which are specified in [PolarBleApi]
    /// construction. SDK calls this method after device is connected.
    /// When this call back is received, device should be ready to
    /// perform methods that depend on indicated feature.
    ///
    /// - Requires SDK feature(s): None (observer callback).
    ///
    /// - Parameter identifier: Polar device id
    /// - Parameter feature: the feature that is now ready for use
    ///
    func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdkFeature)
    
    
    /// All features that are listed in [PolarBleApi] construction and are supported by this device are ready. 
    /// Constructing with an empty feature set causes SDK to enable all SDK features by default.
    /// SDK calls this method after a device is connected.
    ///
    /// This is the simplest way to wait for device to become ready for further operations.
    ///
    /// The `ready` list contains features that are confirmed ready for use.
    /// The `unavailable` list contains features that are not supported by this device.
    /// Features absent from both lists timed out before their readiness could be established;
    /// they can still become ready later and will be reported via `bleSdkFeatureReady`.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    ///   - ready: features that are confirmed ready for use on this device
    ///   - unavailable: features that are not supported by this device
    ///
    /// - Requires SDK feature(s): None (observer callback).
    ///
    func bleSdkFeaturesReadiness(_ identifier: String, ready: [PolarBleSdkFeature], unavailable: [PolarBleSdkFeature])

}

public extension PolarBleApiDeviceFeaturesObserver {
    func bleSdkFeaturesReadiness(_ identifier: String, ready: [PolarBleSdkFeature], unavailable: [PolarBleSdkFeature]) {}
}

/// logger observer
public protocol PolarBleApiLogger: AnyObject {
    
    /// log message from sdk
    ///
    /// - Requires SDK feature(s): None (core API callback).
    ///
    /// - Parameter str: message
    func message(_ str: String)
}
