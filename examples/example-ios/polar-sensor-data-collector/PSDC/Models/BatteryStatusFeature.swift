/// Copyright © 2023 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

struct BatteryStatusFeature {
    var isSupported: Bool
    
    var batteryLevel: UInt? {
        get { _batteryLevel }
        set {
            if let newValue = newValue, newValue <= 100 {
                _batteryLevel = newValue
            } else {
                _batteryLevel = nil
                print("Invalid battery level \(String(describing: newValue)). Battery level must be between 0 and 100.")
            }
        }
    }
    
    var chargeState: BleBasClient.ChargeState
    var powerSourcesState: BleBasClient.PowerSourcesState
    
    private var _batteryLevel: UInt?
    
    init(isSupported: Bool = false, batteryLevel: UInt? = nil,
         chargeStatus: BleBasClient.ChargeState = .unknown,
         powerSourcesState: BleBasClient.PowerSourcesState? = nil) {
        self.isSupported = isSupported
        self._batteryLevel = batteryLevel
        self.chargeState = chargeStatus
        self.powerSourcesState = powerSourcesState ?? BleBasClient.PowerSourcesState(batteryPresent: .unknown,
                                                                                     wiredExternalPowerConnected: .unknown,
                                                                                     wirelessExternalPowerConnected: .unknown)
    }
}

