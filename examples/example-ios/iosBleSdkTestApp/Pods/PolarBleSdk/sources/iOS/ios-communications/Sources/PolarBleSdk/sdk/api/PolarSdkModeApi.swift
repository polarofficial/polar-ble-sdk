//  Copyright © 2023 Polar. All rights reserved.
import Foundation
import RxSwift

/// Polar SDK mode API
///
/// In SDK mode the wider range of capabilities is available for the online streaming or
/// for the offline recording than in normal operation mode. The available capabilities can be
/// asked from device using `PolarOnlineStreamingApi.requestFullStreamSettings` or
///  `PolarOfflineRecordingApi.requestFullOfflineRecordingSettings`
///
/// Requires features `PolarBleSdkFeature.feature_polar_sdk_mode`
///
/// Note, SDK mode supported by VeritySense starting from firmware 1.1.5
///
public protocol PolarSdkModeApi {
    
    ///  Enables SDK mode.
    ///
    /// - Parameter identifier: Polar device id or device address
    /// - Returns: Completable stream
    ///   - success: if SDK mode is enabled or device is already in SDK mode
    ///   - onError: if SDK mode enable failed
    func enableSDKMode(_ identifier: String) -> Completable
    
    /// Disables SDK mode.
    ///
    /// - Parameter identifier: Polar device id or device address
    /// - Returns: Completable stream
    ///   - success: if SDK mode is disabled or SDK mode was already disabled
    ///   - onError: if SDK mode disable failed
    func disableSDKMode(_ identifier: String) -> Completable
    
    /// Check if SDK mode currently enabled.
    ///
    /// Note, SDK status check is supported by VeritySense starting from firmware 2.1.0
    ///
    /// - Parameter identifier: Polar device id or device address
    /// - Returns: Single stream
    ///   - success: emits true, if the SDK mode is currently enabled
    ///   - onError: see `PolarErrors` for possible errors invoked
    func isSDKModeEnabled(_ identifier: String) -> Single<Bool>
}
