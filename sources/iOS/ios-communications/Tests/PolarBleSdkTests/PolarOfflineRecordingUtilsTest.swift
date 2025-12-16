//  Copyright © 2025 Polar. All rights reserved.

import XCTest
import RxSwift
import RxBlocking
@testable import PolarBleSdk

final class PolarOfflineRecordingUtilsTest: XCTestCase {

    private var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockGattServiceTransmitterImpl())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testListOfflineRecordingsV1_mergesSplitRecFiles() throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [
            ("/U/0/20250730/R/101010/ACC0.REC", 500_120),
            ("/U/0/20250730/R/101010/ACC1.REC", 500_103),
            ("/U/0/20250730/R/101010/ACC2.REC", 102_325),
            ("/U/0/20250730/R/101010/HR0.REC", 500_000),
            ("/U/0/20250730/R/101010/HR1.REC", 500_050),
            ("/U/0/20250730/R/101010/PPG0.REC", 300)
        ]

        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool)
            -> Observable<(String, UInt)> = { _, _, _ in
            Observable.from(sampleEntries)
        }

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient,
            fetchRecursively: fetchRecursively
        )
        .toBlocking()
        .toArray()

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries = emitted.filter { $0.path.contains("HR") }
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

    func testListOfflineRecordingsV1_returns_empty_when_regex_fails() throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [
            ("/U/0/2025073/R/101010/PPG0.REC", 300)
        ]

        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool)
            -> Observable<(String, UInt)> = { _, _, _ in
            Observable.from(sampleEntries)
        }

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient,
            fetchRecursively: fetchRecursively
        )
        .toBlocking()
        .toArray()

        // Assert
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(ppgEntries.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_date_parsing_fails() throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [
            ("/U/0/99999999/R/101010/PPG0.REC", 300)
        ]

        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool)
            -> Observable<(String, UInt)> = { _, _, _ in
            Observable.from(sampleEntries)
        }

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient,
            fetchRecursively: fetchRecursively
        )
        .toBlocking()
        .toArray()

        // Assert
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(ppgEntries.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_time_parsing_fails() throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [
            ("/U/0/20250730/R/999999/PPG0.REC", 300)
        ]

        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool)
            -> Observable<(String, UInt)> = { _, _, _ in
            Observable.from(sampleEntries)
        }

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient,
            fetchRecursively: fetchRecursively
        )
        .toBlocking()
        .toArray()

        // Assert
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(ppgEntries.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_meas_type_parsing_fails() throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [
            ("/U/0/20250730/R/101010/ZZZ9.REC", 300)
        ]

        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool)
            -> Observable<(String, UInt)> = { _, _, _ in
            Observable.from(sampleEntries)
        }

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient,
            fetchRecursively: fetchRecursively
        )
        .toBlocking()
        .toArray()

        // Assert
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(ppgEntries.count, 0)
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

        let mockGetFile: (BlePsFtpClient, String) -> Single<Data> = { _, path in
            XCTAssertEqual(path, "/PMDFILES.TXT")
            return Single.just(pmdTxt)
        }

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV2(
            client: mockClient,
            getFile: mockGetFile
        )
        .toBlocking()
        .single()

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries = emitted.filter { $0.path.contains("HR") }
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
}
