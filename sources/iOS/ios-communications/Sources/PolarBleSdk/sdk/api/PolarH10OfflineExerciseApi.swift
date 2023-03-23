//  Copyright © 2023 Polar. All rights reserved.

import Foundation
import RxSwift

///  Recoding intervals for H10 recording start
public enum RecordingInterval: Int {
    case interval_1s = 1
    case interval_5s = 5
}

/// Sample types for H10 recording start
public enum SampleType: Int {
    /// recording type to use is hr in BPM
    case hr
    /// recording type to use is rr interval
    case rr
}

/// H10 Exercise recording API.
///
/// H10 Exercise recording makes it possible to record Hr or Rr data to H10 device memory.
/// With H10 Exercise recording the H10 and phone don't need to be connected all the time, as H10 exercise recording
/// continues in Polar device even the BLE disconnects.
///
/// Requires features `PolarBleSdkFeature.feature_polar_h10_exercise_recording`
///
/// Note, API is working only with Polar H10 device
///
public protocol PolarH10OfflineExerciseApi {
    /// Request start recording. Supported only by Polar H10. Requires `polarFileTransfer` feature.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    ///   - exerciseId: unique identifier for for exercise entry length from 1-64 bytes
    ///   - interval: recording interval to be used. Has no effect if `sampleType` is `SampleType.rr`
    ///   - sampleType: sample type to be used.
    /// - Returns: Completable stream
    ///   - success: recording started
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startRecording(_ identifier: String, exerciseId: String, interval: RecordingInterval, sampleType: SampleType) -> Completable
    
    /// Request stop for current recording. Supported only by Polar H10. Requires `polarFileTransfer` feature.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or UUID
    /// - Returns: Completable stream
    ///   - success: recording stopped
    ///   - onError: see `PolarErrors` for possible errors invoked
    func stopRecording(_ identifier: String) -> Completable
    
    /// Request current recording status. Supported only by Polar H10. Requires `polarFileTransfer` feature.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id
    /// - Returns: Single stream
    ///   - success: see `PolarRecordingStatus`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestRecordingStatus(_ identifier: String) -> Single<PolarRecordingStatus>
    
    /// Api for fetching stored exercises list from Polar H10 device. Requires `polarFileTransfer` feature. This API is working for Polar OH1 and Polar Verity Sense devices too, however in those devices recording of exercise requires that sensor is registered to Polar Flow account.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    /// - Returns: Observable stream
    ///   - onNext: see `PolarExerciseEntry`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func fetchStoredExerciseList(_ identifier: String) -> Observable<PolarExerciseEntry>
    
    /// Api for fetching a single exercise from Polar H10 device. Requires `polarFileTransfer` feature. This API is working for Polar OH1 and Polar Verity Sense devices too, however in those devices recording of exercise requires that sensor is registered to Polar Flow account.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - entry: single exercise entry to be fetched
    /// - Returns: Single stream
    ///   - success: invoked after exercise data has been fetched from the device. see `PolarExerciseEntry`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func fetchExercise(_ identifier: String, entry: PolarExerciseEntry) -> Single<PolarExerciseData>
    
    /// Api for removing single exercise from Polar H10 device. Requires `polarFileTransfer` feature. This API is working for Polar OH1 and Polar Verity Sense devices too, however in those devices recording of exercise requires that sensor is registered to Polar Flow account.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - entry: single exercise entry to be removed
    /// - Returns: Completable stream
    ///   - complete: entry successfully removed
    ///   - onError: see `PolarErrors` for possible errors invoked
    func removeExercise(_ identifier: String, entry: PolarExerciseEntry) ->Completable
}
