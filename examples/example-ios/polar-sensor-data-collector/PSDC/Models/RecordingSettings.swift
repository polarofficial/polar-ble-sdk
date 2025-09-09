/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

struct RecordingSettings: Identifiable, Hashable {
    let id = UUID()
    let feature: PolarDeviceDataType
    var settings: [TypeSetting] = []
    var sortedSettings: [TypeSetting] { return settings.sorted{ $0.type.rawValue < $1.type.rawValue }}
}

struct TypeSetting: Identifiable, Hashable {
    let id = UUID()
    let type: PolarSensorSetting.SettingType
    var values: [Int] = []
    var sortedValues: [Int] { return values.sorted(by:<)}
}
