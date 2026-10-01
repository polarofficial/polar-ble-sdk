///  Copyright © 2024 Polar. All rights reserved.

import Foundation

/// Sleep recording status reported by the device.
public enum PolarSleepRecordingStatus {
    /// Sleep recording is on.
    case enabled
    /// Sleep recording is off.
    case disabled
    /// The device did not report the state.
    case unknown
}

/// Protocol defining methods to get Polar Sleep Data
public protocol PolarSleepApi {
    /// Get sleep recording state
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address
    ///   - timeoutMs: Timeout in milliseconds while waiting for the first sleep recording state event
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_sleep_data`
    /// - Returns: AnyPublisher emitting a single Bool value indicating if sleep recording is ongoing
    /// - Throws: ``PolarErrors/timeout(description:)`` if timeout is exceeded before state is received.
    @available(*, deprecated, message: "Use getSleepRecordingStatus(identifier:timeoutMs:), which reports unknown state instead of off")
    func getSleepRecordingState(identifier: String, timeoutMs: UInt64) async throws -> Bool
    
    /// - Returns: Publisher stream of Bool values indicating if sleep recording is ongoing
    @available(*, deprecated, message: "Use observeSleepRecordingStatus(identifier:), which reports unknown state instead of off")
    func observeSleepRecordingState(identifier: String) -> AsyncThrowingStream<[Bool], Error>

    /// Get sleep recording status.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address
    ///   - timeoutMs: Timeout in milliseconds while waiting for the first sleep recording status event
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_sleep_data`
    /// - Returns: The sleep recording status. `.unknown` means the device did not report the state.
    /// - Throws: ``PolarErrors/timeout(description:)`` if timeout is exceeded before a status is received.
    func getSleepRecordingStatus(identifier: String, timeoutMs: UInt64) async throws -> PolarSleepRecordingStatus

    /// Observe sleep recording status.
    /// - Returns: Stream of sleep recording status batches. `.unknown` means the device did not report the state.
    func observeSleepRecordingStatus(identifier: String) -> AsyncThrowingStream<[PolarSleepRecordingStatus], Error>

    /// - Returns: Publisher stream
    func stopSleepRecording(identifier: String) async throws
    
    /// - Returns: Publisher emitting an array of `PolarSleepData` for the specified period.
    func getSleep(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSleepData.PolarSleepAnalysisResult]

    /// - Deprecated: Use ``getSleep(identifier:fromDate:toDate:)`` instead.
    @available(*, deprecated, renamed: "getSleep(identifier:fromDate:toDate:)")
    func getSleepData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSleepData.PolarSleepAnalysisResult]
}

public extension PolarSleepApi {
    /// Get sleep recording state with default timeout of 30 seconds.
    @available(*, deprecated, message: "Use getSleepRecordingStatus(identifier:), which reports unknown state instead of off")
    func getSleepRecordingState(identifier: String) async throws -> Bool {
        try await getSleepRecordingStatus(identifier: identifier) == .enabled
    }

    /// Get sleep recording status with default timeout of 30 seconds.
    func getSleepRecordingStatus(identifier: String) async throws -> PolarSleepRecordingStatus {
        try await getSleepRecordingStatus(identifier: identifier, timeoutMs: 30_000)
    }

    @available(*, deprecated, renamed: "getSleep(identifier:fromDate:toDate:)")
    func getSleepData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSleepData.PolarSleepAnalysisResult] {
        return try await getSleep(identifier: identifier, fromDate: fromDate, toDate: toDate)
    }
}
