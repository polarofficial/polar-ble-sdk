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
        case UPPER_ARM_RIGHT
        case BIKE_MOUNT

        public func toInt() -> Int {
            let allValues: NSArray = DeviceLocation.allCases as NSArray
            let result: Int = allValues.index(of: self)
            return result
        }
    }

    public enum UsbConnectionMode: String, Codable {
        case ON
        case OFF

        func toProto() -> Data_PbUsbConnectionSettings.PbUsbConnectionMode {
            switch self {
            case .ON:
                return Data_PbUsbConnectionSettings.PbUsbConnectionMode.on
            case .OFF:
                return Data_PbUsbConnectionSettings.PbUsbConnectionMode.off
            }
        }

        static func fromProto(proto: Data_PbUsbConnectionSettings.PbUsbConnectionMode) -> UsbConnectionMode? {
            switch proto {
            case Data_PbUsbConnectionSettings.PbUsbConnectionMode.on:
                return .ON
            case Data_PbUsbConnectionSettings.PbUsbConnectionMode.off:
                return .OFF
            case Data_PbUsbConnectionSettings.PbUsbConnectionMode.unknown:
                return nil
            }
        }
    }

    public var timestamp: Date = NSDate() as Date
    public var _deviceLocation: DeviceLocation = DeviceLocation.UNDEFINED
    public var usbConnectionMode: UsbConnectionMode? = nil
    
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
        public var usbConnectionMode: UsbConnectionMode? = nil
    }

    static func toProto(userDeviceSettings: PolarUserDeviceSettings) -> Data_PbUserDeviceSettings {

        var proto = Data_PbUserDeviceSettings()
        var generalSettings = Data_PbUserDeviceGeneralSettings()
        generalSettings.deviceLocation = PbDeviceLocation.init(rawValue: userDeviceSettings.deviceLocation.toInt())!
        proto.generalSettings = generalSettings
        proto.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date())

        if let usbConnectionMode = userDeviceSettings.usbConnectionMode {
            var usbConnectionSettings = Data_PbUsbConnectionSettings()
            usbConnectionSettings.mode = usbConnectionMode.toProto()
            proto.usbConnectionSettings = usbConnectionSettings
        }

        return proto
    }

    static func fromProto(pbUserDeviceSettings: Data_PbUserDeviceSettings) -> PolarUserDeviceSettingsResult {
        var result = PolarUserDeviceSettingsResult()
        result.deviceLocation = PolarUserDeviceSettings.DeviceLocation.allCases[pbUserDeviceSettings.generalSettings.deviceLocation.rawValue]
        
        if pbUserDeviceSettings.hasUsbConnectionSettings {
            result.usbConnectionMode = UsbConnectionMode.fromProto(proto: pbUserDeviceSettings.usbConnectionSettings.mode)
        }
        
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
