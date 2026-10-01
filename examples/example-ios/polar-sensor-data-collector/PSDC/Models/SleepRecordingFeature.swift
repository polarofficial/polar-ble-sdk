///  Copyright © 2025 Polar. All rights reserved.

import Foundation
import PolarBleSdk

struct SleepRecordingFeature {
    var isSupported: Bool = false
    // nil means the status has not been fetched yet / control not available.
    var status: PolarSleepRecordingStatus? = nil
}
