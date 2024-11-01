/// Copyright © 2024 Polar Electro Oy. All rights reserved.

import Foundation

public struct AutomaticSamples {
    
    public var day: Date? = nil
    
    public init(day: Date?) {
        self.day = day
    }
    
    static func fromProto(proto: Data_PbAutomaticSampleSessions) -> AutomaticSamples {
        
        var date: Date?
        
        do {
            date = proto.hasDay ? try PolarTimeUtils.pbDateToUTCDate(pbDate: proto.day) : nil
            return AutomaticSamples(day: date)
        } catch let err {
            return AutomaticSamples(day: nil)
        }
    }
}
