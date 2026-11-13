///  Copyright © 2025 Polar. All rights reserved.

import Foundation

/// Protocol defining methods to access training session data.
public protocol PolarTrainingSessionApi {
    /// Get training session references for a given period. If fromDate and toDate are not given this
    /// method will return all training session references in the device.
    /// Otherwise returns list of training session references from the specified closed or
    /// half-open date interval.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - fromDate: The starting date of the period to retrieve training session references from. Optional.
    ///   - toDate: The ending date of the period to retrieve training session references from. Optional.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Returns: An array of `PolarTrainingSessionReference` objects for the specified period.
    /// - Throws: See `PolarErrors` for possible errors.
    func getTrainingSessionReferences(
        identifier: String,
        fromDate: Date?,
        toDate: Date?
    ) async throws -> [PolarTrainingSessionReference]

    /// Get a specific training session using a reference.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - trainingSessionReference: The reference to the training session to retrieve.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Returns: `PolarTrainingSession` containing the session data.
    /// - Throws: See `PolarErrors` for possible errors.
    func getTrainingSession(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ) async throws -> PolarTrainingSession

    /// Get a specific training session using a reference with progress updates.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - trainingSessionReference: The reference to the training session to retrieve.
    ///   - progressHandler: A closure called with `PolarTrainingSessionProgress` as the session data is being fetched.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Returns: `PolarTrainingSession` containing the complete session data.
    /// - Throws: See `PolarErrors` for possible errors.
    func getTrainingSessionWithProgress(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference,
        progressHandler: @escaping (PolarTrainingSessionProgress) -> Void
    ) async throws -> PolarTrainingSession

    /// Api for removing single training session from a Polar device. You can get a list of training sessions with `getTrainingSessionReferences` API.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - reference: PolarTrainingSessionReference with path in device to the training session to be removed.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Throws: See `PolarErrors` for possible errors invoked.
    func deleteTrainingSession(identifier: String, reference: PolarTrainingSessionReference) async throws

    /// Start an exercise session on the device.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - profile: Sport profile to use for the exercise session.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Throws: `PolarErrors.invalidArgument` if `profile` is `.unknown`, or an error if the
    ///   operation fails or the device does not support the requested sport.
    func startExercise(identifier: String, profile: PolarExerciseSession.SportProfile) async throws

    /// Start an exercise using a numeric Polar sport ecosystem identifier, including sports
    /// not listed in `PolarExerciseSession.SportProfile`.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - sportId: Positive sport identifier no greater than `Int64.max`.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Throws: `PolarErrors.invalidArgument` if `sportId` is zero or greater than `Int64.max`;
    ///   or an error if the device rejects the sport or the operation fails.
    func startExercise(identifier: String, sportId: UInt64) async throws

    /// Pause an ongoing exercise session.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Throws: See `PolarErrors` for possible errors invoked.
    func pauseExercise(identifier: String) async throws

    /// Resume a paused exercise session.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Throws: See `PolarErrors` for possible errors invoked.
    func resumeExercise(identifier: String) async throws

    /// Stop the current exercise session.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - save: Whether to save the stopped exercise on the device. Defaults to `true`.
    ///           Pass `false` to discard the session without persisting it, which avoids the
    ///           need for a subsequent stop → list → delete sequence.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Throws: See `PolarErrors` for possible errors invoked.
    func stopExercise(identifier: String, save: Bool) async throws

    /// Get the current exercise session status from the device.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Returns: The current `PolarExerciseSession.ExerciseInfo` for the device.
    /// - Throws: See `PolarErrors` for possible errors invoked.
    func getExerciseStatus(identifier: String) async throws -> PolarExerciseSession.ExerciseInfo

    /// Observe exercise session status notifications from the device.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Returns: `AsyncThrowingStream` emitting `PolarExerciseSession.ExerciseInfo` whenever the session status changes.
    func observeExerciseStatus(identifier: String) -> AsyncThrowingStream<PolarExerciseSession.ExerciseInfo, Error>
}

public extension PolarTrainingSessionApi {
    /// Start an exercise with the default sport, `.otherOutdoor`.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Throws: See `PolarErrors` for possible errors.
    func startExercise(identifier: String) async throws {
        try await startExercise(identifier: identifier, profile: .otherOutdoor)
    }

    /// Compatibility implementation for conformers that do not implement numeric sport IDs.
    /// Only known `SportProfile` values are supported by this default; SDK implementations may
    /// override it to support additional installed sport IDs.
    func startExercise(identifier: String, sportId: UInt64) async throws {
        guard sportId > 0,
              sportId <= UInt64(Int64.max),
              let rawValue = Int(exactly: sportId),
              let profile = PolarExerciseSession.SportProfile(rawValue: rawValue),
              profile != .unknown else {
            throw PolarErrors.invalidArgument(description: "This API implementation does not support sport ID \(sportId)")
        }
        try await startExercise(identifier: identifier, profile: profile)
    }

    /// Convenience overload that saves the exercise on the device (default behaviour).
    func stopExercise(identifier: String) async throws {
        try await stopExercise(identifier: identifier, save: true)
    }
}

