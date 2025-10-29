//  Copyright © 2023 Polar. All rights reserved.

import Foundation

public enum PolarOfflineRecordingData  {
    case accOfflineRecordingData(PolarAccData, startTime:Date, settings:PolarSensorSetting)
    case gyroOfflineRecordingData(PolarGyroData, startTime:Date, settings:PolarSensorSetting)
    case magOfflineRecordingData(PolarMagnetometerData, startTime:Date, settings:PolarSensorSetting)
    case ppgOfflineRecordingData(PolarPpgData, startTime:Date, settings:PolarSensorSetting)
    case ppiOfflineRecordingData(PolarPpiData, startTime:Date)
    case hrOfflineRecordingData(PolarHrData, startTime:Date)
    case temperatureOfflineRecordingData(PolarTemperatureData, startTime:Date)
    case skinTemperatureOfflineRecordingData(PolarTemperatureData, startTime:Date)
    case emptyData(startTime:Date)
}

public struct PolarOfflineRecordingProgress {
    public let bytesDownloaded: Int64
    public let totalBytes: Int64
    public let progressPercent: Int
    
    public init(bytesDownloaded: Int64, totalBytes: Int64, progressPercent: Int) {
        self.bytesDownloaded = bytesDownloaded
        self.totalBytes = totalBytes
        self.progressPercent = progressPercent
    }
}

public enum PolarOfflineRecordingResult {
    case progress(PolarOfflineRecordingProgress)
    case complete(PolarOfflineRecordingData)
}
