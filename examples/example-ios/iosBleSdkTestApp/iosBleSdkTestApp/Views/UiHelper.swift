/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

func getStreamingFeatureString(_ feature: DeviceStreamingFeature) -> String {
    switch feature {
    case .ecg:
        return "ECG"
    case .acc:
        return "ACC"
    case .ppg:
        return "PPG"
    case .ppi:
        return "PPI"
    case .gyro:
        return "GYR"
    case .magnetometer:
        return "MAG"
    @unknown default:
        fatalError()
    }
}
