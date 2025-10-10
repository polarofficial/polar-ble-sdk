//  Copyright © 2025 Polar. All rights reserved.

import Foundation

class PolarOfflineRecordingUtils {

    static func mapOfflineRecordingFileNameToDeviceDataType(fileName: String) throws -> PolarDeviceDataType {
        let fileNameWithoutExtension = fileName.components(separatedBy: ".").first!
        switch fileNameWithoutExtension.replacingOccurrences(of: "\\d+", with: "", options: .regularExpression) {
        case "ACC": return .acc
        case "GYRO": return .gyro
        case "MAGNETOMETER" : return .magnetometer
        case "PPG": return .ppg
        case "PPI": return .ppi
        case "HR": return .hr
        case "TEMP": return .temperature
        case "SKINTEMP": return .skinTemperature
        default: throw BleGattException.gattDataError(description: "Unknown Polar Device Data type: \(fileName)")
        }
    }
}
