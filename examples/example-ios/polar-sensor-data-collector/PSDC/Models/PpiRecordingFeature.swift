//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation

struct PpiRecordingFeature {
    var ppInMs: UInt16 = 0
    var blockerBit: Int = 0
    var ppErrorEstimate: UInt16 = 0
    var timeStamp: UInt64 = 0
}
