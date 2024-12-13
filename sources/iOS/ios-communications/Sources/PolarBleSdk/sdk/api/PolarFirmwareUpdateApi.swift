//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

public protocol PolarFirmwareUpdateApi {
    /**
     * Checks firmware update to given device.
     *
     * - Parameter identifier: Polar device ID or BT address
     * - Returns: Observable emitting status of firmware update check
     */
    func checkFirmwareUpdate(_ identifier: String) -> Observable<CheckFirmwareUpdateStatus>
    /**
     * Updates firmware to given device.
     *
     * - Parameter identifier: Polar device ID or BT address
     * - Returns: Observable emitting status of firmware update
     */
    func updateFirmware(_ identifier: String) -> Observable<FirmwareUpdateStatus>
}
