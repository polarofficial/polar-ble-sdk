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
        /// range in g's
        case range = 2
        /// type is unknown
        case unknown = 0xff
    }
    
    /// current settings available / set
    public let settings: [SettingType : Set<UInt16>]

    init() {
        self.settings = [SettingType : Set<UInt16>]()
    }
    
    /// constructor with desired settings
    ///
    /// - Parameter settings: single key value pairs to start stream
    public init(_ settings: [SettingType : UInt16]) {
        self.settings = settings.mapValues({ (v) -> Set<UInt16> in
            var set = Set<UInt16>()
            set.insert(v)
            return set
        })
    }
    
    init(_ settings: [Pmd.PmdSetting.PmdSettingType : Set<UInt16>]) {
        self.settings = settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[SettingType.init(rawValue: Int(key.rawValue)) ?? SettingType.unknown]=value
        }
    }
    
    func map2PmdSetting() -> Pmd.PmdSetting {
        return Pmd.PmdSetting(settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[Pmd.PmdSetting.PmdSettingType.init(rawValue: UInt8(key.rawValue)) ?? Pmd.PmdSetting.PmdSettingType.unknown]=Set(value)
        })
    }
    
    /// helper to retreive max settings available
    ///
    /// - Returns: PolarSensorSetting with max settings
    public func maxSettings() -> PolarSensorSetting {
        let selected = settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[key] = value.max() ?? 0
        } as [SettingType : UInt16]
        return PolarSensorSetting(selected)
    }
}



