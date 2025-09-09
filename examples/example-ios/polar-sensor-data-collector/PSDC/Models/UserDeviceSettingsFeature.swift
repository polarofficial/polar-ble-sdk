//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import PolarBleSdk

struct UserDeviceSettingsFeature: Identifiable {
    let id = UUID()
    var deviceUserLocation: PolarUserDeviceSettings.DeviceLocation
}
