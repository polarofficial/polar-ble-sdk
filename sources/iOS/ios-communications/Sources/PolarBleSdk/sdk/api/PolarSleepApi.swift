///  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

/// Protocol defining methods to get Polar Sleep Data
public protocol PolarSleepApi {
    
    /// Get sleep recording state
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address
    /// - Returns: Single Bool value indicating if sleep recording is ongoing
    func getSleepRecordingState(identifier: String) -> Single<Bool>
    
    /// Observe sleep recording state
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address
    /// - Returns: Observable of Bool value indicating if sleep recording is ongoing
    func observeSleepRecordingState(identifier: String) -> Observable<[Bool]>
    
    /// Stop sleep recording
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address
    /// - Returns: Completable stream
    ///   - success: when sleep recording has been stopped
    ///   - onError: see `PolarErrors` for possible errors invoked
    func stopSleepRecording(identifier: String) -> Completable
    
    /// Get sleep analysis data for a given period.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - fromDate: The starting date of the period to retrieve sleep data from.
    ///   - toDate: The ending date of the period to retrieve sleep until.
    /// - Throws Invalid argument exception if toDate is not after or equal to fromDate
    /// - Returns: A Single emitting an array of `PolarSleepData` representing the sleep analysis data for the specified period.
    func getSleepData(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarSleepData.PolarSleepAnalysisResult]>
}
