//  Copyright © 2026 Polar. All rights reserved.

import Foundation

/// Represents the possible outcomes when attempting to start
/// an offline exercise session on a supported Polar device.
public enum OfflineExerciseStartResultType {

    /// Exercise started successfully.
    case success

    /// An exercise is already ongoing on the device.
    case exerciseOngoing

    /// Device battery level is too low to start exercise.
    case lowBattery

    /// Device is currently in SDK mode and cannot start exercise.
    case sdkMode

    /// Provided sport profile is not recognized by the device.
    case unknownSport

    /// Any other unspecified result.
    case other
}

/// Contains the result information returned when starting
/// an offline exercise session.
///
/// All methods require the SDK feature
/// `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`
/// and the device must support the `dm_exercise` capability.
public struct OfflineExerciseStartResult {

    /// Result type returned by the device.
    public let result: OfflineExerciseStartResultType

    /// Directory path created for the exercise on the device.
    /// This path can later be used to list and fetch exercise files.
    public let directoryPath: String

    /// Creates a new start result container.
    ///
    /// - Parameters:
    ///   - result: The result type returned by the device.
    ///   - directoryPath: The directory path created for the exercise.
    public init(
        result: OfflineExerciseStartResultType,
        directoryPath: String
    ) {
        self.result = result
        self.directoryPath = directoryPath
    }
}

/// Offline Exercise V2 API.
///
/// Allows managing offline exercise sessions on supported Polar devices.
/// This API supports devices that use the Data Merge protocol for offline
/// exercise recording, enabling recording of exercise data even when the
/// device is not connected.
///
/// All methods require the SDK feature
/// `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`
/// and the device must have the `dm_exercise` capability.
///
/// All operations are asynchronous and use Swift async/await or `AsyncThrowingStream`.
public protocol PolarOfflineExerciseV2Api {

    /// Start an offline exercise session on the device.
    ///
    /// - Parameters:
    ///   - identifier: Polar device ID or BT address.
    ///   - sportProfile: The sport profile to use for the session.
    /// - Returns: `OfflineExerciseStartResult` containing the outcome and created directory path.
    /// - Throws: See `PolarErrors` for possible errors.
    func startOfflineExerciseV2(identifier: String, sportProfile: PolarExerciseSession.SportProfile) async throws -> OfflineExerciseStartResult

    /// Stop the ongoing offline exercise session on the device.
    ///
    /// - Parameter identifier: Polar device ID or BT address.
    /// - Throws: See `PolarErrors` for possible errors.
    func stopOfflineExerciseV2(identifier: String) async throws

    /// Check whether an offline exercise session is currently ongoing on the device.
    ///
    /// - Parameter identifier: Polar device ID or BT address.
    /// - Returns: `true` if an exercise session is ongoing, `false` otherwise.
    /// - Throws: See `PolarErrors` for possible errors.
    func getOfflineExerciseStatusV2(identifier: String) async throws -> Bool

    /// List offline exercise entries found under the given directory path on the device.
    ///
    /// - Parameters:
    ///   - identifier: Polar device ID or BT address.
    ///   - directoryPath: Root directory path to search. Use `"/"` to list all exercises.
    /// - Returns: `AsyncThrowingStream` emitting `PolarExerciseEntry` for each found exercise file.
    func listOfflineExercisesV2(identifier: String, directoryPath: String) -> AsyncThrowingStream<PolarExerciseEntry, Error>

    /// Fetch the data for a specific offline exercise entry.
    ///
    /// - Parameters:
    ///   - identifier: Polar device ID or BT address.
    ///   - entry: The `PolarExerciseEntry` referencing the exercise to fetch.
    /// - Returns: `PolarExerciseData` containing the recorded exercise data.
    /// - Throws: See `PolarErrors` for possible errors.
    func fetchOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry) async throws -> PolarExerciseData

    /// Remove a specific offline exercise entry from the device.
    ///
    /// - Parameters:
    ///   - identifier: Polar device ID or BT address.
    ///   - entry: The `PolarExerciseEntry` referencing the exercise to delete.
    /// - Throws: See `PolarErrors` for possible errors.
    func removeOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry) async throws

    /// Check whether the device supports the Offline Exercise V2 feature.
    ///
    /// - Parameter identifier: Polar device ID or BT address.
    /// - Returns: `true` if the device supports Offline Exercise V2, `false` otherwise.
    /// - Throws: See `PolarErrors` for possible errors.
    func isOfflineExerciseV2Supported(identifier: String) async throws -> Bool

    /// The filename used for exercise sample data files on the device.
    static var exerciseSamplesFile: String { get }
}

public extension PolarOfflineExerciseV2Api {
    static var exerciseSamplesFile: String { return "SAMPLES.BPB" }

    func startOfflineExerciseV2(identifier: String) async throws -> OfflineExerciseStartResult {
        return try await startOfflineExerciseV2(identifier: identifier, sportProfile: .otherOutdoor)
    }

    func listOfflineExercisesV2(identifier: String) -> AsyncThrowingStream<PolarExerciseEntry, Error> {
        return listOfflineExercisesV2(identifier: identifier, directoryPath: "/")
    }
}
