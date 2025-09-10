/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation

/// polar sensor settings class
public struct PolarSensorSetting {
    
    /// settings type
    public enum SettingType: Int {
        /// sample rate in hz
        case sampleRate = 0
        /// resolution in bits
        case resolution = 1
        /// range
        case range = 2
        /// range with min and max allowed values
        case rangeMilliunit = 3
        /// amount of channels available
        case channels = 4
        /// type is unknown
        case unknown = 0xff
    }
    
    /// current settings available / set
    public let settings: [SettingType : Set<UInt32>]
    
    init() {
        self.settings = [SettingType : Set<UInt32>]()
    }
    
    /// Constructor with validation that all values are > 0
    ///
    /// - Parameter settings: single key value pairs to start stream
    /// - Throws: PolarErrors.invalidSensorSettingValue if any value is <= 0
    public init(_ settings: [SettingType : UInt32]) throws {
        for (key, value) in settings {
            if value == 0 {
                throw PolarErrors.invalidSensorSettingValue(setting: key, value: value)
            }
        }
        self.settings = settings.mapValues { Set([$0]) }
    }
    
    init(_ settings: [SettingType : Set<UInt32>]) {
        self.settings = settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[SettingType(rawValue: Int(key.rawValue)) ?? SettingType.unknown]=value
        }
    }
    
    func map2PmdSetting() -> PmdSetting {
        return PmdSetting(settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[PmdSetting.PmdSettingType(rawValue: UInt8(key.rawValue)) ?? PmdSetting.PmdSettingType.unknown]=value.first!
        })
    }
    
    /// helper to retrieve max settings available
    ///
    /// - Returns: PolarSensorSetting with max settings
    public func maxSettings() -> PolarSensorSetting {
        let selected = settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[key] = value.max() ?? 0
        } as [SettingType : UInt32]
        return try! PolarSensorSetting(selected)
    }
}

extension PolarSensorSetting: CustomStringConvertible {
    public var description: String {
        var descriptionString: String = ""
        for setting in settings {
            descriptionString.append(contentsOf: "(\(setting.key):\(setting.value) )")
        }
        return descriptionString
    }
}
