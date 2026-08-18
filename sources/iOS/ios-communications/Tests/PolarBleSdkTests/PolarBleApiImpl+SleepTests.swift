// Copyright © 2026 Polar. All rights reserved.

import XCTest

@testable import PolarBleSdk

final class PolarBleApiImplSleepTests: XCTestCase {

    private let deviceId = "ABCDEF01"
    private var mockClient: MockBlePsFtpClient!
    private var mockSession: MockBleDeviceSession!
    private var api: PolarBleApiImplWithMockSession!

    override func setUpWithError() throws {
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: ["h10": .h10FileSystem],
            defaultFileSystemType: .polarFileSystemV2,
            defaultRecordingSupported: false
        )
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
    }

    override func tearDownWithError() throws {
        api = nil
        mockSession = nil
        mockClient = nil
    }

    func test_stopSleepRecording_success_checksServiceAndSendsStopEndpointNotification() async throws {
        try await api.stopSleepRecording(identifier: deviceId)

        XCTAssertEqual(mockClient.requestCalls.count, 1)
        let getOperation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
        XCTAssertEqual(getOperation.command, .get)
        XCTAssertEqual(getOperation.path, "/REST/SLEEP.API")

        XCTAssertEqual(mockClient.writeCalls.count, 1)
        let putOperation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.writeCalls[0].header as Data)
        XCTAssertEqual(putOperation.command, .put)
        XCTAssertEqual(putOperation.path, "/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording")
        XCTAssertEqual(readAll(from: mockClient.writeCalls[0].data), Data("{}".utf8))
    }

    func test_stopSleepRecording_whenServiceProbeFails_propagatesError() async {
        mockClient.requestReturnValue = .failure(NSError(domain: "sleep", code: 42))

        do {
            try await api.stopSleepRecording(identifier: deviceId)
            XCTFail("Expected error")
        } catch let error as PolarErrors {
            if case .deviceError = error {
                XCTAssertTrue(true)
            } else {
                XCTFail("Expected PolarErrors.deviceError, got \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func test_getSleepRecordingState_returnsLastValueFromFirstEventBatch() async throws {
        mockClient.receiveNotificationCalls = [
            (restApiNotificationId, [sleepRecordingEvent(enabled: 1), sleepRecordingEvent(enabled: 0)], false)
        ]

        let state = try await api.getSleepRecordingState(identifier: deviceId, timeoutMs: 2_000)

        XCTAssertFalse(state)
        XCTAssertEqual(mockClient.writeCalls.count, 1)
        let operation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.writeCalls[0].header as Data)
        XCTAssertEqual(operation.path, "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]")
    }

    func test_getSleepRecordingState_whenNotificationsHang_throwsTimeout() async {
        let hangingClient = HangingSleepNotificationClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        let hangingSession = MockBleDeviceSession(mockFtpClient: hangingClient)
        let hangingApi = PolarBleApiImplWithMockSession(mockDeviceSession: hangingSession)

        do {
            _ = try await hangingApi.getSleepRecordingState(identifier: deviceId, timeoutMs: 100)
            XCTFail("Expected timeout")
        } catch let error as PolarErrors {
            if case .timeout = error {
                XCTAssertTrue(true)
            } else {
                XCTFail("Expected PolarErrors.timeout, got \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func test_getSleepRecordingState_whenNotificationStreamFails_propagatesOriginalError() async {
        let expected = NSError(domain: "sleep.notification", code: 999)
        let failingClient = FailingSleepNotificationClient(
            gattServiceTransmitter: MockPolarGattServiceTransmitter(),
            waitNotificationError: expected
        )
        let failingSession = MockBleDeviceSession(mockFtpClient: failingClient)
        let failingApi = PolarBleApiImplWithMockSession(mockDeviceSession: failingSession)

        do {
            _ = try await failingApi.getSleepRecordingState(identifier: deviceId, timeoutMs: 2_000)
            XCTFail("Expected stream failure")
        } catch {
            let nsError = error as NSError
            XCTAssertEqual(nsError.domain, expected.domain)
            XCTAssertEqual(nsError.code, expected.code)
        }
    }

    func test_observeSleepRecordingState_subscribesAndYieldsMappedBooleanBatches() async throws {
        mockClient.receiveNotificationCalls = [
            (restApiNotificationId, [sleepRecordingEvent(enabled: 1), sleepRecordingEvent(enabled: 0)], false)
        ]

        var values: [[Bool]] = []
        for try await batch in api.observeSleepRecordingState(identifier: deviceId) {
            values.append(batch)
        }

        XCTAssertEqual(values.count, 1)
        XCTAssertEqual(values[0], [true, false])

        XCTAssertEqual(mockClient.requestCalls.count, 1)
        let getOperation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
        XCTAssertEqual(getOperation.path, "/REST/SLEEP.API")

        XCTAssertEqual(mockClient.writeCalls.count, 1)
        let subscribeOperation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.writeCalls[0].header as Data)
        XCTAssertEqual(subscribeOperation.command, .put)
        XCTAssertEqual(subscribeOperation.path, "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]")
        XCTAssertEqual(readAll(from: mockClient.writeCalls[0].data), Data("{}".utf8))
    }

    func test_getSleep_whenFromDateAfterToDate_throwsInvalidArgument() async {

        let from = makeUtcDate(year: 2026, month: 7, day: 2)
        let to = makeUtcDate(year: 2026, month: 7, day: 1)

        do {
            _ = try await api.getSleep(identifier: deviceId, fromDate: from, toDate: to)
            XCTFail("Expected invalidArgument")
        } catch let error as PolarErrors {
            if case .invalidArgument = error {
                XCTAssertTrue(true)
            } else {
                XCTFail("Expected PolarErrors.invalidArgument, got \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func test_getSleep_singleDay_returnsEmptyWhenNoValidSleepFilesFound() async throws {
        let day = makeUtcDate(year: 2026, month: 7, day: 1)

        let result = try await api.getSleep(identifier: deviceId, fromDate: day, toDate: day)

        XCTAssertTrue(result.isEmpty)
        XCTAssertEqual(mockClient.requestCalls.count, 2)
    }

    func test_getSleep_multiDay_queriesEachDayAndReturnsEmptyWithoutDecodableData() async throws {
        let from = makeUtcDate(year: 2026, month: 7, day: 1)
        let to = makeUtcDate(year: 2026, month: 7, day: 3)

        let result = try await api.getSleep(identifier: deviceId, fromDate: from, toDate: to)

        XCTAssertTrue(result.isEmpty)
        XCTAssertEqual(mockClient.requestCalls.count, 6)
    }

    // MARK: - getSleepData (deprecated alias)
    func test_getSleepData_deprecatedAlias_behavesLikeGetSleep() async throws {
        let day = makeUtcDate(year: 2026, month: 7, day: 6)

        let result = try await api.getSleepData(identifier: deviceId, fromDate: day, toDate: day)

        XCTAssertTrue(result.isEmpty)
        XCTAssertEqual(mockClient.requestCalls.count, 2)
    }

    // MARK: - Helpers
    private var restApiNotificationId: Int {
        Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue
    }

    private func sleepRecordingEvent(enabled: Int) -> Data {
        Data("{\"sleep_recording_state\":{\"enabled\":\(enabled)}}".utf8)
    }

    private func makeUtcDate(year: Int, month: Int, day: Int) -> Date {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = day
        comps.hour = 0
        comps.minute = 0
        comps.second = 0
        comps.timeZone = TimeZone(secondsFromGMT: 0)
        return Calendar(identifier: .gregorian).date(from: comps)!
    }

    private func readAll(from stream: InputStream) -> Data {
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 256)
        while stream.hasBytesAvailable {
            let read = stream.read(&buffer, maxLength: buffer.count)
            if read <= 0 { break }
            data.append(buffer, count: read)
        }
        return data
    }
}

private final class HangingSleepNotificationClient: MockBlePsFtpClient, @unchecked Sendable {
    override func waitNotification() -> AsyncThrowingStream<PsFtpNotification, Error> {
        AsyncThrowingStream { _ in }
    }
}

private final class FailingSleepNotificationClient: MockBlePsFtpClient, @unchecked Sendable {
    let waitNotificationError: Error

    init(gattServiceTransmitter: BleAttributeTransportProtocol, waitNotificationError: Error) {
        self.waitNotificationError = waitNotificationError
        super.init(gattServiceTransmitter: gattServiceTransmitter)
    }

    override func waitNotification() -> AsyncThrowingStream<PsFtpNotification, Error> {
        AsyncThrowingStream { continuation in
            continuation.finish(throwing: waitNotificationError)
        }
    }
}
