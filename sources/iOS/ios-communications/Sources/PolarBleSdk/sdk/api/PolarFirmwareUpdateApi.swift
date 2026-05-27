//  Copyright © 2024 Polar. All rights reserved.

import Foundation

public protocol PolarFirmwareUpdateApi {
    /// Checks firmware update to given device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_firmware_update`
    /// - Parameter identifier: Polar device ID or BT address
    /// - Returns: Publisher emitting status of firmware update check
    func checkFirmwareUpdate(_ identifier: String) -> AsyncThrowingStream<CheckFirmwareUpdateStatus, Error>

    /// Updates firmware to given device.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_firmware_update`
    /// - Parameter identifier: Polar device ID or BT address
    /// - Returns: Publisher emitting status of firmware update
    func updateFirmware(_ identifier: String) -> AsyncThrowingStream<FirmwareUpdateStatus, Error>

    /// Updates firmware from specific URL.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_firmware_update`
    /// - Parameter identifier: Polar device ID or BT address
    /// - Parameter fromFirmwareURL: URL to firmware file. Firmware file must be compatible with target device.
    /// - Returns: Publisher emitting status of firmware update
    func updateFirmware(_ identifier: String, fromFirmwareURL: URL) -> AsyncThrowingStream<FirmwareUpdateStatus, Error>
}
