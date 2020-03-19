
import Foundation

open class TimeUtility {
    
    public static func currentTime() -> TimeInterval {
        return Date().timeIntervalSince1970
    }
    
    public static func timeDeltaSeconds(_ from: TimeInterval) -> TimeInterval {
        return currentTime() - from
    }
    
    public static func timeDeltaSeconds(_ from: Date) -> TimeInterval {
        return currentTime() - from.timeIntervalSince1970
    }
}
