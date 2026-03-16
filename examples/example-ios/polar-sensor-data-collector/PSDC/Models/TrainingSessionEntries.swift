// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

struct TrainingSessionEntries: Identifiable {
    let id = UUID()
    var isSupported: Bool = false
    var isFetching: Bool = true
    var entries = [PolarTrainingSessionReference]()
}
