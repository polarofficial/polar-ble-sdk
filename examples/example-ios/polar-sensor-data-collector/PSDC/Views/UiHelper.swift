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
    case .temperature:
        return "TEMP"
    case .pressure:
        return "PRE"
    case .skinTemperature:
        return "SKINTEMP"
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
    case .temperature:
        return "Temperature"
    case .pressure:
        return "Pressure"
    case .skinTemperature:
        return "Skin temperature"
    }
}

func mapStringToDeviceDataType(_ key: String) -> PolarDeviceDataType? {
    switch key {
    case "ACC": return .acc
    case "ECG": return .ecg
    case "GYR": return .gyro
    case "HR": return .hr
    case "MAG": return .magnetometer
    case "PPG": return .ppg
    case "PPI": return .ppi
    case "PRESSURE": return .pressure
    case "SKINTEMP": return .skinTemperature
    case "TEMPERATURE": return .temperature

    default: return nil
    }
}
