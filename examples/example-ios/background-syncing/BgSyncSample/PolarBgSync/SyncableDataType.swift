// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Data types that can be synced from a Polar device.
/// Maps to the various data retrieval APIs in the Polar BLE SDK.
public enum SyncableDataType: String, CaseIterable, Codable {
    case steps = "STEPS"
    case distance = "DISTANCE"
    case activeTime = "ACTIVE_TIME"
    case calories = "CALORIES"
    case hrSamples247 = "HR_SAMPLES_247"
    case ppiSamples247 = "PPI_SAMPLES_247"
    case nightlyRecharge = "NIGHTLY_RECHARGE"
    case activitySamples = "ACTIVITY_SAMPLES"
    case dailySummary = "DAILY_SUMMARY"
    case sleep = "SLEEP"
    case trainingSessions = "TRAINING_SESSIONS"
    case skinTemperature = "SKIN_TEMPERATURE"
    case offlineAcc = "OFFLINE_ACC"
    case offlineDerivedAcc = "OFFLINE_DERIVED_ACC"
    case offlineEcg = "OFFLINE_ECG"
    case offlineGyro = "OFFLINE_GYRO"
    case offlineMagnetometer = "OFFLINE_MAGNETOMETER"
    case offlinePpg = "OFFLINE_PPG"
    case offlinePpi = "OFFLINE_PPI"
    case offlineHr = "OFFLINE_HR"
    case offlineTemperature = "OFFLINE_TEMPERATURE"
    case offlineSkinTemperature = "OFFLINE_SKIN_TEMPERATURE"

    /// Whether this type represents an offline recording (stored as binary data).
    public var isOfflineRecording: Bool {
        return rawValue.hasPrefix("OFFLINE_")
    }
}
