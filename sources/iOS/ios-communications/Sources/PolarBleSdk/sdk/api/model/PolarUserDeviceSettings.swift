//
//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation

public class PolarUserDeviceSettings {

    public enum DeviceLocation: String, Codable, CaseIterable, Identifiable {
        
        public var id: Self { self }
        
        case UNDEFINED
        case OTHER
        case WRIST_LEFT
        case WRIST_RIGHT
        case NECKLACE
        case CHEST
        case UPPER_BACK
        case FOOT_LEFT
        case FOOT_RIGHT
        case LOWER_ARM_LEFT
        case LOWER_ARM_RIGHT
        case UPPER_ARM_LEFT
        case UPPER_ARM_RIGHTs
        case BIKE_MOUNT

        public func toInt() -> Int {
            let allValues: NSArray = DeviceLocation.allCases as NSArray
            let result: Int = allValues.index(of: self)
            return result
        }
    }
    public var timestamp: Date = NSDate() as Date
    public var _deviceLocation: DeviceLocation = DeviceLocation.UNDEFINED
    
    public var deviceLocation: DeviceLocation {
        set (newValue) {
            _deviceLocation = newValue
        }
        get {
            return _deviceLocation
        }
    }
    
    public struct PolarUserDeviceSettingsResult: Codable {
        public var deviceLocation: DeviceLocation = .UNDEFINED
    }
    
    static func toProto(deviceUserLocation: (DeviceLocation)) -> Data_PbUserDeviceSettings {
        
        var proto = Data_PbUserDeviceSettings()
        var generalSettings = Data_PbUserDeviceGeneralSettings()
        generalSettings.deviceLocation = PbDeviceLocation.init(rawValue: deviceUserLocation.toInt())!
        proto.generalSettings = generalSettings
        proto.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date())
        return proto
    }
    
    static func fromProto(pBDeviceUserLocation: (Data_PbUserDeviceSettings)) -> PolarUserDeviceSettingsResult {
        var result = PolarUserDeviceSettingsResult()
        result.deviceLocation = PolarUserDeviceSettings.DeviceLocation.allCases[pBDeviceUserLocation.generalSettings.deviceLocation.rawValue]
        
        return result
    }
    
    public static func getStringValue(deviceLocationIndex: Int) -> String {
        return String(describing: DeviceLocation.allCases[deviceLocationIndex])
    }
    
    public static func getDeviceLocation(deviceLocation: String) -> DeviceLocation {
        
        for devicelocation in DeviceLocation.allCases {
            if (devicelocation.rawValue == deviceLocation) {
                return devicelocation
            }
        }
        
        return DeviceLocation.UNDEFINED
    }
    
    public static func getAllAsString() -> [String] {
        var items: [String] = []
        
        for item in PolarUserDeviceSettings.DeviceLocation.allCases {
            items.append(PolarUserDeviceSettings.getStringValue(deviceLocationIndex: item.toInt()))
        }
        return items
    }
}

public struct PolarUserDeviceSettingsData: Identifiable {
    
    public let id = UUID()
    public var _polarUserDeviceSettings: PolarUserDeviceSettings
    
    public init() {
        self._polarUserDeviceSettings = PolarUserDeviceSettings.init()
    }
    public var polarUserDeviceSettings: PolarUserDeviceSettings {
        get {
            return _polarUserDeviceSettings
        }
        set(newValue) {
            _polarUserDeviceSettings = newValue
        }
    }
}
