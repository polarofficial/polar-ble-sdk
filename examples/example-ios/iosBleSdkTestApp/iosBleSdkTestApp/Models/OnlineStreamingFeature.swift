/// Copyright © 2022 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

enum OnlineStreamingState {
    case inProgress
    case success(url: URL?)
    case failed(error: String)
}

struct OnlineStreamingFeature {
    var isSupported = false
    var availableOnlineDataTypes: [PolarDeviceDataType: Bool] = Dictionary(uniqueKeysWithValues: zip(PolarDeviceDataType.allCases, [false]))
    var isStreaming: [PolarDeviceDataType: OnlineStreamingState] = Dictionary(uniqueKeysWithValues: PolarDeviceDataType.allCases.map { ($0, OnlineStreamingState.success(url: nil)) })
}
