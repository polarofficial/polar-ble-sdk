// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation

extension PolarBleApiImpl: PolarDerivedMeasurementApi {

    func requestDerivedMeasurementGroupIds(
        _ identifier: String,
        sourceType: PolarDeviceDataType
    ) async throws -> Set<Int> {
        logApiCall("requestDerivedMeasurementGroupIds", ("identifier", identifier), ("sourceType", sourceType))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
            throw PolarErrors.serviceNotFound
        }
        let pmdType = PolarDataUtils.mapToPmdClientMeasurementType(from: sourceType)
        BleLogger.trace("requestDerivedMeasurementGroupIds: sourceType=\(sourceType) Device: \(identifier)")
        let pmdSetting = try await client.querySettings(pmdType, .offline)
        let groupIds = (pmdSetting.settings[.derivedMeasurementSettingsGroupId] ?? []).map { Int($0) }
        return Set(groupIds)
    }

    func requestDerivedMeasurementSettingsGroup(
        _ identifier: String,
        groupId: Int
    ) async throws -> PolarDerivedMeasurementSettingsGroup {
        logApiCall("requestDerivedMeasurementSettingsGroup", ("identifier", identifier), ("groupId", groupId))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
            throw PolarErrors.serviceNotFound
        }
        BleLogger.trace("requestDerivedMeasurementSettingsGroup: groupId=\(groupId) Device: \(identifier)")
        let pmdSetting = try await client.queryDerivedMeasurementSettingsGroup(groupId)
        return PolarDataUtils.mapPmdSettingsToPolarDerivedMeasurementSettingsGroup(pmdSetting, requestedGroupId: groupId)
    }

    func startDerivedOfflineRecording(
        _ identifier: String,
        settings: PolarDerivedMeasurementSettings,
        secret: PolarRecordingSecret? = nil
    ) async throws {
        logApiCall("startDerivedOfflineRecording", ("identifier", identifier), ("groupId", settings.groupId), ("sourceMeasurementType", settings.sourceMeasurementType))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
            throw PolarErrors.serviceNotFound
        }
        let pmdSettings = PolarDataUtils.mapPolarDerivedMeasurementSettingsToPmdSettings(settings)
        var pmdSecret: PmdSecret? = nil
        if let s = secret { pmdSecret = try PolarDataUtils.mapToPmdSecret(from: s) }
        BleLogger.trace("startDerivedOfflineRecording: group=\(settings.groupId) source=\(settings.sourceMeasurementType) @\(settings.sourceSampleRate)Hz window=\(settings.timeWindowMs)ms methods=\(settings.selectedMethods) Device: \(identifier)")
        try await client.startMeasurement(.derivedMeasurement, settings: pmdSettings, .offline, pmdSecret)
        let methodIds = Set(settings.selectedMethods.map { $0.rawValue })
        lastDerivedMethodsCache[identifier, default: [:]][settings.groupId] = methodIds
    }

    func stopDerivedOfflineRecording(_ identifier: String) async throws {
        logApiCall("stopDerivedOfflineRecording", ("identifier", identifier))
        let session = try serviceClientUtils.sessionPmdClientReady(identifier)
        guard let client = session.fetchGattClient(BlePmdClient.PMD_SERVICE) as? BlePmdClient else {
            throw PolarErrors.serviceNotFound
        }
        BleLogger.trace("stopDerivedOfflineRecording Device: \(identifier)")
        try await client.stopMeasurement(.derivedMeasurement)
    }
}

