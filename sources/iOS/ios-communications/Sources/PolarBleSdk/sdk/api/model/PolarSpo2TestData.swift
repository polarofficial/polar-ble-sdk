/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Model for SPO2 test data retrieved from a Polar device.
public struct PolarSpo2TestData: Encodable {

    /// SpO2 classification
    public enum Spo2Class: Int, Encodable {
        case unknown = 0
        case veryLow = 1
        case low = 2
        case normal = 3
    }

    /// Trigger type that initiated the SPO2 test
    public enum Spo2TestTriggerType: Int, Encodable {
        case manual = 0
        case automatic = 1
    }

    /// SPO2 test completion status
    public enum Spo2TestStatus: Int, Encodable {
        case passed = 0
        case inconclusiveTooLowQualityInSamples = 1
        case inconclusiveTooLowOverallQuality = 2
        case inconclusiveTooManyMissingSamples = 3
    }

    /// Deviation of a measurement from the user's baseline
    public enum DeviationFromBaseline: Int, Encodable {
        case noBaseline = 0
        case belowUsual = 1
        case usual = 2
        case aboveUsual = 3
    }

    // MARK: - Properties

    /// Name / model of the recording device
    public let recordingDevice: String?

    /// Date when the test was performed
    public let date: Date

    /// Timezone offset from UTC in minutes at the time of the test
    public let timeZoneOffsetMinutes: Int?

    /// Status of the SPO2 test
    public let testStatus: Spo2TestStatus?

    /// Blood oxygen saturation percentage
    public let bloodOxygenPercent: Int?

    /// SpO2 classification result
    public let spo2Class: Spo2Class?

    /// SpO2 value deviation from the user's baseline
    public let spo2ValueDeviationFromBaseline: DeviationFromBaseline?

    /// Average SpO2 signal quality percentage during the test
    public let spo2QualityAveragePercent: Float?

    /// Average heart rate in BPM during the test
    public let averageHeartRateBpm: UInt?

    /// Heart rate variability in milliseconds during the test
    public let heartRateVariabilityMs: Float?

    /// SpO2 HRV deviation from the user's baseline
    public let spo2HrvDeviationFromBaseline: DeviationFromBaseline?

    /// Altitude in meters at the time of the test
    public let altitudeMeters: Float?

    /// What triggered the test
    public let triggerType: Spo2TestTriggerType?

    public init(
        recordingDevice: String? = nil,
        date: Date,
        timeZoneOffsetMinutes: Int? = nil,
        testStatus: Spo2TestStatus? = nil,
        bloodOxygenPercent: Int? = nil,
        spo2Class: Spo2Class? = nil,
        spo2ValueDeviationFromBaseline: DeviationFromBaseline? = nil,
        spo2QualityAveragePercent: Float? = nil,
        averageHeartRateBpm: UInt? = nil,
        heartRateVariabilityMs: Float? = nil,
        spo2HrvDeviationFromBaseline: DeviationFromBaseline? = nil,
        altitudeMeters: Float? = nil,
        triggerType: Spo2TestTriggerType? = nil
    ) {
        self.recordingDevice = recordingDevice
        self.date = date
        self.timeZoneOffsetMinutes = timeZoneOffsetMinutes
        self.testStatus = testStatus
        self.bloodOxygenPercent = bloodOxygenPercent
        self.spo2Class = spo2Class
        self.spo2ValueDeviationFromBaseline = spo2ValueDeviationFromBaseline
        self.spo2QualityAveragePercent = spo2QualityAveragePercent
        self.averageHeartRateBpm = averageHeartRateBpm
        self.heartRateVariabilityMs = heartRateVariabilityMs
        self.spo2HrvDeviationFromBaseline = spo2HrvDeviationFromBaseline
        self.altitudeMeters = altitudeMeters
        self.triggerType = triggerType
    }
}

