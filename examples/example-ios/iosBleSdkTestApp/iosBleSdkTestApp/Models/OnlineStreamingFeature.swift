/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

struct OnlineStreamingFeature {
    var isSupported = false
    var availableOnlineDataTypes: [PolarDeviceDataType: Bool] = Dictionary(uniqueKeysWithValues: zip(PolarDeviceDataType.allCases, [false]))
    var isStreaming: [PolarDeviceDataType: Bool] = Dictionary(uniqueKeysWithValues: zip(PolarDeviceDataType.allCases, [false]))
}
