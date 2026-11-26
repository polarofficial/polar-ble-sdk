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

/// Tracks the transition into charging while HR is streaming, independently of
/// whether the UI has already consumed the warning.
struct HrChargingWarningState {
    private var chargingDevices: Set<String> = []
    private var streamingDevice: String?
    private(set) var isActive = false

    mutating func setChargeState(_ state: BleBasClient.ChargeState, for identifier: String) -> Bool {
        if state == .charging {
            chargingDevices.insert(identifier)
        } else {
            chargingDevices.remove(identifier)
        }
        return update()
    }

    mutating func setStreamingDevice(_ identifier: String?) -> Bool {
        streamingDevice = identifier
        return update()
    }

    mutating func disconnect(_ identifier: String) -> Bool {
        chargingDevices.remove(identifier)
        if streamingDevice == identifier { streamingDevice = nil }
        return update()
    }

    private mutating func update() -> Bool {
        let active = streamingDevice.map { chargingDevices.contains($0) } ?? false
        let shouldWarn = active && !isActive
        isActive = active
        return shouldWarn
    }
}

