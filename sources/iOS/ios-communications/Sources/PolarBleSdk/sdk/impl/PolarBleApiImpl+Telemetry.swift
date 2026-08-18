// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
import Combine

private let TAG = "PolarBleApiImpl"

extension PolarBleApiImpl: PolarDeviceTelemetryApi {

    func getAvailableTelemetryTypes(_ identifier: String) -> DeviceTelemetrySupport {
        logApiCall("getAvailableTelemetryTypes", ("identifier", identifier))
        telemetryAvailabilityLock.lock()
        let types = telemetryAvailabilityMap[identifier] ?? []
        telemetryAvailabilityLock.unlock()
        return DeviceTelemetrySupport(availableTelemetryTypes: types, deviceId: identifier)
    }

    func getDeviceTelemetryConfiguration(telemetryType: PolarDeviceTelemetryType, _ identifier: String) async throws -> DeviceTelemetryConfiguration? {
        logApiCall("getDeviceTelemetryConfiguration", ("telemetryType", telemetryType), ("identifier", identifier))
        switch telemetryType {
            case .memfault_mds:
                let session = try serviceClientUtils.sessionMdsClientReady(identifier)
                guard let client = session.fetchGattClient(BleMdsClient.MDS_SERVICE) as? BleMdsClient else {
                    BleLogger.trace("\(TAG) getDeviceTelemetryConfiguration: BLE MDS client not available for \(identifier), no-op")
                    throw PolarErrors.serviceNotFound
                }
                return try await PolarTelemetryMemfaultUtils.getDeviceTelemetryConfiguration(client: client, identifier)
        }
    }

    func startTelemetry(telemetryType: PolarDeviceTelemetryType, _ identifier: String) async throws -> AsyncThrowingStream<DeviceTelemetryEvent, Error> {
        logApiCall("startTelemetry", ("telemetryType", telemetryType), ("identifier", identifier))
        switch telemetryType {
            case .memfault_mds:
                let session = try serviceClientUtils.sessionMdsClientReady(identifier)
                guard let client = session.fetchGattClient(BleMdsClient.MDS_SERVICE) as? BleMdsClient else {
                    BleLogger.trace("\(TAG) startTelemetry: BLE \(telemetryType) client not available for \(identifier), no-op")
                    throw PolarErrors.serviceNotFound
                }
                return PolarTelemetryMemfaultUtils.startMemfaultStream(client: client, identifier)
        }
    }

    func stopTelemetry(telemetryType: PolarDeviceTelemetryType, _ identifier: String) async throws {
        logApiCall("stopTelemetry", ("telemetryType", telemetryType), ("identifier", identifier))
        switch telemetryType {
        case .memfault_mds:
            guard let session = try? serviceClientUtils.sessionMdsClientReady(identifier),
                  let client = session.fetchGattClient(BleMdsClient.MDS_SERVICE) as? BleMdsClient else {
                BleLogger.trace("\(TAG) stopTelemetry: BLE MDS client not available for \(identifier), no-op")
                throw PolarErrors.serviceNotFound
            }
            BleLogger.trace("\(TAG) stopTelemetry for \(identifier)")
            try await client.disableDataExportNotification()
        }
    }
    
    func isMdsReady(_ session: BleDeviceSession) -> AnyPublisher<FeatureState, Never> {
        guard let mdsClient = session.fetchGattClient(BleMdsClient.MDS_SERVICE) as? BleMdsClient else {
            return Just(.notAvailable).eraseToAnyPublisher()
        }
        let state: FeatureState = mdsClient.isServiceDiscovered() ? .ready : .notReady
        return Just(state).eraseToAnyPublisher()
    }
}
