/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

struct StreamSettings: Identifiable, Hashable {
    let id = UUID()
    let feature: DeviceStreamingFeature
    var settings: [StreamSetting] = []
    var sortedSettings: [StreamSetting] { return settings.sorted{ $0.type.rawValue < $1.type.rawValue }}
}

struct StreamSetting: Identifiable, Hashable {
    let id = UUID()
    let type: PolarSensorSetting.SettingType
    var values: [Int] = []
    var sortedValues: [Int] { return values.sorted(by:<)}
}
