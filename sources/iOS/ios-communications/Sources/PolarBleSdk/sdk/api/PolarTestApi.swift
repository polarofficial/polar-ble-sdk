/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Polar SPO2 test API.
public protocol PolarTestApi {
    /// Get SPO2 test data for a given date period.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_spo2_test_data`
    ///
    /// - Parameters:
    ///   - identifier: The Polar device ID or BT address.
    ///   - fromDate: The starting date of the period to retrieve data from.
    ///   - toDate: The ending date of the period to retrieve data from.
    /// - Returns: A list of `PolarSpo2TestData` for the specified period.
    func getSpo2TestData(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSpo2TestData]
}
