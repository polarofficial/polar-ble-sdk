// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Identifiers for individual watch face complications.
public enum PolarWatchFaceComplication: CaseIterable {
    case alarm
    case altitude
    case activity
    case battery
    case breathingExercise
    case calories
    case compass
    case countdownTimer
    case date
    case daylight
    case ecg
    case empty
    case flashlight
    case heartRate
    case jumpTest
    case latestTraining
    case navigation
    case nightlyRecharge
    case polarLogo
    case secondsAnalog
    case secondsDigital
    case spo2
    case timer
    case userName
    case weather
    case weeklySummary

    /// The string complication identifier used in the device KVS.
    public var complicationId: String {
        switch self {
        case .alarm:             return "alarm-complication"
        case .altitude:          return "altitude-complication"
        case .activity:          return "activity-percentage-complication"
        case .battery:           return "battery-complication"
        case .breathingExercise: return "serene-complication"
        case .calories:          return "calories-complication"
        case .compass:           return "compass-complication"
        case .countdownTimer:    return "countdownTimer-complication"
        case .date:              return "date-complication"
        case .daylight:          return "daylight-complication"
        case .ecg:               return "ecg-complication"
        case .empty:             return ""
        case .flashlight:        return "flashlight-complication"
        case .heartRate:         return "heart-rate-complication"
        case .jumpTest:          return "jump-test-complication"
        case .latestTraining:    return "latest-training-complication"
        case .navigation:        return "navigation-complication"
        case .nightlyRecharge:   return "nightly-recharge-complication"
        case .polarLogo:         return "polar-logo-complication"
        case .secondsAnalog:     return "analog-seconds-complication"
        case .secondsDigital:    return "digital-seconds-complication"
        case .spo2:              return "spo2-complication"
        case .timer:             return "timer-complication"
        case .userName:          return "user-name-complication"
        case .weather:           return "weather-complication"
        case .weeklySummary:     return "weeklysummary-complication"
        }
    }

    /// Integer key derived from `complicationId` matching the Android hash code.
    /// Uses Java's `String.hashCode()` algorithm (signed 32-bit polynomial).
    public var id: Int32 {
        var h: Int32 = 0
        for scalar in complicationId.unicodeScalars {
            h = h &* 31 &+ Int32(bitPattern: scalar.value)
        }
        return h
    }

    /// Resolve a complication by its integer id (Java `String.hashCode()`).
    public static func fromId(_ id: Int32) -> PolarWatchFaceComplication? {
        return allCases.first { $0.id == id }
    }
}

/// Watch face configuration containing the ordered list of enabled complications.
public struct PolarWatchFaceConfig {
    /// Ordered list of complications currently active on the watch face.
    public let enabledComplications: [PolarWatchFaceComplication]

    public init(enabledComplications: [PolarWatchFaceComplication]) {
        self.enabledComplications = enabledComplications
    }
}

