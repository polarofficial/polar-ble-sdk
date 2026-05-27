// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

final class FlatBufferBuilderTests: XCTestCase {

    // MARK: - Empty table (no fields set)

    func test_emptyTable_producesFourByteBuffer() {
        let builder = FlatBufferBuilder(initialSize: 64)
        builder.startTable(numFields: 3)
        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        let bytes = builder.sizedByteArray()

        // Must be parseable: root offset at position 0
        XCTAssertGreaterThanOrEqual(bytes.count, 8, "Buffer must hold root offset + vtable + object")
        let rootOffset = Int(readU32LE(bytes, at: 0))
        XCTAssertLessThan(rootOffset, bytes.count)
    }

    // MARK: - Single Int32 field round-trip

    func test_singleInt32Field_roundTrip() {
        let builder = FlatBufferBuilder(initialSize: 64)
        builder.startTable(numFields: 1)
        builder.addInt32(field: 0, value: 0x12345678, defaultValue: 0)
        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        let bytes = builder.sizedByteArray()

        let readback = readInt32Field(bytes, fieldIndex: 0)
        XCTAssertEqual(readback, 0x12345678)
    }

    func test_singleInt32Field_defaultNotWritten() {
        let builder = FlatBufferBuilder(initialSize: 64)
        builder.startTable(numFields: 1)
        builder.addInt32(field: 0, value: 0, defaultValue: 0)   // equals default → omitted
        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        let bytes = builder.sizedByteArray()

        let readback = readInt32Field(bytes, fieldIndex: 0)
        XCTAssertNil(readback, "Field equal to default should be absent")
    }

    // MARK: - Int16 field

    func test_int16Field_roundTrip() {
        let builder = FlatBufferBuilder(initialSize: 64)
        builder.startTable(numFields: 2)
        builder.addInt16(field: 1, value: 0x0ABC, defaultValue: 0)
        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        let bytes = builder.sizedByteArray()

        let readback = readInt16Field(bytes, fieldIndex: 1)
        XCTAssertEqual(readback, 0x0ABC)
    }

    // MARK: - Byte field

    func test_byteField_roundTrip() {
        let builder = FlatBufferBuilder(initialSize: 64)
        builder.startTable(numFields: 1)
        builder.addByte(field: 0, value: 7, defaultValue: 0)
        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        let bytes = builder.sizedByteArray()

        let readback = readByteField(bytes, fieldIndex: 0)
        XCTAssertEqual(readback, 7)
    }

    // MARK: - Int32 vector round-trip

    func test_int32Vector_roundTrip() {
        let values: [Int32] = [10, 20, 30, -1]
        let builder = FlatBufferBuilder(initialSize: 128)

        builder.startVector(elemSize: 4, count: values.count, alignment: 4)
        for v in values.reversed() { builder.addRawInt32(v) }
        let vecOffset = builder.endVector(count: values.count)

        builder.startTable(numFields: 1)
        builder.addOffset(field: 0, value: vecOffset, defaultValue: 0)
        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        let bytes = builder.sizedByteArray()

        let readback = readInt32Vector(bytes, fieldIndex: 0)
        XCTAssertEqual(readback, values)
    }

    func test_emptyVector_roundTrip() {
        let builder = FlatBufferBuilder(initialSize: 64)

        builder.startVector(elemSize: 4, count: 0, alignment: 4)
        let vecOffset = builder.endVector(count: 0)

        builder.startTable(numFields: 1)
        builder.addOffset(field: 0, value: vecOffset, defaultValue: 0)
        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        let bytes = builder.sizedByteArray()

        let readback = readInt32Vector(bytes, fieldIndex: 0)
        XCTAssertEqual(readback, [])
    }

    // MARK: - Multiple fields

    func test_multipleFields_allPreserved() {
        let builder = FlatBufferBuilder(initialSize: 128)
        builder.startTable(numFields: 3)
        builder.addInt16(field: 0, value: 0x00AB, defaultValue: 0)
        builder.addInt32(field: 1, value: Int32(bitPattern: 0xDEAD_BEEF), defaultValue: 0)
        builder.addByte(field: 2, value: 0x03, defaultValue: 0)
        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        let bytes = builder.sizedByteArray()

        XCTAssertEqual(readInt16Field(bytes, fieldIndex: 0), 0x00AB)
        XCTAssertEqual(readInt32Field(bytes, fieldIndex: 1), Int32(bitPattern: 0xDEAD_BEEF))
        XCTAssertEqual(readByteField(bytes, fieldIndex: 2), 0x03)
    }

    // MARK: - Buffer growth

    func test_bufferGrowth_smallInitialSize() {
        // Start with a tiny buffer to exercise growth
        let builder = FlatBufferBuilder(initialSize: 4)
        builder.startVector(elemSize: 4, count: 10, alignment: 4)
        for i in (0..<10).reversed() { builder.addRawInt32(Int32(i)) }
        let vecOffset = builder.endVector(count: 10)

        builder.startTable(numFields: 1)
        builder.addOffset(field: 0, value: vecOffset, defaultValue: 0)
        let tableOffset = builder.endTable()
        builder.finish(rootTable: tableOffset)
        let bytes = builder.sizedByteArray()

        let readback = readInt32Vector(bytes, fieldIndex: 0)
        XCTAssertEqual(readback, Array(0..<10).map { Int32($0) })
    }

    // MARK: - Helpers

    private func readU32LE(_ buf: [UInt8], at pos: Int) -> UInt32 {
        UInt32(buf[pos]) | UInt32(buf[pos+1]) << 8 | UInt32(buf[pos+2]) << 16 | UInt32(buf[pos+3]) << 24
    }

    private func readI32LE(_ buf: [UInt8], at pos: Int) -> Int32 {
        Int32(bitPattern: readU32LE(buf, at: pos))
    }

    private func readU16LE(_ buf: [UInt8], at pos: Int) -> UInt16 {
        UInt16(buf[pos]) | UInt16(buf[pos+1]) << 8
    }

    /// Returns the rootOffset → table start → vtable → field offset, resolving a scalar Int32 field.
    private func readInt32Field(_ bytes: [UInt8], fieldIndex: Int) -> Int32? {
        let rootOffset = Int(readU32LE(bytes, at: 0))
        return readScalarField(bytes, rootOffset: rootOffset, fieldIndex: fieldIndex, size: 4).map {
            Int32(bitPattern: readU32LE(bytes, at: $0))
        }
    }

    private func readInt16Field(_ bytes: [UInt8], fieldIndex: Int) -> Int16? {
        let rootOffset = Int(readU32LE(bytes, at: 0))
        return readScalarField(bytes, rootOffset: rootOffset, fieldIndex: fieldIndex, size: 2).map {
            Int16(bitPattern: readU16LE(bytes, at: $0))
        }
    }

    private func readByteField(_ bytes: [UInt8], fieldIndex: Int) -> UInt8? {
        let rootOffset = Int(readU32LE(bytes, at: 0))
        return readScalarField(bytes, rootOffset: rootOffset, fieldIndex: fieldIndex, size: 1).map { bytes[$0] }
    }

    private func readInt32Vector(_ bytes: [UInt8], fieldIndex: Int) -> [Int32]? {
        let rootOffset = Int(readU32LE(bytes, at: 0))
        guard let fieldAbsOffset = readScalarField(bytes, rootOffset: rootOffset, fieldIndex: fieldIndex, size: 4) else { return nil }
        let vectorAbsOffset = fieldAbsOffset + Int(readI32LE(bytes, at: fieldAbsOffset))
        let count = Int(readI32LE(bytes, at: vectorAbsOffset))
        guard count >= 0 else { return nil }
        return (0..<count).map { readI32LE(bytes, at: vectorAbsOffset + 4 + $0 * 4) }
    }

    /// Resolves the absolute position of a field given rootOffset and fieldIndex via vtable.
    /// Returns nil if the field is absent (vtable offset == 0).
    private func readScalarField(_ bytes: [UInt8], rootOffset: Int, fieldIndex: Int, size: Int) -> Int? {
        let vtableRelOffset = Int(readI32LE(bytes, at: rootOffset))
        let vtablePos = rootOffset - vtableRelOffset
        let vtableSize = Int(readU16LE(bytes, at: vtablePos))
        let fieldCount = (vtableSize - 4) / 2
        guard fieldIndex < fieldCount else { return nil }
        let fo = Int(readU16LE(bytes, at: vtablePos + 4 + fieldIndex * 2))
        guard fo != 0 else { return nil }
        return rootOffset + fo
    }
}



