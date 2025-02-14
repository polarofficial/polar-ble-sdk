//  Copyright © 2023 Polar. All rights reserved.
import Foundation

class OfflineRecordingUtils {
        
    static func mapOfflineRecordingFileNameToMeasurementType(fileName: String) throws -> PmdMeasurementType {
        let fileNameWithoutExtension = fileName.components(separatedBy: ".").first!
        switch fileNameWithoutExtension.replacingOccurrences(of: "\\d+", with: "", options: .regularExpression) {
            case "ACC": return .acc
            case "GYRO": return .gyro
            case "MAG": return .mgn
            case "PPG": return .ppg
            case "PPI": return .ppi
            case "HR": return .offline_hr
            case "TEMP": return .temperature
            case "SKINTEMP": return .skinTemperature
            default: throw BleGattException.gattDataError(description: "Unknown offline file \(fileName)")
        }
    }
}
