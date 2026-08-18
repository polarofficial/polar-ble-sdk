//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

class BlePmdClientTest: XCTestCase {

    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    var blePmdClient: BlePmdClient!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        blePmdClient = BlePmdClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        blePmdClient = nil
    }

    func testProcessControlPointResponseWhenStatusIsSuccess() throws {
        // Arrange
        // HEX: F0 01 00 00 00 00 00 00 70 FF
        // index    type                                data
        // 0:      Response code                        F0
        // 1...:   Data                                 01 00 00 00 00 00 00 70 FF
        let controlPointResponse = Data([
            0xF0,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x70, 0xFF
        ])
        let successErrCode = 0x00

        // Act
        blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: controlPointResponse, err: successErrCode)

        // Assert
        let data = try blePmdClient.pmdCpResponseQueue.pop()
        XCTAssertEqual(controlPointResponse, data)
    }

    func testProcessMeasurementStopControlPointCommand() async throws {
        // Arrange
        // HEX: 01 01 02
        // index    type                                data
        // 0:      Online Measurement Stopped           01
        // 1...:   Measurement types                    01 (PPG), 02 (ACC)
        let controlPointResponse = Data([0x01, 0x01, 0x02])
        let successErrCode = 0x00

        let ppgStream = blePmdClient.observePpg()
        let accStream = blePmdClient.observeAcc()
        let ppiStream = blePmdClient.observePpi()

        // Start tasks that consume the streams — they will unblock when streams close
        let ppgTask = Task<Error?, Never> {
            do { for try await _ in ppgStream {} } catch { return error }
            return nil
        }
        let accTask = Task<Error?, Never> {
            do { for try await _ in accStream {} } catch { return error }
            return nil
        }

        // Give tasks a moment to start iterating before the stop command arrives
        try await Task.sleep(nanoseconds: 20_000_000) // 20ms

        // Act
        blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: controlPointResponse, err: successErrCode)

        // Await the stream close errors
        let ppgError = await ppgTask.value
        let accError = await accTask.value

        // Assert PPG stream closed with bleOnlineStreamClosed
        XCTAssertNotNil(ppgError, "PPG stream should have closed with an error")
        guard case BlePmdError.bleOnlineStreamClosed = ppgError! else {
            return XCTFail("Expected bleOnlineStreamClosed for PPG, got \(ppgError!)")
        }

        // Assert ACC stream closed with bleOnlineStreamClosed
        XCTAssertNotNil(accError, "ACC stream should have closed with an error")
        guard case BlePmdError.bleOnlineStreamClosed = accError! else {
            return XCTFail("Expected bleOnlineStreamClosed for ACC, got \(accError!)")
        }

        // Assert PPI stream was NOT closed (type 0x03 was not in the stop command)
        // Race the stream against a short timeout — timeout should win (stream still open)
        let ppiClosedWithError = await withTaskGroup(of: Bool.self) { group in
            group.addTask {
                do { for try await _ in ppiStream {} } catch { return true }
                return false
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: 50_000_000) // 50ms timeout
                return false
            }
            let result = await group.next() ?? false
            group.cancelAll()
            return result
        }
        XCTAssertFalse(ppiClosedWithError, "PPI stream should NOT have been closed by the stop command")
    }

    // MARK: - processPmdData error handling tests

    func testProcessPmdData_tooShortFrame_doesNotCrash() {
        // Arrange – frame shorter than MIN_FRAME_SIZE (10 bytes)
        let tooShortData = Data([0x02, 0x00, 0x94])

        // Act & Assert – must not crash / throw
        blePmdClient.processServiceData(BlePmdClient.PMD_DATA, data: tooShortData, err: 0)
    }

    func testProcessPmdData_emptyFrame_doesNotCrash() {
        // Arrange
        let emptyData = Data()

        // Act & Assert – must not crash / throw
        blePmdClient.processServiceData(BlePmdClient.PMD_DATA, data: emptyData, err: 0)
    }

    func testProcessPmdData_exactlyMinSize_doesNotCrash() {
        // Arrange – exactly 10 bytes (valid header, empty data content, ACC type)
        let minData = Data([
            0x02,                                    // measurement type: ACC
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,  // timestamp
            0x00                                     // frame type 0, raw
        ])

        // Act & Assert – should not crash (empty data content produces 0 samples)
        blePmdClient.processServiceData(BlePmdClient.PMD_DATA, data: minData, err: 0)
    }

    func testProcessPmdData_accStreamReceivesError_whenDataContentMisaligned() async throws {
        // Arrange – valid 10-byte header, but data content has 5 bytes (not a multiple of 6
        // for ACC raw TYPE_1: 2 bytes/channel * 3 channels = 6 bytes per sample)
        let header = Data([
            0x02,                                    // ACC
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,  // timestamp
            0x01                                     // frame type 1, raw
        ])
        let misalignedContent = Data([0x01, 0x02, 0x03, 0x04, 0x05]) // 5 bytes – not multiple of 6
        let frameData = header + misalignedContent

        let accStream = blePmdClient.observeAcc()

        let accTask = Task<Error?, Never> {
            do { for try await _ in accStream {} } catch { return error }
            return nil
        }

        try await Task.sleep(nanoseconds: 20_000_000) // 20ms

        // Act
        blePmdClient.processServiceData(BlePmdClient.PMD_DATA, data: frameData, err: 0)

        // The stream should close with an error due to misaligned data
        let accError = await accTask.value
        XCTAssertNotNil(accError, "ACC stream should have closed with an error for misaligned data")
    }
    
    // MARK: - parseDeltaFrameRefSamples Tests
    
    func testParseDeltaFrameRefSamples_withValidData_shouldReturnSamples() {
        // Arrange: Valid data with 3 channels, 16-bit resolution (2 bytes per channel)
        let channels: UInt8 = 3
        let resolution: UInt8 = 16
        let data = Data([
            0x01, 0x00,  // Channel 1: 1
            0x02, 0x00,  // Channel 2: 2
            0x03, 0x00   // Channel 3: 3
        ])
        
        // Act
        let result = Pmd.parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertEqual(result.count, 3, "Should return 3 samples")
        XCTAssertEqual(result[0], 1)
        XCTAssertEqual(result[1], 2)
        XCTAssertEqual(result[2], 3)
    }
    
    func testParseDeltaFrameRefSamples_withZeroResolution_shouldReturnEmpty() {
        // Arrange: Zero resolution (invalid) - Tests the first guard we added
        let channels: UInt8 = 3
        let resolution: UInt8 = 0
        let data = Data([0x01, 0x02, 0x03])
        
        // Act
        let result = Pmd.parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertTrue(result.isEmpty, "Should return empty array for zero resolution to prevent crash")
    }
    
    func testParseDeltaFrameRefSamples_withInsufficientData_shouldReturnEmpty() {
        // Arrange: Data too short for requested channels/resolution - Tests the second guard
        let channels: UInt8 = 3
        let resolution: UInt8 = 16  // Needs 6 bytes total (3 channels × 2 bytes)
        let data = Data([0x01, 0x02, 0x03, 0x04])  // Only 4 bytes
        
        // Act
        let result = Pmd.parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertTrue(result.isEmpty, "Should return empty array when data is too short to prevent index out of bounds crash")
    }
    
    func testParseDeltaFrameRefSamples_withEmptyData_shouldReturnEmpty() {
        // Arrange: Empty data
        let channels: UInt8 = 3
        let resolution: UInt8 = 16
        let data = Data()
        
        // Act
        let result = Pmd.parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertTrue(result.isEmpty, "Should return empty array for empty data")
    }
    
    func testParseDeltaFrameRefSamples_with8BitResolution_shouldWork() {
        // Arrange: 8-bit resolution (1 byte per channel)
        let channels: UInt8 = 3
        let resolution: UInt8 = 8
        let data = Data([0x0A, 0x14, 0x1E])  // 10, 20, 30
        
        // Act
        let result = Pmd.parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertEqual(result.count, 3)
        XCTAssertEqual(result[0], 10)
        XCTAssertEqual(result[1], 20)
        XCTAssertEqual(result[2], 30)
    }
    
    func testParseDeltaFrameRefSamples_with24BitResolution_shouldWork() {
        // Arrange: 24-bit resolution (3 bytes per channel)
        let channels: UInt8 = 2
        let resolution: UInt8 = 24
        let data = Data([
            0x01, 0x02, 0x03,  // Channel 1
            0x04, 0x05, 0x06   // Channel 2
        ])
        
        // Act
        let result = Pmd.parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertEqual(result.count, 2, "Should return 2 samples for 2 channels")
    }
    
    // MARK: - parseDeltaFramesToSamples Tests
    
    func testParseDeltaFramesToSamples_withValidData_shouldReturnSamples() {
        // Arrange: Valid delta frame data
        let channels: UInt8 = 3
        let resolution: UInt8 = 16
        
        // Reference samples (6 bytes for 3 channels × 2 bytes)
        var data = Data([
            0x64, 0x00,  // Channel 1: 100
            0xC8, 0x00,  // Channel 2: 200
            0x2C, 0x01   // Channel 3: 300
        ])
        
        // Delta frame header + data
        data.append(contentsOf: [
            0x08,  // deltaSize: 8 bits
            0x01   // sampleCount: 1
        ])
        data.append(contentsOf: [0x01, 0x02, 0x03])  // Deltas: +1, +2, +3
        
        // Act
        let result = Pmd.parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertEqual(result.count, 2, "Should have reference + 1 delta sample")
        XCTAssertEqual(result[0], [100, 200, 300], "First sample should be reference")
        XCTAssertEqual(result[1], [101, 202, 303], "Second sample should apply deltas")
    }
    
    func testParseDeltaFramesToSamples_withEmptyRefSamples_shouldReturnEmpty() {
        // Arrange: Data that causes parseDeltaFrameRefSamples to return empty
        // This tests the CRITICAL guard we added to prevent crash
        let channels: UInt8 = 3
        let resolution: UInt8 = 0  // Invalid resolution causes empty refSamples
        let data = Data([0x01, 0x02, 0x03])
        
        // Act - Should NOT crash (this was the bug we fixed)
        let result = Pmd.parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertTrue(result.isEmpty, "Should return empty array when refSamples is empty (prevents index out of bounds crash)")
    }
    
    func testParseDeltaFramesToSamples_withInsufficientDataForRefSamples_shouldReturnEmpty() {
        // Arrange: Insufficient data for reference samples - Tests the integration
        let channels: UInt8 = 3
        let resolution: UInt8 = 16
        let data = Data([0x01, 0x02])  // Only 2 bytes, needs 6
        
        // Act - Should NOT crash
        let result = Pmd.parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertTrue(result.isEmpty, "Should return empty when reference samples cannot be parsed (prevents crash)")
    }
    
    func testParseDeltaFramesToSamples_withOnlyRefSamples_shouldReturnRefSamplesOnly() {
        // Arrange: Only reference samples, no delta frames
        let channels: UInt8 = 3
        let resolution: UInt8 = 16
        let data = Data([
            0x0A, 0x00,  // 10
            0x14, 0x00,  // 20
            0x1E, 0x00   // 30
        ])
        
        // Act
        let result = Pmd.parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertEqual(result.count, 1, "Should only have reference sample")
        XCTAssertEqual(result[0], [10, 20, 30])
    }
    
    func testParseDeltaFramesToSamples_withZeroDeltaSize_shouldSkipFrame() {
        // Arrange: Delta frame with deltaSize = 0 (invalid)
        let channels: UInt8 = 3
        let resolution: UInt8 = 16
        
        var data = Data([
            0x64, 0x00,  // Channel 1: 100
            0xC8, 0x00,  // Channel 2: 200
            0x2C, 0x01   // Channel 3: 300
        ])
        
        data.append(contentsOf: [
            0x00,  // deltaSize: 0 (invalid)
            0x01   // sampleCount: 1
        ])
        
        // Act
        let result = Pmd.parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertEqual(result.count, 1, "Should only have reference sample, skip invalid delta")
        XCTAssertEqual(result[0], [100, 200, 300])
    }
    
    func testParseDeltaFramesToSamples_withTruncatedHeader_shouldReturnPartialSamples() {
        // Arrange: Truncated delta frame header
        let channels: UInt8 = 3
        let resolution: UInt8 = 16
        
        var data = Data([
            0x64, 0x00,  // Channel 1: 100
            0xC8, 0x00,  // Channel 2: 200
            0x2C, 0x01   // Channel 3: 300
        ])
        
        data.append(0x08)  // Only deltaSize, missing sampleCount
        
        // Act
        let result = Pmd.parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertEqual(result.count, 1, "Should return reference samples and stop gracefully")
    }
    
    // MARK: - parseDeltaFrame Tests
    
    func testParseDeltaFrame_withZeroBitWidth_shouldReturnEmpty() {
        // Arrange: Zero bitWidth (invalid) - Tests existing guard
        let data = Data([0x01, 0x02, 0x03])
        let channels: UInt32 = 3
        let bitWidth: UInt32 = 0
        let totalBitLength: UInt32 = 24
        
        // Act
        let result = Pmd.parseDeltaFrame(data, channels: channels, bitWidth: bitWidth, totalBitLength: totalBitLength)
        
        // Assert
        XCTAssertTrue(result.isEmpty, "Should return empty array for zero bitWidth to prevent crash")
    }
    
    func testParseDeltaFrame_withValidData_shouldReturnSamples() {
        // Arrange: Valid delta frame
        let data = Data([0xFF])  // 11111111 in binary
        let channels: UInt32 = 1
        let bitWidth: UInt32 = 8
        let totalBitLength: UInt32 = 8
        
        // Act
        let result = Pmd.parseDeltaFrame(data, channels: channels, bitWidth: bitWidth, totalBitLength: totalBitLength)
        
        // Assert
        XCTAssertFalse(result.isEmpty, "Should return samples for valid data")
        XCTAssertEqual(result.count, 1, "Should have one sample")
        XCTAssertEqual(result[0].count, 1, "Sample should have one channel")
    }
    
    // MARK: - Regression Tests for Crash Scenarios
    
    func testRegressionCrash_emptyRefSamplesWithDeltaFrames() {
        // Arrange: This specifically tests the bug that was fixed:
        // Empty refSamples + delta frames = index out of bounds crash
        let channels: UInt8 = 3
        let resolution: UInt8 = 0  // Causes empty refSamples
        
        var data = Data([0xFF, 0xFF, 0xFF])
        data.append(contentsOf: [0x08, 0x01, 0x01, 0x01, 0x01])
        
        // Act - Should NOT crash (this was the critical bug)
        let result = Pmd.parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertTrue(result.isEmpty, "Should handle gracefully without crashing")
    }
    
    func testRegressionCrash_insufficientRefDataWithDeltaFrames() {
        // Arrange: Another crash scenario: insufficient data for ref + delta frames
        let channels: UInt8 = 3
        let resolution: UInt8 = 16
        let data = Data([0x01, 0x02, 0x08, 0x01])  // Not enough for 3 channels
        
        // Act - Should NOT crash
        let result = Pmd.parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertTrue(result.isEmpty, "Should handle gracefully without crashing")
    }
    
    func testEdgeCase_singleChannel() {
        // Arrange: Single channel data
        let channels: UInt8 = 1
        let resolution: UInt8 = 16
        let data = Data([0x64, 0x00])  // 100
        
        // Act
        let result = Pmd.parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0], 100)
    }
    
    func testEdgeCase_maxChannels() {
        // Arrange: Maximum realistic channels (e.g., PPG with many channels)
        let channels: UInt8 = 10
        let resolution: UInt8 = 8
        var data = Data()
        for i in 0..<channels {
            data.append(UInt8(i))
        }
        
        // Act
        let result = Pmd.parseDeltaFrameRefSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertEqual(result.count, Int(channels))
    }
    
    func testEdgeCase_multipleConsecutiveDeltaFrames() {
        // Arrange: Multiple delta frames to test accumulation
        let channels: UInt8 = 3
        let resolution: UInt8 = 16
        
        var data = Data([
            0x64, 0x00,  // Channel 1: 100
            0xC8, 0x00,  // Channel 2: 200
            0x2C, 0x01   // Channel 3: 300
        ])
        
        // First delta frame
        data.append(contentsOf: [0x08, 0x02])  // deltaSize: 8, sampleCount: 2
        data.append(contentsOf: [0x01, 0x02, 0x03,  // +1, +2, +3
                                  0x01, 0x02, 0x03]) // +1, +2, +3
        
        // Second delta frame
        data.append(contentsOf: [0x08, 0x01])  // deltaSize: 8, sampleCount: 1
        data.append(contentsOf: [0x01, 0x01, 0x01])  // +1, +1, +1
        
        // Act
        let result = Pmd.parseDeltaFramesToSamples(data, channels: channels, resolution: resolution)
        
        // Assert
        XCTAssertGreaterThan(result.count, 1, "Should process multiple delta frames")
        XCTAssertEqual(result[0], [100, 200, 300], "Reference samples")
    }
}
