// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

enum TrainingSessionDataLoadingState {
    case notStarted
    case inProgress
    case success
    case failed(error: String)
}

struct TrainingSessionData: Identifiable {
    let id = UUID()
    var loadState: TrainingSessionDataLoadingState = .notStarted
    var startTime: Date = Date()
    var endTime: Date = Date()
    var data: String = ""
    var progress: PolarTrainingSessionProgress? = nil
}
