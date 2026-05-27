//  Copyright © 2025 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarOfflineRecordingUtilsTest: XCTestCase {

    private var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testListOfflineRecordingsV1_mergesSplitRecFiles() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [
            ("/U/0/20250730/R/101010/ACC0.REC", 500_120),
            ("/U/0/20250730/R/101010/ACC1.REC", 500_103),
            ("/U/0/20250730/R/101010/ACC2.REC", 102_325),
            ("/U/0/20250730/R/101010/HR0.REC", 500_000),
            ("/U/0/20250730/R/101010/HR1.REC", 500_050),
            ("/U/0/20250730/R/101010/PPG0.REC", 300)
        ]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient,
            fetchRecursively: fetchRecursively
        ))

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries  = emitted.filter { $0.path.contains("HR") }
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(accEntries.count, 1)
        XCTAssertEqual(accEntries.first?.size, 500_120 + 500_103 + 102_325)
        XCTAssertTrue(accEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(hrEntries.count, 1)
        XCTAssertEqual(hrEntries.first?.size, 500_000 + 500_050)
        XCTAssertTrue(hrEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(ppgEntries.count, 1)
        XCTAssertEqual(ppgEntries.first?.size, 300)
        XCTAssertTrue(ppgEntries.first?.path.hasSuffix(".REC") ?? false)

        emitted.forEach { XCTAssertNotNil($0.date) }
    }

    func testListOfflineRecordingsV1_returns_empty_when_regex_fails() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [("/U/0/2025073/R/101010/PPG0.REC", 300)]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        XCTAssertEqual(emitted.filter { $0.path.contains("PPG") }.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_date_parsing_fails() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [("/U/0/99999999/R/101010/PPG0.REC", 300)]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        XCTAssertEqual(emitted.filter { $0.path.contains("PPG") }.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_time_parsing_fails() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [("/U/0/20250730/R/999999/PPG0.REC", 300)]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        XCTAssertEqual(emitted.filter { $0.path.contains("PPG") }.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_meas_type_parsing_fails() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [("/U/0/20250730/R/101010/ZZZ9.REC", 300)]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        XCTAssertEqual(emitted.filter { $0.path.contains("PPG") }.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_empty_file() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [
            ("/U/0/20250730/R/101010/ACC0.REC", 500_120),
            ("/U/0/20250730/R/101010/ACC1.REC", 500_103),
            ("/U/0/20250730/R/101010/ACC2.REC", 0),
            ("/U/0/20250730/R/101010/HR0.REC", 500_000),
            ("/U/0/20250730/R/101010/HR1.REC", 0),
            ("/U/0/20250730/R/101010/PPG0.REC", 0)
        ]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries  = emitted.filter { $0.path.contains("HR") }
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(accEntries.count, 1)
        XCTAssertEqual(accEntries.first?.size, 500_120 + 500_103)
        XCTAssertTrue(accEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(hrEntries.count, 1)
        XCTAssertEqual(hrEntries.first?.size, 500_000)
        XCTAssertTrue(hrEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(ppgEntries.count, 0)

        emitted.forEach { XCTAssertNotNil($0.date) }
    }

    func testListOfflineRecordingsV2_mergesSplitRecFiles() throws {
        // Arrange
        let pmdTxt = """
        500120 /U/0/20250730/R/101010/ACC0.REC
        500103 /U/0/20250730/R/101010/ACC1.REC
        102325 /U/0/20250730/R/101010/ACC2.REC
        500000 /U/0/20250730/R/101010/HR0.REC
        500050 /U/0/20250730/R/101010/HR1.REC
        300 /U/0/20250730/R/101010/PPG0.REC
        """.data(using: .utf8)!

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV2(fileData: pmdTxt)

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries  = emitted.filter { $0.path.contains("HR") }
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(accEntries.count, 1)
        XCTAssertEqual(accEntries.first?.size, 500_120 + 500_103 + 102_325)
        XCTAssertTrue(accEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(hrEntries.count, 1)
        XCTAssertEqual(hrEntries.first?.size, 500_000 + 500_050)
        XCTAssertTrue(hrEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(ppgEntries.count, 1)
        XCTAssertEqual(ppgEntries.first?.size, 300)
        XCTAssertTrue(ppgEntries.first?.path.hasSuffix(".REC") ?? false)

        emitted.forEach { XCTAssertNotNil($0.date) }
    }

    func testListOfflineRecordingsV2_returns_empty_when_empty_file() throws {
        // Arrange
        let pmdTxt = """
        500120 /U/0/20250730/R/101010/ACC0.REC
        500103 /U/0/20250730/R/101010/ACC1.REC
        0 /U/0/20250730/R/101010/ACC2.REC
        500000 /U/0/20250730/R/101010/HR0.REC
        0 /U/0/20250730/R/101010/HR1.REC
        0 /U/0/20250730/R/101010/PPG0.REC
        """.data(using: .utf8)!

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV2(fileData: pmdTxt)

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries  = emitted.filter { $0.path.contains("HR") }
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(accEntries.count, 1)
        XCTAssertEqual(accEntries.first?.size, 500_120 + 500_103)
        XCTAssertTrue(accEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(hrEntries.count, 1)
        XCTAssertEqual(hrEntries.first?.size, 500_000)
        XCTAssertTrue(hrEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(ppgEntries.count, 0)

        emitted.forEach { XCTAssertNotNil($0.date) }
    }

    // MARK: - Helpers

    private func collectStream<T>(_ stream: AsyncThrowingStream<T, Error>) async throws -> [T] {
        var results: [T] = []
        for try await value in stream { results.append(value) }
        return results
    }
}
