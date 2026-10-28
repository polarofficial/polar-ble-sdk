// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.api

/**
 * Data types that can be synced from a Polar device.
 * Maps to the various data retrieval APIs in the Polar BLE SDK.
 */
enum class SyncableDataType {
    /** Daily step counts */
    STEPS,
    /** Daily distance in meters */
    DISTANCE,
    /** Daily active time breakdown */
    ACTIVE_TIME,
    /** Daily calories (activity + training + BMR) */
    CALORIES,
    /** 24/7 heart rate samples (5-min averages and per-minute during high activity) */
    HR_SAMPLES_247,
    /** 24/7 pulse-to-pulse interval samples */
    PPI_SAMPLES_247,
    /** Nightly recharge / recovery data */
    NIGHTLY_RECHARGE,
    /** Per-minute activity samples (step count + MET) */
    ACTIVITY_SAMPLES,
    /** Daily summary aggregations */
    DAILY_SUMMARY,
    /** Sleep analysis results */
    SLEEP,
    /** Training sessions (exercise data with route and samples) */
    TRAINING_SESSIONS,
    /** 24/7 skin temperature samples */
    SKIN_TEMPERATURE,
    /** Offline accelerometer recording */
    OFFLINE_ACC,
    /** Offline derived accelerometer recording */
    OFFLINE_DERIVED_ACC,
    /** Offline ECG recording */
    OFFLINE_ECG,
    /** Offline gyroscope recording */
    OFFLINE_GYRO,
    /** Offline magnetometer recording */
    OFFLINE_MAGNETOMETER,
    /** Offline PPG recording */
    OFFLINE_PPG,
    /** Offline PPI recording */
    OFFLINE_PPI,
    /** Offline HR recording */
    OFFLINE_HR,
    /** Offline temperature recording */
    OFFLINE_TEMPERATURE,
    /** Offline skin temperature recording */
    OFFLINE_SKIN_TEMPERATURE;

    /** Whether this type represents an offline recording (stored as binary data). */
    val isOfflineRecording: Boolean
        get() = name.startsWith("OFFLINE_")
}
