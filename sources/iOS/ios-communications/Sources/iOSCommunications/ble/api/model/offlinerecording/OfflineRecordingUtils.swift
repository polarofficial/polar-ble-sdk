//  Copyright © 2023 Polar. All rights reserved.
import Foundation

class OfflineRecordingUtils {
        
    static func mapOfflineRecordingFileNameToMeasurementType(fileName: String) throws -> PmdMeasurementType {
        switch(fileName) {
        case "ACC.REC": return PmdMeasurementType.acc
        case "GYRO.REC": return PmdMeasurementType.gyro
        case "MAG.REC": return PmdMeasurementType.mgn
        case "PPG.REC": return PmdMeasurementType.ppg
        case "PPI.REC": return PmdMeasurementType.ppi
        case "HR.REC": return PmdMeasurementType.offline_hr
        default: throw BleGattException.gattDataError(description: "Unknown offline file \(fileName)")
        }
    }
}
