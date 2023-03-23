//  Copyright © 2023 Polar. All rights reserved.

import Foundation
import RxSwift

/// Online steaming API.
///
/// Online streaming makes it possible to stream live online data from Polar device.
///
/// Requires features `PolarBleSdkFeature.feature_polar_online_streaming`
///
/// Note, online streaming is supported by VeritySense, H10 and OH1 devices
///
public protocol PolarOnlineStreamingApi {
    ///  Get the data types available in this device for online streaming
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    /// - Returns: Single stream
    ///   - success: set of available online streaming data types in this device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func getAvailableOnlineStreamDataTypes(_ identifier: String) -> Single<Set<PolarDeviceDataType>>
    
    ///  Request the stream settings available in current operation mode. This request shall be used before the stream is started
    ///  to decide currently available settings. The available settings depend on the state of the device. For example, if any stream(s)
    ///  or optical heart rate measurement is already enabled, then the device may limit the offer of possible settings for other stream feature.
    ///  Requires `polarSensorStreaming` feature.
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: selected feature from`PolarDeviceDataType`
    /// - Returns: Single stream
    ///   - success: once after settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestStreamSettings(_ identifier: String, feature: PolarDeviceDataType) -> Single<PolarSensorSetting>
    
    /// Request full steam settings capabilities. The request returns the all capabilities of the requested streaming feature not limited by the current operation mode.
    /// Requires `polarSensorStreaming` feature. This request is supported only by Polar Verity Sense firmware 1.1.5
    ///
    /// - Parameters:
    ///   - identifier: polar device id
    ///   - feature: selected feature from`PolarDeviceDataType`
    /// - Returns: Single stream
    ///   - success: once after full settings received from device
    ///   - onError: see `PolarErrors` for possible errors invoked
    func requestFullStreamSettings(_ identifier: String, feature: PolarDeviceDataType) -> Single<PolarSensorSetting>
    
    /// Start heart rate stream. Heart rate stream is stopped if the connection is closed,
    /// error occurs or stream is disposed.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarHrData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startHrStreaming(_ identifier: String) -> Observable<PolarHrData>
    
    /// Start the ECG (Electrocardiography) stream. ECG stream is stopped if the connection is closed, error occurs or stream is disposed.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarEcgData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startEcgStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarEcgData>
    
    ///  Start ACC (Accelerometer) stream. ACC stream is stopped if the connection is closed, error occurs or stream is disposed.
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarAccData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startAccStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarAccData>
    
    /// Start Gyro stream. Gyro stream is stopped if the connection is closed, error occurs during start or stream is disposed.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    func startGyroStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarGyroData>
    
    /// Start magnetometer stream. Magnetometer stream is stopped if the connection is closed, error occurs or stream is disposed.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    func startMagnetometerStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarMagnetometerData>
    
    /// Start OHR (Optical heart rate) PPG (Photoplethysmography) stream. PPG stream is stopped if the connection is closed, error occurs or stream is disposed.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarOhrData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    @available(*, deprecated, renamed: "startPpgStreaming")
    func startOhrStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarOhrData>
    
    /// Start optical sensor PPG (Photoplethysmography) stream. PPG stream is stopped if the connection is closed, error occurs or stream is disposed.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    ///   - settings: selected settings to start the stream
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarOhrData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startPpgStreaming(_ identifier: String, settings: PolarSensorSetting) -> Observable<PolarPpgData>
    
    /// Start PPI (Pulse to Pulse interval) stream.
    /// PPI stream is stopped if the connection is closed, error occurs or stream is disposed.
    /// Notice that there is a delay before PPI data stream starts.
    ///
    /// - Parameters:
    ///   - identifier: Polar device id or device address
    /// - Returns: Observable stream
    ///   - onNext: for every air packet received. see `PolarPpiData`
    ///   - onError: see `PolarErrors` for possible errors invoked
    func startPpiStreaming(_ identifier: String) -> Observable<PolarPpiData>
    
    @available(*, deprecated, renamed: "startPpiStreaming")
    func startOhrPPIStreaming(_ identifier: String) -> Observable<PolarPpiData>
}
