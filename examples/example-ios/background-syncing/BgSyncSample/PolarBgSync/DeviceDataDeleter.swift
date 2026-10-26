// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk
import os.log

/// Handles deletion of synced data from the Polar device.
/// Respects retention policy: date-structured data is only deleted when at least
/// `retentionDays` old.
class DeviceDataDeleter {

    private static let logger = Logger(subsystem: "com.polar.bgsync", category: "DeviceDeleter")

    private let api: PolarBleApi
    private let localDataStore: LocalDataStore

    init(api: PolarBleApi, localDataStore: LocalDataStore) {
        self.api = api
        self.localDataStore = localDataStore
    }

    func deleteOldSyncedData(
        deviceId: String,
        dataTypes: Set<SyncableDataType>,
        retentionDays: Int
    ) async throws {
        let cutoffDate = Calendar.current.date(byAdding: .day, value: -retentionDays, to: Calendar.current.startOfDay(for: Date()))!

        Self.logger.info("Cleaning device \(deviceId) — deleting data before \(cutoffDate)")

        for dataType in dataTypes {
            try Task.checkCancellation()
            if dataType == .trainingSessions {
                try await deleteTrainingSessions(deviceId: deviceId, cutoffDate: cutoffDate)
                continue
            }

            guard let storedType = mapToStoredDataType(dataType) else { continue }

            let syncedDates = localDataStore.getSyncedDates(deviceId: deviceId, dataType: dataType)
            guard syncedDates.contains(where: { $0 < cutoffDate }) else { continue }

            do {
                try await api.deleteStoredDeviceData(deviceId, dataType: storedType, until: cutoffDate)
                Self.logger.debug("Deleted \(dataType.rawValue) from device")
            } catch {
                Self.logger.warning("Failed to delete \(dataType.rawValue) from device: \(error)")
                throw error
            }
        }
    }

    private func deleteTrainingSessions(
        deviceId: String,
        cutoffDate: Date
    ) async throws {
        let refs: [PolarTrainingSessionReference]
        do {
            refs = try await api.getTrainingSessionReferences(identifier: deviceId, fromDate: Date(timeIntervalSince1970: 0), toDate: cutoffDate)
        } catch {
            Self.logger.warning("Failed to list training session references for deletion: \(error)")
            throw error
        }

        for ref in refs {
            try Task.checkCancellation()
            guard localDataStore.hasData(deviceId: deviceId, dataType: .trainingSessions, date: ref.date) else {
                continue
            }
            do {
                try await api.deleteTrainingSession(identifier: deviceId, reference: ref)
                Self.logger.debug("Deleted training session \(ref.date) from device")
            } catch {
                Self.logger.warning("Failed to delete training session \(ref.date): \(error)")
                throw error
            }
        }
    }

    private func mapToStoredDataType(_ dataType: SyncableDataType) -> PolarStoredDataType.StoredDataType? {
        switch dataType {
        case .steps, .distance, .activeTime, .calories, .activitySamples:
            return .ACTIVITY
        case .hrSamples247, .ppiSamples247:
            return .AUTO_SAMPLE
        case .nightlyRecharge:
            return .NIGHTLY_RECOVERY
        case .dailySummary:
            return .DAILY_SUMMARY
        case .sleep:
            return .SLEEP
        case .skinTemperature:
            return .SKINTEMP
        case .trainingSessions:
            return nil // Handled separately
        case .offlineAcc, .offlineDerivedAcc, .offlineEcg, .offlineGyro, .offlineMagnetometer,
             .offlinePpg, .offlinePpi, .offlineHr, .offlineTemperature, .offlineSkinTemperature:
            return nil // Recordings are removed individually after fetch
        }
    }
}
