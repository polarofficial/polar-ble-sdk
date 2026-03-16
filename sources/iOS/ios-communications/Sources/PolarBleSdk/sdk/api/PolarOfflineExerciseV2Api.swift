//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import RxSwift

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
/// All operations are asynchronous and return RxSwift primitives.
public protocol PolarOfflineExerciseV2Api {

    /// Starts a new offline exercise session on the device.
    ///
    /// Requires feature `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`.
    ///
    /// - Parameters:
    ///   - identifier: The unique device identifier.
    ///   - sportProfile: The sport profile to use for the session.
    ///
    /// - Returns: A `Single` emitting `OfflineExerciseStartResult`
    ///            describing the outcome of the start request.
    ///
    /// - Errors:
    ///   Emits an error if:
    ///   - The device is not connected.
    ///   - The PSFTP service is not available.
    ///   - The device returns a protocol-level error.
    func startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile
    ) -> Single<OfflineExerciseStartResult>

    /// Stops the currently running offline exercise session.
    ///
    /// Requires feature `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`.
    ///
    /// - Parameter identifier: The unique device identifier.
    ///
    /// - Returns: A `Completable` that completes when the device
    ///            confirms the stop operation.
    ///
    /// - Errors:
    ///   Emits an error if stopping fails or the device is not ready.
    func stopOfflineExerciseV2(
        identifier: String
    ) -> Completable

    /// Retrieves the current offline exercise state.
    ///
    /// Requires feature `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`.
    ///
    /// - Parameter identifier: The unique device identifier.
    ///
    /// - Returns: A `Single` emitting:
    ///   - `true` if a Data Merge exercise is currently running.
    ///   - `false` otherwise.
    ///
    /// - Errors:
    ///   Emits an error if the status request fails.
    func getOfflineExerciseStatusV2(
        identifier: String
    ) -> Single<Bool>

    /// Lists offline exercise entries stored in the device.
    ///
    /// Requires feature `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`.
    ///
    /// - Parameters:
    ///   - identifier: The unique device identifier.
    ///   - directoryPath: Root directory path to search from.
    ///
    /// - Returns: An `Observable` emitting `PolarExerciseEntry`
    ///            objects representing available exercise files.
    func listOfflineExercisesV2(
        identifier: String,
        directoryPath: String
    ) -> Observable<PolarExerciseEntry>

    /// Fetches offline exercise data from the device.
    ///
    /// Requires feature `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`.
    ///
    /// - Parameters:
    ///   - identifier: The unique device identifier.
    ///   - entry: The exercise entry to fetch.
    ///
    /// - Returns: A `Single` emitting `PolarExerciseData`
    ///            containing parsed exercise samples.
    func fetchOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ) -> Single<PolarExerciseData>

    /// Removes an offline exercise from the device.
    ///
    /// Requires feature `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`.
    ///
    /// - Parameters:
    ///   - identifier: The unique device identifier.
    ///   - entry: The exercise entry to remove.
    ///
    /// - Returns: A `Completable` that completes once removal
    ///            has been confirmed by the device.
    func removeOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ) -> Completable

    /// Checks whether the connected device supports
    /// Offline Exercise V2 functionality (`dm_exercise` capability).
    ///
    /// Requires feature `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`.
    ///
    /// - Parameter identifier: The unique device identifier.
    ///
    /// - Returns: A `Single` emitting:
    ///   - `true` if the device supports `dm_exercise`.
    ///   - `false` otherwise.
    func isOfflineExerciseV2Supported(
        identifier: String
    ) -> Single<Bool>

    /// Name of the exercise samples file stored inside
    /// exercise directories on the device.
    static var exerciseSamplesFile: String { get }
}

public extension PolarOfflineExerciseV2Api {

    static var exerciseSamplesFile: String {
        return "SAMPLES.BPB"
    }
    
    /// Starts a new offline exercise session on the device using the default sport profile.
    ///
    /// This is a convenience method that calls
    /// `startOfflineExerciseV2(identifier:sportProfile:)` internally
    /// with `PolarExerciseSession.SportProfile.OTHER_OUTDOOR` as the default sport profile.
    ///
    /// Requires feature `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`.
    ///
    /// - Parameter identifier: The unique device identifier.
    ///
    /// - Returns: A `Single` emitting `OfflineExerciseStartResult`
    ///            describing the outcome of the start request.
    ///
    /// - Errors:
    ///   Emits an error if:
    ///   - The device is not connected.
    ///   - The PSFTP service is not available.
    ///   - The device returns a protocol-level error.
    func startOfflineExerciseV2(
        identifier: String
    ) -> Single<OfflineExerciseStartResult> {
        return startOfflineExerciseV2(
            identifier: identifier,
            sportProfile: .otherOutdoor
        )
    }
    
    /// Lists offline exercise entries stored in the device using the default directory (`"/"`).
    ///
    /// This is a convenience method that calls
    /// `listOfflineExercisesV2(identifier:directoryPath:)` internally.
    ///
    /// Requires feature `PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2`.
    ///
    /// - Parameter identifier: The unique device identifier.
    ///
    /// - Returns: An `Observable` emitting `PolarExerciseEntry` objects
    ///            representing available exercise files in the default directory.
    func listOfflineExercisesV2(
        identifier: String
    ) -> Observable<PolarExerciseEntry> {
        return listOfflineExercisesV2(
            identifier: identifier,
            directoryPath: "/"
        )
    }
}
