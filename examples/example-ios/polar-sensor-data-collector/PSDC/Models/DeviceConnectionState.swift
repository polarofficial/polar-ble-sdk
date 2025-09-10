/// Copyright © 2023 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

let nullPolarDeviceInfo = (deviceId: "no device", address: UUID(), rssi: 0, name: "no device", connectable: false)

enum DeviceConnectionState {
    case noDevice(PolarDeviceInfo)
    case connecting(PolarDeviceInfo)
    case connected(PolarDeviceInfo)
    case disconnected(PolarDeviceInfo)
//    case disconnecting(PolarDeviceInfo)
    
    func get() -> PolarDeviceInfo {
        
        switch self {
        case .noDevice(let deviceInfo), .connected(let deviceInfo), .disconnected(let deviceInfo), .connecting(let deviceInfo)/*, .disconnecting(let deviceInfo)*/:
            return deviceInfo
        }
        
    }
}
