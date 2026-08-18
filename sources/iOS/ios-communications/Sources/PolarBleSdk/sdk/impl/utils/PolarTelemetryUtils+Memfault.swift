// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import Combine

private let TAG = "PolarDeviceTelemetryApi"

internal class PolarTelemetryMemfaultUtils {
    
    static func getDeviceTelemetryConfiguration(client: BleMdsClient,_ identifier: String) async throws -> DeviceTelemetryConfiguration {
        // Read all four characteristics concurrently
        async let features  = client.readSupportedFeatures()
        async let deviceId  = client.readDeviceIdentifier()
        async let dataUri   = client.readDataUri()
        async let auth      = client.readAuthorization()
        let (f, d, u, a) = try await (features, deviceId, dataUri, auth)
        BleLogger.trace("\(TAG) getDeviceTelemetryConfiguration for \(identifier): deviceId=\(d) uri=\(u) supportedFeatures=0x\(String(f, radix: 16))")
        
        let memfaultConfig = BleMdsClient.MemfaultTelemetryConfiguration(
            deviceIdentifier: d,
            dataUri: u,
            authorization: a,
            supportedFeatures: [f]
        )
        return .memfault(memfaultConfig)
    }

    static func startMemfaultStream(client: BleMdsClient, _ identifier: String) -> AsyncThrowingStream<DeviceTelemetryEvent, Error> {
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    // Register the chunk stream listener BEFORE enabling notifications so that
                    // no chunks are lost in the window between the 0x01 write and the loop start.
                    let chunkStream = client.observeMemfaultChunks(checkConnection: true)
                    // Enable DATA_EXPORT notification and write 0x01 to start streaming
                    try await client.enableDataExportNotification()
                    BleLogger.trace("\(TAG) telemetry streaming started for \(identifier)")
                    // Stream chunks until disconnected, cancelled, or error
                    for try await chunk in chunkStream {
                        continuation.yield(DeviceTelemetryEvent(type: PolarDeviceTelemetryType.memfault_mds, payload: chunk))
                    }
                    continuation.finish()
                } catch {
                    BleLogger.error("\(TAG) startMemfaultStream error for \(identifier): \(error)")
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    static func stopMemfaultStream(client: BleMdsClient, _ identifier: String) async throws {
        BleLogger.trace("\(TAG) stopMemfaultStreaming for \(identifier)")
        try await client.disableDataExportNotification()
    }
}
