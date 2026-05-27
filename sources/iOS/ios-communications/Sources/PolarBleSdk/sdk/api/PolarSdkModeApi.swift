//  Copyright © 2023 Polar. All rights reserved.
import Foundation

/// Polar SDK mode API
///
/// In SDK mode the wider range of capabilities is available for the online streaming or
/// for the offline recording than in normal operation mode. The available capabilities can be
/// asked from device using `PolarOnlineStreamingApi.requestFullStreamSettings` or
///  `PolarOfflineRecordingApi.requestFullOfflineRecordingSettings`
///
/// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_sdk_mode`
///
/// Note, SDK mode supported by VeritySense starting from firmware 1.1.5
///
public protocol PolarSdkModeApi {
    ///  Enables SDK mode.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_sdk_mode`
    ///
    /// - Parameter identifier: Polar device id or device address
    /// - Returns: Publisher stream
    func enableSDKMode(_ identifier: String) async throws

    /// Disables SDK mode.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_sdk_mode`
    ///
    /// - Parameter identifier: Polar device id or device address
    /// - Returns: Publisher stream
    func disableSDKMode(_ identifier: String) async throws

    /// Check if SDK mode currently enabled.
    ///
    /// Note, SDK status check is supported by VeritySense starting from firmware 2.1.0
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_sdk_mode`
    ///
    /// - Parameter identifier: Polar device id or device address
    /// - Returns: Publisher emitting true if SDK mode is currently enabled
    func isSDKModeEnabled(_ identifier: String) async throws -> Bool
}
