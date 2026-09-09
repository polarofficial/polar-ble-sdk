// Copyright © 2026 Polar Electro Oy. All rights reserved.

/// Unit tests for parsing Derived Measurement PMD notifications.
/// Each test constructs raw PMD notification bytes, feeds them through
/// PmdDataFrame + DerivedAccData.parseDataFromDataFrame, and asserts every
/// slot value matches the expected encoded integer.
///
/// Timestamp used in all frames: 0x005ED0B2 (bytes LE: B2 D0 5E 00 00 00 00 00)
///
/// Derived-frame dataContent layout (headerSize=4, methodBitsOffset=2):
///   [source(1B)] [src_frame(1B)] [methodBitsLow(1B)] [methodBitsHigh(1B)] [sampleData...]

import XCTest
@testable import iOSCommunications

final class DerivedMeasurementDataTest: XCTestCase {

    // MARK: - Helpers

    private static let frameTimestamp: UInt64 = 0x000000005ED0B2

    /// Build a PmdDataFrame from a raw PMD notification byte array.
    private func makeFrame(_ bytes: [UInt8]) throws -> PmdDataFrame {
        return try PmdDataFrame(
            data: Data(bytes),
            { _, _ in 0 },   // previousTimeStamp – set to 0 so all slots share frame timestamp
            { _ in 1.0 },    // factor – not used by DerivedAccData parser
            { _ in 0 }       // sampleRate – not used by DerivedAccData parser
        )
    }

    // MARK: - Test Case 1: Gyroscope — Min, Std, Norm, StdOfNorms, NormOfStds

    func testDerivedGyroscopeTC1() throws {
        // [type(0F)] [timestamp 8B] [frame_type(00)] [source(05)] [src_frame(00)]
        // [method_bits LE: 32 03] [slot0: 18B] [slot1: 18B] [slot2: 18B]
        let rawBytes: [UInt8] = [
            0x0F,
            0xB2, 0xD0, 0x5E, 0x00, 0x00, 0x00, 0x00, 0x00,  // timestamp
            0x00,                                               // frame type = type_0
            0x05, 0x00, 0x32, 0x03,                           // source=gyro, src_frame=0, methodBits=0x0332
            // Slot 0 (samples 0–49)
            0x47, 0x00, 0xE9, 0xFF, 0xEE, 0xFF, // M1 Min  [x=71, y=-23, z=-18]
            0x16, 0x00, 0x49, 0x00, 0x21, 0x00, // M4 Std  [22, 73, 33]
            0x9A, 0x00,                          // M5 Norm [154]
            0x25, 0x00,                          // M8 StdOfNorms [37]
            0x54, 0x00,                          // M9 NormOfStds [84]
            // Slot 1 (samples 50–99)
            0x19, 0xFF, 0x05, 0xFF, 0x94, 0xFF, // M1 Min  [-231, -251, -108]
            0x8F, 0x00, 0x4F, 0x00, 0x1A, 0x00, // M4 Std  [143, 79, 26]
            0x46, 0x01,                          // M5 Norm [326]
            0x40, 0x00,                          // M8 StdOfNorms [64]
            0xA5, 0x00,                          // M9 NormOfStds [165]
            // Slot 2 (samples 100–149)
            0x1C, 0xFF, 0xF6, 0xFE, 0xE8, 0xFF, // M1 Min  [-228, -266, -24]
            0x4B, 0x00, 0x8D, 0x00, 0x21, 0x00, // M4 Std  [75, 141, 33]
            0xB6, 0x00,                          // M5 Norm [182]
            0x56, 0x00,                          // M8 StdOfNorms [86]
            0xA3, 0x00,                          // M9 NormOfStds [163]
        ]

        let frame = try makeFrame(rawBytes)
        XCTAssertEqual(frame.measurementType, .derivedMeasurement)

        let result = try DerivedAccData.parseDataFromDataFrame(frame: frame, activeMethods: [])

        XCTAssertEqual(result.derivedSamples.count, 3, "expected 3 slots")

        // --- Slot 0 ---
        let s0 = result.derivedSamples[0]
        // M1 Min (signed, 3D)
        XCTAssertEqual(s0.methodValues[1]?[0],   71, "slot0 min.x")
        XCTAssertEqual(s0.methodValues[1]?[1],  -23, "slot0 min.y")
        XCTAssertEqual(s0.methodValues[1]?[2],  -18, "slot0 min.z")
        // M4 Std (unsigned, 3D)
        XCTAssertEqual(s0.methodValues[4]?[0],   22, "slot0 std.x")
        XCTAssertEqual(s0.methodValues[4]?[1],   73, "slot0 std.y")
        XCTAssertEqual(s0.methodValues[4]?[2],   33, "slot0 std.z")
        // M5 Norm (unsigned, scalar) — last-sample norm
        XCTAssertEqual(s0.methodValues[5]?[0],  154, "slot0 norm")
        // M8 StdOfNorms (unsigned, scalar)
        XCTAssertEqual(s0.methodValues[8]?[0],   37, "slot0 stdOfNorms")
        // M9 NormOfStds (unsigned, scalar)
        XCTAssertEqual(s0.methodValues[9]?[0],   84, "slot0 normOfStds")

        // --- Slot 1 ---
        let s1 = result.derivedSamples[1]
        XCTAssertEqual(s1.methodValues[1]?[0], -231, "slot1 min.x")
        XCTAssertEqual(s1.methodValues[1]?[1], -251, "slot1 min.y")
        XCTAssertEqual(s1.methodValues[1]?[2], -108, "slot1 min.z")
        XCTAssertEqual(s1.methodValues[4]?[0],  143, "slot1 std.x")
        XCTAssertEqual(s1.methodValues[4]?[1],   79, "slot1 std.y")
        XCTAssertEqual(s1.methodValues[4]?[2],   26, "slot1 std.z")
        XCTAssertEqual(s1.methodValues[5]?[0],  326, "slot1 norm")
        XCTAssertEqual(s1.methodValues[8]?[0],   64, "slot1 stdOfNorms")
        XCTAssertEqual(s1.methodValues[9]?[0],  165, "slot1 normOfStds")

        // --- Slot 2 ---
        let s2 = result.derivedSamples[2]
        XCTAssertEqual(s2.methodValues[1]?[0], -228, "slot2 min.x")
        // -266 = 0xFEF6 → stored as two bytes F6 FE → signed int16 = -266 → Int32 = -266
        XCTAssertEqual(s2.methodValues[1]?[1], -266, "slot2 min.y")
        XCTAssertEqual(s2.methodValues[1]?[2],  -24, "slot2 min.z")
        XCTAssertEqual(s2.methodValues[4]?[0],   75, "slot2 std.x")
        XCTAssertEqual(s2.methodValues[4]?[1],  141, "slot2 std.y")
        XCTAssertEqual(s2.methodValues[4]?[2],   33, "slot2 std.z")
        XCTAssertEqual(s2.methodValues[5]?[0],  182, "slot2 norm")
        XCTAssertEqual(s2.methodValues[8]?[0],   86, "slot2 stdOfNorms")
        XCTAssertEqual(s2.methodValues[9]?[0],  163, "slot2 normOfStds")
    }

    // MARK: - Test Case 2: Acceleration — Downsample + Max + StdOfNorms

    func testDerivedAccelerationTC2() throws {
        let rawBytes: [UInt8] = [
            0x0F,
            0xB2, 0xD0, 0x5E, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00,
            0x02, 0x01, 0x05, 0x01,                           // source=acc, src_frame=1, methodBits=0x0105
            // Slot 0
            0x5A, 0x00, 0xBA, 0xFF, 0xCF, 0x03, // M0 Downsample [90, -70, 975]
            0x78, 0x00, 0xE2, 0xFF, 0xDE, 0x03, // M2 Max        [120, -30, 990]
            0x08, 0x00,                          // M8 StdOfNorms [8]
            // Slot 1
            0xA6, 0xFF, 0x3C, 0x00, 0xC5, 0x03, // M0 Downsample [-90, 60, 965]
            0xB0, 0xFF, 0x46, 0x00, 0xCA, 0x03, // M2 Max        [-80, 70, 970]
            0x06, 0x00,                          // M8 StdOfNorms [6]
            // Slot 2
            0x2D, 0x00, 0x69, 0x00, 0xE3, 0x03, // M0 Downsample [45, 105, 995]
            0x3C, 0x00, 0x6E, 0x00, 0xF2, 0x03, // M2 Max        [60, 110, 1010]
            0x07, 0x00,                          // M8 StdOfNorms [7]
        ]

        let frame = try makeFrame(rawBytes)
        let result = try DerivedAccData.parseDataFromDataFrame(frame: frame, activeMethods: [])

        XCTAssertEqual(result.derivedSamples.count, 3)

        // Slot 0
        let s0 = result.derivedSamples[0]
        XCTAssertEqual(s0.methodValues[0]?[0],   90, "slot0 downsample.x")
        XCTAssertEqual(s0.methodValues[0]?[1],  -70, "slot0 downsample.y")
        XCTAssertEqual(s0.methodValues[0]?[2],  975, "slot0 downsample.z")
        XCTAssertEqual(s0.methodValues[2]?[0],  120, "slot0 max.x")
        XCTAssertEqual(s0.methodValues[2]?[1],  -30, "slot0 max.y")
        XCTAssertEqual(s0.methodValues[2]?[2],  990, "slot0 max.z")
        XCTAssertEqual(s0.methodValues[8]?[0],    8, "slot0 stdOfNorms")

        // Slot 1
        let s1 = result.derivedSamples[1]
        XCTAssertEqual(s1.methodValues[0]?[0],  -90, "slot1 downsample.x")
        XCTAssertEqual(s1.methodValues[0]?[1],   60, "slot1 downsample.y")
        XCTAssertEqual(s1.methodValues[0]?[2],  965, "slot1 downsample.z")
        XCTAssertEqual(s1.methodValues[2]?[0],  -80, "slot1 max.x")
        XCTAssertEqual(s1.methodValues[2]?[1],   70, "slot1 max.y")
        XCTAssertEqual(s1.methodValues[2]?[2],  970, "slot1 max.z")
        XCTAssertEqual(s1.methodValues[8]?[0],    6, "slot1 stdOfNorms")

        // Slot 2
        let s2 = result.derivedSamples[2]
        XCTAssertEqual(s2.methodValues[0]?[0],   45, "slot2 downsample.x")
        XCTAssertEqual(s2.methodValues[0]?[1],  105, "slot2 downsample.y")
        XCTAssertEqual(s2.methodValues[0]?[2],  995, "slot2 downsample.z")
        XCTAssertEqual(s2.methodValues[2]?[0],   60, "slot2 max.x")
        XCTAssertEqual(s2.methodValues[2]?[1],  110, "slot2 max.y")
        XCTAssertEqual(s2.methodValues[2]?[2], 1010, "slot2 max.z")
        XCTAssertEqual(s2.methodValues[8]?[0],    7, "slot2 stdOfNorms")
    }

    // MARK: - Test Case 5: Acceleration — Downsample + Norm + StdOfNorms

    func testDerivedAccelerationTC5() throws {
        let rawBytes: [UInt8] = [
            0x0F,
            0xB2, 0xD0, 0x5E, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00,
            0x02, 0x01, 0x21, 0x01,                           // source=acc, src_frame=1, methodBits=0x0121
            // Slot 0
            0x5A, 0x00, 0xBA, 0xFF, 0xCF, 0x03, // M0 Downsample [90, -70, 975]
            0xD6, 0x03,                          // M5 Norm [982]
            0x08, 0x00,                          // M8 StdOfNorms [8]
            // Slot 1
            0xA6, 0xFF, 0x3C, 0x00, 0xC5, 0x03, // M0 Downsample [-90, 60, 965]
            0xCB, 0x03,                          // M5 Norm [971]
            0x06, 0x00,                          // M8 StdOfNorms [6]
            // Slot 2
            0x2D, 0x00, 0x69, 0x00, 0xE3, 0x03, // M0 Downsample [45, 105, 995]
            0xEA, 0x03,                          // M5 Norm [1002]
            0x07, 0x00,                          // M8 StdOfNorms [7]
        ]

        let frame = try makeFrame(rawBytes)
        let result = try DerivedAccData.parseDataFromDataFrame(frame: frame, activeMethods: [])

        XCTAssertEqual(result.derivedSamples.count, 3)

        // Slot 0
        let s0 = result.derivedSamples[0]
        XCTAssertEqual(s0.methodValues[0]?[0],   90, "slot0 downsample.x")
        XCTAssertEqual(s0.methodValues[0]?[1],  -70, "slot0 downsample.y")
        XCTAssertEqual(s0.methodValues[0]?[2],  975, "slot0 downsample.z")
        XCTAssertEqual(s0.methodValues[5]?[0],  982, "slot0 norm")
        XCTAssertEqual(s0.methodValues[8]?[0],    8, "slot0 stdOfNorms")

        // Slot 1
        let s1 = result.derivedSamples[1]
        XCTAssertEqual(s1.methodValues[0]?[0],  -90, "slot1 downsample.x")
        XCTAssertEqual(s1.methodValues[0]?[1],   60, "slot1 downsample.y")
        XCTAssertEqual(s1.methodValues[0]?[2],  965, "slot1 downsample.z")
        XCTAssertEqual(s1.methodValues[5]?[0],  971, "slot1 norm")
        XCTAssertEqual(s1.methodValues[8]?[0],    6, "slot1 stdOfNorms")

        // Slot 2
        let s2 = result.derivedSamples[2]
        XCTAssertEqual(s2.methodValues[0]?[0],   45, "slot2 downsample.x")
        XCTAssertEqual(s2.methodValues[0]?[1],  105, "slot2 downsample.y")
        XCTAssertEqual(s2.methodValues[0]?[2],  995, "slot2 downsample.z")
        XCTAssertEqual(s2.methodValues[5]?[0], 1002, "slot2 norm")
        XCTAssertEqual(s2.methodValues[8]?[0],    7, "slot2 stdOfNorms")
    }

    // MARK: - Test Case 6: Acceleration — All Methods (0,1,2,4,5,6,7,8)

    func testDerivedAccelerationTC6() throws {
        let rawBytes: [UInt8] = [
            0x0F,
            0xB2, 0xD0, 0x5E, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00,
            0x02, 0x01, 0xF7, 0x01,                           // source=acc, src_frame=1, methodBits=0x01F7
            // Slot 0 (last=(90,-70,975), norms=[986.36,997.70,975.14,991.93,981.64])
            0x5A, 0x00, 0xBA, 0xFF, 0xCF, 0x03, // M0 Downsample [90,  -70, 975]
            0x50, 0x00, 0xBA, 0xFF, 0xCA, 0x03, // M1 Min        [80,  -70, 970]
            0x78, 0x00, 0xE2, 0xFF, 0xDE, 0x03, // M2 Max        [120, -30, 990]
            0x0E, 0x00, 0x0E, 0x00, 0x07, 0x00, // M4 Std        [14,   14,   7]
            0xD6, 0x03,                          // M5 Norm       [982]
            0xCF, 0x03,                          // M6 MinOfNorms [975]
            0xE6, 0x03,                          // M7 MaxOfNorms [998]
            0x08, 0x00,                          // M8 StdOfNorms [8]
            // Slot 1 (last=(-90,60,965), norms=[966.49,958.02,975.81,962.15,971.04])
            0xA6, 0xFF, 0x3C, 0x00, 0xC5, 0x03, // M0 Downsample [-90,  60, 965]
            0x88, 0xFF, 0x1E, 0x00, 0xB6, 0x03, // M1 Min        [-120, 30, 950]
            0xB0, 0xFF, 0x46, 0x00, 0xCA, 0x03, // M2 Max        [-80,  70, 970]
            0x0E, 0x00, 0x0E, 0x00, 0x07, 0x00, // M4 Std        [14,   14,   7]
            0xCB, 0x03,                          // M5 Norm       [971]
            0xBE, 0x03,                          // M6 MinOfNorms [958]
            0xD0, 0x03,                          // M7 MaxOfNorms [976]
            0x06, 0x00,                          // M8 StdOfNorms [6]
            // Slot 2 (last=(45,105,995), norms=[1006.23,1015.78,996.90,1010.98,1001.54])
            0x2D, 0x00, 0x69, 0x00, 0xE3, 0x03, // M0 Downsample [45,  105, 995]
            0x28, 0x00, 0x5A, 0x00, 0xDE, 0x03, // M1 Min        [40,   90, 990]
            0x3C, 0x00, 0x6E, 0x00, 0xF2, 0x03, // M2 Max        [60,  110, 1010]
            0x07, 0x00, 0x07, 0x00, 0x07, 0x00, // M4 Std        [7,     7,   7]
            0xEA, 0x03,                          // M5 Norm       [1002]
            0xE5, 0x03,                          // M6 MinOfNorms [997]
            0xF8, 0x03,                          // M7 MaxOfNorms [1016]
            0x07, 0x00,                          // M8 StdOfNorms [7]
        ]

        let frame = try makeFrame(rawBytes)
        let result = try DerivedAccData.parseDataFromDataFrame(frame: frame, activeMethods: [])

        XCTAssertEqual(result.derivedSamples.count, 3)

        // --- Slot 0 ---
        let s0 = result.derivedSamples[0]
        XCTAssertEqual(s0.methodValues[0]?[0],   90, "slot0 downsample.x")
        XCTAssertEqual(s0.methodValues[0]?[1],  -70, "slot0 downsample.y")
        XCTAssertEqual(s0.methodValues[0]?[2],  975, "slot0 downsample.z")
        XCTAssertEqual(s0.methodValues[1]?[0],   80, "slot0 min.x")
        XCTAssertEqual(s0.methodValues[1]?[1],  -70, "slot0 min.y")
        XCTAssertEqual(s0.methodValues[1]?[2],  970, "slot0 min.z")
        XCTAssertEqual(s0.methodValues[2]?[0],  120, "slot0 max.x")
        XCTAssertEqual(s0.methodValues[2]?[1],  -30, "slot0 max.y")
        XCTAssertEqual(s0.methodValues[2]?[2],  990, "slot0 max.z")
        XCTAssertEqual(s0.methodValues[4]?[0],   14, "slot0 std.x")
        XCTAssertEqual(s0.methodValues[4]?[1],   14, "slot0 std.y")
        XCTAssertEqual(s0.methodValues[4]?[2],    7, "slot0 std.z")
        XCTAssertEqual(s0.methodValues[5]?[0],  982, "slot0 norm")
        XCTAssertEqual(s0.methodValues[6]?[0],  975, "slot0 minOfNorms")
        XCTAssertEqual(s0.methodValues[7]?[0],  998, "slot0 maxOfNorms")
        XCTAssertEqual(s0.methodValues[8]?[0],    8, "slot0 stdOfNorms")

        // --- Slot 1 ---
        let s1 = result.derivedSamples[1]
        XCTAssertEqual(s1.methodValues[0]?[0],  -90, "slot1 downsample.x")
        XCTAssertEqual(s1.methodValues[0]?[1],   60, "slot1 downsample.y")
        XCTAssertEqual(s1.methodValues[0]?[2],  965, "slot1 downsample.z")
        XCTAssertEqual(s1.methodValues[1]?[0], -120, "slot1 min.x")
        XCTAssertEqual(s1.methodValues[1]?[1],   30, "slot1 min.y")
        XCTAssertEqual(s1.methodValues[1]?[2],  950, "slot1 min.z")
        XCTAssertEqual(s1.methodValues[2]?[0],  -80, "slot1 max.x")
        XCTAssertEqual(s1.methodValues[2]?[1],   70, "slot1 max.y")
        XCTAssertEqual(s1.methodValues[2]?[2],  970, "slot1 max.z")
        XCTAssertEqual(s1.methodValues[4]?[0],   14, "slot1 std.x")
        XCTAssertEqual(s1.methodValues[4]?[1],   14, "slot1 std.y")
        XCTAssertEqual(s1.methodValues[4]?[2],    7, "slot1 std.z")
        XCTAssertEqual(s1.methodValues[5]?[0],  971, "slot1 norm")
        XCTAssertEqual(s1.methodValues[6]?[0],  958, "slot1 minOfNorms")
        XCTAssertEqual(s1.methodValues[7]?[0],  976, "slot1 maxOfNorms")
        XCTAssertEqual(s1.methodValues[8]?[0],    6, "slot1 stdOfNorms")

        // --- Slot 2 ---
        let s2 = result.derivedSamples[2]
        XCTAssertEqual(s2.methodValues[0]?[0],   45, "slot2 downsample.x")
        XCTAssertEqual(s2.methodValues[0]?[1],  105, "slot2 downsample.y")
        XCTAssertEqual(s2.methodValues[0]?[2],  995, "slot2 downsample.z")
        XCTAssertEqual(s2.methodValues[1]?[0],   40, "slot2 min.x")
        XCTAssertEqual(s2.methodValues[1]?[1],   90, "slot2 min.y")
        XCTAssertEqual(s2.methodValues[1]?[2],  990, "slot2 min.z")
        XCTAssertEqual(s2.methodValues[2]?[0],   60, "slot2 max.x")
        XCTAssertEqual(s2.methodValues[2]?[1],  110, "slot2 max.y")
        XCTAssertEqual(s2.methodValues[2]?[2], 1010, "slot2 max.z")
        XCTAssertEqual(s2.methodValues[4]?[0],    7, "slot2 std.x")
        XCTAssertEqual(s2.methodValues[4]?[1],    7, "slot2 std.y")
        XCTAssertEqual(s2.methodValues[4]?[2],    7, "slot2 std.z")
        XCTAssertEqual(s2.methodValues[5]?[0], 1002, "slot2 norm")
        XCTAssertEqual(s2.methodValues[6]?[0],  997, "slot2 minOfNorms")
        XCTAssertEqual(s2.methodValues[7]?[0], 1016, "slot2 maxOfNorms")
        XCTAssertEqual(s2.methodValues[8]?[0],    7, "slot2 stdOfNorms")
    }

    // MARK: - Frame-level properties

    /// The measurement type byte 0x0F is correctly identified as derivedMeasurement.
    func testDerivedFrameMeasurementType() throws {
        // Minimal valid derived frame with one int16 slot, single scalar method (bit 8 = StdOfNorms)
        let rawBytes: [UInt8] = [
            0x0F,                                               // derivedMeasurement
            0xB2, 0xD0, 0x5E, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00,                                               // frame type 0
            0x02, 0x01, 0x00, 0x01,                           // source=acc, src_frame=1, methodBits=0x0100 (bit 8 = StdOfNorms)
            0x05, 0x00,                                         // one uint16 scalar slot: value=5
        ]
        let frame = try makeFrame(rawBytes)
        XCTAssertEqual(frame.measurementType, .derivedMeasurement)
        XCTAssertEqual(frame.frameType, .type_0)

        let result = try DerivedAccData.parseDataFromDataFrame(frame: frame, activeMethods: [])
        XCTAssertEqual(result.derivedSamples.count, 1)
        XCTAssertEqual(result.derivedSamples[0].methodValues[8]?[0], 5)
    }

    /// An empty data content results in an empty samples array (no crash).
    func testEmptyDerivedFrameReturnsNoSamples() throws {
        let rawBytes: [UInt8] = [
            0x0F,
            0xB2, 0xD0, 0x5E, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00,
            // no content bytes at all
        ]
        let frame = try makeFrame(rawBytes)
        let result = try DerivedAccData.parseDataFromDataFrame(frame: frame, activeMethods: [])
        XCTAssertTrue(result.derivedSamples.isEmpty)
    }

    /// A derived frame with content shorter than the 4-byte header returns empty samples.
    func testTruncatedHeaderReturnsNoSamples() throws {
        let rawBytes: [UInt8] = [
            0x0F,
            0xB2, 0xD0, 0x5E, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00,
            0x02, 0x01, 0x05, // only 3 bytes — less than 4-byte header
        ]
        let frame = try makeFrame(rawBytes)
        let result = try DerivedAccData.parseDataFromDataFrame(frame: frame, activeMethods: [])
        XCTAssertTrue(result.derivedSamples.isEmpty)
    }

    // MARK: - Timestamp is carried through from the PMD frame

    /// Verifies that each slot receives the frame timestamp when previousTimeStamp == 0.
    func testTimestampsAreSetFromFrame() throws {
        // TC2 has 3 slots; with previousTimeStamp=0 all 3 should equal the frame timestamp.
        let rawBytes: [UInt8] = [
            0x0F,
            0xB2, 0xD0, 0x5E, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00,
            0x02, 0x01, 0x05, 0x01,                           // methodBits=0x0105
            // Slot 0
            0x5A, 0x00, 0xBA, 0xFF, 0xCF, 0x03,
            0x78, 0x00, 0xE2, 0xFF, 0xDE, 0x03,
            0x08, 0x00,
            // Slot 1
            0xA6, 0xFF, 0x3C, 0x00, 0xC5, 0x03,
            0xB0, 0xFF, 0x46, 0x00, 0xCA, 0x03,
            0x06, 0x00,
            // Slot 2
            0x2D, 0x00, 0x69, 0x00, 0xE3, 0x03,
            0x3C, 0x00, 0x6E, 0x00, 0xF2, 0x03,
            0x07, 0x00,
        ]

        let frame = try makeFrame(rawBytes)
        let expectedTs: UInt64 = 0x000000005ED0B2
        let result = try DerivedAccData.parseDataFromDataFrame(frame: frame, activeMethods: [])

        XCTAssertEqual(result.derivedSamples.count, 3)
        for (i, sample) in result.derivedSamples.enumerated() {
            XCTAssertEqual(sample.timeStamp, expectedTs, "slot \(i) timestamp")
        }
    }
}
