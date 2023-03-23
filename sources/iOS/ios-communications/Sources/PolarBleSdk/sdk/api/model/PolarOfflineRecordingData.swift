//  Copyright © 2023 Polar. All rights reserved.

import Foundation

public enum PolarOfflineRecordingData  {
    case accOfflineRecordingData(PolarAccData, startTime:Date, settings:PolarSensorSetting)
    case gyroOfflineRecordingData(PolarGyroData, startTime:Date, settings:PolarSensorSetting)
    case magOfflineRecordingData(PolarMagnetometerData, startTime:Date, settings:PolarSensorSetting)
    case ppgOfflineRecordingData(PolarPpgData, startTime:Date, settings:PolarSensorSetting)
    case ppiOfflineRecordingData(PolarPpiData, startTime:Date)
    case hrOfflineRecordingData(PolarHrData, startTime:Date)
}
