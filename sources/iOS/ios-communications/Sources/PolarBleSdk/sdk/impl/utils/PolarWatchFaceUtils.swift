// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation

private let TAG = "PolarWatchFaceUtils"

// MARK: - Data model

/// Full set of watch face config fields, preserving all values across a read-modify-write cycle.
struct WatchfaceConfigFields {
    var timeStyleId: UInt16 = 0
    var complicationLayoutId: UInt16 = 0
    var backgroundStyleId: UInt16 = 0
    var accentColor: UInt32 = 0
    var complicationIds: [Int32] = []
    var fontfaceId: UInt8 = 0
}

// MARK: - Watch face utilities

internal enum PolarWatchFaceUtils {

    /// KVS key for "ui.watchface_config"
    static let WATCH_FACE_CONFIG_KVS_KEY: UInt32 = 1064434511

    private static let KVTX_FILE_PATH = "/SYS/KVTX"

    // FlatBuffer field indices
    private static let FB_FIELD_TIME_STYLE_ID:          Int = 0
    private static let FB_FIELD_COMPLICATION_LAYOUT_ID: Int = 1
    private static let FB_FIELD_BACKGROUND_STYLE_ID:    Int = 2
    private static let FB_FIELD_ACCENT_COLOR:           Int = 3
    private static let FB_FIELD_COMPLICATION_IDS:       Int = 4
    private static let FB_FIELD_FONTFACE_ID:            Int = 5
    private static let FB_TABLE_FIELD_COUNT:            Int = 6

    // MARK: - Public API

    /// Build a KVTXScript (WRITE_BYTES + COMMIT) carrying a full WatchfaceConfig FlatBuffer.
    static func buildKvtxScript(fields: WatchfaceConfigFields) -> [UInt8] {
        let configData = buildWatchFaceConfigFlatBuffer(fields: fields)
        return KvtxScriptUtils.buildWriteAndCommit(kvKey: WATCH_FACE_CONFIG_KVS_KEY, data: configData)
    }

    static func extractWatchFaceConfigFromKvtxScript(script: [UInt8]) -> [UInt8]? {
        return KvtxScriptUtils.extractValueForKey(script: script, kvKey: WATCH_FACE_CONFIG_KVS_KEY)
    }

    // MARK: - FlatBuffer encode

    /// Build a WatchfaceConfig FlatBuffer preserving all fields from `fields`.
    static func buildWatchFaceConfigFlatBuffer(fields: WatchfaceConfigFields) -> [UInt8] {
        let builder = FlatBufferBuilder(initialSize: 256)

        // Build complication_ids vector first
        builder.startVector(elemSize: 4, count: fields.complicationIds.count, alignment: 4)
        for i in stride(from: fields.complicationIds.count - 1, through: 0, by: -1) {
            builder.addRawInt32(fields.complicationIds[i])
        }
        let vectorOffset = builder.endVector(count: fields.complicationIds.count)

        builder.startTable(numFields: FB_TABLE_FIELD_COUNT)

        // field 0: time_style_id (uint16)
        builder.addInt16(field: FB_FIELD_TIME_STYLE_ID,
                         value: Int16(bitPattern: fields.timeStyleId),
                         defaultValue: 0)
        // field 1: complication_layout_id (uint16)
        builder.addInt16(field: FB_FIELD_COMPLICATION_LAYOUT_ID,
                         value: Int16(bitPattern: fields.complicationLayoutId),
                         defaultValue: 0)
        // field 2: background_style_id (uint16)
        builder.addInt16(field: FB_FIELD_BACKGROUND_STYLE_ID,
                         value: Int16(bitPattern: fields.backgroundStyleId),
                         defaultValue: 0)
        // field 3: accent_color (uint32 stored as int32)
        builder.addInt32(field: FB_FIELD_ACCENT_COLOR,
                         value: Int32(bitPattern: fields.accentColor),
                         defaultValue: 0)
        // field 4: complication_ids (vector offset)
        builder.addOffset(field: FB_FIELD_COMPLICATION_IDS,
                          value: vectorOffset,
                          defaultValue: 0)
        // field 5: fontface_id (byte)
        builder.addByte(field: FB_FIELD_FONTFACE_ID,
                        value: Int8(bitPattern: fields.fontfaceId),
                        defaultValue: 0)

        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        return builder.sizedByteArray()
    }

    // MARK: - FlatBuffer decode

    static func parseWatchFaceConfigFlatBuffer(raw: [UInt8]) -> WatchfaceConfigFields {
        let empty = WatchfaceConfigFields()
        guard raw.count >= 4 else {
            BleLogger.trace("\(TAG): parseWatchFaceConfigFlatBuffer: too short (\(raw.count) bytes), returning defaults")
            return empty
        }

        func u16At(_ p: Int) -> Int {
            guard p + 2 <= raw.count else { return 0 }
            return Int(raw[p]) | (Int(raw[p+1]) << 8)
        }
        func i16At(_ p: Int) -> Int16 {
            return Int16(bitPattern: UInt16(u16At(p)))
        }
        func u32At(_ p: Int) -> UInt32 {
            guard p + 4 <= raw.count else { return 0 }
            return UInt32(raw[p]) | (UInt32(raw[p+1]) << 8) |
                   (UInt32(raw[p+2]) << 16) | (UInt32(raw[p+3]) << 24)
        }
        func i32At(_ p: Int) -> Int32 {
            return Int32(bitPattern: u32At(p))
        }

        let rootOffset = Int(u32At(0))
        guard rootOffset + 4 <= raw.count else {
            BleLogger.trace("\(TAG): parseWatchFaceConfigFlatBuffer: rootOffset out of bounds")
            return empty
        }

        let vtableOffsetFromTable = Int(i32At(rootOffset))
        let vtablePos = rootOffset - vtableOffsetFromTable
        guard vtablePos >= 0, vtablePos + 4 <= raw.count else {
            BleLogger.trace("\(TAG): parseWatchFaceConfigFlatBuffer: vtablePos out of bounds")
            return empty
        }

        let vtableSize = u16At(vtablePos)
        let fieldCount = (vtableSize - 4) / 2

        func fieldOffset(_ fieldIdx: Int) -> Int {
            guard fieldIdx < fieldCount else { return 0 }
            return u16At(vtablePos + 4 + fieldIdx * 2)
        }

        // field 0: time_style_id (uint16)
        let timeStyleId: UInt16 = {
            let fo = fieldOffset(0); guard fo != 0 else { return 0 }
            return UInt16(bitPattern: i16At(rootOffset + fo))
        }()

        // field 1: complication_layout_id (uint16)
        let complicationLayoutId: UInt16 = {
            let fo = fieldOffset(1); guard fo != 0 else { return 0 }
            return UInt16(bitPattern: i16At(rootOffset + fo))
        }()

        // field 2: background_style_id (uint16)
        let backgroundStyleId: UInt16 = {
            let fo = fieldOffset(2); guard fo != 0 else { return 0 }
            return UInt16(bitPattern: i16At(rootOffset + fo))
        }()

        // field 3: accent_color (uint32)
        let accentColor: UInt32 = {
            let fo = fieldOffset(3); guard fo != 0 else { return 0 }
            return u32At(rootOffset + fo)
        }()

        // field 4: complication_ids ([int32])
        let complicationIds: [Int32] = {
            let fo = fieldOffset(4)
            guard fo != 0 else { return [] }
            let vectorRefPos = rootOffset + fo
            guard vectorRefPos + 4 <= raw.count else {
                BleLogger.trace("\(TAG): parseWatchFaceConfigFlatBuffer: complication_ids ref out of bounds")
                return []
            }
            let vectorPos = vectorRefPos + Int(i32At(vectorRefPos))
            guard vectorPos + 4 <= raw.count else {
                BleLogger.trace("\(TAG): parseWatchFaceConfigFlatBuffer: complication_ids vector out of bounds")
                return []
            }
            let vectorLength = Int(i32At(vectorPos))
            guard vectorLength >= 0, vectorLength <= 1000 else {
                BleLogger.trace("\(TAG): parseWatchFaceConfigFlatBuffer: complication_ids length \(vectorLength) invalid")
                return []
            }
            let dataStart = vectorPos + 4
            guard dataStart + vectorLength * 4 <= raw.count else {
                BleLogger.trace("\(TAG): parseWatchFaceConfigFlatBuffer: complication_ids data overruns buffer")
                return []
            }
            var ids = [Int32]()
            for i in 0..<vectorLength {
                let id = i32At(dataStart + i * 4)
                let known = PolarWatchFaceComplication.fromId(id)
                BleLogger.trace("\(TAG): complication_ids[\(i)] = \(id) (0x\(String(UInt32(bitPattern: id), radix: 16)))" +
                    (known != nil ? " => \(known!)" : " => UNKNOWN"))
                ids.append(id)
            }
            return ids
        }()

        // field 5: fontface_id (byte)
        let fontfaceId: UInt8 = {
            let fo = fieldOffset(5); guard fo != 0 else { return 0 }
            return raw[rootOffset + fo]
        }()

        return WatchfaceConfigFields(
            timeStyleId: timeStyleId,
            complicationLayoutId: complicationLayoutId,
            backgroundStyleId: backgroundStyleId,
            accentColor: accentColor,
            complicationIds: complicationIds,
            fontfaceId: fontfaceId
        )
    }

    // MARK: - PFTP read / write

    static func readWatchFaceConfigFields(_ identifier: String,
                                          serviceClientUtils: PolarServiceClientUtils) async throws -> WatchfaceConfigFields {
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = KVTX_FILE_PATH
        BleLogger.trace("\(TAG): readWatchFaceConfigFields: GET \(KVTX_FILE_PATH)")
        let requestData = try operation.serializedData()
        let responseData = try await client.request(requestData)
        let bytes = [UInt8](responseData)
        BleLogger.trace("\(TAG): readWatchFaceConfigFields: received \(bytes.count) bytes")
        if let flatBufferBytes = extractWatchFaceConfigFromKvtxScript(script: bytes) {
            return parseWatchFaceConfigFlatBuffer(raw: flatBufferBytes)
        } else {
            BleLogger.trace("\(TAG): readWatchFaceConfigFields: key not found, returning defaults")
            return WatchfaceConfigFields()
        }
    }

    static func writeWatchFaceComplicationInts(_ identifier: String,
                                               int32Ids: [Int32],
                                               serviceClientUtils: PolarServiceClientUtils) async throws {
        var existingFields = try await readWatchFaceConfigFields(identifier, serviceClientUtils: serviceClientUtils)
        existingFields.complicationIds = int32Ids
        BleLogger.trace("\(TAG): writeWatchFaceComplicationInts: merged=\(existingFields)")

        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        let kvtxScript = buildKvtxScript(fields: existingFields)
        BleLogger.trace("\(TAG): writeWatchFaceComplicationInts: PUT \(kvtxScript.count) bytes to \(KVTX_FILE_PATH)")

        var operation = Protocol_PbPFtpOperation()
        operation.command = .put
        operation.path = KVTX_FILE_PATH
        let proto = try operation.serializedData()
        let inputStream = InputStream(data: Data(kvtxScript))
        for try await _ in client.write(proto as NSData, data: inputStream) {}
    }
}
