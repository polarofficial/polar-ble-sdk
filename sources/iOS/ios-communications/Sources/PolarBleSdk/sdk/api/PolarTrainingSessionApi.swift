///  Copyright © 2025 Polar. All rights reserved.

import Foundation
import RxSwift

/// Protocol defining methods to access training session data.
public protocol PolarTrainingSessionApi {
    /// Get training session references for a given period.
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
}
