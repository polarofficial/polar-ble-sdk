/// Copyright © 2023 Polar Electro Oy. All rights reserved.
///
import Foundation
import PolarBleSdk

enum OfflineRecordingDataLoadingState {
    case notStarted
    case inProgress
    case success
    case failed(error: String)
}

struct OfflineRecordingData: Identifiable {
    let id = UUID()
    var loadState: OfflineRecordingDataLoadingState = .notStarted
    var startTime: Date = Date()
    var usedSettings: PolarSensorSetting? = nil
    var downLoadTime: TimeInterval? = nil
    var dataSize: UInt = 0
    var downloadSpeed: Double {
        if let time = downLoadTime, dataSize > 0 {
            return Double(dataSize) / time
        } else {
            return 0.0
        }
    }
    var data: String = ""
    var progress: PolarOfflineRecordingProgress? = nil
    var offlineRecordingData: PolarOfflineRecordingData? = nil
}
