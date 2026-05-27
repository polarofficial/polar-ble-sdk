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
    ///   - profile: The sport profile to use for the exercise session.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Throws: See `PolarErrors` for possible errors invoked.
    func startExercise(identifier: String, profile: PolarExerciseSession.SportProfile) async throws

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
    /// By default, the session is saved on the device.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_training_data`
    /// - Throws: See `PolarErrors` for possible errors invoked.
    func stopExercise(identifier: String) async throws

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
