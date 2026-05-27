// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

final class PolarWatchFaceUtilsTests: XCTestCase {

    // MARK: - FlatBuffer encode → decode round-trip

    func test_buildAndParseWatchFaceConfig_defaultFields() {
        let fields = WatchfaceConfigFields()
        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.timeStyleId, 0)
        XCTAssertEqual(parsed.complicationLayoutId, 0)
        XCTAssertEqual(parsed.backgroundStyleId, 0)
        XCTAssertEqual(parsed.accentColor, 0)
        XCTAssertEqual(parsed.complicationIds, [])
        XCTAssertEqual(parsed.fontfaceId, 0)
    }

    func test_buildAndParseWatchFaceConfig_allScalarsSet() {
        var fields = WatchfaceConfigFields()
        fields.timeStyleId = 3
        fields.complicationLayoutId = 7
        fields.backgroundStyleId = 2
        fields.accentColor = 0xFF_CC_88_00
        fields.fontfaceId = 1

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.timeStyleId, 3)
        XCTAssertEqual(parsed.complicationLayoutId, 7)
        XCTAssertEqual(parsed.backgroundStyleId, 2)
        XCTAssertEqual(parsed.accentColor, 0xFF_CC_88_00)
        XCTAssertEqual(parsed.complicationIds, [])
        XCTAssertEqual(parsed.fontfaceId, 1)
    }

    func test_buildAndParseWatchFaceConfig_withComplications() {
        var fields = WatchfaceConfigFields()
        let heartRateId = PolarWatchFaceComplication.heartRate.id
        let spo2Id      = PolarWatchFaceComplication.spo2.id
        let stepsId     = PolarWatchFaceComplication.activity.id
        fields.complicationIds = [heartRateId, spo2Id, stepsId]

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.complicationIds, [heartRateId, spo2Id, stepsId])
    }

    func test_buildAndParseWatchFaceConfig_preservesOrderOfComplications() {
        var fields = WatchfaceConfigFields()
        let ids: [Int32] = [
            PolarWatchFaceComplication.date.id,
            PolarWatchFaceComplication.battery.id,
            PolarWatchFaceComplication.heartRate.id,
            PolarWatchFaceComplication.empty.id,
            PolarWatchFaceComplication.weather.id,
        ]
        fields.complicationIds = ids

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.complicationIds, ids)
    }

    func test_buildAndParseWatchFaceConfig_singleComplication() {
        var fields = WatchfaceConfigFields()
        fields.complicationIds = [PolarWatchFaceComplication.compass.id]

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.complicationIds, [PolarWatchFaceComplication.compass.id])
    }

    // MARK: - KVTX round-trip (build + extract)

    func test_buildKvtxScript_roundTrip() {
        var fields = WatchfaceConfigFields()
        fields.timeStyleId = 1
        fields.complicationIds = [PolarWatchFaceComplication.heartRate.id]

        let script = PolarWatchFaceUtils.buildKvtxScript(fields: fields)
        let extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script: script)

        XCTAssertNotNil(extracted)

        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: extracted!)
        XCTAssertEqual(parsed.timeStyleId, 1)
        XCTAssertEqual(parsed.complicationIds, [PolarWatchFaceComplication.heartRate.id])
    }

    func test_buildKvtxScript_wrongKey_returnsNil() {
        let fields = WatchfaceConfigFields()
        let script = PolarWatchFaceUtils.buildKvtxScript(fields: fields)

        // Try a different key
        let wrongKey: UInt32 = 0x0000_0001
        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: wrongKey)
        XCTAssertNil(result)
    }

    func test_extractWatchFaceConfigFromKvtxScript_emptyScript_returnsNil() {
        let result = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script: [])
        XCTAssertNil(result)
    }

    // MARK: - parseWatchFaceConfigFlatBuffer - edge cases

    func test_parseWatchFaceConfigFlatBuffer_tooShort_returnsDefaults() {
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: [0x00, 0x01])
        XCTAssertEqual(parsed.complicationIds, [])
        XCTAssertEqual(parsed.timeStyleId, 0)
    }

    func test_parseWatchFaceConfigFlatBuffer_emptyBytes_returnsDefaults() {
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: [])
        XCTAssertEqual(parsed.complicationIds, [])
    }

    // MARK: - Complication IDs match Android enum hash codes

    func test_complicationId_heartRate_matchesJavaHashCode() {
        // Java "heart-rate-complication".hashCode() == known value
        let expected = javaHashCode("heart-rate-complication")
        XCTAssertEqual(PolarWatchFaceComplication.heartRate.id, expected)
    }

    func test_complicationId_spo2_matchesJavaHashCode() {
        let expected = javaHashCode("spo2-complication")
        XCTAssertEqual(PolarWatchFaceComplication.spo2.id, expected)
    }

    func test_complicationId_empty_isZero() {
        // empty string has Java hashCode 0
        XCTAssertEqual(PolarWatchFaceComplication.empty.id, 0)
    }

    func test_complicationFromId_roundTrip() {
        for complication in PolarWatchFaceComplication.allCases {
            let resolved = PolarWatchFaceComplication.fromId(complication.id)
            XCTAssertEqual(resolved, complication, "fromId should resolve \(complication)")
        }
    }

    // MARK: - Preserve existing non-complication fields on write

    func test_writePreservesExistingFields() {
        // Simulate existing device state
        var existing = WatchfaceConfigFields()
        existing.timeStyleId = 5
        existing.complicationLayoutId = 2
        existing.backgroundStyleId = 3
        existing.accentColor = 0xAABBCCDD
        existing.fontfaceId = 1
        existing.complicationIds = [PolarWatchFaceComplication.date.id]

        // Only change complications
        var merged = existing
        merged.complicationIds = [PolarWatchFaceComplication.heartRate.id, PolarWatchFaceComplication.battery.id]

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: merged)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        // Scalars preserved
        XCTAssertEqual(parsed.timeStyleId, 5)
        XCTAssertEqual(parsed.complicationLayoutId, 2)
        XCTAssertEqual(parsed.backgroundStyleId, 3)
        XCTAssertEqual(parsed.accentColor, 0xAABBCCDD)
        XCTAssertEqual(parsed.fontfaceId, 1)
        // Complications updated
        XCTAssertEqual(parsed.complicationIds, [
            PolarWatchFaceComplication.heartRate.id,
            PolarWatchFaceComplication.battery.id
        ])
    }

    // MARK: - Helpers

    /// Reproduces Java's String.hashCode() algorithm (signed 32-bit).
    private func javaHashCode(_ s: String) -> Int32 {
        var h: Int32 = 0
        for scalar in s.unicodeScalars {
            h = h &* 31 &+ Int32(bitPattern: scalar.value)
        }
        return h
    }
}

