// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation

private let TAG = "PolarWatchFace"

extension PolarBleApiImpl: PolarWatchFaceApi {

    func getWatchFaceConfig(_ identifier: String) async throws -> PolarWatchFaceConfig {
        BleLogger.trace("\(TAG): getWatchFaceConfig: device=\(identifier) key=\(PolarWatchFaceUtils.WATCH_FACE_CONFIG_KVS_KEY)")
        let fields = try await PolarWatchFaceUtils.readWatchFaceConfigFields(identifier, serviceClientUtils: serviceClientUtils)
        let complications = fields.complicationIds.compactMap { id -> PolarWatchFaceComplication? in
            let c = PolarWatchFaceComplication.fromId(id)
            if c == nil {
                BleLogger.trace("\(TAG): getWatchFaceConfig: id=\(id) not in enum, skipping")
            }
            return c
        }
        BleLogger.trace("\(TAG): getWatchFaceConfig: resolved complications = \(complications.map { "\($0)" })")
        return PolarWatchFaceConfig(enabledComplications: complications)
    }

    func setWatchFaceConfig(_ identifier: String, config: PolarWatchFaceConfig) async throws {
        let ids = config.enabledComplications.map { $0.id }
        BleLogger.trace("\(TAG): setWatchFaceConfig: device=\(identifier) ids=\(ids)")
        try await PolarWatchFaceUtils.writeWatchFaceComplicationInts(identifier,
                                                                     int32Ids: ids,
                                                                     serviceClientUtils: serviceClientUtils)
    }
}