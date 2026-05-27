//  Copyright © 2021 Polar. All rights reserved.

import XCTest
import iOSCommunications
import CoreBluetooth

private struct TimeoutError: Error {}

class BlePsFtpClientTest: XCTestCase {
    var blePsFtpClient: BlePsFtpClient!
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        blePsFtpClient = BlePsFtpClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        blePsFtpClient.disconnected()
        mockGattServiceTransmitterImpl = nil
        blePsFtpClient = nil
    }

    // Helper – returns the first notification from the stream or throws TimeoutError if nothing arrives within `timeout` seconds.
    private func firstNotification(
        from stream: AsyncThrowingStream<BlePsFtpClient.PsFtpNotification, Error>,
        timeout: TimeInterval = 5.0
    ) async throws -> BlePsFtpClient.PsFtpNotification? {
        try await withThrowingTaskGroup(of: BlePsFtpClient.PsFtpNotification?.self) { group in
            group.addTask {
                var iter = stream.makeAsyncIterator()
                return try await iter.next()
            }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                throw TimeoutError()
            }
            do {
                let result = try await group.next()
                group.cancelAll()
                return result ?? nil
            } catch {
                group.cancelAll()
                throw error
            }
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 single frame notification
    // THEN BLE PSFTP Service client emits the rfc76 payload to subscriber
    func testWaitNotificationSingleFrame() async throws {
        // Arrange
        let rfc76Header = Data([0x02])
        var rfc76Payload = Data()
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams = Data([0xFF, 0x00])
        rfc76Payload.append(psftpNotifcationId)
        rfc76Payload.append(psftpNotificationParams)
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(rfc76Payload)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC

        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDevice, err: 0)
        let event = try await firstNotification(from: blePsFtpClient.waitNotification())

        // Assert
        XCTAssertEqual(Int32(psftpNotifcationId[0]), event!.id)
        XCTAssertTrue(event!.parameters.isEqual(to: psftpNotificationParams))
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 multi frame notification
    // THEN BLE PSFTP Service client emits the combined payload from rfc76 notifications to subscriber
    func testWaitNotificationMultiFrame() async throws {
        // Arrange
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF, 0x00])
        let psftpNotificationParams2 = Data([0xEF, 0xFE])

        let rfc76Header1 = Data([0x06])
        var rfc76Payload1 = Data()
        rfc76Payload1.append(psftpNotifcationId)
        rfc76Payload1.append(psftpNotificationParams1)

        let rfc76Header2 = Data([0x13])
        var rfc76Payload2 = Data()
        rfc76Payload2.append(psftpNotificationParams2)

        var notificationFromDeviceData1 = Data()
        notificationFromDeviceData1.append(rfc76Header1)
        notificationFromDeviceData1.append(rfc76Payload1)

        var notificationFromDeviceData2 = Data()
        notificationFromDeviceData2.append(rfc76Header2)
        notificationFromDeviceData2.append(rfc76Payload2)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC

        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData1, err: 0)
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData2, err: 0)
        let event = try await firstNotification(from: blePsFtpClient.waitNotification())

        // Assert
        XCTAssertEqual(Int32(psftpNotifcationId[0]), event!.id)
        var expectedParameters = Data()
        expectedParameters.append(psftpNotificationParams1)
        expectedParameters.append(psftpNotificationParams2)
        XCTAssertTrue(event!.parameters.isEqual(to: expectedParameters))
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 error notification in first frame
    // THEN BLE PSFTP Service client shall ignore the received rfc76 error frame, emit nothing and continue waiting
    func testWaitNotificationErrorInFirstFrame() async throws {
        // Arrange
        let rfc76Header = Data([0x00])
        var rfc76Payload = Data([0xFF, 0x00])
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(rfc76Payload)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC

        // Act & Assert – stream should not emit within the timeout
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDevice, err: 0)
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification(), timeout: 1.0)
            XCTFail("Expected TimeoutError but got a notification")
        } catch is TimeoutError {
            // Expected: no notification emitted
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 error notification in second frame
    // THEN BLE PSFTP Service client shall ignore the received rfc76 error frame, emit nothing and continue waiting
    func testWaitNotificationErrorInSecondFrame() async throws {
        // Arrange
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF, 0x00])
        let psftpNotificationParams2 = Data([0xEF, 0xFE])

        let rfc76Header1 = Data([0x06])
        var rfc76Payload1 = Data()
        rfc76Payload1.append(psftpNotifcationId)
        rfc76Payload1.append(psftpNotificationParams1)

        // status = error (00b)
        let rfc76Header2 = Data([0x11])
        var rfc76Payload2 = Data()
        rfc76Payload2.append(psftpNotificationParams2)

        var notificationFromDeviceData1 = Data()
        notificationFromDeviceData1.append(rfc76Header1)
        notificationFromDeviceData1.append(rfc76Payload1)

        var notificationFromDeviceData2 = Data()
        notificationFromDeviceData2.append(rfc76Header2)
        notificationFromDeviceData2.append(rfc76Payload2)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC

        // Act & Assert – stream should not emit within the timeout
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData1, err: 0)
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData2, err: 0)
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification(), timeout: 1.0)
            XCTFail("Expected TimeoutError but got a notification")
        } catch is TimeoutError {
            // Expected: no notification emitted
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service client receives error packet (err != 0) in first packet
    // THEN BLE PSFTP Service client shall finish the stream with an error
    func testWaitNotificationErrorInFirstPackage() async throws {
        // Arrange
        let rfc76Header = Data([0x00])
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(Data([0xFF, 0x00]))

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let expectedError = 1

        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDevice, err: expectedError)

        // Assert
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification())
            XCTFail("Observable should fail instead of complete")
        } catch let error as BlePsFtpException {
            guard case .responseError(errorCode: expectedError) = error else {
                return XCTFail("Unexpected error code in \(error)")
            }
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service client receives error in second packet (err != 0)
    // THEN BLE PSFTP Service client shall finish the stream with an error
    func testWaitNotificationErrorInSecondPackage() async throws {
        // Arrange
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF, 0x00])
        let psftpNotificationParams2 = Data([0xEF, 0xFE])

        let rfc76Header1 = Data([0x06])
        var rfc76Payload1 = Data()
        rfc76Payload1.append(psftpNotifcationId)
        rfc76Payload1.append(psftpNotificationParams1)

        let rfc76Header2 = Data([0x13])
        var rfc76Payload2 = Data()
        rfc76Payload2.append(psftpNotificationParams2)

        var notificationFromDeviceData1 = Data()
        notificationFromDeviceData1.append(rfc76Header1)
        notificationFromDeviceData1.append(rfc76Payload1)

        var notificationFromDeviceData2 = Data()
        notificationFromDeviceData2.append(rfc76Header2)
        notificationFromDeviceData2.append(rfc76Payload2)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let expectedError = 255

        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData1, err: 0)
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData2, err: expectedError)

        // Assert
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification())
            XCTFail("Observable should fail instead of complete")
        } catch let error as BlePsFtpException {
            guard case .responseError(errorCode: expectedError) = error else {
                return XCTFail("Unexpected error code in \(error)")
            }
        }
    }
}
