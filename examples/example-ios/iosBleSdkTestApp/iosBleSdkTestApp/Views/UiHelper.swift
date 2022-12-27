/// Copyright © 2021 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

func getShortNameForDataType(_ feature: PolarDeviceDataType) -> String {
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
    case .hr:
        return "HR"
    }
}

func getLongNameForDataType(_ feature: PolarDeviceDataType) -> String {
    switch feature {
    case .ecg:
        return "Electrocardiogram (ECG)"
    case .acc:
        return "Accelerometer"
    case .ppg:
        return "Photoplethysmography (PPG)"
    case .ppi:
        return "Peak Interval (PPI)"
    case .gyro:
        return "Gyroscope"
    case .magnetometer:
        return "Magnetometer"
    case .hr:
        return "Heart rate"
    }
}
