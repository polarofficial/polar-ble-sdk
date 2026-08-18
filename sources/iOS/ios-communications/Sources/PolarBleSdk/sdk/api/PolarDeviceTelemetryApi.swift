// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation

/// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
/// Polar Telemetry API for streaming telemetry data from devices.
///
/// Provides access to telemetry specific BLE services for streaming telemetry data from device.
///
/// Typical usage:
/// ```swift
///
/// 1. Get available telemetry types for the device using `getAvailableTelemetryTypes` API.
/// 2. Read connection parameters from the device
/// let configuration = try await api.getDeviceTelemetryConfiguration(identifier)
///
/// 2. Start streaming and listen for telemetry data using `startTelemetryStreaming` API.
/// for try await telemetry in api.startTelemetry(identifier) {
///     try await sendTelemetryData(telemetry, configuration: configuration) // Not SDK API
/// }
/// ```
///
/// - Requires feature `PolarBleSdkFeature.feature_telemetry_data_streaming`.
public protocol PolarDeviceTelemetryApi {

    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// Read telemetry configuration from the device.
    ///
    /// Reads the telemetry configuration from device or from BLE service.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_telemetry_data_streaming`
    /// - Parameter identifier: Polar device ID or BT address.
    /// - Parameter telemetryType: PolarDeviceTelemetryType to stream. If the desired telemetry type is not supported by the device, the API will throw an error.
    /// - Returns: `DeviceTelemetryConfiguration` containing items that describe the incoming data configuration. Nil if configuration is not available.
    /// - Throws: See `PolarErrors` for possible errors.
    ///
    func getDeviceTelemetryConfiguration(telemetryType: PolarDeviceTelemetryType, _ identifier: String) async throws -> DeviceTelemetryConfiguration?

    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// Request a device  to start streaming telemetry data.
    /// Each element emitted by the stream is one unmodified raw data sent by the device.
    ///
    /// The stream finishes when the device disconnects, an error occurs, or the returned
    /// stream is cancelled (which also disables the device notification).
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_telemetry_data_streaming`
    /// - Parameter identifier: Polar device ID or BT address.
    /// - Parameter telemetryType: PolarDeviceTelemetryType to stream.  If the desired telemetry type is not supported by the device, the API will throw an error.
    /// - Returns: `AsyncThrowingStream<DeviceTelemetryEvent, Error>` that emits one `DeviceTelemetryEvent` per received telemetry data item.
    func startTelemetry(telemetryType: PolarDeviceTelemetryType, _ identifier: String) async throws -> AsyncThrowingStream<DeviceTelemetryEvent, Error>

    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// Request a device  to stop streaming telemetry data.
    ///
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_telemetry_data_streaming`
    /// - Parameter identifier: Polar device ID or BT address.
    /// - Parameter telemetryType: PolarDeviceTelemetryType the device is desired to stop streaming. If the desired PolarDeviceTelemetryType is not supported by the device, the API will throw an error.
    /// - Throws: See `PolarErrors` for possible errors.
    func stopTelemetry(telemetryType: PolarDeviceTelemetryType, _ identifier: String) async throws

    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// Returns all telemetry types supported by this SDK for the given device.
    ///
    /// The returned values are the `DeviceTelemetrySupport` representation of each currently available telemetry streaming type in device. Use this list to discover which telemetry types can be requested before starting a stream.
    ///
    /// - Parameter identifier: Polar device ID or BT address.
    /// - Returns: `DeviceTelemetrySupport` containing available telemetry types for the device.
    func getAvailableTelemetryTypes(_ identifier: String) -> DeviceTelemetrySupport
}

/// Telemetry types available in the Polar SDK.
public enum PolarDeviceTelemetryType: CaseIterable {
    /// Telemetry type for Memfault diagnostic data streaming.
    case memfault_mds

    public var displayName: String {
        switch self {
            case .memfault_mds: return "Memfault MDS"
        }
    }

    public static var allCases: [PolarDeviceTelemetryType] {
        return [.memfault_mds]
    }
}

/// NOTE: this is experimental code intended for Polar internal use only. Polar will not support 3rd party users with this API.
public struct DeviceTelemetryEvent {
    public let type: PolarDeviceTelemetryType
    public let payload: Data
}

/// NOTE: this is experimental code intended for Polar internal use only. Polar will not support 3rd party users with this API.
public struct DeviceTelemetrySupport {
    public let availableTelemetryTypes: [PolarDeviceTelemetryType]
    public let deviceId: String
}

/// NOTE: this is experimental code intended for Polar internal use only. Polar will not support 3rd party users with this API.
/// Telemetry configuration that can hold different configuration types for different telemetry services.
/// Use pattern matching to extract the appropriate configuration for the telemetry type.
public enum DeviceTelemetryConfiguration: Equatable, Sendable {
    /// Memfault MDS telemetry configuration with cloud upload parameters
    case memfault(BleMdsClient.MemfaultTelemetryConfiguration)
    
    /// Common device identifier across all configuration types
    public var deviceIdentifier: String {
        switch self {
        case .memfault(let config):
            return config.deviceIdentifier
        }
    }
    
    /// Data URI for uploading telemetry data (Memfault only, nil for other types)
    public var dataUri: String? {
        switch self {
        case .memfault(let config):
            return config.dataUri
        }
    }
    
    /// Authorization token for uploading telemetry data (Memfault only, nil for other types)
    public var authorization: String? {
        switch self {
        case .memfault(let config):
            return config.authorization
        }
    }
    
    /// Supported features bitmask (type-specific meaning)
    public var supportedFeatures: [UInt32]? {
        switch self {
        case .memfault(let config):
            return config.supportedFeatures
        }
    }
}
