// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest

final class SyncLogStoreTests: XCTestCase {
    private var directory: URL!
    private var logURL: URL { directory.appendingPathComponent("sync_log.txt") }

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try FileManager.default.removeItem(at: directory)
    }

    func testAppendPreservesExistingBytesAndReloadsLegacyAndNewRecordsNewestFirst() throws {
        let now = Date()
        let timestamp = Int64(now.timeIntervalSince1970 * 1000)
        let legacy = "\(timestamp - 1000)|newer legacy\n\(timestamp - 2000)|older legacy\n"
        try Data(legacy.utf8).write(to: logURL)
        let store = SyncLogStore(logFileURL: logURL, now: { now })
        let message = "SYNC [12:00:00.000] [sensor] PHASE:FETCH — background diagnostic\nsecond line"
        store.addEntry(message)

        let disk = try String(contentsOf: logURL, encoding: .utf8)
        XCTAssertTrue(disk.hasPrefix(legacy), "Appending must not rewrite the existing journal")
        XCTAssertEqual(disk.split(separator: "\n").count, 3)
        let restored = SyncLogStore(logFileURL: logURL, now: { now })
        XCTAssertEqual(restored.getAllMessages(), [message, "newer legacy", "older legacy"])
    }

    func testDiskHistoryIsNotTruncatedToUILimit() throws {
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        let records = (0..<5001).map { "\(timestamp - Int64($0))|record \($0)\n" }.joined()
        try Data(records.utf8).write(to: logURL)
        let store = SyncLogStore(logFileURL: logURL)
        XCTAssertEqual(store.count, 5000)
        store.addEntry("latest")
        XCTAssertEqual(try String(contentsOf: logURL, encoding: .utf8).split(separator: "\n").count, 5002)
        XCTAssertEqual(SyncLogStore(logFileURL: logURL).count, 5000)
    }

    func testRetentionPrunesExpiredRecordsAtLoadAndDuringLongRunningSession() throws {
        var now = Date()
        let expired = Int64(now.addingTimeInterval(-31 * 86400).timeIntervalSince1970 * 1000)
        let recent = Int64(now.timeIntervalSince1970 * 1000)
        try Data("\(expired)|expired\n\(recent)|retained\n".utf8).write(to: logURL)
        let store = SyncLogStore(logFileURL: logURL, now: { now })
        XCTAssertEqual(store.getAllMessages(), ["retained"])
        XCTAssertFalse(try String(contentsOf: logURL, encoding: .utf8).contains("expired"))
        now = now.addingTimeInterval(31 * 86400)
        store.addEntry("new session data")
        XCTAssertEqual(store.getAllMessages(), ["new session data"])
        XCTAssertFalse(try String(contentsOf: logURL, encoding: .utf8).contains("retained"))
    }

    func testDisplayRowsAreReusedAcrossRefreshesAndDiagnosticsRemainStored() {
        let store = SyncLogStore(logFileURL: logURL)
        store.addEntry("ordinary event")
        let first = store.getSyncLogEntries()
        store.addEntry("SYNC [12:00:00.000] [sensor] PHASE:FETCH — diagnostic")
        store.addEntry("POLARBAT|battery diagnostic")
        store.addEntry("PLOG|2026-10-19 12:01:02.000|sensor|SYNC_START|")

        let refreshed = store.getSyncLogEntries()
        XCTAssertEqual(refreshed.map(\.message), ["Sync started", "ordinary event"])
        XCTAssertEqual(refreshed.last?.id, first.first?.id)
        XCTAssertEqual(store.getSyncLogEntries().map(\.id), refreshed.map(\.id))
        XCTAssertEqual(store.getAllMessages().count, 4)
        XCTAssertTrue(store.getDisplayEntries().first?.contains("[sensor] Sync started") == true)
    }

    func testInterruptedAppendDoesNotCorruptNextRecordAndClearRemovesHistory() throws {
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        try Data("\(timestamp)|complete\n\(timestamp)|partial".utf8).write(to: logURL)
        let store = SyncLogStore(logFileURL: logURL)
        store.addEntry("next")
        XCTAssertEqual(SyncLogStore(logFileURL: logURL).getAllMessages().sorted(), ["complete", "next"])
        store.clear()
        XCTAssertEqual(store.count, 0)
        XCTAssertTrue(store.getSyncLogEntries().isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: logURL.path))
        store.addEntry("after clear")
        XCTAssertEqual(SyncLogStore(logFileURL: logURL).getAllMessages(), ["after clear"])
    }
}
