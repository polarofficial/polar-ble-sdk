/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import RxSwift

/// Polar temperature API.
///
/// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_temperature_data`
public protocol PolarTemperatureApi {

    /// Get skin temperature for a given period.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_temperature_data`
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - fromDate: The starting date of the period to retrieve skin temperature from.
    ///   - toDate: The ending date of the period to retrieve skin temperature from.
    /// - Returns: A Single emitting an array of `PolarSkinTemperatureResult` representing the skin temperature data for the specified period.
    func getSkinTemperature(identifier: String, fromDate: Date, toDate: Date) -> Single<[PolarSkinTemperatureData.PolarSkinTemperatureResult]>
}
