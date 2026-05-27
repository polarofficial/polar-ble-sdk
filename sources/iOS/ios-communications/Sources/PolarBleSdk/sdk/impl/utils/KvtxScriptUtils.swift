// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation

private let TAG = "KvtxScriptUtils"

/// Generic KVTXScript builder and scanner.
internal enum KvtxScriptUtils {

    static let CMD_WRITE_BYTES:    UInt8 = 0x00
    static let CMD_APPEND_BYTES:   UInt8 = 0x01
    static let CMD_REMOVE:         UInt8 = 0x02
    static let CMD_COPY:           UInt8 = 0x03
    static let CMD_MOVE:           UInt8 = 0x04
    static let CMD_COMMIT:         UInt8 = 0x05
    static let CMD_WRITE_BYTES_EX: UInt8 = 0x06
    static let CMD_APPEND_BYTES_EX:UInt8 = 0x07
    static let CMD_REMOVE_EX:      UInt8 = 0x08

    /// Build a KVTXScript that writes `data` to `kvKey` and commits.
    ///
    /// Structure: WRITE_BYTES(key, data) + COMMIT
    static func buildWriteAndCommit(kvKey: UInt32, data: [UInt8]) -> [UInt8] {
        var script = [UInt8]()
        script.append(CMD_WRITE_BYTES)
        script.append(contentsOf: u32Le(kvKey))
        script.append(contentsOf: u32Le(UInt32(data.count)))
        script.append(contentsOf: data)
        script.append(CMD_COMMIT)
        return script
    }

    /// Scan a full KVTXScript binary and extract the raw value bytes stored under `kvKey`.
    ///
    /// Returns `nil` if the key is not present (or was removed) in the script.
    static func extractValueForKey(script: [UInt8], kvKey: UInt32) -> [UInt8]? {
        var pos = 0
        var result: [UInt8]? = nil

        func readUInt32() -> UInt32? {
            guard pos + 4 <= script.count else { return nil }
            let v = UInt32(script[pos]) |
                    (UInt32(script[pos+1]) << 8) |
                    (UInt32(script[pos+2]) << 16) |
                    (UInt32(script[pos+3]) << 24)
            pos += 4
            return v
        }

        func readBytes(_ n: Int) -> [UInt8]? {
            guard n >= 0, pos + n <= script.count else { return nil }
            let bytes = Array(script[pos..<(pos+n)])
            pos += n
            return bytes
        }

        while pos < script.count {
            let startPos = pos
            let cmd = script[pos]; pos += 1

            switch cmd {
            case CMD_WRITE_BYTES, CMD_APPEND_BYTES:
                guard let key = readUInt32(), let length = readUInt32(), let data = readBytes(Int(length)) else {
                    BleLogger.trace("\(TAG): extractValueForKey: parse error at \(startPos) — stopping")
                    return result
                }
                if key == kvKey {
                    result = (cmd == CMD_WRITE_BYTES) ? data : (result ?? []) + data
                }

            case CMD_REMOVE:
                guard let key = readUInt32() else { return result }
                if key == kvKey { result = nil }

            case CMD_COPY, CMD_MOVE:
                guard readUInt32() != nil, readUInt32() != nil else { return result }

            case CMD_COMMIT:
                break // no payload

            case CMD_WRITE_BYTES_EX, CMD_APPEND_BYTES_EX:
                guard let key = readUInt32() else { return result }
                guard pos < script.count else { return result }
                let idxLen = Int(script[pos]); pos += 1
                guard let _ = readBytes(idxLen), let length = readUInt32(), let data = readBytes(Int(length)) else { return result }
                if key == kvKey && idxLen == 0 {
                    result = (cmd == CMD_WRITE_BYTES_EX) ? data : (result ?? []) + data
                }

            case CMD_REMOVE_EX:
                guard let key = readUInt32() else { return result }
                guard pos < script.count else { return result }
                let idxLen = Int(script[pos]); pos += 1
                guard readBytes(idxLen) != nil else { return result }
                if key == kvKey && idxLen == 0 { result = nil }

            default:
                BleLogger.trace("\(TAG): extractValueForKey: unknown command 0x\(String(cmd, radix: 16)) @ \(startPos) — stopping")
                return result
            }
        }
        return result
    }

    // MARK: - Helpers

    static func u32Le(_ value: UInt32) -> [UInt8] {
        return [
            UInt8(value & 0xFF),
            UInt8((value >> 8) & 0xFF),
            UInt8((value >> 16) & 0xFF),
            UInt8((value >> 24) & 0xFF)
        ]
    }
}

