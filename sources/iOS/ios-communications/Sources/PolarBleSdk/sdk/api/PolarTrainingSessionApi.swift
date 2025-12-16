///  Copyright © 2025 Polar. All rights reserved.

import Foundation
import RxSwift

/// Protocol defining methods to access training session data.
public protocol PolarTrainingSessionApi {
    /// Get training session references for a given period. If fromDate and/or toDate are not given this method will return all training session references in the device.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - fromDate: The starting date of the period to retrieve training session references from. Optional.
    ///   - toDate: The ending date of the period to retrieve training session references from. Optional.
    /// - Returns: An Observable emitting `PolarTrainingSessionReference` objects for the specified period.
    func getTrainingSessionReferences(
        identifier: String,
        fromDate: Date?,
        toDate: Date?
    ) -> Observable<PolarTrainingSessionReference>

    /// Get a specific training session using a reference.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - trainingSessionReference: The reference to the training session to retrieve.
    /// - Returns: A Single emitting a `PolarTrainingSession` containing the session data.
    func getTrainingSession(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ) -> Single<PolarTrainingSession>
    
    /// Get a specific training session using a reference with progress updates.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - trainingSessionReference: The reference to the training session to retrieve.
    /// - Returns: An Observable emitting `PolarTrainingSessionFetchResult` containing either progress updates or the complete session data.
    func getTrainingSessionWithProgress(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ) -> Observable<PolarTrainingSessionFetchResult>
    
    /// Api for removing single training session from a Polar device. You can get a list of training sessions with `getTrainingSessionReferences` API.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - reference: PolarTrainingSessionReference with path in device to the training session to be removed.
    /// - Returns: Completable
    ///   - success: When the training session has been deleted from the device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func deleteTrainingSession(identifier: String, reference: PolarTrainingSessionReference) -> Completable
    
    /// Start an exercise session on the device.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - profile: The sport profile to use for the exercise session.
    /// - Returns: A `Completable` that completes when the command has been delivered to the device.
    func startExercise(identifier: String, profile: PolarExerciseSession.SportProfile) -> Completable

    /// Pause an ongoing exercise session.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Returns: A `Completable` that completes when the command has been delivered to the device.
    func pauseExercise(identifier: String) -> Completable

    /// Resume a paused exercise session.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Returns: A `Completable` that completes when the command has been delivered to the device.
    func resumeExercise(identifier: String) -> Completable

    /// Stop the current exercise session.
    ///
    /// By default, the session is saved on the device.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Returns: A `Completable` that completes when the command has been delivered to the device.
    func stopExercise(identifier: String) -> Completable

    /// Get the current exercise session status from the device.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Returns: A `Single` emitting the current `PolarExerciseSession.ExerciseInfo` for the device.
    func getExerciseStatus(identifier: String) -> Single<PolarExerciseSession.ExerciseInfo>
    
    /// Observe exercise session status notifications from the device.
    ///
    /// - Parameter identifier: The Polar device ID or BT address.
    /// - Returns: An `Observable` emitting `PolarExerciseSession.ExerciseInfo` whenever the session status changes.
    func observeExerciseStatus(identifier:  String) -> Observable<PolarExerciseSession.ExerciseInfo>
}
