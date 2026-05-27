import XCTest
@testable import iOSCommunications
import Combine

final class BlePfcClientTest: XCTestCase {

    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    var blePfcClient: BlePfcClient!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        blePfcClient = BlePfcClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        blePfcClient = nil
    }

    // MARK: - Helper

    /// Awaits a Combine publisher that produces no values (AnyPublisher<Never, Error>).
    private func awaitCompletion(_ publisher: AnyPublisher<Never, Error>) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            var cancellable: AnyCancellable?
            cancellable = publisher.sink(
                receiveCompletion: { completion in
                    switch completion {
                    case .finished: continuation.resume()
                    case .failure(let error): continuation.resume(throwing: error)
                    }
                    _ = cancellable
                },
                receiveValue: { _ in }
            )
        }
    }

    // MARK: - Tests

    func testSecurityModeSupportedFeatureParsing() async throws {
        // Arrange
        // byte1 bit1 => security mode supported
        let featureData = Data([0x00, 0x02])

        // Act
        blePfcClient.processServiceData(blePfcClient.PFC_FEATURE, data: featureData, err: 0)
        let feature = try await blePfcClient.readFeature(false)

        // Assert
        XCTAssertTrue(feature.securityModeSupported)
    }

    func testProcessConfigureSensorInitiatedSecurityModeResponseIsQueued() throws {
        // Arrange
        let response = Data([
            0xF0, // response code
            0x0E, // CONFIGURE_SENSOR_INITIATED_SECURITY_MODE
            0x00  // SUCCESS
        ])

        // Act
        blePfcClient.processServiceData(BlePfcClient.PFC_CP, data: response, err: 0)

        // Assert
        let queued = try blePfcClient.pfcInputQueue.poll(1)
        XCTAssertEqual(response, queued.first?.0)
        XCTAssertEqual(0, queued.first?.1)
    }

    func testSendConfigureSensorInitiatedSecurityModeCommand() async throws {
        // Arrange
        mockGattServiceTransmitterImpl.mockConnectionStatus = true
        // Pre-enable the PFC_CP notification so clientReady completes immediately.
        blePfcClient.notifyDescriptorWritten(BlePfcClient.PFC_CP, enabled: true, err: 0)
        try await awaitCompletion(blePfcClient.clientReady(false))

        let response = Data([0xF0, 0x0E, 0x01])
        blePfcClient.processServiceData(BlePfcClient.PFC_CP, data: response, err: 0)

        // Act
        let result = try await blePfcClient.sendControlPointCommand(
            .pfcConfigureSensorInitiatedSecurityMode,
            value: 0x01
        )

        // Assert
        XCTAssertEqual(result.opCode, 0x0E)
        XCTAssertEqual(result.status, .success)
    }

    func testSendRequestSecurityModeCommand() async throws {
        // Arrange
        mockGattServiceTransmitterImpl.mockConnectionStatus = true
        blePfcClient.notifyDescriptorWritten(BlePfcClient.PFC_CP, enabled: true, err: 0)
        try await awaitCompletion(blePfcClient.clientReady(false))

        let response = Data([
            0xF0,
            0x0C, // REQUEST_SECURITY_MODE
            0x01,
            0x01  // secure connection
        ])
        blePfcClient.processServiceData(BlePfcClient.PFC_CP, data: response, err: 0)

        // Act
        let result = try await blePfcClient.sendControlPointCommand(.pfcRequestSecurityMode, value: [])

        // Assert
        XCTAssertEqual(result.opCode, 0x0C)
        XCTAssertEqual(result.status, .success)
        XCTAssertEqual(result.payload.first, 0x01)
    }

    func testSendRequestSensorInitiatedSecurityModeCommand() async throws {
        // Arrange
        mockGattServiceTransmitterImpl.mockConnectionStatus = true
        blePfcClient.notifyDescriptorWritten(BlePfcClient.PFC_CP, enabled: true, err: 0)
        try await awaitCompletion(blePfcClient.clientReady(false))

        let response = Data([0xF0, 0x0F, 0x01, 0x01])
        blePfcClient.processServiceData(BlePfcClient.PFC_CP, data: response, err: 0)

        // Act
        let result = try await blePfcClient.sendControlPointCommand(.pfcRequestSensorInitiatedSecurityMode, value: [])

        // Assert
        XCTAssertEqual(result.opCode, 0x0F)
        XCTAssertEqual(result.status, .success)
        XCTAssertEqual(result.payload.first, 0x01)
    }
}
