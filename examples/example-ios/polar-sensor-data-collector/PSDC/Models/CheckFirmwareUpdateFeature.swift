/// Copyright © 2024 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

struct CheckFirmwareUpdateFeature {
    var isSupported = false
    var status: String = "Not started"
    var inProgress: Bool = false
    var polarDeviceInfo: PolarDeviceInfo?
    var firmwareVersionAvailable: String?
}
