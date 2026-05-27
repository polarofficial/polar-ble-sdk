// Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarCompressionUtilsTest: XCTestCase {

    // MARK: - deflated – edge cases

    func testDeflated_emptyData_returnsNil() {
        XCTAssertNil(Data().deflated())
    }

    func testDeflated_nonEmptyData_returnsNonNilData() {
        let input = Data("Hello, Polar!".utf8)
        XCTAssertNotNil(input.deflated())
    }

    func testDeflated_nonEmptyData_compressedDataIsNotEmpty() {
        let input = Data("Hello, Polar!".utf8)
        let compressed = input.deflated()
        XCTAssertFalse(compressed?.isEmpty ?? true)
    }

    func testDeflated_singleByte_compresses() {
        let input = Data([0x42])
        XCTAssertNotNil(input.deflated())
    }

    func testDeflated_highlyRepetitiveData_producesSmallerOutput() {
        // Highly compressible: 10 000 identical bytes
        let input = Data(repeating: 0xAB, count: 10_000)
        let compressed = input.deflated()!
        XCTAssertLessThan(compressed.count, input.count,
            "Deflated repetitive data should be smaller than the original")
    }

    func testDeflated_randomLikeData_doesNotCrash() {
        // Incompressible data should still return a result (possibly slightly larger)
        let input = Data((0..<256).map { UInt8($0) })
        XCTAssertNotNil(input.deflated())
    }

    // MARK: - inflated – edge cases

    func testInflated_emptyData_returnsNil() {
        XCTAssertNil(Data().inflated())
    }

    func testInflated_nonZlibData_returnsNil() {
        // Random bytes that are not valid zlib stream
        let garbage = Data([0x00, 0x01, 0x02, 0x03, 0xFF])
        XCTAssertNil(garbage.inflated())
    }

    // MARK: - deflate → inflate round-trip

    func testRoundTrip_shortString_preservesContent() {
        let original = Data("Hello, Polar!".utf8)
        let roundTripped = original.deflated()?.inflated()
        XCTAssertEqual(original, roundTripped)
    }

    func testRoundTrip_longRepetitiveData_preservesContent() {
        let original = Data(repeating: 0xAB, count: 10_000)
        let roundTripped = original.deflated()?.inflated()
        XCTAssertEqual(original, roundTripped)
    }

    func testRoundTrip_binaryData_preservesContent() {
        let original = Data((0..<256).map { UInt8($0) })
        let roundTripped = original.deflated()?.inflated()
        XCTAssertEqual(original, roundTripped)
    }

    func testRoundTrip_utf8String_preservesContent() {
        let original = Data("The quick brown fox jumps over the lazy dog".utf8)
        let roundTripped = original.deflated()?.inflated()
        XCTAssertEqual(original, roundTripped)
    }

    func testRoundTrip_singleByte_preservesContent() {
        let original = Data([0x7F])
        let roundTripped = original.deflated()?.inflated()
        XCTAssertEqual(original, roundTripped)
    }

    func testRoundTrip_largeData_preservesContent() {
        // 100 KB of structured data
        let original = Data((0..<100_000).map { UInt8($0 % 256) })
        let roundTripped = original.deflated()?.inflated()
        XCTAssertEqual(original, roundTripped)
    }

    // MARK: - custom buffer size

    func testDeflated_customBufferSize_returnsNonNilData() {
        let input = Data("buffer size test".utf8)
        XCTAssertNotNil(input.deflated(64))
    }

    func testInflated_customBufferSize_returnsNonNilData() {
        let input = Data("buffer size test".utf8)
        let compressed = input.deflated()!
        XCTAssertNotNil(compressed.inflated(64))
    }

    func testRoundTrip_smallBuffer_preservesContent() {
        let original = Data("small buffer round-trip".utf8)
        let compressed = original.deflated(32)
        let roundTripped = compressed?.inflated(32)
        XCTAssertEqual(original, roundTripped)
    }

    func testRoundTrip_largeBuffer_preservesContent() {
        let original = Data(repeating: 0xCC, count: 5_000)
        let compressed = original.deflated(65_536)
        let roundTripped = compressed?.inflated(65_536)
        XCTAssertEqual(original, roundTripped)
    }
}
