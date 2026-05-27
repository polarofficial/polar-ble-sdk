// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// Polar watch face configuration API.
///
/// Allows reading and writing complications configuration on PolarOS-based Polar devices.
/// Complications are configurable UI elements on a watch face (e.g. SpO2, heart rate, steps).
///
/// Requires feature `PolarBleSdkFeature.feature_polar_watch_faces_configuration`.
public protocol PolarWatchFaceApi {

    /// Read the current watch face configuration from the device KVS.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_watch_faces_configuration`
    /// - Parameter identifier: Polar device ID or BT address.
    /// - Returns: `PolarWatchFaceConfig` reflecting what is currently on the device,
    ///            or a config with an empty list if no complications are configured.
    /// - Throws: see `PolarErrors` for possible errors
    ///
    func getWatchFaceConfig(_ identifier: String) async throws -> PolarWatchFaceConfig

    /// Set the watch face complications on the device.
    ///
    /// Writes the provided complication list to the device KVS watch face configuration.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_watch_faces_configuration`
    /// - Parameters:
    ///   - identifier: Polar device ID or BT address.
    ///   - config: Watch face configuration containing the ordered list of complications to enable.
    /// - Throws: see `PolarErrors` for possible errors
    ///
    func setWatchFaceConfig(_ identifier: String, config: PolarWatchFaceConfig) async throws
}
