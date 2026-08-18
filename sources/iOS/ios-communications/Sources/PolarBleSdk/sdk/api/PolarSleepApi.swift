///  Copyright © 2024 Polar. All rights reserved.

import Foundation

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
    func getSleepRecordingState(identifier: String, timeoutMs: UInt64) async throws -> Bool
    
    /// - Returns: Publisher stream of Bool values indicating if sleep recording is ongoing
    func observeSleepRecordingState(identifier: String) -> AsyncThrowingStream<[Bool], Error>
    
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
    func getSleepRecordingState(identifier: String) async throws -> Bool {
        try await getSleepRecordingState(identifier: identifier, timeoutMs: 30_000)
    }

    @available(*, deprecated, renamed: "getSleep(identifier:fromDate:toDate:)")
    func getSleepData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSleepData.PolarSleepAnalysisResult] {
        return try await getSleep(identifier: identifier, fromDate: fromDate, toDate: toDate)
    }
}
