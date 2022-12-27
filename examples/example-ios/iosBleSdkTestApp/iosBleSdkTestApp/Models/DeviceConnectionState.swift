/// Copyright © 2023 Polar Electro Oy. All rights reserved.

import Foundation

enum DeviceConnectionState {
    case disconnected
    case connecting(String)
    case connected(String)
}
