//  Copyright © 2022 Polar. All rights reserved.

import Foundation

public enum PmdRecordingType: UInt8, CaseIterable, Sendable {
    case online = 0
    case offline = 1
    
    func asBitField() -> UInt8 {
        return self.rawValue << 7
    }
}
