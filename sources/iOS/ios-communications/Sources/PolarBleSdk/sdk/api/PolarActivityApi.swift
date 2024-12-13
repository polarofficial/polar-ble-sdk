///  Copyright © 2024 Polar. All rights reserved.

import Foundation
import RxSwift

/// Protocol defining methods to get steps for a given period.
public protocol PolarActivityApi {
    /// Get steps for a given period.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - fromDate: The starting date of the period to retrieve steps from.
    ///   - toDate: The ending date of the period to retrieve steps from.
    /// - Returns: A Single emitting an array of `PolarStepsData` representing the steps data for the specified period.
    func getSteps(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarStepsData]>
    
    /// Get distance for a given period.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - fromDate: The starting date of the period to retrieve distance from.
    ///   - toDate: The ending date of the period to retrieve distance from.
    /// - Returns: A Single emitting an array of `PolarDistanceData` representing the distance data for the specified period.
    func getDistance(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarDistanceData]>

    /// Get active time for a given period.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - fromDate: The starting date of the period to retrieve active time from.
    ///   - toDate: The ending date of the period to retrieve active time from.
    /// - Returns: A Single emitting an array of `PolarActiveTimeData` representing the active time data for the specified period.
    func getActiveTime(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarActiveTimeData]>
    
    /// Get 24/7 heart rate samples for a given period.
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - fromDate: The starting date of the period to retrieve heart rate samples from.
    ///   - toDate: The ending date of the period to retrieve heart rate samples from.
    /// - Returns: A Single emitting an array of `PolarActiveTimeData` representing the heart rate samples data for the specified period.
    func get247HrSamples(identifier: String, fromDate: Date, toDate: Date) -> Single<[Polar247HrSamplesData]>
}
