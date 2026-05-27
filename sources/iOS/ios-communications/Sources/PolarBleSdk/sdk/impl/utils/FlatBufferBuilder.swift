// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation

// MARK: - FlatBufferBuilder

/// Builds FlatBuffers binary data.
///
/// Encodes tables with scalar fields (byte, int16, int32), int32 vectors and offset
/// fields following the FlatBuffers wire format. The buffer grows automatically and
/// the final bytes are retrieved via ``sizedByteArray()``.
internal class FlatBufferBuilder {
    private var buf: [UInt8]
    private var space: Int
    private var minalign: Int = 1

    // Table building state
    private var tableVtable: [Int] = []
    private var tableObjectStart: Int = 0

    init(initialSize: Int = 256) {
        buf = [UInt8](repeating: 0, count: initialSize)
        space = initialSize
    }

    // MARK: - Internal state

    var offset: Int { buf.count - space }

    private func growIfNeeded(_ needed: Int) {
        guard space < needed else { return }
        var newSize = buf.count
        while newSize - (buf.count - space) < needed {
            newSize = max(newSize * 2, needed + (buf.count - space))
        }
        var newBuf = [UInt8](repeating: 0, count: newSize)
        let newSpace = newSize - (buf.count - space)
        newBuf[newSpace...] = buf[space...]
        buf = newBuf
        space = newSpace
    }

    private func prep(_ size: Int, _ additional: Int) {
        if size > minalign { minalign = size }
        let off = offset + additional
        let alignSize = (~off + 1) & (size - 1)
        growIfNeeded(alignSize + size + additional)
        space -= alignSize
    }

    // MARK: - Raw put operations

    func putByte(_ x: UInt8) {
        space -= 1; buf[space] = x
    }

    private func putInt16(_ x: Int16) {
        let v = UInt16(bitPattern: x)
        space -= 2
        buf[space]   = UInt8(v & 0xFF)
        buf[space+1] = UInt8(v >> 8)
    }

    private func putInt32(_ x: Int32) {
        let v = UInt32(bitPattern: x)
        space -= 4
        buf[space]   = UInt8(v & 0xFF)
        buf[space+1] = UInt8((v >> 8)  & 0xFF)
        buf[space+2] = UInt8((v >> 16) & 0xFF)
        buf[space+3] = UInt8(v >> 24)
    }

    // MARK: - Aligned add operations

    func addRawInt32(_ x: Int32) { prep(4, 0); putInt32(x) }

    private func addRawInt16(_ x: Int16) { prep(2, 0); putInt16(x) }

    private func addRawByte(_ x: UInt8) { prep(1, 0); putByte(x) }

    private func addRawOffset(_ off: Int) {
        prep(4, 0)
        let stored = offset - off + 4
        putInt32(Int32(stored))
    }

    // MARK: - Slot tracking

    private func slot(_ fieldIdx: Int) {
        tableVtable[fieldIdx] = offset
    }

    // MARK: - Table field adders

    func addByte(field: Int, value: Int8, defaultValue: Int8) {
        if value != defaultValue { addRawByte(UInt8(bitPattern: value)); slot(field) }
    }

    func addInt16(field: Int, value: Int16, defaultValue: Int16) {
        if value != defaultValue { addRawInt16(value); slot(field) }
    }

    func addInt32(field: Int, value: Int32, defaultValue: Int32) {
        if value != defaultValue { addRawInt32(value); slot(field) }
    }

    func addOffset(field: Int, value: Int, defaultValue: Int) {
        if value != defaultValue { addRawOffset(value); slot(field) }
    }

    // MARK: - Vector

    func startVector(elemSize: Int, count: Int, alignment: Int) {
        prep(4, elemSize * count)
        prep(alignment, elemSize * count)
    }

    func endVector(count: Int) -> Int {
        putInt32(Int32(count))
        return offset
    }

    // MARK: - Table

    func startTable(numFields: Int) {
        tableVtable = [Int](repeating: 0, count: numFields)
        tableObjectStart = offset
    }

    func endTable() -> Int {
        // Write placeholder soffset (patched after vtable is written)
        prep(4, 0)
        putInt32(0)
        let objectOffset = offset

        // Trim trailing zero vtable entries
        var numFields = tableVtable.count
        while numFields > 0 && tableVtable[numFields - 1] == 0 {
            numFields -= 1
        }

        // Write field offsets in reverse order
        for i in stride(from: numFields - 1, through: 0, by: -1) {
            let fo: Int16 = tableVtable[i] != 0 ? Int16(objectOffset - tableVtable[i]) : 0
            addRawInt16(fo)
        }

        // Object data size and vtable byte size
        addRawInt16(Int16(objectOffset - tableObjectStart))
        let vtableByteSize = numFields * 2 + 4
        addRawInt16(Int16(vtableByteSize))

        let vtableOffset = offset

        // Patch the soffset field in the object header
        let soffsetPos = buf.count - objectOffset
        let sv = UInt32(bitPattern: Int32(vtableOffset - objectOffset))
        buf[soffsetPos]   = UInt8(sv & 0xFF)
        buf[soffsetPos+1] = UInt8((sv >> 8) & 0xFF)
        buf[soffsetPos+2] = UInt8((sv >> 16) & 0xFF)
        buf[soffsetPos+3] = UInt8(sv >> 24)

        return objectOffset
    }

    // MARK: - Finish

    func finish(rootTable: Int) {
        prep(minalign, 4)
        addRawOffset(rootTable)
    }

    // MARK: - Output

    func sizedByteArray() -> [UInt8] {
        return Array(buf[space...])
    }
}

