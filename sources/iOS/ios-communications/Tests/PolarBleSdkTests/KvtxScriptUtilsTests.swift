// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

final class KvtxScriptUtilsTests: XCTestCase {

    // MARK: - buildWriteAndCommit

    func test_buildWriteAndCommit_producesCorrectHeader() {
        let data: [UInt8] = [0xAA, 0xBB, 0xCC]
        let key: UInt32 = 0x0000_0007
        let script = KvtxScriptUtils.buildWriteAndCommit(kvKey: key, data: data)

        // byte 0: CMD_WRITE_BYTES (0x00)
        XCTAssertEqual(script[0], KvtxScriptUtils.CMD_WRITE_BYTES)

        // bytes 1-4: key little-endian
        XCTAssertEqual(script[1], 0x07)
        XCTAssertEqual(script[2], 0x00)
        XCTAssertEqual(script[3], 0x00)
        XCTAssertEqual(script[4], 0x00)

        // bytes 5-8: length little-endian (3)
        XCTAssertEqual(script[5], 0x03)
        XCTAssertEqual(script[6], 0x00)
        XCTAssertEqual(script[7], 0x00)
        XCTAssertEqual(script[8], 0x00)

        // bytes 9-11: payload
        XCTAssertEqual(script[9],  0xAA)
        XCTAssertEqual(script[10], 0xBB)
        XCTAssertEqual(script[11], 0xCC)

        // last byte: CMD_COMMIT (0x05)
        XCTAssertEqual(script.last, KvtxScriptUtils.CMD_COMMIT)

        // total length = 1 + 4 + 4 + 3 + 1 = 13
        XCTAssertEqual(script.count, 13)
    }

    func test_buildWriteAndCommit_emptyData() {
        let script = KvtxScriptUtils.buildWriteAndCommit(kvKey: 1, data: [])
        // 1 (cmd) + 4 (key) + 4 (len=0) + 0 (data) + 1 (commit) = 10
        XCTAssertEqual(script.count, 10)
        XCTAssertEqual(script[0], KvtxScriptUtils.CMD_WRITE_BYTES)
        XCTAssertEqual(script.last, KvtxScriptUtils.CMD_COMMIT)
    }

    // MARK: - extractValueForKey - basic round-trip

    func test_extractValueForKey_roundTrip() {
        let payload: [UInt8] = [1, 2, 3, 4, 5]
        let key: UInt32 = 0xDEAD_BEEF
        let script = KvtxScriptUtils.buildWriteAndCommit(kvKey: key, data: payload)
        let extracted = KvtxScriptUtils.extractValueForKey(script: script, kvKey: key)
        XCTAssertEqual(extracted, payload)
    }

    func test_extractValueForKey_wrongKey_returnsNil() {
        let script = KvtxScriptUtils.buildWriteAndCommit(kvKey: 0x01, data: [0xFF])
        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: 0x02)
        XCTAssertNil(result)
    }

    func test_extractValueForKey_emptyScript_returnsNil() {
        let result = KvtxScriptUtils.extractValueForKey(script: [], kvKey: 1)
        XCTAssertNil(result)
    }

    // MARK: - CMD_REMOVE

    func test_extractValueForKey_removedKey_returnsNil() {
        let key: UInt32 = 0x42
        // WRITE_BYTES key data + REMOVE key + COMMIT
        var script = KvtxScriptUtils.buildWriteAndCommit(kvKey: key, data: [0x01])
        // Insert a REMOVE before the trailing COMMIT
        let removeBytes: [UInt8] = [KvtxScriptUtils.CMD_REMOVE] + KvtxScriptUtils.u32Le(key)
        // Remove the trailing COMMIT, add REMOVE + COMMIT
        script = Array(script.dropLast()) + removeBytes + [KvtxScriptUtils.CMD_COMMIT]
        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: key)
        XCTAssertNil(result)
    }

    // MARK: - CMD_APPEND_BYTES

    func test_extractValueForKey_appendBytes_concatenatesData() {
        let key: UInt32 = 0x10
        let part1: [UInt8] = [0xAA, 0xBB]
        let part2: [UInt8] = [0xCC, 0xDD]

        // Build: WRITE_BYTES(key, part1) + APPEND_BYTES(key, part2) + COMMIT
        var script: [UInt8] = []
        script += [KvtxScriptUtils.CMD_WRITE_BYTES] + KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(UInt32(part1.count)) + part1
        script += [KvtxScriptUtils.CMD_APPEND_BYTES] + KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(UInt32(part2.count)) + part2
        script += [KvtxScriptUtils.CMD_COMMIT]

        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: key)
        XCTAssertEqual(result, part1 + part2)
    }

    // MARK: - Multiple keys

    func test_extractValueForKey_multipleKeys_returnsCorrectOne() {
        let keyA: UInt32 = 1
        let keyB: UInt32 = 2
        var script: [UInt8] = []
        script += [KvtxScriptUtils.CMD_WRITE_BYTES] + KvtxScriptUtils.u32Le(keyA) + KvtxScriptUtils.u32Le(3) + [0xA1, 0xA2, 0xA3]
        script += [KvtxScriptUtils.CMD_WRITE_BYTES] + KvtxScriptUtils.u32Le(keyB) + KvtxScriptUtils.u32Le(2) + [0xB1, 0xB2]
        script += [KvtxScriptUtils.CMD_COMMIT]

        XCTAssertEqual(KvtxScriptUtils.extractValueForKey(script: script, kvKey: keyA), [0xA1, 0xA2, 0xA3])
        XCTAssertEqual(KvtxScriptUtils.extractValueForKey(script: script, kvKey: keyB), [0xB1, 0xB2])
    }

    // MARK: - CMD_COPY / CMD_MOVE are skipped

    func test_extractValueForKey_copyCommand_isSkipped() {
        let key: UInt32 = 5
        var script: [UInt8] = []
        script += [KvtxScriptUtils.CMD_WRITE_BYTES] + KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(1) + [0x99]
        // COPY command: just 2 x uint32 args, no payload
        script += [KvtxScriptUtils.CMD_COPY] + KvtxScriptUtils.u32Le(99) + KvtxScriptUtils.u32Le(100)
        script += [KvtxScriptUtils.CMD_COMMIT]

        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: key)
        XCTAssertEqual(result, [0x99])
    }

    // MARK: - u32Le helper

    func test_u32Le_encoding() {
        let bytes = KvtxScriptUtils.u32Le(0x01020304)
        XCTAssertEqual(bytes, [0x04, 0x03, 0x02, 0x01])
    }

    func test_u32Le_zero() {
        XCTAssertEqual(KvtxScriptUtils.u32Le(0), [0, 0, 0, 0])
    }

    func test_u32Le_maxValue() {
        XCTAssertEqual(KvtxScriptUtils.u32Le(0xFFFF_FFFF), [0xFF, 0xFF, 0xFF, 0xFF])
    }
}

