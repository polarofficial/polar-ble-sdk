/// Copyright © 2024 Polar Electro Oy. All rights reserved.
///
import Foundation
import PolarBleSdk

enum ActivityRecordingDataLoadingState {
    case inProgress
    case success
    case failed(error: String)
}

struct ActivityRecordingData: Identifiable {
    let id = UUID()
    var loadingState = ActivityRecordingDataLoadingState.inProgress
    var startDate: Date = Date()
    var endDate: Date = Date()
    var activityType: PolarActivityDataType = PolarActivityDataType.NONE
    var caloriesType: CaloriesType = .activity
    var data:String = ""
}
