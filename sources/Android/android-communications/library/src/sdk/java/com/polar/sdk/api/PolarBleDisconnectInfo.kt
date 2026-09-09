// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

/**
 * SDK-defined reason for a device disconnect.
 *
 * The reason is diagnostic. Applications should follow [recoveryAction] and wait
 * for [PolarBleApiCallbackProvider.deviceConnected] before retrying operations.
 */
enum class PolarBleDisconnectReason {
    CONNECTION_LOST,
    INSUFFICIENT_ENCRYPTION,
    INSUFFICIENT_AUTHENTICATION,
    ENCRYPTION_TIMEOUT,
    PAIRING_INFORMATION_REMOVED,
    PAIRING_NEGOTIATION_FAILED,
    SERVICE_DISCOVERY_FAILED,
    GATT_ERROR,
    // The remote device actively terminated the link, as opposed to the connection timing out.
    PEER_TERMINATED,
    // Disconnect is the expected outcome of a device-control command (restart, factory reset,
    // warehouse sleep, hibernate, turn off) that already reached its success path.
    DEVICE_COMMAND,
    UNKNOWN
}

/**
 * The specific device-control command a [PolarBleDisconnectReason.DEVICE_COMMAND] disconnect
 * followed. Only set when [PolarBleDisconnectInfo.reason] is [PolarBleDisconnectReason.DEVICE_COMMAND].
 */
enum class PolarBleDeviceCommand {
    RESTART,
    FACTORY_RESET,
    WAREHOUSE_SLEEP,
    HIBERNATE,
    TURN_OFF
}

/**
 * Recovery category for a disconnect.
 *
 * RETRY_OPERATION and RETRY_CONNECTION do not mean that arbitrary application
 * operations are replayed by the SDK. Applications must wait for the next
 * deviceConnected callback before retrying, and must not blindly replay
 * non-idempotent writes.
 */
enum class PolarBleRecoveryAction {
    NONE,
    RETRY_OPERATION,
    RETRY_CONNECTION,
    REMOVE_PAIRING_AND_PAIR_AGAIN,
    RETRY_PAIRING
}

/**
 * Detailed disconnect information delivered through the typed callback.
 *
 * @property reason SDK-defined disconnect classification.
 * @property recoveryAction recommended next recovery category.
 * @property gattStatus Android BluetoothGatt status when one was reported.
 * @property deviceCommand the command that caused the disconnect, set only when [reason] is [PolarBleDisconnectReason.DEVICE_COMMAND].
 */
data class PolarBleDisconnectInfo(
    val reason: PolarBleDisconnectReason,
    val recoveryAction: PolarBleRecoveryAction,
    val gattStatus: Int? = null,
    val deviceCommand: PolarBleDeviceCommand? = null
)
