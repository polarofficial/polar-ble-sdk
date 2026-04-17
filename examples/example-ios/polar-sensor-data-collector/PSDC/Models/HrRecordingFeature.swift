//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation

struct HrRecordingFeature {
    var hr: UInt8 = 0
    var rrs: [Int] = []
    var rrAvailable: Bool = false
    var contactStatus: Bool = false
    var contactStatusSupported: Bool = false
}
