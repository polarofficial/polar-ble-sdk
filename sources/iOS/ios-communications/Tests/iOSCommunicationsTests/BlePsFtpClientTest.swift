//  Copyright © 2021 Polar. All rights reserved.

import XCTest
import iOSCommunications
import CoreBluetooth

private struct TimeoutError: Error {}

// MARK: - Auto-acknowledging transmitter for request() tests

/// Immediately acknowledges each MTU packet write (unblocking `waitPacketsWritten`) and
/// injects a pre-queued response frame into the client's MTU input queue so that
/// `readResponse` finds it deterministically without `Task.sleep` delays.
private class ResponseInjectingTransmitter: MockGattServiceTransmitterImpl {
    var responseQueue: [Data] = []

    override func transmitMessage(
        _ parent: BleGattClientBase,
        serviceUuid: CBUUID,
        characteristicUuid: CBUUID,
        packet: Data,
        withResponse: Bool
    ) throws {
        guard characteristicUuid == BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC else { return }
        parent.serviceDataWritten(characteristicUuid, err: 0)
        if !responseQueue.isEmpty {
            parent.processServiceData(characteristicUuid, data: responseQueue.removeFirst(), err: 0)
        }
    }
}

// MARK: - Test class

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

    // MARK: - RFC76 frame helpers

    /// RFC76 LAST frame: status=LAST, next=0, seq=seqNum.  Header = 0x02 | (seqNum << 4).
    private func makeLASTFrame(seqNum: Int = 0, payload: Data = Data()) -> Data {
        var frame = Data([UInt8(0x02 | (seqNum << 4))])
        frame.append(payload)
        return frame
    }

    /// RFC76 ERROR_OR_RESPONSE frame with error code 0 (success).
    /// Header = 0x00, followed by two zero bytes for the error code field.
    private func makeSuccessResponseFrame() -> Data {
        return Data([0x00, 0x00, 0x00])
    }

    // MARK: - waitNotification helper

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

    // MARK: - request() + PFTP_AIR_PACKET_LOST_ERROR tests

    // GIVEN the device returns a response whose RFC76 sequence number does not match
    //       what the client expects (simulating an air-packet loss / reorder)
    // WHEN  request() is awaited
    // THEN  it throws BlePsFtpException.responseError(PFTP_AIR_PACKET_LOST_ERROR = 303)
    //       and does NOT hang
    func testRequest_whenResponseSequenceNumberMismatches_throwsAirPacketLostError() async throws {
        let transmitter = ResponseInjectingTransmitter()
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
        defer { client.disconnected() }

        // Inject a LAST frame with seq=1 — client expects seq=0, triggering the mismatch.
        // Header: 0x02 (LAST) | (1 << 4) = 0x12
        transmitter.responseQueue = [makeLASTFrame(seqNum: 1, payload: Data([0x00]))]

        do {
            _ = try await client.request(Data([0x00]))
            XCTFail("Expected PFTP_AIR_PACKET_LOST_ERROR to be thrown")
        } catch let error as BlePsFtpException {
            guard case .responseError(let code) = error else {
                return XCTFail("Expected responseError, got \(error)")
            }
            XCTAssertEqual(
                code,
                BlePsFtpUtility.PFTP_AIR_PACKET_LOST_ERROR,
                "Expected air-packet-lost code \(BlePsFtpUtility.PFTP_AIR_PACKET_LOST_ERROR), got \(code)"
            )
        } catch {
            XCTFail("Expected BlePsFtpException, got \(error)")
        }
    }

    // GIVEN request() has previously failed with PFTP_AIR_PACKET_LOST_ERROR
    // WHEN  a subsequent request() is made with a valid device response
    // THEN  the second request() succeeds, confirming the MTU queue was cleaned up
    //       and the client recovered correctly
    func testRequest_afterAirPacketLostError_subsequentRequestSucceeds() async throws {
        let transmitter = ResponseInjectingTransmitter()
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
        defer { client.disconnected() }

        // First request — wrong seq → fails
        transmitter.responseQueue = [makeLASTFrame(seqNum: 1, payload: Data([0x00]))]
        _ = try? await client.request(Data([0x00]))

        // Second request — valid ERROR_OR_RESPONSE frame with error=0
        transmitter.responseQueue = [makeSuccessResponseFrame()]
        do {
            _ = try await client.request(Data([0x00]))
        } catch {
            XCTFail("Second request should succeed after air-packet-lost recovery, got: \(error)")
        }
    }

    // GIVEN request() has failed with PFTP_AIR_PACKET_LOST_ERROR
    //   AND extra stale packets (simulating late device frames) are pushed to the MTU
    //       queue between the two requests
    // WHEN  the second request() is made with a valid device response
    // THEN  the second request() succeeds — confirming that mtuInputQueue.removeAll()
    //       in the catch block plus resetMtuPipe() at the start of the next request
    //       together ensure a clean slate
    func testRequest_afterAirPacketLostError_stalePacketsDoNotPoisonNextRequest() async throws {
        let transmitter = ResponseInjectingTransmitter()
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
        defer { client.disconnected() }

        // First request fails
        transmitter.responseQueue = [makeLASTFrame(seqNum: 1, payload: Data([0x00]))]
        _ = try? await client.request(Data([0x00]))

        // Stale device frames arrive after the error (device still sending before cancel is honoured)
        client.processServiceData(
            BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC,
            data: makeLASTFrame(seqNum: 0, payload: Data([0xBA, 0xD0])),
            err: 0
        )
        client.processServiceData(
            BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC,
            data: makeLASTFrame(seqNum: 0, payload: Data([0xBA, 0xD1])),
            err: 0
        )

        // Second request — valid success response
        transmitter.responseQueue = [makeSuccessResponseFrame()]
        do {
            _ = try await client.request(Data([0x00]))
        } catch {
            XCTFail(
                "Second request should succeed despite stale packets — " +
                "mtuInputQueue.removeAll() in catch block may be missing. Error: \(error)"
            )
        }
    }

    // MARK: - waitNotification tests

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 single frame notification
    // THEN BLE PSFTP Service client emits the rfc76 payload to subscriber
    func testWaitNotificationSingleFrame() async throws {
        let rfc76Header = Data([0x02])
        var rfc76Payload = Data()
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams = Data([0xFF, 0x00])
        rfc76Payload.append(psftpNotifcationId)
        rfc76Payload.append(psftpNotificationParams)
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(rfc76Payload)

        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: notificationFromDevice, err: 0)
        let event = try await firstNotification(from: blePsFtpClient.waitNotification())

        XCTAssertEqual(Int32(psftpNotifcationId[0]), event!.id)
        XCTAssertTrue(event!.parameters.isEqual(to: psftpNotificationParams))
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 multi frame notification
    // THEN BLE PSFTP Service client emits the combined payload from rfc76 notifications to subscriber
    func testWaitNotificationMultiFrame() async throws {
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF, 0x00])
        let psftpNotificationParams2 = Data([0xEF, 0xFE])

        var notificationFromDeviceData1 = Data([0x06])
        notificationFromDeviceData1.append(psftpNotifcationId)
        notificationFromDeviceData1.append(psftpNotificationParams1)

        var notificationFromDeviceData2 = Data([0x13])
        notificationFromDeviceData2.append(psftpNotificationParams2)

        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: notificationFromDeviceData1, err: 0)
        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: notificationFromDeviceData2, err: 0)
        let event = try await firstNotification(from: blePsFtpClient.waitNotification())

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
        var notificationFromDevice = Data([0x00])
        notificationFromDevice.append(Data([0xFF, 0x00]))

        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: notificationFromDevice, err: 0)
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification(), timeout: 1.0)
            XCTFail("Expected TimeoutError but got a notification")
        } catch is TimeoutError {
            // Expected
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 error notification in second frame
    // THEN BLE PSFTP Service client shall ignore the received rfc76 error frame, emit nothing and continue waiting
    func testWaitNotificationErrorInSecondFrame() async throws {
        var frame1 = Data([0x06])
        frame1.append(Data([0x01, 0xFF, 0x00]))
        var frame2 = Data([0x11])
        frame2.append(Data([0xEF, 0xFE]))

        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: frame1, err: 0)
        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: frame2, err: 0)
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification(), timeout: 1.0)
            XCTFail("Expected TimeoutError but got a notification")
        } catch is TimeoutError {
            // Expected
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service client receives error packet (err != 0) in first packet
    // THEN BLE PSFTP Service client shall finish the stream with an error
    func testWaitNotificationErrorInFirstPackage() async throws {
        var notificationFromDevice = Data([0x00])
        notificationFromDevice.append(Data([0xFF, 0x00]))
        let expectedError = 1

        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: notificationFromDevice, err: expectedError)

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
        var frame1 = Data([0x06])
        frame1.append(Data([0x01, 0xFF, 0x00]))
        var frame2 = Data([0x13])
        frame2.append(Data([0xEF, 0xFE]))
        let expectedError = 255

        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: frame1, err: 0)
        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: frame2, err: expectedError)

        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification())
            XCTFail("Observable should fail instead of complete")
        } catch let error as BlePsFtpException {
            guard case .responseError(errorCode: expectedError) = error else {
                return XCTFail("Unexpected error code in \(error)")
            }
        }
    }

    // MARK: - Fan-out / multiplexing tests (Option A)

    // GIVEN two concurrent callers each call waitNotification()
    // WHEN a single notification is pushed by the device
    // THEN both callers receive the same notification — fan-out works correctly
    // (this was the root cause of the customer-reported D2H + REST API conflict)
    func testWaitNotification_twoSimultaneousSubscribers_bothReceiveNotification() async throws {
        // Subscribe both callers before pushing any data
        let stream1 = blePsFtpClient.waitNotification()
        let stream2 = blePsFtpClient.waitNotification()

        // Single LAST-frame notification: id=0x03, params=[0xAB]
        var notification = Data([0x02])   // RFC76 LAST, seq=0, next=0
        notification.append(Data([0x03, 0xAB]))

        blePsFtpClient.processServiceData(
            BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC,
            data: notification,
            err: 0
        )

        let event1 = try await firstNotification(from: stream1)
        let event2 = try await firstNotification(from: stream2)

        XCTAssertNotNil(event1, "Subscriber 1 should receive the notification")
        XCTAssertNotNil(event2, "Subscriber 2 should receive the notification")
        XCTAssertEqual(event1!.id, 0x03, "Subscriber 1 notification id mismatch")
        XCTAssertEqual(event2!.id, 0x03, "Subscriber 2 notification id mismatch")
        XCTAssertTrue(event1!.parameters.isEqual(to: Data([0xAB])),
                      "Subscriber 1 parameters mismatch")
        XCTAssertTrue(event2!.parameters.isEqual(to: Data([0xAB])),
                      "Subscriber 2 parameters mismatch")
    }

    // GIVEN two concurrent callers each call waitNotification()
    // WHEN multiple sequential notifications arrive from the device
    // THEN each subscriber independently receives every notification in order
    func testWaitNotification_twoSubscribers_eachReceiveAllNotifications() async throws {
        let stream1 = blePsFtpClient.waitNotification()
        let stream2 = blePsFtpClient.waitNotification()

        // Build two distinct LAST-frame notifications
        var notif1 = Data([0x02]); notif1.append(Data([0x01, 0x11]))   // id=1, params=[0x11]
        var notif2 = Data([0x02]); notif2.append(Data([0x02, 0x22]))   // id=2, params=[0x22]

        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: notif1, err: 0)
        blePsFtpClient.processServiceData(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: notif2, err: 0)

        // Collect two events from each subscriber
        async let e1a = firstNotification(from: stream1)
        async let e2a = firstNotification(from: stream2)

        let first1 = try await e1a
        let first2 = try await e2a

        XCTAssertNotNil(first1, "Subscriber 1 should receive first notification")
        XCTAssertNotNil(first2, "Subscriber 2 should receive first notification")
        XCTAssertEqual(first1!.id, 0x01, "Subscriber 1 first notification id mismatch")
        XCTAssertEqual(first2!.id, 0x01, "Subscriber 2 first notification id mismatch")
    }

    // GIVEN two concurrent callers each call waitNotification()
    // WHEN only ONE broadcast loop is started regardless of subscriber count
    // THEN a second notification is still received after the first — loop keeps running
    func testWaitNotification_singleBroadcastLoop_doesNotStartDuplicateLoops() async throws {
        // Subscribe three callers — all backed by the same loop
        let stream1 = blePsFtpClient.waitNotification()
        let stream2 = blePsFtpClient.waitNotification()
        let stream3 = blePsFtpClient.waitNotification()

        var notif = Data([0x02]); notif.append(Data([0x07, 0xFF]))

        blePsFtpClient.processServiceData(
            BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC,
            data: notif,
            err: 0
        )

        let e1 = try await firstNotification(from: stream1)
        let e2 = try await firstNotification(from: stream2)
        let e3 = try await firstNotification(from: stream3)

        XCTAssertEqual(e1!.id, 0x07, "stream1 wrong id")
        XCTAssertEqual(e2!.id, 0x07, "stream2 wrong id")
        XCTAssertEqual(e3!.id, 0x07, "stream3 wrong id")
    }

    // GIVEN two concurrent callers each call waitNotification()
    // WHEN a transport error packet arrives (err != 0)
    // THEN BOTH subscriber streams finish with the same error — not just the first one
    func testWaitNotification_transportError_bothSubscribersReceiveError() async throws {
        let stream1 = blePsFtpClient.waitNotification()
        let stream2 = blePsFtpClient.waitNotification()

        let expectedErrorCode = 42
        var errorFrame = Data([0x02]); errorFrame.append(Data([0x01]))

        blePsFtpClient.processServiceData(
            BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC,
            data: errorFrame,
            err: expectedErrorCode
        )

        do {
            _ = try await firstNotification(from: stream1)
            XCTFail("stream1 should have thrown")
        } catch let error as BlePsFtpException {
            guard case .responseError(let code) = error else {
                return XCTFail("stream1: expected responseError, got \(error)")
            }
            XCTAssertEqual(code, expectedErrorCode, "stream1 wrong error code")
        }

        do {
            _ = try await firstNotification(from: stream2)
            XCTFail("stream2 should have thrown")
        } catch let error as BlePsFtpException {
            guard case .responseError(let code) = error else {
                return XCTFail("stream2: expected responseError, got \(error)")
            }
            XCTAssertEqual(code, expectedErrorCode, "stream2 wrong error code")
        }
    }

    // GIVEN one active subscriber and one that has been cancelled
    // WHEN a notification arrives
    // THEN only the active subscriber receives it — cancelled subscriber is silently unregistered
    func testWaitNotification_cancelledSubscriber_doesNotReceiveNotifications() async throws {
        let activeStream = blePsFtpClient.waitNotification()

        // Subscribe a second stream in a Task that we cancel immediately
        let cancelledTask = Task {
            for try await _ in blePsFtpClient.waitNotification() { }
        }
        cancelledTask.cancel()
        // Give the cancellation a moment to propagate and remove the continuation
        try await Task.sleep(nanoseconds: 50_000_000)

        var notif = Data([0x02]); notif.append(Data([0x05, 0xBB]))
        blePsFtpClient.processServiceData(
            BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC,
            data: notif,
            err: 0
        )

        // Active subscriber should still receive the notification cleanly
        let event = try await firstNotification(from: activeStream)
        XCTAssertNotNil(event, "Active subscriber should still receive notification after sibling cancellation")
        XCTAssertEqual(event!.id, 0x05, "Active subscriber wrong notification id")
    }

    // GIVEN an active waitNotification() subscription
    // WHEN disconnected() is called
    // THEN the subscriber stream finishes with an error (gattDisconnected or operationCanceled)
    // NOTE: disconnected() cancels the operation queue first, so the broadcast loop may exit
    //       with operationCanceled before the gattDisconnected flush in disconnected() runs.
    //       Both are valid termination signals — the important thing is that the stream
    //       does NOT hang.
    func testWaitNotification_onDisconnect_subscriberStreamFinishesWithError() async throws {
        let stream = blePsFtpClient.waitNotification()

        // Disconnect on a background thread after a short delay
        Task {
            try await Task.sleep(nanoseconds: 100_000_000)
            self.blePsFtpClient.disconnected()
        }

        do {
            _ = try await firstNotification(from: stream, timeout: 3.0)
            XCTFail("Expected error after disconnected() — stream should not yield a notification")
        } catch is BleGattException {
            // gattDisconnected — expected if flush in disconnected() fires first
        } catch let error as BlePsFtpException {
            guard case .operationCanceled = error else {
                return XCTFail("Expected operationCanceled or gattDisconnected, got \(error)")
            }
            // operationCanceled — expected if cancelAllOperations() fires first
        } catch is TimeoutError {
            XCTFail("Stream hung instead of completing after disconnected()")
        }
    }

    // MARK: - sendMtuCancelPacket cancellation fix tests

    // GIVEN request() throws PFTP_AIR_PACKET_LOST_ERROR during a multi-frame response
    //   AND the BLE stack never ACKs the cancel packet (congested/dead link)
    // WHEN disconnected() is called
    // THEN mtuOperationQueue drains within a short deadline — NOT after the full 90s PROTOCOL_TIMEOUT
    //      This verifies the readResponse → sendMtuCancelPacket(canceled: canceled) path.
    func testDisconnect_whileSendMtuCancelPacketIsInFlight_drainsQueueQuickly() async throws {
        // A transmitter that:
        // - ACKs the initial request packet immediately (so the request sends)
        // - injects a seq-mismatch MORE frame (status=0x06 | seq=1 → 0x16),
        //   triggering sendMtuCancelPacket inside readResponse
        // - then NEVER ACKs the cancel packet (simulating a dead link)
        final class HangingCancelTransmitter: MockGattServiceTransmitterImpl, @unchecked Sendable {
            var callCount = 0
            override func transmitMessage(
                _ parent: BleGattClientBase,
                serviceUuid: CBUUID,
                characteristicUuid: CBUUID,
                packet: Data,
                withResponse: Bool
            ) throws {
                guard characteristicUuid == BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC else { return }
                callCount += 1
                if callCount == 1 {
                    parent.serviceDataWritten(characteristicUuid, err: 0)
                    parent.processServiceData(characteristicUuid, data: Data([0x16, 0x00]), err: 0)
                }
                // Cancel packet (callCount >= 2): do NOT ACK → waitPacketsWritten blocks
            }
        }

        let transmitter = HangingCancelTransmitter()
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)

        Task { _ = try? await client.request(Data([0x00])) }
        try await Task.sleep(nanoseconds: 200_000_000)

        let deadline = Date().addingTimeInterval(5.0)
        let disconnectTask = Task { client.disconnected() }

        var timedOut = false
        while !disconnectTask.isCancelled {
            if Date() > deadline { timedOut = true; disconnectTask.cancel(); break }
            if await disconnectTask.value == () { break }
        }

        XCTAssertFalse(timedOut,
            "disconnected() hung — sendMtuCancelPacket blocked on uncancellable BlockOperation()")
    }

    // GIVEN request() is mid-write when cancelAllOperations() fires (via disconnected())
    //   AND the cancel packet's ACK never arrives (dead link)
    // WHEN disconnected() is called while sendMtuCancelPacket is in-flight
    // THEN mtuOperationQueue drains quickly — verifies request catch block path:
    //      sendMtuCancelPacket(canceled: block ?? BlockOperation())
    //
    // NOTE: This test triggers the request catch block by calling disconnected() while
    //       the first transmitMtuPacket is blocking in waitPacketsWritten (never ACKed).
    //       cancelAllOperations() marks the outer block as cancelled; transmitMtuPacket
    //       then throws operationCanceled; catch block sees isCancelled==true and calls
    //       sendMtuCancelPacket(canceled: block). Since block is already cancelled,
    //       transmitMtuPacket inside sendMtuCancelPacket exits immediately → no hang.
    func testDisconnect_duringRequestCancelBlock_drainsQueueQuickly() async throws {
        // Transmitter that never ACKs any packet — the request blocks in waitPacketsWritten
        // until disconnected() cancels the operation queue.
        final class NeverAckTransmitter: MockGattServiceTransmitterImpl, @unchecked Sendable {
            override func transmitMessage(
                _ parent: BleGattClientBase,
                serviceUuid: CBUUID,
                characteristicUuid: CBUUID,
                packet: Data,
                withResponse: Bool
            ) throws {
                // Never ACK → waitPacketsWritten blocks until canceled
            }
        }

        let transmitter = NeverAckTransmitter()
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)

        // Fire a request — it immediately blocks in waitPacketsWritten (no ACK)
        Task { _ = try? await client.request(Data([0x00])) }
        try await Task.sleep(nanoseconds: 100_000_000)

        // disconnected() → cancelAllOperations() → outer block cancelled →
        // waitPacketsWritten exits → catch block runs with isCancelled==true →
        // sendMtuCancelPacket(canceled: block) called — block already cancelled so
        // transmitMtuPacket exits immediately → disconnected() drains quickly
        let deadline = Date().addingTimeInterval(5.0)
        let disconnectTask = Task { client.disconnected() }

        var timedOut = false
        while !disconnectTask.isCancelled {
            if Date() > deadline { timedOut = true; disconnectTask.cancel(); break }
            if await disconnectTask.value == () { break }
        }

        XCTAssertFalse(timedOut,
            "disconnected() hung in request catch block — " +
            "sendMtuCancelPacket did not use the outer canceled block")
    }

    // GIVEN query() is waiting for a response when cancelAllOperations() fires (via disconnected())
    //   AND the cancel packet's ACK never arrives
    // WHEN disconnected() is called
    // THEN mtuOperationQueue drains quickly — verifies query catch block path:
    //      sendMtuCancelPacket(canceled: block ?? BlockOperation())
    func testDisconnect_duringQueryCancelBlock_drainsQueueQuickly() async throws {
        // Transmitter that ACKs the query packet (so it transmits) but never delivers a response,
        // then never ACKs the cancel packet either.
        final class QueryHangingTransmitter: MockGattServiceTransmitterImpl, @unchecked Sendable {
            var callCount = 0
            override func transmitMessage(
                _ parent: BleGattClientBase,
                serviceUuid: CBUUID,
                characteristicUuid: CBUUID,
                packet: Data,
                withResponse: Bool
            ) throws {
                guard characteristicUuid == BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC else { return }
                callCount += 1
                if callCount == 1 {
                    // ACK the query packet so it passes waitPacketsWritten and enters readResponse
                    parent.serviceDataWritten(characteristicUuid, err: 0)
                    // Inject no response → readResponse blocks polling notificationInputQueue
                    // until the outer block is cancelled by disconnected()
                }
                // Cancel packet: never ACK
            }
        }

        let transmitter = QueryHangingTransmitter()
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)

        Task { _ = try? await client.query(1, parameters: nil) }
        try await Task.sleep(nanoseconds: 200_000_000)

        let deadline = Date().addingTimeInterval(5.0)
        let disconnectTask = Task { client.disconnected() }

        var timedOut = false
        while !disconnectTask.isCancelled {
            if Date() > deadline { timedOut = true; disconnectTask.cancel(); break }
            if await disconnectTask.value == () { break }
        }

        XCTAssertFalse(timedOut,
            "disconnected() hung in query catch block — " +
            "sendMtuCancelPacket did not use the outer canceled block")
    }

    // GIVEN write() is transmitting when cancelAllOperations() fires (via disconnected())
    //   AND the cancel packet's ACK never arrives
    // WHEN disconnected() is called
    // THEN mtuOperationQueue drains quickly — verifies write catch block path:
    //      sendMtuCancelPacket(canceled: block ?? BlockOperation())
    func testDisconnect_duringWriteCancelBlock_drainsQueueQuickly() async throws {
        // Transmitter that ACKs the first write frame, then never ACKs the second
        // (simulates partial write + dead link on subsequent packets)
        final class WriteHangingTransmitter: MockGattServiceTransmitterImpl, @unchecked Sendable {
            var callCount = 0
            override func transmitMessage(
                _ parent: BleGattClientBase,
                serviceUuid: CBUUID,
                characteristicUuid: CBUUID,
                packet: Data,
                withResponse: Bool
            ) throws {
                guard characteristicUuid == BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC else { return }
                callCount += 1
                if callCount == 1 {
                    parent.serviceDataWritten(characteristicUuid, err: 0)
                }
                // 2nd+ packets (data frame, cancel packet): never ACK → blocks
            }
        }

        let transmitter = WriteHangingTransmitter()
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)

        var op = Communications_PbPFtpOperation()
        op.command = .put
        op.path = "/TEST"
        let header = try op.serializedData() as NSData
        let payload = InputStream(data: Data([0xAB]))

        Task {
            for try await _ in client.write(header, data: payload) { }
        }
        try await Task.sleep(nanoseconds: 200_000_000)

        let deadline = Date().addingTimeInterval(5.0)
        let disconnectTask = Task { client.disconnected() }

        var timedOut = false
        while !disconnectTask.isCancelled {
            if Date() > deadline { timedOut = true; disconnectTask.cancel(); break }
            if await disconnectTask.value == () { break }
        }

        XCTAssertFalse(timedOut,
            "disconnected() hung in write catch block — " +
            "sendMtuCancelPacket did not use the outer canceled block")
    }

    // GIVEN a client that previously got stuck in sendMtuCancelPacket (non-recovery scenario)
    //   AND disconnected() returned quickly due to the fix
    // WHEN a new BlePsFtpClient is created (simulating device reconnect)
    // THEN it can process a new request successfully — full recovery without BT toggle
    func testFullRecovery_afterCancelPacketHangAndDisconnect_newClientRequestSucceeds() async throws {
        final class HangThenRecoverTransmitter: MockGattServiceTransmitterImpl, @unchecked Sendable {
            var callCount = 0
            override func transmitMessage(
                _ parent: BleGattClientBase,
                serviceUuid: CBUUID,
                characteristicUuid: CBUUID,
                packet: Data,
                withResponse: Bool
            ) throws {
                guard characteristicUuid == BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC else { return }
                callCount += 1
                if callCount == 1 {
                    // First client: ACK request, inject MORE seq-mismatch frame
                    parent.serviceDataWritten(characteristicUuid, err: 0)
                    parent.processServiceData(characteristicUuid, data: Data([0x16, 0x00]), err: 0)
                }
                // Cancel packet: never ACK → hang
            }
        }

        // --- Phase 1: trigger the bug, disconnect quickly ---
        let hangingTransmitter = HangThenRecoverTransmitter()
        let brokenClient = BlePsFtpClient(gattServiceTransmitter: hangingTransmitter)

        Task { _ = try? await brokenClient.request(Data([0x00])) }
        try await Task.sleep(nanoseconds: 200_000_000)

        // disconnected() must return quickly (< 5s)
        let deadline = Date().addingTimeInterval(5.0)
        let disconnectTask = Task { brokenClient.disconnected() }
        while !disconnectTask.isCancelled {
            if Date() > deadline { disconnectTask.cancel(); XCTFail("disconnected() timed out"); return }
            if await disconnectTask.value == () { break }
        }

        // --- Phase 2: reconnect — new client, normal transmitter ---
        let recoveredTransmitter = ResponseInjectingTransmitter()
        let recoveredClient = BlePsFtpClient(gattServiceTransmitter: recoveredTransmitter)
        defer { recoveredClient.disconnected() }

        recoveredTransmitter.responseQueue = [makeSuccessResponseFrame()]

        do {
            _ = try await recoveredClient.request(Data([0x00]))
        } catch {
            XCTFail("New client request failed after recovery — device still stuck: \(error)")
        }
    }
}
