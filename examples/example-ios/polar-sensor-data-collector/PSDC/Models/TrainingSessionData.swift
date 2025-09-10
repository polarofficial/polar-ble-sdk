// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

enum TrainingSessionDataLoadingState {
    case inProgress
    case success
    case failed(error: String)
}

struct TrainingSessionData: Identifiable {
    let id = UUID()
    var loadState: TrainingSessionDataLoadingState = TrainingSessionDataLoadingState.inProgress
    var startTime: Date = Date()
    var data: String = ""
}
