// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest
import Combine
import CoreBluetooth

@testable import PolarBleSdk

/// Unit tests for `PolarBleApiImpl` REST Service API extension
final class PolarBleApiImplRestServiceTests: XCTestCase {

    // MARK: - Properties

    private let deviceId = "ABCDEF01"
    private var mockClient: MockBlePsFtpClient!
    private var mockSession: MockBleDeviceSession!
    private var api: PolarBleApiImplWithMockSession!
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Set-up / Tear-down

    override func setUpWithError() throws {
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: ["360": .polarFileSystemV2],
            defaultFileSystemType: .polarFileSystemV2,
            defaultRecordingSupported: false
        )
        let gatt = MockPolarGattServiceTransmitter()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: gatt)
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
    }

    override func tearDownWithError() throws {
        mockClient = nil
        mockSession = nil
        api = nil
        cancellables.removeAll()
    }

    // MARK: - Async/await helpers

    @discardableResult
    private func awaitSingleAsync<T>(_ operation: @escaping () async throws -> T, timeout: TimeInterval = 2) throws -> T {
        var result: Result<T, Error>?
        let exp = XCTestExpectation(description: "awaitSingleAsync")
        Task {
            do { result = .success(try await operation()) }
            catch { result = .failure(error) }
            exp.fulfill()
        }
        wait(for: [exp], timeout: timeout)
        return try XCTUnwrap(result).get()
    }

    private func awaitVoidAsync(_ operation: @escaping () async throws -> Void, timeout: TimeInterval = 2) throws {
        try awaitSingleAsync(operation, timeout: timeout)
    }

    private func awaitErrorAsync<T>(_ operation: @escaping () async throws -> T, timeout: TimeInterval = 2) -> Error? {
        var receivedError: Error?
        let exp = XCTestExpectation(description: "awaitErrorAsync")
        Task {
            do { _ = try await operation() }
            catch { receivedError = error }
            exp.fulfill()
        }
        wait(for: [exp], timeout: timeout)
        return receivedError
    }

    /// Collects all values from an AsyncThrowingStream until it finishes or throws.
    private func collectAllAsync<T>(_ stream: AsyncThrowingStream<T, Error>, timeout: TimeInterval = 5) throws -> [T] {
        var results: [T] = []
        try awaitSingleAsync({
            var r: [T] = []
            for try await value in stream { r.append(value) }
            return r
        }, timeout: timeout).forEach { results.append($0) }
        return results
    }

    private func awaitStreamError<T>(_ stream: AsyncThrowingStream<T, Error>, timeout: TimeInterval = 2) -> Error? {
        awaitErrorAsync({
            for try await _ in stream {}
        }, timeout: timeout)
    }

    // MARK: - Helper methods

    private func makeServicesJsonData() -> Data {
        let json = """
        {
            "services": {
                "training": "/REST/TRAINING.API",
                "sleep": "/REST/SLEEP.API",
                "ui_states": "/REST/UISTATES.API"
            }
        }
        """
        return json.data(using: .utf8)!
    }

    private func makeServiceDescriptionJsonData() -> Data {
        let json = """
        {
            "events": ["lap_data", "exercise_summary"],
            "cmd": {
                "subscribe": "./REST/TRAINING.API?cmd=subscribe&event=&resend=&details=[]&triggers=[]",
                "unsubscribe": "./REST/TRAINING.API?cmd=unsubscribe&event="
            },
            "lap_data": {
                "details": ["lap_hr_bpm_avg", "lap_speed_avg", "lap_time"],
                "triggers": ["default", "distance", "time"]
            },
            "exercise_summary": {
                "details": ["duration", "distance", "hr_bpm_avg"]
            }
        }
        """
        return json.data(using: .utf8)!
    }

    private func makePFtpGetOperation(path: String) -> Data {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = path
        return (try? operation.serializedData()) ?? Data()
    }

    private func makePFtpPutOperation(path: String) -> Data {
        var operation = Protocol_PbPFtpOperation()
        operation.command = .put
        operation.path = path
        return (try? operation.serializedData()) ?? Data()
    }

    // MARK: - Tests for listRestApiServices

    func testListRestApiServicesSuccess() throws {
        // Arrange
        let expectedData = makeServicesJsonData()
        mockClient.requestReturnValue = .success(expectedData)

        // Act
        let result = try awaitSingleAsync {
            try await self.api.listRestApiServices(identifier: self.deviceId)
        }

        // Assert
        XCTAssertNotNil(result.pathsForServices)
        XCTAssertEqual(result.serviceNames.count, 3)
        XCTAssertTrue(result.serviceNames.contains("training"))
        XCTAssertTrue(result.serviceNames.contains("sleep"))
        XCTAssertTrue(result.serviceNames.contains("ui_states"))

        XCTAssertEqual(result.servicePaths.count, 3)
        XCTAssertTrue(result.servicePaths.contains("/REST/TRAINING.API"))
        XCTAssertTrue(result.servicePaths.contains("/REST/SLEEP.API"))
        XCTAssertTrue(result.servicePaths.contains("/REST/UISTATES.API"))
    }

    func testListRestApiServicesCallsCorrectPath() throws {
        // Arrange
        let expectedData = makeServicesJsonData()
        mockClient.requestReturnValue = .success(expectedData)

        // Act
        _ = try awaitSingleAsync {
            try await self.api.listRestApiServices(identifier: self.deviceId)
        }

        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        let requestData = try XCTUnwrap(mockClient.requestCalls.first)
        let operation = try Protocol_PbPFtpOperation(serializedBytes: requestData)
        XCTAssertEqual(operation.command, .get)
        XCTAssertEqual(operation.path, "/REST/SERVICE.API")
    }

    func testListRestApiServicesThrowsOnFtpClientMissing() throws {
        // Arrange
        let session = MockNoFtpClientBleDeviceSession()
        let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: session)

        // Act & Assert
        let error = awaitErrorAsync {
            try await noFtpApi.listRestApiServices(identifier: self.deviceId)
        }
        XCTAssertNotNil(error)
        if let polarError = error as? PolarErrors {
            if case .serviceNotFound = polarError {
                XCTAssertTrue(true)
            } else {
                XCTFail("Expected PolarErrors.serviceNotFound, got \(polarError)")
            }
        } else {
            XCTFail("Expected PolarErrors.serviceNotFound")
        }
    }

    func testListRestApiServicesThrowsOnInvalidJson() throws {
        // Arrange
        let invalidData = "invalid json".data(using: .utf8)!
        mockClient.requestReturnValue = .success(invalidData)

        // Act & Assert
        let error = awaitErrorAsync {
            try await self.api.listRestApiServices(identifier: self.deviceId)
        }
        XCTAssertNotNil(error)
    }

    func testListRestApiServicesThrowsOnNetworkError() throws {
        // Arrange
        let networkError = NSError(domain: "NetworkError", code: -1)
        mockClient.requestReturnValue = .failure(networkError)

        // Act & Assert
        let error = awaitErrorAsync {
            try await self.api.listRestApiServices(identifier: self.deviceId)
        }
        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, "NetworkError")
    }

    // MARK: - Tests for getRestApiDescription

    func testGetRestApiDescriptionSuccess() throws {
        // Arrange
        let path = "/REST/TRAINING.API"
        let expectedData = makeServiceDescriptionJsonData()
        mockClient.requestReturnValue = .success(expectedData)

        // Act
        let result = try awaitSingleAsync {
            try await self.api.getRestApiDescription(identifier: self.deviceId, path: path)
        }

        // Assert
        XCTAssertEqual(result.events.count, 2)
        XCTAssertTrue(result.events.contains("lap_data"))
        XCTAssertTrue(result.events.contains("exercise_summary"))

        XCTAssertEqual(result.actionNames.count, 2)
        XCTAssertTrue(result.actionNames.contains("subscribe"))
        XCTAssertTrue(result.actionNames.contains("unsubscribe"))

        let lapDataDetails = result.eventDetails(for: "lap_data")
        XCTAssertEqual(lapDataDetails.count, 3)
        XCTAssertTrue(lapDataDetails.contains("lap_hr_bpm_avg"))

        let lapDataTriggers = result.eventTriggers(for: "lap_data")
        XCTAssertEqual(lapDataTriggers.count, 3)
        XCTAssertTrue(lapDataTriggers.contains("distance"))
    }

    func testGetRestApiDescriptionCallsCorrectPath() throws {
        // Arrange
        let path = "/REST/TRAINING.API"
        let expectedData = makeServiceDescriptionJsonData()
        mockClient.requestReturnValue = .success(expectedData)

        // Act
        _ = try awaitSingleAsync {
            try await self.api.getRestApiDescription(identifier: self.deviceId, path: path)
        }

        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        let requestData = try XCTUnwrap(mockClient.requestCalls.first)
        let operation = try Protocol_PbPFtpOperation(serializedBytes: requestData)
        XCTAssertEqual(operation.command, .get)
        XCTAssertEqual(operation.path, path)
    }

    func testGetRestApiDescriptionPassesDifferentPaths() throws {
        // Arrange
        let paths = ["/REST/TRAINING.API", "/REST/SLEEP.API", "/REST/UISTATES.API"]
        let expectedData = makeServiceDescriptionJsonData()
        mockClient.requestReturnValue = .success(expectedData)

        // Act & Assert
        for path in paths {
            mockClient.requestCalls.removeAll()
            _ = try awaitSingleAsync {
                try await self.api.getRestApiDescription(identifier: self.deviceId, path: path)
            }

            let requestData = try XCTUnwrap(mockClient.requestCalls.first)
            let operation = try Protocol_PbPFtpOperation(serializedBytes: requestData)
            XCTAssertEqual(operation.path, path)
        }
    }

    func testGetRestApiDescriptionThrowsOnFtpClientMissing() throws {
        // Arrange
        let session = MockNoFtpClientBleDeviceSession()
        let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: session)

        // Act & Assert
        let error = awaitErrorAsync {
            try await noFtpApi.getRestApiDescription(identifier: self.deviceId, path: "/REST/TRAINING.API")
        }
        XCTAssertNotNil(error)
    }

    func testGetRestApiDescriptionThrowsOnInvalidJson() throws {
        // Arrange
        let invalidData = "not json".data(using: .utf8)!
        mockClient.requestReturnValue = .success(invalidData)

        // Act & Assert
        let error = awaitErrorAsync {
            try await self.api.getRestApiDescription(identifier: self.deviceId, path: "/REST/TRAINING.API")
        }
        XCTAssertNotNil(error)
    }

    func testGetRestApiDescriptionThrowsOnNetworkError() throws {
        // Arrange
        let networkError = NSError(domain: "NetworkError", code: -2)
        mockClient.requestReturnValue = .failure(networkError)

        // Act & Assert
        let error = awaitErrorAsync {
            try await self.api.getRestApiDescription(identifier: self.deviceId, path: "/REST/TRAINING.API")
        }
        XCTAssertNotNil(error)
    }

    // MARK: - Tests for putNotification

    func testPutNotificationSuccess() throws {
        // Arrange
        let notification = "test_notification"
        let path = "/REST/TRAINING.API?cmd=subscribe"
        
        // Act
        try awaitVoidAsync {
            try await self.api.putNotification(
                identifier: self.deviceId,
                notification: notification,
                path: path
            )
        }

        // Assert
        XCTAssertEqual(mockClient.writeCalls.count, 1)
    }

    func testPutNotificationPassesCorrectData() throws {
        // Arrange
        let notification = "test_notification"
        let path = "/REST/TRAINING.API?cmd=subscribe"
        
        // Act
        try awaitVoidAsync {
            try await self.api.putNotification(
                identifier: self.deviceId,
                notification: notification,
                path: path
            )
        }

        // Assert
        XCTAssertEqual(mockClient.writeCalls.count, 1)
        let writeCall = try XCTUnwrap(mockClient.writeCalls.first)
        let operation = try Protocol_PbPFtpOperation(serializedBytes: Data(writeCall.header))
        XCTAssertEqual(operation.command, .put)
        XCTAssertEqual(operation.path, path)
    }

    func testPutNotificationPassesNotificationContent() throws {
        // Arrange
        let notification = "my_notification_content"
        let path = "/REST/TRAINING.API?cmd=subscribe"
        
        // Act
        try awaitVoidAsync {
            try await self.api.putNotification(
                identifier: self.deviceId,
                notification: notification,
                path: path
            )
        }

        // Assert
        let writeCall = try XCTUnwrap(mockClient.writeCalls.first)
        let data = readAll(from: writeCall.data)
        let notificationData = notification.data(using: .utf8)!
        XCTAssertEqual(data, notificationData)
    }

    func testPutNotificationWithDifferentPaths() throws {
        // Arrange
        let paths = [
            "/REST/TRAINING.API?cmd=subscribe",
            "/REST/SLEEP.API?cmd=subscribe",
            "/REST/UISTATES.API?cmd=subscribe"
        ]
        let notification = "test"

        // Act & Assert
        for path in paths {
            mockClient.writeCalls.removeAll()
            
            try awaitVoidAsync {
                try await self.api.putNotification(
                    identifier: self.deviceId,
                    notification: notification,
                    path: path
                )
            }

            let writeCall = try XCTUnwrap(mockClient.writeCalls.first)
            let operation = try Protocol_PbPFtpOperation(serializedBytes: Data(writeCall.header))
            XCTAssertEqual(operation.path, path)
        }
    }

    func testPutNotificationThrowsOnFtpClientMissing() throws {
        // Arrange
        let session = MockNoFtpClientBleDeviceSession()
        let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: session)

        // Act & Assert
        let error = awaitErrorAsync {
            try await noFtpApi.putNotification(
                identifier: self.deviceId,
                notification: "test",
                path: "/REST/TRAINING.API?cmd=subscribe"
            )
        }
        XCTAssertNotNil(error)
    }

    func testPutNotificationThrowsOnWriteError() throws {
        // Arrange
        let writeError = NSError(domain: "WriteError", code: -3)
        let stream = AsyncThrowingStream<UInt, Error> { continuation in
            continuation.finish(throwing: writeError)
        }
        mockClient.writeReturnValue = stream

        // Act & Assert
        let error = awaitErrorAsync {
            try await self.api.putNotification(
                identifier: self.deviceId,
                notification: "test",
                path: "/REST/TRAINING.API?cmd=subscribe"
            )
        }
        XCTAssertNotNil(error)
    }

    // MARK: - Tests for receiveRestApiEvents

    func testReceiveRestApiEventsReturnsStream() throws {
        // Arrange
        let eventData = makeServiceDescriptionJsonData()
        mockClient.receiveNotificationCalls.append(
            (Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue, [eventData], false)
        )

        // Act
        let stream = api.receiveRestApiEvents(identifier: deviceId) as AsyncThrowingStream<[JSON], Error>
        let result = try collectAllAsync(stream)

        // Assert
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].count, 1)
    }

    func testReceiveRestApiEventsReceivesMultipleEvents() throws {
        // Arrange
        let eventData1 = makeServiceDescriptionJsonData()
        let eventData2 = makeServiceDescriptionJsonData()
        let eventData3 = makeServiceDescriptionJsonData()

        mockClient.receiveNotificationCalls.append(
            (Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue, [eventData1], false)
        )
        mockClient.receiveNotificationCalls.append(
            (Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue, [eventData2], false)
        )
        mockClient.receiveNotificationCalls.append(
            (Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue, [eventData3], false)
        )

        // Act
        let stream = api.receiveRestApiEvents(identifier: deviceId) as AsyncThrowingStream<[JSON], Error>
        let result = try collectAllAsync(stream)

        // Assert
        XCTAssertEqual(result.count, 3)
        XCTAssertEqual(result[0].count, 1)
        XCTAssertEqual(result[1].count, 1)
        XCTAssertEqual(result[2].count, 1)
    }

    func testReceiveRestApiEventsReceivesMultipleEventsInBatch() throws {
        // Arrange
        let eventData1 = makeServiceDescriptionJsonData()
        let eventData2 = makeServiceDescriptionJsonData()

        mockClient.receiveNotificationCalls.append(
            (Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue, [eventData1, eventData2], false)
        )

        // Act
        let stream = api.receiveRestApiEvents(identifier: deviceId) as AsyncThrowingStream<[JSON], Error>
        let result = try collectAllAsync(stream)

        // Assert
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].count, 2)
    }

    func testReceiveRestApiEventsWithCompressedData() throws {
        // Arrange
        let eventData = makeServiceDescriptionJsonData()
        guard let compressedData = eventData.deflated(512) else {
            XCTFail("Failed to compress test data")
            return
        }

        mockClient.receiveNotificationCalls.append(
            (Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue, [compressedData], true)
        )

        // Act
        let stream = api.receiveRestApiEvents(identifier: deviceId) as AsyncThrowingStream<[JSON], Error>
        let result = try collectAllAsync(stream)

        // Assert
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].count, 1)
    }

    func testReceiveRestApiEventsThrowsOnFtpClientMissing() throws {
        // Arrange
        let session = MockNoFtpClientBleDeviceSession()
        let noFtpApi = PolarBleApiImplWithNoFtpSession(mockDeviceSession: session)

        // Act & Assert
        let stream = noFtpApi.receiveRestApiEvents(identifier: deviceId) as AsyncThrowingStream<[JSON], Error>
        let error = awaitStreamError(stream)
        XCTAssertNotNil(error)
    }

    func testReceiveRestApiEventsFinishesWhenNoMoreEvents() throws {
        // Arrange
        let eventData = makeServiceDescriptionJsonData()
        mockClient.receiveNotificationCalls.append(
            (Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue, [eventData], false)
        )

        // Act
        let stream = api.receiveRestApiEvents(identifier: deviceId) as AsyncThrowingStream<[Data], Error>
        let result = try collectAllAsync(stream)

        // Assert - should finish gracefully after receiving events
        XCTAssertEqual(result.count, 1)
    }

    func testReceiveRestApiEventsWithDecodableType() throws {
        // Arrange
        let eventJson = """
        {
            "lap_data": {
                "lap_hr_bpm_avg": 140,
                "lap_speed_avg": 12.5
            }
        }
        """
        let eventData = eventJson.data(using: .utf8)!

        mockClient.receiveNotificationCalls.append(
            (Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue, [eventData], false)
        )

        // Act
        let stream = api.receiveRestApiEvents(identifier: deviceId) as AsyncThrowingStream<[JSON], Error>
        let result = try collectAllAsync(stream)

        // Assert
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].count, 1)
    }

    // MARK: - Helper utilities

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

// MARK: - JSON Helper for decodable type test

struct JSON: Decodable {
    var data: [String: AnyCodable]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: DynamicCodingKeys.self)
        var data = [String: AnyCodable]()
        for key in container.allKeys {
            data[key.stringValue] = try container.decode(AnyCodable.self, forKey: key)
        }
        self.data = data
    }
}

struct DynamicCodingKeys: CodingKey {
    let stringValue: String
    var intValue: Int? { return nil }
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { return nil }
}

enum AnyCodable: Decodable {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case string(String)
    case array([AnyCodable])
    case object([String: AnyCodable])

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let bool = try? container.decode(Bool.self) {
            self = .bool(bool)
        } else if let int = try? container.decode(Int.self) {
            self = .int(int)
        } else if let double = try? container.decode(Double.self) {
            self = .double(double)
        } else if let string = try? container.decode(String.self) {
            self = .string(string)
        } else if let array = try? container.decode([AnyCodable].self) {
            self = .array(array)
        } else if let object = try? container.decode([String: AnyCodable].self) {
            self = .object(object)
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Cannot decode AnyCodable")
        }
    }
}
