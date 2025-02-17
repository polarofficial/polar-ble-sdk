//
//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation

public class PolarStoredDataType {
    
    public enum StoredDataType: String, CaseIterable, Codable {
        
        case UNDEFINED = "UNDEFINED"
        case ACTIVITY = "ACT"
        case AUTO_SAMPLE = "AUTOS"
        case DAILY_SUMMARY = "DSUM"
        case NIGHTLY_RECOVERY = "NR"
        case SDLOGS = "SDLOGS"
        case SLEEP = "SLEEP"
        case SLEEP_SCORE = "SLEEPSCO"
        case SKIN_CONTACT_CHANGES = "SKINCONT"
        case SKINTEMP = "SKINTEMP"
        
        public func toInt() -> Int {
            let allValues: NSArray = StoredDataType.allCases as NSArray
            let result: Int = allValues.index(of: self)
            return result
        }
    }
    
    public var _storedDataType: StoredDataType = StoredDataType.UNDEFINED
    public var storedDataType: StoredDataType {
        set (newValue) {
            _storedDataType = newValue
        }
        get {
            return _storedDataType
        }
    }
    
    public struct PolarStoredDataTypeResult: Codable {
        public var storedDataType: StoredDataType!
    }
    
    public static func getStringValue(dataTypeLocationIndex: Int) -> String {
        return String(describing: StoredDataType.allCases[dataTypeLocationIndex])
    }
    
    public static func getAllAsString() -> [String] {
        var items: [String] = []
        
        for item in PolarStoredDataType.StoredDataType.allCases {
            items.append(PolarStoredDataType.getStringValue(dataTypeLocationIndex: item.toInt()))
        }
        return items
    }
    
    public static func getValue(name: String) -> StoredDataType {
        
        for value in StoredDataType.allCases {
            if (name.compare("\(value)") == .orderedSame) {
                return value
            }
        }
        
        return .UNDEFINED
    }
}
