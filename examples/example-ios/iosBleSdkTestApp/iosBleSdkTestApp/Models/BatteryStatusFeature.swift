/// Copyright © 2023 Polar Electro Oy. All rights reserved.

import Foundation

struct BatteryStatusFeature {
    var isSupported: Bool
    var batteryLevel: UInt {
        get {
            return _batteryLevel
        }
        set {
            if newValue >= 0 && newValue <= 100 {
                _batteryLevel = newValue
            } else {
                print("Invalid battery level \(newValue). Battery level must be between 0 and 100.")
            }
        }
    }
    
    private var _batteryLevel: UInt
    
    init(isSupported: Bool = false , batteryLevel: UInt = 0) {
        self.isSupported = isSupported
        self._batteryLevel = batteryLevel
    }
}
