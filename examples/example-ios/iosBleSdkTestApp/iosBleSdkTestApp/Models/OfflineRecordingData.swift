/// Copyright © 2023 Polar Electro Oy. All rights reserved.
///
import Foundation
import PolarBleSdk

struct OfflineRecordingData: Identifiable {
    let id = UUID()
    var isFetching: Bool = false
    var startTime: Date = Date()
    var usedSettings: PolarSensorSetting? = nil
    var downLoadTime: TimeInterval? = nil
    var dataSize: UInt = 0
    var downloadSpeed: Double {
        if let time = downLoadTime, dataSize > 0 {
            return  Double(dataSize) / time
        } else {
            return 0.0
        }
    }
    var data:String = ""
}
